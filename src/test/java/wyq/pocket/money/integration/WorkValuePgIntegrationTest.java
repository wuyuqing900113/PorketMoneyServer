package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 工作价值集成测试（M2 设计 §12.2 WorkValueIT）：
 * 家长记录本人工资并入账本人账户（同事务）、列表月份过滤、
 * salary=0 允许、写接口限家长（孩子 403）、读家庭内透明。
 */
class WorkValuePgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    @Test
    void workValueShouldRecordAndCreditSelfAccount() {
        TestAccount parent = registerAndLogin(
                String.format("1397%07d", COUNTER.incrementAndGet()));
        String childUsername = String.format("pgwv%08d", COUNTER.incrementAndGet());
        long childId = createChild(parent, childUsername);
        String childToken = loginAndChangePassword(childUsername,
                CHILD_INITIAL_PASSWORD, CHILD_NEW_PASSWORD);
        long familyId = parent.familyId();

        // 家长记录：入账本人账户
        Response create = withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("workMonth", "2026-08", "salaryIncome", "8000.00",
                        "allowanceAmount", "200.00", "workSummary", "8月工资"))
                .when().post("/api/v1/families/{familyId}/work-values", familyId);
        create.then().statusCode(200).body("code", equalTo(0))
                .body("data.transactionId", notNullValue());

        Response dashboard = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", familyId);
        dashboard.then().statusCode(200).body("code", equalTo(0));
        assertEquals(0, new BigDecimal(dashboard.jsonPath().getString("data.totalBalance"))
                .compareTo(new BigDecimal("200.00")), "工资发放应入账本人账户");

        // 同月多条允许 + salary=0 允许
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("workMonth", "2026-08", "salaryIncome", "0.00",
                        "allowanceAmount", "50.00"))
                .when().post("/api/v1/families/{familyId}/work-values", familyId)
                .then().statusCode(200).body("code", equalTo(0));

        // 列表月份过滤（家庭内透明：孩子可读）
        withToken(childToken).when()
                .get("/api/v1/families/{familyId}/work-values?workMonth=2026-08", familyId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.size()", equalTo(2));
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/work-values?workMonth=2025-01", familyId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.size()", equalTo(0));

        // 写接口限家长：孩子创建 403
        withToken(childToken).contentType(ContentType.JSON)
                .body(Map.of("workMonth", "2026-08", "salaryIncome", "0.00",
                        "allowanceAmount", "1.00"))
                .when().post("/api/v1/families/{familyId}/work-values", familyId)
                .then().statusCode(403).body("code", equalTo(100004));
    }
}
