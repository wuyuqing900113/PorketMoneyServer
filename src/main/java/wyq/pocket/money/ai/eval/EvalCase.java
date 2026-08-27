package wyq.pocket.money.ai.eval;

import java.util.Map;

import wyq.pocket.money.ai.domain.AiIntent;

/**
 * 评测样本（M4 设计 §11.3）：一条自然语言指令 + 期望意图 + 期望参数。
 *
 * @param text           自然语言指令
 * @param expectedIntent 期望意图码
 * @param expectedParams 期望原始参数（键值字符串）
 */
public record EvalCase(String text, AiIntent expectedIntent, Map<String, String> expectedParams) {

    /**
     * 紧凑构造器：期望参数映射做不可变快照，杜绝内外双向的可变共享。
     */
    public EvalCase {
        expectedParams = Map.copyOf(expectedParams);
    }
}
