package wyq.pocket.money.ai.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import wyq.pocket.money.ai.domain.AiMessage;
import wyq.pocket.money.ai.domain.AiSession;
import wyq.pocket.money.ai.dto.AiMessageResponse;
import wyq.pocket.money.ai.mapper.AiMessageMapper;
import wyq.pocket.money.ai.mapper.AiSessionMapper;
import wyq.pocket.money.common.ai.AiProperties;
import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.CommonErrorCode;

/**
 * AI 会话 / 消息落库与读取（M4 设计 §6.1/§7.4）。
 *
 * <p>一人一活跃会话；ASSISTANT 消息的 {@code tool_call_json} 落
 * 「意图 → 参数 → 工具调用 → 结果」调用链（D34）。会话属主校验：越权
 * 抛 {@link AccessDeniedException}（全局 403 + 100004）。
 */
@Component
public class AiSessionService {

    private final AiSessionMapper sessionMapper;

    private final AiMessageMapper messageMapper;

    private final AiProperties properties;

    private final JsonMapper jsonMapper;

    private final Clock clock;

    private final AuditService auditService;

    /**
     * 注入协作对象。
     *
     * @param sessionMapper 会话 Mapper
     * @param messageMapper 消息 Mapper
     * @param properties    AI 配置
     * @param jsonMapper    JSON 序列化器（Jackson 3）
     * @param clock         时钟
     * @param auditService  审计服务
     */
    public AiSessionService(AiSessionMapper sessionMapper, AiMessageMapper messageMapper,
                            AiProperties properties, JsonMapper jsonMapper, Clock clock,
                            AuditService auditService) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.auditService = auditService;
    }

    /**
     * 获取（或创建）当前用户的活跃会话。
     *
     * @param principal 当前登录主体
     * @return 活跃会话
     */
    @Transactional
    public AiSession getOrCreateSession(UserIdPrincipal principal) {
        AiSession session = sessionMapper.findActiveByUser(principal.userId());
        if (session != null) {
            return session;
        }
        AiSession created = new AiSession();
        created.setUserId(principal.userId());
        created.setFamilyId(principal.familyId());
        created.setChannel(properties.channelDefault());
        created.setStatus(AiSession.STATUS_ACTIVE);
        sessionMapper.insert(created);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.AI_SESSION_START,
                "AI_SESSION", String.valueOf(created.getId()), null));
        return created;
    }

    /**
     * 记录用户消息并刷新会话活跃时间。
     *
     * @param sessionId 会话 ID
     * @param content   用户原文
     */
    @Transactional
    public void recordUserMessage(long sessionId, String content) {
        insertMessage(sessionId, AiMessage.ROLE_USER, content, null, null);
        sessionMapper.updateLastActive(sessionId, clock.instant());
    }

    /**
     * 记录助手消息（含调用链）并刷新会话活跃时间。
     *
     * @param sessionId 会话 ID
     * @param content   回复文本
     * @param intent    意图码
     * @param params    原始参数
     * @param result    工具执行结果（序列化进调用链）
     */
    @Transactional
    public void recordAssistantMessage(long sessionId, String content, String intent,
                                       Map<String, String> params, Object result) {
        insertMessage(sessionId, AiMessage.ROLE_ASSISTANT, content, intent,
                buildCallChain(intent, params, result));
        sessionMapper.updateLastActive(sessionId, clock.instant());
    }

    /**
     * 查询会话消息（按时间升序），越权抛 403。
     *
     * @param sessionId 会话 ID
     * @param principal 当前登录主体
     * @return 消息响应列表
     */
    public List<AiMessageResponse> listMessages(long sessionId, UserIdPrincipal principal) {
        AiSession session = sessionMapper.findById(sessionId);
        if (session == null || session.getUserId() != principal.userId()) {
            throw new AccessDeniedException("AI_SESSION_ACCESS_DENIED session=" + sessionId);
        }
        return messageMapper.findBySession(sessionId).stream()
                .map(this::toResponse).toList();
    }

    private void insertMessage(long sessionId, String role, String content, String intent,
                               String toolCallJson) {
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent);
        message.setToolCallJson(toolCallJson);
        messageMapper.insert(message);
    }

    private AiMessageResponse toResponse(AiMessage message) {
        return new AiMessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getIntent(), message.getToolCallJson(), message.getCreatedAt());
    }

    private String buildCallChain(String intent, Map<String, String> params, Object result) {
        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("intent", intent);
        chain.put("params", params);
        chain.put("result", result);
        try {
            return jsonMapper.writeValueAsString(chain);
        } catch (JacksonException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "调用链序列化失败", e);
        }
    }
}
