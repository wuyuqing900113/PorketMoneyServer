package wyq.pocket.money.notify.service.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.money.event.MoneyTransactionCreatedEvent;
import wyq.pocket.money.notify.config.NotifyProperties;
import wyq.pocket.money.notify.service.NotificationService;

/**
 * 账务变动 → 通知监听器测试（M5 设计 §6.2）：入账 → TX_IN；
 * 出账 → TX_OUT；出账低于阈值 → LOW_BALANCE（阈值 0 关闭）；
 * 监听器异常不回滚（try-catch 语义）。
 */
class MoneyTransactionNotifyListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);

    private final NotifyProperties properties = properties(new BigDecimal("5.00"));

    private final MoneyTransactionNotifyListener listener =
            new MoneyTransactionNotifyListener(notificationService, properties);

    private static NotifyProperties properties(BigDecimal threshold) {
        return new NotifyProperties(true, threshold,
                new NotifyProperties.Push(false),
                new NotifyProperties.Relay(true, "0 17 2 * * *", 3, Duration.ofMinutes(1)),
                new NotifyProperties.Cleanup(true, "0 47 4 * * *", Duration.ofDays(30)));
    }

    private MoneyTransactionCreatedEvent event(String direction, String balanceAfter) {
        String bizType = "IN".equals(direction) ? "MANUAL_ADD" : "WITHDRAW";
        return new MoneyTransactionCreatedEvent(10L, 42L, 99L, direction, bizType,
                new BigDecimal("20.00"), new BigDecimal(balanceAfter), 7L, null);
    }

    @Test
    void inboundShouldCreateTxNotificationOnly() {
        MoneyTransactionCreatedEvent event = event("IN", "150.00");

        listener.onTransactionCreated(event);

        verify(notificationService).createTxNotification(event);
        verify(notificationService, never())
                .createLowBalanceNotification(anyLong(), anyLong(), any());
    }

    @Test
    void outboundAboveThresholdShouldNotTriggerLowBalance() {
        MoneyTransactionCreatedEvent event = event("OUT", "6.00");

        listener.onTransactionCreated(event);

        verify(notificationService).createTxNotification(event);
        verify(notificationService, never())
                .createLowBalanceNotification(anyLong(), anyLong(), any());
    }

    @Test
    void outboundBelowThresholdShouldTriggerLowBalance() {
        MoneyTransactionCreatedEvent event = event("OUT", "4.00");

        listener.onTransactionCreated(event);

        verify(notificationService).createTxNotification(event);
        verify(notificationService).createLowBalanceNotification(10L, 42L,
                new BigDecimal("4.00"));
    }

    @Test
    void zeroThresholdShouldDisableLowBalance() {
        MoneyTransactionNotifyListener zeroListener =
                new MoneyTransactionNotifyListener(notificationService,
                        properties(BigDecimal.ZERO));
        MoneyTransactionCreatedEvent event = event("OUT", "4.00");

        zeroListener.onTransactionCreated(event);

        verify(notificationService).createTxNotification(event);
        verify(notificationService, never())
                .createLowBalanceNotification(anyLong(), anyLong(), any());
    }

    @Test
    void exceptionShouldBeSwallowed() {
        doThrow(new RuntimeException("db down"))
                .when(notificationService).createTxNotification(any());

        listener.onTransactionCreated(event("IN", "150.00"));
    }
}
