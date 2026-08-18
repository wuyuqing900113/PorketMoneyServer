package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 审计落库集成测试（M1 设计 §15 DoD：集成测试断言关键动作行存在；§9.1）。
 *
 * <p>直查 audit_log：注册 / 建家庭 / 登录成败 / 家庭与孩子各写操作 /
 * 令牌轮转 / 重用检测 / 登出均按 user_id + action 精确断言行数。
 */
class AuditTrailPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PHONE = "13910000061";

    @Autowired
    private DataSource dataSource;

    @Test
    void keyActionsShouldLandInAuditLog() throws SQLException {
        // 注册（REGISTER + FAMILY_CREATE）与登录（LOGIN_SUCCESS）
        TestAccount account = registerAndLogin(PHONE);

        // 错误口令（LOGIN_FAILURE）
        loginAs(PHONE, "WrongPass1")
                .then().statusCode(200).body("code", equalTo(200002));

        // 家庭域动作：添孩子 / 改家庭名 / 孩子改密 / 重置孩子密码 / 移除成员
        long childId = createChild(account, "pgaudit01a");
        withToken(account).contentType(ContentType.JSON)
                .body(Map.of("familyName", "审计家庭"))
                .when().put("/api/v1/families/{familyId}", account.familyId())
                .then().statusCode(200).body("code", equalTo(0));
        loginAndChangePassword("pgaudit01a", CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);
        withToken(account).contentType(ContentType.JSON)
                .body(Map.of("newPassword", "ChildNew789"))
                .when().post("/api/v1/families/{familyId}/children/{userId}/password-reset",
                        account.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(account).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        account.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0));

        // 令牌动作：轮转（TOKEN_REFRESH）→ 重放旧令牌（TOKEN_REUSE_DETECTED）→ 登出
        Response rotated = post("/api/v1/auth/refresh",
                Map.of("refreshToken", account.refreshToken()));
        rotated.then().statusCode(200).body("code", equalTo(0));
        String rotatedAccess = rotated.jsonPath().getString("data.accessToken");
        String rotatedRefresh = rotated.jsonPath().getString("data.refreshToken");
        post("/api/v1/auth/refresh", Map.of("refreshToken", account.refreshToken()))
                .then().statusCode(200).body("code", equalTo(100003));
        withToken(rotatedAccess).contentType(ContentType.JSON)
                .body(Map.of("refreshToken", rotatedRefresh))
                .when().post("/api/v1/auth/logout")
                .then().statusCode(200).body("code", equalTo(0));

        assertAuditCount(account.userId(), "REGISTER", 1);
        assertAuditCount(account.userId(), "FAMILY_CREATE", 1);
        assertAuditCount(account.userId(), "LOGIN_SUCCESS", 1);
        assertAuditCount(account.userId(), "LOGIN_FAILURE", 1);
        assertAuditCount(account.userId(), "CHILD_CREATE", 1);
        assertAuditCount(account.userId(), "FAMILY_UPDATE", 1);
        assertAuditCount(account.userId(), "CHILD_PASSWORD_RESET", 1);
        assertAuditCount(account.userId(), "MEMBER_REMOVE", 1);
        assertAuditCount(account.userId(), "TOKEN_REFRESH", 1);
        assertAuditCount(account.userId(), "TOKEN_REUSE_DETECTED", 1);
        assertAuditCount(account.userId(), "LOGOUT", 1);
        // 孩子自助改密落在孩子自身名下
        assertAuditCount(childId, "PASSWORD_CHANGE", 1);
    }

    private void assertAuditCount(long userId, String action, int expected)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = ?")) {
            statement.setLong(1, userId);
            statement.setString(2, action);
            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(expected, resultSet.getInt(1),
                        "audit_log 动作 " + action + " 行数不符");
            }
        }
    }
}
