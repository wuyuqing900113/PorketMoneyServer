package wyq.pocket.money.money.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import wyq.pocket.money.money.domain.WorkValueRecord;

/**
 * 工作价值记录 Mapper（work_value_record，M2 设计 §11.3）。
 *
 * <p>参数一律 #{} 占位。
 */
@Mapper
public interface WorkValueRecordMapper {

    /** 查询列清单。 */
    String RECORD_COLUMNS = "id, family_id, parent_user_id, work_month, salary_income, "
            + "allowance_amount, work_summary, transaction_id, recorded_by, "
            + "created_at, updated_at";

    /**
     * 插入记录。
     *
     * @param record 记录（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO work_value_record (family_id, parent_user_id, work_month, "
            + "salary_income, allowance_amount, work_summary, transaction_id, recorded_by) "
            + "VALUES (#{familyId}, #{parentUserId}, #{workMonth}, #{salaryIncome}, "
            + "#{allowanceAmount}, #{workSummary}, #{transactionId}, #{recordedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkValueRecord record);

    /**
     * 按 ID 查询记录。
     *
     * @param id 记录 ID
     * @return 记录，不存在返回 null
     */
    @Select("SELECT " + RECORD_COLUMNS + " FROM work_value_record WHERE id = #{id}")
    WorkValueRecord findById(@Param("id") long id);

    /**
     * 记录列表（按月份倒序，可选月份过滤）。
     *
     * @param familyId  家庭 ID
     * @param workMonth 可选：月份过滤（YYYY-MM）
     * @param limit     条数上限
     * @return 记录列表
     */
    @Select("<script>SELECT " + RECORD_COLUMNS + " FROM work_value_record "
            + "WHERE family_id = #{familyId} "
            + "<if test='workMonth != null'>AND work_month = #{workMonth} </if>"
            + "ORDER BY work_month DESC, id DESC LIMIT #{limit}</script>")
    List<WorkValueRecord> findList(@Param("familyId") long familyId,
                                   @Param("workMonth") String workMonth,
                                   @Param("limit") int limit);
}
