package wyq.pocket.money.notify.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import wyq.pocket.money.money.event.MoneyTransactionCreatedEvent;
import wyq.pocket.money.notify.config.NotifyProperties;
import wyq.pocket.money.notify.service.NotificationService;

/**
 * 账务变动 → 通知（M5 设计 §6.2）。
 *
 * <p>同步监听（默认），入账生成 TX_IN、出账生成 TX_OUT；出账且记账后余额
 * 低于阈值追加 LOW_BALANCE（阈值 0 关闭，D44）。监听器 try-catch，
 * 失败仅 ERROR 留痕，不回滚记账主流程（镜像 M2 D11）。
 */
@Component
public class MoneyTransactionNotifyListener {

    private static final Logger LOG = LoggerFactory.getLogger(MoneyTransactionNotifyListener.class);

    private final NotificationService notificationService;

    private final NotifyProperties properties;

    /**
     * 注入协作对象。
     *
     * @param notificationService 通知服务
     * @param properties          通知配置（余额不足阈值）
     */
    public MoneyTransactionNotifyListener(NotificationService notificationService,
                                          NotifyProperties properties) {
        this.notificationService = notificationService;
        this.properties = properties;
    }

    /**
     * 处理记账成功事件。
     *
     * @param event 记账成功事件
     */
    @EventListener
    public void onTransactionCreated(MoneyTransactionCreatedEvent event) {
        try {
            notificationService.createTxNotification(event);
            if ("OUT".equals(event.direction())
                    && event.balanceAfter().compareTo(properties.lowBalanceThreshold()) < 0) {
                notificationService.createLowBalanceNotification(event.familyId(),
                        event.userId(), event.balanceAfter());
            }
        } catch (RuntimeException e) {
            LOG.error("NOTIFY_LISTENER_FAILED type=TX userId={} transactionId={}",
                    event.userId(), event.transactionId(), e);
        }
    }
}
