package wyq.pocket.money.finance.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 月度收支报表响应（M2 设计 §8.2 #23，同步聚合，D7）。
 *
 * @param month          报表月份（YYYY-MM）
 * @param totalIncome    当月收入合计
 * @param totalExpense   当月支出合计
 * @param net            净额（收入 − 支出）
 * @param incomeByType   收入按业务类型拆分（TxBizType 名 → 金额，构造后不可变）
 * @param expenseByType  支出按业务类型拆分（TxBizType 名 → 金额，构造后不可变）
 * @param members        成员收支行（仅含当月有流水的成员，构造后不可变）
 */
public record IncomeExpenseReportResponse(String month, BigDecimal totalIncome,
                                          BigDecimal totalExpense, BigDecimal net,
                                          Map<String, BigDecimal> incomeByType,
                                          Map<String, BigDecimal> expenseByType,
                                          List<MemberReportRow> members) {

    /**
     * 紧凑构造器：拆分映射与成员行做不可变快照，杜绝内外双向的可变共享。
     */
    public IncomeExpenseReportResponse {
        incomeByType = Map.copyOf(incomeByType);
        expenseByType = Map.copyOf(expenseByType);
        members = List.copyOf(members);
    }
}
