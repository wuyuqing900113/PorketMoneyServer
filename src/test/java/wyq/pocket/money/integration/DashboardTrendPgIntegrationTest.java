package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 看板 / 趋势 / 榜单集成测试（M2 设计 §12.2 DashboardTrendIT）：
 * 看板总额与成员余额、周趋势 12 点 / 日趋势 30 点、USER 作用域、
 * 本周收入榜稠密排名。
 */
class DashboardTrendPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private Response deposit(TestAccount parent, long userId, String amount) {
        return withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", userId, "amount", amount, "remark", "看板存入"))
                .when().post("/api/v1/families/{familyId}/deposits", parent.familyId());
    }

    private Response withdraw(TestAccount parent, long userId, String amount) {
        return withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", userId, "amount", amount, "remark", "看板取出"))
                .when().post("/api/v1/families/{familyId}/withdrawals", parent.familyId());
    }

    private void assertAmount(Response response, String path, String expected) {
        BigDecimal actual = new BigDecimal(response.jsonPath().getString(path));
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                path + " 期望 " + expected + " 实际 " + actual);
    }

    @Test
    void dashboardTrendAndLeaderboardShouldAggregate() {
        TestAccount parent = registerAndLogin(
                String.format("1383%07d", COUNTER.incrementAndGet()));
        long familyId = parent.familyId();
        long child1 = createChild(parent,
                String.format("pgdt%08d", COUNTER.incrementAndGet()));
        long child2 = createChild(parent,
                String.format("pgdt%08d", COUNTER.incrementAndGet()));

        deposit(parent, child1, "100.00").then().statusCode(200).body("code", equalTo(0));
        deposit(parent, child2, "50.00").then().statusCode(200).body("code", equalTo(0));
        withdraw(parent, child1, "20.00").then().statusCode(200).body("code", equalTo(0));

        // 看板：总余额 / 本周 / 本月 / 成员余额行
        Response dashboard = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", familyId);
        dashboard.then().statusCode(200).body("code", equalTo(0));
        assertAmount(dashboard, "data.totalBalance", "130.00");
        assertAmount(dashboard, "data.weekIncome", "150.00");
        assertAmount(dashboard, "data.weekExpense", "20.00");
        assertAmount(dashboard, "data.monthIncome", "150.00");
        assertAmount(dashboard, "data.monthExpense", "20.00");
        assertAmount(dashboard,
                "data.members.find { it.userId == " + child1 + " }.balance", "80.00");
        assertAmount(dashboard,
                "data.members.find { it.userId == " + child2 + " }.balance", "50.00");

        // 周趋势：家庭作用域 12 周，末点（本周）聚合本周收支
        Response weekly = withToken(parent).when()
                .get("/api/v1/families/{familyId}/trends", familyId);
        weekly.then().statusCode(200).body("code", equalTo(0))
                .body("data.granularity", equalTo("WEEK"))
                .body("data.scope", equalTo("FAMILY"))
                .body("data.series.size()", equalTo(12));
        int weekPoints = weekly.jsonPath().getInt("data.series.size()");
        assertAmount(weekly, "data.series[" + (weekPoints - 1) + "].income", "150.00");
        assertAmount(weekly, "data.series[" + (weekPoints - 1) + "].expense", "20.00");

        // 日趋势：30 天，末日（今日）聚合当日收支与期末余额
        Response daily = withToken(parent).when()
                .get("/api/v1/families/{familyId}/trends?granularity=DAY", familyId);
        daily.then().statusCode(200).body("code", equalTo(0))
                .body("data.granularity", equalTo("DAY"))
                .body("data.series.size()", equalTo(30));
        int dayPoints = daily.jsonPath().getInt("data.series.size()");
        assertAmount(daily, "data.series[" + (dayPoints - 1) + "].income", "150.00");
        assertAmount(daily, "data.series[" + (dayPoints - 1) + "].expense", "20.00");
        assertAmount(daily, "data.series[" + (dayPoints - 1) + "].endingBalance", "130.00");

        // USER 作用域：仅聚合孩子甲
        Response userScope = withToken(parent).when()
                .get("/api/v1/families/{familyId}/trends?scope=USER&userId={userId}",
                        familyId, child1);
        userScope.then().statusCode(200).body("code", equalTo(0))
                .body("data.scope", equalTo("USER"))
                .body("data.userId", equalTo((int) child1));
        int userPoints = userScope.jsonPath().getInt("data.series.size()");
        assertAmount(userScope, "data.series[" + (userPoints - 1) + "].income", "100.00");
        assertAmount(userScope, "data.series[" + (userPoints - 1) + "].expense", "20.00");

        // 本周收入榜：稠密排名，本周一为窗口起点
        Response leaderboard = withToken(parent).when()
                .get("/api/v1/families/{familyId}/leaderboards/weekly-income", familyId);
        leaderboard.then().statusCode(200).body("code", equalTo(0))
                .body("data.weekStartDate", equalTo(LocalDate.now(BUSINESS_ZONE)
                        .with(DayOfWeek.MONDAY).toString()));
        assertEquals(1, leaderboard.jsonPath().getInt(
                "data.entries.find { it.userId == " + child1 + " }.rank"),
                "孩子甲收入 100 应排第 1");
        assertEquals(2, leaderboard.jsonPath().getInt(
                "data.entries.find { it.userId == " + child2 + " }.rank"),
                "孩子乙收入 50 应排第 2");
        assertAmount(leaderboard,
                "data.entries.find { it.userId == " + child1 + " }.totalIncome", "100.00");
    }
}
