package wyq.pocket.money.common.resilience;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 韧性配置装配（M3 设计 §7）。
 *
 * <p>仅负责将 {@link ResilienceProperties} 注册为配置属性 Bean，
 * 与 IdempotencyConfig 的 {@code @EnableConfigurationProperties} 惯例一致。
 */
@Configuration
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceConfig {
}
