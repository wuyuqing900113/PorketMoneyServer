package wyq.pocket.money.finance.dto;

import java.math.BigDecimal;

/**
 * 统计摘要响应（M2 设计 §8.2 #24）。
 *
 * @param totalBalance        家庭总余额
 * @param allTimeIncome       历史累计收入
 * @param allTimeExpense      历史累计支出
 * @param currentMonthIncome  当月收入
 * @param currentMonthExpense 当月支出
 * @param memberCount         在册成员数
 */
public record StatisticsSummaryResponse(BigDecimal totalBalance, BigDecimal allTimeIncome,
                                        BigDecimal allTimeExpense,
                                        BigDecimal currentMonthIncome,
                                        BigDecimal currentMonthExpense, int memberCount) {
}
