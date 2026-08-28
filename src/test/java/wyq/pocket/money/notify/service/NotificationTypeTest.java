package wyq.pocket.money.notify.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 通知类型目录守护测试（M5 设计 §5.1）：4 类枚举完备，类型码与
 * V9 CHECK 约束一一对应。
 */
class NotificationTypeTest {

    @Test
    void shouldContainExactlyFourTypes() {
        assertThat(NotificationType.values()).containsExactlyInAnyOrder(
                NotificationType.TX_IN, NotificationType.TX_OUT,
                NotificationType.LOW_BALANCE, NotificationType.RULE_EXPIRED);
    }

    @Test
    void namesShouldMatchCheckConstraint() {
        assertThat(NotificationType.TX_IN.name()).isEqualTo("TX_IN");
        assertThat(NotificationType.TX_OUT.name()).isEqualTo("TX_OUT");
        assertThat(NotificationType.LOW_BALANCE.name()).isEqualTo("LOW_BALANCE");
        assertThat(NotificationType.RULE_EXPIRED.name()).isEqualTo("RULE_EXPIRED");
    }
}
