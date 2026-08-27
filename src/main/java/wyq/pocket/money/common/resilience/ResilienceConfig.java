package wyq.pocket.money.common.resilience;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 韧性配置装配（M3 设计 §7 / M4 设计 §8.1）。
 *
 * <p>M3 仅注册 {@link ResilienceProperties} 配置 Bean；M4 真正注册
 * AI 意图解析链路所需的 CircuitBreaker / TimeLimiter / Retry 三实例，
 * 兑现 M3 设计 §7.3 承诺的稳定性骨架。
 */
@Configuration
@EnableConfigurationProperties(ResilienceProperties.class)
public class ResilienceConfig {

    /** Retry 指数退避初始间隔（固定 500ms，未外化为配置项）。 */
    private static final Duration RETRY_INITIAL_INTERVAL = Duration.ofMillis(500);

    /**
     * AI 意图解析熔断器：滑动窗口内失败率达到阈值即开启，进入半开探测。
     *
     * @param properties 韧性配置（AI 段）
     * @return CircuitBreaker Bean
     */
    @Bean
    public CircuitBreaker aiCircuitBreaker(ResilienceProperties properties) {
        ResilienceProperties.Ai ai = properties.ai();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(ai.circuitBreakerFailureRate())
                .slidingWindowSize(ai.circuitBreakerSlidingWindow())
                // 最小调用数对齐滑动窗口，避免默认 100 使低流量家庭应用永不开路
                .minimumNumberOfCalls(ai.circuitBreakerSlidingWindow())
                .build();
        return CircuitBreaker.of("aiCircuitBreaker", config);
    }

    /**
     * AI 意图解析超时器：阻塞调用超过超时阈值即失败。
     *
     * @param properties 韧性配置（AI 段）
     * @return TimeLimiter Bean
     */
    @Bean
    public TimeLimiter aiTimeLimiter(ResilienceProperties properties) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(properties.ai().timeout())
                .build();
        return TimeLimiter.of("aiTimeLimiter", config);
    }

    /**
     * AI 意图解析重试器：指数退避，仅限幂等查询类调用。
     *
     * @param properties 韧性配置（AI 段）
     * @return Retry Bean
     */
    @Bean
    public Retry aiRetry(ResilienceProperties properties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.ai().retryMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(RETRY_INITIAL_INTERVAL))
                .build();
        return Retry.of("aiRetry", config);
    }
}
