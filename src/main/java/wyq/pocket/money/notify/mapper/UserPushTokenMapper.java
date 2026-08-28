package wyq.pocket.money.notify.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.notify.domain.UserPushToken;

/**
 * 用户外部推送设备令牌 Mapper（user_push_token，V10 / GA D68）。
 *
 * <p>一人一渠道一条（UNIQUE(user_id, provider)）：注册走「插入忽略冲突 +
 * 冲突则更新」两步，与 money_account 惰性开户同一模式，H2 / PostgreSQL 均兼容；
 * 参数一律 #{} 占位。
 */
@Mapper
public interface UserPushTokenMapper {

    /**
     * 插入令牌：(user_id, provider) 冲突时忽略（重复注册改走 {@link #updateToken}）。
     *
     * @param userId   用户 ID
     * @param provider 推送渠道
     * @param token    设备令牌
     * @return 影响行数（0 = 已存在，需改更新）
     */
    @Insert("INSERT INTO user_push_token (user_id, provider, token) "
            + "VALUES (#{userId}, #{provider}, #{token}) ON CONFLICT DO NOTHING")
    int insertIgnoreConflict(@Param("userId") long userId, @Param("provider") String provider,
                             @Param("token") String token);

    /**
     * 更新令牌（重复注册覆盖，并重新启用）。
     *
     * @param userId   用户 ID
     * @param provider 推送渠道
     * @param token    新设备令牌
     * @return 影响行数
     */
    @Update("UPDATE user_push_token SET token = #{token}, enabled = TRUE, updated_at = now() "
            + "WHERE user_id = #{userId} AND provider = #{provider}")
    int updateToken(@Param("userId") long userId, @Param("provider") String provider,
                    @Param("token") String token);

    /**
     * 按用户与渠道查询启用中的令牌。
     *
     * @param userId   用户 ID
     * @param provider 推送渠道
     * @return 令牌记录，不存在返回 null
     */
    @Select("SELECT id, user_id, provider, token, enabled, created_at, updated_at "
            + "FROM user_push_token WHERE user_id = #{userId} AND provider = #{provider}")
    UserPushToken findByUserAndProvider(@Param("userId") long userId,
                                        @Param("provider") String provider);
}
