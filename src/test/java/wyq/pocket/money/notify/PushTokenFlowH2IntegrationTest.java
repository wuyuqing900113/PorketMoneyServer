package wyq.pocket.money.notify;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import wyq.pocket.money.support.IdempotencyKeys;

/**
 * 推送令牌注册 H2 托底集成测试（GA D68）：鸿蒙客户端登录后上报 HMS Push token，
 * 走真实过滤链 / MVC / MyBatis / Flyway(V1–V10)，验证 user_push_token 覆盖更新
 * 与校验失败 100001。令牌为敏感凭据，注册后不回显（仅 200 + code 0）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=jdbc:h2:mem:pocket_money_d68_token;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class PushTokenFlowH2IntegrationTest {

    private static final String DEFAULT_PASSWORD = "Passw0rd!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.replaceFiltersWith(IdempotencyKeys.uniqueKeyPerRequest());
    }

    private record TestAccount(long userId, long familyId, String accessToken) {
    }

    private TestAccount registerAndLogin(String phone) {
        Response register = given().contentType(ContentType.JSON)
                .body(Map.of("phone", phone, "password", DEFAULT_PASSWORD, "nickname", "家长",
                        "childPrivacyPolicyAccepted", true))
                .when().post("/api/v1/auth/register");
        register.then().statusCode(200).body("code", equalTo(0));
        Response login = given().contentType(ContentType.JSON)
                .body(Map.of("identifier", phone, "password", DEFAULT_PASSWORD))
                .when().post("/api/v1/auth/login");
        login.then().statusCode(200).body("code", equalTo(0));
        return new TestAccount(register.jsonPath().getLong("data.userId"),
                register.jsonPath().getLong("data.familyId"),
                login.jsonPath().getString("data.accessToken"));
    }

    private Response registerToken(TestAccount account, String token) {
        return given().header("Authorization", "Bearer " + account.accessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("deviceToken", token))
                .when().post("/api/v1/notifications/push-token");
    }

    private String tokenOf(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT token FROM user_push_token WHERE user_id = ? AND provider = 'HARMONY'",
                String.class, userId);
    }

    private Integer tokenCount(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_push_token WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void registerShouldUpsertSingleTokenPerUser() {
        TestAccount parent = registerAndLogin("13970000001");

        registerToken(parent, "hms-token-v1").then().statusCode(200).body("code", equalTo(0));
        assertThat(tokenOf(parent.userId())).isEqualTo("hms-token-v1");
        assertThat(tokenCount(parent.userId())).isEqualTo(1);

        // 重复注册覆盖更新，仍为一条
        registerToken(parent, "hms-token-v2").then().statusCode(200).body("code", equalTo(0));
        assertThat(tokenOf(parent.userId())).isEqualTo("hms-token-v2");
        assertThat(tokenCount(parent.userId())).isEqualTo(1);
    }

    @Test
    void blankTokenShouldBeRejectedWithParamInvalid() {
        TestAccount parent = registerAndLogin("13970000002");

        registerToken(parent, "   ").then().statusCode(200).body("code", equalTo(100001));
        assertThat(tokenCount(parent.userId())).isZero();
    }
}
