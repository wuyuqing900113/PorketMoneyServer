package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

/**
 * 收入榜条目（dense ranking：同收入同名次，不跳号）。
 *
 * @param rank        名次（从 1 起）
 * @param userId      用户 ID
 * @param nickname    昵称
 * @param totalIncome 本周收入合计
 */
public record LeaderboardEntry(int rank, Long userId, String nickname, BigDecimal totalIncome) {
}
