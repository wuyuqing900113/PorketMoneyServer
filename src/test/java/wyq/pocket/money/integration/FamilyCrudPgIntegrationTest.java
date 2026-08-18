package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 家庭域 CRUD 集成测试（M1 设计 §12.2 FamilyCrudIT / §6）。
 *
 * <p>改名 / 添加孩子 / 上限 200006 / 移除约束（非成员 200011、
 * 移除自己 200012）/ 被移除孩子停用后无法登录。
 */
class FamilyCrudPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PHONE = "13910000041";

    private static final int FAMILY_MEMBER_LIMIT = 8;

    @Test
    void familyCrudShouldWorkEndToEnd() {
        TestAccount parent = registerAndLogin(PHONE);

        // 修改家庭名
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("familyName", "测试家庭改名"))
                .when().put("/api/v1/families/{familyId}", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).when().get("/api/v1/families/{familyId}", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.familyName", equalTo("测试家庭改名"));

        // 添加孩子 → 花名册可见
        long childId = createChild(parent, "pgcrud01a");
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/members", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.size()", equalTo(2));

        // 修改孩子昵称 → 详情可见
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("nickname", "小明改名"))
                .when().put("/api/v1/families/{familyId}/children/{userId}",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).when().get("/api/v1/families/{familyId}", parent.familyId())
                .then().statusCode(200)
                .body("data.members[1].nickname", equalTo("小明改名"));
    }

    @Test
    void memberLimitShouldReturn200006() {
        TestAccount parent = registerAndLogin("13910000042");
        for (int index = 0; index < FAMILY_MEMBER_LIMIT - 1; index++) {
            createChild(parent, "pgcrud02" + (char) ('a' + index));
        }
        // 家长 + 7 孩子 = 8 名成员，达到上限
        withToken(parent).contentType(ContentType.JSON)
                .body(addChildBody("pgcrud02h"))
                .when().post("/api/v1/families/{familyId}/children", parent.familyId())
                .then().statusCode(200).body("code", equalTo(200006));
    }

    @Test
    void removeConstraintsShouldBeEnforced() {
        TestAccount parent = registerAndLogin("13910000043");
        long childId = createChild(parent, "pgcrud03a");

        // 移除自己（家庭主）→ 200012
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        parent.familyId(), parent.userId())
                .then().statusCode(200).body("code", equalTo(200012));

        // 移除不存在的成员 → 200011
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        parent.familyId(), childId + 900_000L)
                .then().statusCode(200).body("code", equalTo(200011));

        // 正常移除 → 花名册收缩，被移除孩子停用、无法登录
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/members", parent.familyId())
                .then().statusCode(200).body("data.size()", equalTo(1));
        loginAs("pgcrud03a", CHILD_INITIAL_PASSWORD)
                .then().statusCode(200).body("code", equalTo(200004));
    }

    @Test
    void resetChildPasswordShouldForceChangeAgain() {
        TestAccount parent = registerAndLogin("13910000044");
        long childId = createChild(parent, "pgcrud04a");
        loginAndChangePassword("pgcrud04a", CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);

        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("newPassword", "ChildNew789"))
                .when().post("/api/v1/families/{familyId}/children/{userId}/password-reset",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));

        // 旧密码失效；新密码登录后 mcp 重新置位
        loginAs("pgcrud04a", CHILD_NEW_PASSWORD)
                .then().statusCode(200).body("code", equalTo(200002));
        Response relogin = loginAs("pgcrud04a", "ChildNew789");
        relogin.then().statusCode(200).body("code", equalTo(0))
                .body("data.mustChangePassword", equalTo(true));
    }
}
