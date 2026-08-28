package wyq.pocket.money.rule.job;

import java.time.Clock;
import java.time.YearMonth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import wyq.pocket.money.rule.domain.MoneyRule;
import wyq.pocket.money.rule.event.RuleArchivedEvent;
import wyq.pocket.money.rule.mapper.MoneyRuleMapper;

/**
 * 规则到期归档任务（M2 设计 §7.2 / M5 设计 §6.1）：默认每日 01:23，
 * end_month 早于当月的 ACTIVE / PAUSED 规则逐条归档，归档成功后发布
 * {@link RuleArchivedEvent}（通知模块监听，家长提醒）。
 */
@Component
@ConditionalOnProperty(name = "pocket-money.money.settlement.enabled",
        havingValue = "true", matchIfMissing = true)
public class RuleExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(RuleExpiryJob.class);

    private final MoneyRuleMapper ruleMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param ruleMapper     规则 Mapper
     * @param eventPublisher 领域事件发布器（规则到期归档）
     * @param clock          时钟（业务时区）
     */
    public RuleExpiryJob(MoneyRuleMapper ruleMapper, ApplicationEventPublisher eventPublisher,
                         Clock clock) {
        this.ruleMapper = ruleMapper;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 定时归档到期规则，逐条发布规则到期事件。
     */
    @Scheduled(cron = "${pocket-money.rule.expiry-cron:0 23 1 * * *}")
    public void run() {
        String month = YearMonth.now(clock).toString();
        int archived = 0;
        for (MoneyRule rule : ruleMapper.findExpired(month)) {
            if (ruleMapper.archiveById(rule.getId()) == 1) {
                eventPublisher.publishEvent(new RuleArchivedEvent(rule.getFamilyId(),
                        rule.getBeneficiaryUserId(), rule.getId(), rule.getRuleName(),
                        rule.getEndMonth()));
                archived++;
            }
        }
        LOG.info("RULE_EXPIRY_SUMMARY month={} archived={}", month, archived);
    }
}
