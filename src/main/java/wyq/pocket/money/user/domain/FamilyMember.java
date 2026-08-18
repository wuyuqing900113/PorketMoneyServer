package wyq.pocket.money.user.domain;

import java.time.Instant;

/**
 * 家庭成员关系领域对象，映射 family_member 表（M1 设计 §7.1）。
 *
 * <p>一人一家庭（user_id 唯一约束，M1 无加入 / 迁移他途）。
 */
public class FamilyMember {

    private Long id;

    private Long familyId;

    private Long userId;

    private Instant joinedAt;

    /**
     * 默认构造（MyBatis 映射用）。
     */
    public FamilyMember() {
    }

    /**
     * 业务构造。
     *
     * @param familyId 家庭 ID
     * @param userId   用户 ID
     */
    public FamilyMember(Long familyId, Long userId) {
        this.familyId = familyId;
        this.userId = userId;
    }

    /**
     * 获取关系记录 ID。
     *
     * @return 记录 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置记录 ID。
     *
     * @param id 记录 ID
     */
    public void setId(Long id) {
        this.id = id;
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
     * 获取用户 ID。
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置用户 ID。
     *
     * @param userId 用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取加入时间。
     *
     * @return 加入时间
     */
    public Instant getJoinedAt() {
        return joinedAt;
    }

    /**
     * 设置加入时间。
     *
     * @param joinedAt 加入时间
     */
    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}
