package wyq.pocket.money.user.service;

import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.audit.SecurityLogger;
import wyq.pocket.money.common.crypto.Hashes;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.log.MaskingRules;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;
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
import wyq.pocket.money.user.dto.UserErrorCode;
import wyq.pocket.money.user.mapper.FamilyMapper;
import wyq.pocket.money.user.mapper.FamilyMemberMapper;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * 认证业务：注册 / 登录 / 刷新 / 登出（M1 设计 §5.1–§5.3）。
 *
 * <p>登录为家长手机号 / 孩子登录名统一入口：11 位数字串按手机号哈希
 * 查找，否则按登录名查找。账号不存在与密码错误统一 200002（防枚举），
 * 校验顺序：锁定（200003）→ 停用（200004）→ 密码比对。
 */
@Component
public class AuthService {

    private static final Pattern PHONE_IDENTIFIER = Pattern.compile("\\d{11}");

    private static final int FAMILY_NAME_NICKNAME_MAX_PART = 29;

    private static final String FAMILY_NAME_SUFFIX = "的家庭";

    private final UserMapper userMapper;

    private final FamilyMapper familyMapper;

    private final FamilyMemberMapper familyMemberMapper;

    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenService refreshTokenService;

    private final LoginGuardService loginGuardService;

    private final AuditService auditService;

    /**
     * 注入协作对象。
     *
     * @param userMapper          用户 Mapper
     * @param familyMapper        家庭 Mapper
     * @param familyMemberMapper  成员关系 Mapper
     * @param passwordEncoder     BCrypt 编码器（strength=10）
     * @param refreshTokenService 令牌服务
     * @param loginGuardService   登录锁定守护
     * @param auditService        审计服务
     */
    public AuthService(UserMapper userMapper, FamilyMapper familyMapper,
                       FamilyMemberMapper familyMemberMapper, PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService, LoginGuardService loginGuardService,
                       AuditService auditService) {
        this.userMapper = userMapper;
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.loginGuardService = loginGuardService;
        this.auditService = auditService;
    }

    /**
     * 家长注册：同事务完成用户 + 家庭 + 成员关系 + 审计（§5.1）。
     *
     * @param request 注册请求
     * @return 注册结果
     * @throws BusinessException 200001 手机号已注册
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String phoneHash = Hashes.sha256Hex(request.phone());
        if (userMapper.findByPhoneHash(phoneHash) != null) {
            throw new BusinessException(UserErrorCode.PHONE_ALREADY_REGISTERED);
        }
        User user = insertParent(request, phoneHash);
        Family family = insertFamily(user);
        familyMemberMapper.insert(new FamilyMember(family.getId(), user.getId()));
        auditService.record(new AuditEntry(user.getId(), AuditAction.REGISTER,
                "USER", String.valueOf(user.getId()), null));
        auditService.record(new AuditEntry(user.getId(), AuditAction.FAMILY_CREATE,
                "FAMILY", String.valueOf(family.getId()), null));
        return new RegisterResponse(user.getId(), family.getId(), user.getNickname(), user.getRole());
    }

    /**
     * 登录：锁定 → 停用 → 密码比对（§4.5 / §5.2）。
     *
     * @param request 登录请求
     * @return 令牌对与用户摘要
     * @throws BusinessException 200002 / 200003 / 200004
     */
    public LoginResponse login(LoginRequest request) {
        User user = findByIdentifier(request.identifier());
        if (user == null) {
            throw failLogin(null, request.identifier(), UserErrorCode.BAD_CREDENTIALS,
                    "ACCOUNT_NOT_FOUND");
        }
        checkLoginAllowed(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginGuardService.recordFailure(user);
            throw failLogin(user, request.identifier(), UserErrorCode.BAD_CREDENTIALS,
                    "BAD_CREDENTIALS");
        }
        return issueLoginResponse(user);
    }

    /**
     * 刷新令牌（轮转，§4.4）。
     *
     * @param request 刷新请求
     * @return 新令牌对
     * @throws BusinessException 100003（一切刷新失败统一码）
     */
    public TokenPairResponse refresh(RefreshRequest request) {
        return refreshTokenService.rotate(request.refreshToken());
    }

    /**
     * 登出：吊销提交的 refresh 令牌并审计（§5.3）。
     *
     * @param principal 当前登录主体
     * @param request   登出请求
     */
    public void logout(UserIdPrincipal principal, LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        auditService.record(new AuditEntry(principal.userId(), AuditAction.LOGOUT,
                "REFRESH_TOKEN", Hashes.sha256Hex(request.refreshToken()), null));
    }

    private User insertParent(RegisterRequest request, String phoneHash) {
        User user = new User();
        user.setPhoneHash(phoneHash);
        user.setPhoneEncrypted(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRole(User.ROLE_PARENT);
        user.setStatus(User.STATUS_ACTIVE);
        userMapper.insert(user);
        return user;
    }

    private Family insertFamily(User user) {
        Family family = new Family(defaultFamilyName(user.getNickname()), user.getId());
        familyMapper.insert(family);
        return family;
    }

    private String defaultFamilyName(String nickname) {
        String part = nickname.length() > FAMILY_NAME_NICKNAME_MAX_PART
                ? nickname.substring(0, FAMILY_NAME_NICKNAME_MAX_PART)
                : nickname;
        return part + FAMILY_NAME_SUFFIX;
    }

    private User findByIdentifier(String identifier) {
        if (PHONE_IDENTIFIER.matcher(identifier).matches()) {
            return userMapper.findByPhoneHash(Hashes.sha256Hex(identifier));
        }
        return userMapper.findByUsername(identifier);
    }

    private void checkLoginAllowed(User user) {
        if (loginGuardService.isLocked(user)) {
            throw new BusinessException(UserErrorCode.ACCOUNT_LOCKED);
        }
        if (User.STATUS_DISABLED.equals(user.getStatus())) {
            throw new BusinessException(UserErrorCode.ACCOUNT_DISABLED);
        }
    }

    private LoginResponse issueLoginResponse(User user) {
        loginGuardService.resetAfterSuccess(user);
        long familyId = requireFamilyId(user.getId());
        TokenPairResponse pair = refreshTokenService.issue(user, familyId);
        auditService.record(new AuditEntry(user.getId(), AuditAction.LOGIN_SUCCESS,
                "USER", String.valueOf(user.getId()), null));
        return new LoginResponse(pair.accessToken(), pair.refreshToken(), pair.expiresIn(),
                pair.mustChangePassword(),
                new LoginResponse.UserSummary(user.getId(), user.getNickname(),
                        user.getRole(), familyId));
    }

    private long requireFamilyId(long userId) {
        Long familyId = familyMemberMapper.findFamilyIdByUserId(userId);
        if (familyId == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return familyId;
    }

    private BusinessException failLogin(User user, String identifier, UserErrorCode code,
                                        String reason) {
        Long userId = user == null ? null : user.getId();
        SecurityLogger.warn("LOGIN_FAILURE user={} identifier={} reason={}",
                userId, MaskingRules.mask(identifier), reason);
        auditService.record(new AuditEntry(userId, AuditAction.LOGIN_FAILURE, "USER",
                userId == null ? null : String.valueOf(userId),
                "{\"reason\":\"" + reason + "\"}"));
        return new BusinessException(code);
    }
}
