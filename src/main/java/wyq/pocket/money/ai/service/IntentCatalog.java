package wyq.pocket.money.ai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import wyq.pocket.money.ai.domain.AiIntent;
import wyq.pocket.money.ai.dto.AiErrorCode;
import wyq.pocket.money.common.ai.ToolDefinition;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.web.CommonErrorCode;

/**
 * 封闭意图目录（M4 设计 §5.1/§5.2）：工具定义清单 + 意图映射 + 参数校验。
 *
 * <p>每个意图对应一条 {@link ToolDefinition}（名称/描述/参数 schema），
 * 供 {@code ChatPort} 在解析时作为工具候选；本目录亦承担「模型不得直接
 * 触碰 mapper」的兜底——所有参数须通过本地校验后方可进入 service 层。
 */
@Component
public class IntentCatalog {

    private static final String PARAM_AMOUNT = "amount";

    private static final String PARAM_TARGET_USER_NAME = "targetUserName";

    private static final String PARAM_TARGET_USER_ID = "targetUserId";

    /** 意图→工具定义清单（启动期构建，构造后不可变）。 */
    private final List<ToolDefinition> definitions = List.copyOf(buildDefinitions());

    /**
     * 返回全部工具定义（意图目录），供 NLU 解析时作为候选工具。
     *
     * @return 工具定义列表（11 项）
     */
    public List<ToolDefinition> toolDefinitions() {
        return definitions;
    }

    /**
     * 将工具名映射为意图枚举。
     *
     * @param toolName 工具名（意图码）
     * @return 意图枚举
     * @throws BusinessException 600002 工具名为空或未识别
     */
    public AiIntent requireIntent(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new BusinessException(AiErrorCode.INTENT_UNRECOGNIZED);
        }
        return parseIntent(toolName);
    }

    private AiIntent parseIntent(String toolName) {
        try {
            return AiIntent.valueOf(toolName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(AiErrorCode.INTENT_UNRECOGNIZED);
        }
    }

    /**
     * 校验意图参数（M4 设计 §5.2）：查询类参数均可选，资金写须金额为正数
     * 且提供目标成员。
     *
     * @param intent    意图
     * @param rawParams 原始参数
     * @throws BusinessException 100001 金额缺失/非法/非正 或 目标成员缺失
     */
    public void validate(AiIntent intent, Map<String, String> rawParams) {
        if (!intent.requiresConfirmation()) {
            return;
        }
        requirePositiveAmount(rawParams);
        requireTarget(rawParams);
    }

    private void requirePositiveAmount(Map<String, String> rawParams) {
        BigDecimal amount = parseAmount(rawParams.get(PARAM_AMOUNT));
        if (amount == null) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "金额非法");
        }
        if (amount.signum() <= 0) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "金额必须大于0");
        }
    }

    private void requireTarget(Map<String, String> rawParams) {
        String targetUserName = rawParams.get(PARAM_TARGET_USER_NAME);
        String targetUserId = rawParams.get(PARAM_TARGET_USER_ID);
        boolean hasName = targetUserName != null && !targetUserName.isBlank();
        boolean hasId = targetUserId != null && !targetUserId.isBlank();
        if (!hasName && !hasId) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "缺少目标成员");
        }
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<ToolDefinition> buildDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>();
        defs.add(tool(AiIntent.BALANCE_QUERY, "查询家庭总余额", Map.of()));
        defs.add(tool(AiIntent.TRANSACTION_QUERY, "查询流水明细",
                Map.of("userId", "int", "page", "int", "size", "int")));
        defs.add(tool(AiIntent.DASHBOARD, "查询家庭看板", Map.of()));
        defs.add(tool(AiIntent.TREND, "查询收支趋势",
                Map.of("scope", "string", "userId", "int", "granularity", "string")));
        defs.add(tool(AiIntent.LEADERBOARD, "查询本周收入榜", Map.of()));
        defs.add(tool(AiIntent.RULE_QUERY, "查询包月规则列表", Map.of()));
        defs.add(tool(AiIntent.TASK_QUERY, "查询学习任务列表",
                Map.of("status", "string", "assigneeUserId", "int", "page", "int", "size", "int")));
        defs.add(tool(AiIntent.WORK_VALUE_QUERY, "查询工作价值记录",
                Map.of("workMonth", "string")));
        defs.add(tool(AiIntent.STATISTICS_QUERY, "查询统计摘要", Map.of()));
        defs.add(tool(AiIntent.DEPOSIT, "存入资金",
                Map.of("targetUserName", "string", "amount", "decimal", "remark", "string")));
        defs.add(tool(AiIntent.WITHDRAW, "提取资金",
                Map.of("targetUserName", "string", "amount", "decimal", "remark", "string")));
        return defs;
    }

    private ToolDefinition tool(AiIntent intent, String description, Map<String, String> params) {
        return new ToolDefinition(intent.name(), description, params);
    }
}
