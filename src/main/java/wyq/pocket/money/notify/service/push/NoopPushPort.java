package wyq.pocket.money.notify.service.push;

/**
 * 默认空推送实现（M5 设计 §7.1）：不投递，恒返回 false。
 *
 * <p>push.enabled=false 时根本不产生 delivery 行，relay 不会调用本实现；
 * 作为默认 Bean 兜底避免缺失实现导致启动失败（镜像 M4 StubChatPort）。
 */
public class NoopPushPort implements PushPort {

    @Override
    public boolean send(long notificationId, long userId, String deviceToken,
                        String title, String content) {
        return false;
    }
}
