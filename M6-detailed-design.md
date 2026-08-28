# M6 测试加固与安全 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（M6 章节）、`M0-detailed-design.md`、`M1-detailed-design.md`、`M2-detailed-design.md`、`M3-detailed-design.md`、`M4-detailed-design.md`、`M5-detailed-design.md`、`docs/version-matrix.md`
> 文档版本：v1.0（2026-08-28，M6 开发基线）
> 适用范围：M6 阶段（第 16–17 周，11-30 ~ 12-13）
> M5 基线：`mvn clean verify` 全绿（Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥ 80% BUNDLE 门禁通过）；通知模块（站内信 + 外部通道投递 + 事件驱动）就绪；AI 抽象层四端口 + `StubChatPort`、意图目录 11 意图、二次确认状态机、Resilience4j CB/TimeLimiter/Retry、`AiCleanupJob` 就绪；幂等键协议（M3）与登录锁定/令牌轮转（M1）就绪

---

## 1. 概述

### 1.1 目标

M6 是发布（GA）前最后一个「专项加固」里程碑：在 M0–M5 交付的全部功能基线之上，完成 mission「测试约束」与「安全约束」的全部收口项，并承接此前各里程碑显式挂起至 M6 的遗留项，交付：

- **单测补全**：全模块 JaCoCo 覆盖率 ≥ 80%（BUNDLE 口径），以报告为准逐模块补漏
- **集成测试加固**：关键路径全覆盖；首次引入 WireMock，为外部 AI/语音 HTTP 依赖建立可控故障注入的 HTTP mock 桩
- **性能测试**（发布前置条件）：JMeter 10 TPS 容量压测，P95 ≤ 500ms、错误率达标、无资源泄漏
- **安全测试**：OWASP Top 10 自查（SQL 注入 / XSS / CSRF / 越权）+ 认证与加密实现审查 + 季度渗透测试机制（首轮 GA 前）
- **数据合规**：儿童个人信息保护（COPPA 类）复核、日志脱敏覆盖率验证、测试数据脱敏流程验证
- **质量门禁**：以云效代码检测为等效质量门禁（替代 M0 D8 预留的 SonarQube 部署）

### 1.2 范围（In Scope）

- 单测补全：全模块覆盖率缺口定位（JaCoCo report）与补测（JUnit 5 + Mockito + AssertJ，测试数据工厂）
- 集成测试：WireMock 依赖引入 + HTTP mock 桩 + 安全/合规/降级专项套件 + 既有 M1–M5 套件回归
- 性能测试：JMeter 测试计划（.jmx）、数据种子复用、10 TPS 稳态压测、报告存档、无泄漏验证
- 安全测试：OWASP 专项用例（含越权 IDOR、注入、XSS、CSRF 正当性说明）+ 认证加密实现审查清单
- 渗透测试机制：季度流程 + 首轮安排（GA 前），落 `docs/security/`
- 数据合规：COPPA 复核清单、脱敏覆盖验证、测试数据脱敏流程
- 质量门禁：`yunxiao-pipeline.yml` 代码检测组件启用与基线固化
- 性能复测回补触发表（M2 D7 / M3 R6 / M5 遗留的「超限回补」条件汇总）

### 1.3 非目标（Out of Scope）

| 事项 | 归属阶段 |
|---|---|
| Docker 镜像构建与 JVM 参数落地（ZGC 选型 M3 D22/D23 已定，镜像落地） | M7 |
| 阿里云资源、环境隔离、CI/CD 部署流水线、灰度回滚 | M7 |
| 每日增量 + 每周全量备份策略生效与恢复演练 | M7 |
| SLS 结构化日志接入与告警规则配置（M1 §9.2 标注 M6/M7，此处归 M7） | M7 |
| 真实 LLM ≥95% 准确率复跑 + 真实降级演练（provider 接入后复跑） | provider 接入后（M4 R2 口径） |
| 真实鸿蒙 Push / 短信 / 邮件通道接入 | 通道拍板后（M5 D39） |
| SonarQube 独立部署（自建/SonarCloud） | 已由 D48 改为云效代码检测替代 |
| 新业务功能、新数据库表 | 无（M6 不新增业务表，无 V10 迁移） |

---

## 2. 决策记录（已确认）

