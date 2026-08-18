package wyq.pocket.money.user.dto;

/**
 * 家庭成员摘要（M1 设计 §6.4 成员列表只读项）。
 *
 * <p>不含手机号 / 邮箱等敏感字段（孩子账号本身无此二者，COPPA 类合规）；
 * 由 family_member 联 app_user 查询直接经构造器映射。
 *
 * @param userId   成员用户 ID
 * @param nickname 成员昵称
 * @param role     成员角色：PARENT / CHILD
 */
public record MemberSummary(long userId, String nickname, String role) {
}
