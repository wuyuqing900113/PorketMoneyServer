package wyq.pocket.money.ai.domain;

import java.math.BigDecimal;

/**
 * 资金写参数快照（M4 设计 §6.2）：经业务解析后的确定参数，落
 * {@code ai_pending_action.params_json} 供确认执行时原样重放。
 *
 * @param targetUserId   目标账户持有人用户 ID
 * @param targetNickname 目标成员昵称（确认话术展示用）
 * @param amount         金额（已校验为正数）
 * @param remark         备注（可空）
 */
public record FundActionParams(long targetUserId, String targetNickname, BigDecimal amount,
                               String remark) {
}
