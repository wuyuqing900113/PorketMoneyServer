package wyq.pocket.money.rule.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.rule.domain.MoneyRule;
import wyq.pocket.money.rule.event.RuleArchivedEvent;
import wyq.pocket.money.rule.mapper.MoneyRuleMapper;
import wyq.pocket.money.rule.service.RuleSettlementService;

/**
 * 规则定时任务委托测试（M2 设计 §13 / M5 设计 §6.1）：结算 Job 委托结算引擎；
 * 到期归档 Job 以当前业务月份查询到期规则、逐条归档并发布规则到期事件。
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
    void expiryJobShouldArchiveExpiredAndPublishEvents() {
        MoneyRuleMapper ruleMapper = mock(MoneyRuleMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        MoneyRule expired = new MoneyRule();
        expired.setId(7L);
        expired.setFamilyId(10L);
        expired.setBeneficiaryUserId(2L);
        expired.setRuleName("每周零花钱");
        expired.setEndMonth("2026-07");
        when(ruleMapper.findExpired("2026-08")).thenReturn(List.of(expired));
        when(ruleMapper.archiveById(7L)).thenReturn(1);

        new RuleExpiryJob(ruleMapper, eventPublisher, CLOCK).run();

        verify(ruleMapper).archiveById(7L);
        verify(eventPublisher).publishEvent(any(RuleArchivedEvent.class));
    }

    @Test
    void expiryJobShouldSkipAlreadyArchivedRule() {
        MoneyRuleMapper ruleMapper = mock(MoneyRuleMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        MoneyRule expired = new MoneyRule();
        expired.setId(7L);
        expired.setFamilyId(10L);
        expired.setBeneficiaryUserId(2L);
        expired.setRuleName("每周零花钱");
        expired.setEndMonth("2026-07");
        when(ruleMapper.findExpired("2026-08")).thenReturn(List.of(expired));
        when(ruleMapper.archiveById(7L)).thenReturn(0);

        new RuleExpiryJob(ruleMapper, eventPublisher, CLOCK).run();

        verify(ruleMapper).archiveById(7L);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
