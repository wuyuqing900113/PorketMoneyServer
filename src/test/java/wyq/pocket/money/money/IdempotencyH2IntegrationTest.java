package wyq.pocket.money.money;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * 幂等拦截 H2 托底集成测试（M3 设计 §5）：RestAssured 走真实过滤链 / MVC /
 * MyBatis / Flyway(V7)，以资金存入接口验证幂等四态：缺失键 → 100008、
 * 重放返回原响应、同键不同体 → 100009、不同键各自记账。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=jdbc:h2:mem:pocket_money_m1_spike;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class IdempotencyH2IntegrationTest {

    private static final String DEFAULT_PASSWORD = "Passw0rd!";

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        // 清空可能由其他测试类遗留的全局过滤器，确保显式幂等键测试不被干扰
        RestAssured.reset();
        RestAssured.port = port;
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

    private Response deposit(TestAccount account, String key, String amount) {
        String body = "{\"targetUserId\":" + account.userId() + ",\"amount\":" + amount + "}";
        RequestSpecification request = given()
                .header("Authorization", "Bearer " + account.accessToken())
                .contentType(ContentType.JSON)
                .body(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request.when()
                .post("/api/v1/families/{familyId}/deposits", account.familyId());
    }

    @Test
    void missingKeyShouldReturn100008() {
        TestAccount account = registerAndLogin("13900000201");

        deposit(account, null, "100")
                .then().statusCode(200).body("code", equalTo(100008));
    }

    @Test
    void replaySameKeyAndBodyShouldReturnCachedResponse() {
        TestAccount account = registerAndLogin("13900000202");

        Response first = deposit(account, "idem-replay-001", "100");
        first.then().statusCode(200).body("code", equalTo(0));
        long firstTx = first.jsonPath().getLong("data.transactionId");

        Response second = deposit(account, "idem-replay-001", "100");
        second.then().statusCode(200).body("code", equalTo(0));

        assertThat(second.jsonPath().getLong("data.transactionId")).isEqualTo(firstTx);
    }

    @Test
    void sameKeyDifferentBodyShouldReturn100009() {
        TestAccount account = registerAndLogin("13900000203");

        deposit(account, "idem-conflict-001", "100")
                .then().statusCode(200).body("code", equalTo(0));

        deposit(account, "idem-conflict-001", "200")
                .then().statusCode(200).body("code", equalTo(100009));
    }

    @Test
    void differentKeysShouldCreateDistinctTransactions() {
        TestAccount account = registerAndLogin("13900000204");

        Response first = deposit(account, "idem-distinct-001", "100");
        first.then().statusCode(200).body("code", equalTo(0));
        Response second = deposit(account, "idem-distinct-002", "100");
        second.then().statusCode(200).body("code", equalTo(0));

        assertThat(second.jsonPath().getLong("data.transactionId"))
                .isNotEqualTo(first.jsonPath().getLong("data.transactionId"));
    }
}
