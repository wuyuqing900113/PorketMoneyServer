package wyq.pocket.money.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import wyq.pocket.money.user.service.LoginGuardProperties;

/**
 * user 模块配置装配。
 *
 * <p>登录锁定策略属性在此启用（common 层 SecurityConfig 不可反向依赖
 * user 模块，ArchUnit 约束）。
 */
@Configuration
@EnableConfigurationProperties(LoginGuardProperties.class)
public class UserModuleConfig {
}
