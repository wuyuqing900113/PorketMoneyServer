package wyq.pocket.money.common.web;

/**
 * 通用段（10xxxx）与系统段（90xxxx）错误码定义（M0 基线）。
 *
 * <p>业务模块错误码在对应阶段新增各自枚举：
 * 用户与家庭 20xxxx（M1）、零花钱 30xxxx / 规则 40xxxx / 财务 50xxxx（M2）、
 * AI 60xxxx（M4）、通知 70xxxx（M5）。
 */
public enum CommonErrorCode implements ErrorCode {

    /** 成功。 */
    SUCCESS(0, "success"),

    /** 参数校验失败：修正输入后重新提交，不可重试。 */
    PARAM_INVALID(100001, "参数校验失败"),

    /** 请求格式错误：请求体无法解析，不可重试。 */
    REQUEST_MALFORMED(100002, "请求格式错误"),

    /** 未认证或登录态失效：客户端应跳转登录（M1 启用）。 */
    UNAUTHORIZED(100003, "未认证或登录态失效"),

    /** 无权限执行该操作，不可重试。 */
    FORBIDDEN(100004, "无权限执行该操作"),

    /** 资源不存在，不可重试。 */
    RESOURCE_NOT_FOUND(100005, "资源不存在"),

    /** 重复请求（幂等拦截）：视为已受理，不可重试。 */
    DUPLICATE_REQUEST(100006, "重复请求"),

    /** 请求过于频繁（限流）：延迟后重试。 */
    RATE_LIMITED(100007, "请求过于频繁"),

    /** 缺少幂等键（写操作必填 Idempotency-Key 请求头），不可重试。 */
    IDEMPOTENCY_KEY_REQUIRED(100008, "缺少幂等键"),

    /** 幂等键冲突（同键不同请求体），不可重试。 */
    IDEMPOTENCY_CONFLICT(100009, "幂等键冲突"),

    /** 系统内部错误：可重试（携带幂等键）。 */
    INTERNAL_ERROR(900001, "系统内部错误"),

    /** 下游服务超时：可重试。 */
    DOWNSTREAM_TIMEOUT(900002, "下游服务超时"),

    /** 数据库访问异常：可重试。 */
    DATABASE_ERROR(900003, "数据库访问异常"),

    /** 服务维护中：延迟后重试。 */
    SERVICE_MAINTENANCE(900004, "服务维护中");

    private final int code;

    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
