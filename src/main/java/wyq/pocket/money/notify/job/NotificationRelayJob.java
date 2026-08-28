package wyq.pocket.money.notify.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import wyq.pocket.money.notify.service.NotificationRelayService;

/**
 * 通知投递任务（M5 设计 §7.2）：默认每日 02:17，排空 PENDING delivery 行。
 */
@Component
@ConditionalOnProperty(name = "pocket-money.notify.relay.enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationRelayJob {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationRelayJob.class);

    private final NotificationRelayService relayService;

    /**
     * 注入投递引擎。
     *
     * @param relayService 投递重试引擎
     */
    public NotificationRelayJob(NotificationRelayService relayService) {
        this.relayService = relayService;
    }

    /**
     * 定时排空待投递记录。
     */
    @Scheduled(cron = "${pocket-money.notify.relay.cron:0 17 2 * * *}")
    public void run() {
        int processed = relayService.drainPending();
        LOG.info("NOTIFY_RELAY_SUMMARY processed={}", processed);
    }
}
