package wyq.pocket.money.ai.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.stereotype.Component;

import wyq.pocket.money.common.ai.AiProperties;

/**
 * AI 调用限流服务（M4 设计 §8.2）。
 *
 * <p>复用 {@code RateLimitService} 的每用户令牌桶 + 非阻塞
 * {@code timeoutDuration=ZERO} 模式：以用户为维度惰性创建限流器，
 * 额度耗尽立即拒绝（返回 100007 + Retry-After），防止单用户高频调用
 * 消耗 AI token 额度（R6）。
 */
@Component
public class AiRateLimitService {

    private final ConcurrentMap<Long, RateLimiter> limiters = new ConcurrentHashMap<>();

    private final RateLimiterConfig config;

    private final Duration refreshPeriod;

    /**
     * 由 AI 配置构建限流器模板。
     *
     * @param properties AI 配置（rate-limit 段）
     */
    public AiRateLimitService(AiProperties properties) {
        AiProperties.RateLimit rateLimit = properties.rateLimit();
        this.config = RateLimiterConfig.custom()
                .timeoutDuration(Duration.ZERO)
                .limitForPeriod(rateLimit.limitForPeriod())
                .limitRefreshPeriod(rateLimit.limitRefreshPeriod())
                .build();
        this.refreshPeriod = rateLimit.limitRefreshPeriod();
    }

    /**
     * 非阻塞尝试为指定用户获取一次许可。
     *
     * @param userId 用户 ID
     * @return 获取成功返回 true；超出限额返回 false
     */
    public boolean tryAcquire(long userId) {
        RateLimiter limiter = limiters.computeIfAbsent(userId,
                id -> RateLimiter.of("ai-" + id, config));
        return limiter.acquirePermission();
    }

    /**
     * 限流刷新周期（供 Retry-After 计算）。
     *
     * @return 刷新周期
     */
    public Duration refreshPeriod() {
        return refreshPeriod;
    }
}
