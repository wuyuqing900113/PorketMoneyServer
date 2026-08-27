package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

import wyq.pocket.money.money.domain.TxDirection;

/**
 * 按方向聚合的流水金额合计。
 *
 * @param direction 方向
 * @param total     金额合计
 */
public record DirectionSum(TxDirection direction, BigDecimal total) {
}
