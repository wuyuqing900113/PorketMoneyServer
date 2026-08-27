package wyq.pocket.money.common.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 抽象层装配（M4 设计 §4.2）：默认注册确定性 {@link StubChatPort}。
 *
 * <p>{@link EmbeddingPort} / {@link SpeechToTextPort} / {@link TextToSpeechPort}
 * 仅契约预留，不注册 Bean；真实提供商接入后装配对应实现。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * 默认对话端口：无自定义 {@link ChatPort} 时装配确定性桩。
     *
     * @param properties AI 配置（桩 fail 开关）
     * @return ChatPort Bean
     */
    @Bean
    @ConditionalOnMissingBean(ChatPort.class)
    public ChatPort chatPort(AiProperties properties) {
        return new StubChatPort(properties.stub().fail());
    }
}
