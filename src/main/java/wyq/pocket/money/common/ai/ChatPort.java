package wyq.pocket.money.common.ai;

import java.util.List;

/**
 * 对话 + Function Calling 端口（M4 设计 D27）。
 *
 * <p>实现方：{@link StubChatPort}（默认，确定性意图路由桩，零新依赖）/
 * SpringAiChatPort（生产，提供商拍板后接入）。端口仅产出结构化解析结果，
 * 不依赖任何业务模块，成员名到 userId 的业务解析由 ai 模块编排器完成。
 */
public interface ChatPort {

    /**
     * 解析用户指令为结构化意图。
     *
     * @param userText 用户原始指令文本
     * @param tools    可用工具定义（意图目录）
     * @return 解析结果（工具名 + 原始参数 + 置信度）
     */
    IntentResult parseIntent(String userText, List<ToolDefinition> tools);
}
