package wyq.pocket.money.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
import wyq.pocket.money.notify.service.NotificationRelayService;
import wyq.pocket.money.support.ScriptedPushPort;

/**
 * 通知审计落库集成测试（M5 设计 §10.2 NotifyAuditPgIntegrationTest）：
 * 通知生成 / 投递成功 / 死信三类动作落 audit_log 可追溯，以 target_type 区分
 * 站内信（NOTIFICATION）与外部投递（NOTIFICATION_DELIVERY）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pocket-money.notify.push.enabled=true"
})
class NotifyAuditPgIntegrationTest extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    static class ScriptedPushConfig {

        @Bean
        @Primary
        ScriptedPushPort scriptedPushPort() {
            return new ScriptedPushPort();
        }
    }

    private static final AtomicLong COUNTER = new AtomicLong();

    @Autowired
    private NotificationRelayService relayService;

    @Autowired
    private ScriptedPushPort pushPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void notifyActionsShouldLandInAuditLog() {
        TestAccount parent = registerAndLogin(
                String.format("1399%07d", COUNTER.incrementAndGet()));
        long childId = createChild(parent,
                String.format("pgau%08d", COUNTER.incrementAndGet()));

        // 投递成功路径：通知生成 + 投递成功各落一条 NOTIFY_DELIVERED
        pushPort.setSucceed(true);
        deposit(parent, childId);
        long sentDeliveryId = latestDeliveryId();
        assertThat(relayService.drainPending()).isEqualTo(1);
        assertThat(statusOf(sentDeliveryId)).isEqualTo("SENT");

        // 死信路径：投递失败重试耗尽落 NOTIFY_DELIVERY_FAILED
        pushPort.setSucceed(false);
        deposit(parent, childId);
        long deadDeliveryId = latestDeliveryId();
        for (int i = 0; i < 3; i++) {
            forceDue(deadDeliveryId);
            relayService.drainPending();
        }
        assertThat(statusOf(deadDeliveryId)).isEqualTo("DEAD");

        // 三类动作均落 audit_log（user_id = 接收人 childId）
        assertThat(countAudit(childId, "NOTIFY_DELIVERED", "NOTIFICATION")).isEqualTo(2);
        assertThat(countAudit(childId, "NOTIFY_DELIVERED", "NOTIFICATION_DELIVERY"))
                .isEqualTo(1);
        assertThat(countAudit(childId, "NOTIFY_DELIVERY_FAILED", "NOTIFICATION_DELIVERY"))
                .isEqualTo(1);
    }

    private void deposit(TestAccount parent, long childId) {
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "10.00", "remark", "审计测试"))
                .when().post("/api/v1/families/{familyId}/deposits", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0));
    }

    private long latestDeliveryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM notification_delivery ORDER BY id DESC LIMIT 1", Long.class);
    }

    private String statusOf(long deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM notification_delivery WHERE id = ?", String.class,
                deliveryId);
    }

    private void forceDue(long deliveryId) {
        jdbcTemplate.update(
                "UPDATE notification_delivery SET next_retry_at = now() - interval '1 minute' "
                        + "WHERE id = ?", deliveryId);
    }

    private int countAudit(long userId, String action, String targetType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = ? "
                        + "AND target_type = ?",
                Integer.class, userId, action, targetType);
        return count == null ? 0 : count;
    }
}
