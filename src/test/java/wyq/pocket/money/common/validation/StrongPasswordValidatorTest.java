package wyq.pocket.money.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @StrongPassword 边界测试（M1 设计 §12.1）：长度与字母 / 数字组合。
 */
class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    private boolean isValid(String value) {
        return validator.isValid(value, null);
    }

    @Test
    void nullShouldPassAndDelegateToNotBlank() {
        assertThat(isValid(null)).isTrue();
    }

    @Test
    void shorterThanEightCharsShouldFail() {
        assertThat(isValid("Pass0rd")).isFalse();
    }

    @Test
    void eightCharsWithLetterAndDigitShouldPass() {
        assertThat(isValid("Passw0rd")).isTrue();
    }

    @Test
    void lettersOnlyShouldFail() {
        assertThat(isValid("PasswordOnly")).isFalse();
    }

    @Test
    void digitsOnlyShouldFail() {
        assertThat(isValid("12345678")).isFalse();
    }

    @Test
    void digitsAndSymbolsWithoutLetterShouldFail() {
        assertThat(isValid("12345678!")).isFalse();
    }

    @Test
    void lettersAndSymbolsWithoutDigitShouldFail() {
        assertThat(isValid("Password!")).isFalse();
    }

    @Test
    void typicalStrongPasswordShouldPass() {
        assertThat(isValid("Passw0rd!")).isTrue();
    }

    @Test
    void emptyStringShouldFail() {
        assertThat(isValid("")).isFalse();
    }
}
