package wyq.pocket.money.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.user.domain.Family;
import wyq.pocket.money.user.domain.FamilyMember;
import wyq.pocket.money.user.domain.User;
import wyq.pocket.money.user.dto.AddChildRequest;
import wyq.pocket.money.user.dto.ChildCreateResponse;
import wyq.pocket.money.user.dto.FamilyDetailResponse;
import wyq.pocket.money.user.dto.MemberSummary;
import wyq.pocket.money.user.dto.ResetChildPasswordRequest;
import wyq.pocket.money.user.dto.UpdateFamilyRequest;
import wyq.pocket.money.user.dto.UpdateNicknameRequest;
import wyq.pocket.money.user.event.MemberRemovedEvent;
import wyq.pocket.money.user.mapper.FamilyMapper;
import wyq.pocket.money.user.mapper.FamilyMemberMapper;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * FamilyService 单元测试（M1 设计 §6 / §12.1）：家庭读取与改名、
 * 创建孩子（上限 200006 / 登录名占用 200007 / 留痕字段）、孩子信息修改与
 * 密码重置（200011 边界、mcp 重置、会话吊销）、移除成员（200012 创建者保护、
 * 200011 非成员、删关系 + 吊销 + DISABLED + 审计）。
 */
class FamilyServiceTest {

    private static final UserIdPrincipal PARENT = new UserIdPrincipal(1L, 10L, "PARENT", false);

    private static final String CHILD_USERNAME = "xiaoming";

    private final FamilyMapper familyMapper = mock(FamilyMapper.class);

