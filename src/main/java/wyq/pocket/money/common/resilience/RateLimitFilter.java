package wyq.pocket.money.common.resilience;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.common.web.Result;

/**
 * 写接口限流过滤器（M3 设计 §7）。
 *
 * <p>位于安全链之后、幂等拦截之前（order=-80），对已认证的写请求按用户
 * 限流：超出限额返回 100007 并携带 {@code Retry-After} 头。未认证请求
 * （注册 / 登录 / 刷新）不拦截；读请求不拦截。
 */
@Component
@Order(RateLimitFilter.ORDER)
public class RateLimitFilter extends OncePerRequestFilter {

    /** 过滤器顺序（安全链之后、幂等拦截之前）。 */
    public static final int ORDER = -80;

    private static final String METHOD_POST = "POST";

    private static final String METHOD_PUT = "PUT";

    private static final String METHOD_DELETE = "DELETE";

    private final RateLimitService rateLimitService;

    private final JsonMapper jsonMapper;

    /**
     * 注入协作对象。
     *
     * @param rateLimitService 限流服务
     * @param jsonMapper       JSON 序列化器（Jackson 3）
     */
    public RateLimitFilter(RateLimitService rateLimitService, JsonMapper jsonMapper) {
        this.rateLimitService = rateLimitService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isWrite(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Long userId = resolveUserId();
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!rateLimitService.tryAcquire(userId)) {
            writeRateLimited(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isWrite(HttpServletRequest request) {
        String method = request.getMethod();
        return METHOD_POST.equals(method) || METHOD_PUT.equals(method)
                || METHOD_DELETE.equals(method);
    }

    private Long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserIdPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds());
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(
                Result.failure(CommonErrorCode.RATE_LIMITED)));
    }

    private String retryAfterSeconds() {
        long seconds = rateLimitService.refreshPeriod().toSeconds();
        return String.valueOf(Math.max(1L, seconds));
    }
}
