package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import wyq.pocket.money.ai.dto.AiErrorCode;
import wyq.pocket.money.common.ai.IntentResult;
import wyq.pocket.money.common.ai.StubChatPort;
import wyq.pocket.money.common.ai.ToolDefinition;
import wyq.pocket.money.common.exception.BusinessException;

/**
 * AI 意图解析调用器单元测试（M4 设计 §8）：Retry → TimeLimiter →
 * CircuitBreaker 装饰链；成功直返解析结果，provider 失败统一降级 600001。
 */
class AiInvokerTest {

    private static final List<ToolDefinition> TOOLS = List.of();

    @Test
    void shouldReturnParsedIntentOnSuccess() {
        AiInvoker invoker = new AiInvoker(new StubChatPort(false), circuitBreaker(), timeLimiter(),
                retry());

        IntentResult result = invoker.invoke("查一下余额", TOOLS);

        assertThat(result.toolName()).isEqualTo("BALANCE_QUERY");
    }

    @Test
    void shouldDegradeOnProviderFailure() {
        AiInvoker invoker = new AiInvoker(new StubChatPort(true), circuitBreaker(), timeLimiter(),
                retry());

        assertThatThrownBy(() -> invoker.invoke("查余额", TOOLS))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_UNAVAILABLE));
    }

    private static CircuitBreaker circuitBreaker() {
        return CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .failureRateThreshold(50).slidingWindowSize(10).minimumNumberOfCalls(10).build());
    }

    private static TimeLimiter timeLimiter() {
        return TimeLimiter.of("test", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30)).build());
    }

    private static Retry retry() {
        return Retry.of("test", RetryConfig.custom().maxAttempts(2)
                .waitDuration(Duration.ofMillis(1)).build());
    }
}
