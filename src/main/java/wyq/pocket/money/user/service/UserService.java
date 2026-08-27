package wyq.pocket.money.user.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.log.MaskingRules;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.user.domain.User;
import wyq.pocket.money.user.dto.ChangePasswordRequest;
import wyq.pocket.money.user.dto.UpdateNicknameRequest;
import wyq.pocket.money.user.dto.UserErrorCode;
import wyq.pocket.money.user.dto.UserMeResponse;
import wyq.pocket.money.user.mapper.FamilyMemberMapper;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * 用户个人信息业务（M1 设计 §5.4 / §5.5）。
 *
 * <p>手机号解密结果不出 service 层：getMe 返回前即经 MaskingRules
 * 脱敏；改密成功后吊销该用户全部 refresh 令牌（§4.3 吊销时机表）。
 */
@Component
public class UserService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenService refreshTokenService;

    private final AuditService auditService;

    private final FamilyMemberMapper familyMemberMapper;

    /**
     * 注入协作对象。
     *
     * @param userMapper          用户 Mapper
     * @param passwordEncoder     BCrypt 编码器
     * @param refreshTokenService 令牌服务
     * @param auditService        审计服务
     * @param familyMemberMapper  成员关系 Mapper（M2 统计摘要成员数）
     */
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService, AuditService auditService,
                       FamilyMemberMapper familyMemberMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.familyMemberMapper = familyMemberMapper;
    }

    /**
     * 查询当前用户信息（手机号脱敏回显，§5.5）。
     *
     * @param principal 当前登录主体
     * @return 用户信息
     */
    public UserMeResponse getMe(UserIdPrincipal principal) {
        User user = requireUser(principal.userId());
        String maskedPhone = user.getPhoneEncrypted() == null
                ? null
                : MaskingRules.mask(user.getPhoneEncrypted());
        return new UserMeResponse(user.getId(), user.getNickname(), user.getRole(),
                principal.familyId(), maskedPhone);
    }

    /**
     * 修改昵称（§5.5；登录名与手机号 M1 不可变）。
     *
     * @param principal 当前登录主体
     * @param request   修改请求
     */
    public void updateNickname(UserIdPrincipal principal, UpdateNicknameRequest request) {
        requireUser(principal.userId());
        userMapper.updateNickname(principal.userId(), request.nickname());
    }

    /**
     * 自助修改密码（§5.4）：校验旧密码 → 更新哈希并清除 mcp →
     * 吊销全部 refresh → 审计。孩子首次改密同此端点（mcp 豁免路径）。
     *
     * @param principal 当前登录主体
     * @param request   修改请求
     * @throws BusinessException 200008 原密码不正确
     */
    @Transactional
    public void changePassword(UserIdPrincipal principal, ChangePasswordRequest request) {
        User user = requireUser(principal.userId());
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(UserErrorCode.OLD_PASSWORD_WRONG);
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword()), false);
        refreshTokenService.revokeAll(user.getId());
        auditService.record(new AuditEntry(user.getId(), AuditAction.PASSWORD_CHANGE,
                "USER", String.valueOf(user.getId()), null));
    }

    /**
     * 批量查询昵称映射（M2 榜单 / 报表昵称回显）。
     *
     * @param userIds 用户 ID 集合，空集合返回空 Map
     * @return userId → nickname
     */
    public Map<Long, String> findNicknameMap(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nicknameMap = new HashMap<>();
        for (User user : userMapper.findNicknamesByIds(userIds)) {
            nicknameMap.put(user.getId(), user.getNickname());
        }
        return nicknameMap;
    }

    /**
     * 家庭成员数（M2 统计摘要）。
     *
     * @param familyId 家庭 ID
     * @return 在册成员数
     */
    public int countFamilyMembers(long familyId) {
        return familyMemberMapper.countByFamilyId(familyId);
    }

    private User requireUser(long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return user;
    }
}
