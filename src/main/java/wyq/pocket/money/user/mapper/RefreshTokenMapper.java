package wyq.pocket.money.user.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.user.domain.RefreshToken;

/**
 * refresh 令牌 Mapper（user_refresh_token，M1 设计 §4.3 / §4.4）。
 *
 * <p>仅存 SHA-256 哈希；吊销为软删除（revoked_at），保留审计线索。
 */
@Mapper
public interface RefreshTokenMapper {

    /**
     * 插入令牌记录。
     *
     * @param refreshToken 令牌记录（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO user_refresh_token (user_id, token_hash, expires_at) "
            + "VALUES (#{userId}, #{tokenHash}, #{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RefreshToken refreshToken);

    /**
     * 按令牌哈希查询。
     *
     * @param tokenHash SHA-256 十六进制哈希
     * @return 令牌记录，不存在返回 null
     */
    @Select("SELECT id, user_id, token_hash, expires_at, revoked_at, created_at "
            + "FROM user_refresh_token WHERE token_hash = #{tokenHash}")
    RefreshToken findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 吊销单个令牌（登出 / 轮转；已吊销行不重复置位）。
     *
     * @param tokenHash 令牌哈希
     * @param revokedAt 吊销时间
     * @return 影响行数（0 表示不存在或已吊销）
     */
    @Update("UPDATE user_refresh_token SET revoked_at = #{revokedAt} "
            + "WHERE token_hash = #{tokenHash} AND revoked_at IS NULL")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash,
                          @Param("revokedAt") Instant revokedAt);

    /**
     * 吊销用户全部未吊销令牌（改密 / 移出家庭 / 重用检测，§4.3）。
     *
     * @param userId    用户 ID
     * @param revokedAt 吊销时间
     * @return 影响行数
     */
    @Update("UPDATE user_refresh_token SET revoked_at = #{revokedAt} "
            + "WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllByUserId(@Param("userId") long userId, @Param("revokedAt") Instant revokedAt);
}
