package wyq.pocket.money.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.ai.dto.AiChatRequest;
import wyq.pocket.money.ai.dto.AiChatResponse;
import wyq.pocket.money.ai.dto.AiConfirmResponse;
import wyq.pocket.money.ai.dto.AiMessageResponse;
import wyq.pocket.money.ai.service.AiOrchestrator;
import wyq.pocket.money.ai.service.AiSessionService;
import wyq.pocket.money.ai.service.PendingActionService;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.Result;

/**
 * AI 端点单元测试（M4 设计 §8）：对话、资金写确认 / 取消、会话历史四个
 * 端点向编排器 / 状态机 / 会话服务的委托。
 */
class AiControllerTest {

    private static final UserIdPrincipal PARENT = new UserIdPrincipal(1L, 10L, "PARENT", false);

    private final AiOrchestrator orchestrator = mock(AiOrchestrator.class);

    private final PendingActionService pendingActionService = mock(PendingActionService.class);

    private final AiSessionService sessionService = mock(AiSessionService.class);

    private final AiController controller = new AiController(orchestrator, pendingActionService,
            sessionService);

    @Test
    void chatShouldDelegateToOrchestrator() {
        when(orchestrator.answer(PARENT, "查余额")).thenReturn(new AiChatResponse("回复", null));

        Result<AiChatResponse> result = controller.chat(PARENT, new AiChatRequest("查余额"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).isEqualTo(new AiChatResponse("回复", null));
    }

    @Test
    void confirmShouldDelegateToPendingActionService() {
        when(pendingActionService.confirm(PARENT, 5L)).thenReturn(
                new AiConfirmResponse(9L, 2L, new BigDecimal("10"), new BigDecimal("20")));

        Result<AiConfirmResponse> result = controller.confirm(PARENT, 5L);

        assertThat(result.data().transactionId()).isEqualTo(9L);
    }

    @Test
    void cancelShouldDelegateAndReturnSuccess() {
        Result<Void> result = controller.cancel(PARENT, 5L);

        assertThat(result.isSuccess()).isTrue();
        verify(pendingActionService).cancel(PARENT, 5L);
    }

    @Test
    void messagesShouldDelegateToSessionService() {
        when(sessionService.listMessages(5L, PARENT)).thenReturn(List.of(
                new AiMessageResponse(1L, "USER", "查余额", null, null,
                        Instant.parse("2026-08-27T10:00:00Z"))));

        Result<List<AiMessageResponse>> result = controller.messages(PARENT, 5L);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).content()).isEqualTo("查余额");
    }
}
