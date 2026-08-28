package wyq.pocket.money.notify.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.money.event.MoneyTransactionCreatedEvent;
import wyq.pocket.money.notify.config.NotifyProperties;
import wyq.pocket.money.notify.domain.DeliveryStatus;
import wyq.pocket.money.notify.domain.Notification;
import wyq.pocket.money.notify.domain.NotificationDelivery;
import wyq.pocket.money.notify.dto.NotificationItemResponse;
import wyq.pocket.money.notify.dto.NotificationPageResponse;
import wyq.pocket.money.notify.dto.NotifyErrorCode;
import wyq.pocket.money.notify.mapper.NotificationDeliveryMapper;
import wyq.pocket.money.notify.mapper.NotificationMapper;
import wyq.pocket.money.rule.event.RuleArchivedEvent;
import wyq.pocket.money.user.service.FamilyService;

/**
 * 通知服务（M5 设计 §5）：站内信创建 / 查询 / 已读，外部通道 delivery 行生成。
 *
 * <p>创建方法 {@code @Transactional}：请求路径 join 记账事务（同提交同回滚），
 * Job 路径自开事务（§6.3）；创建站内信即审计 {@link AuditAction#NOTIFY_DELIVERED}，
 * 外部通道 enabled 时另落 PENDING delivery 行由 relay 异步投递（§7.2）。
 * 金额 / 归属校验走 {@link NotifyErrorCode}（700001 段）。
 */
@Component
public class NotificationService {

    /** 业务锚点类型：流水（§9.1）。 */
    private static final String BIZ_REF_TRANSACTION = "MONEY_TRANSACTION";

    /** 业务锚点类型：规则（§9.1）。 */
    private static final String BIZ_REF_RULE = "MONEY_RULE";

    /** 最大页大小（§5.4：页长上限 50）。 */
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationMapper notificationMapper;

    private final NotificationDeliveryMapper deliveryMapper;

    private final NotificationTemplateService templateService;

    private final NotifyRecipientResolver recipientResolver;

    private final FamilyService familyService;

    private final AuditService auditService;

    private final NotifyProperties properties;

