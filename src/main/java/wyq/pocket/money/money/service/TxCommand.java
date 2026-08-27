package wyq.pocket.money.money.service;

import java.math.BigDecimal;

import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.domain.TxRefType;

/**
 * 记账原语命令（M2 设计 §4.3）。
 *
 * @param familyId       家庭 ID
 * @param userId         账户持有人用户 ID
 * @param direction      方向
 * @param bizType        业务类型
 * @param amount         金额（恒正）
 * @param refType        关联单据类型（可空）
 * @param refId          关联单据 ID（可空）
 * @param operatorUserId 操作人用户 ID（定时任务为 null）
 * @param remark         备注（可空）
 * @param requestId      幂等键（M2 预留 null，M3 启用）
 */
public record TxCommand(long familyId, long userId, TxDirection direction, TxBizType bizType,
                        BigDecimal amount, TxRefType refType, Long refId, Long operatorUserId,
                        String remark, String requestId) {
}
