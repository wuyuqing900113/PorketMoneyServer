package wyq.pocket.money.money.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 工作价值记录（M2 设计 §9 业务决策 / §11.3）：父母记录每月工资收入，
 * 并将手动填写的发放金额入账本人账户（操作人 = 收款人）。
 */
public class WorkValueRecord {

    private Long id;

    private Long familyId;

    private Long parentUserId;

    private String workMonth;

    private BigDecimal salaryIncome;

    private BigDecimal allowanceAmount;

    private String workSummary;

    private Long transactionId;

    private Long recordedBy;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 获取记录 ID。
     *
     * @return 记录 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置记录 ID。
     *
     * @param id 记录 ID
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
     * 获取收款人（父母本人）用户 ID。
     *
     * @return 收款人（父母本人）用户 ID
     */
    public Long getParentUserId() {
        return parentUserId;
    }

    /**
     * 设置收款人用户 ID。
     *
     * @param parentUserId 收款人（父母本人）用户 ID
     */
    public void setParentUserId(Long parentUserId) {
        this.parentUserId = parentUserId;
    }

    /**
     * 获取工作月份，YYYY-MM。
     *
     * @return 工作月份，YYYY-MM
     */
    public String getWorkMonth() {
        return workMonth;
    }

    /**
     * 设置工作月份。
     *
     * @param workMonth 工作月份，YYYY-MM
     */
    public void setWorkMonth(String workMonth) {
        this.workMonth = workMonth;
    }

    /**
     * 获取当月工资收入（仅记录展示，不参与余额）。
     *
     * @return 当月工资收入（仅记录展示，不参与余额）
     */
    public BigDecimal getSalaryIncome() {
        return salaryIncome;
    }

    /**
     * 设置工资收入。
     *
     * @param salaryIncome 当月工资收入（仅记录展示，不参与余额）
     */
    public void setSalaryIncome(BigDecimal salaryIncome) {
        this.salaryIncome = salaryIncome;
    }

    /**
     * 获取发放入账金额。
     *
     * @return 发放入账金额
     */
    public BigDecimal getAllowanceAmount() {
        return allowanceAmount;
    }

    /**
     * 设置发放入账金额。
     *
     * @param allowanceAmount 发放入账金额
     */
    public void setAllowanceAmount(BigDecimal allowanceAmount) {
        this.allowanceAmount = allowanceAmount;
    }

    /**
     * 获取工作内容摘要。
     *
     * @return 工作内容摘要
     */
    public String getWorkSummary() {
        return workSummary;
    }

    /**
     * 设置工作内容摘要。
     *
     * @param workSummary 工作内容摘要
     */
    public void setWorkSummary(String workSummary) {
        this.workSummary = workSummary;
    }

    /**
     * 获取入账流水 ID。
     *
     * @return 入账流水 ID
     */
    public Long getTransactionId() {
        return transactionId;
    }

    /**
     * 设置入账流水 ID。
     *
     * @param transactionId 入账流水 ID
     */
    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * 获取记录人用户 ID。
     *
     * @return 记录人用户 ID
     */
    public Long getRecordedBy() {
        return recordedBy;
    }

    /**
     * 设置记录人用户 ID。
     *
     * @param recordedBy 记录人用户 ID
     */
    public void setRecordedBy(Long recordedBy) {
        this.recordedBy = recordedBy;
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
