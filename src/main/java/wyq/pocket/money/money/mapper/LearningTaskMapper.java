package wyq.pocket.money.money.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.money.domain.LearningTask;

/**
 * 学习任务 Mapper（learning_task，M2 设计 §10 / §11.3）。
 *
 * <p>状态流转在 service 层校验后调用对应 update；
 * 参数一律 #{} 占位。
 */
@Mapper
public interface LearningTaskMapper {

    /** 查询列清单。 */
    String TASK_COLUMNS = "id, family_id, assignee_user_id, created_by, title, reward_amount, "
            + "deadline, status, submit_note, submitted_at, reject_reason, reviewed_by, "
            + "reviewed_at, transaction_id, created_at, updated_at";

    /**
     * 插入任务（status 由数据库默认 PENDING）。
     *
     * @param task 任务（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO learning_task (family_id, assignee_user_id, created_by, title, "
            + "reward_amount, deadline) VALUES ("
            + "#{familyId}, #{assigneeUserId}, #{createdBy}, #{title}, "
            + "#{rewardAmount}, #{deadline})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LearningTask task);

    /**
     * 按 ID 查询任务。
     *
     * @param id 任务 ID
     * @return 任务，不存在返回 null
     */
    @Select("SELECT " + TASK_COLUMNS + " FROM learning_task WHERE id = #{id}")
    LearningTask findById(@Param("id") long id);

    /**
     * 任务分页查询（按 id 倒序）。
     *
     * @param familyId       家庭 ID
     * @param status         可选：状态过滤
     * @param assigneeUserId 可选：执行人过滤
     * @param limit          页大小
     * @param offset         偏移量
     * @return 任务列表
     */
    @Select("<script>SELECT " + TASK_COLUMNS + " FROM learning_task "
            + "WHERE family_id = #{familyId} "
            + "<if test='status != null'>AND status = #{status} </if>"
            + "<if test='assigneeUserId != null'>AND assignee_user_id = #{assigneeUserId} </if>"
            + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<LearningTask> findPage(@Param("familyId") long familyId,
                                @Param("status") String status,
                                @Param("assigneeUserId") Long assigneeUserId,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    /**
     * 任务分页计数（过滤条件同 findPage）。
     *
     * @param familyId       家庭 ID
     * @param status         可选过滤
     * @param assigneeUserId 可选过滤
     * @return 总条数
     */
    @Select("<script>SELECT COUNT(*) FROM learning_task WHERE family_id = #{familyId} "
            + "<if test='status != null'>AND status = #{status} </if>"
            + "<if test='assigneeUserId != null'>AND assignee_user_id = #{assigneeUserId} </if>"
            + "</script>")
    int countPage(@Param("familyId") long familyId,
                  @Param("status") String status,
                  @Param("assigneeUserId") Long assigneeUserId);

    /**
     * 提交（PENDING / REJECTED → SUBMITTED）。
     *
     * @param id          任务 ID
     * @param submitNote  提交说明
     * @param submittedAt 提交时间
     * @return 影响行数
     */
    @Update("UPDATE learning_task SET status = 'SUBMITTED', submit_note = #{submitNote}, "
            + "submitted_at = #{submittedAt}, reject_reason = NULL, updated_at = now() "
            + "WHERE id = #{id}")
    int updateSubmit(@Param("id") long id, @Param("submitNote") String submitNote,
                     @Param("submittedAt") Instant submittedAt);

    /**
     * 通过并发放（SUBMITTED → APPROVED）。
     *
     * @param id            任务 ID
     * @param reviewedBy    审核人用户 ID
     * @param reviewedAt    审核时间
     * @param transactionId 发放流水 ID
     * @return 影响行数
     */
    @Update("UPDATE learning_task SET status = 'APPROVED', reviewed_by = #{reviewedBy}, "
            + "reviewed_at = #{reviewedAt}, transaction_id = #{transactionId}, "
            + "updated_at = now() WHERE id = #{id}")
    int updateApprove(@Param("id") long id, @Param("reviewedBy") long reviewedBy,
                      @Param("reviewedAt") Instant reviewedAt,
                      @Param("transactionId") long transactionId);

    /**
     * 驳回（SUBMITTED → REJECTED，可重提）。
     *
     * @param id           任务 ID
     * @param rejectReason 驳回原因
     * @param reviewedBy   审核人用户 ID
     * @param reviewedAt   审核时间
     * @return 影响行数
     */
    @Update("UPDATE learning_task SET status = 'REJECTED', reject_reason = #{rejectReason}, "
            + "reviewed_by = #{reviewedBy}, reviewed_at = #{reviewedAt}, updated_at = now() "
            + "WHERE id = #{id}")
    int updateReject(@Param("id") long id, @Param("rejectReason") String rejectReason,
                     @Param("reviewedBy") long reviewedBy, @Param("reviewedAt") Instant reviewedAt);

    /**
     * 取消（PENDING / SUBMITTED → CANCELED，仅发放前）。
     *
     * @param id 任务 ID
     * @return 影响行数
     */
    @Update("UPDATE learning_task SET status = 'CANCELED', updated_at = now() WHERE id = #{id}")
    int updateCancel(@Param("id") long id);

    /**
     * 取消执行人全部未发放任务（成员移除联动：PENDING / SUBMITTED → CANCELED）。
     *
     * @param assigneeUserId 执行人用户 ID
     * @return 影响行数
     */
    @Update("UPDATE learning_task SET status = 'CANCELED', updated_at = now() "
            + "WHERE assignee_user_id = #{assigneeUserId} "
            + "AND status IN ('PENDING', 'SUBMITTED')")
    int cancelOpenByAssignee(@Param("assigneeUserId") long assigneeUserId);
}
