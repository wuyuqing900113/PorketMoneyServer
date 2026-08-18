package wyq.pocket.money.user.dto;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * 用户与家庭段错误码（20xxxx，M1 设计 §10.4）。
 *
 * <p>200010「请先修改初始密码」由 common 层 SecurityErrorCode 单点定义
 * （过滤链先于 MVC 执行，且 ArchUnit 禁止 common 反向依赖 user 模块）。
 * 200009 按 §10.4 保留声明：M1 刷新链路的一切失败（未命中 / 过期 /
 * 重用）统一返回 100003（§4.4），失败原因不可区分以防令牌状态探测，
 * 本码暂不对外发出。
 */
public enum UserErrorCode implements ErrorCode {

    /** 该手机号已注册，不可重试。 */
    PHONE_ALREADY_REGISTERED(200001, "该手机号已注册"),

    /** 账号或密码错误（防枚举统一文案），不可重试。 */
    BAD_CREDENTIALS(200002, "账号或密码错误"),

    /** 账号已临时锁定：延迟后重试。 */
    ACCOUNT_LOCKED(200003, "账号已临时锁定，请稍后再试"),

    /** 账号已停用：不可重试，提示联系家长。 */
    ACCOUNT_DISABLED(200004, "账号已停用"),

    /** 家庭不存在，不可重试。 */
    FAMILY_NOT_FOUND(200005, "家庭不存在"),

    /** 家庭成员数量已达上限（8 人），不可重试。 */
    FAMILY_MEMBER_LIMIT_REACHED(200006, "家庭成员数量已达上限"),

    /** 该登录名已被占用：提示更换，不可重试。 */
    USERNAME_TAKEN(200007, "该登录名已被占用"),

    /** 原密码不正确，不可重试。 */
    OLD_PASSWORD_WRONG(200008, "原密码不正确"),

    /** 刷新令牌无效或已过期（M1 保留码，实际统一走 100003，见类注释）。 */
    REFRESH_TOKEN_INVALID(200009, "刷新令牌无效或已过期"),

    /** 目标成员不在该家庭中，不可重试。 */
    MEMBER_NOT_IN_FAMILY(200011, "目标成员不在该家庭中"),

    /** 不能移除家庭创建者，不可重试。 */
    CANNOT_REMOVE_OWNER(200012, "不能移除家庭创建者");

    private final int code;

    private final String message;

    UserErrorCode(int code, String message) {
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
