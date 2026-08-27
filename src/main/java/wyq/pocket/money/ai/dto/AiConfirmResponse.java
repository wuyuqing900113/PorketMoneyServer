package wyq.pocket.money.ai.dto;

import java.math.BigDecimal;

/**
 * 待确认动作确认结果（M4 设计 §6.2）。
 *
 * @param transactionId 记账流水 ID
 * @param userId        目标账户持有人用户 ID
 * @param amount        记账金额
 * @param balanceAfter  记账后余额
 */
public record AiConfirmResponse(Long transactionId, Long userId, BigDecimal amount,
                                BigDecimal balanceAfter) {
}
