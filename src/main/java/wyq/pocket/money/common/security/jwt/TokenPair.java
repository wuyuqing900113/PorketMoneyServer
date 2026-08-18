package wyq.pocket.money.common.security.jwt;

/**
 * 登录 / 刷新结果的令牌对（M1 设计 §4.3）。
 *
 * @param accessToken  access 令牌（短 TTL，请求鉴权）
 * @param refreshToken refresh 令牌（长 TTL，仅用于换新，服务端持久化可吊销）
 * @param expiresIn    access 令牌有效期（秒）
 */
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
}
