package wyq.pocket.money.notify.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 默认空推送实现测试（M5 设计 §7.1）：不投递，恒返回 false。
 */
class NoopPushPortTest {

    @Test
    void sendShouldAlwaysReturnFalse() {
        assertThat(new NoopPushPort().send(1L, 42L, "device-token", "标题", "正文")).isFalse();
    }
}
