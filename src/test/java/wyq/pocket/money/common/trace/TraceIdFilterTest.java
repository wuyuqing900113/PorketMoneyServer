package wyq.pocket.money.common.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

/**
 * TraceIdFilter 单元测试。
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldGenerateTraceIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(TraceIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(mdcDuringChain.get()).isNotBlank().hasSize(16);
        assertThat(response.getHeader(TraceIdFilter.HEADER_NAME)).isEqualTo(mdcDuringChain.get());
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).as("请求结束后 MDC 必须清理").isNull();
    }

    @Test
    void shouldReuseTraceIdFromRequestHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER_NAME, "client-trace-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(TraceIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(mdcDuringChain.get()).isEqualTo("client-trace-001");
        assertThat(response.getHeader(TraceIdFilter.HEADER_NAME)).isEqualTo("client-trace-001");
    }

    @Test
    void shouldRejectCrlfInjectionAttempt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER_NAME, "evil\r\nX-Injected: 1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(TraceIdFilter.HEADER_NAME))
                .as("含 CRLF 的输入必须被丢弃并重新生成")
                .matches("[0-9a-f]{16}")
                .doesNotContain("evil");
    }

    @Test
    void shouldRejectInvalidCharactersAndGenerateNewId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER_NAME, "trace id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(TraceIdFilter.HEADER_NAME)).matches("[0-9a-f]{16}");
    }

    @Test
    void traceIdsIsAcceptableShouldEnforceWhitelist() {
        assertThat(TraceIds.isAcceptable("client-trace_001")).isTrue();
        assertThat(TraceIds.isAcceptable(null)).isFalse();
        assertThat(TraceIds.isAcceptable("")).isFalse();
        assertThat(TraceIds.isAcceptable("a".repeat(65))).isFalse();
        assertThat(TraceIds.isAcceptable("bad:value")).isFalse();
    }

    @Test
    void shouldClearMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            MDC.put(TraceIdFilter.MDC_KEY, "will-be-cleared");
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void traceIdsGenerateShouldProduceHexOfExpectedLength() {
        String traceId = TraceIds.generate();

        assertThat(traceId).hasSize(16).matches("[0-9a-f]{16}");
    }

    @Test
    void mockFilterChainShouldNotBreakFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.HEADER_NAME)).isNotBlank();
    }
}
