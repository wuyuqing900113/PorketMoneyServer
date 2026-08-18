package wyq.pocket.money.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * 通用错误码单元测试：校验段位划分与重试约定（M0 设计 §6.2）。
 */
class CommonErrorCodeTest {

    @Test
    void successCodeShouldBeZero() {
        assertThat(CommonErrorCode.SUCCESS.getCode()).isZero();
        assertThat(CommonErrorCode.SUCCESS.isRetryable()).isFalse();
    }

    @Test
    void codesShouldBeUnique() {
        long distinct = Arrays.stream(CommonErrorCode.values())
                .map(CommonErrorCode::getCode)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(CommonErrorCode.values().length);
    }

    @Test
    void nonSuccessCodesShouldBeSixDigits() {
        Arrays.stream(CommonErrorCode.values())
                .filter(code -> code != CommonErrorCode.SUCCESS)
                .forEach(code -> assertThat(code.getCode())
                        .as("错误码须为 6 位数字: %s", code.name())
                        .isBetween(100000, 999999));
    }

    @Test
    void systemSegmentCodesShouldBeRetryable() {
        assertThat(CommonErrorCode.INTERNAL_ERROR.isRetryable()).isTrue();
        assertThat(CommonErrorCode.DOWNSTREAM_TIMEOUT.isRetryable()).isTrue();
        assertThat(CommonErrorCode.DATABASE_ERROR.isRetryable()).isTrue();
        assertThat(CommonErrorCode.SERVICE_MAINTENANCE.isRetryable()).isTrue();
    }

    @Test
    void nonSystemSegmentCodesShouldNotBeRetryable() {
        Arrays.stream(CommonErrorCode.values())
                .filter(code -> code.getCode() > 0)
                .filter(code -> code.getCode() / ErrorCode.SEGMENT_BASE != ErrorCode.SYSTEM_SEGMENT)
                .forEach(code -> assertThat(code.isRetryable())
                        .as("非系统段错误码默认不可重试: %s", code.name())
                        .isFalse());
    }

    @Test
    void commonSegmentCodesShouldMatchDesign() {
        assertThat(CommonErrorCode.PARAM_INVALID.getCode()).isEqualTo(100001);
        assertThat(CommonErrorCode.UNAUTHORIZED.getCode()).isEqualTo(100003);
        assertThat(CommonErrorCode.FORBIDDEN.getCode()).isEqualTo(100004);
        assertThat(CommonErrorCode.RESOURCE_NOT_FOUND.getCode()).isEqualTo(100005);
        assertThat(CommonErrorCode.RATE_LIMITED.getCode()).isEqualTo(100007);
    }
}
