package wyq.pocket.money.money.domain;

/**
 * 学习任务状态（M2 设计 §10.1 状态机）。
 *
 * <p>PENDING → SUBMITTED → APPROVED / REJECTED（驳回可重提回 SUBMITTED），
 * 发放前（PENDING / SUBMITTED）可取消 → CANCELED。
 */
public enum LearningTaskStatus {

    /** 已创建待提交。 */
    PENDING,

    /** 孩子已提交，待家长确认。 */
    SUBMITTED,

    /** 家长确认，奖励已发放。 */
    APPROVED,

    /** 家长驳回，可重新提交。 */
    REJECTED,

    /** 发放前取消。 */
    CANCELED
}
