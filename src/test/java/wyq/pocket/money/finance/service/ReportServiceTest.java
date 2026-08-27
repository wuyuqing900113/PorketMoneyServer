package wyq.pocket.money.finance.service;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.finance.dto.IncomeExpenseReportResponse;
import wyq.pocket.money.finance.dto.MemberReportRow;
import wyq.pocket.money.finance.dto.StatisticsSummaryResponse;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.AccountTotals;
import wyq.pocket.money.money.dto.BizTypeSum;
import wyq.pocket.money.money.dto.DirectionSum;
import wyq.pocket.money.money.dto.UserDirectionSum;
import wyq.pocket.money.money.service.MoneyQueryService;
import wyq.pocket.money.user.dto.MemberSummary;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.FamilyService;
import wyq.pocket.money.user.service.UserService;

/**
 * 财务报表单元测试（M2 设计 §12.1 ReportServiceTest）：
 * 分项聚合与净额、成员行全家庭成员上榜（纯支出成员、零收支成员 0/0/0）、
 * 统计摘要、500001 月份格式非法、500002 月份在未来。
 *
 * <p>固定时钟 2026-08-19（Asia/Shanghai），报表月窗 = 2026-08。
 */
class ReportServiceTest {

    private static final long FAMILY_ID = 10L;

