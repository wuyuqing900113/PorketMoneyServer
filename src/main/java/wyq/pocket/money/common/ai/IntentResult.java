package wyq.pocket.money.common.ai;

import java.util.Map;

/**
 * 意图解析结果（M4 设计 §4.2）。
 *
 * @param toolName   解析出的工具名（意图码），未识别为 null
 * @param rawParams  原始参数（键值字符串，未做业务解析）
 * @param confidence 置信度（0.0–1.0，未识别为 0）
 */
public record IntentResult(String toolName, Map<String, String> rawParams, double confidence) {

    /**
     * 紧凑构造器：原始参数映射做不可变快照，杜绝内外双向的可变共享。
     */
    public IntentResult {
        rawParams = Map.copyOf(rawParams);
    }
}
