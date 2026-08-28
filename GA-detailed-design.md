# GA 1.0 正式发布 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（里程碑总览 GA 行、M7 章节「达成 GA」、GA 后持续运营表、§1.3 量化约束、§7 约束追溯表）、`M0`–`M7-detailed-design.md`、`docs/version-matrix.md`
> 文档版本：v1.0（2026-08-28，GA 发布工程基线）
> 适用范围：GA（1.0 正式发布，roadmap 排期 2027-01 初）准入门、发布窗口与 GA 后运营衔接
> M7 基线：`mvn clean verify` 全绿（Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥ 80% BUNDLE，213+ 类）；Docker 多阶段镜像资产就绪（`Dockerfile` / `.dockerignore`，基础镜像 tag 待 Docker spike 锁定）；云效流水线七阶段（secret-scan → build-and-verify → code-inspection → archive → docker-build-push → deploy-test → deploy-prod-canary）就绪；灰度/回滚脚本（`scripts/deploy/`）就绪；文档五件套齐套（`docs/deploy`、`docs/ops`、`docs/dev`、`docs/security` + springdoc API 文档）；迁移 V1–V10 就绪；AI 四端口契约（`common/ai/`：`ChatPort`/`SpeechToTextPort`/`TextToSpeechPort`/`EmbeddingPort`）+ DeepSeek 适配器（`SpringAiChatPort`，D67）+ 进程内桩（`StubChatPort`）；通知 `PushPort` + 鸿蒙适配器（`HarmonyPushPort`，D68）+ `NoopPushPort`

---

## 1. 概述

### 1.1 目标

GA 是路线图的收口里程碑：roadmap 对 GA 的定义为 **「1.0 正式发布：生产可用 + 文档齐套」**，M7 的目标亦明确为「完成阿里云生产环境部署与发布机制，**达成 GA**」。因此 GA 不新增业务功能，而是把 M0–M7 的交付物转化为一次**可签核、可割接、可运营的正式发布**，交付：

- **发布工程**：SemVer 版本号规则、1.0.0 版本切割流程（分支 / tag / 制品）、发布窗口执行与回滚衔接
- **准入门（Go/No-Go）**：roadmap §1.3 量化基线逐条签核的追溯矩阵；M0–M7 各阶段 DoD 闭环确认；环境阻塞项（云上演练 / 首轮渗透 / provider 依赖项）的执行清单与判据
- **发布文档**：`CHANGELOG.md`（1.0.0 发布说明）、GA 上线检查单（`docs/release/go-live-checklist.md`）、README 刷新至 GA 基线
- **GA 后运营衔接**：roadmap「GA 后持续运营」表落实 owner / 节奏 / 证据，与运维手册、安全操作手册衔接

### 1.2 范围（In Scope）

- 版本与发布工程：版本号规则、release 分支与 tag 约定、`scripts/release/cut-release.sh` 版本切割脚本、制品命名（ACR 镜像 tag）
- GA 准入门：量化基线追溯矩阵（P95 / 10 TPS / 99.5% / 覆盖率 / AI 准确率 / TLS 1.3 / AES-256 / OWASP / COPPA / 备份）、Go/No-Go 签核机制、上线检查单
- 发布窗口：T-7 / T-0 / T+7 执行清单，与 `docs/deploy/release-runbook.md`（金丝雀/回滚）衔接
- 安全收尾：首轮渗透测试的 GA 前定位（机制已在 M6 D53 建立，GA 前执行首轮并闭环高危项）
- AI 准确率评测报告的 GA 定位与范围声明（Stub 形态 vs provider 接入后）
- 文档齐套收口：CHANGELOG、README、文档索引、五件套评审归档状态
- GA 后持续运营机制：节奏 / owner / 证据归档

### 1.3 非目标（Out of Scope）

| 事项 | 归属 |
|---|---|
| 新业务功能（GA 后迭代） | 无（GA 不新增业务功能；D67/D68 的 provider/通道接入随 V10 迁移与 `spring-ai` 依赖在 1.0.0 内落地） |
| ASR / TTS 语音 provider 接入 | 语音服务商选型（roadmap 前置决策 #3）后迭代；GA 真实大模型对话已接 DeepSeek（D67），语音能力仅有 `SpeechToTextPort` / `TextToSpeechPort` 端口契约 |
| Embedding 语义搜索适配器 | `EmbeddingPort` 契约已立，适配器随 provider 迭代 |
| 短信推送通道 | 外部推送已拍板鸿蒙 Push Kit（D68），短信渠道待后续扩展（`PushPort` 端口按 provider 扩展） |
| 微服务拆分 / Kubernetes / 跨 Region 容灾 | 后续演进（M7 D57 单 Region 多可用区为 1.0 形态） |
| 鸿蒙客户端开发与客户端离线逻辑 | 客户端仓库职责；服务端离线支撑（幂等键、友好错误码、同步协议）M3 已就绪 |
| 云上资源开通与演练的**实际执行** | 需阿里云 / Docker 目标环境（本环境不可达）；GA 交付执行清单与判据，执行证据按检查单归档 |

