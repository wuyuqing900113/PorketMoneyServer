package wyq.pocket.money.user.dto;

/**
 * 注册成功响应（M1 设计 §10.3 示例）：注册不自动签发令牌，
 * 端上随后走登录流程。
 *
 * @param userId   用户 ID
 * @param familyId 自动创建的家庭 ID
 * @param nickname 昵称
 * @param role     角色（注册固定为 PARENT）
 */
public record RegisterResponse(long userId, long familyId, String nickname, String role) {
}
