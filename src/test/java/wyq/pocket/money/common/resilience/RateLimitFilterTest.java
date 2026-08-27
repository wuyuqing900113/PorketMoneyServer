package wyq.pocket.money.common.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import wyq.pocket.money.common.security.UserIdPrincipal;

/**
 * RateLimitFilter 单元测试：写接口按用户限流，超出返回 100007 + Retry-After；
 * 读请求与未认证请求不拦截（M3 设计 §7）。
 */
class RateLimitFilterTest {

    private final RateLimitService rateLimitService = mock(RateLimitService.class);

    private final JsonMapper jsonMapper = new JsonMapper();

    private final RateLimitFilter filter = new RateLimitFilter(rateLimitService, jsonMapper);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughReadRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/families/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, chain(called));

        assertThat(called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldPassThroughUnauthenticatedWrite() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, chain(called));

        assertThat(called).isTrue();
    }

    @Test
    void shouldPassThroughWriteWithinLimit() throws Exception {
        authenticate(1L);
        when(rateLimitService.tryAcquire(1L)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, chain(called));

        assertThat(called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectWhenLimitExhausted() throws Exception {
        authenticate(1L);
        when(rateLimitService.tryAcquire(1L)).thenReturn(false);
        when(rateLimitService.refreshPeriod()).thenReturn(Duration.ofMinutes(1));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, chain(called));

        assertThat(called).isFalse();
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        JsonNode body = jsonMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("code").intValue()).isEqualTo(100007);
    }

    private FilterChain chain(AtomicBoolean called) {
        return (req, res) -> called.set(true);
    }

    private void authenticate(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserIdPrincipal(userId, 2L, "PARENT", false), null, List.of()));
    }
}
