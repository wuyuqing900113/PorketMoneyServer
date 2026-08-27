package wyq.pocket.money.rule.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.rule.mapper.MoneyRuleMapper;
import wyq.pocket.money.rule.service.RuleSettlementService;

/**
 * 规则定时任务委托测试（M2 设计 §13）：结算 Job 委托结算引擎；
 * 到期归档 Job 以当前业务月份调用 archiveExpired（固定 Clock）。
 */
class RuleJobTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 8, 19).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(),
            ClockConfig.BUSINESS_ZONE);

    @Test
    void settlementJobShouldDelegate() {
        RuleSettlementService settlementService = mock(RuleSettlementService.class);

        new RuleSettlementJob(settlementService).run();

        verify(settlementService).settleDueRules();
    }

    @Test
    void expiryJobShouldArchiveExpiredWithCurrentMonth() {
        MoneyRuleMapper ruleMapper = mock(MoneyRuleMapper.class);

        new RuleExpiryJob(ruleMapper, CLOCK).run();

        verify(ruleMapper).archiveExpired("2026-08");
    }
}
