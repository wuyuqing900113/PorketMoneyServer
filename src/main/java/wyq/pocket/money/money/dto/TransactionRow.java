package wyq.pocket.money.money.dto;

import java.math.BigDecimal;
import java.time.Instant;

import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;

/**
 * 流水分页查询行（含用户 / 操作人昵称回显）。
 *
 * @param id               流水 ID
 * @param userId           账户持有人用户 ID
 * @param nickname         持有人昵称
 * @param direction        方向
 * @param bizType          业务类型
 * @param amount           金额（恒正）
 * @param balanceAfter     记账后余额
 * @param operatorNickname 操作人昵称（结算等无操作人为 null）
 * @param remark           备注
 * @param createdAt        记账时间
 */
public record TransactionRow(Long id, Long userId, String nickname, TxDirection direction,
                             TxBizType bizType, BigDecimal amount, BigDecimal balanceAfter,
                             String operatorNickname, String remark, Instant createdAt) {
}
