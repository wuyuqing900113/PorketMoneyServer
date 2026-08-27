package wyq.pocket.money.ai.eval;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import wyq.pocket.money.ai.domain.AiIntent;
import wyq.pocket.money.ai.service.IntentCatalog;
import wyq.pocket.money.common.ai.ChatPort;
import wyq.pocket.money.common.ai.IntentResult;
import wyq.pocket.money.common.ai.ToolDefinition;

/**
 * AI 意图准确率评测器（M4 设计 §11.3）：provider 无关。
 *
 * <p>注入任一 {@link ChatPort} 跑 golden 评测集，统计意图准确率与参数
 * 准确率。确定性 {@code StubChatPort} 下封闭意图集准确率为 100%（评测
 * 框架基线）；真实 LLM ≥95% 达标待提供商接入后复跑（R2 口径明示）。
 */
@Component
public class AiAccuracyEvaluator {

    private final ChatPort chatPort;

    private final List<ToolDefinition> toolDefinitions;

    private final List<EvalCase> dataset;

    /**
     * 注入对话端口与意图目录。
     *
     * @param chatPort      对话端口（评测对象）
     * @param intentCatalog 意图目录（工具定义清单）
     */
    public AiAccuracyEvaluator(ChatPort chatPort, IntentCatalog intentCatalog) {
        this.chatPort = chatPort;
        this.toolDefinitions = intentCatalog.toolDefinitions();
        this.dataset = buildDataset();
    }

    /**
     * 跑全量评测集并返回报告。
     *
     * @return 评测报告
     */
    public EvalReport evaluate() {
        int intentCorrect = 0;
        int paramCorrect = 0;
        for (EvalCase testCase : dataset) {
            IntentResult result = chatPort.parseIntent(testCase.text(), toolDefinitions);
            if (testCase.expectedIntent().name().equals(result.toolName())) {
                intentCorrect++;
            }
            if (testCase.expectedParams().equals(result.rawParams())) {
                paramCorrect++;
            }
        }
        return new EvalReport(dataset.size(), intentCorrect, paramCorrect);
    }

    private List<EvalCase> buildDataset() {
        return List.of(
                query("查一下余额", AiIntent.BALANCE_QUERY),
                query("我还有多少钱", AiIntent.BALANCE_QUERY),
                query("看看最近流水", AiIntent.TRANSACTION_QUERY),
                query("本月交易明细", AiIntent.TRANSACTION_QUERY),
                query("家庭看板", AiIntent.DASHBOARD),
                query("看下数据面板", AiIntent.DASHBOARD),
                query("这个月趋势", AiIntent.TREND),
                query("收支走势", AiIntent.TREND),
                query("本周排行榜", AiIntent.LEADERBOARD),
                query("收入排名", AiIntent.LEADERBOARD),
                query("有哪些包月规则", AiIntent.RULE_QUERY),
                query("查看规则", AiIntent.RULE_QUERY),
                query("学习任务列表", AiIntent.TASK_QUERY),
                query("看看任务", AiIntent.TASK_QUERY),
                query("工作价值记录", AiIntent.WORK_VALUE_QUERY),
                query("看看工资", AiIntent.WORK_VALUE_QUERY),
                query("本月统计", AiIntent.STATISTICS_QUERY),
                query("收支汇总", AiIntent.STATISTICS_QUERY),
                fundWrite("给小明存50", AiIntent.DEPOSIT, "50", "小明"),
                fundWrite("给小明存入100", AiIntent.DEPOSIT, "100", "小明"),
                fundWrite("给小明取20", AiIntent.WITHDRAW, "20", "小明"),
                fundWrite("给小明提取30", AiIntent.WITHDRAW, "30", "小明"));
    }

    private EvalCase query(String text, AiIntent intent) {
        return new EvalCase(text, intent, Map.of());
    }

    private EvalCase fundWrite(String text, AiIntent intent, String amount, String targetName) {
        return new EvalCase(text, intent, Map.of("amount", amount, "targetUserName", targetName));
    }
}
