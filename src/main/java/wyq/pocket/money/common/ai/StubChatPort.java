package wyq.pocket.money.common.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性意图路由桩（M4 设计 §4.3）：默认 {@link ChatPort} 实现。
 *
 * <p>对意图目录做关键词匹配产出结构化意图，金额 / 成员名以正则抽取为
 * 原始参数字符串；无网络、无外部依赖、可复现，支撑单测 / 集成测试 /
 * 评测集（封闭意图集下准确率确定性 100%）。业务解析边界：只产出原始参数，
 * 不解析 userId / 账号（该解析在 ai 编排器经 FamilyService 完成）。
 */
public class StubChatPort implements ChatPort {

    /** 金额正则：整数或两位以内小数（如 50、50.5、50.55）。 */
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)");

    /** 目标成员正则：给「某名」存 / 取（存入 / 提取等组合亦匹配）。 */
    private static final Pattern TARGET =
            Pattern.compile("给([\\u4e00-\\u9fa5\\w]{1,12}?)(?:存|入|取|提)");

    /** 关键词路由表：顺序即优先级（资金写动词优先判定）。 */
    private static final List<Rule> RULES = List.of(
            new Rule("DEPOSIT", List.of("存", "入账")),
            new Rule("WITHDRAW", List.of("取", "提现", "提取", "出账")),
            new Rule("BALANCE_QUERY", List.of("余额", "多少钱", "剩多少")),
            new Rule("TRANSACTION_QUERY", List.of("流水", "花销", "交易", "明细")),
            new Rule("DASHBOARD", List.of("看板", "面板")),
            new Rule("TREND", List.of("趋势", "走势")),
            new Rule("LEADERBOARD", List.of("排行", "榜单", "排名")),
            new Rule("RULE_QUERY", List.of("规则")),
            new Rule("TASK_QUERY", List.of("任务")),
            new Rule("WORK_VALUE_QUERY", List.of("工作价值", "工资")),
            new Rule("STATISTICS_QUERY", List.of("统计", "汇总")));

    /** 降级演练开关：true 时模拟 provider 不可用。 */
    private final boolean fail;

    /**
     * 构造桩。
     *
     * @param fail 是否模拟 provider 不可用（降级演练）
     */
    public StubChatPort(boolean fail) {
        this.fail = fail;
    }

    @Override
    public IntentResult parseIntent(String userText, List<ToolDefinition> tools) {
        if (fail) {
            throw new IllegalStateException("AI stub configured to fail");
        }
        String text = userText == null ? "" : userText;
        String toolName = route(text);
        if (toolName == null) {
            return new IntentResult(null, Map.of(), 0.0);
        }
        return new IntentResult(toolName, extractParams(toolName, text), 1.0);
    }

    private String route(String text) {
        for (Rule rule : RULES) {
            if (rule.matches(text)) {
                return rule.toolName();
            }
        }
        return null;
    }

    private Map<String, String> extractParams(String toolName, String text) {
        Map<String, String> params = new HashMap<>();
        if ("DEPOSIT".equals(toolName) || "WITHDRAW".equals(toolName)) {
            Matcher amount = AMOUNT.matcher(text);
            if (amount.find()) {
                params.put("amount", amount.group(1));
            }
            Matcher target = TARGET.matcher(text);
            if (target.find()) {
                params.put("targetUserName", target.group(1));
            }
        }
        return params;
    }

    /** 单个意图的关键词路由规则。 */
    private record Rule(String toolName, List<String> keywords) {

        boolean matches(String text) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }
}
