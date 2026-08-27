package wyq.pocket.money.money.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 看板响应（M2 设计 §8.2 #1）。
 *
 * @param totalBalance 家庭总余额
 * @param weekIncome   本周收入（周一起）
 * @param weekExpense  本周支出
 * @param monthIncome  本月收入
 * @param monthExpense 本月支出
 * @param members      全员余额行（构造后不可变）
 */
public record DashboardResponse(BigDecimal totalBalance, BigDecimal weekIncome,
                                BigDecimal weekExpense, BigDecimal monthIncome,
                                BigDecimal monthExpense, List<MemberBalanceRow> members) {

    /**
     * 紧凑构造器：成员余额行做不可变快照，杜绝内外双向的可变共享。
     */
    public DashboardResponse {
        members = List.copyOf(members);
    }
}
