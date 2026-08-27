package wyq.pocket.money.money.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 零花钱模块错误码守护测试（M2 设计 §8.4）：段位 30xxxx、无重复、
 * 与设计表一致、不可重试、文案非空。
 */
class MoneyErrorCodeTest {

    @Test
    void codesShouldStayInMoneySegment() {
        for (MoneyErrorCode code : MoneyErrorCode.values()) {
            assertThat(code.getCode()).as(code.name())
                    .isBetween(300000, 309999);
        }
    }

    @Test
    void codesShouldBeUnique() {
        List<Integer> codes = Arrays.stream(MoneyErrorCode.values())
                .map(MoneyErrorCode::getCode).toList();
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void codesShouldMatchDesignTable() {
        assertThat(MoneyErrorCode.BALANCE_NOT_ENOUGH.getCode()).isEqualTo(300001);
        assertThat(MoneyErrorCode.ACCOUNT_FROZEN.getCode()).isEqualTo(300002);
        assertThat(MoneyErrorCode.TRANSACTION_NOT_FOUND.getCode()).isEqualTo(300003);
        assertThat(MoneyErrorCode.AMOUNT_INVALID.getCode()).isEqualTo(300004);
        assertThat(MoneyErrorCode.LEARNING_TASK_NOT_FOUND.getCode()).isEqualTo(300005);
        assertThat(MoneyErrorCode.TASK_STATUS_NOT_ALLOWED.getCode()).isEqualTo(300006);
        assertThat(MoneyErrorCode.WORK_VALUE_NOT_FOUND.getCode()).isEqualTo(300007);
    }

    @Test
    void codesShouldNotBeRetryable() {
        for (MoneyErrorCode code : MoneyErrorCode.values()) {
            assertThat(code.isRetryable()).as(code.name()).isFalse();
        }
    }

    @Test
    void messagesShouldNotBeBlank() {
        for (MoneyErrorCode code : MoneyErrorCode.values()) {
            assertThat(code.getMessage()).as(code.name()).isNotBlank();
        }
    }
}
