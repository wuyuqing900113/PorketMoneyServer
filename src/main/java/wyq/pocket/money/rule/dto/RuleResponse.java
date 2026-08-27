package wyq.pocket.money.rule.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 包月规则响应。
 *
 * @param id                  规则 ID
 * @param beneficiaryUserId   受益人用户 ID
 * @param beneficiaryNickname 受益人昵称
 * @param ruleName            规则名称
 * @param amount              每月发放金额
 * @param grantDay            发放日
 * @param status              状态（ACTIVE / PAUSED / ARCHIVED）
 * @param startMonth          生效起始月
 * @param endMonth            失效月（可空）
 * @param remark              备注
 * @param createdBy           创建人用户 ID
 * @param createdAt           创建时间
 * @param grantedThisMonth    当月是否已发放（列表页标记）
 */
public record RuleResponse(Long id, Long beneficiaryUserId, String beneficiaryNickname,
                           String ruleName, BigDecimal amount, int grantDay, String status,
                           String startMonth, String endMonth, String remark, Long createdBy,
                           Instant createdAt, boolean grantedThisMonth) {
}
