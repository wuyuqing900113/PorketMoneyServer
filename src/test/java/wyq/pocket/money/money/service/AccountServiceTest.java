package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;

/**
 * 账户服务单元测试（M2 设计 §4.1）：惰性开户、并发竞态、requireAccount 300001。
 */
class AccountServiceTest {

    private static final long FAMILY_ID = 10L;

    private static final long USER_ID = 42L;

    private final MoneyAccountMapper accountMapper = mock(MoneyAccountMapper.class);

    private final AccountService service = new AccountService(accountMapper);

    private MoneyAccount existing() {
        MoneyAccount account = new MoneyAccount();
        account.setId(7L);
        account.setFamilyId(FAMILY_ID);
        account.setUserId(USER_ID);
        return account;
    }

    @Test
    void getOrOpenShouldReturnExistingWithoutInsert() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(existing());

        MoneyAccount account = service.getOrOpen(FAMILY_ID, USER_ID);

        assertThat(account.getId()).isEqualTo(7L);
        verify(accountMapper, never()).insertIgnoreConflict(any());
    }

    @Test
    void getOrOpenShouldInsertThenReloadWhenAbsent() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(null, existing());

        MoneyAccount account = service.getOrOpen(FAMILY_ID, USER_ID);

        ArgumentCaptor<MoneyAccount> captor = ArgumentCaptor.forClass(MoneyAccount.class);
        verify(accountMapper).insertIgnoreConflict(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(account.getId()).isEqualTo(7L);
    }

    @Test
    void requireAccountShouldThrow300001WhenAbsent() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.requireAccount(USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(
                        ((BusinessException) thrown).getErrorCode().getCode())
                        .isEqualTo(300001));
    }

    @Test
    void requireAccountShouldReturnAccountWhenPresent() {
        when(accountMapper.findByUserId(USER_ID)).thenReturn(existing());

        assertThat(service.requireAccount(USER_ID).getId()).isEqualTo(7L);
    }
}
