package wyq.pocket.money.ai.dto;

import java.time.Instant;

/**
 * AI 消息响应（M4 设计 §7.4，会话历史检索）。
 *
 * @param id           消息 ID
 * @param role         消息角色（USER / ASSISTANT / SYSTEM）
 * @param content      消息内容
 * @param intent       意图码（USER 消息为 null）
 * @param toolCallJson 调用链 JSON 文本（意图→参数→工具调用→结果）
 * @param createdAt    创建时间
 */
public record AiMessageResponse(Long id, String role, String content, String intent,
                                String toolCallJson, Instant createdAt) {
}