> 续 M5 D47 编号。D48/D49 为两项前置决策（质量门禁形态、WireMock 引入），D50–D55 为 M6 设计基线决策。

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D48 | 质量门禁形态 | **以云效代码检测替代 SonarQube 独立部署**（修正 M0 D8「自建/SonarCloud 届时决策」）：不引入 sonar 插件、不自建实例；`yunxiao-pipeline.yml` 已占位的「代码检测」组件承载安全/规范扫描，与 `mvn clean verify`（Checkstyle/PMD/SpotBugs + JaCoCo 80%）组成等效质量门禁。门禁基线沿用 M0 §11.3 口径平移：无阻断/严重问题、覆盖率 ≥ 80%、重复率 ≤ 3% | §11；对齐 roadmap M2–M5 DoD 中「SonarQube 归 M6」的最终落点 |
| D49 | WireMock 引入 | **本次引入** `org.wiremock:wiremock-standalone:3.x`（test scope），为外部 AI/语音 **HTTP 依赖**建立可控故障注入的 HTTP mock 桩；与 M4 `StubChatPort`（进程内默认桩）**并存、定位分层**：桩 = provider 未接入时的确定性默认实现（业务意图路由）；WireMock = 真实 HTTP 适配器存在时的传输层故障注入（连接超时/4xx/5xx/慢响应/畸形 body）。M6 以「测试专用 HTTP ChatPort 适配器指向 WireMock」验证：若未来接入真实 HTTP 适配器，既有 Resilience4j CB/TimeLimiter/Retry 与 600001 降级在真实网络语义下仍成立 | §5；roadmap M6 任务 2「外部 AI/语音依赖用 WireMock 模拟」的落地口径 |
| D50 | JMeter 压测形态 | `scripts/jmeter/` 下 `.jmx` 计划 + 运行说明；数据种子复用 `PerformanceDataSeeder`（50 家庭 × 8 成员 × 36 月 ≈ 5 万流水，M2 §12.5）；范围 = 认证 + 读端点（看板/流水/趋势/报表）+ 资金写（幂等键）+ AI 意图（`StubChatPort` 进程内，不占外部延迟）+ 通知（站内信）；10 TPS 稳态 ≥ 30 分钟，断言 P95 ≤ 500ms、错误率 ≤ 0.5%、无连接泄漏（HikariCP 活跃连接回落至基线） | §6；承接 M3/M4/M5「正式 10 TPS 压测归 M6」 |
| D51 | 性能复测回补 | 汇总三类「复测超限即回补」条件为一张触发表（§7）：① 报表 P95 > 500ms → 回补异步化（`report_task` 预案 M2 §10.5）；② 看板若引入缓存 → 补缓存失效事件（M5 D41）；③ 并发下幂等预占/连接池压力 → 回补（M3 R6）。任一触发即启用对应预案并复测 | §7；红线对齐 M2 D7 |
| D52 | OWASP 自查形态 | 双轨：**专项安全测试用例**（自动化，可回归）+ **实现审查清单**（人工核验）。CSRF 关闭的正当性：纯 Bearer API、STATELESS、无 Cookie 会话，CSRF 攻击面不成立（记为「不适用 + 理由」而非跳过）；越权 IDOR 复用既有 `FamilyAccessChecker`/`PermissionMatrix` 套件扩展 | §8 |
| D53 | 渗透测试机制 | **季度一次 + 首轮 GA 前**：流程固化（范围界定 → 工具/手法 → 报告分级 → 处置闭环 → 复测），产出落 `docs/security/`；M6 仅建立机制与首轮计划，执行归 GA 前 | §8.3；对齐 mission「渗透测试每季度一次」 |
| D54 | 数据合规自查 | 三件事：① COPPA 类条款复核清单（对照 M1 D3/D5、M4 D35 落地证据）；② 日志脱敏覆盖率验证（`MaskingRules` 四类规则 + `MaskingJsonEncoder` 全端点日志抽检）；③ 测试数据脱敏流程（生产数据禁止用于测试，一律 `PerformanceDataSeeder`/测试工厂生成） | §10；承接 M0 §9.2「M6 脱敏覆盖率验证」与 M4 R5「COPPA 复核」 |
| D55 | 单测补全口径 | 以 **JaCoCo report 为准**逐模块补漏（不凭文件数推断）；**不新增豁免**，仅保留启动类 `PocketMoneyApplication` 豁免（与 M0–M5 一致）；补测遵循 code-style §9（AAA、测试数据工厂、覆盖率 ≥ 80%） | §4；roadmap M6 任务 1 |

---

## 3. 总体设计

### 3.1 M6 定位：不新增运行时链路，聚焦测试与安全资产

M6 与 M0–M5 的本质区别：**不改业务运行时行为**，不新增 Controller/Service/迁移；产出物是「测试资产 + 安全资产 + 报告」。运行时代码仅在「复测回补触发」（D51）或「安全审查发现缺陷」时发生最小修复。

### 3.2 测试资产全景图（M6 结束时）

