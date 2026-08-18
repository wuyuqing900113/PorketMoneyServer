package wyq.pocket.money.user;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
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
 * 家庭域 H2 托底集成测试（M1 设计 §6 / §12.2 Docker 未就绪期间的主验证）：
 * RestAssured 走真实过滤链 / MVC / MyBatis / Flyway，覆盖家庭读取、改名、
 * 创建孩子（上限 / 登录名占用 / mcp 强制首改）、重置密码、移除约束与
 * 跨家庭 / 跨角色越权边界（附录 B 权限矩阵节选）。
 *
 * <p>与 AuthFlowH2IntegrationTest 使用完全一致的上下文属性，
 * 共享 Spring 测试上下文缓存；各用例使用独立手机号与登录名避免同库冲突。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_URL=jdbc:h2:mem:pocket_money_m1_spike;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa",
        "DB_PASSWORD=",
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class FamilyFlowH2IntegrationTest {

    private static final String DEFAULT_PASSWORD = "Passw0rd!";

    private static final String CHILD_INITIAL_PASSWORD = "Init1234";

    private static final String CHILD_NEW_PASSWORD = "ChildNew123";

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

    private RequestSpecification withToken(TestAccount account) {
        return withToken(account.accessToken());
    }

    private RequestSpecification withToken(String accessToken) {
        return given().header("Authorization", "Bearer " + accessToken);
    }

    private Map<String, Object> addChildBody(String username) {
        return Map.of("username", username, "password", CHILD_INITIAL_PASSWORD,
                "nickname", "孩子");
    }

    private long createChild(TestAccount parent, String username) {
        Response response = withToken(parent).contentType(ContentType.JSON)
                .body(addChildBody(username)).when()
                .post("/api/v1/families/{familyId}/children", parent.familyId());
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getLong("data.userId");
    }

    private Response loginAs(String identifier, String password) {
        return post("/api/v1/auth/login", Map.of("identifier", identifier,
                "password", password));
    }

    /** 孩子登录 → 修改初始密码 → 以新密码重登，返回 mcp 解除后的令牌。 */
    private String loginAndChangePassword(String username, String oldPassword,
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

    @Test
    void fullFamilyFlowShouldWorkEndToEnd() {
        TestAccount parent = registerAndLogin("13900000101");

        // 注册即建家庭：/users/me/family 可查自身家庭
        Response myFamily = withToken(parent).when().get("/api/v1/users/me/family");
        myFamily.then().statusCode(200).body("code", equalTo(0))
                .body("data.members.size()", equalTo(1));
        assertThat(myFamily.jsonPath().getLong("data.familyId")).isEqualTo(parent.familyId());
        assertThat(myFamily.jsonPath().getLong("data.ownerUserId")).isEqualTo(parent.userId());

        // 家长改家庭名
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("familyName", "零花钱测试家庭"))
                .when().put("/api/v1/families/{familyId}", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0));

        // 创建孩子：mcp=true（孩子无手机号 / 邮箱，COPPA）
        Response addChild = withToken(parent).contentType(ContentType.JSON)
                .body(addChildBody("xm101a"))
                .when().post("/api/v1/families/{familyId}/children", parent.familyId());
        addChild.then().statusCode(200).body("code", equalTo(0))
                .body("data.username", equalTo("xm101a"))
                .body("data.role", equalTo("CHILD"))
                .body("data.mustChangePassword", equalTo(true));
        long childId = addChild.jsonPath().getLong("data.userId");

        // 成员列表：2 人
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/members", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.size()", equalTo(2));

        // 孩子首次登录：mcp=true，强制期内 /users/me 被拒（200010）
        Response childLogin = loginAs("xm101a", CHILD_INITIAL_PASSWORD);
        childLogin.then().statusCode(200).body("code", equalTo(0))
                .body("data.mustChangePassword", equalTo(true));
        withToken(childLogin.jsonPath().getString("data.accessToken"))
                .when().get("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(200010));

        // 修改初始密码后重登，mcp 解除，可读家庭详情
        String childToken = loginAndChangePassword("xm101a", CHILD_INITIAL_PASSWORD,
                CHILD_NEW_PASSWORD);
        Response detail = withToken(childToken).when()
                .get("/api/v1/families/{familyId}", parent.familyId());
        detail.then().statusCode(200).body("code", equalTo(0))
                .body("data.familyName", equalTo("零花钱测试家庭"));
        assertThat(detail.jsonPath().getLong("data.ownerUserId")).isEqualTo(parent.userId());

        // 家长改孩子昵称，孩子视角的成员列表同步可见
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("nickname", "明明"))
                .when().put("/api/v1/families/{familyId}/children/{userId}",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));
        Response members = withToken(childToken).when()
                .get("/api/v1/families/{familyId}/members", parent.familyId());
        members.then().statusCode(200).body("code", equalTo(0));
        List<Map<String, Object>> roster = members.jsonPath().getList("data");
        assertThat(roster).hasSize(2);
        assertThat(roster.stream()
                .filter(member -> ((Number) member.get("userId")).longValue() == childId)
                .findFirst().orElseThrow().get("nickname")).isEqualTo("明明");
    }

    @Test
    void resetChildPasswordShouldForceChangeAgain() {
        TestAccount parent = registerAndLogin("13900000102");
        long childId = createChild(parent, "xm102a");

        // 初始密码可登录
        loginAs("xm102a", CHILD_INITIAL_PASSWORD)
                .then().statusCode(200).body("code", equalTo(0));

        // 家长重置密码
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("newPassword", "Reset1234"))
                .when().post("/api/v1/families/{familyId}/children/{userId}/password-reset",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));

        // 旧密码被拒；新密码可登录且 mcp 重新生效
        loginAs("xm102a", CHILD_INITIAL_PASSWORD)
                .then().statusCode(200).body("code", equalTo(200002));
        loginAs("xm102a", "Reset1234")
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.mustChangePassword", equalTo(true));
    }

    @Test
    void removeMemberShouldDisableChildAndBlockLogin() {
        TestAccount parent = registerAndLogin("13900000103");
        long childId = createChild(parent, "xm103a");

        // 创建者不可移除：200012
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        parent.familyId(), parent.userId())
                .then().statusCode(200).body("code", equalTo(200012));

        // 家庭外用户不可移除：200011
        TestAccount stranger = registerAndLogin("13900000104");
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        parent.familyId(), stranger.userId())
                .then().statusCode(200).body("code", equalTo(200011));

        // 正常移除孩子
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/members", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.size()", equalTo(1));

        // 被移除孩子账号 DISABLED，无法再登录：200004
        loginAs("xm103a", CHILD_INITIAL_PASSWORD)
                .then().statusCode(200).body("code", equalTo(200004));
    }

    @Test
    void crossFamilyAccessShouldReturn403() {
        TestAccount familyA = registerAndLogin("13900000105");
        TestAccount familyB = registerAndLogin("13900000106");

        // B 家长读写 A 家庭、向 A 家庭创建孩子：数据级越权 403 + 100004
        withToken(familyB).when()
                .get("/api/v1/families/{familyId}", familyA.familyId())
                .then().statusCode(403).body("code", equalTo(100004));
        withToken(familyB).contentType(ContentType.JSON)
                .body(Map.of("familyName", "越权"))
                .when().put("/api/v1/families/{familyId}", familyA.familyId())
                .then().statusCode(403).body("code", equalTo(100004));
        withToken(familyB).contentType(ContentType.JSON)
                .body(addChildBody("xm105x"))
                .when().post("/api/v1/families/{familyId}/children", familyA.familyId())
                .then().statusCode(403).body("code", equalTo(100004));

        // B 家庭孩子读 A 家庭：数据级越权 403 + 100004
        long childBId = createChild(familyB, "xm106a");
        String childBToken = loginAndChangePassword("xm106a", CHILD_INITIAL_PASSWORD,
                CHILD_NEW_PASSWORD);
        withToken(childBToken).when()
                .get("/api/v1/families/{familyId}", familyA.familyId())
                .then().statusCode(403).body("code", equalTo(100004));
        assertThat(childBId).isPositive();
    }

    @Test
    void childCallingParentEndpointsShouldReturn403() {
        TestAccount parent = registerAndLogin("13900000107");
        long childId = createChild(parent, "xm107a");
        String childToken = loginAndChangePassword("xm107a", CHILD_INITIAL_PASSWORD,
                CHILD_NEW_PASSWORD);

        // 接口级：写操作一律限 PARENT（方法安全，403 + 100004）
        withToken(childToken).contentType(ContentType.JSON)
                .body(Map.of("familyName", "孩子"))
                .when().put("/api/v1/families/{familyId}", parent.familyId())
                .then().statusCode(403).body("code", equalTo(100004));
        withToken(childToken).contentType(ContentType.JSON)
                .body(addChildBody("xm107b"))
                .when().post("/api/v1/families/{familyId}/children", parent.familyId())
                .then().statusCode(403).body("code", equalTo(100004));
        withToken(childToken).contentType(ContentType.JSON)
                .body(Map.of("nickname", "逆子"))
                .when().put("/api/v1/families/{familyId}/children/{userId}",
                        parent.familyId(), childId)
                .then().statusCode(403).body("code", equalTo(100004));
        withToken(childToken).contentType(ContentType.JSON)
                .body(Map.of("newPassword", "ChildNew456"))
                .when().post("/api/v1/families/{familyId}/children/{userId}/password-reset",
                        parent.familyId(), childId)
                .then().statusCode(403).body("code", equalTo(100004));
        withToken(childToken).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        parent.familyId(), childId)
                .then().statusCode(403).body("code", equalTo(100004));
    }

    @Test
    void memberLimitShouldReturn200006() {
        TestAccount parent = registerAndLogin("13900000108");
        // 家长 + 7 孩子 = 8 人满员
        for (int i = 0; i < 7; i++) {
            createChild(parent, "xm108" + (char) ('a' + i));
        }

        withToken(parent).contentType(ContentType.JSON)
                .body(addChildBody("xm108h"))
                .when().post("/api/v1/families/{familyId}/children", parent.familyId())
                .then().statusCode(200).body("code", equalTo(200006));
    }

    @Test
    void duplicateUsernameShouldReturn200007() {
        TestAccount parentA = registerAndLogin("13900000109");
        TestAccount parentB = registerAndLogin("13900000110");
        createChild(parentA, "xm109a");

        // 登录名全局唯一：跨家庭同名亦冲突（§6.1）
        withToken(parentB).contentType(ContentType.JSON)
                .body(addChildBody("xm109a"))
                .when().post("/api/v1/families/{familyId}/children", parentB.familyId())
                .then().statusCode(200).body("code", equalTo(200007));
    }

    @Test
    void invalidAddChildPayloadShouldReturn100001() {
        TestAccount parent = registerAndLogin("13900000111");

        // 大写登录名被参数校验拒绝
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("username", "Xm111a", "password", CHILD_INITIAL_PASSWORD,
                        "nickname", "孩子"))
                .when().post("/api/v1/families/{familyId}/children", parent.familyId())
                .then().statusCode(200).body("code", equalTo(100001));
        // 弱密码被参数校验拒绝
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("username", "xm111b", "password", "weak", "nickname", "孩子"))
                .when().post("/api/v1/families/{familyId}/children", parent.familyId())
                .then().statusCode(200).body("code", equalTo(100001));
    }
}
