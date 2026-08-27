package wyq.pocket.money.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import wyq.pocket.money.common.crypto.Hashes;
import wyq.pocket.money.common.idempotency.IdempotencyOutcome.Decision;
import wyq.pocket.money.common.idempotency.mapper.IdempotencyRecordMapper;

/**
 * IdempotencyService 单元测试：两阶段幂等核心（放行 / 重放 / 冲突 / 受理中 /
 * 超期接管）与回填 / 释放的尽力而为语义（M3 设计 §5）。
 */
class IdempotencyServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final IdempotencyRecordMapper mapper = mock(IdempotencyRecordMapper.class);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final IdempotencyProperties properties =
            new IdempotencyProperties(64, Duration.ofDays(7), Duration.ofMinutes(1));

    private final IdempotencyService service = new IdempotencyService(mapper, properties, clock);

    @Test
    void beginShouldProceedWhenInsertSucceeds() {
        IdempotencyOutcome outcome = service.begin(1L, "k1", "POST", "/x", new byte[0]);

        assertThat(outcome.decision()).isEqualTo(Decision.PROCEED);
        assertThat(outcome.record()).isNull();
        verify(mapper).insert(eq(1L), eq("k1"), eq("POST"), eq("/x"), anyString(), any());
    }

    @Test
    void beginShouldReplayWhenProcessedAndHashMatches() {
        String hash = Hashes.sha256Hex("POST\n/x\n".getBytes(StandardCharsets.UTF_8));
        when(mapper.insert(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("dup"));
        IdempotencyRecord existing = processed(hash, "{\"code\":0}");
        when(mapper.findByUserAndKey(1L, "k1")).thenReturn(existing);

        IdempotencyOutcome outcome = service.begin(1L, "k1", "POST", "/x", new byte[0]);

        assertThat(outcome.decision()).isEqualTo(Decision.REPLAY);
        assertThat(outcome.record()).isSameAs(existing);
    }

    @Test
    void beginShouldConflictWhenProcessedButHashMismatch() {
        when(mapper.insert(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("dup"));
        IdempotencyRecord existing = processed("another-hash", "{\"code\":0}");
        when(mapper.findByUserAndKey(1L, "k1")).thenReturn(existing);

        IdempotencyOutcome outcome = service.begin(1L, "k1", "POST", "/x", new byte[0]);

        assertThat(outcome.decision()).isEqualTo(Decision.CONFLICT);
    }

    @Test
    void beginShouldReturnInProgressWhenNotStale() {
        when(mapper.insert(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("dup"));
        IdempotencyRecord existing = inProgress(NOW);
        when(mapper.findByUserAndKey(1L, "k1")).thenReturn(existing);

        IdempotencyOutcome outcome = service.begin(1L, "k1", "POST", "/x", new byte[0]);

        assertThat(outcome.decision()).isEqualTo(Decision.IN_PROGRESS);
    }

    @Test
    void beginShouldTakeoverWhenStaleInProgress() {
        when(mapper.insert(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenReturn(1);
        IdempotencyRecord existing = inProgress(NOW.minus(Duration.ofMinutes(5)));
        when(mapper.findByUserAndKey(1L, "k1")).thenReturn(existing);

        IdempotencyOutcome outcome = service.begin(1L, "k1", "POST", "/x", new byte[0]);

        assertThat(outcome.decision()).isEqualTo(Decision.PROCEED);
        verify(mapper).deleteByUserAndKey(1L, "k1");
    }

    @Test
    void markProcessedShouldSwallowMapperException() {
        when(mapper.markProcessed(eq(1L), eq("k1"), anyInt(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.markProcessed(1L, "k1", 0, "{}"))
                .doesNotThrowAnyException();
    }

    @Test
    void markFailedShouldSwallowMapperException() {
        when(mapper.deleteByUserAndKey(1L, "k1"))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.markFailed(1L, "k1")).doesNotThrowAnyException();
    }

    private IdempotencyRecord processed(String hash, String respBody) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setStatus(IdempotencyRecord.STATUS_PROCESSED);
        record.setPayloadHash(hash);
        record.setRespBody(respBody);
        return record;
    }

    private IdempotencyRecord inProgress(Instant createdAt) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setStatus(IdempotencyRecord.STATUS_IN_PROGRESS);
        record.setCreatedAt(createdAt);
        return record;
    }
}
