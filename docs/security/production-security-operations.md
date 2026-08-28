# 生产安全操作手册（M7）

| 项 | 值 |
|---|---|
| 里程碑 | M7（部署与发布） |
| 依据 | `M7-detailed-design.md` §10（D62）；mission 安全/合规约束；M6 `docs/security/` |
| 适用 | 生产密钥管理、安全事件响应、渗透测试衔接、数据合规操作 |

> 配套文档：`owasp-self-check-report.md`（OWASP 自查）、`auth-encryption-review.md`（认证加密审查）、
> `data-compliance-review.md`（COPPA/脱敏）、`penetration-test-plan.md`（渗透机制）。

---

## 1. 密钥管理

| 密钥 | 环境变量 | 轮换影响 | 轮换方式 |
|---|---|---|---|
| JWT 签名密钥 | `JWT_SECRET` | 轮换后所有已签发令牌失效，用户需重新登录（无状态，无数据影响） | 云效凭据库生成新值 → 更新环境变量 → 滚动重启；可接受的短时强制重登 |
| 数据加密密钥 | `DATA_ENCRYPTION_KEY` | **直接替换会导致既有密文（`phone_encrypted` 等）无法解密** | 见 §2 重加密迁移，**不可简单换值重启** |
| 数据库口令 | `DB_PASSWORD` | RDS 改密后应用需更新连接串 | RDS 控制台改密 → 凭据库更新 → 滚动重启 |
| OSS/ARMS/SLS 凭据 | `OSS_*` / `ARMS_*` / `SLS_*` | 按各服务策略 | 凭据库轮换，最小权限 |

**基线规则**：
- 所有密钥仅存于云效凭据库，运行时注入容器环境变量；**不入代码仓库、不入镜像、不入日志**（`secret-scan.sh` + `MaskingRules` 双拦截）。
- dev/test/prod 三环境密钥**互不相同**。
- 密钥访问最小化，按需授权；访问留痕。

## 2. 数据加密密钥轮换（重加密迁移）

当前 `DataEncryptor` 为**单密钥 AES-256-GCM**，密文不含密钥版本号，故 `DATA_ENCRYPTION_KEY` 轮换必须配合**重加密迁移**（计划内维护）：

1. 准备新密钥 `K2`（32 字节随机 Base64）。
2. 维护窗口：停写或只读模式，备份数据库（见 `../ops/backup-recovery-runbook.md`）。
3. 执行一次性重加密任务：以 `K1`（旧）读取全部加密列 → 以 `K2`（新）加密回写（`phone_encrypted` 等经 `EncryptedFieldTypeHandler` 的列）。
4. 凭据库更新为 `K2`，滚动重启，冒烟验证解密正常（`/users/me` 脱敏手机号正常）。
5. 验证无误后，旧密钥 `K1` 安全销毁。

> 若未来引入密文密钥版本前缀（`v1:`/`v2:`）可支持双密钥无停机轮换，属后续增强（当前非目标）。

## 3. 安全事件响应

| 事件 | 初判 | 处置 |
|---|---|---|
| 登录失败激增 / 疑似爆破 | 登录锁定（5 次/15 分钟，M1 D7）是否生效 | `SecurityLogger` 溯源 IP/账号；必要时 SLB/WAF 封禁；评估账号锁定 |
| 越权访问尝试 | `FamilyAccessChecker` 已拦 403+100004 | 审计日志核对；确认无数据回源；异常模式上报 |
| 敏感数据泄露疑虑 | 出参/日志是否含明文 | 对照 M6 `SensitiveDataExposureTest` 与 SLS 脱敏抽检；确认范围，按合规流程处置 |
| 密钥疑似泄露 | 影响面评估 | 立即轮换受影响密钥（§1/§2）；审计访问记录；必要时强制全量登出（轮换 `JWT_SECRET`） |
| 依赖高危漏洞 | 云效代码检测/公告 | 升级依赖 → `mvn clean verify` 全门禁 → 紧急发布（hotfix 分支） |

- 所有安全事件经 `SecurityLogger`（安全日志）与 `audit_log`（关键操作）留痕，可追溯。
- 事件处置后归档：时间线、影响面、根因、修复、预防措施。

## 4. 渗透测试衔接

- 机制：每季度一次 + 首轮 GA 前（M6 D53，见 `penetration-test-plan.md`）。
- 范围：认证/授权、资金写幂等、越权、注入、AI 指令越权（二次确认绕过）、敏感数据暴露。
- 结果：原始报告存内部安全库（**不入仓库**），仓库仅存结论与处置项；高危必修 + 复测闭环。

## 5. 数据合规操作

- **儿童个人信息（COPPA 类）**：孩子账号由家长创建（家长同意内置）；数据最小化；语音不落盘（`AiCleanupJob` TTL 清理）；家庭成员移除级联清理。复核见 `data-compliance-review.md`。
- **数据删除请求**：家庭成员移除触发级联清理（`MemberRemovedMoneyListener` 等）；备份中的数据按备份保留周期自然过期，超期需合规评估。
- **生产数据**：禁止导入测试环境；测试一律 `PerformanceDataSeeder`/工厂数据（M6 §10.3）。
- **生产变更红线**：严禁在生产环境直接改数据（mission 禁止项）；数据修正走评审 + 审计 + 备份。

## 6. 发布安全检查

- 每次发布：`secret-scan` 无命中、云效代码检测无阻断/严重、Swagger UI 生产关闭、actuator `show-details: when-authorized`、三环境隔离确认。
- 灰度期间安全指标纳入观察：登录失败率、403/100004 越权拦截异常、5xx 突增（见 `../deploy/release-runbook.md`）。