---

## 2. 决策记录（已确认）

> 续 M7 D62 编号。D63–D66 为 GA 发布工程基线决策；D67/D68 为 GA 前拍板的两项 provider/通道决策（真实大模型 DeepSeek、外部推送鸿蒙 Push Kit）。

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D63 | 版本与发布工程 | **语义化版本 SemVer**：GA = `1.0.0`；release 分支 `release/1.0.x`，git tag `v1.0.0`；制品 = ACR 镜像 tag `1.0.0`（+ `latest` 与 git short sha 双标）；版本切割由 `scripts/release/cut-release.sh` 半自动化（工作区洁净校验 → `mvn clean verify` 全门禁 → `versions:set` 去 SNAPSHOT → 提交打 tag → 回切下一迭代 SNAPSHOT），**不自动 push**（push/合并由发布负责人在发布窗口执行并留痕）；pom 在发布窗口前保持 `1.0-SNAPSHOT` | §4；1.0.x 补丁线用于 GA 后 hotfix（`1.0.1` …），主线继续 `1.1.0-SNAPSHOT` |
| D64 | GA 准入门（Go/No-Go） | **量化基线 + 阶段 DoD 双签核**：roadmap §1.3 全部量化指标逐条挂证据（测试报告 / 演练记录 / 审查文档），M0–M7 DoD 全闭环；任一条 P0（功能不可用 / 资金一致性 / 安全高危 / 备份不可恢复 / 密钥泄露）未闭环即 **No-Go**；P1（中低危 / 非阻断文档项）允许带处置计划 Go。目标环境执行项（云演练 / 渗透 / 压测终验）在本开发环境不可达，按「清单先行、目标环境执行、证据归档」处理，证据缺失同样 No-Go | §5；准入门落 `docs/release/go-live-checklist.md`，与 M7 DoD 一一承接 |
| D65 | 发布文档齐套形态 | **CHANGELOG**（根目录 `CHANGELOG.md`，Keep a Changelog 风格，1.0.0 按 M0–M7 列功能与已知限制）+ **上线检查单**（`docs/release/go-live-checklist.md`）+ **README 刷新 GA 基线**（里程碑表 / 文档索引 / 目录结构）；M7 五件套（API / 部署 / 运维 / 开发 / 安全操作）GA 只做评审归档与索引收口，不重写；API 文档以 springdoc 运行时 OpenAPI 为准（生产 swagger-ui 关闭、api-docs JSON 内网保留） | §9；对齐 mission「文档伴随 / 文档五件套」与 roadmap GA「文档齐套」 |
| D66 | GA 后持续运营机制 | roadmap「GA 后持续运营」表**落实节奏与证据归档点**：依赖安全更新（每发布周期 + 高危即时，`docs/version-matrix.md` 记录）、渗透测试（每季度 + GA 前首轮，报告存内部安全库）、性能评估（每季度，ARMS + JMeter 复测）、技术债（每迭代预留容量）、容灾演练（每半年 PITR/切换，`docs/ops/backup-recovery-runbook.md` 附录记录）、AI 准确率回归（模型/Prompt 每次变更后，`ai/eval` 评测集复跑）。运营项纳入运维手册/安全操作手册的既有流程，不新建系统 | §10；运营节奏表进 `go-live-checklist.md` 附录，GA 后首次复核 = 发布后 30 天 |
| D67 | 真实大模型选型 | **DeepSeek（deepseek-v4-pro）经 Spring AI OpenAI 兼容协议接入**：`spring-ai-starter-model-openai`（2.0.0）+ `spring.ai.openai.base-url=https://api.deepseek.com`；自动装配默认关闭（`spring.ai.model.chat=none`），`AI_MOCK=false` 时由 `SpringAiChatPort` 装配（`ChatModel` 缺失即启动失败）；`DEEPSEEK_API_KEY` 经环境变量注入（无默认值，fail-fast）。ASR/TTS/Embedding 仍为端口契约（前置决策 #3 未拍板） | §8；`SpringAiChatPort` + `SpringAiChatPortTest`；pom §spring-ai |
| D68 | 外部推送通道选型 | **鸿蒙 Push Kit**：`HarmonyPushPort` 实现 `PushPort`（OAuth2 client_credentials 换 access_token → `POST /v1/{app-id}/messages:send`，成功码 `80000000`，401 刷新重试一次，令牌缓存 + 过期余量）；设备令牌注册 `POST /api/v1/notifications/push-token`（`user_push_token` 表 V10 迁移，UPSERT）；`NOTIFY_PUSH_ENABLED=true` + `HARMONY_PUSH_*` 凭据经环境变量注入（缺失 fail-fast）。短信渠道待后续扩展 | §3.2 / 附录 B；`HarmonyPushPort` + `HarmonyPushPortWireMockTest` |

