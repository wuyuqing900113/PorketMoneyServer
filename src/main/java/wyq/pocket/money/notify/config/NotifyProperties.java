package wyq.pocket.money.notify.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知配置项（M5 设计 §11 / GA D68 鸿蒙 Push Kit 凭据）。
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
     * 外部推送通道配置（GA D68：鸿蒙 Push Kit）。
     *
     * @param enabled 通道开关：true 时通知创建同步落 PENDING delivery 行
     * @param harmony 鸿蒙 Push Kit 服务端凭据与端点
     */
    public record Push(boolean enabled, Harmony harmony) {

        /**
         * 鸿蒙 Push Kit 服务端配置（AppGallery Connect 凭据，全部经环境变量注入，禁止硬编码）。
         *
         * @param appId        鸿蒙应用 App ID（消息下发路径参数）
         * @param clientId     OAuth2 client_id
         * @param clientSecret OAuth2 client_secret
         * @param tokenUrl     OAuth2 access_token 端点（默认华为官方，专有云可覆盖）
         * @param pushBaseUrl  消息下发端点根地址（默认华为官方，专有云可覆盖）
         */
        public record Harmony(String appId, String clientId, String clientSecret,
                              String tokenUrl, String pushBaseUrl) {
        }
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
