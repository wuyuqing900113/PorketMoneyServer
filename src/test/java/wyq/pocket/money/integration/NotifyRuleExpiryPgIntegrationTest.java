package wyq.pocket.money.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import wyq.pocket.money.common.time.ClockConfig;
import wyq.pocket.money.rule.job.RuleExpiryJob;

/**
 * 规则到期归档 → 通知集成测试（M5 设计 §10.2 NotifyRuleExpiryPgIntegrationTest）：
 * 固定时钟 2026-08，创建 end_month=2026-07 的规则，触发 RuleExpiryJob 归档，
 * 受益人 + 家长均收到 RULE_EXPIRED。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class NotifyRuleExpiryPgIntegrationTest extends AbstractPostgresIntegrationTest {

    /** 固定时钟：2026-08-19（Asia/Shanghai），到期月 = 2026-08。 */
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
    private RuleExpiryJob ruleExpiryJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ruleExpiryShouldNotifyBeneficiaryAndParents() {
        TestAccount parent = registerAndLogin(
                String.format("1397%07d", COUNTER.incrementAndGet()));
        long childId = createChild(parent,
                String.format("pgre%08d", COUNTER.incrementAndGet()));

        // 建规则：生效 2026-06 ~ 2026-07（早于固定时钟 2026-08 → 到期）
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("beneficiaryUserId", childId, "ruleName", "每周零花钱",
                        "amount", "20.00", "grantDay", 1, "startMonth", "2026-06",
                        "endMonth", "2026-07"))
                .when().post("/api/v1/families/{familyId}/rules", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0));

        // 触发到期归档 → 发布 RuleArchivedEvent → 通知受益人 + 家长
        ruleExpiryJob.run();

        List<String> childTypes = jdbcTemplate.queryForList(
                "SELECT type FROM notification WHERE user_id = ?", String.class, childId);
        List<String> parentTypes = jdbcTemplate.queryForList(
                "SELECT type FROM notification WHERE user_id = ?", String.class,
                parent.userId());
        assertThat(childTypes).containsExactly("RULE_EXPIRED");
        assertThat(parentTypes).containsExactly("RULE_EXPIRED");

        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM notification WHERE user_id = ? AND type = 'RULE_EXPIRED'",
                String.class, childId);
        assertThat(content).contains("每周零花钱").contains("2026-07");
    }
}
