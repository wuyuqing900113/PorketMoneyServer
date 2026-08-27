package wyq.pocket.money.finance.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.OpenApiConfig;
import wyq.pocket.money.common.web.Result;
import wyq.pocket.money.finance.dto.IncomeExpenseReportResponse;
import wyq.pocket.money.finance.dto.StatisticsSummaryResponse;
import wyq.pocket.money.finance.service.ReportService;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 财务端点（M2 设计 §8.2 #23–#24，资源挂在家庭路径下）：家庭内全透明读。
 *
 * <p>入口统一校验路径 familyId 的成员资格（跨家庭 100004）。
 */
@Tag(name = "财务报表", description = "月度收支报表与统计摘要（同步聚合）")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/families/{familyId}")
public class FinanceController {

    private final ReportService reportService;

    private final FamilyAccessChecker familyAccessChecker;

    /**
     * 注入报表业务。
     *
     * @param reportService       报表业务
     * @param familyAccessChecker 数据级访问守卫（路径 familyId 成员校验）
     */
    public FinanceController(ReportService reportService,
                             FamilyAccessChecker familyAccessChecker) {
        this.reportService = reportService;
        this.familyAccessChecker = familyAccessChecker;
    }

    /**
     * 月度收支报表（#23）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param month     报表月份（YYYY-MM）
     * @return 收支报表
     */
    @Operation(summary = "月度收支报表",
            description = "家庭内全透明读，同步聚合当月流水。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004；"
                    + "500001 月份格式非法；500002 月份晚于当月。")
    @GetMapping("/reports/income-expense")
    public Result<IncomeExpenseReportResponse> incomeExpense(
            @PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @RequestParam("month") String month) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(reportService.incomeExpense(principal, month));
    }

    /**
     * 统计摘要（#24）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @return 统计摘要
     */
    @Operation(summary = "统计摘要",
            description = "总余额、历史累计收支、当月收支、成员数。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004。")
    @GetMapping("/statistics/summary")
    public Result<StatisticsSummaryResponse> statistics(
            @PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(reportService.statistics(principal));
    }
}
