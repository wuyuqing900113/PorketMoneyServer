package wyq.pocket.money.common.resilience;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 韧性配置项（M3 设计 §7）。
 *
 * <p>M3 仅落地写接口限流（RateLimiter）；CircuitBreaker / TimeLimiter /
 * Retry 归 M4 接入 AI / ASR / TTS 时启用。
 *
 * @param rateLimit 写接口限流配置
 */
@ConfigurationProperties(prefix = "pocket-money.resilience")
public record ResilienceProperties(RateLimit rateLimit) {

    /**
     * 写接口限流配置（每用户）。
     *
     * @param limitForPeriod     刷新周期内允许的请求次数
     * @param limitRefreshPeriod 限流刷新周期
     */
    public record RateLimit(int limitForPeriod, Duration limitRefreshPeriod) {
    }
}
