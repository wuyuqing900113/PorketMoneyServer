package wyq.pocket.money.common.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 基于 Spring AI 的生产对话端口（M4 设计 D27 / GA D67）：DeepSeek 大模型适配器。
 *
 * <p>DeepSeek 提供 OpenAI 兼容协议，由 {@code spring-ai-starter-model-openai}
 * 经 {@code spring.ai.openai.base-url=https://api.deepseek.com} 装配 {@link ChatModel}；
 * 本类仅负责「意图解析边界」：把意图目录（{@link ToolDefinition}）以 JSON Schema
 * 注入 system prompt，约束模型只输出 {@code {"toolName":...,"params":{...}}} JSON，
 * 再解析为 {@link IntentResult}。
 *
 * <p>边界约束（与 M4 设计一致）：
 * <ul>
 *   <li>本端口<strong>不执行工具</strong>：资金类操作经 M4 二次确认状态机后由
 *       {@code AiToolRegistry} 走既有事务与权限体系执行，模型不直接触达业务；</li>
 *   <li>数据类回答（余额等）不由模型生成，编排层实时查账回填；</li>
 *   <li>超时 / 重试 / 熔断由 {@code AiInvoker} 的 Resilience4j 装饰器统一兜底，
 *       本类抛出的任何异常都在调用边界落 {@code AI_UNAVAILABLE(600001)} 降级出口。</li>
 * </ul>
 */
public class SpringAiChatPort implements ChatPort {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiChatPort.class);

    /** 模型返回体的最大记录长度（异常日志中截断，避免超长 prompt 回显）。 */
    private static final int MAX_BODY_LOG_LENGTH = 500;

    private final ChatClient chatClient;

    private final JsonMapper jsonMapper;

    /**
     * 构造 DeepSeek 适配器。
     *
     * @param chatModel  Spring AI 对话模型（OpenAI 兼容协议，指向 DeepSeek）
     * @param jsonMapper JSON 序列化（Jackson 3）
     */
    public SpringAiChatPort(ChatModel chatModel, JsonMapper jsonMapper) {
        this.chatClient = ChatClient.create(chatModel);
        this.jsonMapper = jsonMapper;
    }

    @Override
    public IntentResult parseIntent(String userText, List<ToolDefinition> tools) {
        Set<String> toolNames = tools.stream().map(ToolDefinition::name).collect(Collectors.toSet());
        String content = chatClient.prompt()
                .system(buildSystemPrompt(tools))
                .user(userText == null ? "" : userText)
                .call()
                .content();
        return parse(content, toolNames);
    }

    /**
     * 构造约束性 system prompt：输出格式 + 选择规则 + 工具目录 JSON。
     */
    private String buildSystemPrompt(List<ToolDefinition> tools) {
        return """
                你是零花钱管理系统的语音指令意图解析器。任务：从用户指令中选择一个最匹配的工具并抽取参数。
                只输出一个 JSON 对象，禁止输出 JSON 以外的任何内容（不要解释、不要 markdown）。
                输出格式：{"toolName":"工具名或null","params":{"参数名":"参数值字符串"}}
                规则：
                1. toolName 必须是「可用工具」之一；没有匹配意图时输出 null。
                2. params 的值一律输出为字符串：金额保留原始数字文本（如 "50.50"），成员名输出原文。
                3. 无法抽取的参数不要编造，省略该键。
                可用工具（JSON 数组，name=工具名，description=用途，params=参数名到类型）：
                {TOOLS}""".replace("{TOOLS}", jsonMapper.writeValueAsString(tools));
    }

    /**
     * 解析模型返回：容错提取首个 JSON 对象；toolName 不在目录内视同未识别。
     */
    private IntentResult parse(String body, Set<String> toolNames) {
        JsonNode root = parseJson(body);
        String toolName = resolveToolName(root);
        if (toolName == null) {
            return new IntentResult(null, Map.of(), 0.0);
        }
        if (!toolNames.contains(toolName)) {
            LOG.warn("AI 返回未知工具名：{}（已忽略，按未识别处理）", toolName);
            return new IntentResult(null, Map.of(), 0.0);
        }
        return new IntentResult(toolName, extractParams(root.get("params")), 1.0);
    }

    /**
     * 解析返回体为 JSON 根节点：空体 / 非预期 JSON 一律抛 IllegalStateException。
     */
    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("AI 返回为空");
        }
        return readTree(body);
    }

    /**
     * 截取首个 JSON 对象后反序列化：非预期 / 畸形一律抛 IllegalStateException。
     */
    private JsonNode readTree(String body) {
        try {
            return jsonMapper.readTree(extractJsonString(body));
        } catch (JacksonException | IllegalArgumentException e) {
            LOG.warn("AI 返回非预期 JSON：{}", abbreviate(body));
            throw new IllegalStateException("AI 返回解析失败", e);
        }
    }

    /**
     * 取工具名（null / 缺失 / 空值归一为 null）。
     */
    private String resolveToolName(JsonNode root) {
        JsonNode toolNode = root.get("toolName");
        return toolNode == null || toolNode.isNull() ? null : toolNode.asString();
    }

    /**
     * 从可能夹带多余文本的返回体中截取首个完整 JSON 对象文本（{ 到末 }）。
     */
    private String extractJsonString(String body) {
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("返回体中无 JSON 对象");
        }
        return body.substring(start, end + 1);
    }

    /**
     * params 节点展平为字符串映射（模型输出统一按字符串消费，与 StubChatPort 对齐）。
     */
    private Map<String, String> extractParams(JsonNode paramsNode) {
        Map<String, String> params = new HashMap<>();
        if (paramsNode != null && paramsNode.isObject()) {
            paramsNode.forEachEntry((name, value) -> {
                if (!value.isNull()) {
                    params.put(name, value.asString());
                }
            });
        }
        return params;
    }

    private String abbreviate(String body) {
        if (body.length() <= MAX_BODY_LOG_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LOG_LENGTH) + "...";
    }
}
