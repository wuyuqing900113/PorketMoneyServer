package wyq.pocket.money.notify.dto;

/**
 * 未读通知数响应（M5 设计 §5.4）。
 *
 * @param count 未读数
 */
public record UnreadCountResponse(long count) {
}
