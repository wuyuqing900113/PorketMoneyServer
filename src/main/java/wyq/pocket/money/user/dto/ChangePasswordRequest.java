package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.NotBlank;
import wyq.pocket.money.common.validation.StrongPassword;

/**
 * 自助修改密码请求（M1 设计 §5.4）；孩子首次改密同此端点。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码（≥8 位且含字母与数字）
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @StrongPassword
        String newPassword) {
}
