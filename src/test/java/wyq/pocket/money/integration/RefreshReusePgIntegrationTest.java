package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * refresh 重用检测集成测试（M1 设计 §12.2 RefreshReuseIT / §4.3）。
 *
 * <p>已吊销令牌再次出现即判定泄露：100003 + 吊销该用户全部令牌 +
 * ERROR 安全告警（告警落日志不落断言，避免文件耦合）；用户可重新登录恢复。
 */
class RefreshReusePgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PHONE = "13910000021";

    @Test
    void reusedRefreshTokenShouldTriggerRevokeAll() {
        TestAccount account = registerAndLogin(PHONE);
        String oldRefresh = account.refreshToken();

        // 正常轮转：旧令牌即刻作废，新令牌对生效
        Response rotated = post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh));
        rotated.then().statusCode(200).body("code", equalTo(0));
        String newRefresh = rotated.jsonPath().getString("data.refreshToken");

        // 重放已吊销旧令牌：重用检测 → 100003 + 全吊销
        post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh))
                .then().statusCode(200).body("code", equalTo(100003));

        // 轮转出的新令牌一并被殃及（泄露止损）
        post("/api/v1/auth/refresh", Map.of("refreshToken", newRefresh))
                .then().statusCode(200).body("code", equalTo(100003));

        // 重新验证口令后登录恢复
        loginAs(PHONE, DEFAULT_PASSWORD)
                .then().statusCode(200).body("code", equalTo(0));
    }
}
