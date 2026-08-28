package wyq.pocket.money.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 审计动作枚举守护测试：动作名 ≤ 48 字符（audit_log.action VARCHAR(48)）、
 * 无重复、AI 动作段齐全（M4 设计 §9.2）。
 */
class AuditActionTest {

    @Test
    void namesShouldFitActionColumn() {
        for (AuditAction action : AuditAction.values()) {
            assertThat(action.name().length()).as(action.name())
                    .isLessThanOrEqualTo(48);
        }
    }

    @Test
    void namesShouldBeUnique() {
        List<String> names = Arrays.stream(AuditAction.values())
                .map(AuditAction::name).toList();
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    void aiActionsShouldBePresent() {
        assertThat(AuditAction.values()).contains(
                AuditAction.AI_SESSION_START,
                AuditAction.AI_INTENT,
                AuditAction.AI_ACTION_CONFIRM_REQUEST,
                AuditAction.AI_ACTION_EXECUTED,
                AuditAction.AI_ACTION_REJECTED,
                AuditAction.AI_ACTION_CANCELED,
                AuditAction.AI_ACTION_EXPIRED,
                AuditAction.AI_DEGRADED);
    }

    @Test
    void notifyActionsShouldBePresent() {
        assertThat(AuditAction.values()).contains(
                AuditAction.NOTIFY_DELIVERED,
                AuditAction.NOTIFY_DELIVERY_FAILED);
    }
}
