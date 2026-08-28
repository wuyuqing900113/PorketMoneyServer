package wyq.pocket.money.security;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

/**
 * SQL 注入专项（M6 设计 §8.1 A03）：对字符串查询参数注入典型载荷，
 * 断言被参数校验拒绝（500001）而非触发 500 或数据越权 / 结构破坏。
 *
 * <p>{@code month} 为财务报表唯一字符串入参，先经 {@code ^\d{4}-(0[1-9]|1[0-2])$}
 * 正则校验，注入串不达数据库；MyBatis 全 {@code #\{\}} 参数化为第二道防线。
 */
class SqlInjectionSecurityTest extends AbstractH2SecurityIntegrationTest {

    @Test
    void injectedMonthShouldBeRejectedWithoutLeakOr500() {
        TestAccount account = registerAndLogin(nextPhone());
        String reportPath = "/api/v1/families/{familyId}/reports/income-expense";

        String[] payloads = {
                "' OR '1'='1",
                "2026-08'--",
                "2026-08; DROP TABLE app_user",
                "2026-08 UNION SELECT username, password_hash FROM app_user"
        };
        for (String payload : payloads) {
            withToken(account).queryParam("month", payload).when()
                    .get(reportPath, account.familyId())
                    .then().statusCode(200).body("code", equalTo(500001));
        }

        // 注入尝试后服务仍正常，无数据越权 / 表结构破坏
        withToken(account).when().get("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(0));
    }
}
