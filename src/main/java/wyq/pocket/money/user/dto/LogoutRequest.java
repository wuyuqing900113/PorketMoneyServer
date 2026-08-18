package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登出请求（M1 设计 §5.3）：吊销请求体中提交的 refresh 令牌。
 *
 * @param refreshToken 待吊销的 refresh 令牌
 */
public record LogoutRequest(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken) {
}
