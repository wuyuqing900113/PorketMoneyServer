package wyq.pocket.money.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.user.domain.User;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * LoginGuardService 单元测试（M1 设计 §4.5 / §12.1）：
 * 计数累计、锁定生效与到期、成功清零。固定时钟 2026-08-17T00:00:00Z。
 */
class LoginGuardServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");

    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserMapper userMapper = mock(UserMapper.class);

    private final AuditService auditService = mock(AuditService.class);

    private final LoginGuardService guard = new LoginGuardService(userMapper, auditService,
            new LoginGuardProperties(5, LOCK_DURATION), Clock.fixed(NOW, ZoneOffset.UTC));

    private User userWith(int failedAttempts, Instant lockedUntil) {
        User user = new User();
        user.setId(7L);
        user.setFailedAttempts(failedAttempts);
        user.setLockedUntil(lockedUntil);
        return user;
    }

    @Test
    void shouldNotBeLockedWhenLockedUntilIsNull() {
        assertThat(guard.isLocked(userWith(3, null))).isFalse();
    }

    @Test
    void shouldNotBeLockedAfterLockExpiry() {
        assertThat(guard.isLocked(userWith(0, NOW.minusSeconds(1)))).isFalse();
    }

    @Test
    void shouldBeLockedWhileLockedUntilInFuture() {
        assertThat(guard.isLocked(userWith(0, NOW.plusSeconds(1)))).isTrue();
    }

    @Test
    void failureBelowThresholdShouldIncrementCounter() {
        guard.recordFailure(userWith(2, null));

        verify(userMapper).updateLoginState(7L, 3, null);
        verify(auditService, never()).record(any());
    }

    @Test
    void fifthFailureShouldLockAndResetCounter() {
        guard.recordFailure(userWith(4, null));

        ArgumentCaptor<Instant> lockedUntil = ArgumentCaptor.forClass(Instant.class);
        verify(userMapper).updateLoginState(eq(7L), eq(0), lockedUntil.capture());
        assertThat(lockedUntil.getValue()).isEqualTo(NOW.plus(LOCK_DURATION));
        verify(auditService).record(new AuditEntry(7L, AuditAction.ACCOUNT_LOCKED,
                "USER", "7", null));
    }

    @Test
    void successShouldResetCounterAndLock() {
        guard.resetAfterSuccess(userWith(3, NOW.plusSeconds(30)));

        verify(userMapper).updateLoginState(7L, 0, null);
    }
}
