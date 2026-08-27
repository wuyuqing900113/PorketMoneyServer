package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.DashboardResponse;
import wyq.pocket.money.money.dto.DirectionSum;
import wyq.pocket.money.money.dto.MemberBalanceRow;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 看板服务单元测试（M2 设计 §8.2 #1 / §12.1）：
 * 固定 Clock 下周一边界 / 月初边界、空窗口默认 0、未开户总余额 0。
 */
class DashboardServiceTest {

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, 10L, "PARENT", false);

    /** 固定时钟：2026-08-19（周三，Asia/Shanghai）。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(), ClockConfig.BUSINESS_ZONE);

    private final MoneyAccountMapper accountMapper = mock(MoneyAccountMapper.class);

    private final MoneyTransactionMapper transactionMapper = mock(MoneyTransactionMapper.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final DashboardService service = new DashboardService(
            accountMapper, transactionMapper, familyAccessChecker, CLOCK);

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
    }

    @Test
    void dashboardShouldUseMondayAndMonthBoundaries() {
        Instant weekStart = startOfDay(TODAY.with(DayOfWeek.MONDAY));
        Instant monthStart = startOfDay(TODAY.withDayOfMonth(1));
        when(transactionMapper.sumByDirectionSince(10L, weekStart)).thenReturn(List.of(
                new DirectionSum(TxDirection.IN, new BigDecimal("7.00")),
                new DirectionSum(TxDirection.OUT, new BigDecimal("2.00"))));
        when(transactionMapper.sumByDirectionSince(10L, monthStart)).thenReturn(List.of(
                new DirectionSum(TxDirection.IN, new BigDecimal("100.00")),
                new DirectionSum(TxDirection.OUT, new BigDecimal("30.00"))));
        when(accountMapper.sumBalanceByFamily(10L)).thenReturn(new BigDecimal("68.00"));
        when(accountMapper.findMemberBalances(10L)).thenReturn(List.of(
                new MemberBalanceRow(2L, "小明", new BigDecimal("68.00"))));

        DashboardResponse response = service.getDashboard(PARENT);

        verify(transactionMapper).sumByDirectionSince(10L, weekStart);
        verify(transactionMapper).sumByDirectionSince(10L, monthStart);
        assertThat(response.totalBalance()).isEqualByComparingTo("68.00");
        assertThat(response.weekIncome()).isEqualByComparingTo("7.00");
        assertThat(response.weekExpense()).isEqualByComparingTo("2.00");
        assertThat(response.monthIncome()).isEqualByComparingTo("100.00");
        assertThat(response.monthExpense()).isEqualByComparingTo("30.00");
        assertThat(response.members()).hasSize(1);
    }

    @Test
    void dashboardShouldDefaultToZeroWhenNoTransactions() {
        when(transactionMapper.sumByDirectionSince(10L, startOfDay(TODAY.with(DayOfWeek.MONDAY))))
                .thenReturn(List.of());
        when(transactionMapper.sumByDirectionSince(10L, startOfDay(TODAY.withDayOfMonth(1))))
                .thenReturn(List.of());
        when(accountMapper.sumBalanceByFamily(10L)).thenReturn(BigDecimal.ZERO);
        when(accountMapper.findMemberBalances(10L)).thenReturn(List.of());

        DashboardResponse response = service.getDashboard(PARENT);

        assertThat(response.totalBalance()).isEqualByComparingTo("0");
        assertThat(response.weekIncome()).isEqualByComparingTo("0");
        assertThat(response.weekExpense()).isEqualByComparingTo("0");
        assertThat(response.monthIncome()).isEqualByComparingTo("0");
        assertThat(response.monthExpense()).isEqualByComparingTo("0");
        assertThat(response.members()).isEmpty();
    }
}
