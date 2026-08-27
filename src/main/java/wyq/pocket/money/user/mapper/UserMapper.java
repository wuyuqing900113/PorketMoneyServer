package wyq.pocket.money.user.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.common.crypto.EncryptedFieldTypeHandler;
import wyq.pocket.money.user.domain.User;

/**
 * 用户 Mapper（app_user，M1 设计 §7.1）。
 *
 * <p>{@code phone_encrypted} 列写入 / 读取均经 EncryptedFieldTypeHandler
 * （@Component bean，含 DataEncryptor）：写时 AES-256-GCM 加密、
 * 读时解密；显式 typeHandler 引用绕过全局字符串映射（T3 字节码验证）。
 * 参数一律 #{} 占位（mission 安全约束）。
 */
@Mapper
public interface UserMapper {

    /** 查询列清单（与 app_user 列一一对应，供结果映射复用）。 */
    String USER_COLUMNS = "id, username, phone_hash, phone_encrypted, key_version, password_hash, "
            + "nickname, role, status, must_change_password, consented_at, consented_by, "
            + "failed_attempts, locked_until, created_at, updated_at";

    /**
     * 插入用户（家长注册 / 家长创建孩子共用）。
     *
     * @param user 用户（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO app_user (username, phone_hash, phone_encrypted, password_hash, nickname, "
            + "role, status, must_change_password, consented_by) VALUES ("
            + "#{username}, #{phoneHash}, "
            + "#{phoneEncrypted,typeHandler=wyq.pocket.money.common.crypto.EncryptedFieldTypeHandler}, "
            + "#{passwordHash}, #{nickname}, #{role}, #{status}, #{mustChangePassword}, #{consentedBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /**
     * 按 ID 查询。
     *
     * @param id 用户 ID
     * @return 用户，不存在返回 null
     */
    @Select("SELECT " + USER_COLUMNS + " FROM app_user WHERE id = #{id}")
    @Results(id = "userResultMap", value = {
        @Result(column = "phone_encrypted", property = "phoneEncrypted",
                typeHandler = EncryptedFieldTypeHandler.class)
    })
    User findById(@Param("id") long id);

    /**
     * 按手机号哈希查询（家长登录）。
     *
     * @param phoneHash SHA-256 十六进制哈希
     * @return 用户，不存在返回 null
     */
    @Select("SELECT " + USER_COLUMNS + " FROM app_user WHERE phone_hash = #{phoneHash}")
    @ResultMap("userResultMap")
    User findByPhoneHash(@Param("phoneHash") String phoneHash);

    /**
     * 按登录名查询（孩子登录）。
     *
     * @param username 登录名
     * @return 用户，不存在返回 null
     */
    @Select("SELECT " + USER_COLUMNS + " FROM app_user WHERE username = #{username}")
    @ResultMap("userResultMap")
    User findByUsername(@Param("username") String username);

    /**
     * 修改密码并重置首次改密强制位（改密后 false）。
     *
     * @param id                 用户 ID
     * @param passwordHash       新密码哈希
     * @param mustChangePassword 首次改密强制位
     * @return 影响行数
     */
    @Update("UPDATE app_user SET password_hash = #{passwordHash}, "
            + "must_change_password = #{mustChangePassword}, updated_at = now() WHERE id = #{id}")
    int updatePassword(@Param("id") long id, @Param("passwordHash") String passwordHash,
                       @Param("mustChangePassword") boolean mustChangePassword);

    /**
     * 更新登录失败计数与锁定状态（锁定生效时计数归零，§4.5）。
     *
     * @param id             用户 ID
     * @param failedAttempts 失败次数
     * @param lockedUntil    锁定截止时间，解锁传 null
     * @return 影响行数
     */
    @Update("UPDATE app_user SET failed_attempts = #{failedAttempts}, "
            + "locked_until = #{lockedUntil}, updated_at = now() WHERE id = #{id}")
    int updateLoginState(@Param("id") long id, @Param("failedAttempts") int failedAttempts,
                         @Param("lockedUntil") Instant lockedUntil);

    /**
     * 修改昵称。
     *
     * @param id       用户 ID
     * @param nickname 新昵称
     * @return 影响行数
     */
    @Update("UPDATE app_user SET nickname = #{nickname}, updated_at = now() WHERE id = #{id}")
    int updateNickname(@Param("id") long id, @Param("nickname") String nickname);

    /**
     * 修改账号状态（孩子被移出家庭置 DISABLED，§6.4）。
     *
     * @param id     用户 ID
     * @param status 状态
     * @return 影响行数
     */
    @Update("UPDATE app_user SET status = #{status}, updated_at = now() WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);

    /**
     * 批量查询昵称（M2 榜单 / 报表昵称回显）。
     *
     * <p>仅回填 id / nickname 两列；调用方须保证 ids 非空（空集合 SQL 非法）。
     *
     * @param ids 用户 ID 集合
     * @return 用户列表（仅 id、nickname 字段有值）
     */
    @Select("<script>SELECT id, nickname FROM app_user WHERE id IN "
            + "<foreach collection='ids' item='item' open='(' separator=',' close=')'>"
            + "#{item}</foreach></script>")
    List<User> findNicknamesByIds(@Param("ids") Collection<Long> ids);
}
