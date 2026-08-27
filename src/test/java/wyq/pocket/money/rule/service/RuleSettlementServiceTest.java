package wyq.pocket.money.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.rule.domain.MoneyRule;
import wyq.pocket.money.rule.mapper.MoneyRuleMapper;

/**
 * 结算引擎单元测试（M2 设计 §7.2 D3）：固定 Clock 下按
 * findDueRules(dayOfMonth, month) 扫描、返回实发条数、单条失败隔离不影响其余。
 */
class RuleSettlementServiceTest {

    /** 固定时钟：2026-08-19。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(), ClockConfig.BUSINESS_ZONE);

    private final MoneyRuleMapper ruleMapper = mock(MoneyRuleMapper.class);

    private final RuleGrantExecutor grantExecutor = mock(RuleGrantExecutor.class);

    private final RuleSettlementService service =
            new RuleSettlementService(ruleMapper, grantExecutor, CLOCK);

    private MoneyRule rule(long id) {
        MoneyRule rule = new MoneyRule();
        rule.setId(id);
        rule.setFamilyId(10L);
        rule.setBeneficiaryUserId(2L);
        rule.setRuleName("规则" + id);
        rule.setAmount(new BigDecimal("10.00"));
        rule.setStatus(MoneyRule.STATUS_ACTIVE);
        return rule;
    }

    @Test
    void settleDueRulesShouldScanWithTodayAndCurrentMonth() {
        when(ruleMapper.findDueRules(19, "2026-08")).thenReturn(List.of());

        int granted = service.settleDueRules();

        verify(ruleMapper).findDueRules(19, "2026-08");
        assertThat(granted).isZero();
    }

    @Test
    void settleDueRulesShouldCountGrantedOnly() {
        MoneyRule first = rule(1L);
        MoneyRule second = rule(2L);
        MoneyRule third = rule(3L);
        when(ruleMapper.findDueRules(19, "2026-08"))
                .thenReturn(List.of(first, second, third));
        when(grantExecutor.settle(first, "2026-08")).thenReturn(true);
        // 已发放跳过：不计入
        when(grantExecutor.settle(second, "2026-08")).thenReturn(false);
        when(grantExecutor.settle(third, "2026-08")).thenReturn(true);

        int granted = service.settleDueRules();

        assertThat(granted).isEqualTo(2);
        verify(grantExecutor).settle(eq(first), eq("2026-08"));
        verify(grantExecutor).settle(eq(second), eq("2026-08"));
        verify(grantExecutor).settle(eq(third), eq("2026-08"));
    }

    @Test
    void settleDueRulesShouldIsolateSingleRuleFailure() {
        MoneyRule failing = rule(1L);
        MoneyRule ok = rule(2L);
        when(ruleMapper.findDueRules(19, "2026-08")).thenReturn(List.of(failing, ok));
        when(grantExecutor.settle(failing, "2026-08"))
                .thenThrow(new RuntimeException("db down"));
        when(grantExecutor.settle(ok, "2026-08")).thenReturn(true);

        int granted = service.settleDueRules();

        // 失败仅日志告警，不影响其余规则：两条规则各结算一次
        assertThat(granted).isEqualTo(1);
        verify(grantExecutor).settle(eq(failing), eq("2026-08"));
        verify(grantExecutor).settle(eq(ok), eq("2026-08"));
    }
}
