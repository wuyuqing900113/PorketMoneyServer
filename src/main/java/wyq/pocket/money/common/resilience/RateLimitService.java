package wyq.pocket.money.common.resilience;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.stereotype.Component;

/**
 * 写接口限流服务（M3 设计 §7）。
 *
 * <p>以用户为维度维护令牌桶限流器：每个用户一个 {@link RateLimiter}，
 * 非阻塞尝试获取许可，耗尽即拒绝（返回 100007 + Retry-After）。
 * 限流器按用户惰性创建；家庭理财应用活跃用户有限，缓存增长可接受，
 * 生产化驱逐归后续里程碑。
 */
@Component
public class RateLimitService {

    private final ConcurrentMap<Long, RateLimiter> limiters = new ConcurrentHashMap<>();

    private final RateLimiterConfig config;

    private final Duration refreshPeriod;

    /**
     * 由配置构建限流器模板。
     *
     * @param properties 韧性配置
     */
    public RateLimitService(ResilienceProperties properties) {
        ResilienceProperties.RateLimit rateLimit = properties.rateLimit();
        // timeoutDuration 置零：acquirePermission 默认会阻塞至多 timeoutDuration
        // （默认 5s）等待未来许可；过滤器必须在额度耗尽时立即拒绝，故置零使其
        // 非阻塞（额度可用返回 true，耗尽立即返回 false）。
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
                id -> RateLimiter.of("user-" + id, config));
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