```
┌─────────────────────────────────────────────────────────────────────┐
│ 单元测试（JUnit 5 + Mockito + AssertJ）── 覆盖率门禁主力               │
│   user / money / rule / finance / ai / notify / common 全模块 ≥ 80%   │
├─────────────────────────────────────────────────────────────────────┤
│ 集成测试                                                            │
│   H2 形态（@SpringBootTest RANDOM_PORT + RestAssured + Flyway 全量）  │
│   PG 形态（Testcontainers PostgreSQL 18，disabledWithoutDocker 托底） │
│   WireMock 形态（外部 AI/语音 HTTP 故障注入）── M6 新增               │
├─────────────────────────────────────────────────────────────────────┤
│ 性能测试（JMeter 10 TPS 稳态压测，@Tag("performance") 基准测试并行）  │
├─────────────────────────────────────────────────────────────────────┤
│ 安全测试（OWASP 专项用例 + 认证加密审查 + 渗透测试机制）               │
├─────────────────────────────────────────────────────────────────────┤
│ 合规自查（COPPA 复核 + 脱敏覆盖验证 + 测试数据脱敏流程）               │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.3 包结构增量（M6 产出）

```
src/test/java/wyq/pocket/money/
├── support/
│   ├── WireMockChatPort.java          # 【新增】test 专用 HTTP ChatPort 适配器（§5.2）
│   └── AiProviderWireMock.java        # 【新增】WireMock 桩工厂（LLM/ASR/TTS 契约，§5.2）
├── security/                          # 【新增】OWASP 专项用例（§8）
│   ├── SqlInjectionSecurityTest.java
│   ├── XssSecurityTest.java
│   ├── CsrfPostureSecurityTest.java
│   ├── IdorSecurityTest.java
│   └── SensitiveDataExposureTest.java
└── integration/
    ├── AiHttpDegradationWireMockIntegrationTest.java  # 【新增】HTTP 故障 → 降级（§5.3）
    └── （既有 M1–M5 套件回归，不动）

scripts/jmeter/
├── pocket-money-load.jmx              # 【新增】10 TPS 压测计划（§6.2）
└── README.md                          # 【新增】运行步骤与断言（§6.4）

docs/security/
├── owasp-self-check-report.md         # 【新增】OWASP 自查报告模板（§8）
├── auth-encryption-review.md          # 【新增】认证加密实现审查清单（§9）
└── penetration-test-plan.md           # 【新增】季度渗透测试机制（§8.3）

docs/
└── version-matrix.md                  # 【修改】追加 WireMock 版本锁定（§5.1）

yunxiao-pipeline.yml                   # 【修改】代码检测组件启用说明（§11）
```

### 3.4 与 M5 基线的衔接

| M5/M4/M3 交付物 | M6 变更 |
|---|---|
| JaCoCo 80% BUNDLE 门禁（pom.xml） | **不加豁免、不降门槛**；M6 逐模块补漏至达标（§4） |
| `PerformanceBaselinePgIntegrationTest`（`@Tag("performance")`，surefire 默认排除） | 沿用为「本地基准」；M6 补正式 JMeter 10 TPS 压测（§6） |
| `StubChatPort` / `FailingChatPort` / `ai.stub.fail`（M4） | 原样保留；M6 增 WireMock HTTP mock（D49），二者并存互补 |
| Resilience4j CB/TimeLimiter/Retry + 600001 降级（M4 D36） | WireMock 故障注入验证其在真实 HTTP 语义下成立（§5.3） |
| `AbstractPostgresIntegrationTest` / `PostgresContainerIntegrationTest` / H2 形态 | 原样复用；安全/降级新套件挂载其上 |
| `PerformanceDataSeeder` / `IdempotencyKeys` / `ScriptedPushPort` | 原样复用（性能种子、幂等键、Push 桩） |
| `MaskingRules` / `MaskingJsonEncoder`（M0 §9.2） | 做覆盖率验证，不重构（§10.2） |
| `SecurityConfig`（stateless + CSRF off） / `JwtTokenService` / `DataEncryptor` | 做实现审查，不重构（§9） |

---

## 4. 单测补全（roadmap M6 任务 1，D55）

### 4.1 现状与缺口定位

- 现状：主代码 277 类、测试 110 类；JaCoCo 门禁在 M0–M5 已全绿（≥ 80% BUNDLE），但为「整体达标」，个别模块可能靠整体摊平覆盖，存在局部短板。
- **定位方法（M6 第一周）**：`mvn clean test jacoco:report` → 打开 `target/site/jacoco/index.html` 按包逐模块读行覆盖率，产出《覆盖率缺口清单》（模块 → 未覆盖类/分支 → 补测建议）。
- 候选短板（以报告为准，此处仅提示方向）：`finance`（12 主类 / 2 测试类）、`ai`（33 主类 / 13 测试类）为补测重点；`common` 各工具类分支（异常路径、边界值）通常为分支覆盖缺口。

### 4.2 补测策略（遵循 code-style §9）

- AAA 模式（Arrange-Act-Assert）；测试方法名表达预期行为；测试数据经工厂创建（不散落魔法值）。
- 每个公共方法对应单测；重点补**异常路径**（空值、越界、非法参数、外部依赖异常）与**边界值**（金额 DECIMAL 精度边界、时间边界、幂等键长度边界）。
- 金额类：浮点/精度边界用例（`BigDecimal` 0.00 / 0.005 四舍五入 / 负数拒绝 / 超上限拒绝）。
- AI 类：意图目录 11 意图 × 变体、二次确认状态机全迁移、CB 打开/半开/关闭、Timeout、Retry 幂等。
- 通知类：投递状态机全迁移（PENDING→SENT/DEAD）、阈值触发/关闭边界。

### 4.3 门禁口径

- 不新增豁免（仅 `PocketMoneyApplication.class` 保留，pom.xml 现豁免项不变）。
- 补测以 JaCoCo **BUNDLE LINE COVEREDRATIO ≥ 0.80** 为硬门禁（pom.xml 现有规则），各模块单独不设独立下限但须全部计入 BUNDLE。

---

## 5. 集成测试加固与 WireMock（roadmap M6 任务 2，D49）

### 5.1 WireMock 依赖（pom.xml 变更）

```xml
<!-- M6 集成测试：外部 AI/语音 HTTP 依赖的故障注入模拟（M6-detailed-design.md §5.1，D49）
     版本经 spike 锁定后写入 docs/version-matrix.md -->
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>${wiremock.version}</version>
    <scope>test</scope>
