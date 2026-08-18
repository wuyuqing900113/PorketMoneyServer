package wyq.pocket.money.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全日志统一出口（M1 设计 §9.2）。
 *
 * <p>所有安全事件（认证失败、锁定、令牌重用、越权等）经独立
 * {@code SECURITY} logger 输出 WARN/ERROR 级别日志；
 * M6/M7 由 SLS 按 logger 名 + 级别配置告警规则。
 */
public final class SecurityLogger {

    /** 安全日志 logger 名称（SLS 告警规则的订阅键）。 */
    public static final String LOGGER_NAME = "SECURITY";

    private static final Logger SECURITY = LoggerFactory.getLogger(LOGGER_NAME);

    private SecurityLogger() {
    }

    /**
     * 输出 WARN 级安全日志。
     *
     * @param format    SLF4J 占位符格式
     * @param arguments 占位符参数
     */
    public static void warn(String format, Object... arguments) {
        SECURITY.warn(format, arguments);
    }

    /**
     * 输出 ERROR 级安全日志（疑似安全事件，须告警）。
     *
     * @param format    SLF4J 占位符格式
     * @param arguments 占位符参数
     */
    public static void error(String format, Object... arguments) {
        SECURITY.error(format, arguments);
    }
}
