package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 学习任务状态机集成测试（M2 设计 §12.2 LearningTaskFlowIT）：
 * 创建 → 提交 → 驳回 → 重提 → 通过发放；发放前可取消、发放后拒绝取消；
 * 通过同事务入账。
 */
class LearningTaskFlowPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private long createTask(TestAccount parent, long childId, String title) {
        Response response = withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("assigneeUserId", childId, "title", title,
                        "rewardAmount", "5.00", "deadline", "2026-08-31"))
                .when().post("/api/v1/families/{familyId}/learning-tasks",
                        parent.familyId());
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getLong("data.id");
    }

    private Response taskAction(String token, long familyId, long taskId,
                                String action, Object body) {
        return withToken(token).contentType(ContentType.JSON)
                .body(body == null ? Map.of() : body)
                .when().post("/api/v1/families/{familyId}/learning-tasks/{taskId}/"
                        + action, familyId, taskId);
    }

    private String taskStatus(TestAccount parent, long taskId) {
        Response response = withToken(parent).when()
                .get("/api/v1/families/{familyId}/learning-tasks?page=1&size=100",
                        parent.familyId());
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getString(
                "data.records.find { it.id == " + taskId + " }.status");
    }

    @Test
    void fullFlowShouldGrantRewardOnApprove() {
        TestAccount parent = registerAndLogin(
                String.format("1396%07d", COUNTER.incrementAndGet()));
        String childUsername = String.format("pglt%08d", COUNTER.incrementAndGet());
        long childId = createChild(parent, childUsername);
        String childToken = loginAndChangePassword(childUsername,
                CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);
        long familyId = parent.familyId();

        // 提交 → 驳回 → 重提 → 通过
        long taskId = createTask(parent, childId, "背单词");
        taskAction(childToken, familyId, taskId, "submit", Map.of("submitNote", "背完了"))
                .then().statusCode(200).body("code", equalTo(0));
        assertEquals("SUBMITTED", taskStatus(parent, taskId));
        taskAction(parent.accessToken(), familyId, taskId, "reject",
                Map.of("rejectReason", "不合格"))
                .then().statusCode(200).body("code", equalTo(0));
        assertEquals("REJECTED", taskStatus(parent, taskId));
        taskAction(childToken, familyId, taskId, "submit", Map.of("submitNote", "重背完了"))
                .then().statusCode(200).body("code", equalTo(0));
        Response approve = taskAction(parent.accessToken(), familyId, taskId, "approve", null);
        approve.then().statusCode(200).body("code", equalTo(0));
        assertEquals("APPROVED", taskStatus(parent, taskId));

        // 通过即入账
        Response dashboard = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", familyId);
        dashboard.then().statusCode(200).body("code", equalTo(0));
        assertEquals(0, new BigDecimal(dashboard.jsonPath().getString("data.totalBalance"))
                .compareTo(new BigDecimal("5.00")), "通过后奖励应入账");

        // 发放后取消被拒绝（300006）
        taskAction(parent.accessToken(), familyId, taskId, "cancel", null)
                .then().statusCode(200).body("code", equalTo(300006));

        // 发放前取消允许
        long cancelable = createTask(parent, childId, "练字");
        taskAction(parent.accessToken(), familyId, cancelable, "cancel", null)
                .then().statusCode(200).body("code", equalTo(0));
        assertEquals("CANCELED", taskStatus(parent, cancelable));

        // 孩子不得审批（接口级限家长）
        long guarded = createTask(parent, childId, "算术");
        taskAction(childToken, familyId, guarded, "submit", Map.of("submitNote", "完成"))
                .then().statusCode(200).body("code", equalTo(0));
        taskAction(childToken, familyId, guarded, "approve", null)
                .then().statusCode(403).body("code", equalTo(100004));
    }
}
