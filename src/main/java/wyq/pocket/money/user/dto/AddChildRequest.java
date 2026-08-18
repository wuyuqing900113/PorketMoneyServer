package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import wyq.pocket.money.common.validation.StrongPassword;

/**
 * 家长创建孩子账号请求（M1 设计 §6.3）。
 *
 * <p>COPPA 类合规：孩子账号不采集手机号 / 邮箱，仅登录名 + 初始密码；
 * 初始密码由孩子首次登录后自行修改（mcp=true，§4.6）。
 *
 * @param username 登录名（全局唯一，仅小写字母与数字，4–20 位，§6.1）
 * @param password 初始密码（≥8 位且含字母与数字）
 * @param nickname 昵称（1–32 字）
 */
public record AddChildRequest(
        @NotBlank(message = "登录名不能为空")
        @Size(min = 4, max = 20, message = "登录名长度须为 4-20 位")
        @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "登录名仅允许小写字母与数字")
        String username,

        @NotBlank(message = "密码不能为空")
        @StrongPassword
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(min = 1, max = 32, message = "昵称长度须为 1-32 字")
        String nickname) {
}
