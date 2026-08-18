package wyq.pocket.money.common.security;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * 安全过滤链层出口错误码（编号属 20xxxx 用户与家庭段，M1 设计 §10.4）。
 *
 * <p>200010 由 {@code JwtAuthenticationFilter} 在过滤链内强制执行：
 * 过滤链先于 MVC 且 common 层禁止依赖业务模块（ArchUnit 约束），
 * 无法引用 user 模块的 UserErrorCode，故在此单点定义；
 * UserErrorCode 不再重复声明 200010。
 */
public enum SecurityErrorCode implements ErrorCode {

    /** 请先修改初始密码：mcp=true 账号仅放行改密与登出（§4.6），引导至修改密码页，不可重试。 */
    MUST_CHANGE_PASSWORD_FIRST(200010, "请先修改初始密码");

    private final int code;

    private final String message;

    SecurityErrorCode(int code, String message) {
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
