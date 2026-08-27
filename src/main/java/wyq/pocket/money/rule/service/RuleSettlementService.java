package wyq.pocket.money.rule.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import wyq.pocket.money.rule.domain.MoneyRule;
import wyq.pocket.money.rule.mapper.MoneyRuleMapper;

/**
 * 结算引擎（M2 设计 §7.2，D3）：每日扫描「当月应发未发即补」。
 *
 * <p>扫描条件：ACTIVE 且 grant_day ≤ 当日 且月份在生效区间内；
 * 幂等由 {@link RuleGrantExecutor} 的唯一键锚点保证，
 * 天然覆盖停机错过、月中新建规则、月末兜底。
 * 单条失败仅 ERROR 日志 + 次日重试（风险表 R3），不影响其余规则。
 */
@Component
public class RuleSettlementService {

    private static final Logger LOG = LoggerFactory.getLogger(RuleSettlementService.class);

    private final MoneyRuleMapper ruleMapper;

    private final RuleGrantExecutor grantExecutor;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param ruleMapper    规则 Mapper
     * @param grantExecutor 发放执行器
     * @param clock         时钟（业务时区）
     */
    public RuleSettlementService(MoneyRuleMapper ruleMapper, RuleGrantExecutor grantExecutor,
                                 Clock clock) {
        this.ruleMapper = ruleMapper;
        this.grantExecutor = grantExecutor;
        this.clock = clock;
    }

    /**
     * 结算全部当月应发规则。
     *
     * @return 本次实际发放条数
     */
    public int settleDueRules() {
        LocalDate today = LocalDate.now(clock);
        String month = YearMonth.from(today).toString();
        List<MoneyRule> dueRules = ruleMapper.findDueRules(today.getDayOfMonth(), month);
        int granted = 0;
        for (MoneyRule rule : dueRules) {
            granted += settleOne(rule, month);
        }
        LOG.info("SETTLE_SUMMARY month={} due={} granted={}",
                month, dueRules.size(), granted);
        return granted;
    }

    private int settleOne(MoneyRule rule, String month) {
        try {
            return grantExecutor.settle(rule, month) ? 1 : 0;
        } catch (RuntimeException e) {
            LOG.error("RULE_SETTLE_FAILED ruleId={} month={}", rule.getId(), month, e);
            return 0;
        }
    }
}
