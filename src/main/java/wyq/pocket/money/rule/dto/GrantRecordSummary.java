package wyq.pocket.money.rule.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 发放记录摘要（规则详情页近 12 个月）。
 *
 * @param grantMonth    发放月份（YYYY-MM）
 * @param amount        发放金额（记账时点快照）
 * @param transactionId 发放流水 ID
 * @param grantedAt     发放时间
 */
public record GrantRecordSummary(String grantMonth, BigDecimal amount,
                                 Long transactionId, Instant grantedAt) {
}
