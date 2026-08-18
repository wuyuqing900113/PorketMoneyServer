package wyq.pocket.money.common.security.oauth;

/**
 * OAuth2 第三方登录提供者扩展点（M1 设计 §4.7：仅预留，不实现）。
 *
 * <p>契约：{@link #supports(String)} 判定本实现是否处理指定提供商标识；
 * {@link #authenticate(String)} 将外部凭据提交对应提供商核验，经
 * {@code user_oauth_binding} 表（唯一约束 (provider, external_id)，V3 建表）
 * 解析绑定并返回本地用户 ID。核验失败或绑定缺失时由实现抛携带统一
 * 错误码的 BusinessException（错误码规划随首个 provider 接入一并落地）。
 *
 * <p>未来接入：新增实现注册为 Bean + 新增
 * {@code POST /api/v1/auth/oauth/{provider}} 端点即可，
 * 现有密码认证链路零改动（§4.7）。ArchUnit 分层约束：common 不得反向
 * 依赖业务模块，实现类应置于 user 模块或独立模块。
 */
public interface AuthProvider {

    /**
     * 判定本实现是否处理指定提供商标识。
     *
     * @param provider 提供商标识（如 huawei-account）
     * @return 本实现处理返回 true
     */
    boolean supports(String provider);

    /**
     * 以外部凭据认证并返回绑定的本地用户 ID。
     *
     * @param externalCredential 提供商签发的不透明凭据（授权码 / ID token 等）
     * @return 该凭据绑定的本地用户 ID
     */
    long authenticate(String externalCredential);
}
