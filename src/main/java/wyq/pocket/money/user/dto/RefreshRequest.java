package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求（M1 设计 §4.4）。
 *
 * @param refreshToken refresh 令牌
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken) {
}
