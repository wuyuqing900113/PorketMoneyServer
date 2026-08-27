package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 成员移除联动集成测试（M2 设计 §12.2 MemberRemoveCascadeIT，§7.4）：
 * 移除孩子后账户冻结（300002）、ACTIVE 规则自动暂停、
 * 未发放学习任务自动取消。
 */
class MemberRemoveCascadePgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    @Test
    void removeMemberShouldFreezeAccountPauseRulesAndCancelTasks() {
        TestAccount parent = registerAndLogin(
                String.format("1398%07d", COUNTER.incrementAndGet()));
        String childUsername = String.format("pgmc%08d", COUNTER.incrementAndGet());
        long childId = createChild(parent, childUsername);

        long familyId = parent.familyId();

        // 夹具：账户有余额 + ACTIVE 规则 + PENDING 学习任务
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "50.00", "remark", "移除前存入"))
                .when().post("/api/v1/families/{familyId}/deposits", familyId)
                .then().statusCode(200).body("code", equalTo(0));
        Response createRule = withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("beneficiaryUserId", childId, "ruleName", "移除联动规则",
                        "amount", "10.00", "grantDay", 1, "startMonth", "2026-08"))
                .when().post("/api/v1/families/{familyId}/rules", familyId);
        createRule.then().statusCode(200).body("code", equalTo(0));
        long ruleId = createRule.jsonPath().getLong("data.id");
        Response createTask = withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("assigneeUserId", childId, "title", "移除联动任务",
                        "rewardAmount", "5.00"))
                .when().post("/api/v1/families/{familyId}/learning-tasks", familyId);
        createTask.then().statusCode(200).body("code", equalTo(0));
        long taskId = createTask.jsonPath().getLong("data.id");

        // 移除成员
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        familyId, childId)
                .then().statusCode(200).body("code", equalTo(0));

        // 再入账被拒：数据级守卫先拒非成员目标（403 + 100004）；
        // 账户冻结本身由单元测试覆盖（MemberRemovedMoneyListenerTest /
        // AccountTransactionServiceTest 300002）
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "1.00"))
                .when().post("/api/v1/families/{familyId}/deposits", familyId)
                .then().statusCode(403).body("code", equalTo(100004));

        // 规则自动暂停
        withToken(parent).when().get("/api/v1/families/{familyId}/rules/{ruleId}",
                        familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.rule.status", equalTo("PAUSED"));

        // 未发放任务自动取消
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/learning-tasks?page=1&size=100", familyId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.records.find { it.id == " + taskId + " }.status",
                        equalTo("CANCELED"));
    }
}
