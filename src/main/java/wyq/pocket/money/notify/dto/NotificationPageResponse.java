package wyq.pocket.money.notify.dto;

import java.util.List;

/**
 * 通知分页响应（M5 设计 §5.4）。
 *
 * @param records 通知列表（不可变快照）
 * @param total   总条数
 * @param page    当前页码
 * @param size    页大小
 */
public record NotificationPageResponse(List<NotificationItemResponse> records, long total,
                                       int page, int size) {

    /**
     * 紧凑构造器：列表做不可变快照。
     */
    public NotificationPageResponse {
        records = List.copyOf(records);
    }
}
