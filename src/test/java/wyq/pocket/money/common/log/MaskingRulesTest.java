package wyq.pocket.money.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 敏感信息脱敏规则单元测试（M0 设计 §9.2 脱敏规则基线）。
 */
class MaskingRulesTest {

    @Test
    void shouldMaskMainlandMobile() {
        assertThat(MaskingRules.mask("联系电话 13812345678 充值"))
                .isEqualTo("联系电话 138****5678 充值");
    }

    @Test
    void shouldMaskIdCard() {
        assertThat(MaskingRules.mask("身份证 110101199001011234"))
                .isEqualTo("身份证 110***********1234");
    }

    @Test
    void shouldMaskIdCardEndingWithX() {
        assertThat(MaskingRules.mask("11010119900101123X"))
                .isEqualTo("110***********123X");
    }

    @Test
    void shouldMaskBankCardKeepingLastFour() {
        assertThat(MaskingRules.mask("6222021234567890123"))
                .isEqualTo("************0123");
    }

    @Test
    void shouldNotMaskThirteenDigitTimestamp() {
        assertThat(MaskingRules.mask("时间戳 1787654321000"))
                .isEqualTo("时间戳 1787654321000");
    }

    @Test
    void shouldMaskPasswordKeyValue() {
        assertThat(MaskingRules.mask("password=abc123")).isEqualTo("password=******");
        assertThat(MaskingRules.mask("password: \"abc123\"")).isEqualTo("password: \"******\"");
    }

    @Test
    void shouldMaskTokenCaseInsensitive() {
        assertThat(MaskingRules.mask("Token=xyz987")).isEqualTo("Token=******");
        assertThat(MaskingRules.mask("api_key: ak_123456")).isEqualTo("api_key: ******");
    }

    @Test
    void shouldMaskSecretInJsonStyleText() {
        assertThat(MaskingRules.mask("{\"secret\":\"s3cr3t-value\"}"))
                .isEqualTo("{\"secret\":\"******\"}");
    }

    @Test
    void shouldKeepNormalTextUnchanged() {
        String text = "零花钱增加 10 元，日期 2026-08-17";
        assertThat(MaskingRules.mask(text)).isEqualTo(text);
    }

    @Test
    void shouldHandleNullAndEmpty() {
        assertThat(MaskingRules.mask(null)).isNull();
        assertThat(MaskingRules.mask("")).isEmpty();
    }
}
