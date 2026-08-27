package wyq.pocket.money.money.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 工作价值记录响应。
 *
 * @param id              记录 ID
 * @param parentUserId    收款人（父母本人）用户 ID
 * @param nickname        收款人昵称
 * @param workMonth       工作月份（YYYY-MM）
 * @param salaryIncome    当月工资收入
 * @param allowanceAmount 发放入账金额
 * @param workSummary     工作内容摘要
 * @param transactionId   入账流水 ID
 * @param createdAt       创建时间
 */
public record WorkValueResponse(Long id, Long parentUserId, String nickname, String workMonth,
                                BigDecimal salaryIncome, BigDecimal allowanceAmount,
                                String workSummary, Long transactionId, Instant createdAt) {
}
