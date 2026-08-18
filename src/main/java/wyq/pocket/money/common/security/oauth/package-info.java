/**
 * OAuth2 第三方登录扩展点（M1 设计 §4.7：M1 仅预留，不实现任何 provider）。
 *
 * <p>本包当前仅含 {@link wyq.pocket.money.common.security.oauth.AuthProvider}
 * 抽象；表结构预留见 V3 迁移脚本 {@code user_oauth_binding}
 * （唯一约束 (provider, external_id)）。
 *
 * <p>演进方式：
 * <ol>
 *   <li>新增 provider 实现（如华为账号）并注册为 Bean；受 ArchUnit 分层
 *       约束（common 不得反向依赖业务模块），实现置于 user 模块或独立模块；</li>
 *   <li>新增 {@code POST /api/v1/auth/oauth/{provider}} 端点，按
 *       {@code supports} 分发，认证成功后走既有令牌签发链路；</li>
 *   <li>现有密码认证链路零改动。</li>
 * </ol>
 */
package wyq.pocket.money.common.security.oauth;
