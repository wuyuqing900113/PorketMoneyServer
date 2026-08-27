package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 财务报表集成测试（M2 设计 §12.2 ReportIT）：分项聚合、净额、成员行、
 * 统计摘要；500001 月份格式非法、500002 月份在未来。
 */
class ReportPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private void assertAmount(Response response, String path, String expected) {
        BigDecimal actual = new BigDecimal(response.jsonPath().getString(path));
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                path + " 期望 " + expected + " 实际 " + actual);
    }

    @Test
    void incomeExpenseAndStatisticsShouldAggregate() {
        TestAccount parent = registerAndLogin(
                String.format("1399%07d", COUNTER.incrementAndGet()));
        long childId = createChild(parent,
                String.format("pgrp%08d", COUNTER.incrementAndGet()));
        long familyId = parent.familyId();
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "80.00", "remark", "报表存入"))
                .when().post("/api/v1/families/{familyId}/deposits", familyId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "20.00", "remark", "报表取出"))
                .when().post("/api/v1/families/{familyId}/withdrawals", familyId)
                .then().statusCode(200).body("code", equalTo(0));

        String currentMonth = YearMonth.now(BUSINESS_ZONE).toString();

        // 月度收支报表：分项聚合 + 净额 + 成员行（全员上榜，按 userId 定位）
        Response report = withToken(parent).when()
                .get("/api/v1/families/{familyId}/reports/income-expense?month={month}",
                        familyId, currentMonth);
        report.then().statusCode(200).body("code", equalTo(0))
                .body("data.month", equalTo(currentMonth));
        assertAmount(report, "data.totalIncome", "80.00");
        assertAmount(report, "data.totalExpense", "20.00");
        assertAmount(report, "data.net", "60.00");
        assertAmount(report, "data.incomeByType.MANUAL_ADD", "80.00");
        assertAmount(report, "data.expenseByType.WITHDRAW", "20.00");
        String childRow = "data.members.find { it.userId == " + childId + " }";
        assertAmount(report, childRow + ".income", "80.00");
        assertAmount(report, childRow + ".net", "60.00");
        String parentRow = "data.members.find { it.userId == " + parent.userId() + " }";
        assertAmount(report, parentRow + ".income", "0");
        assertAmount(report, parentRow + ".net", "0");

        // 统计摘要：余额 / 累计 / 当月 / 成员数
        Response statistics = withToken(parent).when()
                .get("/api/v1/families/{familyId}/statistics/summary", familyId);
        statistics.then().statusCode(200).body("code", equalTo(0))
                .body("data.memberCount", equalTo(2));
        assertAmount(statistics, "data.totalBalance", "60.00");
        assertAmount(statistics, "data.allTimeIncome", "80.00");
        assertAmount(statistics, "data.allTimeExpense", "20.00");
        assertAmount(statistics, "data.currentMonthIncome", "80.00");
        assertAmount(statistics, "data.currentMonthExpense", "20.00");

        // 500001 格式非法（HTTP 200 + 业务码，不 500）
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/reports/income-expense?month=2026-13",
                        familyId)
                .then().statusCode(200).body("code", equalTo(500001));
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/reports/income-expense?month=abc",
                        familyId)
                .then().statusCode(200).body("code", equalTo(500001));

        // 500002 月份在未来
        String nextMonth = YearMonth.now(BUSINESS_ZONE).plusMonths(1).toString();
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/reports/income-expense?month={month}",
                        familyId, nextMonth)
                .then().statusCode(200).body("code", equalTo(500002));
    }
}
