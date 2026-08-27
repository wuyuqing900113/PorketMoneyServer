package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

/**
 * 家庭账户累计汇总（money_account 聚合结果）。
 *
 * @param totalIncome  家庭累计收入
 * @param totalExpense 家庭累计支出
 */
public record AccountTotals(BigDecimal totalIncome, BigDecimal totalExpense) {
}
