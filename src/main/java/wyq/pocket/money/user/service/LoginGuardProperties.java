package wyq.pocket.money.user.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录锁定策略配置（M1 设计 §4.5 / §11）。
 *
 * <p>环境变量 {@code LOGIN_MAX_ATTEMPTS} / {@code LOGIN_LOCK_DURATION}
 * 覆盖，默认 5 次 / PT15M。
 *
 * @param maxAttempts  连续失败锁定阈值
 * @param lockDuration 锁定时长
 */
@ConfigurationProperties(prefix = "pocket-money.security.login-guard")
public record LoginGuardProperties(int maxAttempts, Duration lockDuration) {
}
