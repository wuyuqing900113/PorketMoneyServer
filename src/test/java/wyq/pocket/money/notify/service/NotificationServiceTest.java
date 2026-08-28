package wyq.pocket.money.notify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.money.event.MoneyTransactionCreatedEvent;
import wyq.pocket.money.notify.config.NotifyProperties;
import wyq.pocket.money.notify.domain.Notification;
import wyq.pocket.money.notify.domain.NotificationDelivery;
import wyq.pocket.money.notify.dto.NotificationPageResponse;
import wyq.pocket.money.notify.mapper.NotificationDeliveryMapper;
import wyq.pocket.money.notify.mapper.NotificationMapper;
import wyq.pocket.money.rule.event.RuleArchivedEvent;
import wyq.pocket.money.user.service.FamilyService;

/**
 * 通知服务单元测试（M5 设计 §5/§6.3）：创建落库 + delivery 行生成条件、
 * 已读归属校验（他人/不存在 700001）、未读数、分页钳制、总开关短路。
 */
class NotificationServiceTest {

    private final NotificationMapper notificationMapper = mock(NotificationMapper.class);

    private final NotificationDeliveryMapper deliveryMapper =
            mock(NotificationDeliveryMapper.class);

    private final NotifyRecipientResolver recipientResolver = mock(NotifyRecipientResolver.class);

    private final FamilyService familyService = mock(FamilyService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final NotificationTemplateService templateService = new NotificationTemplateService();

    private static NotifyProperties properties(boolean enabled, boolean pushEnabled) {
        return new NotifyProperties(enabled, new BigDecimal("5.00"),
                new NotifyProperties.Push(pushEnabled),
                new NotifyProperties.Relay(true, "0 17 2 * * *", 3, Duration.ofMinutes(1)),
                new NotifyProperties.Cleanup(true, "0 47 4 * * *", Duration.ofDays(30)));
    }

    private NotificationService service(NotifyProperties properties) {
        return new NotificationService(notificationMapper, deliveryMapper, templateService,
                recipientResolver, familyService, auditService, properties);
    }

    @Test
    void createTxNotificationShouldInsertForOwnerAndAuditWhenPushDisabled() {
        when(recipientResolver.txRecipients(42L)).thenReturn(Set.of(42L));
        MoneyTransactionCreatedEvent event = new MoneyTransactionCreatedEvent(10L, 42L, 99L,
                "IN", "MANUAL_ADD", new BigDecimal("50.00"),
                new BigDecimal("150.00"), 7L, null);

        service(properties(true, false)).createTxNotification(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getUserId()).isEqualTo(42L);
        assertThat(notification.getFamilyId()).isEqualTo(10L);
        assertThat(notification.getType()).isEqualTo("TX_IN");
        assertThat(notification.getBizRefType()).isEqualTo("MONEY_TRANSACTION");
        assertThat(notification.getBizRefId()).isEqualTo(7L);
        assertThat(notification.getTitle()).isEqualTo("零花钱入账");
        assertThat(notification.getContent()).contains("50.00");
        verify(auditService).record(any(AuditEntry.class));
        verify(deliveryMapper, never()).insert(any());
    }

    @Test
    void createTxNotificationShouldCreateDeliveryWhenPushEnabled() {
        when(recipientResolver.txRecipients(42L)).thenReturn(Set.of(42L));
        MoneyTransactionCreatedEvent event = new MoneyTransactionCreatedEvent(10L, 42L, 99L,
                "IN", "MONTHLY_RULE", new BigDecimal("20.00"),
                new BigDecimal("20.00"), 8L, null);

        service(properties(true, true)).createTxNotification(event);

        verify(notificationMapper).insert(any(Notification.class));
        verify(deliveryMapper).insert(any(NotificationDelivery.class));
    }

    @Test
    void createShouldNoopWhenDisabled() {
        MoneyTransactionCreatedEvent event = new MoneyTransactionCreatedEvent(10L, 42L, 99L,
                "IN", "MANUAL_ADD", new BigDecimal("50.00"),
                new BigDecimal("150.00"), 7L, null);

        service(properties(false, false)).createTxNotification(event);

        verifyNoInteractions(notificationMapper, deliveryMapper, recipientResolver, auditService);
    }

    @Test
    void createLowBalanceNotificationShouldReachOwnerAndParents() {
        when(recipientResolver.ownerAndParents(10L, 42L)).thenReturn(Set.of(42L, 99L));
        when(familyService.resolveNickname(42L)).thenReturn("小明");

        service(properties(true, false)).createLowBalanceNotification(10L, 42L,
                new BigDecimal("4.00"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Notification::getType)
                .containsOnly("LOW_BALANCE");
        assertThat(captor.getAllValues())
                .extracting(Notification::getUserId)
                .containsExactlyInAnyOrder(42L, 99L);
    }

    @Test
    void createRuleExpiredNotificationShouldAnchorToRuleBizRef() {
        when(recipientResolver.ownerAndParents(10L, 2L)).thenReturn(Set.of(2L, 99L));
        RuleArchivedEvent event = new RuleArchivedEvent(10L, 2L, 7L, "每周零花钱", "2026-07");

        service(properties(true, false)).createRuleExpiredNotification(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Notification::getType)
                .containsOnly("RULE_EXPIRED");
        assertThat(captor.getAllValues())
                .extracting(Notification::getBizRefType)
                .containsOnly("MONEY_RULE");
        assertThat(captor.getAllValues())
                .extracting(Notification::getBizRefId)
                .containsOnly(7L);
    }

    @Test
    void markReadShouldThrow700001WhenNotOwnedOrMissing() {
        when(notificationMapper.markRead(5L, 42L)).thenReturn(0);

        assertThatThrownBy(() -> service(properties(true, false)).markRead(5L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode())
                        .isEqualTo(700001));
    }

    @Test
    void markReadShouldSucceedWhenOwned() {
        when(notificationMapper.markRead(5L, 42L)).thenReturn(1);

        service(properties(true, false)).markRead(5L, 42L);

        verify(notificationMapper).markRead(5L, 42L);
    }

    @Test
    void pageShouldClampSizeAndDelegate() {
        when(notificationMapper.countByUser(42L)).thenReturn(5L);
        when(notificationMapper.findPage(42L, 50, 0)).thenReturn(List.of());

        NotificationPageResponse response = service(properties(true, false)).page(42L, 0, 999);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(50);
        assertThat(response.total()).isEqualTo(5L);
        verify(notificationMapper).findPage(42L, 50, 0);
    }

    @Test
    void unreadCountAndMarkAllReadShouldDelegate() {
        when(notificationMapper.countUnread(42L)).thenReturn(3L);

        assertThat(service(properties(true, false)).unreadCount(42L)).isEqualTo(3L);
        service(properties(true, false)).markAllRead(42L);

        verify(notificationMapper).markAllRead(42L);
    }
}