    /** 家长（报表查询主体）。 */
    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, FAMILY_ID, "PARENT", false);

    /** 月起（含）：2026-08-01 00:00 北京时间。 */
    private static final Instant FROM = Instant.parse("2026-07-31T16:00:00Z");

    /** 月止（不含）：2026-09-01 00:00 北京时间。 */
    private static final Instant TO = Instant.parse("2026-08-31T16:00:00Z");

    private final MoneyQueryService moneyQueryService = mock(MoneyQueryService.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final FamilyService familyService = mock(FamilyService.class);

    private final UserService userService = mock(UserService.class);

    private final Clock clock = Clock.fixed(
            LocalDate.of(2026, 8, 19).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(),
            ClockConfig.BUSINESS_ZONE);

    private final ReportService service = new ReportService(moneyQueryService,
            familyAccessChecker, familyService, userService, clock);

    private void expectCode(Throwable thrown, int code) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode().getCode()).isEqualTo(code);
    }

    private void stubMonthFixture() {
        when(moneyQueryService.sumByBizType(FAMILY_ID, FROM, TO)).thenReturn(List.of(
                new BizTypeSum(TxBizType.MANUAL_ADD, TxDirection.IN, new BigDecimal("80.00")),
                new BizTypeSum(TxBizType.LEARNING_REWARD, TxDirection.IN,
                        new BigDecimal("20.00")),
                new BizTypeSum(TxBizType.WITHDRAW, TxDirection.OUT, new BigDecimal("30.00"))));
        when(moneyQueryService.sumByUserAndDirection(FAMILY_ID, FROM, TO)).thenReturn(List.of(
                new UserDirectionSum(2L, TxDirection.IN, new BigDecimal("80.00")),
                new UserDirectionSum(2L, TxDirection.IN, new BigDecimal("20.00")),
                new UserDirectionSum(3L, TxDirection.OUT, new BigDecimal("30.00"))));
        when(familyService.listMembers(FAMILY_ID, PARENT)).thenReturn(List.of(
                new MemberSummary(1L, "家长", "PARENT"),
                new MemberSummary(2L, "孩子甲", "CHILD"),
                new MemberSummary(3L, "孩子乙", "CHILD")));
    }

    @Test
    void incomeExpenseShouldAggregateAndListAllMembers() {
        stubMonthFixture();

        IncomeExpenseReportResponse report = service.incomeExpense(PARENT, "2026-08");

        verify(familyAccessChecker).requireMember(FAMILY_ID, 1L);
        assertThat(report.month()).isEqualTo("2026-08");
        assertThat(report.totalIncome()).isEqualByComparingTo("100.00");
        assertThat(report.totalExpense()).isEqualByComparingTo("30.00");
        assertThat(report.net()).isEqualByComparingTo("70.00");
        assertThat(report.incomeByType().get("MANUAL_ADD")).isEqualByComparingTo("80.00");
        assertThat(report.incomeByType().get("LEARNING_REWARD")).isEqualByComparingTo("20.00");
        assertThat(report.expenseByType().get("WITHDRAW")).isEqualByComparingTo("30.00");

        // 全员上榜：家长零收支 0/0/0、孩子甲纯收入、孩子乙纯支出
        List<MemberReportRow> rows = report.members();
        assertThat(rows).hasSize(3);
        MemberReportRow parentRow = rows.get(0);
        assertThat(parentRow.userId()).isEqualTo(1L);
        assertThat(parentRow.income()).isEqualByComparingTo("0");
        assertThat(parentRow.expense()).isEqualByComparingTo("0");
        assertThat(parentRow.net()).isEqualByComparingTo("0");
        MemberReportRow incomeOnly = rows.get(1);
        assertThat(incomeOnly.userId()).isEqualTo(2L);
        assertThat(incomeOnly.income()).isEqualByComparingTo("100.00");
        assertThat(incomeOnly.expense()).isEqualByComparingTo("0");
        assertThat(incomeOnly.net()).isEqualByComparingTo("100.00");
        MemberReportRow expenseOnly = rows.get(2);
        assertThat(expenseOnly.userId()).isEqualTo(3L);
        assertThat(expenseOnly.income()).isEqualByComparingTo("0");
        assertThat(expenseOnly.expense()).isEqualByComparingTo("30.00");
        assertThat(expenseOnly.net()).isEqualByComparingTo("-30.00");
    }

    @Test
    void incomeExpenseShouldThrow500001WhenMonthMalformed() {
        assertThatThrownBy(() -> service.incomeExpense(PARENT, "2026-13"))
                .satisfies(thrown -> expectCode(thrown, 500001));
        assertThatThrownBy(() -> service.incomeExpense(PARENT, "abc"))
                .satisfies(thrown -> expectCode(thrown, 500001));
        assertThatThrownBy(() -> service.incomeExpense(PARENT, null))
                .satisfies(thrown -> expectCode(thrown, 500001));
        assertThatThrownBy(() -> service.incomeExpense(PARENT, "2026-8"))
                .satisfies(thrown -> expectCode(thrown, 500001));
        verify(moneyQueryService, never()).sumByBizType(anyLong(), any(), any());
    }

    @Test
    void incomeExpenseShouldThrow500002WhenMonthInFuture() {
        assertThatThrownBy(() -> service.incomeExpense(PARENT, "2026-09"))
                .satisfies(thrown -> expectCode(thrown, 500002));
        verify(moneyQueryService, never()).sumByBizType(anyLong(), any(), any());
    }

    @Test
    void statisticsShouldSummarizeBalanceTotalsAndCurrentMonth() {
        when(moneyQueryService.accountTotals(FAMILY_ID))
                .thenReturn(new AccountTotals(new BigDecimal("1000.00"),
                        new BigDecimal("400.00")));
        when(moneyQueryService.totalBalance(FAMILY_ID)).thenReturn(new BigDecimal("600.00"));
        when(moneyQueryService.sumByDirectionSince(FAMILY_ID, FROM)).thenReturn(List.of(
                new DirectionSum(TxDirection.IN, new BigDecimal("100.00")),
                new DirectionSum(TxDirection.OUT, new BigDecimal("30.00"))));
        when(userService.countFamilyMembers(FAMILY_ID)).thenReturn(3);

        StatisticsSummaryResponse summary = service.statistics(PARENT);

        verify(familyAccessChecker).requireMember(FAMILY_ID, 1L);
        assertThat(summary.totalBalance()).isEqualByComparingTo("600.00");
        assertThat(summary.allTimeIncome()).isEqualByComparingTo("1000.00");
        assertThat(summary.allTimeExpense()).isEqualByComparingTo("400.00");
        assertThat(summary.currentMonthIncome()).isEqualByComparingTo("100.00");
        assertThat(summary.currentMonthExpense()).isEqualByComparingTo("30.00");
        assertThat(summary.memberCount()).isEqualTo(3);
        // 当月窗口以业务时区月起为准
        verify(moneyQueryService).sumByDirectionSince(eq(FAMILY_ID), eq(FROM));
    }
}
