package wyq.pocket.money.money.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.OpenApiConfig;
import wyq.pocket.money.common.web.Result;
import wyq.pocket.money.money.dto.DashboardResponse;
import wyq.pocket.money.money.dto.DepositRequest;
import wyq.pocket.money.money.dto.DepositWithdrawResponse;
import wyq.pocket.money.money.dto.LeaderboardResponse;
import wyq.pocket.money.money.dto.TransactionPageQuery;
import wyq.pocket.money.money.dto.TransactionPageResponse;
import wyq.pocket.money.money.dto.TrendResponse;
import wyq.pocket.money.money.dto.WithdrawRequest;
import wyq.pocket.money.money.service.DashboardService;
import wyq.pocket.money.money.service.LeaderboardService;
import wyq.pocket.money.money.service.MoneyOperationService;
import wyq.pocket.money.money.service.MoneyQueryService;
import wyq.pocket.money.money.service.TrendService;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 零花钱端点（M2 设计 §8.2 #1–#6，资源挂在家庭路径下）。
 *
 * <p>读端点家庭内全透明（家长 + 孩子均可见）；
 * 存取写端点孩子仅限本人账户（service 层数据级守卫）。
 * 入口统一校验路径 familyId 的成员资格（跨家庭 100004）。
 */
@Tag(name = "零花钱", description = "看板、流水、趋势、榜单与手动存取")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/families/{familyId}")
public class MoneyController {

    private final DashboardService dashboardService;

    private final MoneyQueryService moneyQueryService;

    private final TrendService trendService;

    private final LeaderboardService leaderboardService;

    private final MoneyOperationService moneyOperationService;

    private final FamilyAccessChecker familyAccessChecker;

    /**
     * 注入协作对象。
     *
     * @param dashboardService      看板服务
     * @param moneyQueryService     流水查询服务
     * @param trendService          趋势服务
     * @param leaderboardService    榜单服务
     * @param moneyOperationService 存取服务
     * @param familyAccessChecker   数据级访问守卫（路径 familyId 成员校验）
     */
    public MoneyController(DashboardService dashboardService,
                           MoneyQueryService moneyQueryService,
                           TrendService trendService,
                           LeaderboardService leaderboardService,
                           MoneyOperationService moneyOperationService,
                           FamilyAccessChecker familyAccessChecker) {
        this.dashboardService = dashboardService;
        this.moneyQueryService = moneyQueryService;
        this.trendService = trendService;
        this.leaderboardService = leaderboardService;
        this.moneyOperationService = moneyOperationService;
        this.familyAccessChecker = familyAccessChecker;
    }

    /**
     * 看板（§8.2 #1）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @return 看板数据
     */
    @Operation(summary = "看板", description = "总余额、本周 / 本月收支、全员余额。"
            + "错误码：HTTP 401 + 100003 未认证；HTTP 403 + 100004 非本家庭成员。")
    @GetMapping("/dashboard")
    public Result<DashboardResponse> dashboard(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(dashboardService.getDashboard(principal));
    }

    /**
     * 流水分页查询（§8.2 #2）。
     *
     * <p>查询参数经 {@link TransactionPageQuery} 绑定（9 参超限，
     * 收敛为绑定对象；URL 参数名不变）：
     * userId / direction / bizType / from / to 可选，
     * page 默认 1，size 默认 20（上限 50）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param query     分页查询条件（可选过滤 + 分页参数）
     * @return 分页流水
     */
    @Operation(summary = "流水分页查询",
            description = "家庭内全透明读；错误枚举值返回 100001。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004；100001 参数校验失败。")
    @GetMapping("/transactions")
    public Result<TransactionPageResponse> transactions(
            @PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            TransactionPageQuery query) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(moneyQueryService.page(principal, query.getUserId(),
                query.getDirection(), query.getBizType(), query.getFrom(), query.getTo(),
                query.getPage(), query.getSize()));
    }

    /**
     * 趋势（§8.2 #3）。
     *
     * @param familyId    家庭 ID
     * @param principal   当前登录主体
     * @param scope       FAMILY（默认）/ USER
     * @param userId      scope=USER 时可指定成员，缺省为自己
     * @param granularity DAY / WEEK（默认 WEEK）
     * @return 趋势数据
     */
    @Operation(summary = "趋势查询",
            description = "流水实时聚合：日粒度 30 天 / 周粒度 12 周（周一至周日）。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004。")
    @GetMapping("/trends")
    public Result<TrendResponse> trends(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @RequestParam(value = "scope", defaultValue = "FAMILY") String scope,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "granularity", defaultValue = "WEEK") String granularity) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(trendService.trend(principal, scope, userId, granularity));
    }

    /**
     * 本周收入榜（§8.2 #4）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @return 榜单
     */
    @Operation(summary = "本周收入榜",
            description = "本周一 00:00（北京时间）起收入排名，dense ranking。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004。")
    @GetMapping("/leaderboards/weekly-income")
    public Result<LeaderboardResponse> leaderboard(
            @PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(leaderboardService.leaderboard(principal));
    }

    /**
     * 手动存入（§8.2 #5，目标账户在请求体 targetUserId）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param request   存入请求（含目标账户持有人）
     * @return 记账结果
     */
    @Operation(summary = "手动存入",
            description = "家长可对本家庭任意成员账户；孩子仅限本人账户。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004 孩子操作他人账户"
                    + "或目标非本家庭成员；100001 参数校验失败；300002 账户冻结。")
    @PostMapping("/deposits")
    public Result<DepositWithdrawResponse> deposit(
            @PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody DepositRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(
                moneyOperationService.deposit(principal, request.targetUserId(), request));
    }

    /**
     * 手动取出（§8.2 #6，目标账户在请求体 targetUserId）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param request   取出请求（含目标账户持有人）
     * @return 记账结果
     */
    @Operation(summary = "手动取出",
            description = "自由提取，余额不足返回 300001。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004；100001 参数校验失败；"
                    + "300001 余额不足；300002 账户冻结。")
    @PostMapping("/withdrawals")
    public Result<DepositWithdrawResponse> withdraw(
            @PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody WithdrawRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(
                moneyOperationService.withdraw(principal, request.targetUserId(), request));
    }
}
