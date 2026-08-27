package wyq.pocket.money.money.dto;

import java.math.BigDecimal;
import java.time.Instant;

import wyq.pocket.money.money.domain.TxDirection;

/**
 * 趋势计算窗口内流水轻量行（仅方向 / 金额 / 时间）。
 *
 * @param direction 方向
 * @param amount    金额
 * @param createdAt 记账时间
 */
public record TxWindowRow(TxDirection direction, BigDecimal amount, Instant createdAt) {
}
