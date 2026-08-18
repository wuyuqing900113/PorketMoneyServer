package wyq.pocket.money.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * JwtTokenService 单元测试（Spike S2）：HS256 签验、claims、过期、篡改、密钥校验。
 */
class JwtTokenServiceTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);

    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final JwtTokenService service = new JwtTokenService(testProperties());

    private static JwtProperties testProperties() {
        return new JwtProperties(randomSecret(), ACCESS_TTL, REFRESH_TTL);
    }

    private static String randomSecret() {
        byte[] secret = new byte[64];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    @Test
    void accessTokenShouldRoundTripWithClaims() {
        String token = service.issueAccessToken(7L, 3L, "PARENT", false);

        Jwt jwt = service.parse(token);
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_FAMILY)).isEqualTo("3");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE)).isEqualTo("PARENT");
        assertThat(jwt.getClaimAsBoolean(JwtTokenService.CLAIM_MUST_CHANGE_PASSWORD)).isFalse();
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_TYPE)).isEqualTo(JwtTokenService.TYPE_ACCESS);
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void refreshTokenShouldCarryMinimalClaims() {
        String token = service.issueRefreshToken(7L);

        Jwt jwt = service.parse(token);
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_TYPE)).isEqualTo(JwtTokenService.TYPE_REFRESH);
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE)).isNull();
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_FAMILY)).isNull();
    }

    @Test
    void expiredAccessTokenShouldBeRejected() {
        JwtProperties properties = testProperties();
        Clock past = Clock.fixed(Instant.now().minus(ACCESS_TTL).minusSeconds(60), ZoneOffset.UTC);
        String token = new JwtTokenService(properties, past).issueAccessToken(1L, 1L, "PARENT", false);

        assertThatThrownBy(() -> new JwtTokenService(properties).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenShouldBeRejected() {
        String token = service.issueAccessToken(1L, 1L, "PARENT", false);
        char flipped = token.charAt(10) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, 10) + flipped + token.substring(11);

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectBlankSecret() {
        assertThatThrownBy(() -> new JwtTokenService(new JwtProperties("", ACCESS_TTL, REFRESH_TTL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    void shouldRejectShortSecret() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new JwtTokenService(new JwtProperties(shortSecret, ACCESS_TTL, REFRESH_TTL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    void ttlAccessorsShouldMatchConfig() {
        assertThat(service.accessTtlSeconds()).isEqualTo(900L);
        assertThat(service.refreshTtl()).isEqualTo(REFRESH_TTL);
    }
}
