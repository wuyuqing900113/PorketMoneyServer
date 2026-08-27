package wyq.pocket.money.common.idempotency;

import java.time.Instant;

/**
 * 幂等记录（idempotency_record，M3 设计 §5）。
 *
 * <p>记录写操作请求指纹与原始响应缓存，两阶段语义：
 * IN_PROGRESS（受理中）→ PROCESSED（已受理并缓存响应）；
 * 业务失败时由服务删除记录释放幂等键供修正后重试。
 */
public class IdempotencyRecord {

    /** 受理中状态。 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 已受理状态（响应已缓存）。 */
    public static final String STATUS_PROCESSED = "PROCESSED";

    private Long id;

    private Long userId;

    private String idemKey;

    private String method;

    private String path;

    private String payloadHash;

    private Integer respCode;

    private String respBody;

    private String status;

    private Instant createdAt;

    private Instant expiresAt;

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
     * 获取幂等键。
     *
     * @return 幂等键
     */
    public String getIdemKey() {
        return idemKey;
    }

    /**
     * 设置幂等键。
     *
     * @param idemKey 幂等键
     */
    public void setIdemKey(String idemKey) {
        this.idemKey = idemKey;
    }

    /**
     * 获取请求方法。
     *
     * @return 请求方法
     */
    public String getMethod() {
        return method;
    }

    /**
     * 设置请求方法。
     *
     * @param method 请求方法
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * 获取请求路径。
     *
     * @return 请求路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置请求路径。
     *
     * @param path 请求路径
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 获取请求指纹（SHA-256 十六进制）。
     *
     * @return 请求指纹
     */
    public String getPayloadHash() {
        return payloadHash;
    }

    /**
     * 设置请求指纹。
     *
     * @param payloadHash 请求指纹
     */
    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    /**
     * 获取缓存的响应错误码。
     *
     * @return 响应错误码
     */
    public Integer getRespCode() {
        return respCode;
    }

    /**
     * 设置缓存的响应错误码。
     *
     * @param respCode 响应错误码
     */
    public void setRespCode(Integer respCode) {
        this.respCode = respCode;
    }

    /**
     * 获取缓存的原始响应体 JSON。
     *
     * @return 原始响应体 JSON
     */
    public String getRespBody() {
        return respBody;
    }

    /**
     * 设置缓存的原始响应体 JSON。
     *
     * @param respBody 原始响应体 JSON
     */
    public void setRespBody(String respBody) {
        this.respBody = respBody;
    }

    /**
     * 获取记录状态（IN_PROGRESS / PROCESSED）。
     *
     * @return 记录状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置记录状态。
     *
     * @param status 记录状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取受理时间。
     *
     * @return 受理时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置受理时间。
     *
     * @param createdAt 受理时间
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
}
