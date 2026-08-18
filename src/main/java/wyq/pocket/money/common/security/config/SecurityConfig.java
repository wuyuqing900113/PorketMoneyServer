package wyq.pocket.money.common.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;

import wyq.pocket.money.common.crypto.CryptoProperties;
import wyq.pocket.money.common.security.filter.JwtAuthenticationFilter;
import wyq.pocket.money.common.security.handler.RestAccessDeniedHandler;
import wyq.pocket.money.common.security.handler.RestAuthenticationEntryPoint;
import wyq.pocket.money.common.security.jwt.JwtProperties;
import wyq.pocket.money.common.security.jwt.JwtTokenService;

/**
 * Spring Security 配置：无状态 JWT API（M1 设计 §4.2）。
 *
 * <p>会话 STATELESS、CSRF 关闭（纯 Bearer API）；白名单最小化，
 * 其余请求一律要求认证。认证 / 授权拒绝分别走统一 JSON 出口
 * （401 + Result 100003 / 403 + Result 100004）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CryptoProperties.class})
public class SecurityConfig {

    /** 匿名可达路径白名单（最小化原则，M1 设计 §4.2）。 */
    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
        "/actuator/health/**", "/error", "/v3/api-docs/**",
        "/swagger-ui/**", "/swagger-ui.html"
    };

    /** BCrypt 强度（M1 设计 §4.1，D2）。 */
    private static final int BCRYPT_STRENGTH = 10;

    /**
     * 密码哈希编码器：BCrypt strength=10（M1 设计 §4.1）。
     *
     * @return 编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * 安全过滤链。
     *
     * <p>JWT 过滤器以普通对象注入过滤链而非 Spring Bean：避免 Boot 将
     * Filter bean 自动注册到 Servlet 容器导致双重执行。
     *
     * @param http                      HttpSecurity
     * @param authenticationEntryPoint  未认证出口（401 + Result 100003）
     * @param accessDeniedHandler       越权出口（403 + Result 100004）
     * @param tokenService              JWT 校验服务
     * @param jsonMapper                Jackson 3 JsonMapper（过滤器输出 Result JSON）
     * @return 过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            JwtTokenService tokenService,
            JsonMapper jsonMapper) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(tokenService, jsonMapper);
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
