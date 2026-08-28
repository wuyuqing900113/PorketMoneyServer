package wyq.pocket.money.notify.dto;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * 通知模块错误码（70xxxx，M5 设计 §8.1）。
 */
public enum NotifyErrorCode implements ErrorCode {

    /** 通知不存在或非本人，不可重试。 */
    NOTIFICATION_NOT_FOUND(700001, "通知不存在或非本人");

    private final int code;

    private final String message;

    NotifyErrorCode(int code, String message) {
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
