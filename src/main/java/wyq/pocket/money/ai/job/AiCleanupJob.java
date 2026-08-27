package wyq.pocket.money.ai.job;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import wyq.pocket.money.ai.domain.AiPendingAction;
import wyq.pocket.money.ai.mapper.AiMessageMapper;
import wyq.pocket.money.ai.mapper.AiPendingActionMapper;
import wyq.pocket.money.ai.mapper.AiSessionMapper;
import wyq.pocket.money.common.ai.AiProperties;
import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;

/**
 * AI 会话清理任务（M4 设计 §10.2/D35）：每日清理超期数据。
 *
 * <p>范围：过期未确认动作（PENDING → EXPIRED 并审计）、超 TTL 终态动作、
 * 超会话 TTL 的会话（级联消息与动作）。复用 {@code SchedulingConfig} 虚拟
 * 线程调度器；开关由 {@code pocket-money.ai.cleanup-enabled} 控制。
 */
@Component
@ConditionalOnProperty(prefix = "pocket-money.ai", name = "cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
public class AiCleanupJob {

    private final AiPendingActionMapper pendingActionMapper;

    private final AiMessageMapper messageMapper;

    private final AiSessionMapper sessionMapper;

    private final AiProperties properties;

    private final Clock clock;

    private final AuditService auditService;

    /**
     * 注入协作对象。
     *
     * @param pendingActionMapper 待确认动作 Mapper
     * @param messageMapper       消息 Mapper
     * @param sessionMapper       会话 Mapper
     * @param properties          AI 配置（会话 TTL）
     * @param clock               时钟
     * @param auditService        审计服务
     */
    public AiCleanupJob(AiPendingActionMapper pendingActionMapper, AiMessageMapper messageMapper,
                        AiSessionMapper sessionMapper, AiProperties properties, Clock clock,
                        AuditService auditService) {
        this.pendingActionMapper = pendingActionMapper;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.properties = properties;
        this.clock = clock;
        this.auditService = auditService;
    }

    /**
     * 每日清理：过期动作 → 终态动作 → 超期会话。
     */
    @Scheduled(cron = "${pocket-money.ai.cleanup-cron:0 43 4 * * *}")
    public void cleanup() {
        expirePendingActions();
        deleteStaleActions();
        deleteStaleSessions();
    }

    private void expirePendingActions() {
        Instant now = clock.instant();
        List<AiPendingAction> expired = pendingActionMapper.findExpiredPending(now);
        for (AiPendingAction action : expired) {
            pendingActionMapper.updateExpire(action.getId());
            auditService.record(new AuditEntry(action.getUserId(), AuditAction.AI_ACTION_EXPIRED,
                    "AI_PENDING_ACTION", String.valueOf(action.getId()), action.getIntent()));
        }
    }

    private void deleteStaleActions() {
        pendingActionMapper.deleteTerminalBefore(clock.instant().minus(properties.sessionTtl()));
    }

    private void deleteStaleSessions() {
        Instant cutoff = clock.instant().minus(properties.sessionTtl());
        messageMapper.deleteBySessionBefore(cutoff);
        pendingActionMapper.deleteBySessionBefore(cutoff);
        sessionMapper.deleteBefore(cutoff);
    }
}
