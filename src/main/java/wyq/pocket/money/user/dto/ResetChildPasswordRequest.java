package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.NotBlank;
import wyq.pocket.money.common.validation.StrongPassword;

/**
 * 家长重置孩子密码请求（M1 设计 §6.5 / §10.2 #14）。
 *
 * <p>重置后新密码成为孩子的初始密码：mcp 重新生效，
 * 孩子下次登录须再次修改密码，且既有会话全部吊销（§4.3）。
 *
 * @param newPassword 新密码（≥8 位且含字母与数字）
 */
public record ResetChildPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @StrongPassword
        String newPassword) {
}
