package wyq.pocket.money.rule.dto;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * 规则模块错误码（40xxxx，M2 设计 §8.4）。
 */
public enum RuleErrorCode implements ErrorCode {

    /** 规则不存在，不可重试。 */
    RULE_NOT_FOUND(400001, "规则不存在"),

    /** 规则当前状态不允许该操作，不可重试。 */
    RULE_STATUS_NOT_ALLOWED(400002, "规则当前状态不允许该操作"),

    /** 发放日须在 1–28 之间，不可重试。 */
    GRANT_DAY_INVALID(400003, "发放日须在1-28之间"),

    /** 受益人规则数达上限，不可重试。 */
    RULE_LIMIT_REACHED(400004, "受益人规则数已达上限"),

    /** 已有发放记录的规则不可删除，不可重试。 */
    RULE_HAS_GRANTS(400005, "已有发放记录的规则不可删除"),

    /** 家庭内规则名称重复，不可重试。 */
    RULE_NAME_DUPLICATE(400006, "家庭内规则名称重复");

    private final int code;

    private final String message;

    RuleErrorCode(int code, String message) {
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
