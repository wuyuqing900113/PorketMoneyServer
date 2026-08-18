package wyq.pocket.money.user.dto;

/**
 * 刷新成功响应（M1 设计 §4.4）：轮转后的新令牌对。
 *
 * <p>{@code mustChangePassword} 与新 access 令牌的 mcp 声明一致，
 * 端上无需解码 JWT 即可决定是否引导改密。
 *
 * @param accessToken        新 access 令牌
 * @param refreshToken       新 refresh 令牌（旧令牌已吊销）
 * @param expiresIn          access 有效期（秒）
 * @param mustChangePassword 是否须先修改初始密码
 */
public record TokenPairResponse(String accessToken, String refreshToken, long expiresIn,
                                boolean mustChangePassword) {
}
