package wyq.pocket.money.rule.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 包月零花钱规则（M2 设计 §7）：按月定额发放，grant_day 1–28。
 */
public class MoneyRule {

    /** 规则状态：生效。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 规则状态：暂停。 */
    public static final String STATUS_PAUSED = "PAUSED";

    /** 规则状态：归档（终态）。 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private Long id;

    private Long familyId;

    private Long beneficiaryUserId;

    private String ruleName;

    private BigDecimal amount;

    private Integer grantDay;

    private String status;

    private String startMonth;

    private String endMonth;

    private String remark;

    private Long createdBy;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 获取规则 ID。
     *
     * @return 规则 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置规则 ID。
     *
     * @param id 规则 ID
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
     * 获取受益人用户 ID。
     *
     * @return 受益人用户 ID
     */
    public Long getBeneficiaryUserId() {
        return beneficiaryUserId;
    }

    /**
     * 设置受益人用户 ID。
     *
     * @param beneficiaryUserId 受益人用户 ID
     */
    public void setBeneficiaryUserId(Long beneficiaryUserId) {
        this.beneficiaryUserId = beneficiaryUserId;
    }

    /**
     * 获取规则名称（家庭内唯一）。
     *
     * @return 规则名称（家庭内唯一）
     */
    public String getRuleName() {
        return ruleName;
    }

    /**
     * 设置规则名称。
     *
     * @param ruleName 规则名称（家庭内唯一）
     */
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    /**
     * 获取每月发放金额。
     *
     * @return 每月发放金额
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 设置发放金额。
     *
     * @param amount 每月发放金额
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * 获取发放日（1–28）。
     *
     * @return 发放日（1–28）
     */
    public Integer getGrantDay() {
        return grantDay;
    }

    /**
     * 设置发放日。
     *
     * @param grantDay 发放日（1–28）
     */
    public void setGrantDay(Integer grantDay) {
        this.grantDay = grantDay;
    }

    /**
     * 获取规则状态（ACTIVE / PAUSED / ARCHIVED）。
     *
     * @return 规则状态（ACTIVE / PAUSED / ARCHIVED）
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置规则状态。
     *
     * @param status 规则状态（ACTIVE / PAUSED / ARCHIVED）
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取生效起始月（YYYY-MM）。
     *
     * @return 生效起始月（YYYY-MM）
     */
    public String getStartMonth() {
        return startMonth;
    }

    /**
     * 设置生效起始月。
     *
     * @param startMonth 生效起始月（YYYY-MM）
     */
    public void setStartMonth(String startMonth) {
        this.startMonth = startMonth;
    }

    /**
     * 获取失效月（YYYY-MM，含），null 表示长期。
     *
     * @return 失效月（YYYY-MM，含），null 表示长期
     */
    public String getEndMonth() {
        return endMonth;
    }

    /**
     * 设置失效月。
     *
     * @param endMonth 失效月（YYYY-MM，含），null 表示长期
     */
    public void setEndMonth(String endMonth) {
        this.endMonth = endMonth;
    }

    /**
     * 获取备注。
     *
     * @return 备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置备注。
     *
     * @param remark 备注
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 获取创建人用户 ID。
     *
     * @return 创建人用户 ID
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建人用户 ID。
     *
     * @param createdBy 创建人用户 ID
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
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
