package wyq.pocket.money.finance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.Result;
import wyq.pocket.money.finance.dto.IncomeExpenseReportResponse;
import wyq.pocket.money.finance.dto.StatisticsSummaryResponse;
import wyq.pocket.money.finance.service.ReportService;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 财务报表端点单元测试（M2 设计 §8.2 #23–#24）：两个读端点向 {@link ReportService}
 * 的委托，以及路径 familyId 的成员资格守卫（跨家庭 100004 由
 * {@link FamilyAccessChecker#requireMember} 触发，此处仅验证委托边界）。
 */
class FinanceControllerTest {

    private static final long FAMILY_ID = 10L;

    private static final UserIdPrincipal PARENT =
            new UserIdPrincipal(1L, FAMILY_ID, "PARENT", false);

    private final ReportService reportService = mock(ReportService.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final FinanceController controller =
            new FinanceController(reportService, familyAccessChecker);

    @Test
    void incomeExpenseShouldGuardFamilyAndDelegate() {
        when(reportService.incomeExpense(PARENT, "2026-08")).thenReturn(
                new IncomeExpenseReportResponse("2026-08", new BigDecimal("100.00"),
                        new BigDecimal("30.00"), new BigDecimal("70.00"),
                        Map.of("MANUAL_ADD", new BigDecimal("100.00")),
                        Map.of("WITHDRAW", new BigDecimal("30.00")), List.of()));

        Result<IncomeExpenseReportResponse> result =
                controller.incomeExpense(FAMILY_ID, PARENT, "2026-08");

        verify(familyAccessChecker).requireMember(FAMILY_ID, 1L);
        verify(reportService).incomeExpense(PARENT, "2026-08");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data().net()).isEqualByComparingTo("70.00");
    }

    @Test
    void statisticsShouldGuardFamilyAndDelegate() {
        when(reportService.statistics(PARENT)).thenReturn(
                new StatisticsSummaryResponse(new BigDecimal("600.00"),
                        new BigDecimal("1000.00"), new BigDecimal("400.00"),
                        new BigDecimal("100.00"), new BigDecimal("30.00"), 3));

        Result<StatisticsSummaryResponse> result = controller.statistics(FAMILY_ID, PARENT);

        verify(familyAccessChecker).requireMember(FAMILY_ID, 1L);
        verify(reportService).statistics(PARENT);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data().memberCount()).isEqualTo(3);
    }
}
