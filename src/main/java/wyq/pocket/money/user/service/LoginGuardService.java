package wyq.pocket.money.user.service;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.common.audit.SecurityLogger;
import wyq.pocket.money.user.domain.User;
import wyq.pocket.money.user.mapper.UserMapper;

/**
 * 登录锁定守护（M1 设计 §4.5，D7）。
 *
 * <p>计数落库（app_user.failed_attempts / locked_until，重启不丢失）：
 * 连续失败达到阈值 → 锁定并将计数归零重新累计；成功登录清零。
 * 锁定生效与失败计数均写审计，锁定另出 WARN 安全日志（§9.2）。
 */
@Component
public class LoginGuardService {

    private final UserMapper userMapper;

    private final AuditService auditService;

    private final LoginGuardProperties properties;

    private final Clock clock;

    /**
     * 生产构造：使用系统 UTC 时钟。
     *
     * <p>多构造器场景须显式标注注入点，Spring 不会自动推断。
     *
     * @param userMapper    用户 Mapper
     * @param auditService  审计服务
     * @param properties    锁定策略配置
     */
    @Autowired
    public LoginGuardService(UserMapper userMapper, AuditService auditService,
                             LoginGuardProperties properties) {
        this(userMapper, auditService, properties, Clock.systemUTC());
    }

    /**
     * 可测试构造：允许注入固定时钟。
     *
     * @param userMapper   用户 Mapper
     * @param auditService 审计服务
     * @param properties   锁定策略配置
     * @param clock        时钟
     */
    LoginGuardService(UserMapper userMapper, AuditService auditService,
                      LoginGuardProperties properties, Clock clock) {
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 账号当前是否处于锁定期。
     *
     * @param user 用户
     * @return 锁定中返回 true
     */
    public boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(clock.instant());
    }

    /**
     * 记录一次密码失败：累计计数，达到阈值则锁定（计数归零）。
     *
     * @param user 用户
     */
    public void recordFailure(User user) {
        int attempts = user.getFailedAttempts() + 1;
        if (attempts >= properties.maxAttempts()) {
            lock(user, attempts);
        } else {
            userMapper.updateLoginState(user.getId(), attempts, null);
        }
    }

    /**
     * 登录成功后清零计数与锁定标记。
     *
     * @param user 用户
     */
    public void resetAfterSuccess(User user) {
        userMapper.updateLoginState(user.getId(), 0, null);
    }

    private void lock(User user, int attempts) {
        userMapper.updateLoginState(user.getId(), 0, clock.instant().plus(properties.lockDuration()));
        SecurityLogger.warn("ACCOUNT_LOCKED user={} attempts={}", user.getId(), attempts);
        auditService.record(new AuditEntry(user.getId(), AuditAction.ACCOUNT_LOCKED,
                "USER", String.valueOf(user.getId()), null));
    }
}
