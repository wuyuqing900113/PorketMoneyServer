package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import wyq.pocket.money.money.service.ReconciliationService;
import wyq.pocket.money.rule.service.RuleSettlementService;

/**
 * M2 审计落库集成测试（M2 设计 §12.2 / §15 DoD：16 个 M2 审计动作）：
 * MONEY_DEPOSIT/WITHDRAW、RULE_CREATE/UPDATE/PAUSE/RESUME/ARCHIVE/DELETE、
 * RULE_GRANT_EXECUTED（直调结算）、LEARNING_TASK_CREATE/SUBMIT/APPROVE/
 * REJECT/CANCEL、WORK_VALUE_RECORD、RECONCILE_MISMATCH（注入不一致直调对账）。
 *
 * <p>覆盖基座属性：停用定时 Job，结算与对账由测试直调触发。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pocket-money.money.settlement.enabled=false"
})
class M2AuditTrailPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RuleSettlementService settlementService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    void allSixteenM2ActionsShouldLandInAuditLog() throws SQLException {
        // 共享容器库：系统级（user_id 为空）动作按测试开始时间界定窗口
        Instant testStart = fetchDbNow();
        TestAccount parent = registerAndLogin(
                String.format("1385%07d", COUNTER.incrementAndGet()));
        long familyId = parent.familyId();
        String childUsername = String.format("pgat%08d", COUNTER.incrementAndGet());
        long childId = createChild(parent, childUsername);
        String childToken = loginAndChangePassword(childUsername,
                CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);
        String currentMonth = YearMonth.now(BUSINESS_ZONE).toString();

        depositAndWithdraw(parent, familyId, childId);
        runRuleLifecycle(parent, familyId, childId, currentMonth);
        createThenDeleteRule(parent, familyId, childId, currentMonth);
        runLearningTaskFlows(parent, familyId, childId, childToken);
        recordWorkValue(parent, familyId, currentMonth);
        tamperBalanceAndReconcile(familyId);

        // 16 个 M2 动作断言
        assertAuditCount(parent.userId(), "MONEY_DEPOSIT", 1);
        assertAuditCount(parent.userId(), "MONEY_WITHDRAW", 1);
        assertAuditCount(parent.userId(), "RULE_CREATE", 2);
        assertAuditCount(parent.userId(), "RULE_UPDATE", 1);
        assertAuditCount(parent.userId(), "RULE_PAUSE", 1);
        assertAuditCount(parent.userId(), "RULE_RESUME", 1);
        assertAuditCount(parent.userId(), "RULE_ARCHIVE", 1);
        assertAuditCount(parent.userId(), "RULE_DELETE", 1);
        assertAuditCount(parent.userId(), "LEARNING_TASK_CREATE", 3);
        assertAuditCount(childId, "LEARNING_TASK_SUBMIT", 2);
        assertAuditCount(parent.userId(), "LEARNING_TASK_APPROVE", 1);
        assertAuditCount(parent.userId(), "LEARNING_TASK_REJECT", 1);
        assertAuditCount(parent.userId(), "LEARNING_TASK_CANCEL", 1);
        assertAuditCount(parent.userId(), "WORK_VALUE_RECORD", 1);
        // 系统动作无操作人：user_id 为空，窗口内各 1 条
        assertSystemAuditCount("RULE_GRANT_EXECUTED", 1, testStart);
        assertSystemAuditCount("RECONCILE_MISMATCH", 1, testStart);
    }

    private void depositAndWithdraw(TestAccount parent, long familyId, long childId) {
        // 手动存取
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "100.00", "remark", "审计存入"))
                .when().post("/api/v1/families/{familyId}/deposits", familyId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "10.00", "remark", "审计取出"))
                .when().post("/api/v1/families/{familyId}/withdrawals", familyId)
                .then().statusCode(200).body("code", equalTo(0));
    }

    private void runRuleLifecycle(TestAccount parent, long familyId, long childId,
                                  String currentMonth) {
        // 规则：创建 → 修改 → 暂停 → 恢复 → 结算发放 → 归档
        Response createRule = withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("beneficiaryUserId", childId, "ruleName", "审计规则",
                        "amount", "10.00", "grantDay", 1, "startMonth", currentMonth))
                .when().post("/api/v1/families/{familyId}/rules", familyId);
        createRule.then().statusCode(200).body("code", equalTo(0));
        long ruleId = createRule.jsonPath().getLong("data.id");
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("ruleName", "审计规则改", "amount", "10.00", "grantDay", 1))
                .when().put("/api/v1/families/{familyId}/rules/{ruleId}", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).when()
                .post("/api/v1/families/{familyId}/rules/{ruleId}/pause", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).when()
                .post("/api/v1/families/{familyId}/rules/{ruleId}/resume", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0));
        settlementService.settleDueRules();
        withToken(parent).when()
                .post("/api/v1/families/{familyId}/rules/{ruleId}/archive", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0));
    }

    private void createThenDeleteRule(TestAccount parent, long familyId, long childId,
                                      String currentMonth) {
        // 规则：另一条创建后删除（删除仅限无发放记录）
        Response rule2 = withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("beneficiaryUserId", childId, "ruleName", "审计待删规则",
                        "amount", "5.00", "grantDay", 2, "startMonth", currentMonth))
                .when().post("/api/v1/families/{familyId}/rules", familyId);
        rule2.then().statusCode(200).body("code", equalTo(0));
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/rules/{ruleId}",
                        familyId, rule2.jsonPath().getLong("data.id"))
                .then().statusCode(200).body("code", equalTo(0));
    }

    private void runLearningTaskFlows(TestAccount parent, long familyId, long childId,
                                      String childToken) {
        // 学习任务一：创建 → 提交 → 通过
        long task1 = createTask(parent, familyId, childId, "审计任务通过");
        taskAction(childToken, familyId, task1, "submit", Map.of("submitNote", "完成"));
        taskAction(parent.accessToken(), familyId, task1, "approve", null);

        // 学习任务二：创建 → 提交 → 驳回
        long task2 = createTask(parent, familyId, childId, "审计任务驳回");
        taskAction(childToken, familyId, task2, "submit", Map.of("submitNote", "完成"));
        taskAction(parent.accessToken(), familyId, task2, "reject",
                Map.of("rejectReason", "不合格"));

        // 学习任务三：创建 → 取消
        long task3 = createTask(parent, familyId, childId, "审计任务取消");
        taskAction(parent.accessToken(), familyId, task3, "cancel", null);
    }

    private void recordWorkValue(TestAccount parent, long familyId, String currentMonth) {
        // 工作价值
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("workMonth", currentMonth, "salaryIncome", "1000.00",
                        "allowanceAmount", "10.00"))
                .when().post("/api/v1/families/{familyId}/work-values", familyId)
                .then().statusCode(200).body("code", equalTo(0));
    }

    private void tamperBalanceAndReconcile(long familyId) throws SQLException {
        // 对账不一致：直接篡改余额快照后直调对账
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE money_account SET balance = balance + 0.01"
                                + " WHERE family_id = ?")) {
            statement.setLong(1, familyId);
            Assertions.assertTrue(statement.executeUpdate() > 0, "应至少存在一个账户");
        }
        reconciliationService.reconcile();
    }

    private Instant fetchDbNow() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT now()");
                ResultSet resultSet = statement.executeQuery()) {
            Assertions.assertTrue(resultSet.next());
            return resultSet.getTimestamp(1).toInstant();
        }
    }

    private long createTask(TestAccount parent, long familyId, long childId, String title) {
        Response response = withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("assigneeUserId", childId, "title", title,
                        "rewardAmount", "1.00"))
                .when().post("/api/v1/families/{familyId}/learning-tasks", familyId);
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getLong("data.id");
    }

    private void taskAction(String token, long familyId, long taskId,
                            String action, Object body) {
        withToken(token).contentType(ContentType.JSON)
                .body(body == null ? Map.of() : body)
                .when().post("/api/v1/families/{familyId}/learning-tasks/{taskId}/"
                        + action, familyId, taskId)
                .then().statusCode(200).body("code", equalTo(0));
    }

    private void assertAuditCount(long userId, String action, int expected)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = ?")) {
            statement.setLong(1, userId);
            statement.setString(2, action);
            assertCount(statement, action, expected);
        }
    }

    private void assertSystemAuditCount(String action, int expected, Instant since)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM audit_log"
                                + " WHERE user_id IS NULL AND action = ? AND created_at >= ?")) {
            statement.setString(1, action);
            statement.setTimestamp(2, Timestamp.from(since));
            assertCount(statement, action, expected);
        }
    }

    private void assertCount(PreparedStatement statement, String action, int expected)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            Assertions.assertTrue(resultSet.next());
            Assertions.assertEquals(expected, resultSet.getInt(1),
                    "audit_log 动作 " + action + " 行数不符");
        }
    }
}
