package wyq.pocket.money.ai.service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import wyq.pocket.money.ai.domain.AiIntent;
import wyq.pocket.money.finance.dto.StatisticsSummaryResponse;
import wyq.pocket.money.money.dto.DashboardResponse;
import wyq.pocket.money.money.dto.LearningTaskPageResponse;
import wyq.pocket.money.money.dto.LeaderboardResponse;
import wyq.pocket.money.money.dto.TransactionPageResponse;
import wyq.pocket.money.money.dto.TrendResponse;

/**
 * 查询结果自然语言回复组装器（M4 设计 §5.3/§7.1）。
 *
 * <p>确定性模板从工具返回的真实数据（DTO）抽取字段拼装回复，不产生自由
 * 数值（D32）；资金写意图的确认话术由 {@code PendingActionService} 单独组装。
 */
@Component
public class AiReplyComposer {

    /** 意图 → 回复模板映射（启动期构建，只读）。 */
    private final Map<AiIntent, Function<Object, String>> templates;

    /**
     * 构建确定性回复模板映射。
     */
    public AiReplyComposer() {
        this.templates = buildTemplates();
    }

    /**
     * 组装自然语言回复。
     *
     * @param intent 意图
     * @param data   工具返回的真实数据
     * @return 回复文本
     */
    public String compose(AiIntent intent, Object data) {
        Function<Object, String> template = templates.get(intent);
        if (template == null) {
            return "已完成";
        }
        return template.apply(data);
    }

    private Map<AiIntent, Function<Object, String>> buildTemplates() {
        Map<AiIntent, Function<Object, String>> registry = new EnumMap<>(AiIntent.class);
        registry.put(AiIntent.BALANCE_QUERY,
                data -> "家庭总余额 " + ((BigDecimal) data).toPlainString() + " 元");
        registry.put(AiIntent.TRANSACTION_QUERY,
                data -> "共 " + ((TransactionPageResponse) data).total() + " 条流水记录");
        registry.put(AiIntent.DASHBOARD,
                data -> "家庭总余额 " + ((DashboardResponse) data).totalBalance().toPlainString()
                        + " 元");
        registry.put(AiIntent.TREND,
                data -> "已生成 " + ((TrendResponse) data).series().size() + " 个数据点的收支趋势");
        registry.put(AiIntent.LEADERBOARD,
                data -> "本周收入榜已生成，共 " + ((LeaderboardResponse) data).entries().size()
                        + " 名成员");
        registry.put(AiIntent.RULE_QUERY, data -> "共 " + ((List<?>) data).size() + " 条包月规则");
        registry.put(AiIntent.TASK_QUERY,
                data -> "共 " + ((LearningTaskPageResponse) data).total() + " 条学习任务");
        registry.put(AiIntent.WORK_VALUE_QUERY,
                data -> "共 " + ((List<?>) data).size() + " 条工作价值记录");
        registry.put(AiIntent.STATISTICS_QUERY,
                data -> "家庭总余额 "
                        + ((StatisticsSummaryResponse) data).totalBalance().toPlainString() + " 元");
        return registry;
    }
}
