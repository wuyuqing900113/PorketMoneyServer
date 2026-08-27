package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import wyq.pocket.money.ai.domain.AiIntent;
import wyq.pocket.money.ai.domain.AiSession;
import wyq.pocket.money.ai.dto.AiChatResponse;
import wyq.pocket.money.ai.dto.AiErrorCode;
import wyq.pocket.money.common.ai.IntentResult;
import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;

/**
 * AI 编排器单元测试（M4 设计 §6.1）：查询直行、资金写转二次确认、
 * AI 不可用降级审计与每用户限流拒绝。
 */
class AiOrchestratorTest {

    private static final UserIdPrincipal PARENT = new UserIdPrincipal(1L, 10L, "PARENT", false);

    private final IntentCatalog intentCatalog = mock(IntentCatalog.class);

    private final AiInvoker aiInvoker = mock(AiInvoker.class);

    private final AiToolRegistry toolRegistry = mock(AiToolRegistry.class);

    private final AiSessionService sessionService = mock(AiSessionService.class);

    private final PendingActionService pendingActionService = mock(PendingActionService.class);

    private final AiReplyComposer replyComposer = mock(AiReplyComposer.class);

    private final AuditService auditService = mock(AuditService.class);

    private final AiRateLimitService aiRateLimitService = mock(AiRateLimitService.class);

    private final AiOrchestrator orchestrator = new AiOrchestrator(intentCatalog, aiInvoker,
            toolRegistry, sessionService, pendingActionService, replyComposer, auditService,
            aiRateLimitService);

    private final AiSession session = session();

    @BeforeEach
    void setUp() {
        when(aiRateLimitService.tryAcquire(anyLong())).thenReturn(true);
        when(sessionService.getOrCreateSession(any())).thenReturn(session);
    }

    @Test
    void shouldAnswerQueryDirectly() {
        when(aiInvoker.invoke(anyString(), any()))
                .thenReturn(new IntentResult("BALANCE_QUERY", Map.of(), 1.0));
        when(intentCatalog.requireIntent("BALANCE_QUERY")).thenReturn(AiIntent.BALANCE_QUERY);
        when(toolRegistry.execute(eq(AiIntent.BALANCE_QUERY), eq(PARENT), any()))
                .thenReturn(new BigDecimal("520.00"));
        when(replyComposer.compose(eq(AiIntent.BALANCE_QUERY), any()))
                .thenReturn("家庭总余额 520.00 元");

        AiChatResponse response = orchestrator.answer(PARENT, "查一下余额");

        assertThat(response.reply()).isEqualTo("家庭总余额 520.00 元");
        assertThat(response.pendingActionId()).isNull();
        verify(sessionService).recordUserMessage(100L, "查一下余额");
        verify(sessionService).recordAssistantMessage(eq(100L), eq("家庭总余额 520.00 元"),
                eq("BALANCE_QUERY"), any(), any());
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.AI_INTENT);
    }

    @Test
    void shouldRequestConfirmationForFundWrite() {
        when(aiInvoker.invoke(anyString(), any())).thenReturn(
                new IntentResult("DEPOSIT", Map.of("amount", "50", "targetUserName", "小明"),
                        1.0));
        when(intentCatalog.requireIntent("DEPOSIT")).thenReturn(AiIntent.DEPOSIT);
        when(pendingActionService.request(eq(PARENT), eq(session), eq(AiIntent.DEPOSIT), any()))
                .thenReturn(new AiChatResponse("确认给 小明 存入 50 元？", 200L));

        AiChatResponse response = orchestrator.answer(PARENT, "给小明存50");

        assertThat(response.reply()).isEqualTo("确认给 小明 存入 50 元？");
        assertThat(response.pendingActionId()).isEqualTo(200L);
        verify(pendingActionService).request(eq(PARENT), eq(session), eq(AiIntent.DEPOSIT), any());
        verify(sessionService).recordAssistantMessage(eq(100L), anyString(), eq("DEPOSIT"), any(),
                any());
    }

    @Test
    void shouldDegradeWhenAiUnavailable() {
        when(aiInvoker.invoke(anyString(), any()))
                .thenThrow(new BusinessException(AiErrorCode.AI_UNAVAILABLE));

        assertThatThrownBy(() -> orchestrator.answer(PARENT, "查余额"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.AI_UNAVAILABLE));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.AI_DEGRADED);
    }

    @Test
    void shouldRejectWhenRateLimited() {
        when(aiRateLimitService.tryAcquire(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> orchestrator.answer(PARENT, "查余额"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.RATE_LIMITED));

        verify(sessionService, never()).getOrCreateSession(any());
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
}
