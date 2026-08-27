package wyq.pocket.money.money.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;

/**
 * 对账服务单元测试（M2 设计 §4.4）：一致不告警；不一致记 RECONCILE_MISMATCH 审计
 * （仅报告不修正），userId 为 null（系统事件）。
 */
class ReconciliationServiceTest {

    private final MoneyAccountMapper accountMapper = mock(MoneyAccountMapper.class);

    private final AuditService auditService = mock(AuditService.class);

    private final ReconciliationService service =
            new ReconciliationService(accountMapper, auditService);

    @Test
    void reconcileShouldStaySilentWhenConsistent() {
        when(accountMapper.findMismatchedAccountIds()).thenReturn(List.of());
        when(accountMapper.findOrphanBalanceAccountIds()).thenReturn(List.of());

        service.reconcile();

        verify(auditService, never()).record(any(AuditEntry.class));
    }

    @Test
    void reconcileShouldAuditWhenMismatchFound() {
        when(accountMapper.findMismatchedAccountIds()).thenReturn(List.of(1L));
        when(accountMapper.findOrphanBalanceAccountIds()).thenReturn(List.of(2L, 3L));

        service.reconcile();

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        AuditEntry entry = captor.getValue();
        assertThat(entry.userId()).isNull();
        assertThat(entry.action()).isEqualTo(AuditAction.RECONCILE_MISMATCH);
        assertThat(entry.targetType()).isEqualTo("MONEY_ACCOUNT");
        assertThat(entry.targetId()).isEqualTo("3");
        assertThat(entry.detail()).contains("mismatched=[1]").contains("orphan=[2, 3]");
    }

    @Test
    void reconcileShouldAuditWhenOnlyOrphansFound() {
        when(accountMapper.findMismatchedAccountIds()).thenReturn(List.of());
        when(accountMapper.findOrphanBalanceAccountIds()).thenReturn(List.of(9L));

        service.reconcile();

        verify(auditService).record(any(AuditEntry.class));
    }
}
