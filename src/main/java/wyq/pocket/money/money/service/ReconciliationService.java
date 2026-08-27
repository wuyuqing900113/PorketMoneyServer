package wyq.pocket.money.money.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;

/**
 * 对账服务（M2 设计 §4.4）：校验账户余额快照与流水台账一致性。
 *
 * <p>校验项：① 最新流水 balance_after ≠ 账户余额；② 无流水但余额非 0。
 * 发现不一致：SECURITY ERROR 级日志告警 + RECONCILE_MISMATCH 审计，
 * 仅报告不自动修正（资金安全优先，人工介入）。
 */
@Component
public class ReconciliationService {

    /** 安全告警日志通道（与 SECURITY 日志规范一致）。 */
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    private final MoneyAccountMapper accountMapper;

    private final AuditService auditService;

    /**
     * 注入协作对象。
     *
     * @param accountMapper 账户 Mapper
     * @param auditService  审计服务
     */
    public ReconciliationService(MoneyAccountMapper accountMapper, AuditService auditService) {
        this.accountMapper = accountMapper;
        this.auditService = auditService;
    }

    /**
     * 执行一轮全量对账。
     */
    public void reconcile() {
        List<Long> mismatched = accountMapper.findMismatchedAccountIds();
        List<Long> orphan = accountMapper.findOrphanBalanceAccountIds();
        if (mismatched.isEmpty() && orphan.isEmpty()) {
            return;
        }
        SECURITY_LOG.error("RECONCILE_MISMATCH mismatchedAccounts={} orphanAccounts={}",
                mismatched, orphan);
        auditService.record(new AuditEntry(null, AuditAction.RECONCILE_MISMATCH,
                "MONEY_ACCOUNT", String.valueOf(mismatched.size() + orphan.size()),
                "mismatched=" + mismatched + ";orphan=" + orphan));
    }
}
