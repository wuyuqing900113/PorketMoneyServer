package wyq.pocket.money.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.ai.domain.AiPendingAction;
import wyq.pocket.money.ai.mapper.AiMessageMapper;
import wyq.pocket.money.ai.mapper.AiPendingActionMapper;
import wyq.pocket.money.ai.mapper.AiSessionMapper;
import wyq.pocket.money.common.ai.AiProperties;
import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;

/**
 * AI 会话清理任务单元测试（M4 设计 §10.2/D35）：过期动作转 EXPIRED 并审计、
 * 终态动作删除、超期会话级联删除。
 */
class AiCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    private final AiPendingActionMapper pendingActionMapper = mock(AiPendingActionMapper.class);

    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);

    private final AiSessionMapper sessionMapper = mock(AiSessionMapper.class);

    private final AiProperties properties = new AiProperties(true, "TEXT", Duration.ofSeconds(60),
            Duration.ofDays(7), true, "0 43 4 * * *",
            new AiProperties.RateLimit(10, Duration.ofMinutes(1)), new AiProperties.Stub(false));

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AuditService auditService = mock(AuditService.class);

    private final AiCleanupJob job = new AiCleanupJob(pendingActionMapper, messageMapper,
            sessionMapper, properties, clock, auditService);

    @Test
    void cleanupShouldExpireAndDelete() {
        when(pendingActionMapper.findExpiredPending(NOW)).thenReturn(List.of(expiredAction()));

        job.cleanup();

        verify(pendingActionMapper).updateExpire(1L);
        verify(pendingActionMapper).deleteTerminalBefore(any(Instant.class));
        verify(messageMapper).deleteBySessionBefore(any(Instant.class));
        verify(pendingActionMapper).deleteBySessionBefore(any(Instant.class));
        verify(sessionMapper).deleteBefore(any(Instant.class));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.AI_ACTION_EXPIRED);
    }

    private static AiPendingAction expiredAction() {
        AiPendingAction action = new AiPendingAction();
        action.setId(1L);
        action.setUserId(10L);
        action.setIntent("DEPOSIT");
        return action;
    }
}
