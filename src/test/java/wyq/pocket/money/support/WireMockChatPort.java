package wyq.pocket.money.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import wyq.pocket.money.common.ai.ChatPort;
import wyq.pocket.money.common.ai.IntentResult;
import wyq.pocket.money.common.ai.ToolDefinition;

/**
 * 基于 JDK {@link HttpClient} 的 {@link ChatPort} HTTP 适配器测试桩（M6 设计 §5.1 D49）：
 * 以真实网络语义访问 {@link AiProviderWireMock} 提供的 AI provider 端点，用于在 HTTP
 * 传输层验证超时 / 5xx / 畸形响应下的降级与熔断行为。仅测试范围使用，不进入运行时镜像。
 *
 * <p>与 {@code StubChatPort}（进程内确定性路由桩）定位分层：本类走真实 HTTP 传输，
 * 专注故障注入；StubChatPort 走进程内确定性路由，专注业务链路测试。二者并存，
 * 前者验证「若未来接入真实 HTTP 适配器，降级 / 超时 / 限流语义在真实网络下仍成立」。
 */
public class WireMockChatPort implements ChatPort {

    /** 单次请求超时：需大于 TimeLimiter 超时阈值，实际延迟由 WireMock 桩控制。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    private final JsonMapper jsonMapper;

    private final URI endpoint;

    /**
     * 构造指向指定 WireMock 端点的 HTTP 适配器桩。
     *
     * @param endpoint AI provider 完整端点 URL（如 http://localhost:PORT/v1/chat/completions）
     */
    public WireMockChatPort(String endpoint) {
        this.httpClient = HttpClient.newHttpClient();
        this.jsonMapper = new JsonMapper();
        this.endpoint = URI.create(endpoint);
    }

    @Override
    public IntentResult parseIntent(String userText, List<ToolDefinition> tools) {
        HttpResponse<String> response = send(buildBody(userText, tools));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI provider HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    private HttpResponse<String> send(String body) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI provider request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("AI provider request failed", e);
        }
    }

    private String buildBody(String userText, List<ToolDefinition> tools) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userText", userText);
        payload.put("tools", tools.stream().map(ToolDefinition::name).toList());
        return jsonMapper.writeValueAsString(payload);
    }

    private IntentResult parse(String body) {
        JsonNode node = jsonMapper.readTree(body);
        JsonNode toolNode = node.get("toolName");
        String toolName = toolNode == null || toolNode.isNull() ? null : toolNode.asString();
        JsonNode confidenceNode = node.get("confidence");
        double confidence = confidenceNode == null ? 0.0 : confidenceNode.asDouble();
        return new IntentResult(toolName, Map.of(), confidence);
    }
}
