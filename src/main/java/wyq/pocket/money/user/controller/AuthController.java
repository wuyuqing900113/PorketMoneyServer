package wyq.pocket.money.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
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
import wyq.pocket.money.user.dto.LoginRequest;
import wyq.pocket.money.user.dto.LoginResponse;
import wyq.pocket.money.user.dto.LogoutRequest;
import wyq.pocket.money.user.dto.RefreshRequest;
import wyq.pocket.money.user.dto.RegisterRequest;
import wyq.pocket.money.user.dto.RegisterResponse;
import wyq.pocket.money.user.dto.TokenPairResponse;
import wyq.pocket.money.user.service.AuthService;

/**
 * 认证端点（M1 设计 §10.2 #1–#4）：注册 / 登录 / 刷新匿名可达，
 * 登出需 Bearer access 令牌（白名单见 SecurityConfig）。
 */
@Tag(name = "认证", description = "注册 / 登录 / 刷新 / 登出（双令牌 JWT）")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 注入认证业务。
     *
     * @param authService 认证业务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 家长注册（自动建家庭，§5.1）。
     *
     * @param request 注册请求
     * @return 注册结果
     */
    @Operation(summary = "家长注册（自动建家庭）",
            description = "公开端点。注册成功即创建家庭并返回 userId/familyId。"
                    + "错误码：100001 参数校验失败；200001 手机号已注册。")
    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 登录：家长手机号 / 孩子登录名统一入口（§5.2）。
     *
     * @param request 登录请求
     * @return 令牌对与用户摘要
     */
    @Operation(summary = "登录（家长手机号 / 孩子登录名统一入口）",
            description = "公开端点。成功返回 access/refresh 令牌对。"
                    + "错误码：200002 账号不存在或密码错误（防枚举统一返回）；"
                    + "200003 账号锁定中；200004 账号已停用。")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 刷新令牌（轮转，§4.4）。
     *
     * @param request 刷新请求
     * @return 新令牌对
     */
    @Operation(summary = "刷新令牌（轮转）",
            description = "公开端点。旧 refresh 即刻作废并签发新令牌对；"
                    + "已作废令牌重放触发重用检测（全量吊销 + 安全告警）。"
                    + "错误码：100003 令牌非法/过期/已作废（原因不可区分）。")
    @PostMapping("/refresh")
    public Result<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request));
    }

    /**
     * 登出：吊销请求体中的 refresh 令牌（§5.3）。
     *
     * @param principal 当前登录主体
     * @param request   登出请求
     * @return 空响应
     */
    @Operation(summary = "登出（吊销 refresh 令牌）",
            description = "幂等：令牌非法或已失效时静默成功。"
                    + "错误码：HTTP 401 + 100003 未携带有效 access 令牌。",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME))
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal UserIdPrincipal principal,
                               @Valid @RequestBody LogoutRequest request) {
        authService.logout(principal, request);
        return Result.success();
    }
}
