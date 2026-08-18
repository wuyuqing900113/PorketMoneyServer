package wyq.pocket.money.user.controller;

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
import wyq.pocket.money.user.dto.AddChildRequest;
import wyq.pocket.money.user.dto.ChildCreateResponse;
import wyq.pocket.money.user.dto.FamilyDetailResponse;
import wyq.pocket.money.user.dto.MemberSummary;
import wyq.pocket.money.user.dto.ResetChildPasswordRequest;
import wyq.pocket.money.user.dto.UpdateFamilyRequest;
import wyq.pocket.money.user.dto.UpdateNicknameRequest;
import wyq.pocket.money.user.service.FamilyService;

/**
 * 家庭域端点（M1 设计 §10.2 #9–#15，§6）。
 *
 * <p>双层守卫：接口级写操作限 PARENT（方法安全，CHILD 调用 403 + 100004）；
 * 数据级由 FamilyAccessChecker 断言在册成员身份，跨家庭访问 403 + 100004。
 */
@Tag(name = "家庭", description = "家庭信息与成员管理（写操作仅限本家庭 PARENT）")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {

    private final FamilyService familyService;

    /**
     * 注入家庭业务。
     *
     * @param familyService 家庭业务
     */
    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    /**
     * 家庭详情（§6.2 #9）：本家庭全员可见。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @return 家庭详情
     */
    @Operation(summary = "家庭详情",
            description = "本家庭全员可见（含孩子）。"
                    + "错误码：HTTP 401 + 100003 未认证；HTTP 403 + 100004 非本家庭成员；"
                    + "200005 家庭不存在；200010 mcp 门禁（孩子）。")
    @GetMapping("/{familyId}")
    public Result<FamilyDetailResponse> getFamily(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return Result.success(familyService.getFamily(familyId, principal));
    }

    /**
     * 修改家庭名（§6.2 #10，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param request   修改请求
     * @return 空响应
     */
    @Operation(summary = "修改家庭名（仅本家庭家长）",
            description = "错误码：HTTP 401 + 100003 未认证；HTTP 403 + 100004 CHILD 调用"
                    + "或跨家庭访问（双层守卫）；100001 参数校验失败；200005 家庭不存在。")
    @PreAuthorize("hasRole('PARENT')")
    @PutMapping("/{familyId}")
    public Result<Void> updateFamily(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody UpdateFamilyRequest request) {
        familyService.updateFamily(familyId, principal, request);
        return Result.success();
    }

    /**
     * 创建孩子账号（§6.3 #11，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param request   创建请求
     * @return 创建结果
     */
    @Operation(summary = "创建孩子账号（仅本家庭家长）",
            description = "孩子初始登录须强制改密（mcp）。"
                    + "错误码：HTTP 401 + 100003 未认证；HTTP 403 + 100004 CHILD 调用"
                    + "或跨家庭访问；100001 参数校验失败；200005 家庭不存在；"
                    + "200006 成员数达上限（8）；200007 登录名已存在。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{familyId}/children")
    public Result<ChildCreateResponse> addChild(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody AddChildRequest request) {
        return Result.success(familyService.addChild(familyId, principal, request));
    }

    /**
     * 家庭成员列表（§6.4 #12）：本家庭全员可见。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @return 成员摘要列表
     */
    @Operation(summary = "家庭成员列表",
            description = "本家庭全员可见（含孩子）。"
                    + "错误码：HTTP 401 + 100003 未认证；HTTP 403 + 100004 非本家庭成员；"
                    + "200005 家庭不存在；200010 mcp 门禁（孩子）。")
    @GetMapping("/{familyId}/members")
    public Result<List<MemberSummary>> listMembers(@PathVariable("familyId") long familyId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return Result.success(familyService.listMembers(familyId, principal));
    }

    /**
     * 修改孩子昵称（§6.4 #13，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param userId    孩子用户 ID
     * @param principal 当前登录主体
     * @param request   修改请求
     * @return 空响应
     */
    @Operation(summary = "修改孩子昵称（仅本家庭家长）",
            description = "错误码：HTTP 401 + 100003 未认证；HTTP 403 + 100004 CHILD 调用"
                    + "或跨家庭访问；100001 参数校验失败；200011 目标不是本家庭孩子。")
    @PreAuthorize("hasRole('PARENT')")
    @PutMapping("/{familyId}/children/{userId}")
    public Result<Void> updateChild(@PathVariable("familyId") long familyId,
            @PathVariable("userId") long userId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody UpdateNicknameRequest request) {
        familyService.updateChild(familyId, userId, principal, request);
        return Result.success();
    }

    /**
     * 重置孩子密码（§6.5 #14，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param userId    孩子用户 ID
     * @param principal 当前登录主体
     * @param request   重置请求
     * @return 空响应
     */
    @Operation(summary = "重置孩子密码（仅本家庭家长）",
            description = "重置后孩子下次登录须强制改密（mcp 重新置位），"
                    + "并吊销该孩子全部 refresh 令牌。"
                    + "错误码：HTTP 401 + 100003 未认证；HTTP 403 + 100004 CHILD 调用"
                    + "或跨家庭访问；100001 参数校验失败；200011 目标不是本家庭孩子。")
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{familyId}/children/{userId}/password-reset")
    public Result<Void> resetChildPassword(@PathVariable("familyId") long familyId,
            @PathVariable("userId") long userId,
            @AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody ResetChildPasswordRequest request) {
        familyService.resetChildPassword(familyId, userId, principal, request);
        return Result.success();
    }

    /**
     * 移除成员（§6.4 #15，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param userId    目标成员用户 ID
     * @param principal 当前登录主体
     * @return 空响应
     */
    @Operation(summary = "移除成员（仅本家庭家长）",
            description = "被移除孩子账号停用（DISABLED）、全部会话吊销，无法再登录。"
                    + "错误码：HTTP 401 + 100003 未认证；HTTP 403 + 100004 CHILD 调用"
                    + "或跨家庭访问；200011 目标不是本家庭成员；200012 不得移除家庭主自己。")
    @PreAuthorize("hasRole('PARENT')")
    @DeleteMapping("/{familyId}/members/{userId}")
    public Result<Void> removeMember(@PathVariable("familyId") long familyId,
            @PathVariable("userId") long userId,
            @AuthenticationPrincipal UserIdPrincipal principal) {
        familyService.removeMember(familyId, userId, principal);
        return Result.success();
    }
}
