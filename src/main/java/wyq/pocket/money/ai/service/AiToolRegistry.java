package wyq.pocket.money.ai.service;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

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
 * 查询意图工具注册表（M4 设计 §5.3）：意图 → 工具执行既有 service 层。
 *
 * <p>仅注册 9 类查询工具；DEPOSIT / WITHDRAW 两类资金写不走本注册表，
 * 由编排器解析为待确认动作参数快照转二次确认流程（§6）。查询工具以会话
 * 绑定的 {@link UserIdPrincipal} 为身份执行，不接受模型提供的任意 userId。
 */
@Component
public class AiToolRegistry {

    /** 意图 → 查询工具映射（仅查询类，资金写归编排器）。 */
    private final Map<AiIntent, AiTool> tools;

    /**
     * 注入各查询门面 service 并注册查询工具。
     *
     * @param moneyQueryService   流水/余额查询门面
     * @param dashboardService    看板服务
     * @param trendService        趋势服务
     * @param leaderboardService  榜单服务
     * @param ruleService         规则服务
     * @param learningTaskService 学习任务服务
     * @param workValueService    工作价值服务
     * @param reportService       财务报表服务
     */
    public AiToolRegistry(MoneyQueryService moneyQueryService,
                          DashboardService dashboardService,
                          TrendService trendService,
                          LeaderboardService leaderboardService,
                          RuleService ruleService,
                          LearningTaskService learningTaskService,
                          WorkValueService workValueService,
                          ReportService reportService) {
        Map<AiIntent, AiTool> registry = new EnumMap<>(AiIntent.class);
        registry.put(AiIntent.BALANCE_QUERY, (principal, params) ->
                moneyQueryService.totalBalance(principal.familyId()));
        registry.put(AiIntent.TRANSACTION_QUERY, (principal, params) ->
                moneyQueryService.page(principal, longParam(params, "userId"),
                        null, null, null, null,
                        intParam(params, "page", 1), intParam(params, "size", 20)));
        registry.put(AiIntent.DASHBOARD, (principal, params) ->
                dashboardService.getDashboard(principal));
        registry.put(AiIntent.TREND, (principal, params) ->
                trendService.trend(principal, strParam(params, "scope"),
                        longParam(params, "userId"), strParam(params, "granularity")));
        registry.put(AiIntent.LEADERBOARD, (principal, params) ->
                leaderboardService.leaderboard(principal));
        registry.put(AiIntent.RULE_QUERY, (principal, params) ->
                ruleService.list(principal));
        registry.put(AiIntent.TASK_QUERY, (principal, params) ->
                learningTaskService.list(principal, strParam(params, "status"),
                        longParam(params, "assigneeUserId"),
                        intParam(params, "page", 1), intParam(params, "size", 20)));
        registry.put(AiIntent.WORK_VALUE_QUERY, (principal, params) ->
                workValueService.list(principal, strParam(params, "workMonth")));
        registry.put(AiIntent.STATISTICS_QUERY, (principal, params) ->
                reportService.statistics(principal));
        this.tools = registry;
    }

    /**
     * 执行查询工具。
     *
     * @param intent    查询意图
     * @param principal 当前登录主体
     * @param params    已校验参数
     * @return 业务结果
     * @throws BusinessException 600002 意图未注册（资金写或未知意图）
     */
    public Object execute(AiIntent intent, UserIdPrincipal principal, Map<String, String> params) {
        AiTool tool = tools.get(intent);
        if (tool == null) {
            throw new BusinessException(AiErrorCode.INTENT_UNRECOGNIZED);
        }
        return tool.execute(principal, params);
    }

    private static String strParam(Map<String, String> params, String key) {
        return params.get(key);
    }

    private static Long longParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value.trim());
    }

    private static int intParam(Map<String, String> params, String key, int defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }
}
