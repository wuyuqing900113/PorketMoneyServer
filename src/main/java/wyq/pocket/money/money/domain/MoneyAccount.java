package wyq.pocket.money.money.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 零花钱账户（余额快照，M2 设计 §4）。
 *
 * <p>一人一户、惰性开户（首笔入账创建）；余额由数据库 CHECK >= 0 兜底，
 * 并发更新走 version 乐观锁条件更新（§4.3）。
 */
public class MoneyAccount {

    /** 账户状态：正常。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 账户状态：冻结（成员被移除，只读不可变动）。 */
    public static final String STATUS_FROZEN = "FROZEN";

    private Long id;

    private Long familyId;

    private Long userId;

    private BigDecimal balance;

    private BigDecimal totalIncome;

    private BigDecimal totalExpense;

    private String status;

    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 获取账户 ID。
     *
     * @return 账户 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置账户 ID。
     *
     * @param id 账户 ID
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
     * 获取账户持有人用户 ID。
     *
     * @return 账户持有人用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置持有人用户 ID。
     *
     * @param userId 账户持有人用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取当前余额。
     *
     * @return 当前余额
     */
    public BigDecimal getBalance() {
        return balance;
    }

    /**
     * 设置当前余额。
     *
     * @param balance 当前余额
     */
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    /**
     * 获取累计收入。
     *
     * @return 累计收入
     */
    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    /**
     * 设置累计收入。
     *
     * @param totalIncome 累计收入
     */
    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    /**
     * 获取累计支出。
     *
     * @return 累计支出
     */
    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    /**
     * 设置累计支出。
     *
     * @param totalExpense 累计支出
     */
    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    /**
     * 获取账户状态（ACTIVE / FROZEN）。
     *
     * @return 账户状态（ACTIVE / FROZEN）
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置账户状态。
     *
     * @param status 账户状态（ACTIVE / FROZEN）
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取乐观锁版本号。
     *
     * @return 乐观锁版本号
     */
    public Long getVersion() {
        return version;
    }

    /**
     * 设置乐观锁版本号。
     *
     * @param version 乐观锁版本号
     */
    public void setVersion(Long version) {
        this.version = version;
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
