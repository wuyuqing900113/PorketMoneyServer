package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 孩子首次登录强制改密集成测试（M1 设计 §12.2 ChildFirstLoginIT / §5.4）。
 *
 * <p>mcp 置位期间除改密 / 登出外一律 200 + 200010；改密成功后放行，
 * 旧口令失效。
 */
class ChildFirstLoginPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PHONE = "13910000051";

    private static final String CHILD_USERNAME = "pgcfl01a";

    @Test
    void mustChangePasswordGateShouldBlockThenAllow() {
        TestAccount parent = registerAndLogin(PHONE);
        createChild(parent, CHILD_USERNAME);

        // mcp 期间：普通业务端点被 200010 拦截
        String initialToken = loginAs(CHILD_USERNAME, CHILD_INITIAL_PASSWORD)
                .jsonPath().getString("data.accessToken");
        withToken(initialToken).when().get("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(200010));

        // 改密端点在 mcp 白名单内，正常放行
        withToken(initialToken).contentType(ContentType.JSON)
                .body(Map.of("oldPassword", CHILD_INITIAL_PASSWORD,
                        "newPassword", CHILD_NEW_PASSWORD))
                .when().post("/api/v1/users/me/password")
                .then().statusCode(200).body("code", equalTo(0));

        // 改密后业务放行（mcp 解除），旧口令失效
        Response relogin = loginAs(CHILD_USERNAME, CHILD_NEW_PASSWORD);
        relogin.then().statusCode(200).body("code", equalTo(0))
                .body("data.mustChangePassword", equalTo(false));
        withToken(relogin.jsonPath().getString("data.accessToken"))
                .when().get("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(0));
        loginAs(CHILD_USERNAME, CHILD_INITIAL_PASSWORD)
                .then().statusCode(200).body("code", equalTo(200002));
    }
}
