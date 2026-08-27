package wyq.pocket.money.money.dto;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * 零花钱模块错误码（30xxxx，M2 设计 §8.4）。
 */
public enum MoneyErrorCode implements ErrorCode {

    /** 余额不足，不可重试。 */
    BALANCE_NOT_ENOUGH(300001, "余额不足"),

    /** 账户已冻结（成员被移除），不可重试。 */
    ACCOUNT_FROZEN(300002, "账户已冻结"),

    /** 流水不存在，不可重试。 */
    TRANSACTION_NOT_FOUND(300003, "流水不存在"),

    /** 金额必须大于 0，不可重试。 */
    AMOUNT_INVALID(300004, "金额必须大于0"),

    /** 学习任务不存在，不可重试。 */
    LEARNING_TASK_NOT_FOUND(300005, "学习任务不存在"),

    /** 学习任务当前状态不允许该操作，不可重试。 */
    TASK_STATUS_NOT_ALLOWED(300006, "学习任务当前状态不允许该操作"),

    /** 工作价值记录不存在，不可重试。 */
    WORK_VALUE_NOT_FOUND(300007, "工作价值记录不存在");

    private final int code;

    private final String message;

    MoneyErrorCode(int code, String message) {
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
