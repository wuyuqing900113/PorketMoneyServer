package wyq.pocket.money.user.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import wyq.pocket.money.user.mapper.FamilyMemberMapper;

/**
 * 家庭域数据级访问守卫（M1 设计 §4.6、附录 B）。
 *
 * <p>以 family_member 表为准断言「当前用户属于目标家庭」：JWT fam 声明
 * 仅是登录时刻的投影，成员被移除后可能滞后，故家庭域请求一律以库内
 * 成员关系为准（§6.4 移除即失权）。拒绝抛 Spring Security 的
 * {@link AccessDeniedException}，由全局异常处理器统一转为 HTTP 403 +
 * Result(100004) 并落安全日志，与接口级方法安全拒绝同码同出口（§10.1）。
 */
@Component
public class FamilyAccessChecker {

    private final FamilyMemberMapper familyMemberMapper;

    /**
     * 注入成员关系 Mapper。
     *
     * @param familyMemberMapper 成员关系 Mapper
     */
    public FamilyAccessChecker(FamilyMemberMapper familyMemberMapper) {
        this.familyMemberMapper = familyMemberMapper;
    }

    /**
     * 断言用户是目标家庭的在册成员。
     *
     * @param familyId 目标家庭 ID
     * @param userId   当前用户 ID
     * @throws AccessDeniedException 无成员关系或隶属其他家庭（403 + 100004）
     */
    public void requireMember(long familyId, long userId) {
        Long actualFamilyId = familyMemberMapper.findFamilyIdByUserId(userId);
        if (actualFamilyId == null || actualFamilyId != familyId) {
            throw new AccessDeniedException(
                    "FAMILY_ACCESS_DENIED user=" + userId + " targetFamily=" + familyId);
        }
    }
}
