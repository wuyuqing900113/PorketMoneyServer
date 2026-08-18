package wyq.pocket.money.common.exception;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * 业务异常基类：携带错误码，由全局异常处理器统一转换为 Result 响应。
 *
 * <p>业务层禁止抛出裸 RuntimeException；必须携带具体错误码，
 * 错误码以枚举集中维护，禁止散落魔法值（code-style-guide.md §4）。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /**
     * 使用错误码默认提示构造。
     *
     * @param errorCode 错误码，不能为空
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义提示构造。
     *
     * @param errorCode 错误码，不能为空
     * @param message   面向客户端的提示信息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 使用自定义提示与原因构造。
     *
     * @param errorCode 错误码，不能为空
     * @param message   面向客户端的提示信息
     * @param cause     原始异常
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
