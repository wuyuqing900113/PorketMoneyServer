package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求（M1 设计 §5.2）：家长手机号 / 孩子登录名统一入口。
 *
 * @param identifier 手机号（11 位数字）或孩子登录名
 * @param password   密码
 */
public record LoginRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 64, message = "账号长度超限")
        String identifier,

        @NotBlank(message = "密码不能为空")
        String password) {
}
