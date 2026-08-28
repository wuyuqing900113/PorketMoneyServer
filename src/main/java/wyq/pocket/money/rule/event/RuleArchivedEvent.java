package wyq.pocket.money.rule.event;

/**
 * 规则到期归档领域事件（M5 设计 §4.2）：由 RuleExpiryJob 到期归档时发布。
 *
 * <p>监听方：通知模块（规则到期提醒家长）。ruleName / endMonth 随事件携带，
 * 监听方免回查规则 mapper。
 *
 * @param familyId          家庭 ID
 * @param beneficiaryUserId 受益人用户 ID
 * @param ruleId            规则 ID
 * @param ruleName          规则名（文案用）
 * @param endMonth          结束月份（YYYY-MM）
 */
public record RuleArchivedEvent(long familyId, long beneficiaryUserId,
        long ruleId, String ruleName, String endMonth) {
}
