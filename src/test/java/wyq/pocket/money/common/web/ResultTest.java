package wyq.pocket.money.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import wyq.pocket.money.common.trace.TraceIdFilter;

/**
 * Result 统一响应体单元测试。
 */
class ResultTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void successShouldReturnCodeZeroAndData() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-123");

        Result<String> result = Result.success("ok");

        assertThat(result.code()).isZero();
        assertThat(result.message()).isEqualTo("success");
        assertThat(result.data()).isEqualTo("ok");
        assertThat(result.traceId()).isEqualTo("trace-123");
        assertThat(result.timestamp()).isPositive();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void successWithoutDataShouldReturnNullData() {
        Result<Void> result = Result.success();

        assertThat(result.data()).isNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void failureShouldCarryErrorCodeAndDefaultMessage() {
        Result<Void> result = Result.failure(CommonErrorCode.PARAM_INVALID);

        assertThat(result.code()).isEqualTo(100001);
        assertThat(result.message()).isEqualTo("参数校验失败");
        assertThat(result.data()).isNull();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void failureShouldSupportCustomMessage() {
        Result<Void> result = Result.failure(CommonErrorCode.PARAM_INVALID, "参数校验失败：金额不能为空");

        assertThat(result.code()).isEqualTo(100001);
        assertThat(result.message()).isEqualTo("参数校验失败：金额不能为空");
    }
}
