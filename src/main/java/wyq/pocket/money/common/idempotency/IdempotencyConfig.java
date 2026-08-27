package wyq.pocket.money.common.idempotency;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 幂等配置装配（M3 设计 §5）。
 *
 * <p>仅负责将 {@link IdempotencyProperties} 注册为配置属性 Bean，
 * 沿用 SecurityConfig 的 {@code @EnableConfigurationProperties} 显式注册惯例。
 */
@Configuration
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfig {
}
