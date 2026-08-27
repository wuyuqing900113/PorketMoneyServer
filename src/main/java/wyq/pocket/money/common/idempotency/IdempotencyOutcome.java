package wyq.pocket.money.common.idempotency;

/**
 * 幂等受理结果（M3 设计 §5 两阶段语义）。
 *
 * <p>由 {@link IdempotencyService#begin} 返回，过滤器据 {@link #decision()}
 * 决定放行或直接写响应：PROCEED 放行；REPLAY 返回缓存响应；CONFLICT /
 * IN_PROGRESS 分别返回 100009 / 100006。
 *
 * @param decision 受理决策
 * @param record   决策关联的既有记录（仅 REPLAY 时非空）
 */
public record IdempotencyOutcome(Decision decision, IdempotencyRecord record) {

    /**
     * 受理决策枚举。
     */
    public enum Decision {
        /** 放行执行。 */
        PROCEED,
        /** 返回缓存的原始响应。 */
        REPLAY,
        /** 同键不同体冲突（100009）。 */
        CONFLICT,
        /** 前次请求受理中（100006）。 */
        IN_PROGRESS
    }

    /**
     * 构造「放行」结果。
     *
     * @return 放行结果
     */
    public static IdempotencyOutcome proceed() {
        return new IdempotencyOutcome(Decision.PROCEED, null);
    }

    /**
     * 构造「重放」结果。
     *
     * @param record 既有记录（含缓存响应）
     * @return 重放结果
     */
    public static IdempotencyOutcome replay(IdempotencyRecord record) {
        return new IdempotencyOutcome(Decision.REPLAY, record);
    }

    /**
     * 构造「冲突」结果。
     *
     * @return 冲突结果
     */
    public static IdempotencyOutcome conflict() {
        return new IdempotencyOutcome(Decision.CONFLICT, null);
    }

    /**
     * 构造「受理中」结果。
     *
     * @return 受理中结果
     */
    public static IdempotencyOutcome inProgress() {
        return new IdempotencyOutcome(Decision.IN_PROGRESS, null);
    }
}
