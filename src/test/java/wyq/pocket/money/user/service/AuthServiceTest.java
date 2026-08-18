package wyq.pocket.money.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.crypto.Hashes;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.user.domain.Family;
import wyq.pocket.money.user.domain.FamilyMember;
import wyq.pocket.money.user.domain.User;
import wyq.pocket.money.user.dto.LoginRequest;
import wyq.pocket.money.user.dto.LoginResponse;
import wyq.pocket.money.user.dto.LogoutRequest;
import wyq.pocket.money.user.dto.RefreshRequest;
import wyq.pocket.money.user.dto.RegisterRequest;
import wyq.pocket.money.user.dto.RegisterResponse;
import wyq.pocket.money.user.dto.TokenPairResponse;
import wyq.pocket.money.user.mapper.FamilyMapper;
import wyq.pocket.money.user.mapper.FamilyMemberMapper;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * AuthService 单元测试（M1 设计 §5.1–§5.3 / §12.1，mock Mapper）：
 * 注册同事务装配、登录分支（防枚举 / 锁定 / 停用）、登出吊销。
 * BCrypt 测试用 strength=4 提速，逻辑等价。
 */
class AuthServiceTest {

    private static final String PHONE = "13800001234";

    private static final String PASSWORD = "Passw0rd!";

    private final UserMapper userMapper = mock(UserMapper.class);

    private final FamilyMapper familyMapper = mock(FamilyMapper.class);

    private final FamilyMemberMapper memberMapper = mock(FamilyMemberMapper.class);

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

    private final LoginGuardService loginGuardService = mock(LoginGuardService.class);

    private final AuditService auditService = mock(AuditService.class);

    private final AuthService service = new AuthService(userMapper, familyMapper, memberMapper,
            passwordEncoder, refreshTokenService, loginGuardService, auditService);

    private RegisterRequest registerRequest() {
        return new RegisterRequest(PHONE, PASSWORD, "妈妈", true);
    }

