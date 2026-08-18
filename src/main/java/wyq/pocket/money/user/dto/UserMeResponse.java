package wyq.pocket.money.user.dto;

/**
 * 当前用户信息响应（M1 设计 §5.5）。
 *
 * <p>{@code maskedPhone} 为脱敏手机号（如 {@code 138****1234}），
 * 解密结果不出 service 层；孩子无手机号，该字段为 null。
 *
 * @param userId      用户 ID
 * @param nickname    昵称
 * @param role        角色
 * @param familyId    所属家庭 ID
 * @param maskedPhone 脱敏手机号（仅家长）
 */
public record UserMeResponse(long userId, String nickname, String role, long familyId,
                             String maskedPhone) {
}
