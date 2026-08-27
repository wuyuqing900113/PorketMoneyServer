package wyq.pocket.money.ai.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import wyq.pocket.money.ai.dto.AiChatRequest;
import wyq.pocket.money.ai.dto.AiChatResponse;
import wyq.pocket.money.ai.dto.AiConfirmResponse;
import wyq.pocket.money.ai.dto.AiMessageResponse;
import wyq.pocket.money.ai.service.AiOrchestrator;
import wyq.pocket.money.ai.service.AiSessionService;
import wyq.pocket.money.ai.service.PendingActionService;
import wyq.pocket.money.common.security.UserIdPrincipal;
import wyq.pocket.money.common.web.OpenApiConfig;
import wyq.pocket.money.common.web.Result;

/**
 * AI 交互端点（M4 设计 §8）。
 *
 * <p>对话、资金写二次确认与会话历史检索；身份取会话绑定的
 * {@link UserIdPrincipal}，AI 工具权限 = 既有接口权限（家长全家庭、
 * 孩子仅本人账户）。
 */
@Tag(name = "AI 交互", description = "文本指令对话、资金写二次确认与会话历史")
@SecurityRequirement(name = OpenApiConfig.BEARER_SECURITY_SCHEME)
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiOrchestrator orchestrator;

    private final PendingActionService pendingActionService;

    private final AiSessionService sessionService;

    /**
     * 注入协作对象。
     *
     * @param orchestrator         AI 编排器
     * @param pendingActionService 二次确认状态机
     * @param sessionService       会话 / 消息服务
     */
    public AiController(AiOrchestrator orchestrator, PendingActionService pendingActionService,
                        AiSessionService sessionService) {
        this.orchestrator = orchestrator;
        this.pendingActionService = pendingActionService;
        this.sessionService = sessionService;
    }

    /**
     * AI 对话（§8 #1）：意图理解 + 查询直行 / 资金写生成二次确认。
     *
     * @param principal 当前登录主体
     * @param request   用户指令
     * @return 回复 + 待确认动作 ID（查询意图为 null）
     */
    @Operation(summary = "AI 对话",
            description = "意图理解 → 查询直行 / 资金写生成待确认动作。"
                    + "错误码：HTTP 401 + 100003；100007 限流；600001 AI 不可用；"
                    + "600002 意图未识别；600004 已有待确认操作；100001 参数非法。")
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@AuthenticationPrincipal UserIdPrincipal principal,
            @Valid @RequestBody AiChatRequest request) {
        return Result.success(orchestrator.answer(principal, request.text()));
    }

    /**
     * 确认执行待确认动作（§8 #2）。
     *
     * @param principal 当前登录主体
     * @param actionId  待确认动作 ID
     * @return 记账结果
     */
    @Operation(summary = "确认资金写",
            description = "原子抢占 PENDING 后记账，保证单次执行。"
                    + "错误码：HTTP 401 + 100003；600003 动作无效/过期/越权/已终态；"
                    + "下游记账错误码（如 300001 余额不足）。")
    @PostMapping("/actions/{actionId}/confirm")
    public Result<AiConfirmResponse> confirm(@AuthenticationPrincipal UserIdPrincipal principal,
            @PathVariable("actionId") long actionId) {
        return Result.success(pendingActionService.confirm(principal, actionId));
    }

    /**
     * 取消待确认动作（§8 #3）。
     *
     * @param principal 当前登录主体
     * @param actionId  待确认动作 ID
     * @return 无数据成功响应
     */
    @Operation(summary = "取消资金写",
            description = "PENDING → CANCELED。"
                    + "错误码：HTTP 401 + 100003；600003 动作无效/越权/已终态。")
    @PostMapping("/actions/{actionId}/cancel")
    public Result<Void> cancel(@AuthenticationPrincipal UserIdPrincipal principal,
            @PathVariable("actionId") long actionId) {
        pendingActionService.cancel(principal, actionId);
        return Result.success();
    }

    /**
     * 会话历史检索（§8 #4）。
     *
     * @param principal 当前登录主体
     * @param sessionId 会话 ID
     * @return 消息响应列表（时间升序）
     */
    @Operation(summary = "会话历史",
            description = "按时间升序返回会话消息。"
                    + "错误码：HTTP 401 + 100003；HTTP 403 + 100004 会话越权。")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<AiMessageResponse>> messages(
            @AuthenticationPrincipal UserIdPrincipal principal,
            @PathVariable("sessionId") long sessionId) {
        return Result.success(sessionService.listMessages(sessionId, principal));
    }
}
