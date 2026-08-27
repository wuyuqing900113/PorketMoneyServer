package wyq.pocket.money.rule.domain;

/**
 * 包月规则状态（M2 设计 §7）。
 */
public enum RuleStatus {

    /** 生效中，参与每日结算。 */
    ACTIVE,

    /** 暂停，结算跳过，可恢复。 */
    PAUSED,

    /** 归档（手动或到期自动），终态。 */
    ARCHIVED
}
