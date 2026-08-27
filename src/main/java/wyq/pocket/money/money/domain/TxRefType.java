package wyq.pocket.money.money.domain;

/**
 * 流水关联业务单据类型（money_transaction.ref_type）。
 */
public enum TxRefType {

    /** 关联 rule_grant_record。 */
    RULE_GRANT,

    /** 关联 learning_task。 */
    LEARNING_TASK,

    /** 关联 work_value_record。 */
    WORK_VALUE_RECORD
}
