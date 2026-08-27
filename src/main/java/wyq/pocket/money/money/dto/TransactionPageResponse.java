package wyq.pocket.money.money.dto;

import java.util.List;

/**
 * 流水分页响应。
 *
 * @param records 流水行（构造后不可变）
 * @param total   总条数
 * @param page    当前页码
 * @param size    页大小
 */
public record TransactionPageResponse(List<TransactionRow> records, long total,
                                      int page, int size) {

    /**
     * 紧凑构造器：流水行做不可变快照，杜绝内外双向的可变共享。
     */
    public TransactionPageResponse {
        records = List.copyOf(records);
    }
}
