package wyq.pocket.money.common.ai;

import java.util.Map;

/**
 * 工具定义（M4 设计 §4.2）：意图目录中每个意图对应一条工具定义。
 *
 * @param name        工具名（即意图码，如 BALANCE_QUERY）
 * @param description 工具用途描述（供 LLM 选择与解析）
 * @param params      参数 schema：参数名 → 参数类型（string / decimal / int）
 */
public record ToolDefinition(String name, String description, Map<String, String> params) {

    /**
     * 紧凑构造器：参数 schema 映射做不可变快照，杜绝内外双向的可变共享。
     */
    public ToolDefinition {
        params = Map.copyOf(params);
    }
}
