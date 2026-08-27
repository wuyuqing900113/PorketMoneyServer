package wyq.pocket.money.common.idempotency.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.idempotency.mapper.IdempotencyRecordMapper;

/**
 * IdempotencyCleanupJob 单元测试：定时清理过期幂等记录（M3 设计 §5）。
 */
class IdempotencyCleanupJobTest {

    @Test
    void runShouldDeleteExpiredRecords() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        IdempotencyRecordMapper mapper = mock(IdempotencyRecordMapper.class);
        when(mapper.deleteExpired(any())).thenReturn(3);
        IdempotencyCleanupJob job = new IdempotencyCleanupJob(mapper, clock);

        job.run();

        verify(mapper).deleteExpired(clock.instant());
    }
}
