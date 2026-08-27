package wyq.pocket.money.ai.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.ai.domain.AiSession;

/**
 * AI 会话 Mapper（ai_session，M4 设计 §10.1）。
 *
 * <p>一人一活跃会话；时间戳由库默认 / service 显式写入（TIMESTAMPTZ）。
 */
@Mapper
public interface AiSessionMapper {

    /** 查询列清单。 */
    String COLUMNS = "id, user_id, family_id, channel, status, created_at, last_active_at";

    /**
     * 插入会话（status / channel 由调用方显式设置）。
     *
     * @param session 会话（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO ai_session (user_id, family_id, channel, status) "
            + "VALUES (#{userId}, #{familyId}, #{channel}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiSession session);

    /**
     * 按 ID 查询会话。
     *
     * @param id 会话 ID
     * @return 会话，不存在返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM ai_session WHERE id = #{id}")
    AiSession findById(@Param("id") long id);

    /**
     * 查询用户活跃会话（取最新一条）。
     *
     * @param userId 用户 ID
     * @return 活跃会话，无则返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM ai_session "
            + "WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    AiSession findActiveByUser(@Param("userId") long userId);

    /**
     * 刷新最近活跃时间。
     *
     * @param id       会话 ID
     * @param activeAt 最近活跃时间
     * @return 影响行数
     */
    @Update("UPDATE ai_session SET last_active_at = #{activeAt} WHERE id = #{id}")
    int updateLastActive(@Param("id") long id, @Param("activeAt") Instant activeAt);

    /**
     * 删除超期会话（清理任务，调用方须先删其消息与待确认动作）。
     *
     * @param cutoff 保留期阈值（last_active_at 早于该值）
     * @return 影响行数
     */
    @Delete("DELETE FROM ai_session WHERE last_active_at < #{cutoff}")
    int deleteBefore(@Param("cutoff") Instant cutoff);
}
