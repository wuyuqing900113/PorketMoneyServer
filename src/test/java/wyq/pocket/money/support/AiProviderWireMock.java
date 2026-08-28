package wyq.pocket.money.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * AI provider HTTP 桩（M6 设计 §5.1 D49）：基于 WireMock 内嵌服务器，为
 * {@link WireMockChatPort} 提供可切换的故障场景（超时 / 5xx / 畸形响应 / 成功）。
 *
 * <p>每个场景对应 LLM 端点的一条 stub；测试中先选场景再触发调用，验证 AI
 * 意图链路在真实 HTTP 语义下的降级与熔断。动态端口避免与并行测试冲突。
 */
public class AiProviderWireMock {

    /** LLM 端点路径（对齐 OpenAI 兼容接口约定）。 */
    public static final String LLM_PATH = "/v1/chat/completions";

    private final WireMockServer server;

    /**
     * 启动内嵌 WireMock 服务器（动态端口）。
     */
    public AiProviderWireMock() {
        this.server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        this.server.start();
    }

    /**
     * 返回 AI provider 端点完整 URL（含 LLM 路径）。
     *
     * @return 如 http://localhost:PORT/v1/chat/completions
     */
    public String endpoint() {
        return server.baseUrl() + LLM_PATH;
    }

    /**
     * 配置成功响应：HTTP 200 + 合法意图 JSON。
     */
    public void stubSuccess() {
        stub(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"toolName\":\"BALANCE_QUERY\",\"confidence\":0.95}"));
    }

    /**
     * 配置服务端错误：HTTP 500。
     */
    public void stubServerError() {
        stub(WireMock.aResponse().withStatus(500));
    }

    /**
     * 配置响应超时：固定延迟超过 TimeLimiter 超时阈值。
     */
    public void stubTimeout() {
        stub(WireMock.aResponse().withFixedDelay(1000));
    }

    /**
     * 配置畸形响应：HTTP 200 但 body 非法 JSON。
     */
    public void stubMalformed() {
        stub(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{not-valid-json"));
    }

    /**
     * 关闭内嵌服务器，释放端口与线程。
     */
    public void stop() {
        server.stop();
    }

    private void stub(ResponseDefinitionBuilder response) {
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo(LLM_PATH)).willReturn(response));
    }
}
