package wyq.pocket.money.finance.dto;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * 财务模块错误码（50xxxx，M2 设计 §8.4）。
 */
public enum FinanceErrorCode implements ErrorCode {

    /** 月份格式非法（须为 YYYY-MM），不可重试。 */
    MONTH_FORMAT_INVALID(500001, "月份格式非法，须为YYYY-MM"),

    /** 查询月份不可晚于当月，不可重试。 */
    MONTH_IN_FUTURE(500002, "查询月份不可晚于当月");

    private final int code;

    private final String message;

    FinanceErrorCode(int code, String message) {
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
