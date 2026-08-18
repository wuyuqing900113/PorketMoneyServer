package wyq.pocket.money.common.trace;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * TraceId 注入过滤器（最高优先级）。
 *
 * <p>请求头 {@code X-Trace-Id} 有值且通过 {@link TraceIds#isAcceptable(String)}
 * 白名单校验则沿用（支持端上透传），否则生成新 ID；外部输入一律校验后再写入
 * 响应头，防止 CRLF 响应头注入。写入 MDC 供日志携带，并回填响应头与统一
 * 响应体的 traceId 字段。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** TraceId 请求/响应头名称。 */
    public static final String HEADER_NAME = "X-Trace-Id";

    /** MDC 中的 TraceId 键名（logback pattern 与 JSON 日志引用该键）。 */
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String headerValue = request.getHeader(HEADER_NAME);
        String traceId = TraceIds.isAcceptable(headerValue) ? headerValue : TraceIds.generate();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
