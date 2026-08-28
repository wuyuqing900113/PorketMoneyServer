package wyq.pocket.money.support;

import wyq.pocket.money.notify.service.push.PushPort;

/**
 * 可编排推送端口测试桩（M5 设计 §10.2 NotifyRelay/NotifyAudit 集成测试）：
 * 以 {@link #setSucceed(boolean)} 切换投递结果，验证 relay 的 SENT / 退避 / DEAD
 * 状态迁移与审计动作，无需真实 Push 通道。
 */
public class ScriptedPushPort implements PushPort {

    private volatile boolean succeed;

    /**
     * 设置投递结果。
     *
     * @param succeed true = 投递成功；false = 投递失败
     */
    public void setSucceed(boolean succeed) {
        this.succeed = succeed;
    }

    @Override
    public boolean send(long notificationId, long userId, String title, String content) {
        return succeed;
    }
}
