package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.DepositRequest;
import wyq.pocket.money.money.dto.DepositWithdrawResponse;
import wyq.pocket.money.money.dto.WithdrawRequest;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 手动存取单元测试（M2 设计 §8.2 #5–#6、附录 B 权限矩阵）：
 * 家长对任意成员账户存取、孩子仅限本人账户（AccessDeniedException → 403）、审计落库。
 */
class MoneyOperationServiceTest {

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, 10L, "PARENT", false);

    private static final UserIdPrincipal CHILD =
            new UserIdPrincipal(2L, 10L, "CHILD", false);

    private static final long TARGET_USER_ID = 2L;

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final AccountTransactionService accountTransactionService =
            mock(AccountTransactionService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final MoneyOperationService service = new MoneyOperationService(
            familyAccessChecker, accountTransactionService, auditService);

    private MoneyTransaction tx(BigDecimal balanceAfter) {
        MoneyTransaction tx = new MoneyTransaction();
        tx.setId(99L);
        tx.setAmount(new BigDecimal("5.00"));
        tx.setBalanceAfter(balanceAfter);
        return tx;
    }

    @Test
    void parentDepositShouldApplyAndAudit() {
        when(accountTransactionService.apply(any(TxCommand.class)))
                .thenReturn(tx(new BigDecimal("15.00")));

        DepositWithdrawResponse response = service.deposit(PARENT, TARGET_USER_ID,
                new DepositRequest(TARGET_USER_ID, new BigDecimal("5.00"), "压岁钱"));

        ArgumentCaptor<TxCommand> captor = ArgumentCaptor.forClass(TxCommand.class);
        verify(accountTransactionService).apply(captor.capture());
        TxCommand cmd = captor.getValue();
        assertThat(cmd.familyId()).isEqualTo(10L);
        assertThat(cmd.userId()).isEqualTo(TARGET_USER_ID);
        assertThat(cmd.direction()).isEqualTo(TxDirection.IN);
        assertThat(cmd.bizType()).isEqualTo(TxBizType.MANUAL_ADD);
        assertThat(cmd.operatorUserId()).isEqualTo(1L);
        verify(familyAccessChecker).requireMember(10L, 1L);
        verify(familyAccessChecker).requireMember(10L, TARGET_USER_ID);
        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.MONEY_DEPOSIT);
        assertThat(auditCaptor.getValue().targetId()).isEqualTo("99");
        assertThat(response.transactionId()).isEqualTo(99L);
        assertThat(response.balanceAfter()).isEqualByComparingTo("15.00");
    }

    @Test
    void parentWithdrawShouldApplyAndAudit() {
        when(accountTransactionService.apply(any(TxCommand.class)))
                .thenReturn(tx(new BigDecimal("5.00")));

        service.withdraw(PARENT, TARGET_USER_ID,
                new WithdrawRequest(TARGET_USER_ID, new BigDecimal("5.00"), "买文具"));

        ArgumentCaptor<TxCommand> captor = ArgumentCaptor.forClass(TxCommand.class);
        verify(accountTransactionService).apply(captor.capture());
        assertThat(captor.getValue().direction()).isEqualTo(TxDirection.OUT);
        assertThat(captor.getValue().bizType()).isEqualTo(TxBizType.WITHDRAW);
        verify(auditService).record(any(AuditEntry.class));
        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.MONEY_WITHDRAW);
    }

    @Test
    void childShouldOnlyOperateSelfAccount() {
        assertThatThrownBy(() -> service.deposit(CHILD, 3L,
                new DepositRequest(3L, new BigDecimal("1.00"), null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.withdraw(CHILD, 3L,
                new WithdrawRequest(3L, new BigDecimal("1.00"), null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(accountTransactionService, never()).apply(any(TxCommand.class));
    }

    @Test
    void childShouldOperateSelfAccount() {
        when(accountTransactionService.apply(any(TxCommand.class)))
                .thenReturn(tx(new BigDecimal("5.00")));

        DepositWithdrawResponse response = service.withdraw(CHILD, CHILD.userId(),
                new WithdrawRequest(CHILD.userId(), new BigDecimal("5.00"), null));

        verify(accountTransactionService).apply(any(TxCommand.class));
        assertThat(response.userId()).isEqualTo(CHILD.userId());
    }

    @Test
    void depositShouldCheckMembershipOfBothParties() {
        when(accountTransactionService.apply(any(TxCommand.class)))
                .thenReturn(tx(new BigDecimal("5.00")));

        service.deposit(PARENT, TARGET_USER_ID,
                new DepositRequest(TARGET_USER_ID, new BigDecimal("5.00"), null));

        verify(familyAccessChecker).requireMember(eq(10L), eq(1L));
        verify(familyAccessChecker).requireMember(eq(10L), eq(TARGET_USER_ID));
    }
}
