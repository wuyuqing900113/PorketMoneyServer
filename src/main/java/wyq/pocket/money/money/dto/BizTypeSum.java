package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;

/**
 * 按业务类型 + 方向聚合的流水金额合计（收支报表）。
 *
 * @param bizType   业务类型
 * @param direction 方向
 * @param total     金额合计
 */
public record BizTypeSum(TxBizType bizType, TxDirection direction, BigDecimal total) {
}
