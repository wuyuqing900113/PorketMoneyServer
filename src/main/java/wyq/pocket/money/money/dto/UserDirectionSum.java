package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

import wyq.pocket.money.money.domain.TxDirection;

/**
 * 按用户 + 方向聚合的流水金额合计（报表成员行）。
 *
 * @param userId    用户 ID
 * @param direction 方向
 * @param total     金额合计
 */
public record UserDirectionSum(Long userId, TxDirection direction, BigDecimal total) {
}
