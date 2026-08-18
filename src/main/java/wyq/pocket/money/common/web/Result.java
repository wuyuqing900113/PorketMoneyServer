package wyq.pocket.money.common.web;

import wyq.pocket.money.common.trace.TraceIds;

/**
 * 统一响应体：所有 API（含异常）一律以该结构响应。
 *
 * <p>{@code code = 0} 表示成功；非 0 为 6 位数字分段错误码。
 * 业务错误 HTTP 状态码为 200，客户端按 code 处理（API 文档总则）。
 *
 * @param code      错误码，0 表示成功
 * @param message   面向客户端的提示信息
 * @param data      业务数据，失败时为 null
 * @param traceId   链路追踪 ID，排障入口
 * @param timestamp 服务端毫秒时间戳
 * @param <T>       业务数据类型
 */
public record Result<T>(int code, String message, T data, String traceId, long timestamp) {

    /** 成功错误码。 */
    public static final int SUCCESS_CODE = 0;

    private static final String SUCCESS_MESSAGE = "success";

    /**
     * 构造成功响应。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data,
                TraceIds.current(), System.currentTimeMillis());
    }

    /**
     * 构造无数据的成功响应。
     *
     * @param <T> 业务数据类型
     * @return 成功响应
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 构造失败响应（使用错误码默认提示）。
     *
     * @param errorCode 错误码
     * @param <T>       业务数据类型
     * @return 失败响应
     */
    public static <T> Result<T> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.getMessage());
    }

    /**
     * 构造失败响应（自定义提示）。
     *
     * @param errorCode 错误码
     * @param message   面向客户端的提示信息
     * @param <T>       业务数据类型
     * @return 失败响应
     */
    public static <T> Result<T> failure(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null,
                TraceIds.current(), System.currentTimeMillis());
    }

    /**
     * 是否成功。
     *
     * @return 成功返回 true
     */
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }
}
