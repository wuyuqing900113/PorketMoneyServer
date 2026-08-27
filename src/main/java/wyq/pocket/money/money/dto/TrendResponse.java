package wyq.pocket.money.money.dto;

import java.util.List;

/**
 * 趋势响应（M2 设计 §8.2 #3）。
 *
 * @param granularity 粒度（DAY / WEEK）
 * @param scope       范围（FAMILY / USER）
 * @param userId      个人范围时的用户 ID（家庭范围为 null）
 * @param series      数据点序列（时间升序，构造后不可变）
 */
public record TrendResponse(String granularity, String scope, Long userId,
                            List<TrendPoint> series) {

    /**
     * 紧凑构造器：数据点序列做不可变快照，杜绝内外双向的可变共享。
     */
    public TrendResponse {
        series = List.copyOf(series);
    }
}
