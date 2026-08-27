package wyq.pocket.money.common.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.common.web.ErrorCode;
import wyq.pocket.money.common.web.Result;

/**
 * 幂等拦截过滤器（M3 设计 §5）。
 *
 * <p>位于安全链之后（order=-70），对已认证的写操作（POST/PUT/DELETE）强制
 * 读取 {@code Idempotency-Key} 请求头并执行两阶段幂等：缺失 → 100008；
 * 重放 → 返回缓存原始响应；同键不同体 → 100009；受理中 → 100006；否则放行
 * 并在业务完成后回填 / 释放。未认证请求（注册 / 登录 / 刷新）不拦截。
 */
@Component
@Order(IdempotencyFilter.ORDER)
public class IdempotencyFilter extends OncePerRequestFilter {

    /** 过滤器顺序（安全链之后、限流之内）。 */
    public static final int ORDER = -70;

    /** 幂等键请求头。 */
    public static final String HEADER_NAME = "Idempotency-Key";

    private static final String METHOD_POST = "POST";

    private static final String METHOD_PUT = "PUT";

    private static final String METHOD_DELETE = "DELETE";

    private final IdempotencyService idempotencyService;

    private final IdempotencyProperties properties;

    private final JsonMapper jsonMapper;

    /**
     * 注入协作对象。
     *
     * @param idempotencyService 幂等服务
     * @param properties         幂等配置
     * @param jsonMapper         JSON 序列化器（Jackson 3）
     */
    public IdempotencyFilter(IdempotencyService idempotencyService,
                             IdempotencyProperties properties, JsonMapper jsonMapper) {
        this.idempotencyService = idempotencyService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isIdempotentWrite(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Long userId = resolveUserId();
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = request.getHeader(HEADER_NAME);
        if (isKeyInvalid(key)) {
            writeResult(response, CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
            return;
        }
        execute(request, response, filterChain, userId, key);
    }

    private void execute(HttpServletRequest request, HttpServletResponse response,
                         FilterChain filterChain, long userId, String key)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        HttpServletRequest wrapped = new CachedBodyRequestWrapper(request, body);
        IdempotencyOutcome outcome;
        try {
            outcome = idempotencyService.begin(userId, key, request.getMethod(),
                    request.getRequestURI(), body);
        } catch (DataAccessException e) {
            writeResult(response, CommonErrorCode.DATABASE_ERROR);
            return;
        }
        if (outcome.decision() != IdempotencyOutcome.Decision.PROCEED) {
            respondOutcome(response, outcome);
            return;
        }
        IdempotencyContext.set(key);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrapped, cachedResponse);
            finish(userId, key, cachedResponse);
        } finally {
            IdempotencyContext.clear();
        }
        cachedResponse.copyBodyToResponse();
    }

    private void respondOutcome(HttpServletResponse response, IdempotencyOutcome outcome)
            throws IOException {
        switch (outcome.decision()) {
            case REPLAY -> writeReplay(response, outcome.record());
            case CONFLICT -> writeResult(response, CommonErrorCode.IDEMPOTENCY_CONFLICT);
            case IN_PROGRESS -> writeResult(response, CommonErrorCode.DUPLICATE_REQUEST);
            default -> { }
        }
    }

    private void finish(long userId, String key, ContentCachingResponseWrapper cachedResponse) {
        byte[] body = cachedResponse.getContentAsByteArray();
        if (isSuccess(cachedResponse.getStatus(), body)) {
            idempotencyService.markProcessed(userId, key, Result.SUCCESS_CODE,
                    new String(body, StandardCharsets.UTF_8));
        } else {
            idempotencyService.markFailed(userId, key);
        }
    }

    private boolean isSuccess(int status, byte[] body) {
        if (status >= HttpStatus.BAD_REQUEST.value()) {
            return false;
        }
        try {
            JsonNode node = jsonMapper.readTree(body);
            JsonNode code = node.get("code");
            return code != null && code.intValue() == Result.SUCCESS_CODE;
        } catch (JacksonException e) {
            return false;
        }
    }

    private boolean isIdempotentWrite(HttpServletRequest request) {
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

    /**
     * 判断幂等键是否缺失 / 空白 / 超长（M3 设计 §5：键为必填 UUID）。
     *
     * @param key 幂等键请求头值
     * @return 无效返回 true
     */
    private boolean isKeyInvalid(String key) {
        return key == null || key.isBlank() || key.length() > properties.keyMaxLength();
    }

    private void writeResult(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(Result.failure(errorCode)));
    }

    private void writeReplay(HttpServletResponse response, IdempotencyRecord record)
            throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(record.getRespBody());
    }
}
