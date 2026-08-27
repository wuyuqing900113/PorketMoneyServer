package wyq.pocket.money.ai.service;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.stereotype.Component;

import wyq.pocket.money.ai.dto.AiErrorCode;
import wyq.pocket.money.common.ai.ChatPort;
import wyq.pocket.money.common.ai.IntentResult;
import wyq.pocket.money.common.ai.ToolDefinition;
import wyq.pocket.money.common.exception.BusinessException;

/**
 * AI 意图解析调用器（M4 设计 §8）：隔离 {@link ChatPort} 调用边界。
 *
 * <p>以 Resilience4j 装饰器包裹 {@code ChatPort.parseIntent}：Retry（幂等
 * 查询重试，指数退避）内层，TimeLimiter（超时）中层，CircuitBreaker（熔断）
 * 外层。装饰器抛出的熔断 / 超时 / 重试耗尽异常统一落
 * {@code AI_UNAVAILABLE(600001)} 降级出口。
 */
@Component
public class AiInvoker {

    private final ChatPort chatPort;

    private final CircuitBreaker aiCircuitBreaker;

    private final TimeLimiter aiTimeLimiter;

    private final Retry aiRetry;

    /**
     * 注入对话端口与韧性装饰器。
     *
     * @param chatPort         对话端口
     * @param aiCircuitBreaker AI 熔断器
     * @param aiTimeLimiter    AI 超时器
     * @param aiRetry          AI 重试器
     */
    public AiInvoker(ChatPort chatPort, CircuitBreaker aiCircuitBreaker,
                     TimeLimiter aiTimeLimiter, Retry aiRetry) {
        this.chatPort = chatPort;
        this.aiCircuitBreaker = aiCircuitBreaker;
        this.aiTimeLimiter = aiTimeLimiter;
        this.aiRetry = aiRetry;
    }

    /**
     * 解析用户指令（统一降级出口）。
     *
     * @param text  用户指令文本
     * @param tools 工具定义清单
     * @return 解析结果
     * @throws BusinessException 600001 AI 不可用（ChatPort 失败 / 超时 / 熔断）
     */
    public IntentResult invoke(String text, List<ToolDefinition> tools) {
        Supplier<IntentResult> retried = Retry.decorateSupplier(aiRetry,
                () -> chatPort.parseIntent(text, tools));
        Callable<IntentResult> timed = TimeLimiter.decorateFutureSupplier(aiTimeLimiter,
                () -> CompletableFuture.supplyAsync(retried));
        Callable<IntentResult> guarded = CircuitBreaker.decorateCallable(aiCircuitBreaker, timed);
        try {
            return guarded.call();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(AiErrorCode.AI_UNAVAILABLE, "AI 服务不可用", e);
        }
    }
}
