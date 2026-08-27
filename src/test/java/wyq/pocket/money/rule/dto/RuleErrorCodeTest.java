package wyq.pocket.money.rule.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 规则模块错误码守护测试（M2 设计 §8.4）：段位 40xxxx、无重复、
 * 与设计表一致、不可重试、文案非空。
 */
class RuleErrorCodeTest {

    @Test
    void codesShouldStayInRuleSegment() {
        for (RuleErrorCode code : RuleErrorCode.values()) {
            assertThat(code.getCode()).as(code.name())
                    .isBetween(400000, 409999);
        }
    }

    @Test
    void codesShouldBeUnique() {
        List<Integer> codes = Arrays.stream(RuleErrorCode.values())
                .map(RuleErrorCode::getCode).toList();
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void codesShouldMatchDesignTable() {
        assertThat(RuleErrorCode.RULE_NOT_FOUND.getCode()).isEqualTo(400001);
        assertThat(RuleErrorCode.RULE_STATUS_NOT_ALLOWED.getCode()).isEqualTo(400002);
        assertThat(RuleErrorCode.GRANT_DAY_INVALID.getCode()).isEqualTo(400003);
        assertThat(RuleErrorCode.RULE_LIMIT_REACHED.getCode()).isEqualTo(400004);
        assertThat(RuleErrorCode.RULE_HAS_GRANTS.getCode()).isEqualTo(400005);
        assertThat(RuleErrorCode.RULE_NAME_DUPLICATE.getCode()).isEqualTo(400006);
    }

    @Test
    void codesShouldNotBeRetryable() {
        for (RuleErrorCode code : RuleErrorCode.values()) {
            assertThat(code.isRetryable()).as(code.name()).isFalse();
        }
    }

    @Test
    void messagesShouldNotBeBlank() {
        for (RuleErrorCode code : RuleErrorCode.values()) {
            assertThat(code.getMessage()).as(code.name()).isNotBlank();
        }
    }
}
