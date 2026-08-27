package wyq.pocket.money.money.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 零花钱流水（只追加台账，M2 设计 §4）。
 *
 * <p>balance_after 为记账后余额快照，供对账校验；
 * request_id 非空时全局唯一（M3 幂等启用预留）。
 */
public class MoneyTransaction {

    private Long id;

    private Long familyId;

    private Long accountId;

    private Long userId;

    private TxDirection direction;

    private TxBizType bizType;

    private BigDecimal amount;

    private BigDecimal balanceAfter;

    private TxRefType refType;

    private Long refId;

    private Long operatorUserId;

    private String remark;

    private String requestId;

    private Instant createdAt;

    /**
     * 获取流水 ID。
     *
     * @return 流水 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置流水 ID。
     *
     * @param id 流水 ID
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
     * 获取账户 ID。
     *
     * @return 账户 ID
     */
    public Long getAccountId() {
        return accountId;
    }

    /**
     * 设置账户 ID。
     *
     * @param accountId 账户 ID
     */
    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    /**
     * 获取账户持有人用户 ID。
     *
     * @return 账户持有人用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置账户持有人用户 ID。
     *
     * @param userId 账户持有人用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取方向（IN / OUT）。
     *
     * @return 方向（IN / OUT）
     */
    public TxDirection getDirection() {
        return direction;
    }

    /**
     * 设置方向。
     *
     * @param direction 方向（IN / OUT）
     */
    public void setDirection(TxDirection direction) {
        this.direction = direction;
    }

    /**
     * 获取业务类型。
     *
     * @return 业务类型
     */
    public TxBizType getBizType() {
        return bizType;
    }

    /**
     * 设置业务类型。
     *
     * @param bizType 业务类型
     */
    public void setBizType(TxBizType bizType) {
        this.bizType = bizType;
    }

    /**
     * 获取金额（恒正）。
     *
     * @return 金额（恒正）
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 设置金额。
     *
     * @param amount 金额（恒正）
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /**
     * 获取记账后余额。
     *
     * @return 记账后余额
     */
    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    /**
     * 设置记账后余额。
     *
     * @param balanceAfter 记账后余额
     */
    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    /**
     * 获取关联单据类型。
     *
     * @return 关联单据类型
     */
    public TxRefType getRefType() {
        return refType;
    }

    /**
     * 设置关联单据类型。
     *
     * @param refType 关联单据类型
     */
    public void setRefType(TxRefType refType) {
        this.refType = refType;
    }

    /**
     * 获取关联单据 ID。
     *
     * @return 关联单据 ID
     */
    public Long getRefId() {
        return refId;
    }

    /**
     * 设置关联单据 ID。
     *
     * @param refId 关联单据 ID
     */
    public void setRefId(Long refId) {
        this.refId = refId;
    }

    /**
     * 获取操作人用户 ID（结算任务为 null）。
     *
     * @return 操作人用户 ID（结算任务为 null）
     */
    public Long getOperatorUserId() {
        return operatorUserId;
    }

    /**
     * 设置操作人用户 ID。
     *
     * @param operatorUserId 操作人用户 ID（结算任务为 null）
     */
    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
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
     * 获取幂等键（M2 预留，M3 启用）。
     *
     * @return 幂等键（M2 预留，M3 启用）
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 设置幂等键。
     *
     * @param requestId 幂等键（M2 预留，M3 启用）
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * 获取记账时间。
     *
     * @return 记账时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置记账时间。
     *
     * @param createdAt 记账时间
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
