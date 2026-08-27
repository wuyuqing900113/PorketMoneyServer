package wyq.pocket.money.rule.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.rule.domain.RuleGrantRecord;

/**
 * 规则发放记录 Mapper（rule_grant_record，M2 设计 §7.3）。
 *
 * <p>uk(rule_id, grant_month) 为结算幂等锚点；参数一律 #{} 占位。
 */
@Mapper
public interface RuleGrantRecordMapper {

    /** 查询列清单。 */
    String GRANT_COLUMNS =
            "id, rule_id, grant_month, amount, transaction_id, status, granted_at";

    /**
     * 插入发放记录（结算幂等锚点；并发重复插入抛 DuplicateKeyException）。
     *
     * @param record 记录（id 由 BIGSERIAL 回填；amount/status 必填）
     * @return 影响行数
     */
    @Insert("INSERT INTO rule_grant_record (rule_id, grant_month, amount, status) "
            + "VALUES (#{ruleId}, #{grantMonth}, #{amount}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RuleGrantRecord record);

    /**
     * 规则近月发放记录（倒序，规则详情页）。
     *
     * @param ruleId 规则 ID
     * @param limit  条数上限
     * @return 发放记录列表
     */
    @Select("SELECT " + GRANT_COLUMNS + " FROM rule_grant_record WHERE rule_id = #{ruleId} "
            + "ORDER BY grant_month DESC LIMIT #{limit}")
    List<RuleGrantRecord> findRecentByRule(@Param("ruleId") long ruleId,
                                           @Param("limit") int limit);

    /**
     * 规则发放记录总数（删除约束校验）。
     *
     * @param ruleId 规则 ID
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM rule_grant_record WHERE rule_id = #{ruleId}")
    int countByRule(@Param("ruleId") long ruleId);

    /**
     * 家庭内指定月份已发放的规则 ID 集合（列表页「当月已发放」标记）。
     *
     * @param familyId  家庭 ID
     * @param grantMonth 月份（YYYY-MM）
     * @return 已发放规则 ID 列表
     */
    @Select("SELECT g.rule_id FROM rule_grant_record g "
            + "JOIN money_rule r ON r.id = g.rule_id "
            + "WHERE r.family_id = #{familyId} AND g.grant_month = #{grantMonth}")
    List<Long> findGrantedRuleIds(@Param("familyId") long familyId,
                                  @Param("grantMonth") String grantMonth);

    /**
     * 回填发放流水 ID（结算事务内）。
     *
     * @param id            记录 ID
     * @param transactionId 流水 ID
     * @return 影响行数
     */
    @Update("UPDATE rule_grant_record SET transaction_id = #{transactionId} WHERE id = #{id}")
    int updateTransactionId(@Param("id") long id, @Param("transactionId") long transactionId);
}
