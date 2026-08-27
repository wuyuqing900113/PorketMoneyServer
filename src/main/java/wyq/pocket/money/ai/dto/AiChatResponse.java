package wyq.pocket.money.ai.dto;

/**
 * AI 对话响应（M4 设计 §3.2）。
 *
 * @param reply          自然语言回复文本
 * @param pendingActionId 资金写待确认动作 ID（查询意图为 null）
 */
public record AiChatResponse(String reply, Long pendingActionId) {
}
