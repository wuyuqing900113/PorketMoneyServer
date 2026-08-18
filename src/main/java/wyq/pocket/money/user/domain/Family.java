package wyq.pocket.money.user.domain;

import java.time.Instant;

/**
 * 家庭领域对象，映射 family 表（M1 设计 §6 / §7.1）。
 *
 * <p>注册即建（§5.1），创建者为唯一家长（ownerUserId）；
 * M1 一人一家庭、不支持移除家长。
 */
public class Family {

    private Long id;

    private String familyName;

    private Long ownerUserId;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 默认构造（MyBatis 映射用）。
     */
    public Family() {
    }

    /**
     * 业务构造。
     *
     * @param familyName   家庭名（≤32 字）
     * @param ownerUserId  创建者（家长）用户 ID
     */
    public Family(String familyName, Long ownerUserId) {
        this.familyName = familyName;
        this.ownerUserId = ownerUserId;
    }

    /**
     * 获取家庭 ID。
     *
     * @return 家庭 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置家庭 ID。
     *
     * @param id 家庭 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取家庭名。
     *
     * @return 家庭名
     */
    public String getFamilyName() {
        return familyName;
    }

    /**
     * 设置家庭名。
     *
     * @param familyName 家庭名
     */
    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    /**
     * 获取创建者（家长）用户 ID。
     *
     * @return 创建者 ID
     */
    public Long getOwnerUserId() {
        return ownerUserId;
    }

    /**
     * 设置创建者用户 ID。
     *
     * @param ownerUserId 创建者 ID
     */
    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
