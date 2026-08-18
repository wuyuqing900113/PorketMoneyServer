package wyq.pocket.money.user.dto;

/**
 * 登录成功响应（M1 设计 §5.2）：令牌对 + 首次改密标志 + 用户摘要。
 *
 * @param accessToken        access 令牌（短 TTL）
 * @param refreshToken       refresh 令牌（长 TTL，轮转使用）
 * @param expiresIn          access 有效期（秒）
 * @param mustChangePassword 是否须先修改初始密码（孩子首次登录为 true）
 * @param user               用户摘要
 */
public record LoginResponse(String accessToken, String refreshToken, long expiresIn,
                            boolean mustChangePassword, UserSummary user) {

    /**
     * 用户摘要。
     *
     * @param userId   用户 ID
     * @param nickname 昵称
     * @param role     角色
     * @param familyId 所属家庭 ID
     */
    public record UserSummary(long userId, String nickname, String role, long familyId) {
    }
}
