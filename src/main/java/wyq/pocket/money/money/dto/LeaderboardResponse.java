package wyq.pocket.money.money.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 本周收入榜响应（M2 设计 §8.2 #4）。
 *
 * @param weekStartDate 榜单周起始日（周一）
 * @param entries       榜单条目（名次升序，构造后不可变）
 */
public record LeaderboardResponse(LocalDate weekStartDate, List<LeaderboardEntry> entries) {

    /**
     * 紧凑构造器：榜单条目做不可变快照，杜绝内外双向的可变共享。
     */
    public LeaderboardResponse {
        entries = List.copyOf(entries);
    }
}
