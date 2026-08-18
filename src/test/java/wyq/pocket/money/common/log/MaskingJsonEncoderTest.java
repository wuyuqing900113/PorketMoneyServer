package wyq.pocket.money.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

/**
 * MaskingJsonEncoder 单元测试。
 */
class MaskingJsonEncoderTest {

    @Test
    void maskJsonShouldMaskSensitiveContent() {
        MaskingJsonEncoder encoder = new MaskingJsonEncoder();
        String raw = "{\"message\":\"联系电话 13812345678\",\"traceId\":\"a1b2c3d4e5f60718\"}";

        String masked = encoder.maskJson(raw);

        assertThat(masked).contains("138****5678").doesNotContain("13812345678");
    }

    @Test
    void encodeShouldOutputMaskedJson() {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger(MaskingJsonEncoderTest.class);
        MaskingJsonEncoder encoder = new MaskingJsonEncoder();
        encoder.setContext(context);
        encoder.start();
        LoggingEvent event = new LoggingEvent(
                MaskingJsonEncoderTest.class.getName(), logger, Level.INFO,
                "身份证 110101199001011234", null, null);
        // 直接注入 MDC 数据（贴近 TraceIdFilter 真实行为），
        // 避免单测环境下 LoggingEvent 惰性初始化 MDC 适配器为空的问题
        event.setMDCPropertyMap(Map.of("traceId", "a1b2c3d4e5f60718"));

        String output = new String(encoder.encode(event), StandardCharsets.UTF_8);
        encoder.stop();

        assertThat(output)
                .contains("110***********1234")
                .doesNotContain("110101199001011234")
                .contains("message")
                .contains("a1b2c3d4e5f60718");
    }
}
