package wyq.pocket.money.notify.dto;

import java.time.Instant;

import wyq.pocket.money.notify.domain.Notification;

/**
 * 通知列表项（M5 设计 §5.4）。
 *
 * @param id         通知 ID
 * @param type       通知类型（TX_IN / TX_OUT / LOW_BALANCE / RULE_EXPIRED）
 * @param title      标题
 * @param content    正文
 * @param bizRefType 业务锚点类型（MONEY_TRANSACTION / MONEY_RULE，可空）
 * @param bizRefId   业务锚点 ID（可空）
 * @param readAt     已读时间（null = 未读）
 * @param createdAt  创建时间
 */
public record NotificationItemResponse(Long id, String type, String title, String content,
                                       String bizRefType, Long bizRefId, Instant readAt,
                                       Instant createdAt) {

    /**
     * 由领域对象构造响应项。
     *
     * @param notification 通知领域对象
     * @return 响应项
     */
    public static NotificationItemResponse from(Notification notification) {
        return new NotificationItemResponse(notification.getId(), notification.getType(),
                notification.getTitle(), notification.getContent(), notification.getBizRefType(),
                notification.getBizRefId(), notification.getReadAt(), notification.getCreatedAt());
    }
}
