package wyq.pocket.money.notify.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知配置项（M5 设计 §11）。
 *
 * @param enabled             通知总开关（站内信落库总闸）
 * @param lowBalanceThreshold 余额不足提醒阈值（0 = 关闭）
 * @param push                外部推送通道配置
 * @param relay               投递重试配置
 * @param cleanup             已读清理配置
 */
@ConfigurationProperties(prefix = "pocket-money.notify")
public record NotifyProperties(boolean enabled, BigDecimal lowBalanceThreshold, Push push,
                               Relay relay, Cleanup cleanup) {

    /**
     * 外部推送通道配置（鸿蒙 Push 待选型）。
     *
     * @param enabled 通道开关：true 时通知创建同步落 PENDING delivery 行
     */
    public record Push(boolean enabled) {
    }

    /**
     * 投递重试配置。
     *
     * @param enabled      投递任务开关
     * @param cron         投递任务 cron（6 段式）
     * @param maxRetry     最大重试次数（达上限置 DEAD）
     * @param retryBackoff 指数退避初始间隔
     */
    public record Relay(boolean enabled, String cron, int maxRetry, Duration retryBackoff) {
    }

    /**
     * 已读通知清理配置。
     *
     * @param enabled 清理任务开关
     * @param cron    清理任务 cron（6 段式）
     * @param readTtl 已读通知保留期
     */
    public record Cleanup(boolean enabled, String cron, Duration readTtl) {
    }
}
