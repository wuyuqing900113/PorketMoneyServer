package wyq.pocket.money.money.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
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
import wyq.pocket.money.money.dto.CreateWorkValueRequest;
import wyq.pocket.money.money.dto.WorkValueResponse;
import wyq.pocket.money.money.service.WorkValueService;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 工作价值端点（M2 设计 §8.2 #21–#22，资源挂在家庭路径下）。
 *
 * <p>入口统一校验路径 familyId 的成员资格（跨家庭 100004）。
 */
@Tag(name = "工作价值", description = "父母工资收入记录与发放入账")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/families/{familyId}/work-values")
public class WorkValueController {

    private final WorkValueService workValueService;

    private final FamilyAccessChecker familyAccessChecker;

    /**
     * 注入工作价值业务。
     *
     * @param workValueService    工作价值业务
     * @param familyAccessChecker 数据级访问守卫（路径 familyId 成员校验）
     */
    public WorkValueController(WorkValueService workValueService,
                               FamilyAccessChecker familyAccessChecker) {
        this.workValueService = workValueService;
        this.familyAccessChecker = familyAccessChecker;
    }

    /**
     * 记录列表（#22）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param workMonth 可选月份过滤（YYYY-MM）
     * @return 记录列表
     */
    @Operation(summary = "工作价值记录列表",
            description = "家庭内全透明读，按月份倒序，最多 100 条。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004。")
    @GetMapping
    public Result<List<WorkValueResponse>> list(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @RequestParam(value = "workMonth", required = false) String workMonth) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(workValueService.list(principal, workMonth));
    }

    /**
     * 记录工作价值（#21，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体（父母本人，操作人 = 收款人）
     * @param request   创建请求
     * @return 记录响应
     */
    @Operation(summary = "记录工作价值（仅本家庭家长）",
            description = "记录当月工资收入，并将发放金额入账本人账户。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004 CHILD 调用；"
                    + "100001 参数校验失败。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping
    public Result<WorkValueResponse> create(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody CreateWorkValueRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(workValueService.create(principal, request));
    }
}
