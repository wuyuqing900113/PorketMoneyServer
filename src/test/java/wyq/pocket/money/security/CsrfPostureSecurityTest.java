package wyq.pocket.money.security;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * CSRF 姿态专项（M6 设计 §8.1）：纯 Bearer API、会话 STATELESS、无 Cookie
 * 会话，浏览器不会自动附带凭据，故 CSRF 攻击面不成立。此处以断言「无
 * {@code Set-Cookie}、未认证即 401」记录正当性（记为不适用 + 理由，而非跳过）。
 */
class CsrfPostureSecurityTest extends AbstractH2SecurityIntegrationTest {

    @Test
    void protectedEndpointShouldReturn401WithoutSessionCookie() {
        Response response = given().when().get("/api/v1/users/me");

        response.then().statusCode(401).body("code", equalTo(100003));
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void publicEndpointShouldNotEstablishSession() {
        Response response = post("/api/v1/auth/login",
                Map.of("identifier", "nobody", "password", "Whatever1"));

        response.then().statusCode(200);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }
}
