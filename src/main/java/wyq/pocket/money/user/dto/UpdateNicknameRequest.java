package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改昵称请求（M1 设计 §5.5）。
 *
 * @param nickname 新昵称（1–32 字）
 */
public record UpdateNicknameRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(min = 1, max = 32, message = "昵称长度须为 1-32 字")
        String nickname) {
}
