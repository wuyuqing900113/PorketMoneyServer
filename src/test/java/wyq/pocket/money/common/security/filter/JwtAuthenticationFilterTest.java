package wyq.pocket.money.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import tools.jackson.databind.json.JsonMapper;

import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.security.jwt.JwtProperties;
import wyq.pocket.money.common.security.jwt.JwtTokenService;

/**
 * JwtAuthenticationFilter 单元测试（T2）：Bearer 解析、验签拒绝语义、
 * SecurityContext 注入、mcp 强制拦截与豁免路径（M1 设计 §4.6）。
 */
class JwtAuthenticationFilterTest {

    private static final String PASSWORD_PATH = "/api/v1/users/me/password";

    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final JwtTokenService tokenService = new JwtTokenService(
            new JwtProperties(randomSecret(), Duration.ofMinutes(15), Duration.ofDays(14)));

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(tokenService, new JsonMapper());

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    private MockFilterChain filterChain;

    private static String randomSecret() {
        byte[] secret = new byte[64];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestWithoutAuthorizationHeaderShouldPassThroughUnauthenticated() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonBearerSchemeShouldPassThroughUnauthenticated() throws Exception {
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER, "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blankBearerValueShouldPassThroughUnauthenticated() throws Exception {
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER, "Bearer    ");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedTokenShouldPassThroughUnauthenticated() throws Exception {
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER, "Bearer not.a.jwt");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refreshTokenShouldBeRejectedWhenUsedAsAccess() throws Exception {
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER,
                "Bearer " + tokenService.issueRefreshToken(7L));

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validAccessTokenShouldPopulateSecurityContext() throws Exception {
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER,
                "Bearer " + tokenService.issueAccessToken(7L, 3L, "PARENT", false));

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("7");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PARENT");
        UserIdPrincipal principal = (UserIdPrincipal) authentication.getPrincipal();
        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.familyId()).isEqualTo(3L);
        assertThat(principal.role()).isEqualTo("PARENT");
        assertThat(principal.mustChangePassword()).isFalse();
    }

    @Test
    void mcpTokenShouldBeBlockedOnProtectedPath() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/users/me");
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER,
                "Bearer " + tokenService.issueAccessToken(9L, 2L, "CHILD", true));

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"code\":200010");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void mcpTokenShouldBeBlockedForNonPostOnExemptPath() throws Exception {
        request.setMethod("GET");
        request.setRequestURI(PASSWORD_PATH);
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER,
                "Bearer " + tokenService.issueAccessToken(9L, 2L, "CHILD", true));

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("\"code\":200010");
    }

    @Test
    void mcpTokenShouldBeExemptForPasswordChange() throws Exception {
        request.setMethod("POST");
        request.setRequestURI(PASSWORD_PATH);
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER,
                "Bearer " + tokenService.issueAccessToken(9L, 2L, "CHILD", true));

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void mcpTokenShouldBeExemptForLogout() throws Exception {
        request.setMethod("POST");
        request.setRequestURI(LOGOUT_PATH);
        request.addHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER,
                "Bearer " + tokenService.issueAccessToken(9L, 2L, "CHILD", true));

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
