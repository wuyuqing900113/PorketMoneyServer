package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

/**
 * 看板成员余额行（family_member LEFT JOIN money_account 查询结果）。
 *
 * @param userId   成员用户 ID
 * @param nickname 昵称
 * @param balance  余额（无账户记 0）
 */
public record MemberBalanceRow(Long userId, String nickname, BigDecimal balance) {
}
