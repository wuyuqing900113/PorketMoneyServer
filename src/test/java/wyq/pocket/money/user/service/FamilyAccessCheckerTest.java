package wyq.pocket.money.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import wyq.pocket.money.user.mapper.FamilyMemberMapper;

/**
 * FamilyAccessChecker 单元测试（M1 设计 §4.6、附录 B）：
 * 在册成员放行；跨家庭 / 无成员关系（含被移除后）一律 AccessDeniedException，
 * 由全局异常处理器转 403 + 100004。
 */
class FamilyAccessCheckerTest {

    private final FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);

    private final FamilyAccessChecker checker = new FamilyAccessChecker(familyMemberMapper);

    @Test
    void memberOfTargetFamilyShouldPass() {
        when(familyMemberMapper.findFamilyIdByUserId(7L)).thenReturn(3L);

        assertThatCode(() -> checker.requireMember(3L, 7L)).doesNotThrowAnyException();
    }

    @Test
    void memberOfOtherFamilyShouldBeDenied() {
        when(familyMemberMapper.findFamilyIdByUserId(7L)).thenReturn(99L);

        assertThatThrownBy(() -> checker.requireMember(3L, 7L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("FAMILY_ACCESS_DENIED");
    }

    @Test
    void userWithoutMembershipShouldBeDenied() {
        when(familyMemberMapper.findFamilyIdByUserId(7L)).thenReturn(null);

        assertThatThrownBy(() -> checker.requireMember(3L, 7L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
