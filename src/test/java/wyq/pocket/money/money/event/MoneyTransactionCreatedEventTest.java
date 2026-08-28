package wyq.pocket.money.money.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 记账成功领域事件守护测试（M5 设计 §4.1）：record 组件不可变、
 * 字段完备（含 operatorUserId / remark 可空）。
 */
class MoneyTransactionCreatedEventTest {

    @Test
    void recordShouldCarryCompleteSnapshot() {
        MoneyTransactionCreatedEvent event = new MoneyTransactionCreatedEvent(10L, 42L, 99L,
                "IN", "MANUAL_ADD", new BigDecimal("50.00"),
                new BigDecimal("150.00"), 7L, "压岁钱");

        assertThat(event.familyId()).isEqualTo(10L);
        assertThat(event.userId()).isEqualTo(42L);
        assertThat(event.operatorUserId()).isEqualTo(99L);
        assertThat(event.direction()).isEqualTo("IN");
        assertThat(event.bizType()).isEqualTo("MANUAL_ADD");
        assertThat(event.amount()).isEqualByComparingTo("50.00");
        assertThat(event.balanceAfter()).isEqualByComparingTo("150.00");
        assertThat(event.transactionId()).isEqualTo(7L);
        assertThat(event.remark()).isEqualTo("压岁钱");
    }

    @Test
    void operatorUserIdAndRemarkMayBeNull() {
        MoneyTransactionCreatedEvent event = new MoneyTransactionCreatedEvent(10L, 42L, null,
                "IN", "MONTHLY_RULE", new BigDecimal("20.00"),
                new BigDecimal("20.00"), 8L, null);

        assertThat(event.operatorUserId()).isNull();
        assertThat(event.remark()).isNull();
    }
}
