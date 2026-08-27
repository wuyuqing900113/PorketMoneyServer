package wyq.pocket.money.rule.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.rule.domain.MoneyRule;

/**
 * 包月规则 Mapper（money_rule，M2 设计 §7 / §11.2）。
 *
 * <p>参数一律 #{} 占位。
 */
@Mapper
public interface MoneyRuleMapper {

    /** 查询列清单。 */
    String RULE_COLUMNS = "id, family_id, beneficiary_user_id, rule_name, amount, grant_day, "
            + "status, start_month, end_month, remark, created_by, created_at, updated_at";

    /**
     * 插入规则。
     *
     * @param rule 规则（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO money_rule (family_id, beneficiary_user_id, rule_name, amount, "
            + "grant_day, status, start_month, end_month, remark, created_by) VALUES ("
            + "#{familyId}, #{beneficiaryUserId}, #{ruleName}, #{amount}, #{grantDay}, "
            + "#{status}, #{startMonth}, #{endMonth}, #{remark}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MoneyRule rule);

    /**
     * 按 ID 查询规则。
     *
     * @param id 规则 ID
     * @return 规则，不存在返回 null
     */
    @Select("SELECT " + RULE_COLUMNS + " FROM money_rule WHERE id = #{id}")
    MoneyRule findById(@Param("id") long id);

    /**
     * 家庭规则列表（按 id 倒序，全状态）。
     *
     * @param familyId 家庭 ID
     * @return 规则列表
     */
    @Select("SELECT " + RULE_COLUMNS + " FROM money_rule WHERE family_id = #{familyId} "
            + "ORDER BY id DESC")
    List<MoneyRule> findListByFamily(@Param("familyId") long familyId);

    /**
     * 修改规则（名称 / 金额 / 发放日 / 失效月 / 备注）。
     *
     * @param id        规则 ID
     * @param ruleName  名称
     * @param amount    金额
     * @param grantDay  发放日
     * @param endMonth  失效月（可空）
     * @param remark    备注（可空）
     * @return 影响行数
     */
    @Update("UPDATE money_rule SET rule_name = #{ruleName}, amount = #{amount}, "
            + "grant_day = #{grantDay}, end_month = #{endMonth}, remark = #{remark}, "
            + "updated_at = now() WHERE id = #{id}")
    int update(@Param("id") long id, @Param("ruleName") String ruleName,
               @Param("amount") BigDecimal amount, @Param("grantDay") int grantDay,
               @Param("endMonth") String endMonth, @Param("remark") String remark);

    /**
     * 更新规则状态。
     *
     * @param id     规则 ID
     * @param status 目标状态
     * @return 影响行数
     */
    @Update("UPDATE money_rule SET status = #{status}, updated_at = now() WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);

    /**
     * 删除规则（仅限无发放记录）。
     *
     * @param id 规则 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM money_rule WHERE id = #{id}")
    int deleteById(@Param("id") long id);

    /**
     * 受益人未归档规则数（上限校验）。
     *
     * @param beneficiaryUserId 受益人用户 ID
     * @return ACTIVE + PAUSED 规则数
     */
    @Select("SELECT COUNT(*) FROM money_rule WHERE beneficiary_user_id = #{beneficiaryUserId} "
            + "AND status IN ('ACTIVE', 'PAUSED')")
    int countUnarchivedByBeneficiary(@Param("beneficiaryUserId") long beneficiaryUserId);

    /**
     * 家庭内规则重名计数（可排除自身）。
     *
     * @param familyId  家庭 ID
     * @param ruleName  规则名
     * @param excludeId 排除的规则 ID（创建传 null）
     * @return 同名规则数
     */
    @Select("<script>SELECT COUNT(*) FROM money_rule WHERE family_id = #{familyId} "
            + "AND rule_name = #{ruleName}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    int countByName(@Param("familyId") long familyId, @Param("ruleName") String ruleName,
                    @Param("excludeId") Long excludeId);

    /**
     * 结算扫描：当月应发规则（ACTIVE 且发放日已到且月份在生效区间内）。
     *
     * @param day   当月日（1–31）
     * @param month 当月（YYYY-MM）
     * @return 应发规则列表
     */
    @Select("SELECT " + RULE_COLUMNS + " FROM money_rule WHERE status = 'ACTIVE' "
            + "AND grant_day <= #{day} AND start_month <= #{month} "
            + "AND (end_month IS NULL OR end_month >= #{month})")
    List<MoneyRule> findDueRules(@Param("day") int day, @Param("month") String month);

    /**
     * 到期归档：end_month 早于当月的 ACTIVE / PAUSED 规则置 ARCHIVED。
     *
     * @param month 当月（YYYY-MM）
     * @return 影响行数
     */
    @Update("UPDATE money_rule SET status = 'ARCHIVED', updated_at = now() "
            + "WHERE status <> 'ARCHIVED' AND end_month IS NOT NULL "
            + "AND end_month < #{month}")
    int archiveExpired(@Param("month") String month);

    /**
     * 暂停受益人全部 ACTIVE 规则（成员移除联动）。
     *
     * @param userId 受益人用户 ID
     * @return 影响行数
     */
    @Update("UPDATE money_rule SET status = 'PAUSED', updated_at = now() "
            + "WHERE beneficiary_user_id = #{userId} AND status = 'ACTIVE'")
    int pauseActiveByBeneficiary(@Param("userId") long userId);
}
