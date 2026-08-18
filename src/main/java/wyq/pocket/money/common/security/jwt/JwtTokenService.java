package wyq.pocket.money.common.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * JWT 签发与校验服务（HS256，M1 设计 §4.1 / §4.3）。
 *
 * <p>access 令牌携带用户 / 家庭 / 角色 / 首次改密声明；refresh 令牌仅携带
 * {@code sub}/{@code jti}/{@code typ}。两类令牌以 {@code typ} 声明区分，
 * 验签通过但类型不符同样拒绝。密钥缺失或长度不足时启动即失败（fail-fast）。
 */
@Component
public final class JwtTokenService {

    /** 声明名：家庭 ID（字符串形式存储，避免数值类型歧义）。 */
    public static final String CLAIM_FAMILY = "fam";

    /** 声明名：角色（PARENT / CHILD）。 */
    public static final String CLAIM_ROLE = "role";

    /** 声明名：须先修改初始密码（孩子首次登录强制位）。 */
    public static final String CLAIM_MUST_CHANGE_PASSWORD = "mcp";

    /** 声明名：令牌类型。 */
    public static final String CLAIM_TYPE = "typ";

    /** 令牌类型值：access 令牌。 */
    public static final String TYPE_ACCESS = "access";

    /** 令牌类型值：refresh 令牌。 */
    public static final String TYPE_REFRESH = "refresh";

    private static final int MIN_SECRET_BYTES = 32;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final JwtEncoder encoder;

    private final JwtDecoder decoder;

    private final Clock clock;

    private final Duration accessTtl;

    private final Duration refreshTtl;

    /**
     * 生产构造：使用系统 UTC 时钟。
     *
     * <p>多构造器场景须显式标注注入点，Spring 不会自动推断。
     *
     * @param properties JWT 配置
     */
    @Autowired
    public JwtTokenService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * 可测试构造：允许注入固定时钟。
     *
     * @param properties JWT 配置
     * @param clock      时钟
     */
    JwtTokenService(JwtProperties properties, Clock clock) {
        SecretKey key = loadKey(properties.secret());
        // Security 7 API：构造器仅接受 JWK/JWKSource，对称密钥走 withSecretKey builder
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.clock = clock;
        this.accessTtl = properties.accessTtl();
        this.refreshTtl = properties.refreshTtl();
    }

    /**
     * 签发 access 令牌。
     *
     * @param userId             用户 ID
     * @param familyId           家庭 ID
     * @param role               角色（PARENT / CHILD）
     * @param mustChangePassword 是否须先修改初始密码
     * @return 已签名 JWT 字符串
     */
    public String issueAccessToken(long userId, long familyId, String role, boolean mustChangePassword) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_FAMILY, String.valueOf(familyId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_MUST_CHANGE_PASSWORD, mustChangePassword)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .build();
        return encode(claims);
    }

    /**
     * 签发 refresh 令牌（claims 最小化，服务端按 SHA-256 哈希持久化）。
     *
     * @param userId 用户 ID
     * @return 已签名 JWT 字符串
     */
    public String issueRefreshToken(long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(refreshTtl))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .build();
        return encode(claims);
    }

    /**
     * 解析并校验令牌（验签 + 过期）。
     *
     * @param token JWT 字符串
     * @return 解析后的 JWT
     * @throws JwtException 签名非法、已过期或格式错误
     */
    public Jwt parse(String token) {
        return decoder.decode(token);
    }

    /**
     * access 令牌有效期（秒），用于响应体 expiresIn 字段。
     *
     * @return 秒数
     */
    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /**
     * refresh 令牌有效期，供服务端落库 expires_at 使用。
     *
     * @return 有效期
     */
    public Duration refreshTtl() {
        return refreshTtl;
    }

    private String encode(JwtClaimsSet claims) {
        // Security 7 API：JwsHeader 移至 oauth2.jwt 包，with() 接受 JwsAlgorithm（MacAlgorithm 实现之）
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private static SecretKey loadKey(String base64Secret) {
        byte[] bytes = decodeConfiguredSecret(base64Secret);
        requireHmacSha256Length(bytes);
        return new SecretKeySpec(bytes, HMAC_ALGORITHM);
    }

    private static byte[] decodeConfiguredSecret(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置（环境变量注入，禁止硬编码）");
        }
        return Base64.getDecoder().decode(base64Secret.trim());
    }

    private static void requireHmacSha256Length(byte[] bytes) {
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET 长度不足：HS256 要求至少 32 字节");
        }
    }
}
