package wyq.pocket.money.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.common.web.ErrorCode;

/**
 * 用户与家庭段错误码测试（M1 设计 §10.4 / §12.1）：
 * 段位 20xxxx、无重复值、不含 200010（归 common 层 SecurityErrorCode）。
 */
class UserErrorCodeTest {

    @Test
    void codesShouldBeUnique() {
        long distinct = Arrays.stream(UserErrorCode.values())
                .map(UserErrorCode::getCode)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(UserErrorCode.values().length);
    }

    @Test
    void codesShouldStayInUserSegment() {
        Arrays.stream(UserErrorCode.values())
                .forEach(code -> assertThat(code.getCode())
                        .as("用户与家庭段位 20xxxx: %s", code.name())
                        .isBetween(200000, 209999));
    }

    @Test
    void shouldNotDefineMustChangePasswordCode() {
        // 200010 由 SecurityErrorCode 单点定义（过滤链先于 MVC，ArchUnit 约束）
        assertThat(Arrays.stream(UserErrorCode.values()).map(ErrorCode::getCode))
                .doesNotContain(200010);
    }

    @Test
    void codesShouldMatchDesignTable() {
        assertThat(UserErrorCode.PHONE_ALREADY_REGISTERED.getCode()).isEqualTo(200001);
        assertThat(UserErrorCode.BAD_CREDENTIALS.getCode()).isEqualTo(200002);
        assertThat(UserErrorCode.ACCOUNT_LOCKED.getCode()).isEqualTo(200003);
        assertThat(UserErrorCode.ACCOUNT_DISABLED.getCode()).isEqualTo(200004);
        assertThat(UserErrorCode.FAMILY_NOT_FOUND.getCode()).isEqualTo(200005);
        assertThat(UserErrorCode.FAMILY_MEMBER_LIMIT_REACHED.getCode()).isEqualTo(200006);
        assertThat(UserErrorCode.USERNAME_TAKEN.getCode()).isEqualTo(200007);
        assertThat(UserErrorCode.OLD_PASSWORD_WRONG.getCode()).isEqualTo(200008);
        assertThat(UserErrorCode.REFRESH_TOKEN_INVALID.getCode()).isEqualTo(200009);
        assertThat(UserErrorCode.MEMBER_NOT_IN_FAMILY.getCode()).isEqualTo(200011);
        assertThat(UserErrorCode.CANNOT_REMOVE_OWNER.getCode()).isEqualTo(200012);
    }

    @Test
    void userSegmentCodesShouldNotBeRetryable() {
        Arrays.stream(UserErrorCode.values())
                .forEach(code -> assertThat(code.isRetryable())
                        .as("用户段错误码默认不可重试: %s", code.name())
                        .isFalse());
    }

    @Test
    void messagesShouldNotBeBlank() {
        Arrays.stream(UserErrorCode.values())
                .forEach(code -> assertThat(code.getMessage())
                        .as("错误码须带默认提示: %s", code.name())
                        .isNotBlank());
    }
}
