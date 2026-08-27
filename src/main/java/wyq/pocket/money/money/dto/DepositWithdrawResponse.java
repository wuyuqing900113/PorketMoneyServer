package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

/**
 * 存取记账结果。
 *
 * @param transactionId 流水 ID
 * @param userId        账户持有人用户 ID
 * @param amount        金额
 * @param balanceAfter  记账后余额
 */
public record DepositWithdrawResponse(Long transactionId, Long userId, BigDecimal amount,
                                      BigDecimal balanceAfter) {
}
