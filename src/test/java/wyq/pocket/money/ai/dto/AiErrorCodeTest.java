package wyq.pocket.money.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * AI 模块错误码守护测试（M4 设计 §9.1）：段位 60xxxx、无重复、
 * 与设计表一致、不可重试、文案非空。
 */
class AiErrorCodeTest {

    @Test
    void codesShouldStayInAiSegment() {
        for (AiErrorCode code : AiErrorCode.values()) {
            assertThat(code.getCode()).as(code.name())
                    .isBetween(600000, 609999);
        }
    }

    @Test
    void codesShouldBeUnique() {
        List<Integer> codes = Arrays.stream(AiErrorCode.values())
                .map(AiErrorCode::getCode).toList();
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void codesShouldMatchDesignTable() {
        assertThat(AiErrorCode.AI_UNAVAILABLE.getCode()).isEqualTo(600001);
        assertThat(AiErrorCode.INTENT_UNRECOGNIZED.getCode()).isEqualTo(600002);
        assertThat(AiErrorCode.PENDING_ACTION_INVALID.getCode()).isEqualTo(600003);
        assertThat(AiErrorCode.PENDING_ACTION_EXISTS.getCode()).isEqualTo(600004);
    }

    @Test
    void codesShouldNotBeRetryable() {
        for (AiErrorCode code : AiErrorCode.values()) {
            assertThat(code.isRetryable()).as(code.name()).isFalse();
        }
    }

    @Test
    void messagesShouldNotBeBlank() {
        for (AiErrorCode code : AiErrorCode.values()) {
            assertThat(code.getMessage()).as(code.name()).isNotBlank();
        }
    }
}
