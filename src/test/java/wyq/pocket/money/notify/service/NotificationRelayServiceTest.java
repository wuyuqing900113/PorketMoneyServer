package wyq.pocket.money.notify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.notify.config.NotifyProperties;
import wyq.pocket.money.notify.dto.PendingDelivery;
import wyq.pocket.money.notify.mapper.NotificationDeliveryMapper;
import wyq.pocket.money.notify.service.push.PushPort;

/**
 * 投递重试引擎单元测试（M5 设计 §7.2 / GA D68）：PENDING → SENT / 退避重试 / 耗尽置 DEAD；
 * 成功审计 NOTIFY_DELIVERED，耗尽审计 NOTIFY_DELIVERY_FAILED，异常视同失败，
 * 单条失败不阻断其余（无 @Transactional，逐条自提交）；用户未注册设备令牌时
 * 以 NO_PUSH_TOKEN 退避重试且不调用 PushPort。
 */
class NotificationRelayServiceTest {

    private static final Instant NOW = LocalDate.of(2026, 8, 19)
            .atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();

    private final NotificationDeliveryMapper deliveryMapper =
            mock(NotificationDeliveryMapper.class);

    private final PushPort pushPort = mock(PushPort.class);

    private final AuditService auditService = mock(AuditService.class);

    private final Clock clock = Clock.fixed(NOW, ClockConfig.BUSINESS_ZONE);

    private static NotifyProperties properties(int maxRetry) {
        return new NotifyProperties(true, new BigDecimal("5.00"),
                new NotifyProperties.Push(true, null),
                new NotifyProperties.Relay(true, "0 17 2 * * *", maxRetry,
                        Duration.ofMinutes(1)),
                new NotifyProperties.Cleanup(true, "0 47 4 * * *", Duration.ofDays(30)));
    }

    private NotificationRelayService service(int maxRetry) {
        return new NotificationRelayService(deliveryMapper, pushPort, auditService,
                properties(maxRetry), clock);
    }

    private PendingDelivery delivery(int retryCount) {
        return new PendingDelivery(10L, 100L, retryCount, 42L, "harmony-device-token",
                "零花钱入账", "你收到 50.00 元零花钱");
    }

    @Test
    void successShouldMarkSentAndAuditDelivered() {
        when(deliveryMapper.findPendingDeliveries(NOW, 100))
                .thenReturn(List.of(delivery(0)));
        when(pushPort.send(100L, 42L, "harmony-device-token",
                "零花钱入账", "你收到 50.00 元零花钱")).thenReturn(true);

        int processed = service(3).drainPending();

        assertThat(processed).isEqualTo(1);
        verify(deliveryMapper).markSent(10L);
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.NOTIFY_DELIVERED);
        verify(deliveryMapper, never()).scheduleRetry(anyLong(), any(Integer.class),
                any(Instant.class), any());
        verify(deliveryMapper, never()).markDead(anyLong(), any(Integer.class), any());
    }

    @Test
    void failureBelowMaxRetryShouldScheduleBackoffRetry() {
        when(deliveryMapper.findPendingDeliveries(NOW, 100))
                .thenReturn(List.of(delivery(0)));
        when(pushPort.send(100L, 42L, "harmony-device-token",
                "零花钱入账", "你收到 50.00 元零花钱")).thenReturn(false);

        int processed = service(3).drainPending();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<Instant> nextRetryAt = ArgumentCaptor.forClass(Instant.class);
        verify(deliveryMapper).scheduleRetry(eq(10L), eq(1), nextRetryAt.capture(),
                eq("PUSH_SEND_REJECTED"));
        assertThat(nextRetryAt.getValue()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        verify(deliveryMapper, never()).markDead(anyLong(), any(Integer.class), any());
        verify(auditService, never()).record(any(AuditEntry.class));
    }

    @Test
    void failureAtRetryLimitShouldMarkDeadAndAuditFailure() {
        when(deliveryMapper.findPendingDeliveries(NOW, 100))
                .thenReturn(List.of(delivery(2)));
        when(pushPort.send(100L, 42L, "harmony-device-token",
                "零花钱入账", "你收到 50.00 元零花钱")).thenReturn(false);

        int processed = service(3).drainPending();

        assertThat(processed).isEqualTo(1);
        verify(deliveryMapper).markDead(10L, 3, "PUSH_SEND_REJECTED");
        verify(deliveryMapper, never()).scheduleRetry(anyLong(), any(Integer.class),
                any(Instant.class), any());
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.NOTIFY_DELIVERY_FAILED);
    }

    @Test
    void exceptionShouldBeTreatedAsFailureAndTruncateLongMessage() {
        String longMessage = "e".repeat(300);
        when(deliveryMapper.findPendingDeliveries(NOW, 100))
                .thenReturn(List.of(delivery(0)));
        when(pushPort.send(anyLong(), anyLong(), any(), any(), any()))
                .thenThrow(new RuntimeException(longMessage));

        int processed = service(3).drainPending();

        assertThat(processed).isEqualTo(1);
        verify(deliveryMapper).scheduleRetry(eq(10L), eq(1), any(Instant.class),
                eq(longMessage.substring(0, 256)));
    }

    @Test
    void singleFailureShouldNotBlockOthers() {
        when(deliveryMapper.findPendingDeliveries(NOW, 100))
                .thenReturn(List.of(delivery(0),
                        new PendingDelivery(11L, 101L, 0, 43L, "harmony-device-token-2",
                                "规则到期", "规则「每周零花钱」到期")));
        when(pushPort.send(100L, 42L, "harmony-device-token",
                "零花钱入账", "你收到 50.00 元零花钱")).thenReturn(false);
        when(pushPort.send(101L, 43L, "harmony-device-token-2",
                "规则到期", "规则「每周零花钱」到期")).thenReturn(true);

        int processed = service(3).drainPending();

        assertThat(processed).isEqualTo(2);
        verify(deliveryMapper).scheduleRetry(eq(10L), eq(1), any(Instant.class),
                eq("PUSH_SEND_REJECTED"));
        verify(deliveryMapper).markSent(11L);
    }

    @Test
    void missingDeviceTokenShouldBackoffWithoutCallingPushPort() {
        when(deliveryMapper.findPendingDeliveries(NOW, 100))
                .thenReturn(List.of(new PendingDelivery(12L, 102L, 0, 44L, null,
                        "余额不足", "余额低于 5.00 元")));

        int processed = service(3).drainPending();

        assertThat(processed).isEqualTo(1);
        verify(pushPort, never()).send(anyLong(), anyLong(), any(), any(), any());
        verify(deliveryMapper).scheduleRetry(eq(12L), eq(1), any(Instant.class),
                eq("NO_PUSH_TOKEN"));
    }
}
