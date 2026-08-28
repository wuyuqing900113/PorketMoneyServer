package wyq.pocket.money.notify.service.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import wyq.pocket.money.notify.config.NotifyProperties;

/**
 * {@link HarmonyPushPort} HTTP 契约测试（GA D68）：以 WireMock 模拟华为 OAuth2
 * token 端点与 messages:send 端点，验证令牌缓存、401 刷新重试、业务码判定与故障语义。
 * 敏感凭据（client_secret / access_token / 设备令牌）不出现在任何断言日志中。
 */
class HarmonyPushPortWireMockTest {

    private static final String TOKEN_PATH = "/oauth2/v3/token";

    private static final String SEND_PATH = "/v1/app123/messages:send";

    private static final String TOKEN_BODY =
            "{\"access_token\":\"test-access-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";

    private static final String SUCCESS_BODY = "{\"code\":\"80000000\",\"msg\":\"Success\"}";

    private WireMockServer server;

    private HarmonyPushPort pushPort;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        NotifyProperties.Push.Harmony harmony = new NotifyProperties.Push.Harmony(
                "app123", "test-client-id", "test-client-secret",
                server.baseUrl() + TOKEN_PATH, server.baseUrl());
        pushPort = new HarmonyPushPort(harmony);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void shouldFetchTokenThenSendAndReturnTrue() {
        stubToken(200, TOKEN_BODY);
        stubSend(200, SUCCESS_BODY);

        boolean accepted = pushPort.send(100L, 42L, "device-token", "零花钱入账", "你收到 50.00 元");

        assertThat(accepted).isTrue();
        server.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(TOKEN_PATH)));
        server.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(SEND_PATH))
                .withHeader("Authorization", WireMock.equalTo("Bearer test-access-token")));
    }

    @Test
    void shouldCacheTokenAcrossSends() {
        stubToken(200, TOKEN_BODY);
        stubSend(200, SUCCESS_BODY);

        pushPort.send(100L, 42L, "device-token", "标题一", "正文一");
        pushPort.send(101L, 42L, "device-token", "标题二", "正文二");

        server.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(TOKEN_PATH)));
        server.verify(2, WireMock.postRequestedFor(WireMock.urlPathEqualTo(SEND_PATH)));
    }

    @Test
    void shouldRefreshTokenOn401AndRetryOnce() {
        stubToken(200, TOKEN_BODY);
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo(SEND_PATH))
                .inScenario("token-expiry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(401))
                .willSetStateTo("expired"));
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo(SEND_PATH))
                .inScenario("token-expiry")
                .whenScenarioStateIs("expired")
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        boolean accepted = pushPort.send(100L, 42L, "device-token", "零花钱入账", "正文");

        assertThat(accepted).isTrue();
        server.verify(2, WireMock.postRequestedFor(WireMock.urlPathEqualTo(TOKEN_PATH)));
        server.verify(2, WireMock.postRequestedFor(WireMock.urlPathEqualTo(SEND_PATH)));
    }

    @Test
    void shouldReturnFalseOnServerError() {
        stubToken(200, TOKEN_BODY);
        stubSend(500, "{\"code\":\"80300002\",\"msg\":\"Internal error\"}");

        boolean accepted = pushPort.send(100L, 42L, "device-token", "标题", "正文");

        assertThat(accepted).isFalse();
    }

    @Test
    void shouldReturnFalseOnBizFailureCode() {
        stubToken(200, TOKEN_BODY);
        stubSend(200, "{\"code\":\"80300007\",\"msg\":\"Invalid token\"}");

        boolean accepted = pushPort.send(100L, 42L, "device-token", "标题", "正文");

        assertThat(accepted).isFalse();
    }

    @Test
    void shouldReturnFalseWithoutHttpCallWhenDeviceTokenBlank() {
        boolean accepted = pushPort.send(100L, 42L, "  ", "标题", "正文");

        assertThat(accepted).isFalse();
        server.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo(TOKEN_PATH)));
        server.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo(SEND_PATH)));
    }

    @Test
    void shouldThrowWhenTokenEndpointFails() {
        stubToken(500, "{}");

        assertThatThrownBy(() -> pushPort.send(100L, 42L, "device-token", "标题", "正文"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAuth token 获取失败");
        server.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo(SEND_PATH)));
    }

    private void stubToken(int status, String body) {
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo(TOKEN_PATH))
                .willReturn(WireMock.aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubSend(int status, String body) {
        server.stubFor(WireMock.post(WireMock.urlPathEqualTo(SEND_PATH))
                .willReturn(WireMock.aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
