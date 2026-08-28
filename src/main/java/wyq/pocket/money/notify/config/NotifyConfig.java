package wyq.pocket.money.notify.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import wyq.pocket.money.notify.service.push.NoopPushPort;
import wyq.pocket.money.notify.service.push.PushPort;

/**
 * 通知抽象层装配（M5 设计 §7.1）：默认注册空实现 {@link NoopPushPort}。
 *
 * <p>真实推送适配器在通道拍板后实现 {@link PushPort} 并置
 * {@code pocket-money.notify.push.enabled=true}，业务零感知（镜像 M4 ChatPort）。
 */
@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyConfig {

    /**
     * 默认推送端口：无自定义 {@link PushPort} 时装配空实现（不投递）。
     *
     * @return PushPort Bean
     */
    @Bean
    @ConditionalOnMissingBean(PushPort.class)
    public PushPort pushPort() {
        return new NoopPushPort();
    }
}
