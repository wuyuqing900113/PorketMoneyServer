package wyq.pocket.money.user.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.audit.SecurityLogger;
import wyq.pocket.money.common.crypto.Hashes;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.jwt.JwtTokenService;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.user.domain.RefreshToken;
import wyq.pocket.money.user.domain.User;
import wyq.pocket.money.user.dto.TokenPairResponse;
import wyq.pocket.money.user.mapper.FamilyMemberMapper;
import wyq.pocket.money.user.mapper.RefreshTokenMapper;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * refresh 令牌管理：签发落库、轮转、重用检测、吊销（M1 设计 §4.3 / §4.4）。
 *
 * <p>服务端仅存 SHA-256 哈希；轮转保证任一时刻同一用户只有一个有效
 * refresh 令牌，已吊销令牌再次出现即判定泄露：ERROR 安全告警 +
 * 吊销该用户全部令牌。一切刷新失败统一 100003（§4.4），失败原因
 * 不可区分以防令牌状态探测。
 */
@Component
public class RefreshTokenService {

    private final JwtTokenService jwtTokenService;

    private final RefreshTokenMapper refreshTokenMapper;

    private final UserMapper userMapper;

    private final FamilyMemberMapper familyMemberMapper;

    private final AuditService auditService;

    private final Clock clock;

    /**
     * 生产构造：使用系统 UTC 时钟。
     *
     * <p>多构造器场景须显式标注注入点，Spring 不会自动推断。
     *
     * @param jwtTokenService      JWT 服务
     * @param refreshTokenMapper   令牌 Mapper
     * @param userMapper           用户 Mapper
     * @param familyMemberMapper   成员关系 Mapper
     * @param auditService         审计服务
     */
    @Autowired
    public RefreshTokenService(JwtTokenService jwtTokenService,
                               RefreshTokenMapper refreshTokenMapper,
                               UserMapper userMapper,
                               FamilyMemberMapper familyMemberMapper,
                               AuditService auditService) {
        this(jwtTokenService, refreshTokenMapper, userMapper, familyMemberMapper,
                auditService, Clock.systemUTC());
    }

    /**
     * 可测试构造：允许注入固定时钟。
     *
     * @param jwtTokenService    JWT 服务
     * @param refreshTokenMapper 令牌 Mapper
     * @param userMapper         用户 Mapper
     * @param familyMemberMapper 成员关系 Mapper
     * @param auditService       审计服务
     * @param clock              时钟
     */
    RefreshTokenService(JwtTokenService jwtTokenService,
                        RefreshTokenMapper refreshTokenMapper,
                        UserMapper userMapper,
                        FamilyMemberMapper familyMemberMapper,
                        AuditService auditService,
                        Clock clock) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenMapper = refreshTokenMapper;
        this.userMapper = userMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * 签发新令牌对并落库 refresh 哈希（登录成功 / 轮转成功共用）。
     *
     * @param user     用户
     * @param familyId 所属家庭 ID
     * @return 令牌对响应
     */
    public TokenPairResponse issue(User user, long familyId) {
        String accessToken = jwtTokenService.issueAccessToken(user.getId(), familyId,
                user.getRole(), user.isMustChangePassword());
        String refreshToken = jwtTokenService.issueRefreshToken(user.getId());
        Instant expiresAt = clock.instant().plus(jwtTokenService.refreshTtl());
        refreshTokenMapper.insert(new RefreshToken(user.getId(),
                Hashes.sha256Hex(refreshToken), expiresAt));
        return new TokenPairResponse(accessToken, refreshToken,
                jwtTokenService.accessTtlSeconds(), user.isMustChangePassword());
    }

    /**
     * 轮转：旧令牌吊销，签发新令牌对（M1 设计 §4.4）。
     *
     * @param refreshToken 当前 refresh 令牌
     * @return 新令牌对
     * @throws BusinessException 100003（签名非法 / 过期 / 未命中 / 已作废 / 账号失效）
     */
    public TokenPairResponse rotate(String refreshToken) {
        long userId = parseRefreshUserId(refreshToken);
        RefreshToken stored = findValidStoredToken(refreshToken, userId);
        refreshTokenMapper.revokeByTokenHash(stored.getTokenHash(), clock.instant());
        User user = userMapper.findById(userId);
        Long familyId = familyMemberMapper.findFamilyIdByUserId(userId);
        if (user == null || User.STATUS_DISABLED.equals(user.getStatus()) || familyId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        auditService.record(new AuditEntry(userId, AuditAction.TOKEN_REFRESH,
                "REFRESH_TOKEN", stored.getTokenHash(), null));
        return issue(user, familyId);
    }

    /**
     * 登出吊销：吊销提交的 refresh 令牌（§5.3）。
     *
     * <p>幂等：令牌非法或已失效时静默成功（access 短 TTL 自然过期）。
     *
     * @param refreshToken 待吊销令牌
     */
    public void revoke(String refreshToken) {
        try {
            parseRefreshUserId(refreshToken);
            refreshTokenMapper.revokeByTokenHash(Hashes.sha256Hex(refreshToken), clock.instant());
        } catch (BusinessException e) {
            // 幂等登出：非法 / 过期 / 类型不符的令牌无需吊销，拒绝信息不外露
        }
    }

    /**
     * 吊销用户全部未吊销令牌（改密 / 移出家庭 / 重用检测，§4.3）。
     *
     * @param userId 用户 ID
     */
    public void revokeAll(long userId) {
        refreshTokenMapper.revokeAllByUserId(userId, clock.instant());
    }

    private long parseRefreshUserId(String refreshToken) {
        Jwt claims;
        try {
            claims = jwtTokenService.parse(refreshToken);
        } catch (JwtException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (!JwtTokenService.TYPE_REFRESH.equals(claims.getClaimAsString(JwtTokenService.CLAIM_TYPE))) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return Long.parseLong(claims.getSubject());
    }

    private RefreshToken findValidStoredToken(String refreshToken, long userId) {
        RefreshToken stored = refreshTokenMapper.findByTokenHash(Hashes.sha256Hex(refreshToken));
        if (stored == null || !stored.getExpiresAt().isAfter(clock.instant())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (stored.getRevokedAt() != null) {
            detectReuse(userId);
        }
        return stored;
    }

    private void detectReuse(long userId) {
        SecurityLogger.error("TOKEN_REUSE_DETECTED user={}", userId);
        auditService.record(new AuditEntry(userId, AuditAction.TOKEN_REUSE_DETECTED,
                "USER", String.valueOf(userId), null));
        refreshTokenMapper.revokeAllByUserId(userId, clock.instant());
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
}
