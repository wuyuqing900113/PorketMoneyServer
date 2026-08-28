package wyq.pocket.money.notify.service.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.notify.service.NotificationService;
import wyq.pocket.money.rule.event.RuleArchivedEvent;

/**
 * 规则到期归档 → 通知监听器测试（M5 设计 §6.2）：到期事件 → RULE_EXPIRED；
 * 监听器异常不回滚归档主流程。
 */
class RuleArchivedNotifyListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);

    private final RuleArchivedNotifyListener listener =
            new RuleArchivedNotifyListener(notificationService);

    @Test
    void shouldCreateRuleExpiredNotification() {
        RuleArchivedEvent event = new RuleArchivedEvent(10L, 2L, 7L, "每周零花钱", "2026-07");

        listener.onRuleArchived(event);

        verify(notificationService).createRuleExpiredNotification(event);
    }

    @Test
    void exceptionShouldBeSwallowed() {
        doThrow(new RuntimeException("db down"))
                .when(notificationService).createRuleExpiredNotification(any());

        listener.onRuleArchived(new RuleArchivedEvent(10L, 2L, 7L, "每周零花钱", "2026-07"));
    }
}
