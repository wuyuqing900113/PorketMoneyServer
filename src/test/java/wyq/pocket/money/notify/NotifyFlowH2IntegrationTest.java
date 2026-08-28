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
 * 通知全链路 H2 托底集成测试（M5 设计 §10.2，Docker 未就绪期间的主验证）：
 * 存入 → 账户主人收到 TX_IN 站内信；未读数 / 分页 / 标记已读 / 全部已读；
 * 读他人通知 700001。走真实过滤链 / MVC / MyBatis / Flyway(V1–V9)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=jdbc:h2:mem:pocket_money_m5_notify;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class NotifyFlowH2IntegrationTest {

    private static final String DEFAULT_PASSWORD = "Passw0rd!";

    private static final String CHILD_INITIAL_PASSWORD = "Init1234";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        // M3 起写操作强制要求幂等键，为所有 POST/PUT/DELETE 自动注入唯一键
        RestAssured.replaceFiltersWith(IdempotencyKeys.uniqueKeyPerRequest());
    }

    private record TestAccount(long userId, long familyId, String accessToken) {
    }

    private Response post(String path, Object body) {
        return given().contentType(ContentType.JSON).body(body).when().post(path);
    }

    private TestAccount registerAndLogin(String phone) {
        Response register = post("/api/v1/auth/register", Map.of("phone", phone,
                "password", DEFAULT_PASSWORD, "nickname", "家长",
                "childPrivacyPolicyAccepted", true));
        register.then().statusCode(200).body("code", equalTo(0));
        Response login = post("/api/v1/auth/login",
                Map.of("identifier", phone, "password", DEFAULT_PASSWORD));
        login.then().statusCode(200).body("code", equalTo(0));
        return new TestAccount(register.jsonPath().getLong("data.userId"),
                register.jsonPath().getLong("data.familyId"),
                login.jsonPath().getString("data.accessToken"));
    }

    private long createChild(TestAccount parent, String username) {
        Response response = given().header("Authorization", "Bearer " + parent.accessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", CHILD_INITIAL_PASSWORD,
                        "nickname", "孩子"))
                .when().post("/api/v1/families/{familyId}/children", parent.familyId());
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getLong("data.userId");
    }

    private Response deposit(TestAccount parent, long targetUserId, String amount) {
        return given().header("Authorization", "Bearer " + parent.accessToken())
                .contentType(ContentType.JSON)
                .body(Map.of("targetUserId", targetUserId, "amount", amount, "remark", "测试存入"))
                .when().post("/api/v1/families/{familyId}/deposits", parent.familyId());
    }

    @Test
    void depositShouldNotifyOwnerAndSupportReadLifecycle() {
        TestAccount parent = registerAndLogin("13950000001");
        long childId = createChild(parent, "notifychild1");

        // 存入家长自身账户 → 账户主人收到 TX_IN 站内信
        deposit(parent, parent.userId(), "50.00")
                .then().statusCode(200).body("code", equalTo(0));

        Response page = given().header("Authorization", "Bearer " + parent.accessToken())
                .when().get("/api/v1/notifications");
        page.then().statusCode(200).body("code", equalTo(0))
                .body("data.total", equalTo(1))
                .body("data.records[0].type", equalTo("TX_IN"))
                .body("data.records[0].title", equalTo("零花钱入账"));
        assertThat(page.jsonPath().getString("data.records[0].content")).contains("50.00");
        long notifId = page.jsonPath().getLong("data.records[0].id");

        // 未读数 = 1
        given().header("Authorization", "Bearer " + parent.accessToken())
                .when().get("/api/v1/notifications/unread-count")
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.count", equalTo(1));

        // 标记已读 → 未读数归零
        given().header("Authorization", "Bearer " + parent.accessToken())
                .when().post("/api/v1/notifications/{id}/read", notifId)
                .then().statusCode(200).body("code", equalTo(0));
        given().header("Authorization", "Bearer " + parent.accessToken())
                .when().get("/api/v1/notifications/unread-count")
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.count", equalTo(0));

        // 存入孩子账户 → 孩子收到通知；家长读他人通知 → 700001
        deposit(parent, childId, "30.00").then().statusCode(200).body("code", equalTo(0));
        Long childNotifId = jdbcTemplate.queryForObject(
                "SELECT id FROM notification WHERE user_id = ?", Long.class, childId);
        given().header("Authorization", "Bearer " + parent.accessToken())
                .when().post("/api/v1/notifications/{id}/read", childNotifId)
                .then().statusCode(200).body("code", equalTo(700001));

        // 再次存入自身 → 全部已读 → 未读数归零
        deposit(parent, parent.userId(), "10.00")
                .then().statusCode(200).body("code", equalTo(0));
        given().header("Authorization", "Bearer " + parent.accessToken())
                .when().post("/api/v1/notifications/read-all")
                .then().statusCode(200).body("code", equalTo(0));
        given().header("Authorization", "Bearer " + parent.accessToken())
                .when().get("/api/v1/notifications/unread-count")
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.count", equalTo(0));
    }
}
