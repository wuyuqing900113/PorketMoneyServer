package wyq.pocket.money.ai.eval;

/**
 * 评测报告（M4 设计 §11.3）：样本总数与意图/参数命中数，含准确率换算。
 *
 * @param total         样本总数
 * @param intentCorrect 意图命中数
 * @param paramCorrect  参数命中数
 */
public record EvalReport(int total, int intentCorrect, int paramCorrect) {

    /**
     * 意图准确率（0.0–1.0，空集返回 0）。
     *
     * @return 意图命中数 / 样本总数
     */
    public double intentAccuracy() {
        return total == 0 ? 0.0 : (double) intentCorrect / total;
    }

    /**
     * 参数准确率（0.0–1.0，空集返回 0）。
     *
     * @return 参数命中数 / 样本总数
     */
    public double paramAccuracy() {
        return total == 0 ? 0.0 : (double) paramCorrect / total;
    }

    /**
     * 是否达到准确率阈值（意图与参数均达标）。
     *
     * @param threshold 阈值（0.0–1.0）
     * @return 均达标返回 true
     */
    public boolean passed(double threshold) {
        return intentAccuracy() >= threshold && paramAccuracy() >= threshold;
    }
}
