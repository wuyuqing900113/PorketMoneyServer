package wyq.pocket.money.money.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import wyq.pocket.money.money.service.ReconciliationService;

/**
 * 对账定时任务（M2 设计 §4.4 / §13）：默认每周日 02:13 全量对账。
 *
 * <p>cron 可配置 pocket-money.money.reconcile.cron。
 */
@Component
public class ReconciliationJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationService reconciliationService;

    /**
     * 注入对账服务。
     *
     * @param reconciliationService 对账服务
     */
    public ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * 定时执行对账。
     */
    @Scheduled(cron = "${pocket-money.money.reconcile.cron:0 13 2 * * 0}")
    public void run() {
        LOG.info("RECONCILE_JOB_START");
        reconciliationService.reconcile();
        LOG.info("RECONCILE_JOB_END");
    }
}
