package wyq.pocket.money.notify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 推送令牌注册请求（GA D68）：鸿蒙客户端上报 HMS Push Kit 设备令牌。
 *
 * @param deviceToken 设备推送令牌（HMS Core 获取，非空，最长 256）
 */
public record PushTokenRegisterRequest(
        @NotBlank @Size(max = 256) String deviceToken) {
}
