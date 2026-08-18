package wyq.pocket.money.user.domain;

import java.time.Instant;

/**
 * 用户领域对象，映射 app_user 表（M1 设计 §7.1）。
 *
 * <p>家长以手机号标识（phone_hash 查找 + phone_encrypted 回显），
 * 孩子以登录名标识（username），两者互斥（chk_app_user_identifier）。
 * {@code phoneEncrypted} 属性经 EncryptedFieldTypeHandler 写入加密、
 * 读取解密 —— getter 返回的是明文，禁止直接出现在响应或日志中
 * （解密结果不出 service 层，M1 设计 §5.5）。
 */
public class User {

    /** 角色值：家长。 */
    public static final String ROLE_PARENT = "PARENT";

    /** 角色值：孩子。 */
    public static final String ROLE_CHILD = "CHILD";

    /** 状态值：正常。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 状态值：停用（孩子被移出家庭后置位，禁止再登录）。 */
    public static final String STATUS_DISABLED = "DISABLED";

    private Long id;

    private String username;

    private String phoneHash;

    private String phoneEncrypted;

    private Integer keyVersion;

    private String passwordHash;

    private String nickname;

    private String role;

    private String status;

    private boolean mustChangePassword;

    private Instant consentedAt;

    private Long consentedBy;

    private int failedAttempts;

    private Instant lockedUntil;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 获取用户 ID。
     *
     * @return 用户 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置用户 ID。
     *
     * @param id 用户 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取孩子登录名（家长为 null）。
     *
     * @return 登录名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置孩子登录名。
     *
     * @param username 登录名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取手机号 SHA-256 哈希（孩子为 null）。
     *
     * @return 手机号哈希
     */
    public String getPhoneHash() {
        return phoneHash;
    }

    /**
     * 设置手机号哈希。
     *
     * @param phoneHash 手机号哈希
     */
    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    /**
     * 获取手机号（经 TypeHandler 自动解密后的明文，注意脱敏）。
     *
     * @return 手机号明文
     */
    public String getPhoneEncrypted() {
        return phoneEncrypted;
    }

    /**
     * 设置手机号（经 TypeHandler 自动加密后落库）。
     *
     * @param phoneEncrypted 手机号明文
     */
    public void setPhoneEncrypted(String phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
    }

    /**
     * 获取加密密钥版本。
     *
     * @return 密钥版本
     */
    public Integer getKeyVersion() {
        return keyVersion;
    }

    /**
     * 设置加密密钥版本。
     *
     * @param keyVersion 密钥版本
     */
    public void setKeyVersion(Integer keyVersion) {
        this.keyVersion = keyVersion;
    }

    /**
     * 获取 BCrypt 密码哈希。
     *
     * @return 密码哈希
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * 设置密码哈希。
     *
     * @param passwordHash 密码哈希
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 获取昵称。
     *
     * @return 昵称
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 设置昵称。
     *
     * @param nickname 昵称
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 获取角色（PARENT / CHILD）。
     *
     * @return 角色
     */
    public String getRole() {
        return role;
    }

    /**
     * 设置角色。
     *
     * @param role 角色
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * 获取状态（ACTIVE / DISABLED）。
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 是否须先修改初始密码（孩子首次登录强制位）。
     *
     * @return 须先改密返回 true
     */
    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    /**
     * 设置首次改密强制位。
     *
     * @param mustChangePassword 强制位
     */
    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    /**
     * 获取隐私政策同意时间。
     *
     * @return 同意时间
     */
    public Instant getConsentedAt() {
        return consentedAt;
    }

    /**
     * 设置隐私政策同意时间。
     *
     * @param consentedAt 同意时间
     */
    public void setConsentedAt(Instant consentedAt) {
        this.consentedAt = consentedAt;
    }

    /**
     * 获取同意代为创建账号的家长 ID（孩子账号留痕）。
     *
     * @return 家长 ID
     */
    public Long getConsentedBy() {
        return consentedBy;
    }

    /**
     * 设置代为同意的家长 ID。
     *
     * @param consentedBy 家长 ID
     */
    public void setConsentedBy(Long consentedBy) {
        this.consentedBy = consentedBy;
    }

    /**
     * 获取连续登录失败次数。
     *
     * @return 失败次数
     */
    public int getFailedAttempts() {
        return failedAttempts;
    }

    /**
     * 设置连续登录失败次数。
     *
     * @param failedAttempts 失败次数
     */
    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    /**
     * 获取锁定截止时间（未锁定为 null）。
     *
     * @return 锁定截止时间
     */
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /**
     * 设置锁定截止时间。
     *
     * @param lockedUntil 锁定截止时间
     */
    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
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
