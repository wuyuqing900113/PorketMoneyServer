package wyq.pocket.money.finance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 财务模块错误码守护测试（M2 设计 §8.4）：段位 50xxxx、无重复、
 * 与设计表一致、不可重试、文案非空。
 */
class FinanceErrorCodeTest {

    @Test
    void codesShouldStayInFinanceSegment() {
        for (FinanceErrorCode code : FinanceErrorCode.values()) {
            assertThat(code.getCode()).as(code.name())
                    .isBetween(500000, 509999);
        }
    }

    @Test
    void codesShouldBeUnique() {
        List<Integer> codes = Arrays.stream(FinanceErrorCode.values())
                .map(FinanceErrorCode::getCode).toList();
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void codesShouldMatchDesignTable() {
        assertThat(FinanceErrorCode.MONTH_FORMAT_INVALID.getCode()).isEqualTo(500001);
        assertThat(FinanceErrorCode.MONTH_IN_FUTURE.getCode()).isEqualTo(500002);
    }

    @Test
    void codesShouldNotBeRetryable() {
        for (FinanceErrorCode code : FinanceErrorCode.values()) {
            assertThat(code.isRetryable()).as(code.name()).isFalse();
        }
    }

    @Test
    void messagesShouldNotBeBlank() {
        for (FinanceErrorCode code : FinanceErrorCode.values()) {
            assertThat(code.getMessage()).as(code.name()).isNotBlank();
        }
    }
}
