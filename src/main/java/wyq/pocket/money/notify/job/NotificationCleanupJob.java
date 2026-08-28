package wyq.pocket.money.notify.job;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import wyq.pocket.money.notify.config.NotifyProperties;
import wyq.pocket.money.notify.mapper.NotificationDeliveryMapper;
import wyq.pocket.money.notify.mapper.NotificationMapper;

/**
 * 已读通知清理任务（M5 设计 §7.3）：默认每日 04:47，删除超保留期的已读
 * 通知（级联删除其 delivery 行）。
 */
@Component
@ConditionalOnProperty(name = "pocket-money.notify.cleanup.enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationCleanupJob {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationMapper notificationMapper;

    private final NotificationDeliveryMapper deliveryMapper;

    private final NotifyProperties properties;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param notificationMapper 通知 Mapper
     * @param deliveryMapper     投递记录 Mapper
     * @param properties         通知配置（保留期）
     * @param clock              时钟（业务时区）
     */
    public NotificationCleanupJob(NotificationMapper notificationMapper,
                                  NotificationDeliveryMapper deliveryMapper,
                                  NotifyProperties properties, Clock clock) {
        this.notificationMapper = notificationMapper;
        this.deliveryMapper = deliveryMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 定时清理超保留期的已读通知（先删子表 delivery，再删通知）。
     */
    @Scheduled(cron = "${pocket-money.notify.cleanup.cron:0 47 4 * * *}")
    public void run() {
        Instant cutoff = Instant.now(clock).minus(properties.cleanup().readTtl());
        int deletedDeliveries = deliveryMapper.deleteByReadNotificationBefore(cutoff);
        int deletedNotifications = notificationMapper.deleteReadBefore(cutoff);
        LOG.info("NOTIFY_CLEANUP_SUMMARY deliveries={} notifications={}",
                deletedDeliveries, deletedNotifications);
    }
}
