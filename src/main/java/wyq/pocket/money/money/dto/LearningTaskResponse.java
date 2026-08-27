package wyq.pocket.money.money.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 学习任务响应。
 *
 * @param id               任务 ID
 * @param familyId         家庭 ID
 * @param assigneeUserId   执行人用户 ID
 * @param assigneeNickname 执行人昵称
 * @param createdBy        创建人用户 ID
 * @param creatorNickname  创建人昵称
 * @param title            任务标题
 * @param rewardAmount     奖励金额
 * @param deadline         截止日期（可空）
 * @param status           状态（PENDING / SUBMITTED / APPROVED / REJECTED / CANCELED）
 * @param submitNote       提交说明
 * @param submittedAt      提交时间
 * @param rejectReason     驳回原因
 * @param reviewedBy       审核人用户 ID
 * @param reviewedAt       审核时间
 * @param transactionId    发放奖励流水 ID
 * @param createdAt        创建时间
 * @param overdue          是否已过期未完成（截止日早于今天且未发放 / 未取消）
 */
public record LearningTaskResponse(Long id, Long familyId, Long assigneeUserId,
                                   String assigneeNickname, Long createdBy,
                                   String creatorNickname, String title,
                                   BigDecimal rewardAmount, LocalDate deadline, String status,
                                   String submitNote, Instant submittedAt, String rejectReason,
                                   Long reviewedBy, Instant reviewedAt, Long transactionId,
                                   Instant createdAt, boolean overdue) {
}
