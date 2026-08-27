package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.response.Response;
import wyq.pocket.money.common.crypto.DataEncryptor;
import wyq.pocket.money.common.security.jwt.JwtTokenService;
import wyq.pocket.money.integration.support.PerformanceDataSeeder;

/**
 * M2 性能基线集成测试（M2 设计 §15 DoD）：50 家庭 × 8 成员 × 36 月 ≈ 5 万流水
 * 数据量下，4 个读端点各 10 次热身 + 200 次计时，P95 ≤ 500ms。
 *
 * <p>默认由 surefire excludedGroups=performance 排除，手动启用：
 * {@code mvn test -Dgroups=performance "-Dsurefire.excludedGroups="}。
 */
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceBaselinePgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final int WARMUP_CALLS = 10;

    private static final int MEASURED_CALLS = 200;

    private static final long P95_LIMIT_MILLIS = 500;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataEncryptor dataEncryptor;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String accessToken;

    private long familyId;

    private String currentMonth;

    /** 幂等种子：显式 id 段已存在则跳过；令牌直签，免注册链路。 */
    @BeforeAll
    void seedBaselineData() {
        new PerformanceDataSeeder(jdbcTemplate, dataEncryptor).seed();
        familyId = PerformanceDataSeeder.familyId(1);
        accessToken = jwtTokenService.issueAccessToken(
                PerformanceDataSeeder.parentUserId(1), familyId, "PARENT", false);
        currentMonth = YearMonth.now(BUSINESS_ZONE).toString();
    }

    @Test
    void seedVolumeShouldMatchDesign() {
        Integer transactions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM money_transaction WHERE id >= 1000000",
                Integer.class);
        assertEquals(PerformanceDataSeeder.totalTransactions(), transactions.intValue(),
                "种子流水规模应为 50×7×36×4 = 50400");
    }

    @Test
    void dashboardP95ShouldStayUnderBaseline() {
        assertP95("dashboard", () -> withToken(accessToken)
                .when().get("/api/v1/families/{familyId}/dashboard", familyId));
    }

    @Test
    void transactionsP95ShouldStayUnderBaseline() {
        assertP95("transactions", () -> withToken(accessToken)
                .when().get("/api/v1/families/{familyId}/transactions?page=1&size=50",
                        familyId));
    }

    @Test
    void dailyTrendP95ShouldStayUnderBaseline() {
        assertP95("trends(DAY)", () -> withToken(accessToken)
                .when().get("/api/v1/families/{familyId}/trends?granularity=DAY",
                        familyId));
    }

    @Test
    void monthlyReportP95ShouldStayUnderBaseline() {
        assertP95("reports/income-expense", () -> withToken(accessToken)
                .when().get("/api/v1/families/{familyId}/reports/income-expense?month={month}",
                        familyId, currentMonth));
    }

    private void assertP95(String name, Supplier<Response> call) {
        for (int i = 0; i < WARMUP_CALLS; i++) {
            call.get().then().statusCode(200).body("code", equalTo(0));
        }
        long[] samplesMillis = new long[MEASURED_CALLS];
        for (int i = 0; i < MEASURED_CALLS; i++) {
            long startNanos = System.nanoTime();
            call.get().then().statusCode(200).body("code", equalTo(0));
            samplesMillis[i] = (System.nanoTime() - startNanos) / 1_000_000;
        }
        Arrays.sort(samplesMillis);
        long p95Millis = samplesMillis[MEASURED_CALLS * 95 / 100 - 1];
        assertTrue(p95Millis <= P95_LIMIT_MILLIS,
                name + " P95=" + p95Millis + "ms 超出 500ms 基线");
    }
}
