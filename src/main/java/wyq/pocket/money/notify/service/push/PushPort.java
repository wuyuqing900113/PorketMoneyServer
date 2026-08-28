package wyq.pocket.money.notify.service.push;

/**
 * 外部推送通道端口（M5 设计 D39，鸿蒙 Push 待选型）。
 *
 * <p>实现方：{@link NoopPushPort}（默认，push.enabled=false）/ 真实 Push 适配器
 * （通道拍板后）。契约约束：投递幂等（同 notification 重复 send 不得重复推送，
 * 实现方保证）。
 */
public interface PushPort {

    /**
     * 推送一条通知。
     *
     * @param notificationId 通知 ID
     * @param userId         接收人用户 ID
     * @param title          标题
     * @param content        正文
     * @return true = 已受理；false / 异常 = 投递失败（由 relay 记 FAILED）
     */
    boolean send(long notificationId, long userId, String title, String content);
}
