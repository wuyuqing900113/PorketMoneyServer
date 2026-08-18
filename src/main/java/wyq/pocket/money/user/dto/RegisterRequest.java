package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import wyq.pocket.money.common.validation.StrongPassword;

/**
 * 家长注册请求（M1 设计 §5.1）。
 *
 * @param phone                    手机号（大陆 11 位）
 * @param password                 密码（≥8 位且含字母与数字）
 * @param nickname                 昵称（1–32 字）
 * @param childPrivacyPolicyAccepted 儿童隐私政策同意（COPPA 类合规留痕，必须为 true）
 */
public record RegisterRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "密码不能为空")
        @StrongPassword
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(min = 1, max = 32, message = "昵称长度须为 1-32 字")
        String nickname,

        @AssertTrue(message = "须同意儿童隐私政策")
        Boolean childPrivacyPolicyAccepted) {
}
