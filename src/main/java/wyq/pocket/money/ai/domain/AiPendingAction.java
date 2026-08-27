package wyq.pocket.money.ai.domain;

import java.time.Instant;

/**
 * 资金写二次确认动作（M4 设计 §6.2）：参数快照 + TTL 状态机。
 *
 * <p>状态机：PENDING → EXECUTED / REJECTED / CANCELED / EXPIRED；
 * EXECUTED / REJECTED / CANCELED / EXPIRED 为终态。
 */
public class AiPendingAction {

    /** 动作状态：待确认。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 动作状态：已执行。 */
    public static final String STATUS_EXECUTED = "EXECUTED";

    /** 动作状态：已拒绝（业务失败）。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 动作状态：已取消。 */
    public static final String STATUS_CANCELED = "CANCELED";

    /** 动作状态：已过期。 */
    public static final String STATUS_EXPIRED = "EXPIRED";

    private Long id;

    private Long sessionId;

    private Long userId;

    private String intent;

    private String paramsJson;

    private String status;

    private Instant createdAt;

    private Instant expiresAt;

    private Instant executedAt;

    /**
     * 获取动作 ID。
     *
     * @return 动作 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置动作 ID。
     *
     * @param id 动作 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取所属会话 ID。
     *
     * @return 所属会话 ID
     */
    public Long getSessionId() {
        return sessionId;
    }

    /**
     * 设置所属会话 ID。
     *
     * @param sessionId 所属会话 ID
     */
    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 获取发起用户 ID。
     *
     * @return 发起用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置发起用户 ID。
     *
     * @param userId 发起用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取意图码（DEPOSIT / WITHDRAW）。
     *
     * @return 意图码
     */
    public String getIntent() {
        return intent;
    }

    /**
     * 设置意图码。
     *
     * @param intent 意图码（DEPOSIT / WITHDRAW）
     */
    public void setIntent(String intent) {
        this.intent = intent;
    }

    /**
     * 获取参数快照 JSON 文本（{targetUserId, amount, remark}）。
     *
     * @return 参数快照 JSON 文本
     */
    public String getParamsJson() {
        return paramsJson;
    }

    /**
     * 设置参数快照 JSON 文本。
     *
     * @param paramsJson 参数快照 JSON 文本
     */
    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    /**
     * 获取动作状态。
     *
     * @return 动作状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置动作状态。
     *
     * @param status 动作状态
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
     * 获取过期时间。
     *
     * @return 过期时间
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * 设置过期时间。
     *
     * @param expiresAt 过期时间
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * 获取执行时间（未执行为 null）。
     *
     * @return 执行时间
     */
    public Instant getExecutedAt() {
        return executedAt;
    }

    /**
     * 设置执行时间。
     *
     * @param executedAt 执行时间
     */
    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}
