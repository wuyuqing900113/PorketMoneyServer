package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 登录锁定集成测试（M1 设计 §12.2 LoginLockoutIT / §4.5，D7）。
 *
 * <p>连续 5 次失败 → 锁定（第 5 次仍返回 200002，锁定在失败后生效）；
 * 锁定期内正确口令亦拒绝 200003；锁定期过后恢复。本套件覆盖基座
 * @SpringBootTest 属性，将锁定时长缩至 PT2S 以便验证恢复，不操纵数据库。
 * 独立上下文、复用同一静态容器。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
        "DATA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "pocket-money.security.login-guard.lock-duration=PT2S"
})
class LoginLockoutPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PHONE = "13910000031";

    private static final int MAX_ATTEMPTS = 5;

    private static final long LOCK_WAIT_MILLIS = 2500L;

    @Test
    void lockoutAndRecoveryShouldFollowGuardPolicy() throws InterruptedException {
        registerAndLogin(PHONE);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // 含第 5 次在内，每次失败均为 200002（锁定在失败记录后生效）
            loginAs(PHONE, "WrongPass1")
                    .then().statusCode(200).body("code", equalTo(200002));
        }

        // 锁定期内正确口令亦拒绝（校验顺序：锁定 → 停用 → 密码）
        loginAs(PHONE, DEFAULT_PASSWORD)
                .then().statusCode(200).body("code", equalTo(200003));

        Thread.sleep(LOCK_WAIT_MILLIS);

        // 锁定到期后恢复正常
        loginAs(PHONE, DEFAULT_PASSWORD)
                .then().statusCode(200).body("code", equalTo(0));
    }
}
