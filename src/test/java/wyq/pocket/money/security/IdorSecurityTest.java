package wyq.pocket.money.security;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

/**
 * 越权（IDOR）专项（M6 设计 §8.1 A01 / §8.4）：篡改路径 familyId 访问他家庭
 * 财务报表，断言数据级守卫 {@code FamilyAccessChecker} 统一 403 + 100004，
 * 不回源数据。财务端点为家庭路径资源（M2 §8.2 #23–#24），跨家庭必须失权。
 */
class IdorSecurityTest extends AbstractH2SecurityIntegrationTest {

    @Test
    void crossFamilyFinanceAccessShouldBeDenied() {
        TestAccount parentA = registerAndLogin(nextPhone());
        TestAccount parentB = registerAndLogin(nextPhone());

        withToken(parentB).queryParam("month", "2026-08").when()
                .get("/api/v1/families/{familyId}/reports/income-expense", parentA.familyId())
                .then().statusCode(403).body("code", equalTo(100004));

        withToken(parentB).when()
                .get("/api/v1/families/{familyId}/statistics/summary", parentA.familyId())
                .then().statusCode(403).body("code", equalTo(100004));
    }

    @Test
    void nonexistentFamilyShouldBeDenied() {
        TestAccount account = registerAndLogin(nextPhone());

        withToken(account).queryParam("month", "2026-08").when()
                .get("/api/v1/families/{familyId}/reports/income-expense", 999999L)
                .then().statusCode(403).body("code", equalTo(100004));
    }
}
