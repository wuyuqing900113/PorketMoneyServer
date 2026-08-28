package wyq.pocket.money.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import wyq.pocket.money.support.IdempotencyKeys;

/**
 * M6 OWASP 安全专项测试 H2 托底基座（设计 §8）：RestAssured 走真实过滤链 /
 * MVC / MyBatis / Flyway，无需 Docker，与 Testcontainers PG 套件互为补充。
 *
 * <p>共享同一 H2 内存库与 Spring 测试上下文缓存；各用例以独立手机号注册，
 * 互不污染。测试密钥为全零固定值，仅测试用，非任何环境真实密钥。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=jdbc:h2:mem:pocket_money_m6_security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
abstract class AbstractH2SecurityIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "Passw0rd!";

    private static final AtomicLong COUNTER = new AtomicLong();

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.replaceFiltersWith(IdempotencyKeys.uniqueKeyPerRequest());
    }

    protected record TestAccount(long userId, long familyId, String accessToken,
                                 String refreshToken) {
    }

    protected Response post(String path, Object body) {
        return given().contentType(ContentType.JSON).body(body).when().post(path);
    }

    protected Map<String, Object> registerBody(String phone) {
        return Map.of("phone", phone, "password", DEFAULT_PASSWORD, "nickname", "家长",
                "childPrivacyPolicyAccepted", true);
    }

    protected TestAccount registerAndLogin(String phone) {
        Response register = post("/api/v1/auth/register", registerBody(phone));
        register.then().statusCode(200).body("code", equalTo(0));
        Response login = post("/api/v1/auth/login",
                Map.of("identifier", phone, "password", DEFAULT_PASSWORD));
        login.then().statusCode(200).body("code", equalTo(0));
        return new TestAccount(register.jsonPath().getLong("data.userId"),
                register.jsonPath().getLong("data.familyId"),
                login.jsonPath().getString("data.accessToken"),
                login.jsonPath().getString("data.refreshToken"));
    }

    protected RequestSpecification withToken(TestAccount account) {
        return given().header("Authorization", "Bearer " + account.accessToken());
    }

    protected static String nextPhone() {
        return String.format("1398%07d", COUNTER.incrementAndGet());
    }
}
