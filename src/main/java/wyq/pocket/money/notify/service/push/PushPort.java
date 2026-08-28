package wyq.pocket.money.notify.service.push;

/**
 * 外部推送通道端口（M5 设计 D39 / GA D68：通道拍板为鸿蒙 Push Kit）。
 *
 * <p>实现方：{@link NoopPushPort}（默认兜底，push.enabled=false 时不产生 delivery 行）/
 * {@code HarmonyPushPort}（生产实现，NOTIFY_PUSH_ENABLED=true 时装配）。
 *
 * <p>投递语义：relay 仅对 PENDING 行调用本端口，SENT 行不再扫描，正常路径不会重复推送；
 * 网络超时等「结果不明」情况下按 at-least-once 重试，端侧通知中心按 notificationId 去重。
 */
public interface PushPort {

    /**
     * 推送一条通知。
     *
     * @param notificationId 通知 ID（端侧去重键）
     * @param userId         接收人用户 ID
     * @param deviceToken    接收人设备推送令牌（null / 空白 = 未注册，调用方按失败重试处理）
     * @param title          标题
     * @param content        正文
     * @return true = 通道已受理；false / 异常 = 投递失败（由 relay 退避重试，耗尽置 DEAD）
     */
    boolean send(long notificationId, long userId, String deviceToken, String title, String content);
}
