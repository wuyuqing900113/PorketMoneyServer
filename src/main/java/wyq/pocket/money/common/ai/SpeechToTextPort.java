package wyq.pocket.money.common.ai;

/**
 * 语音识别端口（预留，M4 设计 D28）。
 *
 * <p>隐私约束（mission 可信度约束 5）：音频数据仅驻留内存完成转写，
 * 不落盘、不写入任何表或文件；实现方须保证字节数组不持久化、不缓存。
 */
public interface SpeechToTextPort {

    /**
     * 将音频字节流转写为文本。
     *
     * @param audio 音频字节（仅内存，不落盘）
     * @return 转写文本
     */
    String transcribe(byte[] audio);
}
