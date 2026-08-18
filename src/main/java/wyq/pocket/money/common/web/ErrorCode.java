package wyq.pocket.money.common.web;

/**
 * 错误码统一接口，各模块错误码枚举均须实现该接口。
 *
 * <p>错误码为 6 位数字分段码：前 2 位为模块段，后 4 位为段内序号；
 * {@code 0} 恒为成功。段位划分见 M0-detailed-design.md §6.2。
 */
public interface ErrorCode {

    /** 系统段错误码的段位值（90xxxx 的前两位）。 */
    int SYSTEM_SEGMENT = 90;

    /** 系统段段位的进制基数（6 位码取前 2 位）。 */
    int SEGMENT_BASE = 10000;

    /**
     * 获取 6 位数字错误码，0 表示成功。
     *
     * @return 错误码
     */
    int getCode();

    /**
     * 获取面向客户端的默认提示信息（可直接展示给用户）。
     *
     * @return 默认提示信息
     */
    String getMessage();

    /**
     * 判断该错误码是否允许客户端重试。
     *
     * <p>约定：90xxxx 系统段错误可重试（客户端须携带幂等键、指数退避），
     * 其余段位默认不可重试；如需段内例外，在错误码定义处显式标注。
     *
     * @return 可重试返回 true
     */
    default boolean isRetryable() {
        return getCode() / SEGMENT_BASE == SYSTEM_SEGMENT;
    }
}
