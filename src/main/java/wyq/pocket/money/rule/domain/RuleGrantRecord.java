package wyq.pocket.money.rule.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 规则发放记录（M2 设计 §7.3 / §11.2）：(rule_id, grant_month) 唯一 = 结算幂等锚点。
 */
public class RuleGrantRecord {

    /** 发放状态：成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    private Long id;

    private Long ruleId;

    private String grantMonth;

    private BigDecimal amount;

    private Long transactionId;

    private String status;

    private Instant grantedAt;

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
     * 获取规则 ID。
     *
     * @return 规则 ID
     */
    public Long getRuleId() {
        return ruleId;
    }

    /**
     * 设置规则 ID。
     *
     * @param ruleId 规则 ID
     */
    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    /**
     * 获取发放月份（YYYY-MM）。
     *
     * @return 发放月份（YYYY-MM）
     */
    public String getGrantMonth() {
        return grantMonth;
    }

    /**
     * 设置发放月份。
     *
     * @param grantMonth 发放月份（YYYY-MM）
     */
    public void setGrantMonth(String grantMonth) {
        this.grantMonth = grantMonth;
    }

    /**
     * 获取发放金额（记账时点快照）。
     *
     * @return 发放金额（记账时点快照）
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 设置发放金额。
     *
     * @param amount 发放金额（记账时点快照）
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * 获取发放流水 ID。
     *
     * @return 发放流水 ID
     */
    public Long getTransactionId() {
        return transactionId;
    }

    /**
     * 设置发放流水 ID。
     *
     * @param transactionId 发放流水 ID
     */
    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * 获取发放状态（SUCCESS）。
     *
     * @return 发放状态（SUCCESS）
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置发放状态。
     *
     * @param status 发放状态（SUCCESS）
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取发放时间。
     *
     * @return 发放时间
     */
    public Instant getGrantedAt() {
        return grantedAt;
    }

    /**
     * 设置发放时间。
     *
     * @param grantedAt 发放时间
     */
    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }
}
