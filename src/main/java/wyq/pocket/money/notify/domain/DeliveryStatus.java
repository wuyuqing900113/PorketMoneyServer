package wyq.pocket.money.notify.domain;

/**
 * 外部通道投递状态（notification_delivery.status，M5 设计 §7.2）。
 *
 * <p>状态机：PENDING → SENT（成功）；PENDING → 退避重试保持 PENDING；
 * 重试耗尽 → DEAD（死信）。FAILED 为预留观测态，v1 投递引擎的重试
 * 中态保持在 PENDING（retry_count + next_retry_at 递增）。
 */
public enum DeliveryStatus {

    /** 待投递（含退避重试中）。 */
    PENDING,

    /** 已投递成功。 */
    SENT,

    /** 投递失败（预留观测态）。 */
    FAILED,

    /** 死信（重试耗尽，人工排查）。 */
    DEAD
}
