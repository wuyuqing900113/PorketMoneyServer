package wyq.pocket.money.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.crypto.Hashes;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.jwt.JwtProperties;
import wyq.pocket.money.common.security.jwt.JwtTokenService;
import wyq.pocket.money.user.domain.RefreshToken;
import wyq.pocket.money.user.domain.User;
import wyq.pocket.money.user.dto.TokenPairResponse;
import wyq.pocket.money.user.mapper.FamilyMemberMapper;
import wyq.pocket.money.user.mapper.RefreshTokenMapper;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * RefreshTokenService 单元测试（M1 设计 §4.3 / §4.4 / §12.1）：
 * 签发落库、轮转、重用检测、吊销。使用真实 JwtTokenService（固定时钟）
 * 覆盖「验签 + typ + 落库行」三层校验；测试密钥为固定值，仅测试用。
 */
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");

    private static final String SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";

    private final RefreshTokenMapper tokenMapper = mock(RefreshTokenMapper.class);

    private final UserMapper userMapper = mock(UserMapper.class);

    private final FamilyMemberMapper memberMapper = mock(FamilyMemberMapper.class);

    private final AuditService auditService = mock(AuditService.class);

    private final JwtTokenService jwtTokenService = new JwtTokenService(
            new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(14)));

    private final RefreshTokenService service = new RefreshTokenService(jwtTokenService,
            tokenMapper, userMapper, memberMapper, auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private User parent() {
        User user = new User();
        user.setId(7L);
        user.setNickname("妈妈");
        user.setRole(User.ROLE_PARENT);
        user.setStatus(User.STATUS_ACTIVE);
        return user;
    }

    private RefreshToken storedRow(String tokenHash, Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken(7L, tokenHash, expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }

    private void expectUnauthorized(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode().getCode()).isEqualTo(100003);
    }

    @Test
    void issueShouldPersistHashRowAndReturnPair() {
        TokenPairResponse pair = service.issue(parent(), 3L);

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(pair.expiresIn()).isEqualTo(900L);
        assertThat(pair.mustChangePassword()).isFalse();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokenMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo(Hashes.sha256Hex(pair.refreshToken()));
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void rotateShouldRevokeOldRowAndIssueNewPair() {
        TokenPairResponse first = service.issue(parent(), 3L);
        String oldHash = Hashes.sha256Hex(first.refreshToken());
        when(tokenMapper.findByTokenHash(oldHash))
                .thenReturn(storedRow(oldHash, NOW.plus(Duration.ofDays(14)), null));
        when(userMapper.findById(7L)).thenReturn(parent());
        when(memberMapper.findFamilyIdByUserId(7L)).thenReturn(3L);

        TokenPairResponse second = service.rotate(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        verify(tokenMapper).revokeByTokenHash(oldHash, NOW);
        verify(tokenMapper, times(2)).insert(any());
        verify(auditService).record(new AuditEntry(7L, AuditAction.TOKEN_REFRESH,
                "REFRESH_TOKEN", oldHash, null));
    }

    @Test
    void rotateMalformedTokenShouldReturn100003() {
        assertThatThrownBy(() -> service.rotate("garbage"))
                .satisfies(this::expectUnauthorized);
        verifyNoInteractions(tokenMapper);
    }

    @Test
    void rotateAccessTokenShouldBeRejected() {
        String accessToken = jwtTokenService.issueAccessToken(7L, 3L, "PARENT", false);

        assertThatThrownBy(() -> service.rotate(accessToken))
                .satisfies(this::expectUnauthorized);
        verifyNoInteractions(tokenMapper);
    }

    @Test
    void rotateUnknownTokenShouldReturn100003() {
        String refreshToken = jwtTokenService.issueRefreshToken(7L);
        when(tokenMapper.findByTokenHash(Hashes.sha256Hex(refreshToken))).thenReturn(null);

        assertThatThrownBy(() -> service.rotate(refreshToken))
                .satisfies(this::expectUnauthorized);
        verify(tokenMapper, never()).revokeByTokenHash(any(), any());
    }

    @Test
    void rotateExpiredStoredRowShouldReturn100003() {
        String refreshToken = jwtTokenService.issueRefreshToken(7L);
        when(tokenMapper.findByTokenHash(Hashes.sha256Hex(refreshToken)))
                .thenReturn(storedRow(Hashes.sha256Hex(refreshToken), NOW, null));

        assertThatThrownBy(() -> service.rotate(refreshToken))
                .satisfies(this::expectUnauthorized);
    }

    @Test
    void rotateRevokedTokenShouldDetectReuseAndRevokeAll() {
        String refreshToken = jwtTokenService.issueRefreshToken(7L);
        when(tokenMapper.findByTokenHash(Hashes.sha256Hex(refreshToken)))
                .thenReturn(storedRow(Hashes.sha256Hex(refreshToken), NOW.plus(Duration.ofDays(14)),
                        NOW.minusSeconds(60)));

        assertThatThrownBy(() -> service.rotate(refreshToken))
                .satisfies(this::expectUnauthorized);
        verify(tokenMapper).revokeAllByUserId(7L, NOW);
        verify(auditService).record(new AuditEntry(7L, AuditAction.TOKEN_REUSE_DETECTED,
                "USER", "7", null));
        verify(tokenMapper, never()).revokeByTokenHash(any(), any());
    }

    @Test
    void rotateWithDisabledUserShouldReturn100003() {
        String refreshToken = jwtTokenService.issueRefreshToken(7L);
        when(tokenMapper.findByTokenHash(Hashes.sha256Hex(refreshToken)))
                .thenReturn(storedRow(Hashes.sha256Hex(refreshToken), NOW.plus(Duration.ofDays(14)), null));
        User disabled = parent();
        disabled.setStatus(User.STATUS_DISABLED);
        when(userMapper.findById(7L)).thenReturn(disabled);

        assertThatThrownBy(() -> service.rotate(refreshToken))
                .satisfies(this::expectUnauthorized);
    }

    @Test
    void rotateWithoutMembershipShouldReturn100003() {
        String refreshToken = jwtTokenService.issueRefreshToken(7L);
        when(tokenMapper.findByTokenHash(Hashes.sha256Hex(refreshToken)))
                .thenReturn(storedRow(Hashes.sha256Hex(refreshToken), NOW.plus(Duration.ofDays(14)), null));
        when(userMapper.findById(7L)).thenReturn(parent());
        when(memberMapper.findFamilyIdByUserId(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.rotate(refreshToken))
                .satisfies(this::expectUnauthorized);
    }

    @Test
    void revokeShouldRevokeStoredRow() {
        TokenPairResponse pair = service.issue(parent(), 3L);

        service.revoke(pair.refreshToken());

        verify(tokenMapper).revokeByTokenHash(eq(Hashes.sha256Hex(pair.refreshToken())), eq(NOW));
    }

    @Test
    void revokeInvalidTokenShouldStaySilent() {
        service.revoke("garbage");

        verifyNoInteractions(tokenMapper);
    }

    @Test
    void revokeAllShouldDelegateToMapper() {
        service.revokeAll(7L);

        verify(tokenMapper).revokeAllByUserId(7L, NOW);
    }
}
