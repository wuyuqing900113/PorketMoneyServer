package wyq.pocket.money.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 账务变动 → 通知事件链路集成测试（M5 设计 §10.2 NotifyEventPgIntegrationTest）：
 * 手动存入 → TX_IN；取出 → TX_OUT；取出后余额低于阈值 → LOW_BALANCE（主人 + 家长）。
 * 阈值经属性注入家庭级 5.00；各类型通知以 user_id 维度直查 notification 断言。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pocket-money.notify.low-balance-threshold=5.00"
})
class NotifyEventPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String nextPhone() {
        return String.format("1396%07d", COUNTER.incrementAndGet());
    }

    private static String nextUsername() {
        return String.format("pgne%08d", COUNTER.incrementAndGet());
    }

    private Response deposit(TestAccount parent, long userId, String amount) {
        return withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", userId, "amount", amount, "remark", "集成存入"))
                .when().post("/api/v1/families/{familyId}/deposits", parent.familyId());
    }

    private Response withdraw(TestAccount parent, long userId, String amount) {
        return withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", userId, "amount", amount, "remark", "集成取出"))
                .when().post("/api/v1/families/{familyId}/withdrawals", parent.familyId());
    }

    private List<String> typesOf(long userId) {
        return jdbcTemplate.queryForList(
                "SELECT type FROM notification WHERE user_id = ? ORDER BY id",
                String.class, userId);
    }

    @Test
    void transactionEventsShouldProduceCorrectNotificationTypes() {
        TestAccount parent = registerAndLogin(nextPhone());
        long childId = createChild(parent, nextUsername());

        // 存入 → 账户主人收到 TX_IN
        deposit(parent, childId, "10.00").then().statusCode(200).body("code", equalTo(0));
        // 取出（余额 8.00 ≥ 阈值 5.00）→ 仅 TX_OUT
        withdraw(parent, childId, "2.00").then().statusCode(200).body("code", equalTo(0));
        // 取出（余额 4.00 < 阈值 5.00）→ TX_OUT + LOW_BALANCE（主人 + 家长）
        withdraw(parent, childId, "4.00").then().statusCode(200).body("code", equalTo(0));

        List<String> childTypes = typesOf(childId);
        assertThat(childTypes).containsExactly("TX_IN", "TX_OUT", "TX_OUT", "LOW_BALANCE");
        // 家长作为余额不足接收人收到 LOW_BALANCE
        assertThat(typesOf(parent.userId())).containsExactly("LOW_BALANCE");

        String lowBalanceContent = jdbcTemplate.queryForObject(
                "SELECT content FROM notification WHERE user_id = ? AND type = 'LOW_BALANCE'",
                String.class, childId);
        assertThat(lowBalanceContent).contains("4.00").contains("低于提醒阈值");
    }
}
