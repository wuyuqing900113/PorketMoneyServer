package wyq.pocket.money.user.event;

/**
 * 成员移除领域事件（M2 设计 §7.4）：由 FamilyService.removeMember 在事务内发布。
 *
 * <p>监听方：零花钱模块（冻结账户 + 取消未发放任务）、
 * 规则模块（暂停该受益人的 ACTIVE 规则）。
 * 监听器内部自行捕获异常，不回滚移除主流程。
 *
 * @param familyId 家庭 ID
 * @param userId   被移除成员用户 ID
 */
public record MemberRemovedEvent(long familyId, long userId) {
}
