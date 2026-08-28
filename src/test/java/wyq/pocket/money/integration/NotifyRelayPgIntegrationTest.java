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
 * 通知投递重试状态机集成测试（M5 设计 §10.2 NotifyRelayPgIntegrationTest）：
 * PENDING → 成功 SENT；失败退避重试；超 max-retry 置 DEAD。
 * 以 {@link ScriptedPushPort} 编排 PushPort 结果，直查 notification_delivery 断言行状态。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pocket-money.notify.push.enabled=true"
})
class NotifyRelayPgIntegrationTest extends AbstractPostgresIntegrationTest {

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

    private long depositAndGetDeliveryId(TestAccount parent, long childId) {
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "10.00", "remark", "投递测试"))
                .when().post("/api/v1/families/{familyId}/deposits", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM notification_delivery ORDER BY id DESC LIMIT 1", Long.class);
    }

    private void forceDue(long deliveryId) {
        jdbcTemplate.update(
                "UPDATE notification_delivery SET next_retry_at = now() - interval '1 minute' "
                        + "WHERE id = ?", deliveryId);
    }

    @Test
    void relayShouldTransitPendingToSentThenDead() {
        TestAccount parent = registerAndLogin(
                String.format("1398%07d", COUNTER.incrementAndGet()));
        long childId = createChild(parent,
                String.format("pgrl%08d", COUNTER.incrementAndGet()));

        // 成功路径：PushPort 返回 true → PENDING → SENT
        pushPort.setSucceed(true);
        long sentDeliveryId = depositAndGetDeliveryId(parent, childId);
        assertThat(relayService.drainPending()).isEqualTo(1);
        assertThat(statusOf(sentDeliveryId)).isEqualTo("SENT");

        // 失败路径：PushPort 返回 false → 退避重试 → 超 max-retry(3) 置 DEAD
        pushPort.setSucceed(false);
        long deadDeliveryId = depositAndGetDeliveryId(parent, childId);
        forceDue(deadDeliveryId);
        assertThat(relayService.drainPending()).isEqualTo(1);
        assertThat(statusOf(deadDeliveryId)).isEqualTo("PENDING");
        assertThat(retryCountOf(deadDeliveryId)).isEqualTo(1);

        forceDue(deadDeliveryId);
        assertThat(relayService.drainPending()).isEqualTo(1);
        assertThat(retryCountOf(deadDeliveryId)).isEqualTo(2);

        forceDue(deadDeliveryId);
        assertThat(relayService.drainPending()).isEqualTo(1);
        assertThat(statusOf(deadDeliveryId)).isEqualTo("DEAD");
        assertThat(retryCountOf(deadDeliveryId)).isEqualTo(3);
    }

    private String statusOf(long deliveryId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM notification_delivery WHERE id = ?", String.class,
                deliveryId);
    }

    private int retryCountOf(long deliveryId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM notification_delivery WHERE id = ?", Integer.class,
                deliveryId);
        return count == null ? 0 : count;
    }
}
