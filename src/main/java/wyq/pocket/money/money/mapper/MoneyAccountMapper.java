package wyq.pocket.money.money.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.dto.AccountTotals;
import wyq.pocket.money.money.dto.MemberBalanceRow;

/**
 * 零花钱账户 Mapper（money_account，M2 设计 §4 / §11.1）。
 *
 * <p>惰性开户：ON CONFLICT DO NOTHING 容忍并发创建；余额变动一律走
 * version 乐观锁条件更新，余额下限由 CHECK 约束兜底。参数一律 #{} 占位。
 */
@Mapper
public interface MoneyAccountMapper {

    /** 查询列清单。 */
    String ACCOUNT_COLUMNS = "id, family_id, user_id, balance, total_income, total_expense, "
            + "status, version, created_at, updated_at";

    /**
     * 惰性开户：插入账户，user_id 冲突时忽略（并发首笔入账安全）。
     *
     * <p>冲突时影响行数为 0 且不回填 id，调用方须改走 findByUserId。
     *
     * @param account 账户（仅 familyId、userId 有效）
     * @return 影响行数（0 = 已存在）
     */
    @Insert("INSERT INTO money_account (family_id, user_id) "
            + "VALUES (#{familyId}, #{userId}) ON CONFLICT (user_id) DO NOTHING")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnoreConflict(MoneyAccount account);

    /**
     * 按持有人查询账户。
     *
     * @param userId 持有人用户 ID
     * @return 账户，未开户返回 null
     */
    @Select("SELECT " + ACCOUNT_COLUMNS + " FROM money_account WHERE user_id = #{userId}")
    MoneyAccount findByUserId(@Param("userId") long userId);

    /**
     * 按 ID 查询账户。
     *
     * @param id 账户 ID
     * @return 账户，不存在返回 null
     */
    @Select("SELECT " + ACCOUNT_COLUMNS + " FROM money_account WHERE id = #{id}")
    MoneyAccount findById(@Param("id") long id);

    /**
     * 乐观锁条件更新余额：delta 为带符号变动额（入账为正、出账为负）。
     *
     * @param id           账户 ID
     * @param version      期望版本号
     * @param delta        余额变动额（带符号）
     * @param incomeDelta  累计收入增量（0 或正数）
     * @param expenseDelta 累计支出增量（0 或正数）
     * @return 影响行数（0 = 版本冲突，调用方重试）
     */
    @Update("UPDATE money_account SET balance = balance + #{delta}, "
            + "total_income = total_income + #{incomeDelta}, "
            + "total_expense = total_expense + #{expenseDelta}, "
            + "version = version + 1, updated_at = now() "
            + "WHERE id = #{id} AND version = #{version}")
    int applyDelta(@Param("id") long id, @Param("version") long version,
                   @Param("delta") BigDecimal delta, @Param("incomeDelta") BigDecimal incomeDelta,
                   @Param("expenseDelta") BigDecimal expenseDelta);

    /**
     * 按持有人更新账户状态（成员移除冻结）。
     *
     * @param userId 持有人用户 ID
     * @param status 目标状态
     * @return 影响行数（0 = 未开户）
     */
    @Update("UPDATE money_account SET status = #{status}, updated_at = now() "
            + "WHERE user_id = #{userId}")
    int updateStatusByUserId(@Param("userId") long userId, @Param("status") String status);

    /**
     * 家庭总余额。
     *
     * @param familyId 家庭 ID
     * @return 总余额（无账户为 0）
     */
    @Select("SELECT COALESCE(SUM(balance), 0) FROM money_account WHERE family_id = #{familyId}")
    BigDecimal sumBalanceByFamily(@Param("familyId") long familyId);

    /**
     * 家庭累计收支汇总。
     *
     * @param familyId 家庭 ID
     * @return 累计收入 / 支出
     */
    @Select("SELECT COALESCE(SUM(total_income), 0) AS total_income, "
            + "COALESCE(SUM(total_expense), 0) AS total_expense "
            + "FROM money_account WHERE family_id = #{familyId}")
    @ConstructorArgs({
        @Arg(column = "total_income", javaType = BigDecimal.class),
        @Arg(column = "total_expense", javaType = BigDecimal.class)
    })
    AccountTotals sumTotalsByFamily(@Param("familyId") long familyId);

    /**
     * 看板：家庭全员余额（未开户成员余额记 0）。
     *
     * @param familyId 家庭 ID
     * @return 成员余额行列表
     */
    @Select("SELECT fm.user_id, u.nickname, COALESCE(a.balance, 0) AS balance "
            + "FROM family_member fm "
            + "JOIN app_user u ON u.id = fm.user_id "
            + "LEFT JOIN money_account a ON a.user_id = fm.user_id "
            + "WHERE fm.family_id = #{familyId} ORDER BY fm.id")
    @ConstructorArgs({
        @Arg(column = "user_id", javaType = Long.class),
        @Arg(column = "nickname", javaType = String.class),
        @Arg(column = "balance", javaType = BigDecimal.class)
    })
    List<MemberBalanceRow> findMemberBalances(@Param("familyId") long familyId);

    /**
     * 对账：最新流水 balance_after 与账户余额不一致的账户 ID。
     *
     * @return 不一致账户 ID 列表
     */
    @Select("SELECT a.id FROM money_account a WHERE EXISTS ("
            + "SELECT 1 FROM money_transaction t WHERE t.account_id = a.id) "
            + "AND a.balance <> (SELECT t2.balance_after FROM money_transaction t2 "
            + "WHERE t2.account_id = a.id ORDER BY t2.id DESC LIMIT 1)")
    List<Long> findMismatchedAccountIds();

    /**
     * 对账：无流水但余额非 0 的账户 ID（数据异常）。
     *
     * @return 异常账户 ID 列表
     */
    @Select("SELECT a.id FROM money_account a WHERE a.balance <> 0 "
            + "AND NOT EXISTS (SELECT 1 FROM money_transaction t WHERE t.account_id = a.id)")
    List<Long> findOrphanBalanceAccountIds();
}
