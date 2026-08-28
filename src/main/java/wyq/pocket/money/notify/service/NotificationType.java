package wyq.pocket.money.notify.service;

/**
 * 通知类型目录（M5 设计 §5.1）。
 *
 * <p>以 {@code name()} 落 {@code notification.type} 列（VARCHAR(32)），
 * 枚举名即存储值，与 V9 CHECK 约束一一对应；新增类型须同步迁移。
 */
public enum NotificationType {

    /** 入账（包月规则发放 / 手动存入 / 任务奖励 / 工作价值）。 */
    TX_IN,

    /** 出账（提取）。 */
    TX_OUT,

    /** 余额不足提醒（出账后低于阈值）。 */
    LOW_BALANCE,

    /** 规则到期归档。 */
    RULE_EXPIRED
}
