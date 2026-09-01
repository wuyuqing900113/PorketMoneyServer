# GA 1.0.0 上线检查单（Go/No-Go Checklist）

| 项 | 值 |
|---|---|
| 里程碑 | GA（1.0 正式发布） |
| 依据 | `GA-detailed-design.md`（D63–D66）；roadmap §1.3 量化基线、M7 DoD |
| 使用方式 | 发布负责人逐项勾选并填证据；**任一 P0 未闭环即 No-Go**（D64）；P1 可带处置单有条件 Go |
| 配套文档 | `docs/deploy/release-runbook.md`（金丝雀/回滚步骤）、`docs/deploy/deployment-guide.md`、`docs/ops/operations-handbook.md`、`docs/ops/backup-recovery-runbook.md`、`docs/security/production-security-operations.md` |

---

## 0. 签核结论（T-1 评审填写）

| 角色 | 姓名 | 结论（Go/No-Go/有条件 Go） | 签字日期 |
|---|---|---|---|
| 发布负责人 |  |  |  |
| 开发 |  |  |  |
| 测试 |  |  |  |
| 运维 |  |  |  |
| 安全 |  |  |  |

**P0 硬条件（任一不满足 = No-Go）**：① 核心链路可用、资金余额不变式成立；② `mvn clean verify` 全绿、覆盖率 ≥80%；③ 安全高危清零（含渗透首轮）；④ PITR 备份恢复演练通过；⑤ 金丝雀与回滚演练通过；⑥ 生产密钥全入凭据库、三环境隔离。

---

## 1. T-7 ~ T-1：发布前准备

### 1.1 代码与制品

- [ ] 从 `master` 切出冻结分支 `release/1.0.x`；冻结后仅接 blocker 修复
- [ ] `mvn -B -ntp clean verify` 全绿（Checkstyle/PMD/SpotBugs 0 违规、JaCoCo ≥80%）
- [ ] `bash scripts/secret-scan.sh` 零命中
- [ ] 云效流水线阶段 1–4 通过（secret-scan / build-and-verify / code-inspection 无阻断严重 / archive）
- [ ] Flyway 迁移 V1–V10 评审无变更
- [ ] `CHANGELOG.md` 1.0.0 条目定稿（功能清单 / 已知限制 / 部署须知）
- [x] Docker 镜像构建 spike 完成：基础镜像 tag+digest 锁定并回填 `docs/version-matrix.md` §10；镜像内全门禁通过、非 root 运行、HEALTHCHECK UP（**E1**，2026-08-31 本机 Docker Desktop 29.7.2 实测：`pocket-money-server:m7` 构建成功，非 root `pocket`（uid=999）、分层 jar、ZGC gc.log 可写、对真实 postgres:18 Flyway V1–V10 + 注册/登录/脱敏 E2E、health UP；Boot 4 jarmode layertools→tools 修复见 D73）

### 1.2 环境与密钥

- [ ] 阿里云资源就绪：ECS ×2（跨可用区）/ RDS PostgreSQL 18 主备 / SLB（HTTPS TLS 1.3）/ ACR / OSS / 安全组 / VPC（**E2**，清单见 deployment-guide §2）
- [ ] dev/test/prod 三环境隔离验证：独立 ECS/RDS/ACR namespace，配置与数据互不串扰
- [ ] 生产密钥全部经云效凭据库注入：`JWT_SECRET` / `DATA_ENCRYPTION_KEY` / `DB_*`；三环境密钥互不相同；无密钥入镜像/入仓库
- [ ] RDS 首启 Flyway V1–V10 迁移成功（**E3**，启动日志无迁移错误）
- [ ] RDS 备份策略生效：WAL/PITR 实时 + 每周全量（7 天保留）+ OSS 归档加密
- [ ] 发布前手动备份一次（发布窗口回退兜底）

### 1.3 演练与测试（目标环境执行，证据归档）

