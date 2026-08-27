package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

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
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.money.dto.DepositRequest;
import wyq.pocket.money.money.dto.DepositWithdrawResponse;
import wyq.pocket.money.money.service.MoneyOperationService;
import wyq.pocket.money.user.dto.MemberSummary;
import wyq.pocket.money.user.service.FamilyAccessChecker;
import wyq.pocket.money.user.service.FamilyService;

/**
 * 资金写二次确认状态机单元测试（M4 设计 §6.2）：生成 / 确认（原子抢占） /
 * 取消；成员名解析、孩子仅限本人、业务失败落 REJECTED 与审计全程。
 */
class PendingActionServiceTest {

    private static final UserIdPrincipal PARENT = new UserIdPrincipal(1L, 10L, "PARENT", false);

    private static final UserIdPrincipal CHILD = new UserIdPrincipal(2L, 10L, "CHILD", false);

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    private static final List<MemberSummary> MEMBERS = List.of(
            new MemberSummary(1L, "家长", "PARENT"),
            new MemberSummary(2L, "小明", "CHILD"));

    private final AiPendingActionMapper pendingActionMapper = mock(AiPendingActionMapper.class);

    private final FamilyService familyService = mock(FamilyService.class);

    private final FamilyAccessChecker familyAccessChecker = mock(FamilyAccessChecker.class);

    private final AiProperties properties = new AiProperties(true, "TEXT", Duration.ofSeconds(60),
            Duration.ofDays(7), true, "0 43 4 * * *",
            new AiProperties.RateLimit(10, Duration.ofMinutes(1)), new AiProperties.Stub(false));

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final JsonMapper jsonMapper = new JsonMapper();

    private final AuditService auditService = mock(AuditService.class);

    private final MoneyOperationService moneyOperationService = mock(MoneyOperationService.class);

    private final PendingActionService service = new PendingActionService(pendingActionMapper,
            familyService, familyAccessChecker, properties, clock, jsonMapper, auditService,
            moneyOperationService);

