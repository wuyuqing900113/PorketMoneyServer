package wyq.pocket.money.notify.domain;

import java.time.Instant;

/**
 * 外部通道投递与重试记录（M5 设计 §9.1）：PENDING→SENT/DEAD。
 */
public class NotificationDelivery {

    /** 通道值：外部推送（鸿蒙 Push 待选型）。 */
    public static final String CHANNEL_PUSH = "PUSH";

    private Long id;

    private Long notificationId;

    private String channel;

    private String status;

    private int retryCount;

    private Instant nextRetryAt;

    private String lastError;

    private Instant sentAt;

    private Instant createdAt;

    /**
     * 获取投递记录 ID。
     *
     * @return 投递记录 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置投递记录 ID。
     *
     * @param id 投递记录 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取通知 ID。
     *
     * @return 通知 ID
     */
    public Long getNotificationId() {
        return notificationId;
    }

    /**
     * 设置通知 ID。
     *
     * @param notificationId 通知 ID
     */
    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    /**
     * 获取通道（PUSH）。
     *
     * @return 通道
     */
    public String getChannel() {
        return channel;
    }

    /**
     * 设置通道。
     *
     * @param channel 通道
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /**
     * 获取投递状态（PENDING/SENT/FAILED/DEAD）。
     *
     * @return 投递状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置投递状态。
     *
     * @param status 投递状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取已重试次数。
     *
     * @return 已重试次数
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * 设置已重试次数。
     *
     * @param retryCount 已重试次数
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 获取下次重试时间。
     *
     * @return 下次重试时间
     */
    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    /**
     * 设置下次重试时间。
     *
     * @param nextRetryAt 下次重试时间
     */
    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * 获取最近一次错误信息。
     *
     * @return 最近一次错误信息
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * 设置最近一次错误信息。
     *
     * @param lastError 最近一次错误信息
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * 获取投递成功时间。
     *
     * @return 投递成功时间
     */
    public Instant getSentAt() {
        return sentAt;
    }

    /**
     * 设置投递成功时间。
     *
     * @param sentAt 投递成功时间
     */
    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
