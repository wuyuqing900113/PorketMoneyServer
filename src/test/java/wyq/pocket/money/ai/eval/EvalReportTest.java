package wyq.pocket.money.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 评测报告换算测试（M4 设计 §11.3）：准确率换算、阈值判断与空集兜底。
 */
class EvalReportTest {

    @Test
    void shouldBeZeroAccuracyForEmptyDataset() {
        EvalReport report = new EvalReport(0, 0, 0);

        assertThat(report.intentAccuracy()).isEqualTo(0.0);
        assertThat(report.paramAccuracy()).isEqualTo(0.0);
        assertThat(report.passed(0.0)).isTrue();
    }

    @Test
    void shouldComputeAccuracyRatios() {
        EvalReport report = new EvalReport(10, 8, 6);

        assertThat(report.intentAccuracy()).isEqualTo(0.8);
        assertThat(report.paramAccuracy()).isEqualTo(0.6);
    }

    @Test
    void shouldRequireBothAccuraciesToPass() {
        assertThat(new EvalReport(10, 10, 10).passed(0.9)).isTrue();
        assertThat(new EvalReport(10, 5, 10).passed(0.9)).isFalse();
        assertThat(new EvalReport(10, 10, 5).passed(0.9)).isFalse();
    }
}
