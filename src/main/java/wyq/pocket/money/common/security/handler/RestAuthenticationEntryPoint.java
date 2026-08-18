package wyq.pocket.money.common.security.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import wyq.pocket.money.common.audit.SecurityLogger;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.common.web.Result;

/**
 * 未认证请求出口：令牌缺失 / 无效 / 过期一律返回
 * HTTP 401 + Result(100003) JSON，并记安全日志（M1 设计 §4.2、§9.2）。
 *
 * <p>与 GlobalExceptionHandler 对 MVC 层 AuthenticationException 的映射
 * 输出同一 Result 契约（M1 设计 §4.8）。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    /**
     * 注入 JSON 序列化器。
     *
     * @param jsonMapper Jackson 3 JsonMapper（Boot 4 自动配置；
     *                   Jackson 2 的 com.fasterxml ObjectMapper 已不再是装配 Bean）
     */
    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        SecurityLogger.warn("UNAUTHENTICATED_REJECT path={} reason={}",
                request.getRequestURI(), authException.getMessage());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(Result.failure(CommonErrorCode.UNAUTHORIZED)));
    }
}
