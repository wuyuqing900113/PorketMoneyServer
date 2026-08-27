package wyq.pocket.money.common.ai;

/**
 * 语音合成端口（预留，M4 设计 D28）。
 *
 * <p>M4 语音链路以文本输入桩驱动（channel=TEXT），本端口仅契约预留。
 */
public interface TextToSpeechPort {

    /**
     * 将文本合成为音频字节流。
     *
     * @param text 待合成文本
     * @return 音频字节（仅内存）
     */
    byte[] synthesize(String text);
}
