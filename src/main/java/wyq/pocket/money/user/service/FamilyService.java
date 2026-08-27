package wyq.pocket.money.user.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
import wyq.pocket.money.user.dto.UserErrorCode;
import wyq.pocket.money.user.event.MemberRemovedEvent;
import wyq.pocket.money.user.mapper.FamilyMapper;
import wyq.pocket.money.user.mapper.FamilyMemberMapper;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * 家庭域业务：家庭信息、创建孩子账号、成员管理（M1 设计 §6）。
 *
 * <p>家庭域请求一律先经 {@link FamilyAccessChecker} 完成数据级成员校验
 * （拒绝 403 + 100004），写操作另由方法安全做 PARENT 接口级守卫。
 * 孩子账号不采集手机号 / 邮箱（COPPA 类合规），consented_at 由列默认值
 * now() 留痕；移除孩子同事务吊销其全部 refresh 令牌并置 DISABLED（§6.4）。
 */
@Component
public class FamilyService {

    /** 家庭成员上限（§6.1）。 */
    private static final int MAX_FAMILY_MEMBERS = 8;

    private final FamilyMapper familyMapper;

    private final FamilyMemberMapper familyMemberMapper;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenService refreshTokenService;

    private final AuditService auditService;

    private final FamilyAccessChecker familyAccessChecker;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 注入协作对象。
     *
     * @param familyMapper        家庭 Mapper
     * @param familyMemberMapper  成员关系 Mapper
     * @param userMapper          用户 Mapper
     * @param passwordEncoder     BCrypt 编码器
     * @param refreshTokenService 令牌服务
     * @param auditService        审计服务
     * @param familyAccessChecker 数据级访问守卫
     * @param eventPublisher      领域事件发布器（M2 成员移除联动）
     */
    public FamilyService(FamilyMapper familyMapper, FamilyMemberMapper familyMemberMapper,
                         UserMapper userMapper, PasswordEncoder passwordEncoder,
                         RefreshTokenService refreshTokenService, AuditService auditService,
                         FamilyAccessChecker familyAccessChecker,
                         ApplicationEventPublisher eventPublisher) {
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.familyAccessChecker = familyAccessChecker;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 家庭详情（§6.2 #9）：本家庭全员可见。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @return 家庭详情（含成员列表）
     * @throws BusinessException 200005 家庭不存在
     */
    public FamilyDetailResponse getFamily(long familyId, UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        Family family = requireFamily(familyId);
        return new FamilyDetailResponse(family.getId(), family.getFamilyName(),
                family.getOwnerUserId(), familyMemberMapper.findMembersByFamilyId(familyId));
    }

    /**
     * 当前主体所属家庭（§10.2 #8）。
     *
     * @param principal 当前登录主体
     * @return 家庭详情
     */
    public FamilyDetailResponse getMyFamily(UserIdPrincipal principal) {
        return getFamily(principal.familyId(), principal);
    }

    /**
     * 家庭成员列表（§6.4 #12）：本家庭全员可见。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @return 成员摘要列表
     * @throws BusinessException 200005 家庭不存在
     */
    public List<MemberSummary> listMembers(long familyId, UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        requireFamily(familyId);
        return familyMemberMapper.findMembersByFamilyId(familyId);
    }

    /**
     * 修改家庭名（§6.2 #10，仅家长）。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体
     * @param request   修改请求
     * @throws BusinessException 200005 家庭不存在
     */
    @Transactional
    public void updateFamily(long familyId, UserIdPrincipal principal,
                             UpdateFamilyRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        requireFamily(familyId);
        familyMapper.updateFamilyName(familyId, request.familyName());
        auditService.record(new AuditEntry(principal.userId(), AuditAction.FAMILY_UPDATE,
                "FAMILY", String.valueOf(familyId), null));
    }

    /**
     * 创建孩子账号（§6.3 #11，仅家长）：同事务落用户 + 成员关系 + 审计。
     *
     * @param familyId  家庭 ID
     * @param principal 当前登录主体（家长）
     * @param request   创建请求
     * @return 创建结果（mcp=true）
     * @throws BusinessException 200005 家庭不存在 / 200006 成员满员 / 200007 登录名已占用
     */
    @Transactional
    public ChildCreateResponse addChild(long familyId, UserIdPrincipal principal,
                                        AddChildRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        requireFamily(familyId);
        if (familyMemberMapper.countByFamilyId(familyId) >= MAX_FAMILY_MEMBERS) {
            throw new BusinessException(UserErrorCode.FAMILY_MEMBER_LIMIT_REACHED);
        }
        if (userMapper.findByUsername(request.username()) != null) {
            throw new BusinessException(UserErrorCode.USERNAME_TAKEN);
        }
        User child = insertChild(principal, request);
        familyMemberMapper.insert(new FamilyMember(familyId, child.getId()));
        auditService.record(new AuditEntry(principal.userId(), AuditAction.CHILD_CREATE,
                "USER", String.valueOf(child.getId()), null));
        return new ChildCreateResponse(child.getId(), child.getUsername(),
                child.getNickname(), child.getRole(), child.isMustChangePassword());
    }

    /**
     * 修改孩子昵称（§6.4 #13，仅家长）。
     *
     * @param familyId    家庭 ID
     * @param childUserId 孩子用户 ID
     * @param principal   当前登录主体
     * @param request     修改请求
     * @throws BusinessException 200011 目标不是本家庭的孩子
     */
    @Transactional
    public void updateChild(long familyId, long childUserId, UserIdPrincipal principal,
                            UpdateNicknameRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        requireChildInFamily(familyId, childUserId);
        userMapper.updateNickname(childUserId, request.nickname());
        auditService.record(new AuditEntry(principal.userId(), AuditAction.CHILD_UPDATE,
                "USER", String.valueOf(childUserId), null));
    }

    /**
     * 重置孩子密码（§6.5 #14，仅家长）：mcp 重新生效，既有会话全部吊销。
     *
     * @param familyId    家庭 ID
     * @param childUserId 孩子用户 ID
     * @param principal   当前登录主体
     * @param request     重置请求
     * @throws BusinessException 200011 目标不是本家庭的孩子
     */
    @Transactional
    public void resetChildPassword(long familyId, long childUserId, UserIdPrincipal principal,
                                   ResetChildPasswordRequest request) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        requireChildInFamily(familyId, childUserId);
        userMapper.updatePassword(childUserId, passwordEncoder.encode(request.newPassword()),
                true);
        refreshTokenService.revokeAll(childUserId);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.CHILD_PASSWORD_RESET,
                "USER", String.valueOf(childUserId), null));
    }

    /**
     * 移除成员（§6.4 #15，仅家长）：创建者不可移除（200012）；
     * 移除孩子 = 删成员关系 + 吊销全部 refresh + 置 DISABLED + 审计，
     * 并发布 {@link MemberRemovedEvent}（M2：冻结账户 / 取消任务 / 暂停规则）。
     *
     * @param familyId     家庭 ID
     * @param targetUserId 目标成员用户 ID
     * @param principal    当前登录主体
     * @throws BusinessException 200012 目标是创建者 / 200011 目标不在本家庭
     */
    @Transactional
    public void removeMember(long familyId, long targetUserId, UserIdPrincipal principal) {
        familyAccessChecker.requireMember(familyId, principal.userId());
        Family family = requireFamily(familyId);
        if (family.getOwnerUserId() == targetUserId) {
            throw new BusinessException(UserErrorCode.CANNOT_REMOVE_OWNER);
        }
        if (!isMemberOf(familyId, targetUserId)) {
            throw new BusinessException(UserErrorCode.MEMBER_NOT_IN_FAMILY);
        }
        familyMemberMapper.deleteByFamilyIdAndUserId(familyId, targetUserId);
        refreshTokenService.revokeAll(targetUserId);
        userMapper.updateStatus(targetUserId, User.STATUS_DISABLED);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.MEMBER_REMOVE,
                "USER", String.valueOf(targetUserId), null));
        eventPublisher.publishEvent(new MemberRemovedEvent(familyId, targetUserId));
    }

    private Family requireFamily(long familyId) {
        Family family = familyMapper.findById(familyId);
        if (family == null) {
            throw new BusinessException(UserErrorCode.FAMILY_NOT_FOUND);
        }
        return family;
    }

    private void requireChildInFamily(long familyId, long childUserId) {
        User child = userMapper.findById(childUserId);
        if (child == null || !User.ROLE_CHILD.equals(child.getRole())
                || !isMemberOf(familyId, childUserId)) {
            throw new BusinessException(UserErrorCode.MEMBER_NOT_IN_FAMILY);
        }
    }

    private boolean isMemberOf(long familyId, long userId) {
        Long memberFamilyId = familyMemberMapper.findFamilyIdByUserId(userId);
        return memberFamilyId != null && memberFamilyId == familyId;
    }

    private User insertChild(UserIdPrincipal principal, AddChildRequest request) {
        User child = new User();
        child.setUsername(request.username());
        child.setPasswordHash(passwordEncoder.encode(request.password()));
        child.setNickname(request.nickname());
        child.setRole(User.ROLE_CHILD);
        child.setStatus(User.STATUS_ACTIVE);
        child.setMustChangePassword(true);
        child.setConsentedBy(principal.userId());
        userMapper.insert(child);
        return child;
    }
}
