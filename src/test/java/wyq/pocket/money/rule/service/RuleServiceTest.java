package wyq.pocket.money.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.rule.domain.MoneyRule;
import wyq.pocket.money.rule.dto.CreateRuleRequest;
import wyq.pocket.money.rule.dto.RuleResponse;
import wyq.pocket.money.rule.dto.UpdateRuleRequest;
import wyq.pocket.money.rule.mapper.MoneyRuleMapper;
import wyq.pocket.money.rule.mapper.RuleGrantRecordMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.UserService;

/**
 * 包月规则 CRUD 单元测试（M2 设计 §7 / §12.1）：
 * 月份顺序 100001、重名 400006、上限 400004、状态机 400002、
 * 有发放记录禁删 400005、不存在 / 跨家庭 400001、当月已发放标记。
 */
class RuleServiceTest {

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, 10L, "PARENT", false);

    private static final long RULE_ID = 30L;

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 8, 19).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(),
            ClockConfig.BUSINESS_ZONE);

    private final MoneyRuleMapper ruleMapper = mock(MoneyRuleMapper.class);

    private final RuleGrantRecordMapper grantRecordMapper = mock(RuleGrantRecordMapper.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final UserService userService = mock(UserService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final RuleService service = new RuleService(ruleMapper, grantRecordMapper,
            familyAccessChecker, userService, auditService, CLOCK, 10);

    private MoneyRule rule(String status) {
        MoneyRule rule = new MoneyRule();
        rule.setId(RULE_ID);
        rule.setFamilyId(10L);
        rule.setBeneficiaryUserId(2L);
        rule.setRuleName("月考奖励");
        rule.setAmount(new BigDecimal("30.00"));
        rule.setGrantDay(1);
        rule.setStatus(status);
        rule.setStartMonth("2026-08");
        rule.setEndMonth("2026-12");
        rule.setCreatedBy(1L);
        return rule;
    }

    private CreateRuleRequest createRequest(String startMonth, String endMonth) {
        return new CreateRuleRequest(2L, "月考奖励", new BigDecimal("30.00"), 1,
                startMonth, endMonth, null);
    }

    private void expectCode(Throwable thrown, int code) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode().getCode()).isEqualTo(code);
    }

    @Test
    void createShouldInsertAndAudit() {
        when(ruleMapper.countByName(eq(10L), eq("月考奖励"), isNull())).thenReturn(0);
        when(ruleMapper.countUnarchivedByBeneficiary(2L)).thenReturn(0);
        // 模拟 useGeneratedKeys 主键回填（MyBatis 插入后写回 id）
        when(ruleMapper.insert(any(MoneyRule.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MoneyRule.class).setId(RULE_ID);
            return 1;
        });
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_ACTIVE));

        RuleResponse response =
                service.create(PARENT, createRequest("2026-08", "2026-12"));

        verify(ruleMapper).insert(any(MoneyRule.class));
        verify(auditService).record(any());
        assertThat(response.grantedThisMonth()).isFalse();
    }

    @Test
    void createShouldThrow100001WhenEndBeforeStart() {
        assertThatThrownBy(() -> service.create(PARENT, createRequest("2026-09", "2026-08")))
                .satisfies(thrown -> expectCode(thrown, 100001));
        verify(ruleMapper, never()).insert(any(MoneyRule.class));
    }

    @Test
    void createShouldAllowNullEndMonth() {
        when(ruleMapper.countByName(eq(10L), eq("月考奖励"), isNull())).thenReturn(0);
        when(ruleMapper.countUnarchivedByBeneficiary(2L)).thenReturn(0);
        // 模拟 useGeneratedKeys 主键回填（MyBatis 插入后写回 id）
        when(ruleMapper.insert(any(MoneyRule.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MoneyRule.class).setId(RULE_ID);
            return 1;
        });
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_ACTIVE));

        service.create(PARENT, createRequest("2026-08", null));

        verify(ruleMapper).insert(any(MoneyRule.class));
    }

    @Test
    void createShouldThrow400006OnDuplicateName() {
        when(ruleMapper.countByName(eq(10L), eq("月考奖励"), isNull())).thenReturn(1);

        assertThatThrownBy(() -> service.create(PARENT, createRequest("2026-08", null)))
                .satisfies(thrown -> expectCode(thrown, 400006));
    }

    @Test
    void createShouldThrow400004AtLimit() {
        when(ruleMapper.countByName(eq(10L), eq("月考奖励"), isNull())).thenReturn(0);
        when(ruleMapper.countUnarchivedByBeneficiary(2L)).thenReturn(10);

        assertThatThrownBy(() -> service.create(PARENT, createRequest("2026-08", null)))
                .satisfies(thrown -> expectCode(thrown, 400004));
        verify(ruleMapper, never()).insert(any(MoneyRule.class));
    }

    @Test
    void pauseShouldMoveActiveToPaused() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_ACTIVE));
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of(2L, "小明"));

        service.pause(RULE_ID, PARENT);

        verify(ruleMapper).updateStatus(RULE_ID, MoneyRule.STATUS_PAUSED);
    }

    @Test
    void pauseShouldThrow400002WhenAlreadyPaused() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_PAUSED));

        assertThatThrownBy(() -> service.pause(RULE_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 400002));
        verify(ruleMapper, never()).updateStatus(any(Long.class), any());
    }

    @Test
    void resumeShouldThrow400002WhenActive() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_ACTIVE));

        assertThatThrownBy(() -> service.resume(RULE_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 400002));
    }

    @Test
    void archiveShouldThrow400002WhenAlreadyArchived() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_ARCHIVED));

        assertThatThrownBy(() -> service.archive(RULE_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 400002));
    }

    @Test
    void archiveShouldAcceptPausedRule() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_PAUSED));
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of(2L, "小明"));

        service.archive(RULE_ID, PARENT);

        verify(ruleMapper).updateStatus(RULE_ID, MoneyRule.STATUS_ARCHIVED);
    }

    @Test
    void deleteShouldThrow400005WhenGrantsExist() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_ACTIVE));
        when(grantRecordMapper.countByRule(RULE_ID)).thenReturn(2);

        assertThatThrownBy(() -> service.delete(RULE_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 400005));
        verify(ruleMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteShouldRemoveWhenNoGrants() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_ACTIVE));
        when(grantRecordMapper.countByRule(RULE_ID)).thenReturn(0);

        service.delete(RULE_ID, PARENT);

        verify(ruleMapper).deleteById(RULE_ID);
    }

    @Test
    void missingRuleShouldThrow400001() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.pause(RULE_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 400001));
    }

    @Test
    void ruleOfOtherFamilyShouldThrow400001() {
        MoneyRule foreign = rule(MoneyRule.STATUS_ACTIVE);
        foreign.setFamilyId(99L);
        when(ruleMapper.findById(RULE_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> service.delete(RULE_ID, PARENT))
                .satisfies(thrown -> expectCode(thrown, 400001));
    }

    @Test
    void listShouldMarkGrantedThisMonth() {
        MoneyRule grantedRule = rule(MoneyRule.STATUS_ACTIVE);
        MoneyRule pendingRule = rule(MoneyRule.STATUS_ACTIVE);
        pendingRule.setId(31L);
        when(ruleMapper.findListByFamily(10L)).thenReturn(List.of(grantedRule, pendingRule));
        when(grantRecordMapper.findGrantedRuleIds(10L, "2026-08"))
                .thenReturn(List.of(RULE_ID));
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of(2L, "小明"));

        List<RuleResponse> responses = service.list(PARENT);

        assertThat(responses.get(0).grantedThisMonth()).isTrue();
        assertThat(responses.get(1).grantedThisMonth()).isFalse();
    }

    @Test
    void updateShouldKeepStartMonthAndCheckName() {
        MoneyRule existing = rule(MoneyRule.STATUS_ACTIVE);
        when(ruleMapper.findById(RULE_ID)).thenReturn(existing);
        when(ruleMapper.countByName(eq(10L), eq("新名字"), eq(RULE_ID))).thenReturn(0);
        when(userService.findNicknameMap(anySet())).thenReturn(Map.of(2L, "小明"));

        service.update(RULE_ID, PARENT, new UpdateRuleRequest("新名字",
                new BigDecimal("50.00"), 5, "2026-12", null));

        verify(ruleMapper).update(eq(RULE_ID), eq("新名字"), eq(new BigDecimal("50.00")),
                eq(5), eq("2026-12"), isNull());
    }

    @Test
    void updateShouldThrow100001WhenEndBeforeExistingStart() {
        when(ruleMapper.findById(RULE_ID)).thenReturn(rule(MoneyRule.STATUS_ACTIVE));

        assertThatThrownBy(() -> service.update(RULE_ID, PARENT, new UpdateRuleRequest(
                "月考奖励", new BigDecimal("30.00"), 1, "2026-07", null)))
                .satisfies(thrown -> expectCode(thrown, 100001));
    }
}
