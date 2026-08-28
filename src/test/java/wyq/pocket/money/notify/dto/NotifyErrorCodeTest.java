package wyq.pocket.money.notify.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 通知模块错误码守护测试（M5 设计 §8.1）：段位 70xxxx、无重复、
 * 与设计表一致、不可重试、文案非空。
 */
class NotifyErrorCodeTest {

    @Test
    void codesShouldStayInNotifySegment() {
        for (NotifyErrorCode code : NotifyErrorCode.values()) {
            assertThat(code.getCode()).as(code.name()).isBetween(700000, 709999);
        }
    }

    @Test
    void codesShouldBeUnique() {
        List<Integer> codes = Arrays.stream(NotifyErrorCode.values())
                .map(NotifyErrorCode::getCode).toList();
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void notificationNotFoundShouldBe700001() {
        assertThat(NotifyErrorCode.NOTIFICATION_NOT_FOUND.getCode()).isEqualTo(700001);
    }

    @Test
    void codesShouldNotBeRetryable() {
        for (NotifyErrorCode code : NotifyErrorCode.values()) {
            assertThat(code.isRetryable()).as(code.name()).isFalse();
        }
    }

    @Test
    void messagesShouldNotBeBlank() {
        for (NotifyErrorCode code : NotifyErrorCode.values()) {
            assertThat(code.getMessage()).as(code.name()).isNotBlank();
        }
    }
}
