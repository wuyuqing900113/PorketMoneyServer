package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

/**
 * 按用户聚合的收入合计（本周收入榜）。
 *
 * @param userId 用户 ID
 * @param total  收入合计
 */
public record UserSum(Long userId, BigDecimal total) {
}
