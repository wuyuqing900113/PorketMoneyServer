package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.ai.domain.AiIntent;
import wyq.pocket.money.finance.dto.StatisticsSummaryResponse;
import wyq.pocket.money.money.dto.DashboardResponse;
import wyq.pocket.money.money.dto.LearningTaskPageResponse;
import wyq.pocket.money.money.dto.LeaderboardResponse;
import wyq.pocket.money.money.dto.TransactionPageResponse;
import wyq.pocket.money.money.dto.TrendResponse;

/**
 * 查询回复组装器单元测试（M4 设计 §5.3/§7.1）：确定性模板从工具返回的
 * 真实数据抽取字段拼装回复，未注册意图回落「已完成」。
 */
class AiReplyComposerTest {

    private final AiReplyComposer composer = new AiReplyComposer();

    @Test
    void shouldComposeAllQueryTemplates() {
        assertThat(composer.compose(AiIntent.BALANCE_QUERY, new BigDecimal("520.00")))
                .isEqualTo("家庭总余额 520.00 元");
        assertThat(composer.compose(AiIntent.TRANSACTION_QUERY,
                new TransactionPageResponse(List.of(), 5L, 1, 20)))
                .isEqualTo("共 5 条流水记录");
        assertThat(composer.compose(AiIntent.DASHBOARD,
                new DashboardResponse(new BigDecimal("520.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, List.of())))
                .isEqualTo("家庭总余额 520.00 元");
        assertThat(composer.compose(AiIntent.TREND,
                new TrendResponse("WEEK", "FAMILY", null, List.of())))
                .isEqualTo("已生成 0 个数据点的收支趋势");
        assertThat(composer.compose(AiIntent.LEADERBOARD,
                new LeaderboardResponse(LocalDate.of(2026, 8, 24), List.of())))
                .isEqualTo("本周收入榜已生成，共 0 名成员");
        assertThat(composer.compose(AiIntent.RULE_QUERY, List.of(1, 2, 3)))
                .isEqualTo("共 3 条包月规则");
        assertThat(composer.compose(AiIntent.TASK_QUERY,
                new LearningTaskPageResponse(List.of(), 7L, 1, 20)))
                .isEqualTo("共 7 条学习任务");
        assertThat(composer.compose(AiIntent.WORK_VALUE_QUERY, List.of(1)))
                .isEqualTo("共 1 条工作价值记录");
        assertThat(composer.compose(AiIntent.STATISTICS_QUERY,
                new StatisticsSummaryResponse(new BigDecimal("520.00"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0)))
                .isEqualTo("家庭总余额 520.00 元");
    }

    @Test
    void shouldFallbackToDoneForUnregisteredIntent() {
        assertThat(composer.compose(AiIntent.DEPOSIT, new BigDecimal("10")))
                .isEqualTo("已完成");
    }
}
