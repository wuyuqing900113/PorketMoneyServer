package wyq.pocket.money.ai.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.ai.domain.AiPendingAction;

/**
 * 资金写待确认动作 Mapper（ai_pending_action，M4 设计 §6.2）。
 *
 * <p>{@code params_json} 为 JSONB：写入显式 CAST，读回经
 * {@code CAST(x AS VARCHAR)} 还原为 JSON 文本（H2/PG 一致）。
 * 确认执行采用条件更新原子抢占（PENDING → EXECUTED）保证单次执行。
 */
@Mapper
public interface AiPendingActionMapper {

    /** 查询列清单（params_json 读回为 VARCHAR 文本）。 */
    String COLUMNS = "id, session_id, user_id, intent, "
            + "CAST(params_json AS VARCHAR) AS params_json, status, "
            + "created_at, expires_at, executed_at";

    /**
     * 插入待确认动作（status 由库默认 PENDING）。
     *
     * @param action 动作（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO ai_pending_action (session_id, user_id, intent, params_json, "
            + "expires_at) VALUES (#{sessionId}, #{userId}, #{intent}, "
            + "CAST(#{paramsJson} AS jsonb), #{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiPendingAction action);

    /**
     * 按 ID 查询动作。
     *
     * @param id 动作 ID
     * @return 动作，不存在返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM ai_pending_action WHERE id = #{id}")
    AiPendingAction findById(@Param("id") long id);

    /**
     * 查询会话下未完成的待确认动作（600004 检测）。
     *
     * @param sessionId 会话 ID
     * @return 待确认动作，无则返回 null
     */
    @Select("SELECT " + COLUMNS + " FROM ai_pending_action "
            + "WHERE session_id = #{sessionId} AND status = 'PENDING' "
            + "ORDER BY id DESC LIMIT 1")
    AiPendingAction findPendingBySession(@Param("sessionId") long sessionId);

    /**
     * 原子抢占执行（PENDING → EXECUTED）：仅当归属当前用户、未过期、
     * 仍为 PENDING 时成功，返回影响行数（并发仅一个赢家）。
     *
     * @param id     动作 ID
     * @param userId 归属用户 ID
     * @param now    当前时间
     * @return 影响行数（1=抢占成功，0=已终态/过期/越权）
     */
    @Update("UPDATE ai_pending_action SET status = 'EXECUTED', executed_at = #{now} "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = 'PENDING' "
            + "AND expires_at > #{now}")
    int claimExecuted(@Param("id") long id, @Param("userId") long userId,
                      @Param("now") Instant now);

    /**
     * 标记执行被拒（业务失败，如余额不足）。
     *
     * @param id 动作 ID
     * @return 影响行数
     */
    @Update("UPDATE ai_pending_action SET status = 'REJECTED' WHERE id = #{id}")
    int updateReject(@Param("id") long id);

    /**
     * 取消（PENDING → CANCELED）：仅归属当前用户且仍为 PENDING。
     *
     * @param id     动作 ID
     * @param userId 归属用户 ID
     * @return 影响行数（1=取消成功，0=已终态/越权）
     */
    @Update("UPDATE ai_pending_action SET status = 'CANCELED' "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = 'PENDING'")
    int updateCancel(@Param("id") long id, @Param("userId") long userId);

    /**
     * 查询已过期仍未确认的动作（清理任务）。
     *
     * @param now 当前时间
     * @return 过期动作列表
     */
    @Select("SELECT " + COLUMNS + " FROM ai_pending_action "
            + "WHERE status = 'PENDING' AND expires_at < #{now}")
    List<AiPendingAction> findExpiredPending(@Param("now") Instant now);

    /**
     * 标记过期（PENDING → EXPIRED，清理任务）。
     *
     * @param id 动作 ID
     * @return 影响行数
     */
    @Update("UPDATE ai_pending_action SET status = 'EXPIRED' "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int updateExpire(@Param("id") long id);

    /**
     * 删除超期终态动作（清理任务）。
     *
     * @param cutoff 保留期阈值（created_at 早于该值）
     * @return 影响行数
     */
    @Delete("DELETE FROM ai_pending_action WHERE created_at < #{cutoff} "
            + "AND status IN ('EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED')")
    int deleteTerminalBefore(@Param("cutoff") Instant cutoff);

    /**
     * 删除超期会话下的动作（清理任务，会话级联删除前置步骤）。
     *
     * @param cutoff 保留期阈值（所属会话 last_active_at 早于该值）
     * @return 影响行数
     */
    @Delete("DELETE FROM ai_pending_action WHERE session_id IN "
            + "(SELECT id FROM ai_session WHERE last_active_at < #{cutoff})")
    int deleteBySessionBefore(@Param("cutoff") Instant cutoff);
}
