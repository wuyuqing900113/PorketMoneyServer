package wyq.pocket.money.common.idempotency.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.common.idempotency.IdempotencyRecord;

/**
 * 幂等记录 Mapper（idempotency_record，M3 设计 §5）。
 *
 * <p>两阶段写入：INSERT 受理（uk_idem_user_key 唯一约束兜底并发），
 * 成功后 UPDATE 回填响应并置 PROCESSED，失败 DELETE 释放键。
 * 参数一律 #{} 占位；resp_body 存 VARCHAR（原始 JSON 文本），避免 JSONB
 * 在 H2 上回读时被包一层引号（H2 与 PostgreSQL 的 JSON 序列化差异）。
 */
@Mapper
public interface IdempotencyRecordMapper {

    /**
     * 插入受理中记录（status 默认 IN_PROGRESS）。
     *
     * @param userId      用户 ID
     * @param idemKey     幂等键
     * @param method      请求方法
     * @param path        请求路径
     * @param payloadHash 请求指纹
     * @param expiresAt   过期时间
     * @return 影响行数
     */
    @Insert("INSERT INTO idempotency_record "
            + "(user_id, idem_key, method, path, payload_hash, expires_at) VALUES "
            + "(#{userId}, #{idemKey}, #{method}, #{path}, #{payloadHash}, #{expiresAt})")
    int insert(@Param("userId") long userId, @Param("idemKey") String idemKey,
            @Param("method") String method, @Param("path") String path,
            @Param("payloadHash") String payloadHash, @Param("expiresAt") Instant expiresAt);

    /**
     * 按用户与幂等键查询记录。
     *
     * @param userId  用户 ID
     * @param idemKey 幂等键
     * @return 记录；不存在返回 null
     */
    @Select("SELECT id, user_id, idem_key, method, path, payload_hash, resp_code, resp_body, "
            + "status, created_at, expires_at FROM idempotency_record "
            + "WHERE user_id = #{userId} AND idem_key = #{idemKey}")
    IdempotencyRecord findByUserAndKey(@Param("userId") long userId,
            @Param("idemKey") String idemKey);

    /**
     * 回填响应并置 PROCESSED（仅 IN_PROGRESS 生效）。
     *
     * @param userId   用户 ID
     * @param idemKey  幂等键
     * @param respCode 响应错误码
     * @param respBody 原始响应体 JSON
     * @return 影响行数
     */
    @Update("UPDATE idempotency_record SET resp_code = #{respCode}, "
            + "resp_body = #{respBody}, status = 'PROCESSED' "
            + "WHERE user_id = #{userId} AND idem_key = #{idemKey} AND status = 'IN_PROGRESS'")
    int markProcessed(@Param("userId") long userId, @Param("idemKey") String idemKey,
            @Param("respCode") int respCode, @Param("respBody") String respBody);

    /**
     * 删除记录（业务失败释放键）。
     *
     * @param userId  用户 ID
     * @param idemKey 幂等键
     * @return 影响行数
     */
    @Delete("DELETE FROM idempotency_record WHERE user_id = #{userId} AND idem_key = #{idemKey}")
    int deleteByUserAndKey(@Param("userId") long userId, @Param("idemKey") String idemKey);

    /**
     * 删除指定时间之前过期的记录（清理任务）。
     *
     * @param before 过期时间阈值（不含）
     * @return 删除行数
     */
    @Delete("DELETE FROM idempotency_record WHERE expires_at < #{before}")
    int deleteExpired(@Param("before") Instant before);
}
