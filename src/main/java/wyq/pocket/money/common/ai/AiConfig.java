package wyq.pocket.money.common.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * AI 抽象层装配（M4 设计 §4.2 / GA D67）：按 {@code pocket-money.ai.mock} 选择
 * 对话端口实现。
 *
 * <ul>
 *   <li>{@code mock=true}（默认）：确定性 {@link StubChatPort}，零外部依赖，
 *       支撑单测 / 集成测试 / 评测集；</li>
 *   <li>{@code mock=false}：{@link SpringAiChatPort}（DeepSeek，经 Spring AI
 *       OpenAI 兼容协议）；需同时启用 {@code spring.ai.model.chat=openai} 并
 *       配置 {@code DEEPSEEK_API_KEY}，ChatModel 缺失即启动失败（fail-fast）。</li>
 * </ul>
 *
 * <p>{@link EmbeddingPort} / {@link SpeechToTextPort} / {@link TextToSpeechPort}
 * 仅契约预留，不注册 Bean；真实提供商接入后装配对应实现。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * 默认对话端口：mock 模式（缺省）装配确定性桩。
     *
     * @param properties AI 配置（桩 fail 开关）
     * @return ChatPort Bean
     */
    @Bean
    @ConditionalOnMissingBean(ChatPort.class)
    @ConditionalOnProperty(prefix = "pocket-money.ai", name = "mock",
            havingValue = "true", matchIfMissing = true)
    public ChatPort stubChatPort(AiProperties properties) {
        return new StubChatPort(properties.stub().fail());
    }

    /**
     * 生产对话端口：mock=false 时装配 DeepSeek 适配器。
     *
     * @param chatModelProvider Spring AI 对话模型（spring.ai.model.chat=openai 时由自动配置提供）
     * @param jsonMapper        JSON 序列化
     * @return ChatPort Bean
     */
    @Bean
    @ConditionalOnMissingBean(ChatPort.class)
    @ConditionalOnProperty(prefix = "pocket-money.ai", name = "mock", havingValue = "false")
    public ChatPort springAiChatPort(ObjectProvider<ChatModel> chatModelProvider,
                                     JsonMapper jsonMapper) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new IllegalStateException("AI_MOCK=false 需要 Spring AI ChatModel：请设置 "
                    + "SPRING_AI_MODEL_CHAT=openai 并配置 DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL");
        }
        return new SpringAiChatPort(chatModel, jsonMapper);
    }
}
