# 认证与加密实现审查清单（M6）

| 项 | 值 |
|---|---|
| 里程碑 | M6（测试加固与安全） |
| 依据 | `M6-detailed-design.md` §9（D52） |
| 日期 | 2026-08-28 |
| 结论 | **无高危项**；所有审查要点有源码佐证，详见下表 |

---

## 1. 审查清单

| 项 | 审查要点 | 现状依据（file:line） | 结论 |
|---|---|---|---|
| JWT | HS256 密钥经 `JWT_SECRET` 环境变量注入、无硬编码；签名算法固定不随 `alg` 头切换；密钥长度 fail-fast | `JwtTokenService.java:55`（`HmacSHA256`）、`:88-89`（encoder/decoder 固定 `MacAlgorithm.HS256`）、`:187`（密钥 <32 字节抛错）；`JwtProperties.java:14` | ✅ |
| JWT TTL | access 15m / refresh 14d | `application.yml:66-67`（`JWT_ACCESS_TTL:PT15M`、`JWT_REFRESH_TTL:P14D`） | ✅ |
| 密码 | BCrypt strength=10；登录密码不落明文；`mustChangePassword` 首登强制改密 | `SecurityConfig.java:49,55`（`BCryptPasswordEncoder(BCRYPT_STRENGTH)`）；`JwtAuthenticationFilter.java:85`（must-change-password 拦截）；`User.java:46` | ✅ |
| 数据加密 | AES-256-GCM 敏感字段（手机号等）；`DATA_ENCRYPTION_KEY` 环境变量、密钥缺失/长度不符 fail-fast；GCM 认证加密防篡改 | `DataEncryptor.java:25`（`AES/GCM/NoPadding`）、`:111`（32 字节校验抛错）、`:19`（GCM 认证加密）；`UserMapper.java:24`（写时 AES-256-GCM）；`CryptoProperties.java:11` | ✅ |
| 令牌生命周期 | refresh 轮转 + 重用判定并吊销全部令牌（OAuth 2.1 / RFC 6819） | M1 D8；`AuthService`（access/refresh 双令牌） | ✅ |
| 暴力破解 | 登录连续 5 次失败锁 15 分钟 + 安全日志告警 | `application.yml:69-70`（`LOGIN_MAX_ATTEMPTS:5`、`LOGIN_LOCK_DURATION:PT15M`）；`common/audit/SecurityLogger.java` | ✅ |
| 限流 | 写接口 Resilience4j RateLimiter（100007 + `Retry-After`）、AI 独立限流 | `RateLimitFilter.java:27`、`RateLimitService.java:15,59`、`CommonErrorCode.java:34`（`RATE_LIMITED(100007)`）；`AiRateLimitService.java:18,58` | ✅ |
| 审计 | 关键操作 `audit_log` 落库（`REQUIRES_NEW` 独立事务）、安全事件走 `SecurityLogger` | `AuditService.java:15,42`（`@Transactional(REQUIRES_NEW)`）；`common/audit/AuditAction.java`、`SecurityLogger.java` | ✅ |
| 传输 | 生产 TLS 1.3（SLB 终结）、`forward-headers-strategy: native` 还原代理头 | `application.yml`（M1 §8.4） | ✅ |
| 硬编码扫描 | `scripts/secret-scan.sh` 持续拦截（CI 阶段 1） | `scripts/secret-scan.sh`（已存在，`yunxiao-pipeline.yml` 阶段 1 调用） | ✅ |

## 2. 密钥管理

- **来源**：`JWT_SECRET`、`DATA_ENCRYPTION_KEY` 均由环境变量注入，代码/配置无硬编码真实密钥（`application.yml` 仅提供开发默认占位值，生产由云效/部署注入覆盖）。
- **长度校验**：两者均在启动时校验长度并 fail-fast —— JWT `<32 字节` 抛错（`JwtTokenService.java:187`）；AES `≠32 字节` 抛错（`DataEncryptor.java:111`）。缺密钥不会静默降级。
- **测试密钥**：测试资源使用全零固定占位值，注释明确「仅测试用，非任何环境真实密钥」。

## 3. 发现与处置

| # | 级别 | 项 | 处置计划 |
|---|---|---|---|
| 1 | 中 | 依赖已知漏洞扫描未在本机执行 | 移交云效代码检测组件（D48），启用后补扫 |
| 2 | 低 | 生产 TLS 由 SLB 终结，应用侧未强制 HTTPS 重定向 | 依赖部署拓扑（SLB 负责 TLS），记录为「由基础设施保证」；无需代码变更 |

> 无高危项，无需随 M6 紧急修复。