---

## 3. 总体设计

### 3.1 GA 发布全景

```
M0–M7 交付（代码/测试/资产/文档）
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ T-7 ~ T-1  发布冻结与准入（Go/No-Go 评审）                     │
│  · 代码冻结：release/1.0.x 切出，只接 blocker 修复            │
│  · mvn clean verify 全绿 / secret-scan 零命中 / 云效检测无阻断 │
│  · 量化基线追溯矩阵逐条签核（§5 / 附录 A）                     │
│  · 目标环境演练：10TPS 终验 · 灰度+回滚演练 · PITR · 告警链路  │
│  · 首轮渗透测试完成、高危闭环（GA 前）                         │
│  · 文档齐套评审（CHANGELOG / 五件套 / README）                │
└─────────────────────────────────────────────────────────────┘
        │ 全部 P0 闭环 → Go；否则 No-Go（延期或回退修复）
        ▼
┌─────────────────────────────────────────────────────────────┐
│ T-0  发布窗口（衔接 release-runbook.md 金丝雀流程）            │
│  · cut-release.sh 切 1.0.0 → tag v1.0.0 → 镜像推 ACR          │
│  · 金丝雀 5% → 观察（observe-canary 门禁）→ 50% → 100%       │
│  · 超限即一键回滚（rollback.sh，回上一稳定 tag）              │
│  · 发布记录归档（tag / 镜像 digest / 值班人 / 起止时间）       │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│ T+1 ~ T+30  发布后观察与运营衔接                               │
│  · T+1：核心指标复盘（P95/错误率/登录/资金写）、告警静默确认   │
│  · T+7：发布回顾（问题单 / 变更项 / 文档补遗）                 │
│  · T+30：GA 后运营机制首次复核（D66 节奏表启动）              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 GA 与各里程碑的关系

GA **不产出新运行时能力**，是 M0–M7 的签核与割接层：

| 层 | 里程碑 | GA 视角 |
|---|---|---|
| 工程底座 | M0 | 质量门禁、CI、配置分层 → 准入项「全门禁绿」 |
| 身份权限 | M1 | JWT/家庭/加密/审计 → 准入项「权限矩阵 + 加密审查」 |
| 核心业务 | M2 | 看板/规则/收支/报表 → 准入项「资金一致性 + 余额不变式」 |
| 可靠性 | M3 | 幂等/限流/韧性/JVM → 准入项「幂等测试 + 10TPS 压测」 |
| AI 交互 | M4 | 编排/二次确认/审计/四端口 + DeepSeek 适配器（D67）→ 评测机制 |
| 通知事件 | M5 | 站内信/事件解耦/relay + 鸿蒙 Push 适配器（D68）→ 设备令牌注册 |
| 测试安全 | M6 | 覆盖率/WireMock/JMeter/OWASP/合规/渗透机制 → 准入项主体 + 首轮渗透 GA 前执行 |
| 部署发布 | M7 | Docker/阿里云/灰度/监控/备份/五文档 → 发布窗口执行主体 |

---

## 4. 版本与发布工程（D63）

### 4.1 版本号规则（SemVer）

- `MAJOR.MINOR.PATCH`：`1.0.0` = GA 首版。
  - **MAJOR**：不兼容 API 变更（对鸿蒙端契约破坏）。
  - **MINOR**：向下兼容的功能新增（GA 后主线 `1.1.0`、`1.2.0`…）。
  - **PATCH**：向下兼容的缺陷修复（`1.0.x` 补丁线，hotfix）。
- 开发期：`1.0-SNAPSHOT`（当前 pom 版本，保持至发布窗口）。
- 发布制品版本号 = pom 去 SNAPSHOT 后的版本；镜像 tag 与 git tag 同源。

### 4.2 分支与 tag

|  ref | 用途 |
|---|---|
| `master` | 主线；GA 后继续 `1.1.0-SNAPSHOT` |
| `release/1.0.x` | 发布冻结分支：T-7 从 master 切出；GA 后只收 blocker/hotfix，产出 `1.0.1`… |
| `v1.0.0` | 发布 tag（ annotated tag：`git tag -a v1.0.0 -m "GA 1.0.0"`） |
| `hotfix/1.0.x-<issue>` | 生产缺陷修复分支，合入 `release/1.0.x` 与 master |

### 4.3 版本切割脚本 `scripts/release/cut-release.sh`

半自动化，发布负责人在发布窗口本地执行（或云效手工阶段）：

1. 前置校验：当前在 `release/x.y.z` 分支、工作区洁净、无未拉取提交。
2. `mvn -B -ntp clean verify` 全门禁通过（覆盖率/静态检查/测试）。
3. `mvn versions:set -DnewVersion=<RELEASE> -DgenerateBackupPoms=false` → 提交 `chore(release): cut <RELEASE>`。
4. `git tag -a v<RELEASE> -m "Release <RELEASE>"`。
5. `mvn versions:set -DnewVersion=<NEXT_SNAPSHOT> -DgenerateBackupPoms=false` → 提交 `chore(release): next development iteration <NEXT_SNAPSHOT>`。
6. **不自动 push**：打印 `git push origin release/... --tags` 与后续流水线触发指令，由发布负责人核对后执行（外向动作留痕，符合发布双人确认惯例）。

镜像 tag 由流水线阶段 5 从 `BUILD_TAG` 生成：发布时 `BUILD_TAG=1.0.0`，同时打 `latest` 与 `1.0.0-<gitsha>`（可追溯 digest）。

### 4.4 Hotfix 流程（GA 后）

`hotfix/...` 从 `release/1.0.x` 切出 → 修复 + 测试 → 合入 `release/1.0.x` → `cut-release.sh 1.0.1 1.0.2-SNAPSHOT`（在 release 分支上，next 为同线 PATCH SNAPSHOT）→ 金丝雀发布 → 回合 master。资金/安全类 hotfix 强制双人评审（mission 跨阶段约束）。

---

## 5. GA 准入门：Go/No-Go（D64）

### 5.1 签核规则

- 评审时点：T-1（发布窗口前一天），发布负责人主持，开发/测试/运维（小团队可同人多角）逐项签核。
- **P0（任一不满足即 No-Go）**：
  1. 核心业务链路不可用或资金一致性缺陷（余额不变式被破坏）
  2. `mvn clean verify` 不绿 / 覆盖率 < 80% / 静态检查有阻断
  3. 安全高危项未闭环（OWASP / 渗透高危 / 密钥硬编码 / 明文敏感数据）
  4. 备份不可恢复（PITR 演练未通过）
  5. 灰度或回滚流程未演练成功
  6. 生产密钥未入凭据库 / 三环境未隔离
- **P1（可带计划 Go）**：中低危安全项（有处置单与期限）、非阻断文档补遗、观测埋点增强。
- 签核记录归档：`docs/release/go-live-checklist.md` 签核表 + 会议结论（Go / No-Go / 有条件 Go）。

### 5.2 量化基线追溯矩阵（roadmap §1.3 → 证据）

| roadmap §1.3 指标 | 目标 | 证据（归档点） | GA 状态 |
|---|---|---|---|
| API P95 | ≤ 500ms | JMeter 10 TPS 终验报告（`scripts/jmeter/` 产出，存 `docs/performance/`）；ARMS 发布后观测 | 机制就绪；**目标环境终验待执行** |
| 并发 | ≥ 10 TPS | 同上 | 同上 |
| 可用性 | ≥ 99.5% | 双可用区 + SLB 摘除 + 回滚（M7 D57）；ARMS 月度可用性报告（GA 后起算） | 架构就绪；**数值 GA 后观测** |
| 单测覆盖率 | ≥ 80% | JaCoCo BUNDLE 门禁（`mvn verify` 强制） | ✅ 门禁内持续满足 |
| 圈复杂度 | ≤ 5 | PMD 门禁 | ✅ |
| 质量门禁 | 云效代码检测无阻断/严重（D48 替代 SonarQube） | 流水线阶段 3 记录 | ✅ 机制就绪；发布前终检 |
| AI 准确率 | ≥ 95% | `ai/eval` 评测集 + 评测报告 | 机制就绪；**对 DeepSeek（D67）复跑并存档报告**（附录 B） |
| 语音二次确认 / 可追溯 | 强制 / 全程审计 | M4 二次确认状态机 + AI 操作审计链路；M6 集成测试 | ✅ |
| TLS | 1.3 | SLB 监听配置（`docs/deploy/deployment-guide.md` §2） | 资产就绪；**云上配置待核验** |
| 敏感数据加密 | AES-256 | M1 `DataEncryptor`（AES-256-GCM）+ M6 认证加密审查 | ✅ |
| OWASP 合规 | 无高危 | `docs/security/owasp-self-check-report.md` + 首轮渗透报告 | 自查 ✅；**首轮渗透 GA 前待执行** |
| 儿童隐私（COPPA 类） | 合规 | `docs/security/data-compliance-review.md`；语音零残留检查 | ✅ |
| 备份 | 每日增量 + 每周全量 | RDS 策略（`docs/ops/backup-recovery-runbook.md`）+ PITR 演练记录 | 策略就绪；**演练待执行** |
| 审计日志 | 关键操作落库 | `audit_log`（M1）+ 安全日志；备份随库 | ✅ |

### 5.3 阶段 DoD 闭环确认

M0–M7 各里程碑 DoD 在其详细设计中定义并在编码阶段闭环；GA 准入只做**证据抽查与汇总签核**：

- 代码侧（本环境可证）：`mvn clean verify` 全绿、`secret-scan.sh` 零命中、迁移 V1–V10、测试 277+ 主类 / 120+ 测试类。
- 文档侧：五件套 + 四份安全报告 + 发布文档齐套（§9）。
- 环境侧（目标环境执行，证据归档）：见 §5.4。

### 5.4 目标环境执行项（本开发环境不可达，清单先行）

| # | 执行项 | 判据 | 依据文档 |
|---|---|---|---|
| E1 | Docker 镜像构建 spike | 基础镜像 tag 锁定、镜像内 `mvn verify` 通过、非 root、HEALTHCHECK UP | `docs/version-matrix.md` §10 |
| E2 | 阿里云资源开通 | ECS×2 / RDS / SLB / ACR / OSS / 安全组 / VPC | `docs/deploy/deployment-guide.md` §2 |
| E3 | RDS 首启迁移 | Flyway V1–V10 无错 | deployment-guide §7 |
| E4 | 10 TPS 压测终验 | P95 ≤ 500ms、错误率 ≤ 0.5%、连接池回落 | `scripts/jmeter/README.md` |
| E5 | 金丝雀 + 回滚演练 | 5%→50%→100% 流程通；触发回滚后旧版恢复 | `docs/deploy/release-runbook.md` |
| E6 | PITR 恢复演练 | 恢复到指定时间点，行数/余额不变式/加密读取通过 | `docs/ops/backup-recovery-runbook.md` §2 |
| E7 | 告警链路演练 | 制造 5xx/健康失败，告警送达并处置 | `docs/ops/operations-handbook.md` §3 |
| E8 | SLS 脱敏抽检 | 无明文手机号/身份证/银行卡/密钥 | operations-handbook §4 |
| E9 | 首轮渗透测试 | 高危清零、中低危有处置单 | `docs/security/penetration-test-plan.md` |
| E10 | ARMS/SLS 接入 | 核心接口指标上报、日志可查 | operations-handbook §1/§5 |

E1–E10 全部完成并归档证据是 Go 的硬条件（D64）。

---

## 6. 发布窗口执行与观察

发布窗口操作细则以 `docs/deploy/release-runbook.md` 为准（金丝雀步骤、中止触发、蓝绿兜底、回滚），GA 增量为**时间线与角色**：

| 时点 | 动作 | 负责人 |
|---|---|---|
| T-7 | 切 `release/1.0.x`；代码冻结；回归全量；CHANGELOG 定稿 | 发布负责人 |
| T-3 | E1–E10 证据齐备；渗透报告闭环；量化矩阵签核 | 开发/测试/运维 |
| T-1 | Go/No-Go 评审；发布窗口与值班安排通知；备份确认 | 发布负责人 |
| T-0 | `cut-release.sh 1.0.0 1.1.0-SNAPSHOT`；tag 与镜像推送；金丝雀发布（阶段 7 人工审批）；全程 ARMS/SLS 值守 | 发布负责人 + 值守 |
| T+0~2h | 观察门禁：错误率 ≤ 0.5%、P95 ≤ 500ms、无资金写错误、登录无异常；超限即回滚 | 值守 |
| T+1 | 指标复盘；告警静默确认；发布记录归档（tag / digest / 指标截图） | 运维 |
| T+7 | 发布回顾：问题单、热修计划、文档补遗 | 全员 |
| T+30 | GA 后运营首次复核（D66 节奏表启动） | 运维/安全 |

---

## 7. 安全收尾：首轮渗透测试（GA 前）

- 机制已在 M6 D53 建立：季度 cadence + **首轮安排在 GA 前**（roadmap M6 任务 4 原文）。
- GA 准入门对首轮的要求（E9）：
  - 范围按 `docs/security/penetration-test-plan.md`：认证/授权、资金写幂等、越权（IDOR）、注入、AI 指令越权（二次确认绕过）、敏感数据暴露。
  - 结论口径：**高危清零**（P0），中低危挂处置单（P1，带期限）。
  - 原始报告存内部安全库（不入仓库）；仓库仅存结论与处置项（与 M6 D53 一致）。
- 密钥与配置侧发布前检查：`secret-scan.sh` 零命中、生产密钥全部来自云效凭据库、JWT_SECRET/DATA_ENCRYPTION_KEY 三环境互不相同、Swagger UI 生产关闭（见 `docs/security/production-security-operations.md` §6）。

---

## 8. AI 准确率评测与 GA 范围声明

### 8.1 评测机制现状

- 评测资产：`src/main/java/wyq/pocket/money/ai/eval/`（`AiAccuracyEvaluator` / `EvalCase` / `EvalReport`）+ M4 评测集；意图收敛于有限操作集（`IntentCatalog`），数据类回答实时查账（不自由生成）。
- GA 形态：真实大模型对话 = DeepSeek（D67，`AI_MOCK=false` + `SPRING_AI_MODEL_CHAT=openai`），链路（意图 → 二次确认 → Function Calling → 审计）全通；`AI_MOCK=true` 时为 `StubChatPort` 进程内确定性桩（测试/演示/评测基线）。
- **≥95% 准确率的对外承诺时点**：对 DeepSeek 以 `ai/eval` 复跑并存档评测报告（roadmap 风险表：不达标则收窄 AI 可执行范围至查询类）。该项不阻塞 1.0.0 核心业务发布，阻塞的是「AI 语音能力 GA 对外宣传口径」（语音 ASR/TTS 仍待选型）。

### 8.2 GA 1.0.0 范围声明（摘要，全文附录 B）

- **GA 可用**：全部非 AI 业务（认证/家庭/看板/规则/收支/学习工作价值/报表/站内信通知）+ AI 编排链路（真实大模型 DeepSeek，可联调、可审计）+ 外部推送（鸿蒙 Push Kit，设备令牌注册）。
- **GA 时关闭/桩形态**：ASR/TTS、Embedding 语义匹配（端口契约齐备，语音服务商选型后迭代开启）。
- **不含**：客户端离线逻辑（鸿蒙端仓库）、微服务/K8s、跨 Region 容灾、短信推送渠道。

---

## 9. 文档齐套收尾（D65）

| 文档 | 状态 | GA 动作 |
|---|---|---|
| API 文档（springdoc OpenAPI） | M1 起就绪 | 生产 swagger-ui 关、api-docs 内网保留；发布前核验 |
| `docs/deploy/deployment-guide.md` | M7 就绪 | 评审归档 |
| `docs/deploy/release-runbook.md` | M7 就绪 | 评审归档；GA 发布窗口执行依据 |
| `docs/ops/operations-handbook.md` | M7 就绪 | 评审归档；GA 后运营衔接 |
| `docs/ops/backup-recovery-runbook.md` | M7 就绪 | E6 演练记录回填附录 |
| `docs/dev/development-guide.md` | M7 就绪 | 评审归档 |
| `docs/security/production-security-operations.md` | M7 就绪 | 评审归档；渗透衔接 |
| `docs/security/` 四份 M6 报告 | M6 就绪 | 归档；渗透首轮结论追加 |
| `CHANGELOG.md` | **GA 新增** | 1.0.0 发布说明（功能清单 + 已知限制） |
| `docs/release/go-live-checklist.md` | **GA 新增** | 准入门检查单 + 签核表 + 运营节奏附录 |
| `README.md` | M1 基线（滞后） | **刷新至 GA 基线**：里程碑表、文档索引、目录结构 |
| `docs/version-matrix.md` | 持续维护 | E1 spike 后回填基础镜像 tag；无新增依赖 |

---

## 10. GA 后持续运营（D66）

roadmap「GA 后：持续运营」表落实为可执行节奏（进 `go-live-checklist.md` 附录）：

| 事项 | 节奏 | 证据/归档点 | 衔接文档 |
|---|---|---|---|
| 依赖安全更新与版本升级 | 每发布周期；高危即时 | `docs/version-matrix.md` 迭代记录 | development-guide §5 |
| 渗透测试 | 每季度（首轮 GA 前） | 报告存内部安全库，结论入仓库 | penetration-test-plan / production-security-operations §4 |
| 性能评估与优化 | 每季度（ARMS 数据驱动） | JMeter 复测报告 + ARMS 月报 | operations-handbook §2 |
| 技术债评估与偿还 | 每迭代预留容量 | 迭代回顾 | — |
| 灾难恢复演练 | 每半年（PITR/主备切换） | backup-recovery-runbook 附录演练表 | backup-recovery-runbook |
| AI 准确率回归评测 | 模型/Prompt 每次变更后 | `ai/eval` 评测报告 | M4 设计；本设计 §8 |
| 告警/值班复盘 | 每次 P1 事件后 | 事件时间线归档 | operations-handbook §6 |

GA 后首次复核：发布后 30 天（T+30），确认运营节奏启动、监控数据连续、无遗留 blocker。

---

## 11. 配置增量

- **运行时配置（D67/D68 新增）**：`spring.ai.model.chat` / `spring.ai.openai.*`（DeepSeek）+ `pocket-money.notify.push.harmony.*`（鸿蒙 Push）；环境变量 `SPRING_AI_MODEL_CHAT`、`DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`、`NOTIFY_PUSH_ENABLED`、`HARMONY_PUSH_APP_ID` / `CLIENT_ID` / `CLIENT_SECRET` / `TOKEN_URL` / `BASE_URL` 均经 `application.yml` `${...}` 注入（无默认值项缺失 fail-fast）。
- **pom 变更**：新增 `spring-ai-bom` / `spring-ai-starter-model-openai`（`spring-ai.version=2.0.0`，D67）；版本号 `1.0-SNAPSHOT` → 发布窗口由 `cut-release.sh` 切为 `1.0.0` 并回切 `1.1.0-SNAPSHOT`（D63）。
- **新增文件**：`GA-detailed-design.md`、`CHANGELOG.md`、`docs/release/go-live-checklist.md`、`scripts/release/cut-release.sh`；D67/D68 实现（`SpringAiChatPort`、`HarmonyPushPort`、`PushTokenService`、`UserPushToken` 等）+ 迁移 `V10__create_user_push_token.sql`。
- **修改文件**：`README.md`（刷新 GA 基线）、`application.yml`（DeepSeek/鸿蒙配置段）。
- **迁移增量**：V10（`user_push_token` 设备令牌表，D68）。
- **流水线变更：无**（阶段 5 的 `BUILD_TAG` 在发布窗口传 `1.0.0` 即可，无需改 YAML）。

---

## 12. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 | 本环境可交付 |
|---|---|---|---|---|
| T1 | 发布工程：版本规则 + `cut-release.sh` + 分支/tag 约定 | — | 1 人天 | ✅ 脚本与文档 |
| T2 | 准入门：`go-live-checklist.md`（量化矩阵 + E1–E10 + 签核表） | T1 | 1.5 人天 | ✅ 检查单 |
| T3 | 发布文档：`CHANGELOG.md` 1.0.0 + README GA 刷新 + 索引收口 | T2 | 1 人天 | ✅ 文档 |
| T4 | 目标环境演练（E1–E8、E10：镜像 spike / 云资源 / 压测终验 / 灰度回滚 / PITR / 告警 / 脱敏 / 监控接入） | 云资源 | 4 人天 | ⛔ 目标环境执行 |
| T5 | 首轮渗透测试（E9） | 测试环境 | 3 人天 | ⛔ 目标环境执行（机制 M6 就绪） |
| T6 | Go/No-Go 评审 + 发布窗口割接 + T+30 复核 | T4/T5 | 1.5 人天 | ⛔ 发布窗口执行 |

仓库内交付（T1–T3）合计约 **3.5 人天**；T4–T6 为目标环境/发布窗口工作，判据与清单全部在 T2 检查单中固化，证据按 D64 归档后方可 Go。

关键路径：T1 → T2 → T3（仓库内）→ T4/T5（并行，目标环境）→ T6（Go/No-Go → 割接）。

---

## 13. 验收标准（DoD）

- [ ] 版本切割流程就绪：`cut-release.sh` 通过语法与干跑校验；分支/tag/版本号规则文档化（D63）
- [ ] Go/No-Go 准入门建立：roadmap §1.3 量化基线逐条有证据归属；M0–M7 DoD 汇总签核位齐备（D64）
- [ ] E1–E10 目标环境执行项全部完成并归档证据（发布窗口前，硬条件）
- [ ] 首轮渗透测试完成且高危清零（GA 前，roadmap M6 任务 4）
- [ ] `CHANGELOG.md` 1.0.0 发布说明定稿（功能清单 + 已知限制 + 升级说明）
- [ ] README 刷新至 GA 基线；文档索引覆盖五件套 + 安全报告 + 发布文档（D65）
- [ ] 发布窗口按 release-runbook 完成金丝雀割接（或按判据回滚），发布记录归档
- [ ] T+30 运营复核完成，GA 后运营节奏表（D66）启动
- [ ] 全流程零硬编码：`secret-scan.sh` 零命中、密钥仅经凭据库注入

---

## 附录 A：GA 准入追溯矩阵（mission/roadmap 约束 → 里程碑 → GA 证据）

| 约束（roadmap §7 / mission） | 落地里程碑 | GA 证据 |
|---|---|---|
| 家庭看板、规则、收支记录 | M2 | 集成测试套件；余额不变式测试；E4 压测报告 |
| 学习/工作价值零花钱 | M2 | 同上 |
| AI 语音交互 + 五项可信度 | M4 | 二次确认/审计链路测试；评测集（§8）；provider 迭代项附录 B |
| 登录/登出、家庭信息、规则设置 | M1 | 权限矩阵套件；认证加密审查报告 |
| RESTful / P95 ≤500ms / 弱网容错 / 离线同步 | M0/M3 | 幂等套件；JMeter 报告（E4）；同步协议文档 |
| 单测 ≥80% / 集成测试 / 性能测试 / 季度渗透 | 全程/M6 | JaCoCo 门禁；`docs/security/` 四份报告；E9 |
| Docker + 阿里云 / 灰度回滚 / 环境隔离 | M7 | Dockerfile/compose/流水线；E2/E5；deployment-guide |
| 备份策略 / 审计日志 / 数据脱敏 | M1/M6/M7 | audit_log；脱敏抽检（E8）；PITR 演练（E6） |
| 监控与日志（Actuator/ARMS/SLS） | M0/M7 | E10 接入记录；operations-handbook |
| 文档五件套 | 各阶段/M7 | §9 文档表；评审归档 |

## 附录 B：GA 1.0.0 范围声明

**GA 包含（服务端，生产可用）**

- 认证与家庭：JWT 双令牌、登录锁定、家长/孩子角色与权限矩阵、家庭/成员管理、AES-256-GCM 敏感字段加密、审计与安全日志
- 零花钱核心：看板汇总与趋势、包月/个性化规则与自动结算、收支流水、学习任务与工作价值零花钱、家庭收支报表（同步聚合）
- 可靠性：资金写幂等键、限流、AI 依赖熔断/超时/重试降级、ZGC + 虚拟线程 + 容器感知堆
- AI 编排：意图解析（DeepSeek，D67）、Function Calling、资金指令二次确认状态机、操作审计、会话 TTL 清理；四端口契约（Chat/ASR/TTS/Embedding）+ `SpringAiChatPort` 适配器 + `StubChatPort` 桩
- 通知：站内信、Spring Event 解耦、投递 relay 重试与清理；外部推送（鸿蒙 Push Kit，D68）+ 设备令牌注册（`user_push_token`）
- 工程与发布：质量门禁（Checkstyle/PMD/SpotBugs/JaCoCo）、云效七阶段流水线、Docker 镜像资产、阿里云部署/灰度/回滚、ARMS/SLS 监控接入资产、RDS 备份策略、文档五件套

**GA 时为桩/关闭（端口契约齐备，选型后迭代开启）**

| 能力 | 现状 | 开启前置 |
|---|---|---|
| ASR 语音识别 | `SpeechToTextPort` 契约，无适配器 | 语音服务商选型（决策 #3） |
| TTS 语音合成 | `TextToSpeechPort` 契约，无适配器 | 语音服务商选型（决策 #3） |
| Embedding 语义匹配 | `EmbeddingPort` 契约，无适配器 | 随 provider 迭代 |
| 短信推送渠道 | `PushPort` 端口可扩展，无适配器 | 短信服务商选型（后置扩展） |

**不含**：鸿蒙客户端（独立仓库；服务端幂等/错误码/同步协议已支撑）、微服务/K8s、跨 Region 容灾。

## 附录 C：与 roadmap GA/运营条目映射

| roadmap 条目 | 本设计章节 |
|---|---|
| 里程碑总览 GA「生产可用 + 文档齐套」 | §1 / §5 / §9 |
| M7「达成 GA」 | §3.2（M7 为发布窗口执行主体） |
| §1.3 量化约束（验收基线） | §5.2 / 附录 A |
| M6 任务 4「首轮渗透安排在 GA 前」 | §7（E9） |
| M4 DoD「AI 准确率 ≥95% 报告存档」 | §8（provider 迭代口径） |
| M7 DoD（灰度/备份/告警/五文档） | §5.4 E5/E6/E7/E8 + §9 |
| 前置决策 #2（LLM 拍板 DeepSeek）/ #4（推送拍板鸿蒙 Push） | D67 / D68 + 附录 B |
| 前置决策 #3（ASR/TTS 语音未拍板） | 附录 B |
| GA 后持续运营表 | §10（D66） |
| §7 约束追溯表 | 附录 A |

## 附录 D：文档变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-28 | GA 发布工程基线初稿：版本与发布工程（D63）、Go/No-Go 准入门与量化追溯（D64）、发布文档齐套（D65）、GA 后运营机制（D66）、真实大模型 DeepSeek（D67）、外部推送鸿蒙 Push Kit（D68）；发布全景、范围声明（附录 B）、E1–E10 目标环境执行清单、WBS 与 DoD |

---

*本设计作为 GA 发布基线；实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*
