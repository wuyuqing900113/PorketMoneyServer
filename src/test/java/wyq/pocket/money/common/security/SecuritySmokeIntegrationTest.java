package wyq.pocket.money.common.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;

/**
 * M1 Spike S1/S3/S5：Boot 4 Security 无状态过滤链、RestAssured(RANDOM_PORT)、
 * MyBatis 与 Security 自动配置共存。
 *
 * <p>H2 内存库（PostgreSQL 兼容模式）+ 固定测试密钥（全零值，
 * 仅测试用，非任何环境真实密钥）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=jdbc:h2:mem:pocket_money_m1_spike;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class SecuritySmokeIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void healthEndpointShouldRemainPublic() {
        given().when().get("/actuator/health/liveness")
                .then().statusCode(200);
    }

    @Test
    void openApiDocsShouldRemainPublic() {
        given().when().get("/v3/api-docs")
                .then().statusCode(200);
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturn401WithResult100003() {
        given().when().get("/api/v1/users/me")
                .then().statusCode(401)
                .body("code", equalTo(100003))
                .header("X-Trace-Id", notNullValue());
    }
}
