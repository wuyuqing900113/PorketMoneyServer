package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import wyq.pocket.money.support.IdempotencyKeys;

/**
 * M2 权限矩阵参数化集成测试（M2 设计 §12.2 / 附录 B：24 端点 × 4 身份）：
 * 匿名一律 401+100003；跨家庭 PARENT 一律 403+100004；本家庭 CHILD /
 * PARENT 差异按附录 B（读全透明、写限家长、存取限本人账户、任务提交仅
 * 被指派孩子——PARENT 提交亦 100004）。
 *
 * <p>共享容器库中每用例自行准备资源（规则 / 任务按用例新建），
 * 避免状态串扰；资源名以序号保证唯一。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M2PermissionMatrixPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private static final AtomicLong SEQ = new AtomicLong();

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 身份：匿名 / 本家庭孩子 / 本家庭家长 / 跨家庭家长。 */
    enum Identity { ANON, CHILD, PARENT, CROSS_PARENT }

    /** 用例所需前置资源。 */
    enum Prep { NONE, BALANCE, RULE, PAUSED_RULE, TASK, SUBMITTED_TASK }

    /** 端点定义：序号、方法、相对家庭路径（{rid}/{tid} 为资源占位）、请求体、CHILD/PARENT 期望码。 */
    record EndpointCase(int no, String method, String pathPattern,
                        Map<String, Object> body, Prep prep,
                        int childExpect, int parentExpect) {
    }

    /** 矩阵行：端点 × 身份 → 期望业务码与 HTTP 状态。 */
    record MatrixRow(EndpointCase endpoint, Identity identity,
                     int expectCode, int expectStatus) {
    }

    private TestAccount parentA;

    private TestAccount parentB;

    private long childAId;

    private String childAToken;

    private long familyA;

    private String currentMonth;

    /** 当前行前置准备的规则，行后尽力删除，避免受益人非归档规则数触顶 400004。 */
    private long preparedRuleId;

    @LocalServerPort
    private int serverPort;

    /**
     * PER_CLASS 共享夹具在 {@code @BeforeAll} 构建，早于基座 {@code @BeforeEach}
     * 对 RestAssured 端口/幂等键过滤器的设置，故此处先行装配（见基座类 Javadoc）。
     */
    @BeforeAll
    void buildFixtureOnce() {
        RestAssured.port = serverPort;
        RestAssured.replaceFiltersWith(IdempotencyKeys.uniqueKeyPerRequest());
        parentA = registerAndLogin(String.format("1381%07d", COUNTER.incrementAndGet()));
        String childAUsername = String.format("pgpm%08d", COUNTER.incrementAndGet());
        childAId = createChild(parentA, childAUsername);
        childAToken = loginAndChangePassword(childAUsername,
                CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);
        parentB = registerAndLogin(String.format("1382%07d", COUNTER.incrementAndGet()));
        familyA = parentA.familyId();
        currentMonth = YearMonth.now(BUSINESS_ZONE).toString();
    }

    private List<EndpointCase> cases() {
        Map<String, Object> deposit = Map.of("targetUserId", childAId, "amount", "1.00");
        Map<String, Object> withdraw = Map.of("targetUserId", childAId, "amount", "1.00");
        Map<String, Object> ruleCreate = Map.of("beneficiaryUserId", childAId,
                "ruleName", "矩阵规则" + SEQ.incrementAndGet(),
                "amount", "10.00", "grantDay", 1, "startMonth", currentMonth);
        Map<String, Object> ruleUpdate = Map.of("ruleName", "矩阵改名" + SEQ.incrementAndGet(),
                "amount", "10.00", "grantDay", 1);
        Map<String, Object> taskCreate = Map.of("assigneeUserId", childAId,
                "title", "矩阵任务" + SEQ.incrementAndGet(), "rewardAmount", "1.00");
        Map<String, Object> submit = Map.of("submitNote", "矩阵提交");
        Map<String, Object> reject = Map.of("rejectReason", "矩阵驳回");
        Map<String, Object> workValue = Map.of("workMonth", currentMonth,
                "salaryIncome", "0.00", "allowanceAmount", "1.00");

        return List.of(
                new EndpointCase(1, "GET", "dashboard", null, Prep.NONE, 0, 0),
                new EndpointCase(2, "GET", "transactions", null, Prep.NONE, 0, 0),
                new EndpointCase(3, "GET", "trends", null, Prep.NONE, 0, 0),
                new EndpointCase(4, "GET", "leaderboards/weekly-income", null,
                        Prep.NONE, 0, 0),
                new EndpointCase(5, "POST", "deposits", deposit, Prep.NONE, 0, 0),
                new EndpointCase(6, "POST", "withdrawals", withdraw, Prep.BALANCE, 0, 0),
                new EndpointCase(7, "POST", "rules", ruleCreate, Prep.NONE, 100004, 0),
                new EndpointCase(8, "GET", "rules", null, Prep.NONE, 0, 0),
                new EndpointCase(9, "GET", "rules/{rid}", null, Prep.RULE, 0, 0),
                new EndpointCase(10, "PUT", "rules/{rid}", ruleUpdate, Prep.RULE,
                        100004, 0),
                new EndpointCase(11, "POST", "rules/{rid}/pause", null, Prep.RULE,
                        100004, 0),
                new EndpointCase(12, "POST", "rules/{rid}/resume", null, Prep.PAUSED_RULE,
                        100004, 0),
                new EndpointCase(13, "POST", "rules/{rid}/archive", null, Prep.RULE,
                        100004, 0),
                new EndpointCase(14, "DELETE", "rules/{rid}", null, Prep.RULE, 100004, 0),
                new EndpointCase(15, "POST", "learning-tasks", taskCreate, Prep.NONE,
                        100004, 0),
                new EndpointCase(16, "GET", "learning-tasks", null, Prep.NONE, 0, 0),
                new EndpointCase(17, "POST", "learning-tasks/{tid}/submit", submit,
                        Prep.TASK, 0, 100004),
                new EndpointCase(18, "POST", "learning-tasks/{tid}/approve", Map.of(),
                        Prep.SUBMITTED_TASK, 100004, 0),
                new EndpointCase(19, "POST", "learning-tasks/{tid}/reject", reject,
                        Prep.SUBMITTED_TASK, 100004, 0),
                new EndpointCase(20, "POST", "learning-tasks/{tid}/cancel", null,
                        Prep.TASK, 100004, 0),
                new EndpointCase(21, "POST", "work-values", workValue, Prep.NONE,
                        100004, 0),
                new EndpointCase(22, "GET", "work-values", null, Prep.NONE, 0, 0),
                new EndpointCase(23, "GET", "reports/income-expense?month=" + currentMonth,
                        null, Prep.NONE, 0, 0),
                new EndpointCase(24, "GET", "statistics/summary", null, Prep.NONE, 0, 0));
    }

    /** 24 端点 × 4 身份 = 96 行。 */
    Stream<Arguments> matrixRows() {
        List<MatrixRow> rows = new ArrayList<>();
        for (EndpointCase endpoint : cases()) {
            rows.add(new MatrixRow(endpoint, Identity.ANON, 100003, 401));
            rows.add(new MatrixRow(endpoint, Identity.CROSS_PARENT, 100004, 403));
            rows.add(new MatrixRow(endpoint, Identity.CHILD, endpoint.childExpect(),
                    endpoint.childExpect() == 0 ? 200 : 403));
            rows.add(new MatrixRow(endpoint, Identity.PARENT, endpoint.parentExpect(),
                    endpoint.parentExpect() == 0 ? 200 : 403));
        }
        return rows.stream().map(Arguments::of);
    }

    @ParameterizedTest(name = "#{0} {1} {2}")
    @MethodSource("matrixRows")
    void matrixShouldMatchAppendixB(MatrixRow row) {
        String path = prepareAndResolvePath(row.endpoint());
        RequestSpecification request = newRequest(row.identity());
        Response response = row.endpoint().body() == null
                ? request.when().request(row.endpoint().method(), path)
                : request.contentType(ContentType.JSON).body(row.endpoint().body())
                        .when().request(row.endpoint().method(), path);
        response.then().statusCode(row.expectStatus())
                .body("code", equalTo(row.expectCode()));
        cleanupPreparedRule();
    }

    private RequestSpecification newRequest(Identity identity) {
        return switch (identity) {
            case ANON -> RestAssured.given();
            case CHILD -> withToken(childAToken);
            case PARENT -> withToken(parentA);
            case CROSS_PARENT -> withToken(parentB);
        };
    }

    /** 尽力删除前置规则（用例动作可能已删/归档，忽略响应）。 */
    private void cleanupPreparedRule() {
        if (preparedRuleId == 0) {
            return;
        }
        withToken(parentA).when().delete(
                "/api/v1/families/{familyId}/rules/{ruleId}", familyA, preparedRuleId);
        preparedRuleId = 0;
    }

    @Test
    void submitByOtherChildShouldBeRejected() {
        // 附录 B #17 细化：提交仅限被指派孩子本人
        String childBUsername = String.format("pgpm%08d", COUNTER.incrementAndGet());
        createChild(parentA, childBUsername);
        String childBToken = loginAndChangePassword(childBUsername,
                CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);
        long taskId = createTask("指派给孩子甲的任务");

        withToken(childBToken).contentType(ContentType.JSON)
                .body(Map.of("submitNote", "越权提交"))
                .when().post("/api/v1/families/{familyId}/learning-tasks/{taskId}/submit",
                        familyA, taskId)
                .then().statusCode(403).body("code", equalTo(100004));

        // 越权提交被拒后任务状态不受影响
        withToken(parentA).when()
                .get("/api/v1/families/{familyId}/learning-tasks?page=1&size=100", familyA)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.records.find { it.id == " + taskId + " }.status",
                        equalTo("PENDING"));
    }

    private String prepareAndResolvePath(EndpointCase endpoint) {
        return resolveFullPath(substituteResource(endpoint.pathPattern(), endpoint.prep()));
    }

    private String substituteResource(String pattern, Prep prep) {
        switch (prep) {
            case BALANCE -> depositToChildA("5.00");
            case RULE, PAUSED_RULE ->
                    pattern = pattern.replace("{rid}", String.valueOf(prepareRule(prep)));
            case TASK, SUBMITTED_TASK ->
                    pattern = pattern.replace("{tid}", String.valueOf(prepareTask(prep)));
            default -> {
            }
        }
        return pattern;
    }

    private long prepareRule(Prep prep) {
        long ruleId = createRule();
        preparedRuleId = ruleId;
        if (prep == Prep.PAUSED_RULE) {
            ruleLifecycle(ruleId, "pause");
        }
        return ruleId;
    }

    private long prepareTask(Prep prep) {
        long taskId = createTask(null);
        if (prep == Prep.SUBMITTED_TASK) {
            submitTask(taskId);
        }
        return taskId;
    }

    private String resolveFullPath(String pattern) {
        String query = "";
        String path = pattern;
        int queryStart = path.indexOf('?');
        if (queryStart >= 0) {
            query = path.substring(queryStart);
            path = path.substring(0, queryStart);
        }
        return "/api/v1/families/" + familyA + "/" + path + query;
    }

    private void depositToChildA(String amount) {
        withToken(parentA).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childAId, "amount", amount))
                .when().post("/api/v1/families/{familyId}/deposits", familyA)
                .then().statusCode(200).body("code", equalTo(0));
    }

    private long createRule() {
        Response response = withToken(parentA).contentType(ContentType.JSON)
                .body(Map.of("beneficiaryUserId", childAId,
                        "ruleName", "矩阵规则" + SEQ.incrementAndGet(),
                        "amount", "10.00", "grantDay", 1, "startMonth", currentMonth))
                .when().post("/api/v1/families/{familyId}/rules", familyA);
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getLong("data.id");
    }

    private void ruleLifecycle(long ruleId, String action) {
        withToken(parentA).when()
                .post("/api/v1/families/{familyId}/rules/{ruleId}/" + action,
                        familyA, ruleId)
                .then().statusCode(200).body("code", equalTo(0));
    }

    private long createTask(String title) {
        Response response = withToken(parentA).contentType(ContentType.JSON)
                .body(Map.of("assigneeUserId", childAId,
                        "title", title == null ? "矩阵任务" + SEQ.incrementAndGet() : title,
                        "rewardAmount", "1.00"))
                .when().post("/api/v1/families/{familyId}/learning-tasks", familyA);
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getLong("data.id");
    }

    private void submitTask(long taskId) {
        withToken(childAToken).contentType(ContentType.JSON)
                .body(Map.of("submitNote", "前置提交"))
                .when().post("/api/v1/families/{familyId}/learning-tasks/{taskId}/submit",
                        familyA, taskId)
                .then().statusCode(200).body("code", equalTo(0));
    }
}