    private User parent(long id) {
        User user = new User();
        user.setId(id);
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
    void registerShouldInsertUserFamilyMemberAndAudit() {
        when(userMapper.findByPhoneHash(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, User.class).setId(11L);
            return 1;
        });
        when(familyMapper.insert(any(Family.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Family.class).setId(21L);
            return 1;
        });

        RegisterResponse response = service.register(registerRequest());

        assertThat(response).isEqualTo(new RegisterResponse(11L, 21L, "妈妈", User.ROLE_PARENT));
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User inserted = userCaptor.getValue();
        assertThat(inserted.getPhoneHash()).isEqualTo(Hashes.sha256Hex(PHONE));
        assertThat(inserted.getPhoneEncrypted()).isEqualTo(PHONE);
        assertThat(passwordEncoder.matches(PASSWORD, inserted.getPasswordHash())).isTrue();
        assertThat(inserted.getRole()).isEqualTo(User.ROLE_PARENT);
        assertThat(inserted.getStatus()).isEqualTo(User.STATUS_ACTIVE);
        assertThat(inserted.isMustChangePassword()).isFalse();
        ArgumentCaptor<Family> familyCaptor = ArgumentCaptor.forClass(Family.class);
        verify(familyMapper).insert(familyCaptor.capture());
        assertThat(familyCaptor.getValue().getFamilyName()).isEqualTo("妈妈的家庭");
        assertThat(familyCaptor.getValue().getOwnerUserId()).isEqualTo(11L);
        ArgumentCaptor<FamilyMember> memberCaptor = ArgumentCaptor.forClass(FamilyMember.class);
        verify(memberMapper).insert(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getFamilyId()).isEqualTo(21L);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(11L);
        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService, times(2)).record(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues()).map(AuditEntry::action)
                .containsExactly(AuditAction.REGISTER, AuditAction.FAMILY_CREATE);
    }

    @Test
    void registerWithLongNicknameShouldTruncateFamilyNameTo32() {
        when(userMapper.findByPhoneHash(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, User.class).setId(11L);
            return 1;
        });
        when(familyMapper.insert(any(Family.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Family.class).setId(21L);
            return 1;
        });

        service.register(new RegisterRequest(PHONE, PASSWORD, "a".repeat(32), true));

        ArgumentCaptor<Family> familyCaptor = ArgumentCaptor.forClass(Family.class);
        verify(familyMapper).insert(familyCaptor.capture());
        assertThat(familyCaptor.getValue().getFamilyName()).hasSize(32);
    }

    @Test
    void registerDuplicatePhoneShouldThrow200001() {
        when(userMapper.findByPhoneHash(Hashes.sha256Hex(PHONE))).thenReturn(new User());

        assertThatThrownBy(() -> service.register(registerRequest()))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(200001));
        verify(userMapper, never()).insert(any());
    }

    @Test
    void loginByPhoneShouldReturnTokenPairAndAuditSuccess() {
        User stored = parent(7L);
        when(userMapper.findByPhoneHash(Hashes.sha256Hex(PHONE))).thenReturn(stored);
        when(loginGuardService.isLocked(stored)).thenReturn(false);
        when(memberMapper.findFamilyIdByUserId(7L)).thenReturn(5L);
        when(refreshTokenService.issue(stored, 5L))
                .thenReturn(new TokenPairResponse("at", "rt", 900L, false));

        LoginResponse response = service.login(new LoginRequest(PHONE, PASSWORD));

        assertThat(response.accessToken()).isEqualTo("at");
        assertThat(response.refreshToken()).isEqualTo("rt");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.mustChangePassword()).isFalse();
        assertThat(response.user()).isEqualTo(
                new LoginResponse.UserSummary(7L, "妈妈", User.ROLE_PARENT, 5L));
        verify(loginGuardService).resetAfterSuccess(stored);
        verify(auditService).record(new AuditEntry(7L, AuditAction.LOGIN_SUCCESS,
                "USER", "7", null));
    }

    @Test
    void loginByUsernameShouldReturnChildMcpFlag() {
        User child = new User();
        child.setId(9L);
        child.setUsername("xiaoming");
        child.setNickname("小明");
        child.setRole(User.ROLE_CHILD);
        child.setStatus(User.STATUS_ACTIVE);
        child.setMustChangePassword(true);
        child.setPasswordHash(passwordEncoder.encode("Init1234"));
        when(userMapper.findByUsername("xiaoming")).thenReturn(child);
        when(loginGuardService.isLocked(child)).thenReturn(false);
        when(memberMapper.findFamilyIdByUserId(9L)).thenReturn(5L);
        when(refreshTokenService.issue(child, 5L))
                .thenReturn(new TokenPairResponse("at", "rt", 900L, true));

        LoginResponse response = service.login(new LoginRequest("xiaoming", "Init1234"));

        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.user().role()).isEqualTo(User.ROLE_CHILD);
        verify(userMapper, never()).findByPhoneHash(any());
    }

    @Test
    void loginUnknownAccountShouldThrow200002AndAuditAnonymous() {
        when(userMapper.findByUsername("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", PASSWORD)))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(200002));
        verify(auditService).record(new AuditEntry(null, AuditAction.LOGIN_FAILURE,
                "USER", null, "{\"reason\":\"ACCOUNT_NOT_FOUND\"}"));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void loginLockedAccountShouldThrow200003WithoutCounting() {
        User stored = parent(7L);
        when(userMapper.findByPhoneHash(Hashes.sha256Hex(PHONE))).thenReturn(stored);
        when(loginGuardService.isLocked(stored)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest(PHONE, PASSWORD)))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(200003));
        verify(loginGuardService, never()).recordFailure(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void loginDisabledAccountShouldThrow200004() {
        User stored = parent(7L);
        stored.setStatus(User.STATUS_DISABLED);
        when(userMapper.findByPhoneHash(Hashes.sha256Hex(PHONE))).thenReturn(stored);
        when(loginGuardService.isLocked(stored)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest(PHONE, PASSWORD)))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(200004));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void loginWrongPasswordShouldRecordFailureAndThrow200002() {
        User stored = parent(7L);
        when(userMapper.findByPhoneHash(Hashes.sha256Hex(PHONE))).thenReturn(stored);
        when(loginGuardService.isLocked(stored)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest(PHONE, "WrongPass1")))
                .satisfies(thrown -> assertThat(codeOf(thrown)).isEqualTo(200002));
        verify(loginGuardService).recordFailure(stored);
        verify(auditService).record(new AuditEntry(7L, AuditAction.LOGIN_FAILURE,
                "USER", "7", "{\"reason\":\"BAD_CREDENTIALS\"}"));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refreshShouldDelegateToTokenService() {
        TokenPairResponse pair = new TokenPairResponse("at2", "rt2", 900L, false);
        when(refreshTokenService.rotate("rt")).thenReturn(pair);

        assertThat(service.refresh(new RefreshRequest("rt"))).isSameAs(pair);
    }

    @Test
    void logoutShouldRevokeTokenAndAudit() {
        UserIdPrincipal principal = new UserIdPrincipal(7L, 5L, User.ROLE_PARENT, false);

        service.logout(principal, new LogoutRequest("rt"));

        verify(refreshTokenService).revoke("rt");
        verify(auditService).record(new AuditEntry(7L, AuditAction.LOGOUT,
                "REFRESH_TOKEN", Hashes.sha256Hex("rt"), null));
    }
}
