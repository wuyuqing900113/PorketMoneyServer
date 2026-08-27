package wyq.pocket.money.ai.service;

import java.util.Map;

import org.springframework.stereotype.Component;

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
 * AI 编排器（M4 设计 §6.1）：意图解析 → 校验 → 分支执行。
 *
 * <p>查询意图经 {@link AiToolRegistry} 直行取真实数据并组装回复；资金写
 * 意图转 {@link PendingActionService} 生成二次确认动作。全程以会话绑定的
 * {@link UserIdPrincipal} 为身份，工具返回值是唯一数据源（D32）。
 */
@Component
public class AiOrchestrator {

    private final IntentCatalog intentCatalog;

    private final AiInvoker aiInvoker;

    private final AiToolRegistry toolRegistry;

    private final AiSessionService sessionService;

    private final PendingActionService pendingActionService;

    private final AiReplyComposer replyComposer;

    private final AuditService auditService;

    private final AiRateLimitService aiRateLimitService;

    /**
     * 注入协作对象。
     *
     * @param intentCatalog        意图目录
     * @param aiInvoker            意图解析调用器（统一降级出口）
     * @param toolRegistry         查询工具注册表
     * @param sessionService       会话 / 消息服务
     * @param pendingActionService 二次确认状态机
     * @param replyComposer        回复组装器
     * @param auditService         审计服务
     * @param aiRateLimitService   AI 调用限流
     */
    public AiOrchestrator(IntentCatalog intentCatalog, AiInvoker aiInvoker,
                          AiToolRegistry toolRegistry, AiSessionService sessionService,
                          PendingActionService pendingActionService,
                          AiReplyComposer replyComposer, AuditService auditService,
                          AiRateLimitService aiRateLimitService) {
        this.intentCatalog = intentCatalog;
        this.aiInvoker = aiInvoker;
        this.toolRegistry = toolRegistry;
        this.sessionService = sessionService;
        this.pendingActionService = pendingActionService;
        this.replyComposer = replyComposer;
        this.auditService = auditService;
        this.aiRateLimitService = aiRateLimitService;
    }

    /**
     * 处理一次对话指令（§6.1 主流程）。
     *
     * @param principal 当前登录主体
     * @param text      用户指令文本
     * @return 回复 + 待确认动作 ID（查询为 null）
     * @throws BusinessException 600001 AI 不可用 / 600002 意图未识别 / 100001 参数非法
     */
    public AiChatResponse answer(UserIdPrincipal principal, String text) {
        if (!aiRateLimitService.tryAcquire(principal.userId())) {
            throw new BusinessException(CommonErrorCode.RATE_LIMITED);
        }
        AiSession session = sessionService.getOrCreateSession(principal);
        sessionService.recordUserMessage(session.getId(), text);
        IntentResult parsed = invokeSafely(principal, session, text);
        AiIntent intent = intentCatalog.requireIntent(parsed.toolName());
        intentCatalog.validate(intent, parsed.rawParams());
        if (intent.requiresConfirmation()) {
            return fundWrite(principal, session, intent, parsed.rawParams());
        }
        return query(principal, session, intent, parsed.rawParams());
    }

    private IntentResult invokeSafely(UserIdPrincipal principal, AiSession session, String text) {
        try {
            return aiInvoker.invoke(text, intentCatalog.toolDefinitions());
        } catch (BusinessException e) {
            if (e.getErrorCode() == AiErrorCode.AI_UNAVAILABLE) {
                auditService.record(new AuditEntry(principal.userId(), AuditAction.AI_DEGRADED,
                        "AI_SESSION", String.valueOf(session.getId()), text));
            }
            throw e;
        }
    }

    private AiChatResponse query(UserIdPrincipal principal, AiSession session, AiIntent intent,
                                 Map<String, String> rawParams) {
        Object data = toolRegistry.execute(intent, principal, rawParams);
        String reply = replyComposer.compose(intent, data);
        sessionService.recordAssistantMessage(session.getId(), reply, intent.name(), rawParams, data);
        auditService.record(new AuditEntry(principal.userId(), AuditAction.AI_INTENT,
                "AI_SESSION", String.valueOf(session.getId()), intent.name()));
        return new AiChatResponse(reply, null);
    }

    private AiChatResponse fundWrite(UserIdPrincipal principal, AiSession session, AiIntent intent,
                                     Map<String, String> rawParams) {
        AiChatResponse response = pendingActionService.request(principal, session, intent,
                rawParams);
        sessionService.recordAssistantMessage(session.getId(), response.reply(), intent.name(),
                rawParams, pendingResult(response.pendingActionId()));
        auditService.record(new AuditEntry(principal.userId(), AuditAction.AI_INTENT,
                "AI_SESSION", String.valueOf(session.getId()), intent.name()));
        return response;
    }

    private Map<String, Object> pendingResult(Long actionId) {
        return Map.of("status", "PENDING", "actionId", actionId);
    }
}
