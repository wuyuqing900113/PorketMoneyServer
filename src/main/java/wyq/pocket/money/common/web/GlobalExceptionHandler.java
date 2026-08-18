package wyq.pocket.money.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import wyq.pocket.money.common.audit.SecurityLogger;
import wyq.pocket.money.common.exception.BusinessException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：所有异常统一转换为 {@link Result} 响应。
 *
 * <p>业务错误 HTTP 状态码一律 200，错误信息走 code 字段；
 * 兜底异常不对外暴露堆栈细节，完整堆栈与 traceId 落日志。
 * 异常与错误码映射见 M0-detailed-design.md §6.3。
 *
 * <p>Security 异常映射（M1 设计 §4.8）：AccessDeniedException → 100004 + HTTP 403、
 * AuthenticationException → 100003 + HTTP 401。与过滤链出口（EntryPoint /
 * DeniedHandler）分工：过滤链内的拒绝走出口 handler，进入 MVC 之后
 * （@PreAuthorize、service 显式抛出）走本处理器，输出同一 Result 契约。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String FIELD_SEPARATOR = "; ";

    private static final String FIELD_DETAIL_SEPARATOR = ": ";

    /**
     * 请求体参数校验失败（@Valid 触发）。
     *
     * @param ex 校验异常
     * @return 100001 响应，message 聚合全部字段错误
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + FIELD_DETAIL_SEPARATOR + error.getDefaultMessage())
                .collect(Collectors.joining(FIELD_SEPARATOR));
        return warnAndFail(CommonErrorCode.PARAM_INVALID, detail, ex);
    }

    /**
     * 单参数约束校验失败（@Validated 触发）。
     *
     * @param ex 校验异常
     * @return 100001 响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + FIELD_DETAIL_SEPARATOR + v.getMessage())
                .collect(Collectors.joining(FIELD_SEPARATOR));
        return warnAndFail(CommonErrorCode.PARAM_INVALID, detail, ex);
    }

    /**
     * 必填请求参数缺失。
     *
     * @param ex 参数缺失异常
     * @return 100001 响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException ex) {
        String detail = ex.getParameterName() + FIELD_DETAIL_SEPARATOR + "参数缺失";
        return warnAndFail(CommonErrorCode.PARAM_INVALID, detail, ex);
    }

    /**
     * 请求体解析失败（非法 JSON 等）。
     *
     * @param ex 解析异常
     * @return 100002 响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        LOG.warn("请求格式错误: {}", ex.getMessage());
        return Result.failure(CommonErrorCode.REQUEST_MALFORMED);
    }

    /**
     * 业务异常：使用异常自带错误码与提示。
     *
     * @param ex 业务异常
     * @return 对应错误码响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex) {
        LOG.warn("业务异常: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        return Result.failure(ex.getErrorCode(), ex.getMessage());
    }

    /**
     * 资源不存在。
     *
     * @param ex 资源未找到异常
     * @return 100005 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException ex) {
        LOG.warn("资源不存在: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return Result.failure(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 越权（MVC 层）：@PreAuthorize 拒绝或 service 显式抛出（M1 设计 §4.8）。
     *
     * <p>与过滤链 DeniedHandler 输出同一契约：HTTP 403 + Result(100004)。
     *
     * @param ex 越权异常
     * @return 403 + 100004 响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        SecurityLogger.warn("ACCESS_DENIED reason={} detail={}", "MVC_LAYER", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.failure(CommonErrorCode.FORBIDDEN));
    }

    /**
     * 认证失败（MVC 层）：框架抛出的 AuthenticationException 及子类（M1 设计 §4.8）。
     *
     * <p>与过滤链 EntryPoint 输出同一契约：HTTP 401 + Result(100003)。
     *
     * @param ex 认证异常
     * @return 401 + 100003 响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationFailure(AuthenticationException ex) {
        SecurityLogger.warn("UNAUTHENTICATED_REJECT reason={} detail={}", "MVC_LAYER", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.failure(CommonErrorCode.UNAUTHORIZED));
    }

    /**
     * 兜底异常处理：对外不暴露细节，完整信息落日志。
     *
     * @param ex 未知异常
     * @return 900001 响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception ex) {
        LOG.error("系统内部错误", ex);
        return Result.failure(CommonErrorCode.INTERNAL_ERROR);
    }

    private Result<Void> warnAndFail(CommonErrorCode errorCode, String detail, Exception ex) {
        String message = errorCode.getMessage() + FIELD_DETAIL_SEPARATOR + detail;
        LOG.warn("{}: {}", errorCode.getMessage(), ex.getMessage());
        return Result.failure(errorCode, message);
    }
}
