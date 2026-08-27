package wyq.pocket.money.common.idempotency.job;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import wyq.pocket.money.common.idempotency.mapper.IdempotencyRecordMapper;

/**
 * 幂等记录清理任务（M3 设计 §5）。
 *
 * <p>每日定时删除已过期（{@code expires_at} 早于当前时刻）的幂等记录，
 * 复用 SchedulingConfig 虚拟线程执行器。默认启用，可用
 * {@code pocket-money.idempotency.cleanup-enabled=false} 关闭。
 */
@Component
@ConditionalOnProperty(name = "pocket-money.idempotency.cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
public class IdempotencyCleanupJob {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyRecordMapper mapper;

    private final Clock clock;

    /**
     * 注入协作对象。
     *
     * @param mapper 幂等记录 Mapper
     * @param clock  时钟
     */
    public IdempotencyCleanupJob(IdempotencyRecordMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 清理过期记录。
     */
    @Scheduled(cron = "${pocket-money.idempotency.cleanup-cron:0 37 3 * * *}")
    public void run() {
        int deleted = mapper.deleteExpired(clock.instant());
        LOG.info("IDEMPOTENCY_CLEANUP deleted={}", deleted);
    }
}
