package wyq.pocket.money.money.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 趋势单桶数据点。
 *
 * @param period        桶标签（日 = 日期，周 = 周一日期，ISO 格式）
 * @param startDate     桶起始日
 * @param endDate       桶截止日
 * @param endingBalance 桶期末余额（自窗口起点推演）
 * @param income        桶内收入合计
 * @param expense       桶内支出合计
 */
public record TrendPoint(String period, LocalDate startDate, LocalDate endDate,
                         BigDecimal endingBalance, BigDecimal income, BigDecimal expense) {
}
