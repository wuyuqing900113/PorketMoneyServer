package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;

/**
 * 记账原语单元测试（M2 设计 §4.3 / §12.1 AccountTransactionServiceTest）：
 * 入账/出账双写、余额不足 300001、冻结 300002、乐观锁冲突重试与耗尽 900003、
 * balance_after 正确性、惰性开户 / 已开户定位分支。
 */
class AccountTransactionServiceTest {

    private static final long FAMILY_ID = 10L;

    private static final long USER_ID = 42L;

    private final AccountService accountService = mock(AccountService.class);

    private final MoneyAccountMapper accountMapper = mock(MoneyAccountMapper.class);

    private final MoneyTransactionMapper transactionMapper = mock(MoneyTransactionMapper.class);

    private final AccountTransactionService service =
            new AccountTransactionService(accountService, accountMapper, transactionMapper);

    private MoneyAccount account(String balance, String status) {
        MoneyAccount account = new MoneyAccount();
        account.setId(1L);
        account.setFamilyId(FAMILY_ID);
        account.setUserId(USER_ID);
        account.setBalance(new BigDecimal(balance));
        account.setStatus(status);
        account.setVersion(3L);
        return account;
    }

    private TxCommand inCommand(String amount) {
        return new TxCommand(FAMILY_ID, USER_ID, TxDirection.IN, TxBizType.MANUAL_ADD,
                new BigDecimal(amount), null, null, 1L, "压岁钱", null);
    }

    private TxCommand outCommand(String amount) {
        return new TxCommand(FAMILY_ID, USER_ID, TxDirection.OUT, TxBizType.WITHDRAW,
                new BigDecimal(amount), null, null, 1L, "买文具", null);
    }

    private void expectCode(Throwable thrown, int code) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode().getCode()).isEqualTo(code);
    }

    @Test
    void applyInShouldOpenLazilyAndWriteBothSides() {
        when(accountService.getOrOpen(FAMILY_ID, USER_ID))
                .thenReturn(account("10.00", MoneyAccount.STATUS_ACTIVE));
        when(accountMapper.applyDelta(eq(1L), eq(3L), any(), any(), any())).thenReturn(1);

        MoneyTransaction tx = service.apply(inCommand("5.00"));

        verify(accountService).getOrOpen(FAMILY_ID, USER_ID);
        verify(accountMapper).applyDelta(1L, 3L, new BigDecimal("5.00"),
                new BigDecimal("5.00"), BigDecimal.ZERO);
        ArgumentCaptor<MoneyTransaction> captor =
                ArgumentCaptor.forClass(MoneyTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        MoneyTransaction inserted = captor.getValue();
        assertThat(inserted.getAccountId()).isEqualTo(1L);
        assertThat(inserted.getDirection()).isEqualTo(TxDirection.IN);
        assertThat(inserted.getAmount()).isEqualByComparingTo("5.00");
        assertThat(inserted.getBalanceAfter()).isEqualByComparingTo("15.00");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("15.00");
    }

    @Test
    void applyOutShouldRequireExistingAccountAndNegateDelta() {
        when(accountService.requireAccount(USER_ID))
                .thenReturn(account("10.00", MoneyAccount.STATUS_ACTIVE));
        when(accountMapper.applyDelta(eq(1L), eq(3L), any(), any(), any())).thenReturn(1);

        MoneyTransaction tx = service.apply(outCommand("4.00"));

        verify(accountService).requireAccount(USER_ID);
        verify(accountMapper).applyDelta(1L, 3L, new BigDecimal("-4.00"),
                BigDecimal.ZERO, new BigDecimal("4.00"));
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("6.00");
    }

    @Test
    void applyOutShouldThrow300001WhenBalanceNotEnough() {
        when(accountService.requireAccount(USER_ID))
                .thenReturn(account("10.00", MoneyAccount.STATUS_ACTIVE));

        assertThatThrownBy(() -> service.apply(outCommand("10.01")))
                .satisfies(thrown -> expectCode(thrown, 300001));
        verify(accountMapper, never()).applyDelta(anyLong(), anyLong(), any(), any(), any());
        verify(transactionMapper, never()).insert(any());
    }

    @Test
    void applyShouldThrow300002WhenAccountFrozen() {
        when(accountService.getOrOpen(FAMILY_ID, USER_ID))
                .thenReturn(account("10.00", MoneyAccount.STATUS_FROZEN));
        when(accountService.requireAccount(USER_ID))
                .thenReturn(account("10.00", MoneyAccount.STATUS_FROZEN));

        assertThatThrownBy(() -> service.apply(inCommand("1.00")))
                .satisfies(thrown -> expectCode(thrown, 300002));
        assertThatThrownBy(() -> service.apply(outCommand("1.00")))
                .satisfies(thrown -> expectCode(thrown, 300002));
        verify(transactionMapper, never()).insert(any());
    }

    @Test
    void applyShouldRetryOnVersionConflictThenSucceed() {
        when(accountService.getOrOpen(FAMILY_ID, USER_ID))
                .thenReturn(account("10.00", MoneyAccount.STATUS_ACTIVE));
        when(accountMapper.applyDelta(eq(1L), eq(3L), any(), any(), any()))
                .thenReturn(0, 0, 1);

        MoneyTransaction tx = service.apply(inCommand("5.00"));

        verify(accountService, times(3)).getOrOpen(FAMILY_ID, USER_ID);
        verify(accountMapper, times(3)).applyDelta(eq(1L), eq(3L), any(), any(), any());
        verify(transactionMapper, times(1)).insert(any(MoneyTransaction.class));
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("15.00");
    }

    @Test
    void applyShouldThrow900003WhenRetryExhausted() {
        when(accountService.getOrOpen(FAMILY_ID, USER_ID))
                .thenReturn(account("10.00", MoneyAccount.STATUS_ACTIVE));
        when(accountMapper.applyDelta(eq(1L), eq(3L), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.apply(inCommand("5.00")))
                .satisfies(thrown -> expectCode(thrown, 900003));
        verify(accountMapper, times(3)).applyDelta(eq(1L), eq(3L), any(), any(), any());
        verify(transactionMapper, never()).insert(any());
    }

    @Test
    void applyShouldThrow300004WhenAmountZeroOrNegativeOrNull() {
        TxCommand zero = new TxCommand(FAMILY_ID, USER_ID, TxDirection.IN,
                TxBizType.MANUAL_ADD, BigDecimal.ZERO, null, null, 1L, "零金额", null);
        TxCommand negative = new TxCommand(FAMILY_ID, USER_ID, TxDirection.OUT,
                TxBizType.WITHDRAW, new BigDecimal("-0.01"), null, null, 1L, "负金额", null);
        TxCommand nullAmount = new TxCommand(FAMILY_ID, USER_ID, TxDirection.IN,
                TxBizType.MANUAL_ADD, null, null, null, 1L, "空金额", null);

        assertThatThrownBy(() -> service.apply(zero))
                .satisfies(thrown -> expectCode(thrown, 300004));
        assertThatThrownBy(() -> service.apply(negative))
                .satisfies(thrown -> expectCode(thrown, 300004));
        assertThatThrownBy(() -> service.apply(nullAmount))
                .satisfies(thrown -> expectCode(thrown, 300004));
        // 金额校验先于账户定位与双写
        verify(accountService, never()).getOrOpen(anyLong(), anyLong());
        verify(accountService, never()).requireAccount(anyLong());
        verify(transactionMapper, never()).insert(any());
    }
}
