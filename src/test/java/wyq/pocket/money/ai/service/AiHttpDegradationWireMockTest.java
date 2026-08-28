package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import wyq.pocket.money.ai.dto.AiErrorCode;
import wyq.pocket.money.common.ai.IntentResult;
import wyq.pocket.money.common.ai.ToolDefinition;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.support.AiProviderWireMock;
import wyq.pocket.money.support.WireMockChatPort;

/**
 * AI 意图链路 HTTP 降级与熔断专项测试（M6 设计 §5.1 D49）：以 WireMock 在 HTTP
 * 层注入超时 / 5xx / 畸形响应，验证 {@link AiInvoker} 统一降级 600001，以及
 * 熔断器 OPEN → HALF_OPEN → CLOSED 的完整状态迁移。
 */
class AiHttpDegradationWireMockTest {

    private static final List<ToolDefinition> TOOLS = List.of();

    /** 熔断开路后进入半开的等待窗口（远小于 Resilience4j 默认 60s）。 */
    private static final Duration OPEN_STATE_WAIT = Duration.ofMillis(300);

    /** 熔断滑动窗口与最小调用数：4 次失败即可触发开路，避免低流量误判。 */
    private static final int WINDOW_SIZE = 4;

    private final AiProviderWireMock provider = new AiProviderWireMock();

    @AfterEach
    void tearDown() {
        provider.stop();
    }

    @Test
    void shouldDegradeOnProviderTimeout() {
        provider.stubTimeout();
        AiInvoker invoker = invoker(circuitBreaker(), Duration.ofMillis(200));

        assertThatThrownBy(() -> invoker.invoke("查余额", TOOLS))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_UNAVAILABLE));
    }

    @Test
    void shouldDegradeOnHttpServerError() {
        provider.stubServerError();
        AiInvoker invoker = invoker(circuitBreaker(), Duration.ofSeconds(1));

        assertThatThrownBy(() -> invoker.invoke("查余额", TOOLS))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_UNAVAILABLE));
    }

    @Test
    void shouldDegradeOnMalformedBody() {
        provider.stubMalformed();
        AiInvoker invoker = invoker(circuitBreaker(), Duration.ofSeconds(1));

        assertThatThrownBy(() -> invoker.invoke("查余额", TOOLS))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_UNAVAILABLE));
    }

    @Test
    void shouldOpenThenHalfOpenThenCloseCircuit() throws Exception {
        CircuitBreaker breaker = circuitBreaker();
        AiInvoker invoker = invoker(breaker, Duration.ofSeconds(1));
        provider.stubServerError();

        for (int i = 0; i < WINDOW_SIZE; i++) {
            assertThatThrownBy(() -> invoker.invoke("查余额", TOOLS))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        provider.stubSuccess();
        Thread.sleep(OPEN_STATE_WAIT.toMillis() + 100);

        IntentResult result = invoker.invoke("查余额", TOOLS);
        assertThat(result.toolName()).isEqualTo("BALANCE_QUERY");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private AiInvoker invoker(CircuitBreaker breaker, Duration timeout) {
        return new AiInvoker(new WireMockChatPort(provider.endpoint()), breaker,
                TimeLimiter.of("test-time", TimeLimiterConfig.custom()
                        .timeoutDuration(timeout).build()),
                Retry.of("test-retry", RetryConfig.custom().maxAttempts(2)
                        .waitDuration(Duration.ofMillis(1)).build()));
    }

    private static CircuitBreaker circuitBreaker() {
        return CircuitBreaker.of("test-breaker", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(WINDOW_SIZE)
                .minimumNumberOfCalls(WINDOW_SIZE)
                .waitDurationInOpenState(OPEN_STATE_WAIT)
                // 半开态仅放行 1 次探测：成功即闭路，便于在单测内走完 OPEN→HALF_OPEN→CLOSED
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
    }
}
