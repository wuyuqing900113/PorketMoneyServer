package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 * 金额精度单元测试（M2 设计 §12.1 AmountPrecisionTest）：
 * BigDecimal 无浮点漂移、连续累加、DECIMAL(12,2) 上限、
 * balance = amount 恰好取空、amount + 0.01 拒绝。
 */
class AmountPrecisionTest {

    private static final long FAMILY_ID = 10L;

    private static final long USER_ID = 42L;

    private final AccountService accountService = mock(AccountService.class);

    private final MoneyAccountMapper accountMapper = mock(MoneyAccountMapper.class);

    private final MoneyTransactionMapper transactionMapper = mock(MoneyTransactionMapper.class);

    private final AccountTransactionService service =
            new AccountTransactionService(accountService, accountMapper, transactionMapper);

    private MoneyAccount account(String balance) {
        MoneyAccount account = new MoneyAccount();
        account.setId(1L);
        account.setUserId(USER_ID);
        account.setBalance(new BigDecimal(balance));
        account.setStatus(MoneyAccount.STATUS_ACTIVE);
        account.setVersion(1L);
        return account;
    }

    @Test
    void bigDecimalShouldNotDriftLikeFloat() {
        assertThat(new BigDecimal("0.1").add(new BigDecimal("0.2")))
                .isEqualByComparingTo("0.3");
    }

    @Test
    void repeatedAccumulationShouldStayExact() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 100; i++) {
            total = total.add(new BigDecimal("0.01"));
        }
        assertThat(total).isEqualByComparingTo("1.00");
    }

    @Test
    void decimalUpperBoundShouldPassThrough() {
        // DECIMAL(12,2) 上限 9999999999.99（整数位 10 + 小数位 2）
        when(accountService.getOrOpen(FAMILY_ID, USER_ID)).thenReturn(account("0.00"));
        when(accountMapper.applyDelta(eq(1L), eq(1L), any(), any(), any())).thenReturn(1);

        MoneyTransaction tx = service.apply(new TxCommand(FAMILY_ID, USER_ID, TxDirection.IN,
                TxBizType.MANUAL_ADD, new BigDecimal("9999999999.99"), null, null, 1L,
                null, null));

        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("9999999999.99");
    }

    @Test
    void withdrawShouldDrainExactlyWhenAmountEqualsBalance() {
        when(accountService.requireAccount(USER_ID)).thenReturn(account("10.00"));
        when(accountMapper.applyDelta(eq(1L), eq(1L), any(), any(), any())).thenReturn(1);

        MoneyTransaction tx = service.apply(new TxCommand(FAMILY_ID, USER_ID, TxDirection.OUT,
                TxBizType.WITHDRAW, new BigDecimal("10.00"), null, null, 1L, null, null));

        ArgumentCaptor<MoneyTransaction> captor =
                ArgumentCaptor.forClass(MoneyTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("0");
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("0");
    }

    @Test
    void withdrawShouldRejectAmountPlusOneCent() {
        when(accountService.requireAccount(USER_ID)).thenReturn(account("10.00"));

        assertThatThrownBy(() -> service.apply(new TxCommand(FAMILY_ID, USER_ID,
                TxDirection.OUT, TxBizType.WITHDRAW, new BigDecimal("10.01"),
                null, null, 1L, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(
                        ((BusinessException) thrown).getErrorCode().getCode())
                        .isEqualTo(300001));
        verify(accountMapper, never()).applyDelta(anyLong(), anyLong(), any(), any(), any());
    }
}
