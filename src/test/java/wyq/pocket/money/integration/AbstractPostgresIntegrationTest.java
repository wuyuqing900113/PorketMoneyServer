package wyq.pocket.money.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Testcontainers PostgreSQL 18 集成测试公共基座（M1 设计 §12.2，D6）。
 *
 * <p>全部 PG 套件共享一个静态容器：仅启动一次，@ServiceConnection 装配
 * 连接参数；子类以一致属性共享 Spring 测试上下文缓存，个别子类追加
 * 属性（如缩短锁定时长）时获得独立上下文但复用同一容器。Docker 未就绪
 * 时 disabledWithoutDocker 整体跳过，mvn verify 保持常绿（设计 R1 托底）。
 *
 * <p>测试密钥为全零固定值，仅测试用，非任何环境真实密钥。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
public abstract class AbstractPostgresIntegrationTest {

    /** Testcontainers 2.x：PostgreSQLContainer 不再是泛型类（Spike S4 实测）。 */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    protected static final String DEFAULT_PASSWORD = "Passw0rd!";

    protected static final String CHILD_INITIAL_PASSWORD = "Init1234";

    protected static final String CHILD_NEW_PASSWORD = "ChildNew123";

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpRestAssuredPort() {
        RestAssured.port = port;
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
        Response login = loginAs(phone, DEFAULT_PASSWORD);
        login.then().statusCode(200).body("code", equalTo(0));
        return new TestAccount(register.jsonPath().getLong("data.userId"),
                register.jsonPath().getLong("data.familyId"),
                login.jsonPath().getString("data.accessToken"),
                login.jsonPath().getString("data.refreshToken"));
    }

    protected Response loginAs(String identifier, String password) {
        return post("/api/v1/auth/login", Map.of("identifier", identifier,
                "password", password));
    }

    protected RequestSpecification withToken(TestAccount account) {
        return withToken(account.accessToken());
    }

    protected RequestSpecification withToken(String accessToken) {
        return given().header("Authorization", "Bearer " + accessToken);
    }

    protected Map<String, Object> addChildBody(String username) {
        return Map.of("username", username, "password", CHILD_INITIAL_PASSWORD,
                "nickname", "孩子");
    }

    protected long createChild(TestAccount parent, String username) {
        Response response = withToken(parent).contentType(ContentType.JSON)
                .body(addChildBody(username)).when()
                .post("/api/v1/families/{familyId}/children", parent.familyId());
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getLong("data.userId");
    }

    /** 孩子登录 → 修改初始密码 → 以新密码重登，返回 mcp 解除后的令牌。 */
    protected String loginAndChangePassword(String username, String oldPassword,
                                            String newPassword) {
        Response login = loginAs(username, oldPassword);
        login.then().statusCode(200).body("code", equalTo(0));
        withToken(login.jsonPath().getString("data.accessToken"))
                .contentType(ContentType.JSON)
                .body(Map.of("oldPassword", oldPassword, "newPassword", newPassword))
                .when().post("/api/v1/users/me/password")
                .then().statusCode(200).body("code", equalTo(0));
        Response relogin = loginAs(username, newPassword);
        relogin.then().statusCode(200).body("code", equalTo(0))
                .body("data.mustChangePassword", equalTo(false));
        return relogin.jsonPath().getString("data.accessToken");
    }
}
