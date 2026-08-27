package wyq.pocket.money.common.idempotency;

/**
 * 当前请求幂等键持有者（M3 设计 §5）。
 *
 * <p>由 {@link IdempotencyFilter} 在请求受理成功时写入，供资金写服务
 * 回填 {@code money_transaction.request_id}（账务级第二道防线）；
 * 请求结束（含异常）必须 {@link #clear()}，避免虚拟线程复用残留。
 */
public final class IdempotencyContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private IdempotencyContext() {
    }

    /**
     * 写入当前请求的幂等键。
     *
     * @param key 幂等键，可为 null（清空）
     */
    public static void set(String key) {
        CURRENT.set(key);
    }

    /**
     * 读取当前请求的幂等键。
     *
     * @return 幂等键；非幂等请求上下文中返回 null
     */
    public static String currentKey() {
        return CURRENT.get();
    }

    /**
     * 清理当前线程持有的幂等键。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
