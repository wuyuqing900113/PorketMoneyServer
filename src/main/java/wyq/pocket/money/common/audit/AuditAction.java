package wyq.pocket.money.common.audit;

/**
 * 审计动作枚举（M1 设计 §9.1）。
 *
 * <p>以 {@code name()} 落 {@code audit_log.action} 列（VARCHAR(48)），
 * 枚举名即存储值，重命名须同步评估历史数据。
 */
public enum AuditAction {

    /** 家长注册。 */
    REGISTER,

    /** 家庭创建（注册同事务）。 */
    FAMILY_CREATE,

    /** 登录成功。 */
    LOGIN_SUCCESS,

    /** 登录失败（账号或密码错误）。 */
    LOGIN_FAILURE,

    /** 账号临时锁定生效。 */
    ACCOUNT_LOCKED,

    /** 登出（吊销 refresh）。 */
    LOGOUT,

    /** 刷新令牌（轮转）。 */
    TOKEN_REFRESH,

    /** refresh 重用检测命中（疑似令牌泄露，伴随 SECURITY ERROR 告警）。 */
    TOKEN_REUSE_DETECTED,

    /** 自助修改密码。 */
    PASSWORD_CHANGE,

    /** 家长重置孩子密码。 */
    CHILD_PASSWORD_RESET,

    /** 家庭信息编辑。 */
    FAMILY_UPDATE,

    /** 添加孩子账号。 */
    CHILD_CREATE,

    /** 编辑孩子资料。 */
    CHILD_UPDATE,

    /** 移除家庭成员。 */
    MEMBER_REMOVE
}
