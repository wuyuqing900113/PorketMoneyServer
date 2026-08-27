package wyq.pocket.money.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxRefType;
import wyq.pocket.money.money.service.AccountTransactionService;
import wyq.pocket.money.money.service.TxCommand;
import wyq.pocket.money.rule.domain.MoneyRule;
import wyq.pocket.money.rule.domain.RuleGrantRecord;
import wyq.pocket.money.rule.mapper.RuleGrantRecordMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 单条规则发放执行器单元测试（M2 设计 §7.2）：
 * 幂等锚点（uk rule_id+grant_month，DuplicateKey 跳过）、非成员跳过（宁漏勿错）、
 * 发放成功回填 transaction_id 与系统审计。
 */
class RuleGrantExecutorTest {

    private static final String MONTH = "2026-08";

    private final RuleGrantRecordMapper grantRecordMapper = mock(RuleGrantRecordMapper.class);

    private final AccountTransactionService accountTransactionService =
            mock(AccountTransactionService.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final AuditService auditService = mock(AuditService.class);

    private final RuleGrantExecutor executor = new RuleGrantExecutor(grantRecordMapper,
            accountTransactionService, familyAccessChecker, auditService);

    private MoneyRule rule() {
        MoneyRule rule = new MoneyRule();
        rule.setId(30L);
        rule.setFamilyId(10L);
        rule.setBeneficiaryUserId(2L);
        rule.setRuleName("月考奖励");
        rule.setAmount(new BigDecimal("30.00"));
        rule.setStatus(MoneyRule.STATUS_ACTIVE);
        return rule;
    }

    @Test
    void settleShouldSkipWhenBeneficiaryNotMember() {
        when(familyAccessChecker.isMember(10L, 2L)).thenReturn(false);

        boolean granted = executor.settle(rule(), MONTH);

        assertThat(granted).isFalse();
        verify(grantRecordMapper, never()).insert(any(RuleGrantRecord.class));
        verify(accountTransactionService, never()).apply(any(TxCommand.class));
    }

    @Test
    void settleShouldSkipOnDuplicateGrantRecord() {
        when(familyAccessChecker.isMember(10L, 2L)).thenReturn(true);
        doThrow(new DuplicateKeyException("uk_rule_grant_rule_month"))
                .when(grantRecordMapper).insert(any(RuleGrantRecord.class));

        boolean granted = executor.settle(rule(), MONTH);

        assertThat(granted).isFalse();
        verify(accountTransactionService, never()).apply(any(TxCommand.class));
        verify(auditService, never()).record(any(AuditEntry.class));
    }

    @Test
    void settleShouldGrantApplyAndBackfillTransactionId() {
        when(familyAccessChecker.isMember(10L, 2L)).thenReturn(true);
        // 模拟 MyBatis useGeneratedKeys 回填主键
        doAnswer(invocation -> {
            RuleGrantRecord record = invocation.getArgument(0);
            record.setId(55L);
            return 1;
        }).when(grantRecordMapper).insert(any(RuleGrantRecord.class));
        MoneyTransaction tx = new MoneyTransaction();
        tx.setId(66L);
        when(accountTransactionService.apply(any(TxCommand.class))).thenReturn(tx);

        boolean granted = executor.settle(rule(), MONTH);

        assertThat(granted).isTrue();
        ArgumentCaptor<TxCommand> captor = ArgumentCaptor.forClass(TxCommand.class);
        verify(accountTransactionService).apply(captor.capture());
        TxCommand cmd = captor.getValue();
        assertThat(cmd.familyId()).isEqualTo(10L);
        assertThat(cmd.userId()).isEqualTo(2L);
        assertThat(cmd.bizType()).isEqualTo(TxBizType.MONTHLY_RULE);
        assertThat(cmd.refType()).isEqualTo(TxRefType.RULE_GRANT);
        assertThat(cmd.refId()).isEqualTo(55L);
        assertThat(cmd.operatorUserId()).isNull();
        assertThat(cmd.amount()).isEqualByComparingTo("30.00");
        assertThat(cmd.remark()).contains("月考奖励").contains(MONTH);
        verify(grantRecordMapper).updateTransactionId(55L, 66L);
        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().userId()).isNull();
        assertThat(auditCaptor.getValue().action())
                .isEqualTo(AuditAction.RULE_GRANT_EXECUTED);
        assertThat(auditCaptor.getValue().detail()).contains("month=2026-08");
    }
}
