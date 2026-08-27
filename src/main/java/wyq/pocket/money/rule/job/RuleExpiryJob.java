package wyq.pocket.money.rule.job;

import java.time.Clock;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import wyq.pocket.money.rule.mapper.MoneyRuleMapper;

/**
 * 规则到期归档任务（M2 设计 §7.2 到期维护）：默认每日 01:23，
 * end_month 早于当月的 ACTIVE / PAUSED 规则置 ARCHIVED。
 */
@Component
@ConditionalOnProperty(name = "pocket-money.money.settlement.enabled",
        havingValue = "true", matchIfMissing = true)
public class RuleExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(RuleExpiryJob.class);

    private final MoneyRuleMapper ruleMapper;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param ruleMapper 规则 Mapper
     * @param clock      时钟（业务时区）
     */
    public RuleExpiryJob(MoneyRuleMapper ruleMapper, Clock clock) {
        this.ruleMapper = ruleMapper;
        this.clock = clock;
    }

    /**
     * 定时归档到期规则。
     */
    @Scheduled(cron = "${pocket-money.rule.expiry-cron:0 23 1 * * *}")
    public void run() {
        String month = YearMonth.now(clock).toString();
        int archived = ruleMapper.archiveExpired(month);
        LOG.info("RULE_EXPIRY_SUMMARY month={} archived={}", month, archived);
    }
}
