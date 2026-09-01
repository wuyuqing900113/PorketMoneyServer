package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.rule.service.RuleSettlementService;

/**
 * 包月规则结算集成测试（M2 设计 §12.2 RuleSettlementIT，注入固定 Clock）：
 * 发放日发放、重复结算幂等跳过、暂停不发放、移除成员即停发。
 *
 * <p>覆盖基座属性：固定时钟 2026-08-19、停用定时 Job 避免与手工结算竞争。
 * 固定时钟以同名 Bean {@code clock} 替换 {@code ClockConfig} 的系统时钟（单 Bean，
 * 全链路统一为固定时刻），名称覆盖需放开 Bean 定义覆写。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pocket-money.money.settlement.enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
class RuleSettlementPgIntegrationTest extends AbstractPostgresIntegrationTest {

    /** 固定时钟：2026-08-19（Asia/Shanghai），结算月 = 2026-08；Bean 名同名覆盖系统时钟。 */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(LocalDate.of(2026, 8, 19)
                    .atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(),
                    ClockConfig.BUSINESS_ZONE);
        }
    }

    private static final AtomicLong COUNTER = new AtomicLong();

    @Autowired
    private RuleSettlementService settlementService;

    @Test
    void settlementShouldGrantOnceAndStopOnPauseOrRemoval() {
        TestAccount parent = registerAndLogin(
                String.format("1395%07d", COUNTER.incrementAndGet()));
        long childId = createChild(parent,
                String.format("pgrs%08d", COUNTER.incrementAndGet()));

        long familyId = parent.familyId();

        // 建规则：发放日 1 ≤ 今日 19，当月起生效
        Response createRule = withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("beneficiaryUserId", childId, "ruleName", "月零花钱",
                        "amount", "30.00", "grantDay", 1, "startMonth", "2026-08"))
                .when().post("/api/v1/families/{familyId}/rules", familyId);
        createRule.then().statusCode(200).body("code", equalTo(0));
        long ruleId = createRule.jsonPath().getLong("data.id");

        // 首次结算：发放 1 条
        assertEquals(1, settlementService.settleDueRules());
        Response dashboard = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", familyId);
        dashboard.then().statusCode(200).body("code", equalTo(0));
        assertEquals(0, new BigDecimal(dashboard.jsonPath().getString("data.totalBalance"))
                .compareTo(new BigDecimal("30.00")), "结算后余额应为 30.00");

        // 幂等：再次结算跳过（uk rule_id + grant_month），余额不重复增长
        assertEquals(0, settlementService.settleDueRules());
        Response dashboardAgain = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", familyId);
        dashboardAgain.then().statusCode(200).body("code", equalTo(0));
        assertEquals(0, new BigDecimal(dashboardAgain.jsonPath().getString("data.totalBalance"))
                .compareTo(new BigDecimal("30.00")), "重复结算不得重复入账");

        // 详情页可见当月发放记录
        withToken(parent).when().get("/api/v1/families/{familyId}/rules/{ruleId}",
                        familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.rule.grantedThisMonth", equalTo(true))
                .body("data.recentGrants", hasSize(1))
                .body("data.recentGrants[0].grantMonth", equalTo("2026-08"));

        // 暂停 → 不再命中应发扫描
        withToken(parent).when()
                .post("/api/v1/families/{familyId}/rules/{ruleId}/pause", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0));
        assertEquals(0, settlementService.settleDueRules());

        // 恢复后当月仍因幂等跳过（宁漏勿错，不重发）
        withToken(parent).when()
                .post("/api/v1/families/{familyId}/rules/{ruleId}/resume", familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0));
        assertEquals(0, settlementService.settleDueRules());

        // 移除成员 → 联动暂停规则（移除即停发）
        withToken(parent).when()
                .delete("/api/v1/families/{familyId}/members/{userId}",
                        familyId, childId)
                .then().statusCode(200).body("code", equalTo(0));
        withToken(parent).when().get("/api/v1/families/{familyId}/rules/{ruleId}",
                        familyId, ruleId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.rule.status", equalTo("PAUSED"));
        assertEquals(0, settlementService.settleDueRules());
    }
}
