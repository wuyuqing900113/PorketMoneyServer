package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import wyq.pocket.money.common.log.MaskingRules;

/**
 * 认证主链路集成测试（M1 设计 §12.2 AuthFlowIT，真 PostgreSQL 18）。
 *
 * <p>注册 → 登录 → me（手机号脱敏）→ 刷新轮转 → 登出 → 登出后 refresh 被拒。
 */
class AuthFlowPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PHONE = "13910000001";

    @Test
    void fullAuthFlowShouldWorkEndToEnd() {
        TestAccount account = registerAndLogin(PHONE);

        withToken(account).when().get("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.role", equalTo("PARENT"))
                .body("data.familyId", equalTo((int) account.familyId()))
                .body("data.maskedPhone", equalTo(MaskingRules.mask(PHONE)));

        // 刷新轮转：新令牌对可用
        Response refreshed = post("/api/v1/auth/refresh",
                Map.of("refreshToken", account.refreshToken()));
        refreshed.then().statusCode(200).body("code", equalTo(0))
                .body("data.accessToken", notNullValue())
                .body("data.refreshToken", notNullValue());
        String rotatedAccess = refreshed.jsonPath().getString("data.accessToken");
        String rotatedRefresh = refreshed.jsonPath().getString("data.refreshToken");

        withToken(rotatedAccess).when().get("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(0));

        // 登出吊销 refresh
        withToken(rotatedAccess).contentType(ContentType.JSON)
                .body(Map.of("refreshToken", rotatedRefresh))
                .when().post("/api/v1/auth/logout")
                .then().statusCode(200).body("code", equalTo(0));

        // 登出后 refresh 被拒（统一 100003，失败原因不可区分）
        post("/api/v1/auth/refresh", Map.of("refreshToken", rotatedRefresh))
                .then().statusCode(200).body("code", equalTo(100003));
    }

    @Test
    void loginResponseShouldCarryTokenMetadata() {
        post("/api/v1/auth/register", registerBody("13910000002"))
                .then().statusCode(200).body("code", equalTo(0));
        loginAs("13910000002", DEFAULT_PASSWORD)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.expiresIn", greaterThan(0))
                .body("data.mustChangePassword", equalTo(false))
                .body("data.role", equalTo("PARENT"));
    }
}
