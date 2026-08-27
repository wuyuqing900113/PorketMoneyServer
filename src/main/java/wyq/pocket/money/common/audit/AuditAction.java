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
    MEMBER_REMOVE,

    // ---- M2 零花钱域动作（M2-detailed-design.md §9） ----

    /** 手动存入。 */
    MONEY_DEPOSIT,

    /** 手动取出。 */
    MONEY_WITHDRAW,

    /** 包月规则创建。 */
    RULE_CREATE,

    /** 包月规则修改。 */
    RULE_UPDATE,

    /** 包月规则暂停。 */
    RULE_PAUSE,

    /** 包月规则恢复。 */
    RULE_RESUME,

    /** 包月规则归档。 */
    RULE_ARCHIVE,

    /** 包月规则删除（无发放记录时）。 */
    RULE_DELETE,

    /** 包月规则发放执行（定时结算）。 */
    RULE_GRANT_EXECUTED,

    /** 学习任务创建。 */
    LEARNING_TASK_CREATE,

    /** 学习任务提交。 */
    LEARNING_TASK_SUBMIT,

    /** 学习任务通过并发放奖励。 */
    LEARNING_TASK_APPROVE,

    /** 学习任务驳回。 */
    LEARNING_TASK_REJECT,

    /** 学习任务取消（发放前）。 */
    LEARNING_TASK_CANCEL,

    /** 工作价值记录（父母工资入账）。 */
    WORK_VALUE_RECORD,

    /** 对账发现余额/流水不一致（伴随 SECURITY ERROR 告警）。 */
    RECONCILE_MISMATCH,

    // ---- M4 AI 交互动作（M4-detailed-design.md §10） ----

    /** AI 会话创建。 */
    AI_SESSION_START,

    /** AI 意图解析与执行成功。 */
    AI_INTENT,

    /** AI 资金写生成二次确认动作。 */
    AI_ACTION_CONFIRM_REQUEST,

    /** AI 二次确认动作执行成功（记账完成）。 */
    AI_ACTION_EXECUTED,

    /** AI 二次确认动作执行业务失败（落 REJECTED）。 */
    AI_ACTION_REJECTED,

    /** AI 二次确认动作被用户取消。 */
    AI_ACTION_CANCELED,

    /** AI 二次确认动作超时过期。 */
    AI_ACTION_EXPIRED,

    /** AI 服务不可用降级（熔断 / 超时 / 运行期失败）。 */
    AI_DEGRADED
}
