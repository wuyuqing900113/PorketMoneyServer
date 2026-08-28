package wyq.pocket.money.notify.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.notify.config.NotifyProperties;
import wyq.pocket.money.notify.dto.PendingDelivery;
import wyq.pocket.money.notify.mapper.NotificationDeliveryMapper;
import wyq.pocket.money.notify.service.push.PushPort;

/**
 * 通知投递重试引擎（M5 设计 §7.2）：PENDING → SENT / 退避重试 / DEAD。
 *
 * <p>无 {@code @Transactional}：每条 mapper 语句自动提交，单条投递失败
 * 不回滚其余；{@code PushPort.send} 以 try-catch 包裹（异常视同失败）。
 * 成功审计 {@link AuditAction#NOTIFY_DELIVERED}，重试耗尽审计
 * {@link AuditAction#NOTIFY_DELIVERY_FAILED}。
 */
@Component
public class NotificationRelayService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationRelayService.class);

    /** 单批扫描上限。 */
    private static final int BATCH_SIZE = 100;

    /** last_error 列长度上限（VARCHAR(256)）。 */
    private static final int MAX_ERROR_LENGTH = 256;

    private final NotificationDeliveryMapper deliveryMapper;

    private final PushPort pushPort;

    private final AuditService auditService;

    private final NotifyProperties properties;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param deliveryMapper 投递记录 Mapper
     * @param pushPort       推送端口
     * @param auditService   审计服务
     * @param properties     通知配置（退避 / 上限）
     * @param clock          时钟（业务时区）
     */
    public NotificationRelayService(NotificationDeliveryMapper deliveryMapper, PushPort pushPort,
                                    AuditService auditService, NotifyProperties properties,
                                    Clock clock) {
        this.deliveryMapper = deliveryMapper;
        this.pushPort = pushPort;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 排空一批待投递记录。
     *
     * @return 处理条数
     */
    public int drainPending() {
        Instant now = Instant.now(clock);
        int processed = 0;
        for (PendingDelivery delivery : deliveryMapper.findPendingDeliveries(now, BATCH_SIZE)) {
            deliver(delivery);
            processed++;
        }
        return processed;
    }

    private void deliver(PendingDelivery delivery) {
        String error;
        try {
            if (pushPort.send(delivery.notificationId(), delivery.userId(),
                    delivery.title(), delivery.content())) {
                deliveryMapper.markSent(delivery.deliveryId());
                auditService.record(new AuditEntry(delivery.userId(), AuditAction.NOTIFY_DELIVERED,
                        "NOTIFICATION_DELIVERY", String.valueOf(delivery.deliveryId()), null));
                LOG.info("NOTIFY_SENT deliveryId={} notificationId={}",
                        delivery.deliveryId(), delivery.notificationId());
                return;
            }
            error = "PUSH_SEND_REJECTED";
        } catch (RuntimeException e) {
            error = truncate(e.getMessage());
            LOG.error("NOTIFY_PUSH_ERROR deliveryId={} notificationId={}",
                    delivery.deliveryId(), delivery.notificationId(), e);
        }
        int retryCount = delivery.retryCount() + 1;
        if (retryCount < properties.relay().maxRetry()) {
            deliveryMapper.scheduleRetry(delivery.deliveryId(), retryCount,
                    nextRetryAt(retryCount), error);
            LOG.info("NOTIFY_RETRY deliveryId={} retryCount={}",
                    delivery.deliveryId(), retryCount);
        } else {
            deliveryMapper.markDead(delivery.deliveryId(), retryCount, error);
            auditService.record(new AuditEntry(delivery.userId(), AuditAction.NOTIFY_DELIVERY_FAILED,
                    "NOTIFICATION_DELIVERY", String.valueOf(delivery.deliveryId()), null));
            LOG.warn("NOTIFY_DEAD deliveryId={} retryCount={}",
                    delivery.deliveryId(), retryCount);
        }
    }

    private Instant nextRetryAt(int retryCount) {
        long multiplier = 1L << (retryCount - 1);
        return Instant.now(clock).plus(properties.relay().retryBackoff().multipliedBy(multiplier));
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
