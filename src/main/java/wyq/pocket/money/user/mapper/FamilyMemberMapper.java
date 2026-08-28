package wyq.pocket.money.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import wyq.pocket.money.user.domain.FamilyMember;
import wyq.pocket.money.user.dto.MemberSummary;

/**
 * 家庭成员关系 Mapper（family_member，M1 设计 §7.1）。
 */
@Mapper
public interface FamilyMemberMapper {

    /**
     * 插入成员关系（注册同事务插入创建者，§5.1）。
     *
     * @param member 成员关系（id 由 BIGSERIAL 回填）
     * @return 影响行数
     */
    @Insert("INSERT INTO family_member (family_id, user_id) "
            + "VALUES (#{familyId}, #{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FamilyMember member);

    /**
     * 查询用户所属家庭 ID（M1 一人一家庭，至多一行）。
     *
     * @param userId 用户 ID
     * @return 家庭 ID，无成员关系返回 null
     */
    @Select("SELECT family_id FROM family_member WHERE user_id = #{userId}")
    Long findFamilyIdByUserId(@Param("userId") long userId);

    /**
     * 统计家庭成员数（上限 8，§6.1）。
     *
     * @param familyId 家庭 ID
     * @return 成员数
     */
    @Select("SELECT COUNT(*) FROM family_member WHERE family_id = #{familyId}")
    int countByFamilyId(@Param("familyId") long familyId);

    /**
     * 查询家庭成员摘要列表（联 app_user 取昵称与角色，§6.4 全员可见）。
     *
     * <p>record 无默认构造器，显式 @ConstructorArgs 按列映射，
     * 不依赖编译参数名保留。
     *
     * @param familyId 家庭 ID
     * @return 成员摘要（按加入顺序）
     */
    @Select("SELECT u.id AS user_id, u.nickname, u.role FROM family_member fm "
            + "JOIN app_user u ON u.id = fm.user_id "
            + "WHERE fm.family_id = #{familyId} ORDER BY fm.id")
    @ConstructorArgs({
        @Arg(column = "user_id", javaType = long.class),
        @Arg(column = "nickname", javaType = String.class),
        @Arg(column = "role", javaType = String.class)
    })
    List<MemberSummary> findMembersByFamilyId(@Param("familyId") long familyId);

    /**
     * 查询家庭全部家长用户 ID（M5 §5.3）：join app_user 过滤 role=PARENT。
     *
     * @param familyId 家庭 ID
     * @return 家长用户 ID 列表（按成员加入顺序）
     */
    @Select("SELECT u.id FROM family_member fm JOIN app_user u ON u.id = fm.user_id "
            + "WHERE fm.family_id = #{familyId} AND u.role = 'PARENT' ORDER BY fm.id")
    List<Long> findParentUserIdsByFamilyId(@Param("familyId") long familyId);

    /**
     * 删除成员关系（移除孩子，§6.4）。
     *
     * @param familyId 家庭 ID
     * @param userId   用户 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM family_member WHERE family_id = #{familyId} "
            + "AND user_id = #{userId}")
    int deleteByFamilyIdAndUserId(@Param("familyId") long familyId,
                                  @Param("userId") long userId);
}
