package wyq.pocket.money.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import tools.jackson.databind.json.JsonMapper;
import wyq.pocket.money.ai.domain.AiMessage;
import wyq.pocket.money.ai.domain.AiSession;
import wyq.pocket.money.ai.dto.AiMessageResponse;
import wyq.pocket.money.ai.mapper.AiMessageMapper;
import wyq.pocket.money.ai.mapper.AiSessionMapper;
import wyq.pocket.money.common.ai.AiProperties;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.security.UserIdPrincipal;

/**
 * AI 会话 / 消息服务单元测试（M4 设计 §6.1/§7.4）：一人一活跃会话、
 * 消息落库与调用链、会话属主校验越权 403。
 */
class AiSessionServiceTest {

    private static final UserIdPrincipal PARENT = new UserIdPrincipal(1L, 10L, "PARENT", false);

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    private final AiSessionMapper sessionMapper = mock(AiSessionMapper.class);

    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);

    private final AiProperties properties = new AiProperties(true, "TEXT", Duration.ofSeconds(60),
            Duration.ofDays(7), true, "0 43 4 * * *",
            new AiProperties.RateLimit(10, Duration.ofMinutes(1)), new AiProperties.Stub(false));

    private final JsonMapper jsonMapper = new JsonMapper();

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AuditService auditService = mock(AuditService.class);

    private final AiSessionService service = new AiSessionService(sessionMapper, messageMapper,
            properties, jsonMapper, clock, auditService);

    @Test
    void shouldReturnExistingSession() {
        when(sessionMapper.findActiveByUser(1L)).thenReturn(sessionOf(1L));

        AiSession result = service.getOrCreateSession(PARENT);

        assertThat(result.getId()).isEqualTo(100L);
        verify(sessionMapper, never()).insert(any(AiSession.class));
    }

    @Test
    void shouldCreateSessionWhenNone() {
        when(sessionMapper.findActiveByUser(1L)).thenReturn(null);

        service.getOrCreateSession(PARENT);

        ArgumentCaptor<AiSession> captor = ArgumentCaptor.forClass(AiSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getFamilyId()).isEqualTo(10L);
        assertThat(captor.getValue().getStatus()).isEqualTo(AiSession.STATUS_ACTIVE);
        verify(auditService).record(any(AuditEntry.class));
    }

    @Test
    void shouldRecordUserMessageAndRefreshActivity() {
        service.recordUserMessage(100L, "查余额");

        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper).insert(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(AiMessage.ROLE_USER);
        assertThat(captor.getValue().getContent()).isEqualTo("查余额");
        verify(sessionMapper).updateLastActive(100L, NOW);
    }

    @Test
    void shouldRecordAssistantMessageWithCallChain() {
        service.recordAssistantMessage(100L, "家庭总余额 520.00 元", "BALANCE_QUERY", Map.of(),
                new BigDecimal("520.00"));

        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper).insert(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(AiMessage.ROLE_ASSISTANT);
        assertThat(captor.getValue().getIntent()).isEqualTo("BALANCE_QUERY");
        assertThat(captor.getValue().getToolCallJson()).contains("BALANCE_QUERY");
        verify(sessionMapper).updateLastActive(100L, NOW);
    }

    @Test
    void shouldListMessagesForOwner() {
        when(sessionMapper.findById(100L)).thenReturn(sessionOf(1L));
        when(messageMapper.findBySession(100L)).thenReturn(List.of(message()));

        List<AiMessageResponse> messages = service.listMessages(100L, PARENT);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).role()).isEqualTo(AiMessage.ROLE_USER);
    }

    @Test
    void shouldDenyCrossUserAccess() {
        when(sessionMapper.findById(100L)).thenReturn(sessionOf(2L));

        assertThatThrownBy(() -> service.listMessages(100L, PARENT))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldDenyNonexistentSession() {
        when(sessionMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.listMessages(999L, PARENT))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static AiSession sessionOf(long userId) {
        AiSession session = new AiSession();
        session.setId(100L);
        session.setUserId(userId);
        session.setFamilyId(10L);
        session.setChannel("TEXT");
        session.setStatus(AiSession.STATUS_ACTIVE);
        return session;
    }

    private static AiMessage message() {
        AiMessage message = new AiMessage();
        message.setId(1L);
        message.setSessionId(100L);
        message.setRole(AiMessage.ROLE_USER);
        message.setContent("查余额");
        message.setCreatedAt(NOW);
        return message;
    }
}
