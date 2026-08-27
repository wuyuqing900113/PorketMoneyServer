package wyq.pocket.money.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.ai.service.IntentCatalog;
import wyq.pocket.money.common.ai.StubChatPort;

/**
 * AI 意图准确率评测器测试（M4 设计 §11.3）：确定性桩下封闭意图集
 * 意图与参数准确率均为 100%（评测框架基线）。
 */
class AiAccuracyEvaluatorTest {

    @Test
    void shouldScoreFullAccuracyUnderStub() {
        AiAccuracyEvaluator evaluator = new AiAccuracyEvaluator(
                new StubChatPort(false), new IntentCatalog());

        EvalReport report = evaluator.evaluate();

        assertThat(report.total()).isEqualTo(22);
        assertThat(report.intentCorrect()).isEqualTo(22);
        assertThat(report.paramCorrect()).isEqualTo(22);
        assertThat(report.intentAccuracy()).isEqualTo(1.0);
        assertThat(report.paramAccuracy()).isEqualTo(1.0);
        assertThat(report.passed(0.95)).isTrue();
    }
}
