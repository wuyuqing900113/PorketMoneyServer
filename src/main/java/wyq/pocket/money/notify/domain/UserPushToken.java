package wyq.pocket.money.notify.domain;

import java.time.Instant;

/**
 * 用户外部推送设备令牌（V10，GA D68）：鸿蒙客户端上报的 HMS Push token。
 */
public class UserPushToken {

    /** 渠道值：鸿蒙 Push Kit。 */
    public static final String PROVIDER_HARMONY = "HARMONY";

    private Long id;

    private long userId;

    private String provider;

    private String token;

    private boolean enabled;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 获取 ID。
     *
     * @return ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置 ID。
     *
     * @param id ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取用户 ID。
     *
     * @return 用户 ID
     */
    public long getUserId() {
        return userId;
    }

    /**
     * 设置用户 ID。
     *
     * @param userId 用户 ID
     */
    public void setUserId(long userId) {
        this.userId = userId;
    }

    /**
     * 获取推送渠道。
     *
     * @return 渠道（HARMONY）
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置推送渠道。
     *
     * @param provider 渠道
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 获取设备令牌。
     *
     * @return 设备令牌
     */
    public String getToken() {
        return token;
    }

    /**
     * 设置设备令牌。
     *
     * @param token 设备令牌
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * 是否启用。
     *
     * @return true 启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置启用状态。
     *
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