    /**
     * 注入协作对象。
     *
     * @param notificationMapper 通知 Mapper
     * @param deliveryMapper     投递记录 Mapper
     * @param templateService    文案模板
     * @param recipientResolver  接收人解析
     * @param familyService      家庭服务（昵称解析）
     * @param auditService       审计服务
     * @param properties         通知配置
     */
    public NotificationService(NotificationMapper notificationMapper,
                               NotificationDeliveryMapper deliveryMapper,
                               NotificationTemplateService templateService,
                               NotifyRecipientResolver recipientResolver,
                               FamilyService familyService, AuditService auditService,
                               NotifyProperties properties) {
        this.notificationMapper = notificationMapper;
        this.deliveryMapper = deliveryMapper;
        this.templateService = templateService;
        this.recipientResolver = recipientResolver;
        this.familyService = familyService;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * 账务变动通知（§6.2）：入账 TX_IN / 出账 TX_OUT，接收人 = 账户主人。
     *
     * @param event 记账成功事件
     */
    @Transactional
    public void createTxNotification(MoneyTransactionCreatedEvent event) {
        if (!properties.enabled()) {
            return;
        }
        boolean inbound = "IN".equals(event.direction());
        NotificationType type = inbound ? NotificationType.TX_IN : NotificationType.TX_OUT;
        NotificationTemplateService.Template template = inbound
                ? templateService.txIn(event.bizType(), event.amount())
                : templateService.txOut(event.amount());
        for (long recipientId : recipientResolver.txRecipients(event.userId())) {
            insertFor(event.familyId(), recipientId, type, template, BIZ_REF_TRANSACTION,
                    event.transactionId());
        }
    }

    /**
     * 余额不足提醒（§6.2）：接收人 = 账户主人 + 家庭全部家长。
     *
     * @param familyId     家庭 ID
     * @param userId       账户主人用户 ID
     * @param balanceAfter 记账后余额
     */
    @Transactional
    public void createLowBalanceNotification(long familyId, long userId,
                                             BigDecimal balanceAfter) {
        if (!properties.enabled()) {
            return;
        }
        String nickname = familyService.resolveNickname(userId);
        NotificationTemplateService.Template template = templateService.lowBalance(
                nickname == null ? "" : nickname, balanceAfter);
        for (long recipientId : recipientResolver.ownerAndParents(familyId, userId)) {
            insertFor(familyId, recipientId, NotificationType.LOW_BALANCE, template, null, null);
        }
    }

    /**
     * 规则到期通知（§6.2）：接收人 = 受益人 + 家庭全部家长。
     *
     * @param event 规则到期归档事件
     */
    @Transactional
    public void createRuleExpiredNotification(RuleArchivedEvent event) {
        if (!properties.enabled()) {
            return;
        }
        NotificationTemplateService.Template template = templateService.ruleExpired(
                event.ruleName(), event.endMonth());
        for (long recipientId : recipientResolver.ownerAndParents(event.familyId(),
                event.beneficiaryUserId())) {
            insertFor(event.familyId(), recipientId, NotificationType.RULE_EXPIRED, template,
                    BIZ_REF_RULE, event.ruleId());
        }
    }

    /**
     * 本人通知分页（未读优先，page / size 越界统一钳制）。
     *
     * @param userId 接收人用户 ID
     * @param page   页码（<1 按 1）
     * @param size   页大小（上限 50）
     * @return 分页通知
     */
    public NotificationPageResponse page(long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = notificationMapper.countByUser(userId);
        List<NotificationItemResponse> records = notificationMapper
                .findPage(userId, safeSize, (safePage - 1) * safeSize).stream()
                .map(NotificationItemResponse::from).toList();
        return new NotificationPageResponse(records, total, safePage, safeSize);
    }

    /**
     * 本人未读数。
     *
     * @param userId 接收人用户 ID
     * @return 未读数
     */
    public long unreadCount(long userId) {
        return notificationMapper.countUnread(userId);
    }

    /**
     * 标记单条已读（幂等，校验归属本人，越权 / 不存在 700001）。
     *
     * @param id     通知 ID
     * @param userId 接收人用户 ID
     * @throws BusinessException 700001 通知不存在或非本人
     */
    @Transactional
    public void markRead(long id, long userId) {
        if (notificationMapper.markRead(id, userId) == 0) {
            throw new BusinessException(NotifyErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    /**
     * 全部标记已读（仅限本人未读通知）。
     *
     * @param userId 接收人用户 ID
     */
    @Transactional
    public void markAllRead(long userId) {
        notificationMapper.markAllRead(userId);
    }

    /**
     * 落一条站内信 + 审计 NOTIFY_DELIVERED；外部通道 enabled 时另落 PENDING delivery 行。
     */
    private void insertFor(long familyId, long userId, NotificationType type,
                           NotificationTemplateService.Template template, String bizRefType,
                           Long bizRefId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setFamilyId(familyId);
        notification.setType(type.name());
        notification.setTitle(template.title());
        notification.setContent(template.content());
        notification.setBizRefType(bizRefType);
        notification.setBizRefId(bizRefId);
        notificationMapper.insert(notification);
        auditService.record(new AuditEntry(userId, AuditAction.NOTIFY_DELIVERED,
                "NOTIFICATION", String.valueOf(notification.getId()), null));
        if (properties.push().enabled()) {
            NotificationDelivery delivery = new NotificationDelivery();
            delivery.setNotificationId(notification.getId());
            delivery.setChannel(NotificationDelivery.CHANNEL_PUSH);
            delivery.setStatus(DeliveryStatus.PENDING.name());
            deliveryMapper.insert(delivery);
        }
    }
}