    @Test
    void requestShouldCreatePendingActionAndAudit() {
        when(pendingActionMapper.findPendingBySession(100L)).thenReturn(null);
        when(familyService.listMembers(10L, PARENT)).thenReturn(MEMBERS);
        doAnswer(inv -> {
            ((AiPendingAction) inv.getArgument(0)).setId(300L);
            return 1;
        }).when(pendingActionMapper).insert(any(AiPendingAction.class));

        AiChatResponse response = service.request(PARENT, session(), AiIntent.DEPOSIT,
                Map.of("amount", "50", "targetUserName", "小明"));

        assertThat(response.pendingActionId()).isEqualTo(300L);
        assertThat(response.reply()).contains("小明").contains("存入");

        ArgumentCaptor<AiPendingAction> captor = ArgumentCaptor.forClass(AiPendingAction.class);
        verify(pendingActionMapper).insert(captor.capture());
        AiPendingAction saved = captor.getValue();
        assertThat(saved.getIntent()).isEqualTo("DEPOSIT");
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getSessionId()).isEqualTo(100L);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusSeconds(60));

        ArgumentCaptor<AuditEntry> auditCaptor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action())
                .isEqualTo(AuditAction.AI_ACTION_CONFIRM_REQUEST);
    }

    @Test
    void requestShouldRejectWhenPendingExists() {
        when(pendingActionMapper.findPendingBySession(100L)).thenReturn(new AiPendingAction());

        assertThatThrownBy(() -> service.request(PARENT, session(), AiIntent.DEPOSIT, Map.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(AiErrorCode.PENDING_ACTION_EXISTS));
    }

    @Test
    void requestShouldRejectUnknownTarget() {
        when(pendingActionMapper.findPendingBySession(100L)).thenReturn(null);
        when(familyService.listMembers(10L, PARENT)).thenReturn(MEMBERS);

        assertThatThrownBy(() -> service.request(PARENT, session(), AiIntent.DEPOSIT,
                Map.of("amount", "50", "targetUserName", "不存在")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.PARAM_INVALID));
    }

    @Test
    void childShouldOnlyTargetSelf() {
        when(pendingActionMapper.findPendingBySession(100L)).thenReturn(null);
        when(familyService.listMembers(10L, CHILD)).thenReturn(MEMBERS);

        assertThatThrownBy(() -> service.request(CHILD, session(), AiIntent.DEPOSIT,
                Map.of("amount", "50", "targetUserName", "家长")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void confirmShouldExecuteAndAudit() {
        when(pendingActionMapper.findById(200L)).thenReturn(
                pendingAction(200L, AiPendingAction.STATUS_PENDING, NOW.plusSeconds(30)));
        when(pendingActionMapper.claimExecuted(200L, 1L, NOW)).thenReturn(1);
        when(moneyOperationService.deposit(eq(PARENT), eq(2L), any(DepositRequest.class)))
                .thenReturn(new DepositWithdrawResponse(999L, 2L, new BigDecimal("50"),
                        new BigDecimal("520.00")));

        AiConfirmResponse response = service.confirm(PARENT, 200L);

        assertThat(response.transactionId()).isEqualTo(999L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualByComparingTo("50");
        assertThat(response.balanceAfter()).isEqualByComparingTo("520.00");

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.AI_ACTION_EXECUTED);
    }

    @Test
    void confirmShouldRejectNonexistent() {
        when(pendingActionMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.confirm(PARENT, 999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(AiErrorCode.PENDING_ACTION_INVALID));
    }

    @Test
    void confirmShouldRejectWhenClaimFails() {
        when(pendingActionMapper.findById(200L)).thenReturn(
                pendingAction(200L, AiPendingAction.STATUS_PENDING, NOW.plusSeconds(30)));
        when(pendingActionMapper.claimExecuted(200L, 1L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(PARENT, 200L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(AiErrorCode.PENDING_ACTION_INVALID));
    }

    @Test
    void confirmShouldRejectOnBusinessFailure() {
        when(pendingActionMapper.findById(200L)).thenReturn(
                pendingAction(200L, AiPendingAction.STATUS_PENDING, NOW.plusSeconds(30)));
        when(pendingActionMapper.claimExecuted(200L, 1L, NOW)).thenReturn(1);
        when(moneyOperationService.deposit(eq(PARENT), eq(2L), any(DepositRequest.class)))
                .thenThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR));

        assertThatThrownBy(() -> service.confirm(PARENT, 200L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR));

        verify(pendingActionMapper).updateReject(200L);
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.AI_ACTION_REJECTED);
    }

    @Test
    void cancelShouldCancelAndAudit() {
        when(pendingActionMapper.updateCancel(200L, 1L)).thenReturn(1);

        service.cancel(PARENT, 200L);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.AI_ACTION_CANCELED);
    }

    @Test
    void cancelShouldRejectWhenNotPending() {
        when(pendingActionMapper.updateCancel(200L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(PARENT, 200L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(AiErrorCode.PENDING_ACTION_INVALID));
    }

    private static AiSession session() {
        AiSession session = new AiSession();
        session.setId(100L);
        session.setUserId(1L);
        session.setFamilyId(10L);
        session.setChannel("TEXT");
        session.setStatus(AiSession.STATUS_ACTIVE);
        return session;
    }

    private AiPendingAction pendingAction(long id, String status, Instant expiresAt) {
        AiPendingAction action = new AiPendingAction();
        action.setId(id);
        action.setSessionId(100L);
        action.setUserId(1L);
        action.setIntent("DEPOSIT");
        action.setParamsJson(serialize(new FundActionParams(2L, "小明", new BigDecimal("50"),
                null)));
        action.setStatus(status);
        action.setExpiresAt(expiresAt);
        return action;
    }

    private String serialize(FundActionParams params) {
        try {
            return jsonMapper.writeValueAsString(params);
        } catch (JacksonException e) {
            throw new AssertionError(e);
        }
    }
}
