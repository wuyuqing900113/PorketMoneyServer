package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * 并发取出集成测试（M2 设计 §12.2 ConcurrencyWithdrawIT）：
 * 余额 100 时 8 个并发取出 100，恰好 1 笔成功，其余 300001，
 * 最终余额 0、流水恰 2 条（1 存 + 1 取），乐观锁不产生超发。
 */
class ConcurrencyWithdrawPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong COUNTER = new AtomicLong();

    private static final int CONCURRENT_WITHDRAWS = 8;

    @Test
    void exactlyOneWithdrawShouldSucceedUnderContention() throws Exception {
        TestAccount parent = registerAndLogin(
                String.format("1394%07d", COUNTER.incrementAndGet()));
        long childId = createChild(parent,
                String.format("pgcw%08d", COUNTER.incrementAndGet()));
        withToken(parent).contentType(ContentType.JSON)
                .body(Map.of("targetUserId", childId, "amount", "100.00", "remark", "本金"))
                .when().post("/api/v1/families/{familyId}/deposits", parent.familyId())
                .then().statusCode(200).body("code", equalTo(0));

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch ready = new CountDownLatch(CONCURRENT_WITHDRAWS);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<Integer>> codes = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_WITHDRAWS; i++) {
                codes.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await();
                    Response response = withToken(parent).contentType(ContentType.JSON)
                            .body(Map.of("targetUserId", childId, "amount", "100.00"))
                            .when().post("/api/v1/families/{familyId}/withdrawals",
                                    parent.familyId());
                    return response.jsonPath().getInt("code");
                }));
            }
            ready.await();
            fire.countDown();
            int succeeded = 0;
            for (Future<Integer> future : codes) {
                int code = future.get();
                if (code == 0) {
                    succeeded++;
                } else if (code != 300001) {
                    throw new AssertionError("非预期错误码: " + code);
                }
            }
            org.junit.jupiter.api.Assertions.assertEquals(1, succeeded,
                    "并发取空应恰好 1 笔成功");
        } finally {
            pool.shutdownNow();
        }

        // 最终一致：余额 0，流水 2 条
        Response dashboard = withToken(parent).when()
                .get("/api/v1/families/{familyId}/dashboard", parent.familyId());
        dashboard.then().statusCode(200).body("code", equalTo(0));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new BigDecimal(dashboard.jsonPath().getString("data.totalBalance"))
                        .compareTo(java.math.BigDecimal.ZERO),
                "并发取空后余额应为 0");
        withToken(parent).when()
                .get("/api/v1/families/{familyId}/transactions?userId={userId}",
                        parent.familyId(), childId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.total", equalTo(2));
    }
}
