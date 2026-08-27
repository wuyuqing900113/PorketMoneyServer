package wyq.pocket.money.rule.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.OpenApiConfig;
import wyq.pocket.money.common.web.Result;
import wyq.pocket.money.rule.dto.CreateRuleRequest;
import wyq.pocket.money.rule.dto.RuleDetailResponse;
import wyq.pocket.money.rule.dto.RuleResponse;
import wyq.pocket.money.rule.dto.UpdateRuleRequest;
import wyq.pocket.money.rule.service.RuleService;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 包月规则端点（M2 设计 §8.2 #7–#14，资源挂在家庭路径下）。
 *
 * <p>读端点家庭内全透明；写端点接口级限 PARENT。
 * 入口统一校验路径 familyId 的成员资格（跨家庭 100004）。
 */
@Tag(name = "包月规则", description = "按月定额发放规则的增删改查与状态管理")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/families/{familyId}/rules")
public class RuleController {

    private final RuleService ruleService;

    private final FamilyAccessChecker familyAccessChecker;

    /**
     * 注入规则业务。
     *
     * @param ruleService         规则业务
     * @param familyAccessChecker 数据级访问守卫（路径 familyId 成员校验）
     */
    public RuleController(RuleService ruleService, FamilyAccessChecker familyAccessChecker) {
        this.ruleService = ruleService;
        this.familyAccessChecker = familyAccessChecker;
    }

    /**
     * 创建规则（#7，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param request   创建请求
     * @return 规则响应
     */
    @Operation(summary = "创建包月规则（仅本家庭家长）",
            description = "错误码：HTTP 401 + 100003；HTTP 403 + 100004 CHILD 调用"
                    + "或受益人非本家庭成员；100001 参数校验失败（含月份顺序）；"
                    + "400004 规则上限；400006 名称重复。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping
    public Result<RuleResponse> create(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody CreateRuleRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(ruleService.create(principal, request));
    }

    /**
     * 规则列表（#8）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @return 规则列表（含当月已发放标记）
     */
    @Operation(summary = "包月规则列表",
            description = "家庭内全透明读，含「当月已发放」标记。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004。")
    @GetMapping
    public Result<List<RuleResponse>> list(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(ruleService.list(principal));
    }

    /**
     * 规则详情（#9）。
     *
     * @param familyId  家庭 ID
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 规则详情（含近 12 个月发放记录）
     */
    @Operation(summary = "包月规则详情",
            description = "含近 12 个月发放记录。错误码：HTTP 401 + 100003；"
                    + "HTTP 403 + 100004；400001 规则不存在。")
    @GetMapping("/{ruleId}")
    public Result<RuleDetailResponse> detail(@PathVariable("familyId") long familyId,
            @PathVariable("ruleId") long ruleId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(ruleService.detail(ruleId, principal));
    }

    /**
     * 修改规则（#10，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @param request   修改请求
     * @return 规则响应
     */
    @Operation(summary = "修改包月规则（仅本家庭家长）",
            description = "起始月不可改。错误码：HTTP 401 + 100003；HTTP 403 + 100004；"
                    + "100001 参数校验失败；400001 规则不存在；400006 名称重复。")
    @PreAuthorize("hasRole('PARENT')")
    @PutMapping("/{ruleId}")
    public Result<RuleResponse> update(@PathVariable("familyId") long familyId,
            @PathVariable("ruleId") long ruleId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody UpdateRuleRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(ruleService.update(ruleId, principal, request));
    }

    /**
     * 暂停规则（#11，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 规则响应
     */
    @Operation(summary = "暂停规则（仅本家庭家长）",
            description = "ACTIVE → PAUSED。错误码：HTTP 401 + 100003；HTTP 403 + 100004；"
                    + "400001 规则不存在；400002 状态不允许。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{ruleId}/pause")
    public Result<RuleResponse> pause(@PathVariable("familyId") long familyId,
            @PathVariable("ruleId") long ruleId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(ruleService.pause(ruleId, principal));
    }

    /**
     * 恢复规则（#12，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 规则响应
     */
    @Operation(summary = "恢复规则（仅本家庭家长）",
            description = "PAUSED → ACTIVE。错误码：HTTP 401 + 100003；HTTP 403 + 100004；"
                    + "400001 规则不存在；400002 状态不允许。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{ruleId}/resume")
    public Result<RuleResponse> resume(@PathVariable("familyId") long familyId,
            @PathVariable("ruleId") long ruleId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(ruleService.resume(ruleId, principal));
    }

    /**
     * 归档规则（#13，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 规则响应
     */
    @Operation(summary = "归档规则（仅本家庭家长）",
            description = "ACTIVE / PAUSED → ARCHIVED（终态）。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004；"
                    + "400001 规则不存在；400002 状态不允许。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{ruleId}/archive")
    public Result<RuleResponse> archive(@PathVariable("familyId") long familyId,
            @PathVariable("ruleId") long ruleId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        return Result.success(ruleService.archive(ruleId, principal));
    }

    /**
     * 删除规则（#14，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param ruleId    规则 ID
     * @param principal 当前登录主体
     * @return 空响应
     */
    @Operation(summary = "删除规则（仅本家庭家长）",
            description = "有发放记录的规则不可删除（应归档）。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004；"
                    + "400001 规则不存在；400005 有发放记录。")
    @PreAuthorize("hasRole('PARENT')")
    @DeleteMapping("/{ruleId}")
    public Result<Void> delete(@PathVariable("familyId") long familyId,
            @PathVariable("ruleId") long ruleId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        ruleService.delete(ruleId, principal);
        return Result.success();
    }
}
