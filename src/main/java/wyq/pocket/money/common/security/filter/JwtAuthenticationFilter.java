package wyq.pocket.money.common.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import wyq.pocket.money.common.audit.SecurityLogger;
import wyq.pocket.money.common.security.SecurityErrorCode;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.security.jwt.JwtTokenService;
import wyq.pocket.money.common.web.Result;

/**
 * JWT 认证过滤器：Bearer 解析 → HS256 验签 → SecurityContext 注入（M1 设计 §3.1 / §4.2）。
 *
 * <p>只做「尝试认证」：令牌缺失、格式非法、验签失败或类型不符一律以
 * 未认证状态放行，由过滤链统一走 401 出口（SECURITY 日志记录原因）；
 * 首次改密强制位（mcp）在认证成功后于本过滤器执行（§4.6），被拦截请求
 * 返回 HTTP 200 + Result(200010)（仅 100003/100004 使用非 200 状态码，§10.1）。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证请求头名称。 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 令牌前缀（RFC 6750）。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 角色授权前缀：ROLE_{角色}，供 hasRole 表达式使用。 */
    private static final String ROLE_PREFIX = "ROLE_";

    /** mcp 豁免路径：修改本人密码。 */
    private static final String PATH_CHANGE_PASSWORD = "/api/v1/users/me/password";

    /** mcp 豁免路径：登出。 */
    private static final String PATH_LOGOUT = "/api/v1/auth/logout";

    private static final String METHOD_POST = "POST";

    private final JwtTokenService tokenService;

    private final JsonMapper jsonMapper;

    /**
     * 构造过滤器。
     *
     * @param tokenService JWT 校验服务
     * @param jsonMapper   Jackson 3 JsonMapper（Boot 4 自动配置，线程安全共享）
     */
    public JwtAuthenticationFilter(JwtTokenService tokenService, JsonMapper jsonMapper) {
        this.tokenService = tokenService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = resolveBearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Jwt jwt = parseAccessToken(token, request);
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        UserIdPrincipal principal = toPrincipal(jwt);
        SecurityContextHolder.getContext().setAuthentication(toAuthentication(principal));
        if (principal.mustChangePassword() && !isMustChangePasswordExempt(request)) {
            SecurityLogger.warn("ACCESS_DENIED path={} user={} reason=MUST_CHANGE_PASSWORD",
                    request.getRequestURI(), principal.userId());
            writeMustChangePasswordResponse(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 解析并校验 access 令牌；任何失败记安全日志并返回 null（以未认证放行）。
     */
    private Jwt parseAccessToken(String token, HttpServletRequest request) {
        Jwt jwt;
        try {
            jwt = tokenService.parse(token);
        } catch (JwtException e) {
            SecurityLogger.warn("UNAUTHENTICATED_REJECT path={} reason=TOKEN_INVALID detail={}",
                    request.getRequestURI(), e.getMessage());
            return null;
        }
        if (!JwtTokenService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TYPE))) {
            SecurityLogger.warn("UNAUTHENTICATED_REJECT path={} reason=TOKEN_TYPE_MISMATCH",
                    request.getRequestURI());
            return null;
        }
        if (!hasRequiredAccessClaims(jwt)) {
            SecurityLogger.warn("UNAUTHENTICATED_REJECT path={} reason=TOKEN_CLAIMS_MISSING",
                    request.getRequestURI());
            return null;
        }
        return jwt;
    }

    private boolean hasRequiredAccessClaims(Jwt jwt) {
        return jwt.getSubject() != null
                && jwt.getClaimAsString(JwtTokenService.CLAIM_FAMILY) != null
                && jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE) != null
                && jwt.getClaimAsBoolean(JwtTokenService.CLAIM_MUST_CHANGE_PASSWORD) != null;
    }

    private UserIdPrincipal toPrincipal(Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        long familyId = Long.parseLong(jwt.getClaimAsString(JwtTokenService.CLAIM_FAMILY));
        String role = jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE);
        boolean mustChangePassword = Boolean.TRUE
                .equals(jwt.getClaimAsBoolean(JwtTokenService.CLAIM_MUST_CHANGE_PASSWORD));
        return new UserIdPrincipal(userId, familyId, role, mustChangePassword);
    }

    private UsernamePasswordAuthenticationToken toAuthentication(UserIdPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + principal.role())));
    }

    private boolean isMustChangePasswordExempt(HttpServletRequest request) {
        if (!METHOD_POST.equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return PATH_CHANGE_PASSWORD.equals(path) || PATH_LOGOUT.equals(path);
    }

    private void writeMustChangePasswordResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(
                Result.failure(SecurityErrorCode.MUST_CHANGE_PASSWORD_FIRST)));
    }
}
