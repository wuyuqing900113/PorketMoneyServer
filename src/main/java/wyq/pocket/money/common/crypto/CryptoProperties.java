package wyq.pocket.money.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 敏感数据加密配置项（M1 设计 §11）。
 *
 * <p>密钥经环境变量 {@code DATA_ENCRYPTION_KEY} 注入（Base64，32 字节），
 * 严禁硬编码（mission.md 禁止项）。
 *
 * @param dataKey AES-256 密钥（Base64 编码，32 字节原文）
 */
@ConfigurationProperties(prefix = "pocket-money.crypto")
public record CryptoProperties(String dataKey) {
}
