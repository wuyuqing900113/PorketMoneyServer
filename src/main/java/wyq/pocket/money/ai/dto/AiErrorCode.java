package wyq.pocket.money.ai.dto;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * AI 模块错误码（60xxxx，M4 设计 §9.1）。
 *
 * <p>段位 60xxxx 与 {@code CommonErrorCode} javadoc 预留段一致；
 * 60 段非系统段，{@link #isRetryable()} 继承默认返回 false。
 */
public enum AiErrorCode implements ErrorCode {

    /** AI 服务不可用（降级/超时/熔断），客户端回落手动操作。 */
    AI_UNAVAILABLE(600001, "AI 服务不可用"),

    /** 意图无法识别，重新表述指令。 */
    INTENT_UNRECOGNIZED(600002, "意图无法识别"),

    /** 待确认操作不存在或已过期，重新发起指令。 */
    PENDING_ACTION_INVALID(600003, "待确认操作不存在或已过期"),

    /** 已有未完成待确认操作，先确认/取消再发起新指令。 */
    PENDING_ACTION_EXISTS(600004, "已有未完成待确认操作");

    private final int code;

    private final String message;

    AiErrorCode(int code, String message) {
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
