package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 零花钱主流程集成测试（M2 设计 §12.2 MoneyFlowIT + WithdrawBoundaryIT）：
 * 惰性开户、手动存取、余额不足 300001、看板与流水分页。
 *
 * <p>金额断言统一走 BigDecimal.compareTo，规避 JSON 反序列化数值类型差异。
 */
class MoneyFlowPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private static String nextPhone() {
        return String.format("1393%07d", COUNTER.incrementAndGet());
    }

    private static String nextUsername() {
        return String.format("pgmf%08d", COUNTER.incrementAndGet());
    }

    private Response deposit(TestAccount parent, long userId, String amount) {
        return withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", userId, "amount", amount, "remark", "集成存入"))
                .when().post("/api/v1/families/{familyId}/deposits", parent.familyId());
    }

    private Response withdraw(TestAccount parent, long userId, String amount) {
        return withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", userId, "amount", amount, "remark", "集成取出"))
                .when().post("/api/v1/families/{familyId}/withdrawals", parent.familyId());
    }

    private void assertAmount(Response response, String path, String expected) {
        BigDecimal actual = new BigDecimal(response.jsonPath().getString(path));
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                path + " 期望 " + expected + " 实际 " + actual);
    }

    @Test
    void depositWithdrawAndQueryShouldWorkEndToEnd() {
        TestAccount parent = registerAndLogin(nextPhone());
        long childId = createChild(parent, nextUsername());

        // 未开户看板：总余额 0
        Response dashboardBefore = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", parent.familyId());
        dashboardBefore.then().statusCode(200).body("code", equalTo(0));
        assertAmount(dashboardBefore, "data.totalBalance", "0");

        // 存入触发惰性开户
        Response depositResponse = deposit(parent, childId, "100.50");
        depositResponse.then().statusCode(200).body("code", equalTo(0))
                .body("data.userId", equalTo((int) childId));
        assertAmount(depositResponse, "data.balanceAfter", "100.50");

        // 看板反映余额与本月收入
        Response dashboard = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", parent.familyId());
        dashboard.then().statusCode(200).body("code", equalTo(0));
        assertAmount(dashboard, "data.totalBalance", "100.50");
        assertAmount(dashboard, "data.monthIncome", "100.50");

        // 部分取出
        Response partial = withdraw(parent, childId, "0.50");
        partial.then().statusCode(200).body("code", equalTo(0));
        assertAmount(partial, "data.balanceAfter", "100.00");

        // 余额不足边界：balance + 0.01 拒绝（HTTP 200 + 300001）
        withdraw(parent, childId, "100.01")
                .then().statusCode(200).body("code", equalTo(300001));

        // 恰好取空允许
        Response drain = withdraw(parent, childId, "100.00");
        drain.then().statusCode(200).body("code", equalTo(0));
        assertAmount(drain, "data.balanceAfter", "0");

        // 流水分页：2 取 + 1 存
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/transactions?userId={userId}",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.total", equalTo(3))
                .body("data.records", hasSize(3));

        // 非法枚举值 → 100001（不 500）
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/transactions?direction=BOTH",
                        parent.familyId())
                .then().statusCode(200).body("code", equalTo(100001));
    }
}
