package wyq.pocket.money.notify.dto;

/**
 * 待投递记录投影（M5 设计 §7.2 / GA D68）：投递引擎扫描所需的接收人 + 设备令牌
 * + 文案 + 重试元数据。deviceToken 经 LEFT JOIN user_push_token 取得，
 * 用户未注册推送令牌时为 null（relay 按 NO_PUSH_TOKEN 失败重试，令牌注册后下一轮自动命中）。
 *
 * @param deliveryId     投递记录 ID
 * @param notificationId 通知 ID
 * @param retryCount     已重试次数
 * @param userId         接收人用户 ID
 * @param deviceToken    接收人设备推送令牌（未注册为 null）
 * @param title          标题
 * @param content        正文
 */
public record PendingDelivery(long deliveryId, long notificationId, int retryCount,
        long userId, String deviceToken, String title, String content) {
}