    private final FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);

    private final UserMapper userMapper = mock(UserMapper.class);

    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);

    private final FamilyService service = new FamilyService(familyMapper, familyMemberMapper,
            userMapper, passwordEncoder, refreshTokenService, auditService, familyAccessChecker,
            eventPublisher);

    private Family family() {
        Family family = new Family("小明的家庭", 1L);
        family.setId(10L);
        return family;
    }

    private User childUser() {
        User child = new User();
        child.setId(42L);
        child.setUsername(CHILD_USERNAME);
        child.setNickname("小明");
        child.setRole(User.ROLE_CHILD);
        child.setStatus(User.STATUS_ACTIVE);
        return child;
    }

    private AddChildRequest addChildRequest() {
        return new AddChildRequest(CHILD_USERNAME, "Init1234", "小明");
    }

    private void expectCode(Throwable thrown, int code) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode().getCode()).isEqualTo(code);
    }

    @Test
    void getFamilyShouldReturnDetailWithMembers() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.findMembersByFamilyId(10L))
                .thenReturn(List.of(new MemberSummary(1L, "爸爸", "PARENT")));

        FamilyDetailResponse response = service.getFamily(10L, PARENT);

        assertThat(response.familyId()).isEqualTo(10L);
        assertThat(response.familyName()).isEqualTo("小明的家庭");
        assertThat(response.ownerUserId()).isEqualTo(1L);
        assertThat(response.members()).hasSize(1);
        verify(familyAccessChecker).requireMember(10L, 1L);
    }

    @Test
    void getMyFamilyShouldUsePrincipalFamilyId() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.findMembersByFamilyId(10L)).thenReturn(List.of());

        FamilyDetailResponse response = service.getMyFamily(PARENT);

        assertThat(response.familyId()).isEqualTo(10L);
    }

    @Test
    void getFamilyShouldThrow200005WhenFamilyMissing() {
        when(familyMapper.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.getFamily(10L, PARENT))
                .satisfies(thrown -> expectCode(thrown, 200005));
    }

    @Test
    void getFamilyShouldPropagateAccessDeniedWithoutTouchingDb() {
        doThrow(new AccessDeniedException("FAMILY_ACCESS_DENIED user=1 targetFamily=10"))
                .when(familyAccessChecker).requireMember(10L, 1L);

        assertThatThrownBy(() -> service.getFamily(10L, PARENT))
                .isInstanceOf(AccessDeniedException.class);
        verify(familyMapper, never()).findById(anyLong());
    }

    @Test
    void listMembersShouldReturnMemberSummaries() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.findMembersByFamilyId(10L))
                .thenReturn(List.of(new MemberSummary(1L, "爸爸", "PARENT"),
                        new MemberSummary(42L, "小明", "CHILD")));

        List<MemberSummary> members = service.listMembers(10L, PARENT);

        assertThat(members).hasSize(2);
    }

    @Test
    void updateFamilyShouldPersistNameAndAudit() {
        when(familyMapper.findById(10L)).thenReturn(family());

        service.updateFamily(10L, PARENT, new UpdateFamilyRequest("新家庭名"));

        verify(familyMapper).updateFamilyName(10L, "新家庭名");
        verify(auditService).record(
                new AuditEntry(1L, AuditAction.FAMILY_UPDATE, "FAMILY", "10", null));
    }

    @Test
    void updateFamilyShouldThrow200005WhenFamilyMissing() {
        when(familyMapper.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateFamily(10L, PARENT,
                new UpdateFamilyRequest("新家庭名")))
                .satisfies(thrown -> expectCode(thrown, 200005));
        verify(familyMapper, never()).updateFamilyName(anyLong(), any());
    }

    @Test
    void addChildShouldInsertChildMembershipAndAudit() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.countByFamilyId(10L)).thenReturn(1);
        when(userMapper.findByUsername(CHILD_USERNAME)).thenReturn(null);
        when(passwordEncoder.encode("Init1234")).thenReturn("$2b$child");
        doAnswer(invocation -> {
            invocation.getArgument(0, User.class).setId(42L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        ChildCreateResponse response = service.addChild(10L, PARENT, addChildRequest());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User child = userCaptor.getValue();
        assertThat(child.getUsername()).isEqualTo(CHILD_USERNAME);
        assertThat(child.getPasswordHash()).isEqualTo("$2b$child");
        assertThat(child.getRole()).isEqualTo(User.ROLE_CHILD);
        assertThat(child.getStatus()).isEqualTo(User.STATUS_ACTIVE);
        assertThat(child.isMustChangePassword()).isTrue();
        assertThat(child.getConsentedBy()).isEqualTo(1L);
        assertThat(child.getPhoneHash()).isNull();
        assertThat(child.getPhoneEncrypted()).isNull();

        ArgumentCaptor<FamilyMember> memberCaptor = ArgumentCaptor.forClass(FamilyMember.class);
        verify(familyMemberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getFamilyId()).isEqualTo(10L);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(42L);

        verify(auditService).record(
                new AuditEntry(1L, AuditAction.CHILD_CREATE, "USER", "42", null));
        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.username()).isEqualTo(CHILD_USERNAME);
        assertThat(response.role()).isEqualTo(User.ROLE_CHILD);
        assertThat(response.mustChangePassword()).isTrue();
    }

    @Test
    void addChildShouldThrow200005WhenFamilyMissing() {
        when(familyMapper.findById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.addChild(10L, PARENT, addChildRequest()))
                .satisfies(thrown -> expectCode(thrown, 200005));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void addChildShouldThrow200006WhenMemberLimitReached() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.countByFamilyId(10L)).thenReturn(8);

        assertThatThrownBy(() -> service.addChild(10L, PARENT, addChildRequest()))
                .satisfies(thrown -> expectCode(thrown, 200006));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void addChildShouldThrow200007WhenUsernameTaken() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.countByFamilyId(10L)).thenReturn(1);
        when(userMapper.findByUsername(CHILD_USERNAME)).thenReturn(new User());

        assertThatThrownBy(() -> service.addChild(10L, PARENT, addChildRequest()))
                .satisfies(thrown -> expectCode(thrown, 200007));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void updateChildShouldPersistNicknameAndAudit() {
        when(userMapper.findById(42L)).thenReturn(childUser());
        when(familyMemberMapper.findFamilyIdByUserId(42L)).thenReturn(10L);

        service.updateChild(10L, 42L, PARENT, new UpdateNicknameRequest("明明"));

        verify(userMapper).updateNickname(42L, "明明");
        verify(auditService).record(
                new AuditEntry(1L, AuditAction.CHILD_UPDATE, "USER", "42", null));
    }

    @Test
    void updateChildShouldThrow200011WhenTargetMissing() {
        when(userMapper.findById(42L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateChild(10L, 42L, PARENT,
                new UpdateNicknameRequest("明明")))
                .satisfies(thrown -> expectCode(thrown, 200011));
        verify(userMapper, never()).updateNickname(anyLong(), any());
    }

    @Test
    void updateChildShouldThrow200011WhenTargetIsParent() {
        User otherParent = new User();
        otherParent.setId(42L);
        otherParent.setRole(User.ROLE_PARENT);
        when(userMapper.findById(42L)).thenReturn(otherParent);

        assertThatThrownBy(() -> service.updateChild(10L, 42L, PARENT,
                new UpdateNicknameRequest("明明")))
                .satisfies(thrown -> expectCode(thrown, 200011));
        verify(userMapper, never()).updateNickname(anyLong(), any());
    }

    @Test
    void updateChildShouldThrow200011WhenChildInOtherFamily() {
        when(userMapper.findById(42L)).thenReturn(childUser());
        when(familyMemberMapper.findFamilyIdByUserId(42L)).thenReturn(99L);

        assertThatThrownBy(() -> service.updateChild(10L, 42L, PARENT,
                new UpdateNicknameRequest("明明")))
                .satisfies(thrown -> expectCode(thrown, 200011));
        verify(userMapper, never()).updateNickname(anyLong(), any());
    }

    @Test
    void resetChildPasswordShouldEncodeRevokeAndAudit() {
        when(userMapper.findById(42L)).thenReturn(childUser());
        when(familyMemberMapper.findFamilyIdByUserId(42L)).thenReturn(10L);
        when(passwordEncoder.encode("Reset1234")).thenReturn("$2b$reset");

        service.resetChildPassword(10L, 42L, PARENT,
                new ResetChildPasswordRequest("Reset1234"));

        verify(userMapper).updatePassword(42L, "$2b$reset", true);
        verify(refreshTokenService).revokeAll(42L);
        verify(auditService).record(
                new AuditEntry(1L, AuditAction.CHILD_PASSWORD_RESET, "USER", "42", null));
    }

    @Test
    void resetChildPasswordShouldThrow200011WhenTargetNotInFamily() {
        when(userMapper.findById(42L)).thenReturn(childUser());
        when(familyMemberMapper.findFamilyIdByUserId(42L)).thenReturn(null);

        assertThatThrownBy(() -> service.resetChildPassword(10L, 42L, PARENT,
                new ResetChildPasswordRequest("Reset1234")))
                .satisfies(thrown -> expectCode(thrown, 200011));
        verify(userMapper, never()).updatePassword(anyLong(), any(), eq(true));
        verify(refreshTokenService, never()).revokeAll(anyLong());
    }

    @Test
    void removeMemberShouldThrow200012WhenRemovingOwner() {
        when(familyMapper.findById(10L)).thenReturn(family());

        assertThatThrownBy(() -> service.removeMember(10L, 1L, PARENT))
                .satisfies(thrown -> expectCode(thrown, 200012));
        verify(familyMemberMapper, never()).deleteByFamilyIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void removeMemberShouldThrow200011WhenTargetNotMember() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.findFamilyIdByUserId(42L)).thenReturn(null);

        assertThatThrownBy(() -> service.removeMember(10L, 42L, PARENT))
                .satisfies(thrown -> expectCode(thrown, 200011));
        verify(familyMemberMapper, never()).deleteByFamilyIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void removeMemberShouldThrow200011WhenTargetInOtherFamily() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.findFamilyIdByUserId(42L)).thenReturn(99L);

        assertThatThrownBy(() -> service.removeMember(10L, 42L, PARENT))
                .satisfies(thrown -> expectCode(thrown, 200011));
        verify(familyMemberMapper, never()).deleteByFamilyIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void removeMemberShouldDeleteRevokeDisableAndAudit() {
        when(familyMapper.findById(10L)).thenReturn(family());
        when(familyMemberMapper.findFamilyIdByUserId(42L)).thenReturn(10L);

        service.removeMember(10L, 42L, PARENT);

        verify(familyMemberMapper).deleteByFamilyIdAndUserId(10L, 42L);
        verify(refreshTokenService).revokeAll(42L);
        verify(userMapper).updateStatus(42L, User.STATUS_DISABLED);
        verify(auditService).record(
                new AuditEntry(1L, AuditAction.MEMBER_REMOVE, "USER", "42", null));
        // M2 §7.4：移除成员发布领域事件，触发资金 / 规则联动
        verify(eventPublisher).publishEvent(new MemberRemovedEvent(10L, 42L));
    }
}
