package wyq.pocket.money.notify.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import wyq.pocket.money.notify.service.NotificationService;
import wyq.pocket.money.rule.event.RuleArchivedEvent;

/**
 * 规则到期归档 → 通知（M5 设计 §6.2）。
 *
 * <p>同步监听（默认），生成 RULE_EXPIRED 通知（受益人 + 家长）。
 * 监听器 try-catch，失败仅 ERROR 留痕，不回滚归档主流程（镜像 M2 D11）。
 */
@Component
public class RuleArchivedNotifyListener {

    private static final Logger LOG = LoggerFactory.getLogger(RuleArchivedNotifyListener.class);

    private final NotificationService notificationService;

    /**
     * 注入通知服务。
     *
     * @param notificationService 通知服务
     */
    public RuleArchivedNotifyListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 处理规则到期归档事件。
     *
     * @param event 规则到期归档事件
     */
    @EventListener
    public void onRuleArchived(RuleArchivedEvent event) {
        try {
            notificationService.createRuleExpiredNotification(event);
        } catch (RuntimeException e) {
            LOG.error("NOTIFY_LISTENER_FAILED type=RULE_EXPIRED familyId={} ruleId={}",
                    event.familyId(), event.ruleId(), e);
        }
    }
}
