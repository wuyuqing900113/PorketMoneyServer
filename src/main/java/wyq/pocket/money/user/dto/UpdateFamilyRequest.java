package wyq.pocket.money.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改家庭名请求（M1 设计 §6.2，仅家长）。
 *
 * @param familyName 新家庭名（1–32 字）
 */
public record UpdateFamilyRequest(
        @NotBlank(message = "家庭名不能为空")
        @Size(min = 1, max = 32, message = "家庭名长度须为 1-32 字")
        String familyName) {
}
