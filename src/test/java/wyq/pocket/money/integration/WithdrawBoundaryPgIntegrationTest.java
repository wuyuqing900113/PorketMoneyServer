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
 * 取出边界集成测试（M2 设计 §12.2 WithdrawBoundaryIT）：
 * 恰好取空允许、多 0.01 拒绝 300001、空余额再取拒绝 300001、
 * 入口金额校验 100001（0 / 负数）。
 *
 * <p>冻结账户 300002 在 API 路径不可达：成员移除后账户冻结，但数据级守卫
 * 先以 100004 拒绝非成员目标；300002 由单元测试
 * AccountTransactionServiceTest#applyShouldThrow300002WhenAccountFrozen 覆盖。
 */
class WithdrawBoundaryPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private Response deposit(TestAccount parent, long userId, String amount) {
        return withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", userId, "amount", amount, "remark", "边界存入"))
                .when().post("/api/v1/families/{familyId}/deposits", parent.familyId());
    }

    private Response withdraw(TestAccount parent, long userId, String amount) {
        return withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", userId, "amount", amount, "remark", "边界取出"))
                .when().post("/api/v1/families/{familyId}/withdrawals", parent.familyId());
    }

    private BigDecimal totalBalance(TestAccount parent) {
        Response dashboard = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", parent.familyId());
        dashboard.then().statusCode(200).body("code", equalTo(0));
        return new BigDecimal(dashboard.jsonPath().getString("data.totalBalance"));
    }

    @Test
    void withdrawBoundariesShouldBehaveAsDesigned() {
        TestAccount parent = registerAndLogin(
                String.format("1392%07d", COUNTER.incrementAndGet()));
        long childId = createChild(parent,
                String.format("pgwb%08d", COUNTER.incrementAndGet()));

        deposit(parent, childId, "100.00").then().statusCode(200).body("code", equalTo(0));

        // 多取 0.01 → 300001，余额不变
        withdraw(parent, childId, "100.01")
                .then().statusCode(200).body("code", equalTo(300001));
        assertEquals(0, totalBalance(parent).compareTo(new BigDecimal("100.00")),
                "拒绝后余额应保持 100.00");

        // 恰好取空 → 允许
        Response drain = withdraw(parent, childId, "100.00");
        drain.then().statusCode(200).body("code", equalTo(0));
        assertEquals(0, new BigDecimal(drain.jsonPath().getString("data.balanceAfter"))
                .compareTo(BigDecimal.ZERO), "恰好取空后余额应为 0");

        // 空余额再取 0.01 → 300001
        withdraw(parent, childId, "0.01")
                .then().statusCode(200).body("code", equalTo(300001));
    }

    @Test
    void zeroOrNegativeAmountShouldFailWith100001() {
        TestAccount parent = registerAndLogin(
                String.format("1392%07d", COUNTER.incrementAndGet()));
        long childId = createChild(parent,
                String.format("pgwb%08d", COUNTER.incrementAndGet()));
        deposit(parent, childId, "10.00").then().statusCode(200).body("code", equalTo(0));

        // 入口 Bean Validation：0 / 负数一律 100001，不落账
        withdraw(parent, childId, "0.00")
                .then().statusCode(200).body("code", equalTo(100001));
        withdraw(parent, childId, "-1.00")
                .then().statusCode(200).body("code", equalTo(100001));
        assertEquals(0, totalBalance(parent).compareTo(new BigDecimal("10.00")),
                "非法金额不得落账");
    }
}
