package wyq.pocket.money.common.resilience;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 韧性配置项（M3 设计 §7 / M4 设计 §8）。
 *
 * <p>M3 落地写接口限流（RateLimiter）；M4 落地 AI 调用稳定性
 * （CircuitBreaker / TimeLimiter / Retry，§8.1）。
 *
 * @param rateLimit 写接口限流配置
 * @param ai        AI 调用稳定性配置
 */
@ConfigurationProperties(prefix = "pocket-money.resilience")
public record ResilienceProperties(RateLimit rateLimit, Ai ai) {

    /**
     * 写接口限流配置（每用户）。
     *
     * @param limitForPeriod     刷新周期内允许的请求次数
     * @param limitRefreshPeriod 限流刷新周期
     */
    public record RateLimit(int limitForPeriod, Duration limitRefreshPeriod) {
    }

    /**
     * AI 调用稳定性配置（M4 设计 §8.1）。
     *
     * @param timeout                      AI 调用超时（对齐 tech-stack 30 秒）
     * @param circuitBreakerFailureRate    熔断失败率阈值（百分比 0-100）
     * @param circuitBreakerSlidingWindow  熔断滑动窗口大小（计数型）
     * @param retryMaxAttempts             最大尝试次数（含首次，仅限幂等查询）
     */
    public record Ai(Duration timeout, int circuitBreakerFailureRate,
                     int circuitBreakerSlidingWindow, int retryMaxAttempts) {
    }
}
