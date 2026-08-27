package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.TransactionPageResponse;
import wyq.pocket.money.money.dto.TransactionRow;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 流水分页查询单元测试（M2 设计 §8.2 #2）：
 * 页码/页大小钳制、非法枚举 100001、业务日边界转换。
 */
class MoneyQueryServiceTest {

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, 10L, "PARENT", false);

    private final MoneyTransactionMapper transactionMapper = mock(MoneyTransactionMapper.class);

    private final MoneyAccountMapper accountMapper = mock(MoneyAccountMapper.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final MoneyQueryService service = new MoneyQueryService(
            transactionMapper, accountMapper, familyAccessChecker);

    private TransactionRow row() {
        return new TransactionRow(1L, 2L, "小明", TxDirection.IN, TxBizType.MANUAL_ADD,
                java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, null, null, Instant.EPOCH);
    }

    @Test
    void pageShouldClampPageAndSize() {
        when(transactionMapper.countPage(eq(10L), isNull(), isNull(), isNull(),
                isNull(), isNull())).thenReturn(0L);
        when(transactionMapper.findPage(eq(10L), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(50), eq(0))).thenReturn(List.of());

        // page=0 → 1；size=999 → 50（设计 §5.3 页长上限）
        TransactionPageResponse response =
                service.page(PARENT, null, null, null, null, null, 0, 999);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(50);
        verify(transactionMapper).findPage(eq(10L), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(50), eq(0));
    }

    @Test
    void pageShouldUseDefaultSizeWhenBelowOne() {
        when(transactionMapper.countPage(eq(10L), isNull(), isNull(), isNull(),
                isNull(), isNull())).thenReturn(0L);
        when(transactionMapper.findPage(anyLong(), any(), any(), any(), any(), any(),
                eq(20), eq(0))).thenReturn(List.of());

        TransactionPageResponse response =
                service.page(PARENT, null, null, null, null, null, 2, 0);

        assertThat(response.size()).isEqualTo(20);
        // page=2, size=20 → offset 20
        verify(transactionMapper).findPage(anyLong(), any(), any(), any(), any(), any(),
                eq(20), eq(20));
    }

    @Test
    void pageShouldThrow100001OnIllegalEnum() {
        assertThatThrownBy(() -> service.page(PARENT, null, "BOTH", null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(
                        ((BusinessException) thrown).getErrorCode().getCode())
                        .isEqualTo(100001));
        assertThatThrownBy(() -> service.page(PARENT, null, null, "SALARY", null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(
                        ((BusinessException) thrown).getErrorCode().getCode())
                        .isEqualTo(100001));
    }

    @Test
    void pageShouldParseEnumsCaseInsensitive() {
        when(transactionMapper.countPage(eq(10L), isNull(), eq(TxDirection.OUT),
                eq(TxBizType.WITHDRAW), isNull(), isNull())).thenReturn(1L);
        when(transactionMapper.findPage(eq(10L), isNull(), eq(TxDirection.OUT),
                eq(TxBizType.WITHDRAW), isNull(), isNull(), eq(20), eq(0)))
                .thenReturn(List.of(row()));

        TransactionPageResponse response =
                service.page(PARENT, null, "out", "withdraw", null, null, 1, 20);

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.records()).hasSize(1);
    }

    @Test
    void pageShouldConvertDateRangeToBusinessZoneInstants() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 15);
        Instant expectedFrom = from.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
        Instant expectedTo = to.plusDays(1).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
        when(transactionMapper.countPage(eq(10L), isNull(), isNull(), isNull(),
                eq(expectedFrom), eq(expectedTo))).thenReturn(0L);
        when(transactionMapper.findPage(eq(10L), isNull(), isNull(), isNull(),
                eq(expectedFrom), eq(expectedTo), eq(20), eq(0))).thenReturn(List.of());

        service.page(PARENT, null, null, null, from, to, 1, 20);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(transactionMapper).countPage(eq(10L), isNull(), isNull(), isNull(),
                fromCaptor.capture(), eq(expectedTo));
        assertThat(fromCaptor.getValue()).isEqualTo(expectedFrom);
    }

    @Test
    void pageShouldRequireMembershipOfFilterUser() {
        when(transactionMapper.countPage(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(0L);
        when(transactionMapper.findPage(anyLong(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(List.of());

        service.page(PARENT, 3L, null, null, null, null, 1, 20);

        verify(familyAccessChecker).requireMember(10L, 1L);
        verify(familyAccessChecker).requireMember(10L, 3L);
    }
}
