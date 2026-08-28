package wyq.pocket.money.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * XSS 专项（M6 设计 §8.2）：存储型载荷写入昵称后原样落库（无富文本执行面），
 * 出参为 JSON 字符串（非 HTML 渲染），客户端渲染时自行转义。
 *
 * <p>本服务无 HTML 渲染出口，接口为 JSON，故不引入 HTML sanitizer；此处
 * 验证注入串不作为结构破坏地 round-trip，且响应内容类型确为 JSON。
 */
class XssSecurityTest extends AbstractH2SecurityIntegrationTest {

    @Test
    void scriptNicknameShouldRoundTripAsJsonString() {
        TestAccount account = registerAndLogin(nextPhone());
        String payload = "<script>alert(1)</script>";

        withToken(account).contentType(ContentType.JSON)
                .body(Map.of("nickname", payload)).when()
                .put("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(0));

        Response me = withToken(account).when().get("/api/v1/users/me");
        me.then().statusCode(200).body("code", equalTo(0))
                .header("Content-Type", containsString("application/json"))
                .body("data.nickname", equalTo(payload));
    }

    @Test
    void imgOnErrorNicknameShouldRoundTripAsJsonString() {
        TestAccount account = registerAndLogin(nextPhone());
        String payload = "<img src=x onerror=alert(1)>";

        withToken(account).contentType(ContentType.JSON)
                .body(Map.of("nickname", payload)).when()
                .put("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(0));

        withToken(account).when().get("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.nickname", equalTo(payload));
    }
}
