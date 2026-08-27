package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.ai.domain.AiIntent;
import wyq.pocket.money.ai.dto.AiErrorCode;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.finance.service.ReportService;
import wyq.pocket.money.money.service.DashboardService;
import wyq.pocket.money.money.service.LeaderboardService;
import wyq.pocket.money.money.service.LearningTaskService;
import wyq.pocket.money.money.service.MoneyQueryService;
import wyq.pocket.money.money.service.TrendService;
import wyq.pocket.money.money.service.WorkValueService;
import wyq.pocket.money.rule.service.RuleService;

/**
 * 查询工具注册表单元测试（M4 设计 §5.3）：意图 → 既有 service 门面分发、
 * 参数解析（userId/page/size）与未注册意图拒绝。
 */
class AiToolRegistryTest {

    private static final UserIdPrincipal PARENT = new UserIdPrincipal(1L, 10L, "PARENT", false);

    private final MoneyQueryService moneyQueryService = mock(MoneyQueryService.class);

    private final DashboardService dashboardService = mock(DashboardService.class);

    private final TrendService trendService = mock(TrendService.class);

    private final LeaderboardService leaderboardService = mock(LeaderboardService.class);

    private final RuleService ruleService = mock(RuleService.class);

    private final LearningTaskService learningTaskService = mock(LearningTaskService.class);

    private final WorkValueService workValueService = mock(WorkValueService.class);

    private final ReportService reportService = mock(ReportService.class);

    private final AiToolRegistry registry = new AiToolRegistry(moneyQueryService, dashboardService,
            trendService, leaderboardService, ruleService, learningTaskService, workValueService,
            reportService);

    @Test
    void shouldDispatchToRightServiceForEachQueryIntent() {
        registry.execute(AiIntent.BALANCE_QUERY, PARENT, Map.of());
        registry.execute(AiIntent.TRANSACTION_QUERY, PARENT, Map.of());
        registry.execute(AiIntent.DASHBOARD, PARENT, Map.of());
        registry.execute(AiIntent.TREND, PARENT, Map.of());
        registry.execute(AiIntent.LEADERBOARD, PARENT, Map.of());
        registry.execute(AiIntent.RULE_QUERY, PARENT, Map.of());
        registry.execute(AiIntent.TASK_QUERY, PARENT, Map.of());
        registry.execute(AiIntent.WORK_VALUE_QUERY, PARENT, Map.of());
        registry.execute(AiIntent.STATISTICS_QUERY, PARENT, Map.of());

        verify(moneyQueryService).totalBalance(10L);
        verify(moneyQueryService).page(any(), any(), any(), any(), any(), any(), anyInt(),
                anyInt());
        verify(dashboardService).getDashboard(any());
        verify(trendService).trend(any(), any(), any(), any());
        verify(leaderboardService).leaderboard(any());
        verify(ruleService).list(any());
        verify(learningTaskService).list(any(), any(), any(), anyInt(), anyInt());
        verify(workValueService).list(any(), any());
        verify(reportService).statistics(any());
    }

    @Test
    void shouldParseUserIdAndPageParams() {
        registry.execute(AiIntent.TRANSACTION_QUERY, PARENT,
                Map.of("userId", "5", "page", "2", "size", "10"));

        verify(moneyQueryService).page(any(), eq(5L), any(), any(), any(), any(), eq(2), eq(10));
    }

    @Test
    void shouldRejectUnregisteredIntent() {
        assertThatThrownBy(() -> registry.execute(AiIntent.DEPOSIT, PARENT, Map.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(AiErrorCode.INTENT_UNRECOGNIZED));
    }
}
