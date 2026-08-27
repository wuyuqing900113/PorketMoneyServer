package wyq.pocket.money.money.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 学习任务（M2 设计 §10）：家长定义 → 孩子提交 → 家长确认发放奖励。
 */
public class LearningTask {

    private Long id;

    private Long familyId;

    private Long assigneeUserId;

    private Long createdBy;

    private String title;

    private BigDecimal rewardAmount;

    private LocalDate deadline;

    private LearningTaskStatus status;

    private String submitNote;

    private Instant submittedAt;

    private String rejectReason;

    private Long reviewedBy;

    private Instant reviewedAt;

    private Long transactionId;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 获取任务 ID。
     *
     * @return 任务 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置任务 ID。
     *
     * @param id 任务 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取家庭 ID。
     *
     * @return 家庭 ID
     */
    public Long getFamilyId() {
        return familyId;
    }

    /**
     * 设置家庭 ID。
     *
     * @param familyId 家庭 ID
     */
    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    /**
     * 获取执行人（孩子）用户 ID。
     *
     * @return 执行人（孩子）用户 ID
     */
    public Long getAssigneeUserId() {
        return assigneeUserId;
    }

    /**
     * 设置执行人用户 ID。
     *
     * @param assigneeUserId 执行人（孩子）用户 ID
     */
    public void setAssigneeUserId(Long assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    /**
     * 获取创建人（家长）用户 ID。
     *
     * @return 创建人（家长）用户 ID
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建人用户 ID。
     *
     * @param createdBy 创建人（家长）用户 ID
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 获取任务标题。
     *
     * @return 任务标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置任务标题。
     *
     * @param title 任务标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取奖励金额。
     *
     * @return 奖励金额
     */
    public BigDecimal getRewardAmount() {
        return rewardAmount;
    }

    /**
     * 设置奖励金额。
     *
     * @param rewardAmount 奖励金额
     */
    public void setRewardAmount(BigDecimal rewardAmount) {
        this.rewardAmount = rewardAmount;
    }

    /**
     * 获取截止日期（可选）。
     *
     * @return 截止日期（可选）
     */
    public LocalDate getDeadline() {
        return deadline;
    }

    /**
     * 设置截止日期。
     *
     * @param deadline 截止日期（可选）
     */
    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    /**
     * 获取任务状态。
     *
     * @return 任务状态
     */
    public LearningTaskStatus getStatus() {
        return status;
    }

    /**
     * 设置任务状态。
     *
     * @param status 任务状态
     */
    public void setStatus(LearningTaskStatus status) {
        this.status = status;
    }

    /**
     * 获取提交说明。
     *
     * @return 提交说明
     */
    public String getSubmitNote() {
        return submitNote;
    }

    /**
     * 设置提交说明。
     *
     * @param submitNote 提交说明
     */
    public void setSubmitNote(String submitNote) {
        this.submitNote = submitNote;
    }

    /**
     * 获取提交时间。
     *
     * @return 提交时间
     */
    public Instant getSubmittedAt() {
        return submittedAt;
    }

    /**
     * 设置提交时间。
     *
     * @param submittedAt 提交时间
     */
    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    /**
     * 获取驳回原因。
     *
     * @return 驳回原因
     */
    public String getRejectReason() {
        return rejectReason;
    }

    /**
     * 设置驳回原因。
     *
     * @param rejectReason 驳回原因
     */
    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    /**
     * 获取审核人用户 ID。
     *
     * @return 审核人用户 ID
     */
    public Long getReviewedBy() {
        return reviewedBy;
    }

    /**
     * 设置审核人用户 ID。
     *
     * @param reviewedBy 审核人用户 ID
     */
    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    /**
     * 获取审核时间。
     *
     * @return 审核时间
     */
    public Instant getReviewedAt() {
        return reviewedAt;
    }

    /**
     * 设置审核时间。
     *
     * @param reviewedAt 审核时间
     */
    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    /**
     * 获取发放奖励流水 ID。
     *
     * @return 发放奖励流水 ID
     */
    public Long getTransactionId() {
        return transactionId;
    }

    /**
     * 设置发放奖励流水 ID。
     *
     * @param transactionId 发放奖励流水 ID
     */
    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
