package wyq.pocket.money.money.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.money.service.ReconciliationService;

/**
 * 对账定时任务委托测试（M2 设计 §4.4 / §13）。
 */
class ReconciliationJobTest {

    @Test
    void jobShouldDelegateToReconciliationService() {
        ReconciliationService reconciliationService = mock(ReconciliationService.class);

        new ReconciliationJob(reconciliationService).run();

        verify(reconciliationService).reconcile();
    }
}
