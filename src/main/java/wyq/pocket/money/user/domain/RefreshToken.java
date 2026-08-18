package wyq.pocket.money.user.domain;

import java.time.Instant;

/**
 * refresh 令牌持久化记录，映射 user_refresh_token 表（M1 设计 §4.3）。
 *
 * <p>仅存 SHA-256 哈希，不落明文；轮转与重用检测均以
 * {@code revokedAt} 软吊销标记为准（§4.4）。
 */
public class RefreshToken {

    private Long id;

    private Long userId;

    private String tokenHash;

    private Instant expiresAt;

    private Instant revokedAt;

    private Instant createdAt;

    /**
     * 默认构造（MyBatis 映射用）。
     */
    public RefreshToken() {
    }

    /**
     * 业务构造。
     *
     * @param userId    用户 ID
     * @param tokenHash SHA-256(refreshToken)
     * @param expiresAt 过期时间
     */
    public RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 获取记录 ID。
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
     * 获取令牌哈希。
     *
     * @return 令牌哈希
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * 设置令牌哈希。
     *
     * @param tokenHash 令牌哈希
     */
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
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
     * 获取吊销时间（未吊销为 null）。
     *
     * @return 吊销时间
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }

    /**
     * 设置吊销时间。
     *
     * @param revokedAt 吊销时间
     */
    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
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
