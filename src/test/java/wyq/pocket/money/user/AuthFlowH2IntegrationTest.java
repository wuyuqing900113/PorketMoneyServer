package wyq.pocket.money.user;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 认证全链路 H2 托底集成测试（M1 设计 §12.2 Docker 未就绪期间的主验证）：
 * RestAssured 走真实过滤链 / MVC / MyBatis / Flyway(V2、V3)，
 * 覆盖注册→登录→me→刷新轮转→重用检测→登出、锁定前置场景与异常分支。
 *
 * <p>与 SecuritySmokeIntegrationTest 使用完全一致的上下文属性，
 * 共享 Spring 测试上下文缓存；各用例使用独立手机号避免同库冲突。
 * Testcontainers PG 套件（T7）就绪后与本套件互为补充。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=jdbc:h2:mem:pocket_money_m1_spike;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class AuthFlowH2IntegrationTest {

    private static final String DEFAULT_PASSWORD = "Passw0rd!";

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private record TestAccount(long userId, long familyId, String accessToken,
                               String refreshToken) {
    }

    private Map<String, Object> registerBody(String phone) {
        return Map.of("phone", phone, "password", DEFAULT_PASSWORD, "nickname", "家长",
                "childPrivacyPolicyAccepted", true);
    }

    private Response post(String path, Object body) {
        return given().contentType(ContentType.JSON).body(body).when().post(path);
    }

    private TestAccount registerAndLogin(String phone) {
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

    @Test
    void fullAuthFlowShouldWorkEndToEnd() {
        TestAccount account = registerAndLogin("13900000001");
        assertThat(account.userId()).isPositive();
        assertThat(account.familyId()).isPositive();

        // 个人信息：手机号脱敏回显
        Response me = given().header("Authorization", "Bearer " + account.accessToken())
                .when().get("/api/v1/users/me");
        me.then().statusCode(200).body("code", equalTo(0))
                .body("data.maskedPhone", equalTo("139****0001"))
                .body("data.role", equalTo("PARENT"));
        assertThat(me.jsonPath().getLong("data.userId")).isEqualTo(account.userId());
        assertThat(me.jsonPath().getLong("data.familyId")).isEqualTo(account.familyId());

        // 刷新轮转：新令牌生效，旧令牌作废
        Response refresh = post("/api/v1/auth/refresh",
                Map.of("refreshToken", account.refreshToken()));
        refresh.then().statusCode(200).body("code", equalTo(0))
                .body("data.expiresIn", equalTo(900));
        String rotated = refresh.jsonPath().getString("data.refreshToken");
        assertThat(rotated).isNotEqualTo(account.refreshToken());

        // 重用已轮转令牌 → 重用检测 → 100003，且该用户全部令牌吊销
        post("/api/v1/auth/refresh", Map.of("refreshToken", account.refreshToken()))
                .then().statusCode(200).body("code", equalTo(100003));
        post("/api/v1/auth/refresh", Map.of("refreshToken", rotated))
                .then().statusCode(200).body("code", equalTo(100003));

        // 重新登录后登出，登出后 refresh 被拒
        TestAccount again = loginOnly("13900000001");
        given().header("Authorization", "Bearer " + again.accessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("refreshToken", again.refreshToken()))
                .when().post("/api/v1/auth/logout")
                .then().statusCode(200).body("code", equalTo(0));
        post("/api/v1/auth/refresh", Map.of("refreshToken", again.refreshToken()))
                .then().statusCode(200).body("code", equalTo(100003));
    }

    private TestAccount loginOnly(String phone) {
        Response login = post("/api/v1/auth/login",
                Map.of("identifier", phone, "password", DEFAULT_PASSWORD));
        login.then().statusCode(200).body("code", equalTo(0));
        return new TestAccount(login.jsonPath().getLong("data.user.userId"),
                login.jsonPath().getLong("data.user.familyId"),
                login.jsonPath().getString("data.accessToken"),
                login.jsonPath().getString("data.refreshToken"));
    }

    @Test
    void wrongPasswordShouldReturn200002() {
        registerAndLogin("13900000002");

        post("/api/v1/auth/login", Map.of("identifier", "13900000002", "password", "WrongPass1"))
                .then().statusCode(200).body("code", equalTo(200002));
        post("/api/v1/auth/login", Map.of("identifier", "13900000009", "password", "WrongPass1"))
                .then().statusCode(200).body("code", equalTo(200002));
    }

    @Test
    void duplicatePhoneShouldReturn200001() {
        registerAndLogin("13900000003");

        post("/api/v1/auth/register", registerBody("13900000003"))
                .then().statusCode(200).body("code", equalTo(200001));
    }

    @Test
    void invalidRegisterPayloadShouldReturn100001() {
        post("/api/v1/auth/register", Map.of("phone", "123", "password", "weak",
                "nickname", "家长", "childPrivacyPolicyAccepted", true))
                .then().statusCode(200).body("code", equalTo(100001));
    }

    @Test
    void registerWithoutConsentShouldReturn100001() {
        post("/api/v1/auth/register", Map.of("phone", "13900000007", "password",
                DEFAULT_PASSWORD, "nickname", "家长", "childPrivacyPolicyAccepted", false))
                .then().statusCode(200).body("code", equalTo(100001));
    }

    @Test
    void protectedEndpointWithGarbageTokenShouldReturn401() {
        given().header("Authorization", "Bearer not-a-jwt")
                .when().get("/api/v1/users/me")
                .then().statusCode(401).body("code", equalTo(100003))
                .header("X-Trace-Id", notNullValue());
    }

    @Test
    void changePasswordShouldRevokeTokensAndAllowRelogin() {
        TestAccount account = registerAndLogin("13900000004");

        given().header("Authorization", "Bearer " + account.accessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("oldPassword", DEFAULT_PASSWORD, "newPassword", "NewPassw0rd!"))
                .when().post("/api/v1/users/me/password")
                .then().statusCode(200).body("code", equalTo(0));

        // 改密后旧 refresh 全部吊销（§4.3 吊销时机表）
        post("/api/v1/auth/refresh", Map.of("refreshToken", account.refreshToken()))
                .then().statusCode(200).body("code", equalTo(100003));
        // 新密码可登录，旧密码被拒
        post("/api/v1/auth/login", Map.of("identifier", "13900000004", "password",
                "NewPassw0rd!")).then().statusCode(200).body("code", equalTo(0));
        post("/api/v1/auth/login", Map.of("identifier", "13900000004", "password",
                DEFAULT_PASSWORD)).then().statusCode(200).body("code", equalTo(200002));
    }

    @Test
    void changePasswordWithWrongOldShouldReturn200008() {
        TestAccount account = registerAndLogin("13900000006");

        given().header("Authorization", "Bearer " + account.accessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("oldPassword", "WrongPass1", "newPassword", "NewPassw0rd!"))
                .when().post("/api/v1/users/me/password")
                .then().statusCode(200).body("code", equalTo(200008));
    }
}
