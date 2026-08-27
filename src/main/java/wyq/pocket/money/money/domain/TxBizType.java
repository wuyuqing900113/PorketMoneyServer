package wyq.pocket.money.money.domain;

/**
 * 流水业务类型（money_transaction.biz_type）。
 */
public enum TxBizType {

    /** 包月规则发放。 */
    MONTHLY_RULE,

    /** 手动存入。 */
    MANUAL_ADD,

    /** 学习任务奖励。 */
    LEARNING_REWARD,

    /** 工作价值入账。 */
    WORK_VALUE,

    /** 取出。 */
    WITHDRAW
}