</dependency>
```

- 新增 `<wiremock.version>` 属性（3.x 最新稳定版，JDK 25 兼容性 spike 确认后锁定至 `docs/version-matrix.md`，沿用 S 系列 spike 惯例）
- 仅 test scope，不进入运行时镜像

### 5.2 HTTP mock 桩设计

**背景**：M4 建立了四端口（`ChatPort` / `SpeechToTextPort` / `TextToSpeechPort` / `EmbeddingPort`）与 `StubChatPort` 默认桩；当前**无真实 HTTP 适配器**（provider 未选型）。WireMock 桩的作用是**前置验证传输层契约与降级链路**，令未来接入真实 HTTP 适配器时，既有韧性机制无需返工。

- `AiProviderWireMock`（test 工厂）：以 WireMock 定义模拟 LLM/ASR/TTS provider 的 HTTP 契约端点（如 `/v1/chat/completions` 风格占位契约），提供四种故障脚本：
  - `timeout`（固定延迟超过 `AI_TIMEOUT`，触发 TimeLimiter → 降级）
  - `serverError`（5xx，触发 CircuitBreaker 熔断 → 降级）
  - `clientError`（4xx / 参数错误，触发 Retry 与错误透出）
  - `malformed`（200 但畸形 body，触发解析失败降级）
- `WireMockChatPort`（test 专用 `ChatPort` 实现）：经 `RestClient`（或 `WebClient`）指向 WireMock 动态端口，将 HTTP 响应映射回 `ChatPort` 契约。**仅 test scope**，不污染主代码（主代码不出现任何 `wiremock` 依赖）。
- 与 `StubChatPort` 的关系（D49）：`StubChatPort` 继续作为 `ai.mock=true`（默认）的进程内默认桩，验证**业务意图路由**正确；`WireMockChatPort` 仅在专项集成测试中注入，验证**传输层故障与降级**正确。二者不冲突，不互相替换。

### 5.3 集成测试套件增量

| 套件 | 场景 |
|---|---|
| `AiHttpDegradationWireMockIntegrationTest` | 注入 `WireMockChatPort` + `AiProviderWireMock`；① timeout → TimeLimiter 触发 → 600001 降级响应；② 5xx 连续失败 → CB OPEN → 快速失败降级；③ 恢复后 CB HALF_OPEN → CLOSED；④ 畸形 body → 解析失败降级不抛 500 |
| `SqlInjectionSecurityTest`（H2/PG） | 恶意输入（`' OR '1'='1`、`; DROP`、注释符、UNION 注入）注入各查询参数 → 无数据越权、无 500、行为正常（`#{}` 参数化核验，§8.1） |
| `XssSecurityTest` | 昵称/备注/规则名存 `<script>`/`<img onerror>` → 存储原样、出参 JSON 序列化转义、日志不原样落 HTML（§8.2） |
| `IdorSecurityTest` | 跨家庭访问他家庭看板/流水/规则/通知（构造他人 familyId/id）→ 403 + 100004 / 700001（§8.4） |
| `SensitiveDataExposureTest` | 出参不含密码哈希/加密密钥/`data-key`；actuator `show-details: when-authorized` 未授权不泄露明细（§8.5） |
| 既有 M1–M5 套件回归 | 全量回归通过（WireMock 与安全测试不侵入既有链路） |

