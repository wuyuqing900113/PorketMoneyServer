package wyq.pocket.money.ai.domain;

import java.time.Instant;

/**
 * AI 交互会话（M4 设计 §10.1）：一人一活跃会话，消息按会话归属。
 */
public class AiSession {

    /** 会话状态：活跃。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 会话状态：已关闭。 */
    public static final String STATUS_CLOSED = "CLOSED";

    private Long id;

    private Long userId;

    private Long familyId;

    private String channel;

    private String status;

    private Instant createdAt;

    private Instant lastActiveAt;

    /**
     * 获取会话 ID。
     *
     * @return 会话 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置会话 ID。
     *
     * @param id 会话 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取所属用户 ID。
     *
     * @return 所属用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置所属用户 ID。
     *
     * @param userId 所属用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取所属家庭 ID。
     *
     * @return 所属家庭 ID
     */
    public Long getFamilyId() {
        return familyId;
    }

    /**
     * 设置所属家庭 ID。
     *
     * @param familyId 所属家庭 ID
     */
    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    /**
     * 获取通道（TEXT / VOICE）。
     *
     * @return 通道
     */
    public String getChannel() {
        return channel;
    }

    /**
     * 设置通道。
     *
     * @param channel 通道（TEXT / VOICE）
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    /**
     * 获取会话状态。
     *
     * @return 会话状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置会话状态。
     *
     * @param status 会话状态
     */
    public void setStatus(String status) {
        this.status = status;
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

    /**
     * 获取最近活跃时间。
     *
     * @return 最近活跃时间
     */
    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    /**
     * 设置最近活跃时间。
     *
     * @param lastActiveAt 最近活跃时间
     */
    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
