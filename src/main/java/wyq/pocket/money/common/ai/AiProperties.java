package wyq.pocket.money.common.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 配置项（M4 设计 §12）。
 *
 * @param mock           是否使用默认桩（StubChatPort）
 * @param channelDefault 默认通道（TEXT / VOICE，M4 仅 TEXT）
 * @param pendingTtl     待确认动作存活期（默认 60 秒）
 * @param sessionTtl     会话 / 消息保留期（默认 7 天）
 * @param cleanupEnabled 会话清理任务开关
 * @param cleanupCron    清理任务 cron
 * @param rateLimit      AI 调用限流配置
 * @param stub           桩开关（fail=true 模拟 provider 不可用）
 */
@ConfigurationProperties(prefix = "pocket-money.ai")
public record AiProperties(boolean mock, String channelDefault, Duration pendingTtl,
                           Duration sessionTtl, boolean cleanupEnabled, String cleanupCron,
                           RateLimit rateLimit, Stub stub) {

    /**
     * AI 调用限流配置（每用户）。
     *
     * @param limitForPeriod     刷新周期内允许的请求次数
     * @param limitRefreshPeriod 限流刷新周期
     */
    public record RateLimit(int limitForPeriod, Duration limitRefreshPeriod) {
    }

    /**
     * 桩开关。
     *
     * @param fail 模拟 provider 不可用（降级演练）
     */
    public record Stub(boolean fail) {
    }
}
