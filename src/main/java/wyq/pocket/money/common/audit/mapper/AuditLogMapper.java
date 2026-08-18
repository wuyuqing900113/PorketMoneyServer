package wyq.pocket.money.common.audit.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审计日志 Mapper（M1 设计 §9.1）。
 *
 * <p>仅写入，不提供查询（M1 审计查询走 SQL 直查 + DBA 通道）；
 * 参数化占位符（#{}）强制，禁止拼接。
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 写入一条审计记录（created_at 由库默认 now() 填充）。
     *
     * @param userId     用户 ID，可空
     * @param action     审计动作名
     * @param targetType 目标对象类型，可空
     * @param targetId   目标对象 ID，可空
     * @param detail     脱敏后的结构化补充信息，可空
     * @param clientIp   客户端 IP（forward-headers 还原后），可空
     * @param traceId    链路追踪 ID，可空
     * @return 影响行数
     */
    @Insert("INSERT INTO audit_log (user_id, action, target_type, target_id, "
            + "detail, client_ip, trace_id) "
            + "VALUES (#{userId}, #{action}, #{targetType}, #{targetId}, "
            // detail 列为 JSONB：JDBC setString 绑定的是 varchar，PostgreSQL 拒绝
            // 隐式 varchar→jsonb 赋值（除非 URL 加 stringtype=unspecified），
            // 显式 CAST 在 PG 与 H2 PostgreSQL 模式下均可行
            + "CAST(#{detail} AS jsonb), #{clientIp}, #{traceId})")
    int insert(@Param("userId") Long userId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("targetId") String targetId,
            @Param("detail") String detail, @Param("clientIp") String clientIp,
            @Param("traceId") String traceId);
}
