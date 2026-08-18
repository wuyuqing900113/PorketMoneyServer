package wyq.pocket.money.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import wyq.pocket.money.user.domain.Family;

/**
 * 家庭 Mapper（family，M1 设计 §7.1）。
 */
@Mapper
public interface FamilyMapper {

    /**
     * 插入家庭（注册同事务创建，§5.1）。
     *
     * @param family 家庭（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO family (family_name, owner_user_id) "
            + "VALUES (#{familyName}, #{ownerUserId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Family family);

    /**
     * 按 ID 查询。
     *
     * @param id 家庭 ID
     * @return 家庭，不存在返回 null
     */
    @Select("SELECT id, family_name, owner_user_id, created_at, updated_at "
            + "FROM family WHERE id = #{id}")
    Family findById(@Param("id") long id);

    /**
     * 修改家庭名（§6.2，仅家长调用，由 service 层把关）。
     *
     * @param id         家庭 ID
     * @param familyName 新家庭名（≤32 字）
     * @return 影响行数
     */
    @Update("UPDATE family SET family_name = #{familyName}, updated_at = now() "
            + "WHERE id = #{id}")
    int updateFamilyName(@Param("id") long id, @Param("familyName") String familyName);
}
