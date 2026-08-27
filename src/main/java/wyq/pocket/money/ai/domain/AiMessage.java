package wyq.pocket.money.ai.domain;

import java.time.Instant;

/**
 * AI 消息（M4 设计 §10.1）：USER 原文 / ASSISTANT 回复；tool_call_json 落调用链。
 */
public class AiMessage {

    /** 消息角色：用户。 */
    public static final String ROLE_USER = "USER";

    /** 消息角色：助手。 */
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    /** 消息角色：系统。 */
    public static final String ROLE_SYSTEM = "SYSTEM";

    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String intent;

    private String toolCallJson;

    private Instant createdAt;

    /**
     * 获取消息 ID。
     *
     * @return 消息 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置消息 ID。
     *
     * @param id 消息 ID
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
     * 获取消息角色。
     *
     * @return 消息角色（USER / ASSISTANT / SYSTEM）
     */
    public String getRole() {
        return role;
    }

    /**
     * 设置消息角色。
     *
     * @param role 消息角色
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * 获取消息内容（用户原文 / AI 回复文本）。
     *
     * @return 消息内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置消息内容。
     *
     * @param content 消息内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取意图码（USER 消息为 null）。
     *
     * @return 意图码
     */
    public String getIntent() {
        return intent;
    }

    /**
     * 设置意图码。
     *
     * @param intent 意图码
     */
    public void setIntent(String intent) {
        this.intent = intent;
    }

    /**
     * 获取调用链 JSON 文本（意图→参数→工具调用→结果）。
     *
     * @return 调用链 JSON 文本
     */
    public String getToolCallJson() {
        return toolCallJson;
    }

    /**
     * 设置调用链 JSON 文本。
     *
     * @param toolCallJson 调用链 JSON 文本
     */
    public void setToolCallJson(String toolCallJson) {
        this.toolCallJson = toolCallJson;
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