- [ ] **E4 压测终验**：测试环境 JMeter 10 TPS，P95 ≤ 500ms、错误率 ≤ 0.5%、HikariCP 活跃连接回落（报告存 `docs/performance/`）
- [ ] **E5 金丝雀 + 回滚演练**：5%→50%→100% 流程通；人为触发回滚后旧版恢复、健康检查 UP
- [ ] **E6 PITR 恢复演练**：恢复到指定时间点；关键表行数、余额不变式、加密字段读取、冒烟全部通过（记录回填 backup-recovery-runbook 附录）
- [ ] **E7 告警链路演练**：制造 5xx / 健康检查失败，ARMS/云监控告警送达（短信/邮件/IM）并完成处置
- [ ] **E8 SLS 脱敏抽检**：auth/money/rule/ai/notify 各链路日志无明文手机号/身份证/银行卡/密码/密钥
- [ ] **E9 首轮渗透测试**（GA 前，M6 D53）：范围按 penetration-test-plan；高危清零，中低危挂处置单（原始报告存内部安全库）
- [ ] **E10 监控接入**：ARMS 核心接口指标（P95/错误率/QPS/JVM）上报；SLS 日志可查；GC 日志采集
- [ ] AI 范围确认：生产 `AI_MOCK=false` + `SPRING_AI_MODEL_CHAT=openai`（DeepSeek，D67）；`NOTIFY_PUSH_ENABLED=true` + 鸿蒙 Push Kit 凭据（D68）——凭据入云效凭据库，缺失即 fail-fast

### 1.4 文档齐套（D65）

- [ ] 五件套评审归档：deployment-guide / release-runbook / operations-handbook（含 backup-recovery-runbook）/ development-guide / production-security-operations
- [ ] API 文档：生产 swagger-ui 关闭、api-docs JSON 内网保留已核验
- [ ] README 为 GA 基线（里程碑表、文档索引）
- [ ] 本检查单签核完成（§0）

---

## 2. 量化基线追溯矩阵（roadmap §1.3）

| 指标 | 目标 | 证据 | 结果 | 签核 |
|---|---|---|---|---|
| API P95 | ≤ 500ms（10 TPS） | E4 JMeter 报告；发布后 ARMS | ☐ 通过 ☐ 不通过 |  |
| 并发 | ≥ 10 TPS | E4 JMeter 报告 | ☐ 通过 ☐ 不通过 |  |
| 可用性 | ≥ 99.5% | 双可用区 + SLB 摘除 + 回滚（M7 D57）；GA 后 ARMS 月报起算 | ☐ 架构就绪 |  |
| 单测覆盖率 | ≥ 80% | JaCoCo BUNDLE 门禁 | ☐ 通过 |  |
| 圈复杂度 | ≤ 5 | PMD 门禁 | ☐ 通过 |  |
| 质量门禁 | 云效代码检测无阻断/严重 | 流水线阶段 3 | ☐ 通过 |  |
| AI 准确率 | ≥ 95% | `ai/eval` 评测；对 DeepSeek（D67）复跑并存档报告（GA 范围声明附录 B） | ☐ 通过 ☐ 有处置单 |  |
| 语音二次确认/可追溯 | 强制 / 全程审计 | M4 状态机 + AI 审计链路测试 | ☐ 通过 |  |
| TLS | 1.3 | SLB 监听配置核验（E2） | ☐ 通过 |  |
| 敏感数据加密 | AES-256-GCM | 认证加密审查报告 + 静态加密落库测试 | ☐ 通过 |  |
| OWASP | 无高危 | owasp-self-check-report + E9 渗透 | ☐ 通过 ☐ 有处置单 |  |
| 儿童隐私 | COPPA 类合规 | data-compliance-review；语音零残留检查 | ☐ 通过 |  |
| 备份 | 每日增量 + 每周全量 | RDS 策略 + E6 PITR 演练 | ☐ 通过 |  |
| 审计日志 | 关键操作落库 | `audit_log` + 安全日志；随库备份 | ☐ 通过 |  |
| 零硬编码 | 密钥不入仓库/镜像 | secret-scan + 凭据库核对 | ☐ 通过 |  |

