package wyq.pocket.money.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
import wyq.pocket.money.user.dto.ChangePasswordRequest;
import wyq.pocket.money.user.dto.FamilyDetailResponse;
import wyq.pocket.money.user.dto.UpdateNicknameRequest;
import wyq.pocket.money.user.dto.UserMeResponse;
import wyq.pocket.money.user.service.FamilyService;
import wyq.pocket.money.user.service.UserService;

/**
 * 用户个人信息端点（M1 设计 §10.2 #5–#8），家长与孩子均可访问自身数据。
 */
@Tag(name = "用户", description = "个人信息查看与编辑、密码修改（本人数据，家长与孩子均可）")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    private final FamilyService familyService;

    /**
     * 注入用户与家庭业务。
     *
     * @param userService   用户业务
     * @param familyService 家庭业务
     */
    public UserController(UserService userService, FamilyService familyService) {
        this.userService = userService;
        this.familyService = familyService;
    }

    /**
     * 当前用户信息（§5.5）。
     *
     * @param principal 当前登录主体
     * @return 用户信息（手机号脱敏）
     */
    @Operation(summary = "当前用户信息",
            description = "手机号仅回显脱敏形式；孩子账号无手机号字段值。"
                    + "错误码：HTTP 401 + 100003 未认证；"
                    + "HTTP 200 + 200010 孩子须先修改初始密码（mcp 门禁）。")
    @GetMapping("/me")
    public Result<UserMeResponse> me(@AuthenticationPrincipal UserIdPrincipal principal) {
        return Result.success(userService.getMe(principal));
    }

    /**
     * 当前用户所属家庭（§10.2 #8）：家庭详情含成员列表，全员可见。
     *
     * @param principal 当前登录主体
     * @return 家庭详情
     */
    @Operation(summary = "当前用户所属家庭",
            description = "家庭详情含成员列表，本家庭全员可见。"
                    + "错误码：HTTP 401 + 100003 未认证；"
                    + "HTTP 200 + 200005 家庭不存在；200010 mcp 门禁（孩子）。")
    @GetMapping("/me/family")
    public Result<FamilyDetailResponse> myFamily(
            @AuthenticationPrincipal UserIdPrincipal principal) {
        return Result.success(familyService.getMyFamily(principal));
    }

    /**
     * 修改昵称（§5.5）。
     *
     * @param principal 当前登录主体
     * @param request   修改请求
     * @return 空响应
     */
    @Operation(summary = "修改本人昵称",
            description = "错误码：HTTP 401 + 100003 未认证；"
                    + "100001 参数校验失败；200010 mcp 门禁（孩子）。")
    @PutMapping("/me")
    public Result<Void> updateMe(@AuthenticationPrincipal UserIdPrincipal principal,
                                 @Valid @RequestBody UpdateNicknameRequest request) {
        userService.updateNickname(principal, request);
        return Result.success();
    }

    /**
     * 修改密码（§5.4；孩子首次改密同此端点，过滤链 mcp 豁免）。
     *
     * @param principal 当前登录主体
     * @param request   修改请求
     * @return 空响应
     */
    @Operation(summary = "修改本人密码",
            description = "孩子首次登录强制改密（mcp）亦走本端点，为 mcp 门禁豁免项。"
                    + "成功后吊销该用户全部 refresh 令牌。"
                    + "错误码：HTTP 401 + 100003 未认证；"
                    + "100001 参数校验失败；200008 原密码不正确。")
    @PostMapping("/me/password")
    public Result<Void> changePassword(@AuthenticationPrincipal UserIdPrincipal principal,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal, request);
        return Result.success();
    }
}