---

## 6. 性能测试（roadmap M6 任务 3，D50）

### 6.1 目标与前置

- **目标**：发布前置条件——10 TPS 稳态下全 API P95 ≤ 500ms、错误率 ≤ 0.5%、无资源泄漏。
- **前置**：JMeter（5.x）环境 + 目标服务以 `test`/`local` profile 起于干净 PostgreSQL（种子数据 5 万流水）；外部依赖（AI/通知）以进程内桩（`StubChatPort` / `NoopPushPort`）隔离，压测对象为**本服务自身**。

### 6.2 JMeter 测试计划（scripts/jmeter/pocket-money-load.jmx）

- **线程组**：10 并发线程（虚拟用户）持续 30 分钟，无 ramp-up 尖峰；吞吐量整形（Constant Throughput Timer）锁定 10 TPS。
- **场景脚本**（按权重混合）：
  - 认证：`POST /api/v1/auth/login`（预热获取 token，复用至各请求头）
  - 读：`GET /dashboard` / `GET /transactions` / `GET /trends` / `GET /reports/income-expense`（60%）
  - 写：`POST /deposits` / `POST /withdrawals`（带 `Idempotency-Key`，20%）
  - AI 意图：`POST /api/v1/ai/...`（意图解析走 `StubChatPort`，15%）
  - 通知：`GET /notifications` / `POST /notifications/read-all`（5%）
- **断言**：HTTP 200 与业务 `code=0`；响应时间 P95 ≤ 500ms；错误率 ≤ 0.5%。
- **无泄漏验证**：压测期间抽样 `GET /actuator/metrics/hikaricp.connections.active`（或 JDBC 指标）——活跃连接在稳态后回落并稳定，不随时间单调增长；压测结束后 JVM 堆（`/actuator/metrics/jvm.memory.used`）不持续攀升。

### 6.3 数据种子

- 复用 `PerformanceDataSeeder`（`integration/support/PerformanceDataSeeder.java`）：50 家庭 × 8 成员 × 36 月 ≈ 5 万流水，显式 id 段 ≥ 1,000,000 + `setval` 重对齐、余额不变式（M2 §12.5）。
- JMeter 压测前以一次性脚本/测试执行注入种子；报告记录种子规模（`seedVolumeShouldMatchDesign` 同口径 50400 条）。

### 6.4 报告与存档

- 报告落 `docs/performance/m6-load-report-<date>.md`（或 HTML 导出 + 摘要）：TPS 曲线、P95/P99、错误率、活跃连接/堆趋势、结论（达标/不达标）。
- **不达标 → 触发 D51 回补表**（§7），回补后复测直至达标。
- `PerformanceBaselinePgIntegrationTest`（本地基准）与 JMeter（并发压测）**双报告并存**，互为印证（M2 R6 双保险口径）。

---

## 7. 性能复测回补触发表（D51）

> 汇总 M2 D7 / M3 R6 / M5 遗留的「复测超限即回补」条件，M6 复测按表执行；任一触发即启用预案并复测。

| # | 触发条件 | 回补动作 | 预案出处 |
|---|---|---|---|
| C1 | 报表类读端点（`/reports/income-expense` 等）P95 > 500ms | 回补异步化：`report_task(id, family_id, params, status PROCESSING/DONE/FAILED, result JSONB)` + `@Async` 虚拟线程执行 + 客户端轮询 | M2 D7 / §10.5 |
| C2 | 看板读取在高并发下成为瓶颈，决定引入缓存 | 补「看板缓存 + 缓存失效事件」下游（账务变动事件触发缓存失效） | M5 D41 / §遗留 |
| C3 | 并发下幂等预占竞争、HikariCP 连接池压力导致 P95 恶化 | 幂等预占优化 / 连接池参数再评估（M3 R6：最大池 10 是否不足、预占锁竞争） | M3 R6 / §8.1 |
| C4 | AI 意图链路线程/超时在压测下异常 | 复核 AI 限流与 TimeLimiter 参数（`AI_TIMEOUT`/`AI_RATE_LIMIT_*`） | M4 D36 |

- 触发判定以 JMeter 报告 P95 与错误率为准；回补为**最小运行时代码变更**，回补后按 §6.4 复测。

---

## 8. 安全测试（roadmap M6 任务 4，D52/D53）

### 8.1 OWASP Top 10 自查矩阵

