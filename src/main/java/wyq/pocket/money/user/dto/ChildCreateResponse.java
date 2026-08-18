package wyq.pocket.money.user.dto;

/**
 * 创建孩子账号成功响应（M1 设计 §6.3）。
 *
 * @param userId             孩子用户 ID
 * @param username           登录名
 * @param nickname           昵称
 * @param role               角色（CHILD）
 * @param mustChangePassword 首次改密强制位（创建即 true）
 */
public record ChildCreateResponse(long userId, String username, String nickname, String role,
        boolean mustChangePassword) {
}
