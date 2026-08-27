package wyq.pocket.money.ai.domain;

/**
 * 封闭意图目录枚举（M4 设计 §5.1）。
 *
 * <p>9 类查询 + 2 类资金写，共 11 个意图。意图收敛到有限闭集是
 * 「准确率 ≥95%」与「确定性路由」的共同前提；非资金写（规则/任务 CRUD、
 * 工作价值记录）不纳入（D29）。
 */
public enum AiIntent {

    /** 查询家庭总余额。 */
    BALANCE_QUERY,

    /** 查询流水明细。 */
    TRANSACTION_QUERY,

    /** 查询家庭看板。 */
    DASHBOARD,

    /** 查询收支趋势。 */
    TREND,

    /** 查询本周收入榜。 */
    LEADERBOARD,

    /** 查询包月规则列表。 */
    RULE_QUERY,

    /** 查询学习任务列表。 */
    TASK_QUERY,

    /** 查询工作价值记录。 */
    WORK_VALUE_QUERY,

    /** 查询统计摘要。 */
    STATISTICS_QUERY,

    /** 存入资金（资金写，需二次确认）。 */
    DEPOSIT,

    /** 提取资金（资金写，需二次确认）。 */
    WITHDRAW;

    /**
     * 该意图是否资金写（需二次确认）。
     *
     * @return 资金写返回 true，查询返回 false
     */
    public boolean requiresConfirmation() {
        return this == DEPOSIT || this == WITHDRAW;
    }
}
