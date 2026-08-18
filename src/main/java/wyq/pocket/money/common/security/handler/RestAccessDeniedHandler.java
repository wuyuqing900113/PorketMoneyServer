package wyq.pocket.money.common.security.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import wyq.pocket.money.common.audit.SecurityLogger;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.common.web.Result;

/**
 * 越权请求出口：已认证但无权限时返回 HTTP 403 + Result(100004) JSON，
 * 并记安全日志（M1 设计 §4.2、§9.2）。
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    /**
     * 注入 JSON 序列化器。
     *
     * @param jsonMapper Jackson 3 JsonMapper（Boot 4 自动配置；
     *                   Jackson 2 的 com.fasterxml ObjectMapper 已不再是装配 Bean）
     */
    public RestAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        SecurityLogger.warn("ACCESS_DENIED path={} user={}", request.getRequestURI(), currentUserName());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(Result.failure(CommonErrorCode.FORBIDDEN)));
    }

    private String currentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
