package wyq.pocket.money.rule.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 规则到期归档领域事件守护测试（M5 设计 §4.2）：record 组件不可变、
 * 文案字段（ruleName / endMonth）随事件携带免回查。
 */
class RuleArchivedEventTest {

    @Test
    void recordShouldCarryRuleSnapshot() {
        RuleArchivedEvent event = new RuleArchivedEvent(10L, 2L, 7L, "每周零花钱", "2026-07");

        assertThat(event.familyId()).isEqualTo(10L);
        assertThat(event.beneficiaryUserId()).isEqualTo(2L);
        assertThat(event.ruleId()).isEqualTo(7L);
        assertThat(event.ruleName()).isEqualTo("每周零花钱");
        assertThat(event.endMonth()).isEqualTo("2026-07");
    }
}
