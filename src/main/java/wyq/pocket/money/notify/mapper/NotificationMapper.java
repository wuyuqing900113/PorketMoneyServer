package wyq.pocket.money.notify.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.notify.domain.Notification;

/**
 * 站内信通知 Mapper（notification，M5 设计 §9.1）。
 *
 * <p>参数一律 #{} 占位；接收人维度查询，归属校验由上层以 user_id 约束。
 */
@Mapper
public interface NotificationMapper {

    /** 查询列清单。 */
    String COLUMNS = "id, user_id, family_id, type, title, content, biz_ref_type, biz_ref_id, "
            + "read_at, created_at";

    /**
     * 插入通知（created_at 由库默认 now() 填充）。
     *
     * @param notification 通知（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO notification (user_id, family_id, type, title, content, "
            + "biz_ref_type, biz_ref_id) VALUES (#{userId}, #{familyId}, #{type}, #{title}, "
            + "#{content}, #{bizRefType}, #{bizRefId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notification notification);

    /**
     * 按 ID 查询。
     *
     * @param id 通知 ID
     * @return 通知，不存在返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM notification WHERE id = #{id}")
    Notification findById(@Param("id") long id);

    /**
     * 接收人通知分页（未读优先，同刻按 id 倒序）。
     *
     * @param userId 接收人用户 ID
     * @param limit  页大小
     * @param offset 偏移
     * @return 通知列表
     */
    @Select("SELECT " + COLUMNS + " FROM notification WHERE user_id = #{userId} "
            + "ORDER BY (read_at IS NULL) DESC, created_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Notification> findPage(@Param("userId") long userId, @Param("limit") int limit,
                                @Param("offset") int offset);

    /**
     * 接收人通知总数。
     *
     * @param userId 接收人用户 ID
     * @return 通知总数
     */
    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId}")
    long countByUser(@Param("userId") long userId);

    /**
     * 接收人未读数。
     *
     * @param userId 接收人用户 ID
     * @return 未读数
     */
    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") long userId);

    /**
     * 标记单条已读（幂等，仅限本人）。
     *
     * @param id     通知 ID
     * @param userId 接收人用户 ID
     * @return 影响行数（0 = 不存在或非本人或已读）
     */
    @Update("UPDATE notification SET read_at = COALESCE(read_at, now()) "
            + "WHERE id = #{id} AND user_id = #{userId}")
    int markRead(@Param("id") long id, @Param("userId") long userId);

    /**
     * 全部标记已读（仅限本人未读通知）。
     *
     * @param userId 接收人用户 ID
     * @return 影响行数
     */
    @Update("UPDATE notification SET read_at = now() "
            + "WHERE user_id = #{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") long userId);

    /**
     * 删除超保留期的已读通知（清理任务用）。
     *
     * @param cutoff 截止时间（read_at 早于该值的已读通知）
     * @return 影响行数
     */
    @Delete("DELETE FROM notification WHERE read_at IS NOT NULL AND read_at < #{cutoff}")
    int deleteReadBefore(@Param("cutoff") Instant cutoff);
}
