package wyq.pocket.money.rule.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import wyq.pocket.money.rule.service.RuleSettlementService;

/**
 * 包月规则结算任务（M2 设计 §7.2 / §13）：默认每日 01:07。
 *
 * <p>开关 pocket-money.money.settlement.enabled（集成测试置 false）；
 * cron 可配置 pocket-money.money.settlement.cron。
 */
@Component
@ConditionalOnProperty(name = "pocket-money.money.settlement.enabled",
        havingValue = "true", matchIfMissing = true)
public class RuleSettlementJob {

    private static final Logger LOG = LoggerFactory.getLogger(RuleSettlementJob.class);

    private final RuleSettlementService settlementService;

    /**
     * 注入结算引擎。
     *
     * @param settlementService 结算引擎
     */
    public RuleSettlementJob(RuleSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * 定时结算。
     */
    @Scheduled(cron = "${pocket-money.money.settlement.cron:0 7 1 * * *}")
    public void run() {
        LOG.info("SETTLEMENT_JOB_START");
        settlementService.settleDueRules();
        LOG.info("SETTLEMENT_JOB_END");
    }
}
