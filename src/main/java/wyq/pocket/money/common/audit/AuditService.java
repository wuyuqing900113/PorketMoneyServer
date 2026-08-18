package wyq.pocket.money.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import wyq.pocket.money.common.audit.mapper.AuditLogMapper;
import wyq.pocket.money.common.trace.TraceIds;

/**
 * 审计日志服务：{@code REQUIRES_NEW} 独立事务写 audit_log（M1 设计 §9.1）。
 *
 * <p>独立事务保证业务回滚时审计线索仍留存；写入失败记 ERROR 运行日志
 * 但不阻断业务（可靠性优先，失败有告警可追溯）。traceId 自动取自 MDC，
 * client_ip 自动取自请求上下文（forward-headers 还原后）。
 */
@Component
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogMapper auditLogMapper;

    /**
     * 注入审计 Mapper。
     *
     * @param auditLogMapper 审计日志 Mapper
     */
    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 记录一条审计事件（独立事务，失败不阻断业务）。
     *
     * @param entry 审计事件
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        try {
            auditLogMapper.insert(entry.userId(), entry.action().name(),
                    entry.targetType(), entry.targetId(), entry.detail(),
                    resolveClientIp(), TraceIds.current());
        } catch (RuntimeException e) {
            LOG.error("AUDIT_WRITE_FAILED action={} user={} targetType={} targetId={}",
                    entry.action(), entry.userId(), entry.targetType(), entry.targetId(), e);
        }
    }

    private String resolveClientIp() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
