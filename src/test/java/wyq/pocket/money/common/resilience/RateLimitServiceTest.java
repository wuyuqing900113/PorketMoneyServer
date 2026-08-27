package wyq.pocket.money.common.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * RateLimitService 单元测试：按用户维度的非阻塞令牌桶限流
 * （额度内放行、耗尽立即拒绝、用户间隔离，M3 设计 §7）。
 */
class RateLimitServiceTest {

    @Test
    void shouldAllowWithinLimitAndRejectWhenExhausted() {
        RateLimitService service = new RateLimitService(new ResilienceProperties(
                new ResilienceProperties.RateLimit(2, Duration.ofSeconds(60))));

        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isFalse();
    }

    @Test
    void shouldIsolateUsers() {
        RateLimitService service = new RateLimitService(new ResilienceProperties(
                new ResilienceProperties.RateLimit(1, Duration.ofSeconds(60))));

        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isFalse();
        assertThat(service.tryAcquire(2L)).isTrue();
    }

    @Test
    void shouldExposeRefreshPeriod() {
        RateLimitService service = new RateLimitService(new ResilienceProperties(
                new ResilienceProperties.RateLimit(10, Duration.ofMinutes(1))));

        assertThat(service.refreshPeriod()).isEqualTo(Duration.ofMinutes(1));
    }
}
