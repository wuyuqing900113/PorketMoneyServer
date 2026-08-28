package wyq.pocket.money.notify.dto;

/**
 * 待投递记录投影（M5 设计 §7.2）：投递引擎扫描所需的接收人 + 文案 + 重试元数据。
 *
 * @param deliveryId     投递记录 ID
 * @param notificationId 通知 ID
 * @param retryCount     已重试次数
 * @param userId         接收人用户 ID
 * @param title          标题
 * @param content        正文
 */
public record PendingDelivery(long deliveryId, long notificationId, int retryCount,
        long userId, String title, String content) {
}
