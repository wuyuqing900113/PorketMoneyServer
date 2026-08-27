package wyq.pocket.money.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 对话请求（M4 设计 §3.2）。
 *
 * @param text 用户指令文本
 */
public record AiChatRequest(@NotBlank(message = "指令不能为空") String text) {
}
