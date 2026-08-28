# OWASP Top 10 自查报告（M6）

| 项 | 值 |
|---|---|
| 里程碑 | M6（测试加固与安全） |
| 依据 | `M6-detailed-design.md` §8（D52） |
| 日期 | 2026-08-28 |
| 结论 | **无高危项**；CSRF 记为「不适用 + 理由」，XSS 记为「JSON API 低风险 + 理由」；依赖已知漏洞扫描移交云效代码检测组件（§11 / D48） |

---

## 1. 结论摘要

M6 安全自查以「专项测试用例 + 实现审查清单」双轨推进（D52）。自动化安全测试套件 **8/8 通过**（`SqlInjectionSecurityTest`、`XssSecurityTest`、`CsrfPostureSecurityTest`、`IdorSecurityTest`、`SensitiveDataExposureTest`），源码 `${` 全扫确认 MyBatis 无动态 SQL 拼接。审查未发现高危项；中低危项均有处置计划（见 §5）。

## 2. OWASP Top 10 自查矩阵

| OWASP 项 | 现状/对策 | M6 验证方式 | 结论 |
|---|---|---|---|
| A01 越权访问（IDOR） | `FamilyAccessChecker` 数据级守卫 + `@PreAuthorize` 方法级；跨家庭 → 403 + 100004 | `IdorSecurityTest`（跨家庭 familyId 遍历 + 不存在 familyId） | ✅ 通过（§3.1） |
| A02 加密失效 | AES-256-GCM 敏感字段、BCrypt strength=10、TLS 1.3（SLB 终结） | 认证加密审查（`auth-encryption-review.md`） | ✅ 通过 |
| A03 注入（SQL） | MyBatis 全 `#{}` 参数化，无 `${}` | `SqlInjectionSecurityTest` + 源码 `${` 全扫 | ✅ 通过（§3.2 / §4） |
| A03 注入（其他） | 无 LDAP/XXE/命令执行入口；日志以结构化 JSON 规避 | 审查清单 + 恶意输入用例 | ✅ 通过（审查确认无此类入口） |
| A05 安全配置错误 | 生产 `DEBUG` 关、`show-details: when-authorized` | `SensitiveDataExposureTest` + 审查 | ✅ 通过（§3.4） |
| A07 认证与会话 | JWT 双令牌 + 轮转 + 重用吊销（M1 D8）、登录锁定（M1 D7） | 既有套件回归 + 审查 | ✅ 通过 |
| A07 组件已知漏洞 | 依赖漏洞扫描 | 云效代码检测组件（§11） | ⏳ 待云效（处置项 §5） |
| CSRF | **不适用**：纯 Bearer API、STATELESS、无 Cookie 会话 | `CsrfPostureSecurityTest`（断言无 Cookie、STATELESS，记录正当性，非跳过） | ✅ 不适用 + 理由（§3.3） |
| XSS | JSON API（非 HTML 渲染）；出参 JSON 序列化转义；存储侧不落富文本 | `XssSecurityTest` | ✅ 低风险 + 理由（§3.5） |

## 3. 自动化测试证据（`src/test/java/wyq/pocket/money/security/`）

执行：`mvn -B -ntp -Dtest='SqlInjectionSecurityTest,XssSecurityTest,CsrfPostureSecurityTest,IdorSecurityTest,SensitiveDataExposureTest' test` → `Tests run: 8, Failures: 0, Errors: 0`。

### 3.1 SQL 注入（A03）
`SqlInjectionSecurityTest.injectedMonthShouldBeRejectedWithoutLeakOr500`：对 `month` 参数注入 `' OR '1'='1`、`2026-08'--`、`2026-08; DROP TABLE app_user`、`2026-08 UNION SELECT ...` 四种载荷，均被 `^\d{4}-(0[1-9]|1[0-2])$` 正则拦截（HTTP 200 + code 500001），不触发 500、无数据越权、无表结构破坏；注入尝试后 `/users/me` 仍正常返回。

### 3.2 越权（A01）
`IdorSecurityTest`：家长 B 篡改 `familyId` 访问家长 A 的 `/reports/income-expense` 与 `/statistics/summary` → 403 + 100004；不存在 `familyId=999999` → 403 + 100004。

### 3.3 CSRF（不适用）
`CsrfPostureSecurityTest`：受保护端点未认证 → 401 + 100003 且 `Set-Cookie` 为 null；公开登录端点 → 200 且 `Set-Cookie` 为 null。纯 Bearer + STATELESS，无 Cookie 会话，故无浏览器自动附带凭据的 CSRF 攻击面。

### 3.4 敏感数据暴露（A05）
`SensitiveDataExposureTest`：`/users/me` 回参 `data.maskedPhone` 非空且含 `****`；响应体不含明文手机号、`passwordHash`、`phoneEncrypted`、`keyVersion`、`data-key`、`DATA_ENCRYPTION_KEY`、`JWT_SECRET`。

### 3.5 XSS（低风险）
`XssSecurityTest`：`<script>alert(1)</script>`、`<img src=x onerror=alert(1)>` 写入昵称后原样 round-trip（存储不落富文本、无 HTML 渲染出口），响应 `Content-Type: application/json`。结论：本服务无 HTML 渲染出口，接口为 JSON，故不引入 HTML sanitizer。

## 4. 源码扫描证据

对 `src/main/**` 全扫 `${}`：命中的全部为 Spring 配置占位符（`application*.yml` 环境变量、`@Value` 注入、`@Scheduled` cron 表达式），**无一处出现在 MyBatis SQL 片段中**。MyBatis Mapper 参数化确认（例）：`UserMapper.java:46`（`#{passwordHash}, #{nickname}, ...`）、`UserMapper.java:92`（`must_change_password = #{mustChangePassword}, updated_at = now() WHERE id = #{id}`）。

## 5. 发现与处置

| # | 级别 | 项 | 处置计划 |
|---|---|---|---|
| 1 | 中 | A07 组件已知漏洞未在本机扫描（依赖漏洞扫描依赖云效代码检测组件） | 云效流水线 code-inspection 阶段启用后补扫（D48）；`yunxiao-pipeline.yml` 门禁：无阻断/严重问题 |
| 2 | 低 | CSRF 记为不适用，需在产品侧确认无未来引入 Cookie 会话的规划 | 已在本报告记录理由；若未来引入 Cookie 会话，须重新启用 CSRF 防护并补 `CsrfPostureSecurityTest` 断言 |

> 无高危项，无需随 M6 紧急修复。

## 6. 附录：复现命令

```bash
# 安全专项测试
mvn -B -ntp -Dtest='SqlInjectionSecurityTest,XssSecurityTest,CsrfPostureSecurityTest,IdorSecurityTest,SensitiveDataExposureTest' test

# 全量回归（含既有 M1–M5 套件）
mvn -B -ntp clean verify
```
