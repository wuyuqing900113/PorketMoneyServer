package wyq.pocket.money.ai.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import wyq.pocket.money.ai.domain.AiIntent;
import wyq.pocket.money.ai.domain.AiPendingAction;
import wyq.pocket.money.ai.domain.AiSession;
import wyq.pocket.money.ai.domain.FundActionParams;
import wyq.pocket.money.ai.dto.AiChatResponse;
import wyq.pocket.money.ai.dto.AiConfirmResponse;
import wyq.pocket.money.ai.dto.AiErrorCode;
import wyq.pocket.money.ai.mapper.AiPendingActionMapper;
import wyq.pocket.money.common.ai.AiProperties;
import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.idempotency.IdempotencyContext;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.money.dto.DepositRequest;
import wyq.pocket.money.money.dto.DepositWithdrawResponse;
import wyq.pocket.money.money.dto.WithdrawRequest;
import wyq.pocket.money.money.service.MoneyOperationService;
import wyq.pocket.money.user.dto.MemberSummary;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.FamilyService;

/**
 * 资金写二次确认状态机（M4 设计 §6.2）。
 *
 * <p>生成待确认动作（PENDING + 参数快照 + TTL），确认时原子抢占
 * （PENDING → EXECUTED 条件更新）保证单次执行，再以确定性
 * {@code requestId = AI-<actionId>} 回填 {@code IdempotencyContext} 交
 * {@link MoneyOperationService} 记账（{@code uk_mtxn_request} 账务级兜底）。
 * 确认执行与记账不引入非终态中间态；业务失败落 REJECTED。
 */
@Component
public class PendingActionService {

    /** 孩子角色标识（与 user 模块 User.ROLE_CHILD 同值）。 */
    private static final String ROLE_CHILD = "CHILD";

    private final AiPendingActionMapper pendingActionMapper;

    private final FamilyService familyService;

    private final FamilyAccessChecker familyAccessChecker;

    private final AiProperties properties;

    private final Clock clock;

    private final JsonMapper jsonMapper;

    private final AuditService auditService;

    private final MoneyOperationService moneyOperationService;

    /**
     * 注入协作对象。
     *
     * @param pendingActionMapper  待确认动作 Mapper
     * @param familyService        家庭域服务（成员名解析）
     * @param familyAccessChecker  数据级访问守卫
     * @param properties           AI 配置（TTL）
     * @param clock                时钟
     * @param jsonMapper           JSON 序列化器（Jackson 3）
     * @param auditService         审计服务
     * @param moneyOperationService 记账服务
     */
    public PendingActionService(AiPendingActionMapper pendingActionMapper,
                                FamilyService familyService,
                                FamilyAccessChecker familyAccessChecker,
                                AiProperties properties, Clock clock, JsonMapper jsonMapper,
                                AuditService auditService,
                                MoneyOperationService moneyOperationService) {
        this.pendingActionMapper = pendingActionMapper;
        this.familyService = familyService;
        this.familyAccessChecker = familyAccessChecker;
        this.properties = properties;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
        this.auditService = auditService;
        this.moneyOperationService = moneyOperationService;
    }

