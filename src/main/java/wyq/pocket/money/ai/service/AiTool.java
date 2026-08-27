package wyq.pocket.money.ai.service;

import java.util.Map;

import wyq.pocket.money.common.security.UserIdPrincipal;

/**
 * AI 工具（函数式接口，M4 设计 §5.3）：一个意图对应一个工具。
 *
 * <p>工具以会话绑定的 {@link UserIdPrincipal} 为身份执行，不接受模型
 * 提供的任意 userId；目标成员一律经 {@code FamilyAccessChecker} 校验归属
 * 本家庭。参数 {@code params} 已由编排器完成业务解析（成员名→userId）。
 */
@FunctionalInterface
public interface AiTool {

    /**
     * 执行工具，返回业务数据（DTO 或原始值），由编排器组装自然语言回复。
     *
     * @param principal 当前登录主体
     * @param params    已校验、已业务解析的参数（键值字符串）
     * @return 业务结果
     */
    Object execute(UserIdPrincipal principal, Map<String, String> params);
}
