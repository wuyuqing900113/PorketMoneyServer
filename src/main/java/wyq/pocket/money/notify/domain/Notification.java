package wyq.pocket.money.notify.domain;

import java.time.Instant;

/**
 * 站内信通知（M5 设计 §9.1）：接收人维度，read_at 表达已读。
 */
public class Notification {

    private Long id;

    private Long userId;

    private Long familyId;

    private String type;

    private String title;

    private String content;

    private String bizRefType;

    private Long bizRefId;

    private Instant readAt;

    private Instant createdAt;

    /**
     * 获取通知 ID。
     *
     * @return 通知 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置通知 ID。
     *
     * @param id 通知 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取接收人用户 ID。
     *
     * @return 接收人用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置接收人用户 ID。
     *
     * @param userId 接收人用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取家庭 ID。
     *
     * @return 家庭 ID
     */
    public Long getFamilyId() {
        return familyId;
    }

    /**
     * 设置家庭 ID。
     *
     * @param familyId 家庭 ID
     */
    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    /**
     * 获取通知类型（TX_IN/TX_OUT/LOW_BALANCE/RULE_EXPIRED）。
     *
     * @return 通知类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置通知类型。
     *
     * @param type 通知类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置标题。
     *
     * @param title 标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取正文。
     *
     * @return 正文
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置正文。
     *
     * @param content 正文
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取业务锚点类型（MONEY_TRANSACTION / MONEY_RULE）。
     *
     * @return 业务锚点类型
     */
    public String getBizRefType() {
        return bizRefType;
    }

    /**
     * 设置业务锚点类型。
     *
     * @param bizRefType 业务锚点类型
     */
    public void setBizRefType(String bizRefType) {
        this.bizRefType = bizRefType;
    }

    /**
     * 获取业务锚点 ID。
     *
     * @return 业务锚点 ID
     */
    public Long getBizRefId() {
        return bizRefId;
    }

    /**
     * 设置业务锚点 ID。
     *
     * @param bizRefId 业务锚点 ID
     */
    public void setBizRefId(Long bizRefId) {
        this.bizRefId = bizRefId;
    }

    /**
     * 获取已读时间（null = 未读）。
     *
     * @return 已读时间
     */
    public Instant getReadAt() {
        return readAt;
    }

    /**
     * 设置已读时间。
     *
     * @param readAt 已读时间
     */
    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
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