| OWASP 项 | 现状/对策 | M6 验证方式 |
|---|---|---|
| A01 越权访问（IDOR） | `FamilyAccessChecker` 数据级守卫 + `@PreAuthorize` 方法级；跨家庭 → 403 + 100004 | `IdorSecurityTest`（跨家庭 familyId/id 遍历） |
| A02 加密失效 | AES-256-GCM 敏感字段、BCrypt strength=10、TLS 1.3（SLB 终结） | 认证加密审查（§9） |
| A03 注入（SQL） | MyBatis 全 `#{}` 参数化，无 `${}` | `SqlInjectionSecurityTest` + 源码 `${` 全扫 |
| A03 注入（其他） | 无 LDAP/XXE/命令执行入口；日志注入以结构化 JSON 规避 | 审查清单 + 恶意输入用例 |
| A05 安全配置错误 | 生产 `DEBUG` 关、`springdoc`/actuator 明细受控、`show-details: when-authorized` | `SensitiveDataExposureTest` + 审查 |
| A07 认证与会话 | JWT 双令牌 + 轮转 + 重用吊销（M1 D8）、登录锁定（M1 D7） | 既有套件回归 + 审查 |
| A07 组件已知漏洞 | 依赖漏洞扫描 | 云效代码检测组件（§11） |
| CSRF | **不适用**：纯 Bearer API、STATELESS、无 Cookie 会话，无浏览器自动附带凭据的 CSRF 攻击面 | `CsrfPostureSecurityTest`（断言无 Cookie、STATELESS，记录正当性，非跳过） |
| XSS | JSON API（非 HTML 渲染）；出参 JSON 序列化转义；存储侧不落富文本 | `XssSecurityTest`（§8.2） |

### 8.2 XSS 专项（存储型 + 反射型）

- 存储型：昵称/备注/规则名/通知文案参数注入 `<script>alert(1)</script>`、`<img src=x onerror=...>`、`javascript:` 协议 → 落库原样（不执行）、出参 JSON 转义（`<`→`<` 等）、日志经 `MaskingJsonEncoder` 不放大。
- 反射型：错误消息/查询回显中的注入串不进入 HTML 上下文（本服务无 HTML 渲染，接口为 JSON，风险面小，验证 JSON 转义即可）。
- 不引入 HTML sanitizer（无富文本字段）；结论与理由写入报告。

### 8.3 渗透测试机制（D53）

- 频率：每季度一次 + 首轮 GA 前；范围：认证/授权、资金写接口幂等、越权、注入、AI 指令越权（二次确认绕过）、敏感数据暴露。
- 流程：范围界定 → 工具/手法（人工 + 扫描器）→ 报告分级（高危/中危/低危）→ 处置闭环（高危必修、中低危有处置计划）→ 复测确认。
- 首轮计划落 `docs/security/penetration-test-plan.md`；结果存档（报告不存仓库，仅存结论与处置项，避免泄露细节）。

### 8.4 越权用例要点（IdorSecurityTest）

- 家长 A 访问家长 B 家庭（篡改 `familyId` 路径参数）→ 403 + 100004。
- 孩子访问家长专属端点（篡改 `userId`/`familyId`）→ 403 + 100004。
- 用户访问他人通知（篡改 `notification/{id}`）→ 700001。
- 用户访问他人流水/规则/任务（篡改业务 `id`）→ 403/404 且不回源数据。
- 复用既有 `PermissionMatrixPgIntegrationTest` / `M2PermissionMatrixPgIntegrationTest` 覆盖矩阵，M6 增补「跨家庭 ID 遍历」用例。

---

## 9. 认证与加密实现审查（roadmap M6 任务 4，D52）

> 人工审查 + 用例佐证，产出 `docs/security/auth-encryption-review.md`。以下为审查清单骨架。

| 项 | 审查要点 | 现状依据 |
|---|---|---|
| JWT | HS256 密钥经 `JWT_SECRET` 环境变量注入、无硬编码、access 15m / refresh 14d、签名算法固定不随 `alg` 头切换 | `JwtTokenService` / `application.yml` |
| 密码 | BCrypt strength=10、登录密码不落明文、`mustChangePassword` 首登强制改密 | `SecurityConfig` / M1 |
| 数据加密 | AES-256-GCM 手机号等敏感字段，`DATA_ENCRYPTION_KEY` 环境变量、密钥缺失 fail-fast；解密明文仅 service 层短暂存在 | `DataEncryptor` / `CryptoProperties` / M1 §8 |
| 令牌生命周期 | refresh 轮转 + 重用判定并吊销全部令牌（OAuth 2.1 / RFC 6819） | M1 D8 |
| 暴力破解 | 登录连续 5 次失败锁 15 分钟 + 安全日志告警 | M1 D7 / `SecurityLogger` |
| 限流 | 写接口 Resilience4j RateLimiter（100007 + `Retry-After`）、AI 独立限流 | `RateLimitFilter` / M3 D20 / M4 |
| 审计 | 关键操作 `audit_log` 落库（`AuditService` REQUIRES_NEW）、安全事件走 `SecurityLogger` | `common/audit` |
| 传输 | 生产 TLS 1.3（SLB 终结）、`forward-headers-strategy: native` 还原代理头 | `application.yml` / M1 §8.4 |
| 硬编码扫描 | `scripts/secret-scan.sh` 持续拦截（CI 阶段 1） | `secret-scan.sh` / `yunxiao-pipeline.yml` |

