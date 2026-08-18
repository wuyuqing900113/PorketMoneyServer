package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import wyq.pocket.money.common.security.jwt.JwtTokenService;

/**
 * 认证失败场景集成测试（M1 设计 §12.2 AuthFailureScenariosIT）。
 *
 * <p>错误凭证 200002（防枚举）；过期 access / 篡改签名一律 401 + 100003；
 * 停用账号登录 200004。过期令牌用与 JwtTokenService 相同的测试密钥
 * 本地伪造（仅测试用零值密钥）。
 */
class AuthFailureScenariosPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PHONE = "13910000011";

    /** 与基座 @SpringBootTest properties 中 JWT_SECRET 一致的测试零值密钥。 */
    private static final String TEST_JWT_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Test
    void wrongPasswordShouldReturn200002() {
        registerAndLogin(PHONE);
        loginAs(PHONE, "WrongPass1").then().statusCode(200).body("code", equalTo(200002));
        // 账号不存在同为 200002，防枚举
        loginAs("13919999999", DEFAULT_PASSWORD)
                .then().statusCode(200).body("code", equalTo(200002));
    }

    @Test
    void expiredAccessTokenShouldReturn401And100003() {
        TestAccount account = registerAndLogin(PHONE);
        String expired = forgeExpiredAccessToken(account.userId(), account.familyId());
        withToken(expired).when().get("/api/v1/users/me")
                .then().statusCode(401).body("code", equalTo(100003));
    }

    @Test
    void tamperedSignatureShouldReturn401And100003() {
        TestAccount account = registerAndLogin(PHONE);
        String token = account.accessToken();
        char last = token.charAt(token.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;
        withToken(tampered).when().get("/api/v1/users/me")
                .then().statusCode(401).body("code", equalTo(100003));
    }

    @Test
    void disabledChildLoginShouldReturn200004() {
        TestAccount parent = registerAndLogin("13910000012");
        long childId = createChild(parent, "pgfail01a");
        // 移除成员 → 账号停用
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));
        loginAs("pgfail01a", CHILD_INITIAL_PASSWORD)
                .then().statusCode(200).body("code", equalTo(200004));
    }

    /** 以测试密钥伪造 exp 已过的 access 令牌（claims 与真实签发一致）。 */
    private static String forgeExpiredAccessToken(long userId, long familyId) {
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(TEST_JWT_SECRET),
                HMAC_ALGORITHM);
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS256).build();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now.minus(Duration.ofHours(3)))
                .expiresAt(now.minus(Duration.ofHours(1)))
                .id("forged-expired-test-token")
                .claim(JwtTokenService.CLAIM_FAMILY, String.valueOf(familyId))
                .claim(JwtTokenService.CLAIM_ROLE, "PARENT")
                .claim(JwtTokenService.CLAIM_MUST_CHANGE_PASSWORD, false)
                .claim(JwtTokenService.CLAIM_TYPE, JwtTokenService.TYPE_ACCESS)
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}
