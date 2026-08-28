package wyq.pocket.money.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * 敏感数据暴露专项（M6 设计 §8.1 A05 / §8.5）：出参不含密码哈希 / 加密密钥 /
 * 明文手机号，手机号以脱敏形式回显；密钥材料（JWT / AES）不回传。
 */
class SensitiveDataExposureTest extends AbstractH2SecurityIntegrationTest {

    @Test
    void meResponseShouldMaskPhoneAndExposeNoSecrets() {
        String phone = nextPhone();
        TestAccount account = registerAndLogin(phone);

        Response me = withToken(account).when().get("/api/v1/users/me");
        me.then().statusCode(200).body("code", equalTo(0))
                .body("data.maskedPhone", notNullValue());

        String body = me.getBody().asString();
        assertThat(me.jsonPath().getString("data.maskedPhone")).contains("****");
        assertThat(body).doesNotContain(phone)
                .doesNotContain("passwordHash", "phoneEncrypted", "keyVersion", "key_version",
                        "data-key", "DATA_ENCRYPTION_KEY", "JWT_SECRET");
    }
}
