package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 手动存入请求（M2 设计 §8.2 #5）。
 *
 * @param targetUserId 目标账户持有人
 * @param amount       金额（0.01–9999999999.99）
 * @param remark       备注（可空）
 */
public record DepositRequest(
        @NotNull Long targetUserId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(max = 128) String remark) {
}