- 审查发现的问题分级处理：高危必修（随 M6 修复 + 回归）、中低危有处置计划（记录至 M7 或后续）。

---

## 10. 数据合规自查（roadmap M6 任务 5，D54）

### 10.1 儿童个人信息保护（COPPA 类）复核

| 条款 | 落地依据 | M6 复核动作 |
|---|---|---|
| 家长同意（verifiable consent） | 孩子账号由家长创建（M1 D3，家长同意内置） | 复核注册/加孩子链路同意路径与留痕 |
| 数据最小化 | 儿童数据最小化采集（M1 D5，仅登录名/昵称/零花钱） | 复核 `app_user` 儿童字段无超采 |
| 语音隐私 | 语音不落盘（M4 D35，无音频列/文件，`AiCleanupJob` 清理会话/消息/待确认动作 TTL） | 复核「零残留」断言（M4 DoD 用例） |
| 会话清理 | 会话/消息/待确认动作按 TTL 定期清理（`AiCleanupJob`） | 复核清理范围与触发 |
| 数据删除 | 家庭成员移除级联清理（M2 `MemberRemovedMoneyListener` 等） | 复核级联清理不残留孤儿数据 |

### 10.2 日志脱敏覆盖率验证

- 复核 `MaskingRules` 四类规则（身份证/手机号/银行卡/密钥键值对）与 `MaskingJsonEncoder`（JSON 结构化日志脱敏）在**全端点**的覆盖：抽检 auth/money/rule/ai/notify 各链路日志输出，断言无明文手机号/密码/密钥/身份证。
- 产出《脱敏覆盖抽检记录》（`docs/security/`），抽检不通过即修复（不回写业务逻辑，只补脱敏规则/编码器）。

### 10.3 测试数据脱敏流程

- 规则：**生产数据禁止导入测试环境**；测试数据一律经 `PerformanceDataSeeder` / 测试工厂生成（确定性、无真实个人信息）。
- M6 验证：测试库无真实手机号/身份证/姓名（脚本抽检 + 抽查）；`docs/` 与测试资源无真实样例数据。

---

## 11. 质量门禁（D48：云效代码检测替代 SonarQube）

- 修正 M0 D8「自建/SonarCloud 届时决策」：**不引入独立 SonarQube**，以阿里云云效「代码检测」组件（`yunxiao-pipeline.yml` 已占位的 code-inspection 阶段）承载安全/规范扫描。
- 门禁基线（M0 §11.3 口径平移）：无阻断/严重问题、覆盖率 ≥ 80%（JaCoCo）、重复率 ≤ 3%（云效检测）。
- 门禁组合（等效质量门禁）：
  1. `mvn clean verify`：JaCoCo ≥ 80% + Checkstyle + PMD（圈复杂度 ≤ 5）+ SpotBugs（Medium）
  2. 云效代码检测：安全扫描 + 规范 + 重复率
  3. `scripts/secret-scan.sh`：硬编码敏感信息
- `yunxiao-pipeline.yml` 更新：code-inspection 阶段补「阻断级问题方可通过 + 重复率 ≤ 3%」说明；移除「SonarQube 门禁 M6 前接入」占位注释（改为「由云效代码检测承载，D48」）。

---

## 12. 配置增量

- **运行时配置：无新增**（M6 不新增业务开关；安全/合规为测试与审查，不落 `application.yml`）。
- **测试/工具配置**：
  - WireMock 动态端口（测试内 `WireMockConfiguration` 随机端口，无需固定配置）
  - JMeter 计划内置常量（线程数/时长/断言阈值），运行说明见 `scripts/jmeter/README.md`
- 依赖增量：仅 `org.wiremock:wiremock-standalone`（test scope，§5.1）。

---

## 13. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 |
|---|---|---|---|
| T1 | 覆盖率缺口定位 + 单测补全（JaCoCo report → 缺口清单 → finance/ai/common 等补测至 BUNDLE ≥ 80%） | — | 3 人天 |
| T2 | WireMock 引入 + HTTP mock 桩 + 降级专项集成测试（`AiProviderWireMock`/`WireMockChatPort`/`AiHttpDegradationWireMockIntegrationTest`） | T1 | 2 人天 |
| T3 | 安全测试套件（OWASP 专项：SQLi/XSS/CSRF/IDOR/敏感数据）+ 认证加密审查 | T1 | 2.5 人天 |
| T4 | 数据合规自查（COPPA 复核 + 脱敏覆盖 + 测试数据脱敏）+ 渗透测试机制建立 | — | 1.5 人天 |
| T5 | JMeter 压测计划 + 执行 + 复测回补判定（D51 表）+ 报告存档 | T1 | 2 人天 |
| T6 | 云效质量门禁落地（yunxiao-pipeline.yml）+ 全量回归 + DoD 收尾 | T2–T5 | 1.5 人天 |

