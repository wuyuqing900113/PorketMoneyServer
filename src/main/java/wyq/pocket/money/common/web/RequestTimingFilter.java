package wyq.pocket.money.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import wyq.pocket.money.common.trace.TraceIds;

/**
 * 慢接口剖析过滤器（M3 设计 §8）。
 *
 * <p>位于安全链之后、限流与幂等拦截之前（order=-90），统计每个请求耗时，
 * 超过阈值打印 WARN 日志（含 traceId），作为慢接口剖析入口。
 */
@Component
@Order(RequestTimingFilter.ORDER)
public class RequestTimingFilter extends OncePerRequestFilter {

    /** 过滤器顺序（安全链之后、限流之前）。 */
    public static final int ORDER = -90;

    /** 慢请求阈值（毫秒）。 */
    public static final long SLOW_THRESHOLD_MS = 500L;

    private static final Logger LOG = LoggerFactory.getLogger(RequestTimingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000L;
            if (costMs >= SLOW_THRESHOLD_MS) {
                LOG.warn("SLOW_REQUEST method={} uri={} costMs={} traceId={}",
                        request.getMethod(), request.getRequestURI(), costMs, TraceIds.current());
            }
        }
    }
}
