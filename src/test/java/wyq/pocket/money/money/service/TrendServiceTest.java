package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.TrendPoint;
import wyq.pocket.money.money.dto.TrendResponse;
import wyq.pocket.money.money.dto.TxWindowRow;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 趋势服务单元测试（M2 设计 §8.2 #3 / §12.1）：
 * 日 / 周序列桶数、B0 = 当前余额 − 窗口净额 推导、空窗口、USER 范围、周一起始（固定 Clock）。
 */
class TrendServiceTest {

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, 10L, "PARENT", false);

    /** 固定时钟：2026-08-19（周三）。 */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(), ClockConfig.BUSINESS_ZONE);

    private final MoneyTransactionMapper transactionMapper = mock(MoneyTransactionMapper.class);

    private final MoneyAccountMapper accountMapper = mock(MoneyAccountMapper.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final TrendService service = new TrendService(
            transactionMapper, accountMapper, familyAccessChecker, CLOCK);

    private Instant at(LocalDate date) {
        return date.atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
    }

    @Test
    void weeklyTrendShouldHave12BucketsStartingMonday() {
        LocalDate windowStart = TODAY.with(DayOfWeek.MONDAY).minusWeeks(11);
        Instant since = at(windowStart);
        // 当前余额 10.00；窗口内 收入 5.00 支出 2.00 → B0 = 7.00
        when(transactionMapper.findWindow(eq(10L), isNull(), eq(since))).thenReturn(List.of(
                new TxWindowRow(TxDirection.IN, new BigDecimal("5.00"), at(TODAY)),
                new TxWindowRow(TxDirection.OUT, new BigDecimal("2.00"),
                        at(TODAY.minusDays(1)))));
        when(accountMapper.sumBalanceByFamily(10L)).thenReturn(new BigDecimal("10.00"));

        TrendResponse response = service.trend(PARENT, null, null, "WEEK");

        assertThat(response.granularity()).isEqualTo("WEEK");
        assertThat(response.scope()).isEqualTo("FAMILY");
        assertThat(response.userId()).isNull();
        assertThat(response.series()).hasSize(12);
        TrendPoint first = response.series().get(0);
        assertThat(first.startDate()).isEqualTo(windowStart);
        assertThat(first.startDate().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(first.endDate()).isEqualTo(windowStart.plusDays(6));
        // 前两笔流水都在最后一个桶（本周）
        TrendPoint last = response.series().get(11);
        assertThat(last.income()).isEqualByComparingTo("5.00");
        assertThat(last.expense()).isEqualByComparingTo("2.00");
        assertThat(last.endingBalance()).isEqualByComparingTo("10.00");
        // 窗口内其余桶保持 B0
        assertThat(response.series().get(0).endingBalance()).isEqualByComparingTo("7.00");
    }

    @Test
    void dailyTrendShouldHave30Buckets() {
        LocalDate windowStart = TODAY.minusDays(29);
        Instant since = at(windowStart);
        when(transactionMapper.findWindow(eq(10L), isNull(), eq(since))).thenReturn(List.of(
                new TxWindowRow(TxDirection.IN, new BigDecimal("1.00"), at(TODAY))));
        when(accountMapper.sumBalanceByFamily(10L)).thenReturn(new BigDecimal("3.00"));

        TrendResponse response = service.trend(PARENT, null, null, "DAY");

        assertThat(response.granularity()).isEqualTo("DAY");
        assertThat(response.series()).hasSize(30);
        TrendPoint last = response.series().get(29);
        assertThat(last.startDate()).isEqualTo(TODAY);
        assertThat(last.endDate()).isEqualTo(TODAY);
        assertThat(last.endingBalance()).isEqualByComparingTo("3.00");
        assertThat(response.series().get(0).endingBalance()).isEqualByComparingTo("2.00");
    }

    @Test
    void emptyWindowShouldKeepCurrentBalanceAcrossSeries() {
        LocalDate windowStart = TODAY.with(DayOfWeek.MONDAY).minusWeeks(11);
        when(transactionMapper.findWindow(eq(10L), isNull(), eq(at(windowStart))))
                .thenReturn(List.of());
        when(accountMapper.sumBalanceByFamily(10L)).thenReturn(new BigDecimal("9.99"));

        TrendResponse response = service.trend(PARENT, null, null, "WEEK");

        for (TrendPoint point : response.series()) {
            assertThat(point.endingBalance()).isEqualByComparingTo("9.99");
            assertThat(point.income()).isEqualByComparingTo("0");
            assertThat(point.expense()).isEqualByComparingTo("0");
        }
    }

    @Test
    void userScopeShouldDefaultToSelfAndUseAccountBalance() {
        LocalDate windowStart = TODAY.with(DayOfWeek.MONDAY).minusWeeks(11);
        when(transactionMapper.findWindow(eq(10L), eq(1L), eq(at(windowStart))))
                .thenReturn(List.of());
        MoneyAccount account = new MoneyAccount();
        account.setBalance(new BigDecimal("3.50"));
        when(accountMapper.findByUserId(1L)).thenReturn(account);

        TrendResponse response = service.trend(PARENT, "USER", null, "WEEK");

        assertThat(response.scope()).isEqualTo("USER");
        assertThat(response.userId()).isEqualTo(1L);
        verify(familyAccessChecker).requireMember(10L, 1L);
        assertThat(response.series().get(0).endingBalance()).isEqualByComparingTo("3.50");
    }

    @Test
    void userScopeWithoutAccountShouldStartFromZero() {
        LocalDate windowStart = TODAY.minusDays(29);
        when(transactionMapper.findWindow(eq(10L), eq(5L), eq(at(windowStart))))
                .thenReturn(List.of(
                        new TxWindowRow(TxDirection.IN, new BigDecimal("2.00"), at(TODAY))));
        when(accountMapper.findByUserId(5L)).thenReturn(null);

        TrendResponse response = service.trend(PARENT, "user", 5L, "DAY");

        assertThat(response.userId()).isEqualTo(5L);
        assertThat(response.series().get(29).endingBalance()).isEqualByComparingTo("2.00");
    }
}
