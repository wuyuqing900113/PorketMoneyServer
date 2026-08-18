package wyq.pocket.money.common.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项（M1 设计 §4.3、§11）。
 *
 * <p>密钥经环境变量 {@code JWT_SECRET} 注入（Base64，≥32 字节），
 * 严禁硬编码（mission.md 禁止项）；TTL 为 ISO-8601 Duration
 * （如 {@code PT15M}、{@code P14D}）。
 *
 * @param secret     HS256 签名密钥（Base64 编码）
 * @param accessTtl  access 令牌有效期
 * @param refreshTtl refresh 令牌有效期
 */
@ConfigurationProperties(prefix = "pocket-money.security.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