    /**
     * 生成待确认动作并返回确认话术（§6.2 生成段）。
     *
     * @param principal 当前登录主体
     * @param session   当前会话
     * @param intent    资金写意图（DEPOSIT / WITHDRAW）
     * @param rawParams 原始参数
     * @return 确认话术 + 待确认动作 ID
     * @throws BusinessException 600004 已有未完成待确认操作 / 100001 成员不存在 / 金额非法
     */
    public AiChatResponse request(UserIdPrincipal principal, AiSession session, AiIntent intent,
                                  Map<String, String> rawParams) {
        if (pendingActionMapper.findPendingBySession(session.getId()) != null) {
            throw new BusinessException(AiErrorCode.PENDING_ACTION_EXISTS);
        }
        List<MemberSummary> members = familyService.listMembers(principal.familyId(), principal);
        FundActionParams params = resolveParams(principal, rawParams, members);
        AiPendingAction action = new AiPendingAction();
        action.setSessionId(session.getId());
        action.setUserId(principal.userId());
        action.setIntent(intent.name());
        action.setParamsJson(serializeParams(params));
        action.setExpiresAt(clock.instant().plus(properties.pendingTtl()));
        pendingActionMapper.insert(action);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.AI_ACTION_CONFIRM_REQUEST,
                "AI_PENDING_ACTION", String.valueOf(action.getId()), intent.name()));
        return new AiChatResponse(confirmText(intent, params), action.getId());
    }

    /**
     * 确认执行待确认动作（§6.2 确认段）。
     *
     * @param principal 当前登录主体
     * @param actionId  待确认动作 ID
     * @return 记账结果
     * @throws BusinessException 600003 动作不存在/过期/越权/已终态 或 下游业务错误码（如 300001）
     */
    public AiConfirmResponse confirm(UserIdPrincipal principal, long actionId) {
        AiPendingAction action = pendingActionMapper.findById(actionId);
        requireConfirmable(action, principal);
        Instant now = clock.instant();
        if (pendingActionMapper.claimExecuted(actionId, principal.userId(), now) == 0) {
            throw new BusinessException(AiErrorCode.PENDING_ACTION_INVALID);
        }
        FundActionParams params = parseParams(action.getParamsJson());
        AiIntent intent = AiIntent.valueOf(action.getIntent());
        IdempotencyContext.set("AI-" + actionId);
        try {
            DepositWithdrawResponse result = execute(intent, principal, params);
            auditService.record(new AuditEntry(principal.userId(), AuditAction.AI_ACTION_EXECUTED,
                    "AI_PENDING_ACTION", String.valueOf(actionId), intent.name()));
            return new AiConfirmResponse(result.transactionId(), result.userId(), result.amount(),
                    result.balanceAfter());
        } catch (BusinessException e) {
            pendingActionMapper.updateReject(actionId);
            auditService.record(new AuditEntry(principal.userId(), AuditAction.AI_ACTION_REJECTED,
                    "AI_PENDING_ACTION", String.valueOf(actionId), intent.name()));
            throw e;
        } finally {
            IdempotencyContext.clear();
        }
    }

    /**
     * 取消待确认动作（§6.2 取消段）。
     *
     * @param principal 当前登录主体
     * @param actionId  待确认动作 ID
     * @throws BusinessException 600003 动作不存在/越权/已终态
     */
    public void cancel(UserIdPrincipal principal, long actionId) {
        if (pendingActionMapper.updateCancel(actionId, principal.userId()) == 0) {
            throw new BusinessException(AiErrorCode.PENDING_ACTION_INVALID);
        }
        auditService.record(new AuditEntry(principal.userId(), AuditAction.AI_ACTION_CANCELED,
                "AI_PENDING_ACTION", String.valueOf(actionId), null));
    }

    private void requireConfirmable(AiPendingAction action, UserIdPrincipal principal) {
        requireExists(action);
        requireOwner(action, principal);
        requirePendingState(action);
    }

    private void requireExists(AiPendingAction action) {
        if (action == null) {
            throw new BusinessException(AiErrorCode.PENDING_ACTION_INVALID);
        }
    }

    private void requireOwner(AiPendingAction action, UserIdPrincipal principal) {
        if (action.getUserId() != principal.userId()) {
            throw new BusinessException(AiErrorCode.PENDING_ACTION_INVALID);
        }
    }

    private void requirePendingState(AiPendingAction action) {
        if (!AiPendingAction.STATUS_PENDING.equals(action.getStatus()) || isExpired(action)) {
            throw new BusinessException(AiErrorCode.PENDING_ACTION_INVALID);
        }
    }

    private boolean isExpired(AiPendingAction action) {
        return action.getExpiresAt() == null || !action.getExpiresAt().isAfter(clock.instant());
    }

    private FundActionParams resolveParams(UserIdPrincipal principal,
                                           Map<String, String> rawParams,
                                           List<MemberSummary> members) {
        BigDecimal amount = requireAmount(rawParams);
        MemberSummary target = resolveTargetMember(principal, rawParams, members);
        requireSelfIfChild(principal, target.userId());
        return new FundActionParams(target.userId(), target.nickname(), amount,
                rawParams.get("remark"));
    }

    private MemberSummary resolveTargetMember(UserIdPrincipal principal,
                                              Map<String, String> rawParams,
                                              List<MemberSummary> members) {
        String targetUserId = rawParams.get("targetUserId");
        if (targetUserId != null && !targetUserId.isBlank()) {
            long id = Long.parseLong(targetUserId.trim());
            familyAccessChecker.requireMember(principal.familyId(), id);
            return requireMemberById(members, id);
        }
        String targetUserName = rawParams.get("targetUserName");
        if (targetUserName != null && !targetUserName.isBlank()) {
            return requireMemberByNickname(members, targetUserName.trim());
        }
        return requireMemberById(members, principal.userId());
    }

    private void requireSelfIfChild(UserIdPrincipal principal, long targetUserId) {
        if (ROLE_CHILD.equals(principal.role()) && principal.userId() != targetUserId) {
            throw new AccessDeniedException(
                    "CHILD_SELF_ACCOUNT_ONLY user=" + principal.userId());
        }
    }

    private MemberSummary requireMemberById(List<MemberSummary> members, long userId) {
        for (MemberSummary member : members) {
            if (member.userId() == userId) {
                return member;
            }
        }
        throw new BusinessException(CommonErrorCode.PARAM_INVALID, "成员不存在");
    }

    private MemberSummary requireMemberByNickname(List<MemberSummary> members, String nickname) {
        for (MemberSummary member : members) {
            if (nickname.equals(member.nickname())) {
                return member;
            }
        }
        throw new BusinessException(CommonErrorCode.PARAM_INVALID, "成员不存在");
    }

    private BigDecimal requireAmount(Map<String, String> rawParams) {
        String value = rawParams.get("amount");
        if (value == null || value.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "金额非法");
        }
        return new BigDecimal(value.trim());
    }

    private DepositWithdrawResponse execute(AiIntent intent, UserIdPrincipal principal,
                                            FundActionParams params) {
        if (intent == AiIntent.DEPOSIT) {
            return moneyOperationService.deposit(principal, params.targetUserId(),
                    new DepositRequest(params.targetUserId(), params.amount(), params.remark()));
        }
        return moneyOperationService.withdraw(principal, params.targetUserId(),
                new WithdrawRequest(params.targetUserId(), params.amount(), params.remark()));
    }

    private String confirmText(AiIntent intent, FundActionParams params) {
        String verb = intent == AiIntent.DEPOSIT ? "存入" : "提取";
        return "确认给 " + params.targetNickname() + " " + verb + " "
                + params.amount().toPlainString() + " 元？";
    }

    private String serializeParams(FundActionParams params) {
        try {
            return jsonMapper.writeValueAsString(params);
        } catch (JacksonException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "参数快照序列化失败", e);
        }
    }

    private FundActionParams parseParams(String json) {
        try {
            return jsonMapper.readValue(json, FundActionParams.class);
        } catch (JacksonException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "参数快照解析失败", e);
        }
    }
}
