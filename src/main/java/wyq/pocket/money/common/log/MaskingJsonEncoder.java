package wyq.pocket.money.common.log;

import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.encoder.LogstashEncoder;

/**
 * JSON 日志脱敏编码器：在标准 Logstash JSON 输出基础上，
 * 对编码结果整体执行 {@link MaskingRules} 脱敏（覆盖 message 与 MDC 值）。
 *
 * <p>输出字段含 @timestamp、level、logger_name、thread_name、message、
 * MDC（traceId 等）、stack_trace，为 M7 阿里云 SLS 接入打底。
 *
 * <p>logback-spring.xml 中使用：
 * {@code <encoder class="wyq.pocket.money.common.log.MaskingJsonEncoder"/>}
 */
public class MaskingJsonEncoder extends LogstashEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] raw = super.encode(event);
        if (raw == null) {
            return new byte[0];
        }
        String masked = maskJson(new String(raw, StandardCharsets.UTF_8));
        return masked.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 对 JSON 日志文本执行脱敏，独立可测。
     *
     * @param rawJson 原始 JSON 日志行
     * @return 脱敏后的 JSON 日志行
     */
    String maskJson(String rawJson) {
        return MaskingRules.mask(rawJson);
    }
}
