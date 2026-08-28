package wyq.pocket.money.notify.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import wyq.pocket.money.notify.config.NotifyProperties.Push.Harmony;
import wyq.pocket.money.notify.service.push.HarmonyPushPort;
import wyq.pocket.money.notify.service.push.NoopPushPort;
import wyq.pocket.money.notify.service.push.PushPort;

/**
 * 通知抽象层装配（M5 设计 §7.1 / GA D68）：
 *
 * <ul>
 *   <li>{@code pocket-money.notify.push.enabled=true}：装配 {@link HarmonyPushPort}
 *       （鸿蒙 Push Kit）；凭据缺失即启动失败（fail-fast，禁止硬编码兜底）；</li>
 *   <li>其余情况（默认）：装配 {@link NoopPushPort} 空实现，不产生外部投递。</li>
 * </ul>
 *
 * <p>业务代码只依赖 {@link PushPort}，通道实现零感知（镜像 M4 ChatPort）。
 */
@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyConfig {

    /**
     * 生产推送端口：push.enabled=true 时装配鸿蒙 Push Kit 适配器。
     *
     * @param properties 通知配置
     * @return PushPort Bean
     */
    @Bean
    @ConditionalOnMissingBean(PushPort.class)
    @ConditionalOnProperty(prefix = "pocket-money.notify.push", name = "enabled",
            havingValue = "true")
    public PushPort harmonyPushPort(NotifyProperties properties) {
        Harmony harmony = properties.push().harmony();
        requireHarmonyCredentials(harmony);
        return new HarmonyPushPort(harmony);
    }

    /**
     * 默认推送端口：未启用外部通道时装配空实现（不投递）。
     *
     * @return PushPort Bean
     */
    @Bean
    @ConditionalOnMissingBean(PushPort.class)
    public PushPort noopPushPort() {
        return new NoopPushPort();
    }

    /**
     * 校验鸿蒙凭据完整：任一关键项缺失即启动失败（fail-fast，禁止硬编码兜底）。
     *
     * @param harmony 鸿蒙 Push Kit 配置
     */
    private void requireHarmonyCredentials(Harmony harmony) {
        if (harmony == null || anyBlank(harmony.appId(), harmony.clientId(),
                harmony.clientSecret(), harmony.tokenUrl(), harmony.pushBaseUrl())) {
            throw new IllegalStateException("NOTIFY_PUSH_ENABLED=true 需要配置鸿蒙 Push Kit 凭据："
                    + "HARMONY_PUSH_APP_ID / HARMONY_PUSH_CLIENT_ID / HARMONY_PUSH_CLIENT_SECRET"
                    + "（端点 HARMONY_PUSH_TOKEN_URL / HARMONY_PUSH_BASE_URL 有官方默认值）");
        }
    }

    private boolean anyBlank(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
