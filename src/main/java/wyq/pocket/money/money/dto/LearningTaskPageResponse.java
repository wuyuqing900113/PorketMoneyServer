package wyq.pocket.money.money.dto;

import java.util.List;

/**
 * 学习任务分页响应。
 *
 * @param records 任务列表（构造后不可变）
 * @param total   总条数
 * @param page    当前页码
 * @param size    页大小
 */
public record LearningTaskPageResponse(List<LearningTaskResponse> records, long total,
                                       int page, int size) {

    /**
     * 紧凑构造器：任务列表做不可变快照，杜绝内外双向的可变共享。
     */
    public LearningTaskPageResponse {
        records = List.copyOf(records);
    }
}
