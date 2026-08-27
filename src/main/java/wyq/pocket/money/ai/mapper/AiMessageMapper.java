package wyq.pocket.money.ai.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import wyq.pocket.money.ai.domain.AiMessage;

/**
 * AI 消息 Mapper（ai_message，M4 设计 §10.1）。
 *
 * <p>{@code tool_call_json} 为 JSONB：写入显式 CAST，读回经
 * {@code CAST(x AS VARCHAR)} 还原为 JSON 文本（H2/PG 一致，约定同 V3/V7）。
 */
@Mapper
public interface AiMessageMapper {

    /** 查询列清单（tool_call_json 读回为 VARCHAR 文本）。 */
    String COLUMNS = "id, session_id, role, content, intent, "
            + "CAST(tool_call_json AS VARCHAR) AS tool_call_json, created_at";

    /**
     * 插入消息（created_at 由库默认 now() 填充）。
     *
     * @param message 消息（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO ai_message (session_id, role, content, intent, tool_call_json) "
            + "VALUES (#{sessionId}, #{role}, #{content}, #{intent}, "
            + "CAST(#{toolCallJson} AS jsonb))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiMessage message);

    /**
     * 查询会话消息（按创建时间升序）。
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    @Select("SELECT " + COLUMNS + " FROM ai_message WHERE session_id = #{sessionId} "
            + "ORDER BY created_at ASC, id ASC")
    List<AiMessage> findBySession(@Param("sessionId") long sessionId);

    /**
     * 删除超期会话下的消息（清理任务，会话级联删除前置步骤）。
     *
     * @param cutoff 保留期阈值（所属会话 last_active_at 早于该值）
     * @return 影响行数
     */
    @Delete("DELETE FROM ai_message WHERE session_id IN "
            + "(SELECT id FROM ai_session WHERE last_active_at < #{cutoff})")
    int deleteBySessionBefore(@Param("cutoff") Instant cutoff);
}