---

## 3. T-0：发布窗口执行

> 步骤细则以 `docs/deploy/release-runbook.md` 为准；此处为时间线检查点。

- [ ] 发布窗口与值班表已通知；值守人就位（开发 + 运维）
- [ ] 确认发布前备份完成（§1.2）
- [ ] 在 `release/1.0.x` 执行版本切割：`bash scripts/release/cut-release.sh 1.0.0 1.1.0-SNAPSHOT`
- [ ] 发布负责人核对本地结果后 `git push origin release/1.0.x --tags`（脚本不自动 push）
- [ ] 云效流水线以 `BUILD_TAG=1.0.0` 触发：阶段 5 镜像构建推送 ACR（tag `1.0.0` + `latest` + sha）
- [ ] 阶段 6 测试环境部署：`wait-health.sh` 就绪 → 冒烟（`SmokeIntegrationTest`）通过
- [ ] 阶段 7（人工审批后）生产金丝雀：ECS-A 部署 → SLB 权重 5%
- [ ] 观察门禁（`observe-canary.sh`，15 分钟窗）：错误率 ≤ 0.5%、P95 ≤ 500ms、无 5xx 突增、无资金写错误、登录无异常
- [ ] 权重 50% → 观察 10 分钟 → 权重 100% → ECS-B 滚动更新
- [ ] 双实例 `/actuator/health` UP；SLB 健康检查全绿
- [ ] 发布记录归档：tag（v1.0.0）、镜像 digest、值班人、起止时间、指标截图

**立即中止/回滚触发**：错误率 > 0.5% / 5xx 突增 / P95 持续 > 500ms / 健康检查失败 / 登录失败激增 / 资金写错误 →
`bash scripts/deploy/rollback.sh <PREV_STABLE_TAG>`（详见 release-runbook §中止与回滚），并转 T+7 复盘。

---

## 4. 发布后观察

- [ ] **T+0~2h**：值守观察核心指标（P95/错误率/QPS/连接池/熔断状态）；告警无异常
- [ ] **T+1**：指标复盘归档；SLS 日志抽检无异常栈与明文敏感数据；告警静默确认
- [ ] **T+7**：发布回顾——问题单、热修（1.0.x）计划、文档补遗；CHANGELOG 补实际发布日期
- [ ] **T+30**：GA 后运营机制首次复核（§附录节奏表启动）；ARMS 月度可用性报告起算

---

## 附录：GA 后持续运营节奏（D66）

| 事项 | 节奏 | 证据/归档点 | 责任人 |
|---|---|---|---|
| 依赖安全更新与升级 | 每发布周期；高危即时 | `docs/version-matrix.md` | 开发 |
| 渗透测试 | 每季度（首轮 = GA 前 E9） | 内部安全库报告 + 仓库结论 | 安全 |
| 性能评估 | 每季度（ARMS + JMeter 复测） | 复测报告 | 运维/开发 |
| 技术债偿还 | 每迭代预留容量 | 迭代回顾 | 开发 |
| 灾难恢复演练 | 每半年（PITR/主备切换） | backup-recovery-runbook 附录 | 运维 |
| AI 准确率回归 | 模型/Prompt 每次变更后 | `ai/eval` 评测报告 | AI 负责人 |
| 事件复盘 | 每次 P1 事件后 | 事件时间线与改进项 | 全员 |

> 环境阻塞说明：**E1（Docker 镜像构建 spike）已在本机 Docker Desktop 完成并关闭（2026-08-31）**；
> E2–E10 需阿里云目标环境，开发环境不可达。本检查单为执行基线，证据按 D64 归档后方可 Go。
