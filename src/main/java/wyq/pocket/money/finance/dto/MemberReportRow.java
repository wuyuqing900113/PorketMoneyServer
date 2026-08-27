package wyq.pocket.money.finance.dto;

import java.math.BigDecimal;

/**
 * 收支报表成员行。
 *
 * @param userId   用户 ID
 * @param nickname 昵称
 * @param income   当月收入合计
 * @param expense  当月支出合计
 * @param net      净额（收入 − 支出）
 */
public record MemberReportRow(Long userId, String nickname, BigDecimal income,
                              BigDecimal expense, BigDecimal net) {
}