合计约 **12.5 人天**。roadmap 排期 2 周（10 工作日/人）：

- **2 人投入**：约 6.25 人天/人，舒适（推荐：安全与资金链路需双人评审）。
- **1 人投入**：12.5 人天 > 10 人天，略超排期；候选裁剪：渗透测试机制降为「模板 + 首轮范围」、脱敏抽检降为抽样而非全端点、WireMock 桩缩减为仅 timeout/5xx 两脚本。

关键路径：T1 → T2/T3/T5 并行；T6 为收尾闸门。

---

## 14. 验收标准（DoD，与 roadmap 一致并细化）

- [ ] 性能测试报告：10 TPS 稳态下全 API P95 ≤ 500ms、错误率 ≤ 0.5%、无连接/内存泄漏（JMeter 报告 + 指标趋势存档，`docs/performance/`）
- [ ] 安全测试报告无高危项；中低危项有处置计划（`docs/security/owasp-self-check-report.md` + `auth-encryption-review.md`）
- [ ] 覆盖率与质量门禁报告达到门禁：JaCoCo ≥ 80%（`target/site/jacoco/index.html`）+ Checkstyle/PMD/SpotBugs 0 违规 + 云效代码检测无阻断/严重、重复率 ≤ 3%
- [ ] 单测补全：全模块 BUNDLE 覆盖率 ≥ 80%，无新增豁免
- [ ] 集成测试：OWASP 专项 + WireMock 降级专项 + 既有 M1–M5 套件全量回归通过
- [ ] 数据合规：COPPA 复核、脱敏覆盖抽检、测试数据脱敏验证三项通过
- [ ] 渗透测试机制建立：季度流程 + 首轮（GA 前）计划存档
- [ ] 复测回补触发表（D51）建立，C1–C4 任一触发即有对应回补预案

---

## 附录 A：安全测试用例清单

| 用例 | 攻击手法 | 预期 |
|---|---|---|
| SQLi-1 | 查询参数注入 `' OR '1'='1` | 无越权数据、无 500，行为正常 |
| SQLi-2 | 注入 `; DROP TABLE` / 注释符 `--` | 无执行、无结构破坏 |
| SQLi-3 | UNION 注入 | 无联合查询结果泄漏 |
| XSS-1 | 昵称/备注/规则名存 `<script>` | 落库原样、出参 JSON 转义 |
| XSS-2 | `<img onerror>` / `javascript:` | 不执行、不落入 HTML 上下文 |
| CSRF-1 | 无 Cookie、STATELESS、Bearer 断言 | 记录「不适用 + 理由」 |
| IDOR-1 | 篡改 familyId 访问他家庭 | 403 + 100004 |
| IDOR-2 | 篡改 notification/{id} 访问他人通知 | 700001 |
| IDOR-3 | 孩子访问家长专属接口 | 403 + 100004 |
| EXP-1 | 出参/actuator 泄露密码哈希/密钥 | 无泄露、`show-details` 受控 |

## 附录 B：复测回补触发表

见 §7（C1 报表异步化 / C2 看板缓存 / C3 幂等与连接池 / C4 AI 韧性参数）。

## 附录 C：与 roadmap M6 任务/DoD 映射

| roadmap M6 条目 | 设计章节 |
|---|---|
| 任务 1 单测补全 ≥ 80%（JaCoCo） | §4（D55） |
| 任务 2 集成测试全覆盖 + WireMock | §5（D49） |
| 任务 3 JMeter 10 TPS 压测 | §6（D50）+ §7 回补（D51） |
| 任务 4 安全测试 OWASP + 认证加密审查 + 渗透机制 | §8 / §9（D52/D53） |
| 任务 5 数据合规（COPPA/脱敏/测试数据脱敏） | §10（D54） |
| DoD 1 性能报告 | §6.4 / §14 |
| DoD 2 安全报告无高危 | §8 / §14 |
| DoD 3 覆盖率与 SonarQube → 云效质量门禁 | §11（D48）/ §14 |

## 附录 D：文档变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-28 | M6 开发基线初稿：质量门禁改云效（D48）、WireMock 引入（D49）、JMeter 形态（D50）、复测回补表（D51）、OWASP 自查（D52）、渗透机制（D53）、数据合规（D54）、单测口径（D55）；测试资产全景、WBS 与 DoD |

---

*本设计作为 M6 开发基线；实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*
