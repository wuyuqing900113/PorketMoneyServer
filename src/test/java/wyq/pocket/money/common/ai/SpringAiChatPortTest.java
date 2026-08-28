package wyq.pocket.money.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link SpringAiChatPort} 单元测试（GA D67）：模型输出由 {@link ChatModel}
 * Mock 提供，专注 JSON 解析边界（成功 / 未识别 / 未知工具 / 夹带文本 / 畸形 / 异常传播）。
 * HTTP 层超时 / 5xx 由 Spring AI 客户端抛出，统一在 {@code AiInvoker} 落 600001
 * （降级链路已有 AiInvokerTest / WireMock 故障注入覆盖）。
 */
class SpringAiChatPortTest {

    private ChatModel chatModel;

    private SpringAiChatPort chatPort;

    private final List<ToolDefinition> tools = List.of(
            new ToolDefinition("DEPOSIT", "存入零花钱",
                    Map.of("amount", "decimal", "targetUserName", "string")),
            new ToolDefinition("BALANCE_QUERY", "查询余额", Map.of()));

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        // ChatClient 内部会读取 chatModel.getOptions().mutate()，Mock 默认返回 null 会 NPE，
        // 这里显式给一个默认 ChatOptions（与真实装配时 spring.ai.openai 提供的默认值一致）。
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        chatPort = new SpringAiChatPort(chatModel, new JsonMapper());
    }

    @Test
    void shouldParseFundInstructionWithParams() {
        stubModelResponse("{\"toolName\":\"DEPOSIT\",\"params\":{\"amount\":\"50.50\",\"targetUserName\":\"小明\"}}");

        IntentResult result = chatPort.parseIntent("给小明存50.5", tools);

        assertThat(result.toolName()).isEqualTo("DEPOSIT");
        assertThat(result.confidence()).isEqualTo(1.0);
        assertThat(result.rawParams()).containsEntry("amount", "50.50")
                .containsEntry("targetUserName", "小明");
    }

    @Test
    void shouldTolerateJsonWrappedInMarkdownFence() {
        stubModelResponse("好的，解析结果如下：\n```json\n{\"toolName\":\"BALANCE_QUERY\",\"params\":{}}\n```");

        IntentResult result = chatPort.parseIntent("我还有多少零花钱", tools);

        assertThat(result.toolName()).isEqualTo("BALANCE_QUERY");
        assertThat(result.rawParams()).isEmpty();
    }

    @Test
    void shouldReturnUnrecognizedWhenToolNameNull() {
        stubModelResponse("{\"toolName\":null,\"params\":{}}");

        IntentResult result = chatPort.parseIntent("今天天气怎么样", tools);

        assertThat(result.toolName()).isNull();
        assertThat(result.confidence()).isZero();
    }

    @Test
    void shouldIgnoreUnknownToolName() {
        stubModelResponse("{\"toolName\":\"ORDER_PIZZA\",\"params\":{}}");

        IntentResult result = chatPort.parseIntent("点个披萨", tools);

        assertThat(result.toolName()).isNull();
        assertThat(result.confidence()).isZero();
    }

    @Test
    void shouldFailOnMalformedJson() {
        stubModelResponse("{not-valid-json");

        assertThatThrownBy(() -> chatPort.parseIntent("给小明存50", tools))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解析失败");
    }

    @Test
    void shouldFailOnEmptyBody() {
        stubModelResponse("   ");

        assertThatThrownBy(() -> chatPort.parseIntent("给小明存50", tools))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("为空");
    }

    @Test
    void shouldPropagateModelException() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("simulated provider 500"));

        assertThatThrownBy(() -> chatPort.parseIntent("给小明存50", tools))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated provider 500");
    }

    private void stubModelResponse(String content) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }
}
