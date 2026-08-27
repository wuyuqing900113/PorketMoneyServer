package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import wyq.pocket.money.rule.service.RuleSettlementService;

/**
 * 包月规则 CRUD 全分支集成测试（M2 设计 §12.2 RuleCrudIT）：
 * 创建 / 列表 / 详情 / 修改 / 暂停 / 恢复 / 归档 / 删除与错误分支
 * 400001 不存在、400002 状态不允许、400004 上限、400005 有发放记录、
 * 400006 重名。
 *
 * <p>400003（发放日 1–28）由 Bean Validation 在入口以 100001 拦截，
 * 服务层分支见 RuleServiceTest。
 *
 * <p>覆盖基座属性：停用定时 Job，发放记录场景直调结算服务构造。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pocket-money.money.settlement.enabled=false"
})
class RuleCrudPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private RuleSettlementService settlementService;

    private TestAccount parent;

    private long childId;

    private long familyId;

    private String currentMonth;

    @BeforeEach
    void buildFixture() {
        parent = registerAndLogin(String.format("1384%07d", COUNTER.incrementAndGet()));
        childId = createChild(parent,
                String.format("pgrc%08d", COUNTER.incrementAndGet()));
        familyId = parent.familyId();
        currentMonth = YearMonth.now(BUSINESS_ZONE).toString();
    }

    private Map<String, Object> ruleBody(String ruleName) {
        return Map.of("beneficiaryUserId", childId, "ruleName", ruleName,
                "amount", "10.00", "grantDay", 1, "startMonth", currentMonth);
    }

    private long createRule(String ruleName) {
        Response response = withToken(parent).contentType(ContentType.JSON)
                .body(ruleBody(ruleName))
                .when().post("/api/v1/families/{familyId}/rules", familyId);
        response.then().statusCode(200).body("code", equalTo(0));
        return response.jsonPath().getLong("data.id");
    }

    private void ruleAction(String method, long ruleId, int expectedCode, Object body) {
        var request = withToken(parent).contentType(ContentType.JSON);
        Response response = body == null
                ? request.when().request(method,
                        "/api/v1/families/{familyId}/rules/{ruleId}", familyId, ruleId)
                : request.body(body).when().request(method,
                        "/api/v1/families/{familyId}/rules/{ruleId}", familyId, ruleId);
        response.then().statusCode(200).body("code", equalTo(expectedCode));
    }

    @Test
    void createListDetailUpdateShouldRoundTrip() {
        long ruleId = createRule("往返规则");

        // 列表包含该规则
        withToken(parent).when().get("/api/v1/families/{familyId}/rules", familyId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.find { it.id == " + ruleId + " }.ruleName", equalTo("往返规则"));

        // 详情：字段回显 + 无发放记录
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/rules/{ruleId}", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.rule.ruleName", equalTo("往返规则"))
                .body("data.rule.status", equalTo("ACTIVE"))
                .body("data.rule.grantedThisMonth", equalTo(false))
                .body("data.recentGrants.size()", equalTo(0));

        // 修改：名称 / 金额 / 发放日
        ruleAction("PUT", ruleId, 0, Map.of("ruleName", "往返规则改",
                "amount", "20.00", "grantDay", 15));
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/rules/{ruleId}", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.rule.ruleName", equalTo("往返规则改"))
                .body("data.rule.grantDay", equalTo(15));
    }

    @Test
    void lifecycleShouldEnforceStatusRules() {
        long ruleId = createRule("生命周期规则");

        // ACTIVE → PAUSED → ACTIVE
        pauseOrResume(ruleId, "pause", 0);
        pauseOrResume(ruleId, "pause", 400002);
        pauseOrResume(ruleId, "resume", 0);
        pauseOrResume(ruleId, "resume", 400002);

        // ACTIVE → ARCHIVED（终态）
        pauseOrResume(ruleId, "archive", 0);
        pauseOrResume(ruleId, "archive", 400002);
        pauseOrResume(ruleId, "pause", 400002);
        pauseOrResume(ruleId, "resume", 400002);
    }

    private void pauseOrResume(long ruleId, String action, int expectedCode) {
        withToken(parent).when()
                .post("/api/v1/families/{familyId}/rules/{ruleId}/" + action,
                        familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(expectedCode));
    }

    @Test
    void duplicateNameShouldFailWith400006() {
        createRule("重名规则");
        // 创建重名
        withToken(parent).contentType(ContentType.JSON)
                .body(ruleBody("重名规则"))
                .when().post("/api/v1/families/{familyId}/rules", familyId)
                .then().statusCode(200).body("code", equalTo(400006));
        // 修改成已有名称
        long other = createRule("重名规则乙");
        ruleAction("PUT", other, 400006, Map.of("ruleName", "重名规则",
                "amount", "10.00", "grantDay", 1));
    }

    @Test
    void notFoundShouldFailWith400001() {
        long missing = 9_999_999L;
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/rules/{ruleId}", familyId, missing)
                .then().statusCode(200).body("code", equalTo(400001));
        pauseOrResume(missing, "pause", 400001);
        ruleAction("DELETE", missing, 400001, null);
    }

    @Test
    void deleteShouldBeBlockedAfterGrantWith400005() {
        long ruleId = createRule("发放后删除规则");

        // 直调结算制造发放记录（grantDay=1 ≤ 今天，当月起生效）
        settlementService.settleDueRules();

        ruleAction("DELETE", ruleId, 400005, null);
        // 归档后仍有发放记录 → 仍不可删
        pauseOrResume(ruleId, "archive", 0);
        ruleAction("DELETE", ruleId, 400005, null);
    }

    @Test
    void deleteWithoutGrantsShouldSucceed() {
        long ruleId = createRule("无发放删除规则");
        ruleAction("DELETE", ruleId, 0, null);
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/rules/{ruleId}", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(400001));
    }

    @Test
    void ruleLimitShouldFailWith400004() {
        for (int i = 0; i < 10; i++) {
            createRule("上限规则" + i);
        }
        withToken(parent).contentType(ContentType.JSON)
                .body(ruleBody("上限规则溢出"))
                .when().post("/api/v1/families/{familyId}/rules", familyId)
                .then().statusCode(200).body("code", equalTo(400004));
    }

    @Test
    void invalidParamsShouldFailWith100001() {
        // 发放日 29：入口 Bean Validation 拦截（服务层 400003 见 RuleServiceTest）
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("beneficiaryUserId", childId, "ruleName", "非法发放日",
                        "amount", "10.00", "grantDay", 29, "startMonth", currentMonth))
                .when().post("/api/v1/families/{familyId}/rules", familyId)
                .then().statusCode(200).body("code", equalTo(100001));
        // 月份顺序：startMonth 晚于 endMonth
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("beneficiaryUserId", childId, "ruleName", "月份倒挂",
                        "amount", "10.00", "grantDay", 1, "startMonth", currentMonth,
                        "endMonth", "2026-01"))
                .when().post("/api/v1/families/{familyId}/rules", familyId)
                .then().statusCode(200).body("code", equalTo(100001));
    }
}
