package wyq.pocket.money.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.user.domain.User;
import wyq.pocket.money.user.dto.ChangePasswordRequest;
import wyq.pocket.money.user.dto.UpdateNicknameRequest;
import wyq.pocket.money.user.dto.UserMeResponse;
import wyq.pocket.money.user.mapper.FamilyMemberMapper;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * UserService 单元测试（M1 设计 §5.4 / §5.5 / §12.1）：
 * 手机号脱敏回显、改密吊销与审计。BCrypt 测试用 strength=4 提速。
 */
class UserServiceTest {

    private static final String PASSWORD = "Passw0rd!";

    private final UserMapper userMapper = mock(UserMapper.class);

    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private final UserService service = new UserService(userMapper, passwordEncoder,
            refreshTokenService, auditService, familyMemberMapper);

    private final UserIdPrincipal parentPrincipal =
            new UserIdPrincipal(7L, 5L, User.ROLE_PARENT, false);

    private User parent() {
        User user = new User();
        user.setId(7L);
        user.setNickname("妈妈");
        user.setRole(User.ROLE_PARENT);
        user.setStatus(User.STATUS_ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return user;
    }

    private int codeOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getErrorCode().getCode();
    }

    @Test
    void getMeShouldReturnMaskedPhoneForParent() {
        User user = parent();
        user.setPhoneEncrypted("13800001234");
        when(userMapper.findById(7L)).thenReturn(user);

        UserMeResponse response = service.getMe(parentPrincipal);

        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.familyId()).isEqualTo(5L);
        assertThat(response.maskedPhone()).isEqualTo("138****1234");
    }

    @Test
    void getMeShouldReturnNullPhoneForChild() {
        User child = new User();
        child.setId(9L);
        child.setNickname("小明");
        child.setRole(User.ROLE_CHILD);
        UserIdPrincipal childPrincipal = new UserIdPrincipal(9L, 5L, User.ROLE_CHILD, true);
        when(userMapper.findById(9L)).thenReturn(child);

        UserMeResponse response = service.getMe(childPrincipal);

        assertThat(response.maskedPhone()).isNull();
        assertThat(response.role()).isEqualTo(User.ROLE_CHILD);
    }

    @Test
    void getMeUnknownUserShouldThrow100005() {
        when(userMapper.findById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.getMe(parentPrincipal))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(100005));
    }

    @Test
    void updateNicknameShouldDelegateToMapper() {
        when(userMapper.findById(7L)).thenReturn(parent());

        service.updateNickname(parentPrincipal, new UpdateNicknameRequest("新昵称"));

        verify(userMapper).updateNickname(7L, "新昵称");
    }

    @Test
    void updateNicknameUnknownUserShouldThrow100005() {
        when(userMapper.findById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateNickname(parentPrincipal,
                new UpdateNicknameRequest("新昵称")))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(100005));
        verify(userMapper, never()).updateNickname(anyLong(), any());
    }

    @Test
    void changePasswordWithWrongOldShouldThrow200008() {
        when(userMapper.findById(7L)).thenReturn(parent());

        assertThatThrownBy(() -> service.changePassword(parentPrincipal,
                new ChangePasswordRequest("WrongPass1", "NewPassw0rd!")))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(200008));
        verify(userMapper, never()).updatePassword(anyLong(), any(), anyBoolean());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePasswordShouldUpdateHashRevokeTokensAndAudit() {
        when(userMapper.findById(7L)).thenReturn(parent());

        service.changePassword(parentPrincipal,
                new ChangePasswordRequest(PASSWORD, "NewPassw0rd!"));

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePassword(eq(7L), hashCaptor.capture(), eq(false));
        assertThat(passwordEncoder.matches("NewPassw0rd!", hashCaptor.getValue())).isTrue();
        verify(refreshTokenService).revokeAll(7L);
        verify(auditService).record(new AuditEntry(7L, AuditAction.PASSWORD_CHANGE,
                "USER", "7", null));
    }

    @Test
    void changePasswordUnknownUserShouldThrow100005() {
        when(userMapper.findById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.changePassword(parentPrincipal,
                new ChangePasswordRequest(PASSWORD, "NewPassw0rd!")))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(100005));
    }
}
