package wyq.pocket.money.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestExecutionListeners;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import wyq.pocket.money.integration.support.PgDatabaseResetListener;
import wyq.pocket.money.support.IdempotencyKeys;

/**
 * Testcontainers PostgreSQL 18 集成测试公共基座（M1 设计 §12.2，D6）。
 *
 * <p>全部 PG 套件共享一个<b>单例容器</b>：容器以静态初始化块<b>手动启动一次</b>，
 * 不用 {@code @Container} 交由 JUnit 扩展托管——扩展会把容器登记在每个子类
 * class-scope 的 ExtensionContext store 中，子类结束即关闭容器，下一个子类重启
 * 得到新映射端口，而 Spring 上下文缓存里的连接池仍指向旧端口，导致连接被拒。
 * 手动启动的单例容器全 JVM 一份、端口稳定、JVM 退出时由 Ryuk 回收；
 * {@code @ServiceConnection} 据此装配连接参数，子类以一致属性共享 Spring 测试
 * 上下文缓存（追加属性的子类获得独立上下文但仍复用同一容器）。Docker 未就绪时
 * 不启动容器并由 {@code disabledWithoutDocker} 整体跳过，mvn verify 保持常绿。
 *
 * <p><b>跨类隔离</b>：单例库跨测试类共享，先执行类遗留数据会污染后执行类断言
 * （固定号段手机号撞 200001、遗留 ACTIVE 规则进入结算聚合使计数偏离），故经
 * {@link PgDatabaseResetListener} 在<b>每个测试类开始前</b>（先于子类 {@code @BeforeAll}）
 * 清空全部业务表并复位序列——等价于 H2 套件「每类一个独立内存库」，同时保留类内
 * {@code @BeforeAll} 共享夹具。
 *
 * <p>测试密钥为全零固定值，仅测试用，非任何环境真实密钥。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@TestExecutionListeners(
        listeners = PgDatabaseResetListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractPostgresIntegrationTest {

    /** Testcontainers 2.x：PostgreSQLContainer 不再是泛型类（Spike S4 实测）。 */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        // 单例容器手动启动（见类 Javadoc）；Docker 不可用时不启动，交由 @Testcontainers 跳过。
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    protected static final String DEFAULT_PASSWORD = "Passw0rd!";

    protected static final String CHILD_INITIAL_PASSWORD = "Init1234";

    protected static final String CHILD_NEW_PASSWORD = "ChildNew123";

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpRestAssuredPort() {
        RestAssured.port = port;
        // M3 起认证写操作强制 Idempotency-Key；与 H2 套件一致自动注入唯一键（幂等专测显式带键不受影响）。
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
