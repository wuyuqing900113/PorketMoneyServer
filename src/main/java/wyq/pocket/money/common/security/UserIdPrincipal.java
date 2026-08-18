package wyq.pocket.money.common.security;

/**
 * SecurityContext 认证主体：JWT access 令牌声明投影（M1 设计 §4.3）。
 *
 * <p>承载用户 / 家庭 / 角色 / 首次改密四元组；{@link #toString()} 输出
 * userId，作为 {@code Authentication#getName()} 的统一身份，供审计落库
 * 与安全日志引用。
 *
 * @param userId             用户 ID（JWT sub）
 * @param familyId           所属家庭 ID（JWT fam，数据级归属校验快路径）
 * @param role               角色：PARENT / CHILD（映射 ROLE_* 授权）
 * @param mustChangePassword 须先修改初始密码（JWT mcp，设计 §4.6）
 */
public record UserIdPrincipal(long userId, long familyId, String role, boolean mustChangePassword) {

    /**
     * 身份表示：userId 数字串。
     *
     * <p>{@code AbstractAuthenticationToken#getName()} 对非 UserDetails
     * 主体回退为 principal.toString()，审计与安全日志统一取该值。
     *
     * @return userId 字符串形式
     */
    @Override
    public String toString() {
        return String.valueOf(userId);
    }
}
