package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.ai.AiProperties;

/**
 * AI 调用限流单元测试（M4 设计 §8.2）：每用户非阻塞令牌桶，额度内放行、
 * 耗尽立即拒绝、用户间隔离。
 */
class AiRateLimitServiceTest {

    @Test
    void shouldAllowWithinLimitAndRejectWhenExhausted() {
        AiRateLimitService service = new AiRateLimitService(
                properties(2, Duration.ofSeconds(60)));

        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isFalse();
    }

    @Test
    void shouldIsolateUsers() {
        AiRateLimitService service = new AiRateLimitService(
                properties(1, Duration.ofSeconds(60)));

        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isFalse();
        assertThat(service.tryAcquire(2L)).isTrue();
    }

    @Test
    void shouldExposeRefreshPeriod() {
        AiRateLimitService service = new AiRateLimitService(
                properties(10, Duration.ofMinutes(1)));

        assertThat(service.refreshPeriod()).isEqualTo(Duration.ofMinutes(1));
    }

    private static AiProperties properties(int limit, Duration period) {
        return new AiProperties(true, "TEXT", Duration.ofSeconds(60), Duration.ofDays(7),
                true, "0 43 4 * * *", new AiProperties.RateLimit(limit, period),
                new AiProperties.Stub(false));
    }
}
