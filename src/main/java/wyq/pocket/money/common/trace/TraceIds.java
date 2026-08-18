package wyq.pocket.money.common.trace;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * TraceId 工具：生成、校验与读取链路追踪 ID。
 *
 * <p>TraceId 为 8 字节随机数的十六进制表示（16 字符），
 * 写入 MDC 后由日志与统一响应体携带，是 M4 "AI 操作执行路径可追溯" 的底层支撑。
 */
public final class TraceIds {

    private static final int TRACE_ID_BYTES = 8;

    /** 外部传入 TraceId 的白名单：安全字符集 + 长度上限，杜绝 CR/LF 等响应头注入字符。 */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceIds() {
    }

    /**
     * 校验外部传入的 TraceId 是否可安全沿用。
     *
     * <p>仅放行白名单字符集（字母、数字、下划线、短横线）且长度 1~64；
     * 含 CR/LF/空白等字符的输入一律拒绝，由调用方重新生成。
     *
     * @param candidate 外部传入的候选 TraceId（可为 null）
     * @return 合法返回 true
     */
    public static boolean isAcceptable(String candidate) {
        return candidate != null && SAFE_TRACE_ID.matcher(candidate).matches();
    }

    /**
     * 生成新的 TraceId。
     *
     * @return 16 字符十六进制 ID
     */
    public static String generate() {
        byte[] bytes = new byte[TRACE_ID_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 读取当前请求上下文的 TraceId。
     *
     * @return 当前 TraceId；不在请求上下文中时返回 null
     */
    public static String current() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }
}
