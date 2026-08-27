# M4 AI 交互集成 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（M4 章节）、`M0-detailed-design.md`、`M1-detailed-design.md`、`M2-detailed-design.md`、`M3-detailed-design.md`、`docs/version-matrix.md`
> 文档版本：v1.0（2026-08-27，M4 开发基线）
> 适用范围：M4 阶段（第 11–14 周，10-26 ~ 11-22）
> M3 基线：`mvn clean verify` 全绿（Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥ 80% 门禁通过，幂等/限流/性能基线就绪，Testcontainers PG 套件就绪）

---

## 1. 概述

### 1.1 目标

在 M3 可靠性基线与 M2 零花钱核心业务之上，落地 AI 语音交互能力，并同步实现 mission 的五项 AI 交互可信度约束，交付：

- **AI 抽象层**：项目自有 `ChatPort`/`EmbeddingPort`/`SpeechToTextPort`/`TextToSpeechPort` 四端口 + 确定性 `StubChatPort` 默认实现（零新依赖，真实提供商后置接入）
- **意图理解（NLU）**：封闭意图目录（9 类查询 + 2 类资金写）→ Function Calling 结构化参数 → 既有 service 层执行
- **AI Function Calling**：AI 调业务接口执行操作，走既有事务与权限体系（`UserIdPrincipal` + `FamilyAccessChecker` + `requireSelfIfChild`）
- **二次确认**：资金写语音指令的会话级待确认动作（`ai_pending_action` + TTL 状态机）
- **可信度机制**：准确性/安全性/一致性/可解释性/隐私五项约束逐一落地（§7，D32–D35）
- **稳定性**：真正落地 Resilience4j TimeLimiter(30s)/CircuitBreaker/Retry + AI 调用限流 + 降级（600001 回落手动操作）
- **评测集**：provider 无关的 `AiAccuracyEvaluator` 与 golden 评测集，真实 LLM 达标待接入后复跑

### 1.2 范围（In Scope）

- AI 抽象层四端口 + `StubChatPort` 默认桩 + `AiProperties`/`AiConfig` 装配（D27/D28/D38）
- 封闭意图目录 `IntentCatalog` + 工具注册 `AiToolRegistry`（11 意图 ↔ service 方法映射）
- `AiOrchestrator` 编排链路（文本通道：意图解析 → 查询直行 / 资金写二次确认 → 执行 → 反馈）
- 会话与待确认动作：`ai_session` / `ai_message` / `ai_pending_action`（V8 迁移）+ 定期清理任务
- 稳定性落地：Resilience4j CB/TimeLimiter/Retry 真正注册 + `AiRateLimitService` + 降级 600001
- 审计动作增补（AI 动作段）+ `AIIErrorCode` 60xxxx 段
- 评测集与评测器（`ai/eval`）
- 集成测试：全链路「查余额→二次确认→执行→反馈」、越权、确认后恰好一笔流水、超时过期、AI 不可用降级

### 1.3 非目标（Out of Scope）

| 事项 | 归属阶段 |
|---|---|
| 真实大模型 / ASR / TTS 提供商接入与选型 | 前置决策清单 #2/#3（第 8 周前，实际未拍板）；接入归提供商拍板后（D27/D28） |
| 非资金写纳入 AI 可执行范围（规则/任务 CRUD、工作价值记录） | 待 M4 v1 意图收敛验证后再评估（D29） |
| 真实语音文件采集 / 传输 / 存储 | 不实现（隐私约束：语音不落盘，D35）；M4 以文本输入桩驱动链路 |
| Embedding 语义检索真实应用（规则/指令语义匹配） | 后续里程碑（D38 仅抽象预留） |
| 多轮复杂对话记忆 / 上下文管理 | 不实现（单轮指令 + 待确认动作为主） |
| 通知推送（AI 操作结果推送） | M5 |
| JMeter 10 TPS 压测（AI 链路） | M6（M4 以本地基准 + 降级演练落地） |
| 真实 LLM 准确率复跑（≥95%） | 提供商接入后复跑（R2 遗留） |

---

## 2. 决策记录（已确认）

> D27–D29 经 AskUserQuestion 确认（LLM 提供商/ASR-TTS/操作范围三问）；D30–D38 为本设计新增决策。续 M3 D26 编号。

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D27 | LLM 提供商与抽象层 | **暂不定，仅出项目自有抽象** `ChatPort`（chat + Function Calling）；默认确定性 `StubChatPort`（零新依赖）；生产通道按 tech-stack 定为 Spring AI 适配器 `SpringAiChatPort`，提供商拍板后接入 | 镜像 M1 OAuth2「仅结构预留」；Spring AI 2.0.0/Boot 4.1.0 兼容性 spike 记入 version-matrix（§4.1） |
| D28 | ASR / TTS | **暂不定，仅抽象接口** `SpeechToTextPort` / `TextToSpeechPort`；M4 语音链路以文本输入桩驱动（`channel=TEXT` 直进 NLU）；「语音不落盘」约束写入接口契约 javadoc | 真实接入归提供商拍板后；隐私约束 D35 |
| D29 | AI 可执行操作范围 | **查询 + 资金写（二次确认）全链路**：9 类查询（余额/流水/看板/趋势/榜单/规则/任务/工作价值/统计）+ 2 类资金写（存入/提取）；非资金写 M4 不纳入；兜底收窄到仅查询（roadmap 风险应对） | §5.1 意图目录 |
| D30 | 意图解析机制 | **Function Calling 为主**（模型产出结构化参数）+ 本地 `IntentCatalog` 枚举与参数 schema 校验兜底；模型不得直接触碰 mapper，参数必过 service 层 | 封闭意图集使确定性路由可行（§5） |
| D31 | 二次确认机制 | 会话级待确认动作 `ai_pending_action`（参数快照 + TTL 60s）；状态机 PENDING→EXECUTED/REJECTED/CANCELED/EXPIRED；确认执行与业务同事务；资金写复用 `MoneyOperationService` 并以会话操作 ID 回填 `requestId` | 承接 M3 幂等基建（§6） |
| D32 | 准确性落地 | **AI 不得自由生成余额/金额/状态**：所有数据经既有 service 层实时查询，LLM 仅转述工具返回的真实数据；prompt 强约束「无工具结果不得断言数值」 | mission 约束 1（§7.1） |
| D33 | 一致性落地 | 规则语义解释由 rule 服务既有确定性字段/文案承载（`RuleService.detail/list` 返回内容），AI 不即兴生成规则含义 | mission 约束 3（§7.3） |
| D34 | 可解释性落地 | `ai_session`/`ai_message`（`tool_call_json`）落「意图→参数→调用链→结果」全程；`AuditAction` 增补 AI 动作段 8 项 | mission 约束 4（§7.4） |
| D35 | 隐私落地 | 语音不落盘（无音频列/文件存储，接口契约固化）；会话/消息/待确认动作按 TTL 定期清理任务（复用 `SchedulingConfig`） | mission 约束 5（§7.5） |
| D36 | 稳定性落地 | **真正落地** Resilience4j TimeLimiter(30s)/CircuitBreaker/Retry（M3 仅配置骨架未实现）；AI 调用限流 `AiRateLimitService`（复用 `RateLimitService` 模式）；降级 → 600001 客户端回落手动操作；Retry 仅限幂等查询类 LLM 调用 | tech-stack AI 超时/限流/降级（§8） |
| D37 | 错误码 | `AIIErrorCode` 60xxxx 段：600001 AI 不可用/降级、600002 意图无法识别、600003 待确认动作不存在或已过期、600004 已有未完成待确认操作 | 与 `CommonErrorCode` javadoc 预留段一致（§9.1） |
| D38 | Embedding | 与 D27 同源仅 `EmbeddingPort` 抽象预留；语义匹配应用点（规则/指令语义检索）列为 M4 非目标 | tech-stack「Embedding 库 1.0」暂不出真实实现（§4.2） |

---

## 3. 总体设计

### 3.1 请求处理链路（M4 结束时）

```
鸿蒙 APP
  │ HTTPS / TLS 1.3
  ▼
TraceIdFilter → Spring Security → JwtAuthenticationFilter → UserIdPrincipal（M1 链路不变）
  ▼
IdempotencyFilter（AI 写端点 POST /chat、/actions/{id}/confirm、/cancel 走统一幂等键协议，M3 不变）
  ▼
RequestTimingFilter（每端点计时，>500ms WARN + traceId，M3 不变）
  ▼
AiController（@PreAuthorize 仅要求 authenticated：家长/孩子均可）
  ▼
AiRateLimitService.tryAcquire(userId)（AI 维度限流，耗尽 → 100007 + Retry-After）
  ▼
AiOrchestrator.answer(principal, text)
  ├─ 1. 记录 USER 消息（ai_message role=USER）
  ├─ 2. ChatPort.parseIntent(text, toolDefs)（StubChatPort 确定性路由；外包 Resilience4j CB/TimeLimiter/Retry）
  ├─ 3. IntentCatalog 校验意图 + 参数 schema
  ├─ 4. 查询意图 → AiToolRegistry 执行（调既有 service）→ 真实数据 → 组装回复
  └─ 5. 资金写意图 → 角色权限校验 → 生成 ai_pending_action(PENDING, TTL 60s) → 回复确认话术
  ▼
confirm 端点 → PendingActionService.confirm → MoneyOperationService.deposit/withdraw（同事务 + requestId 回填）
  ▼
AiSessionService 记录 ASSISTANT 消息 + AuditService 记录 AI 动作
  ▼
Mapper（MyBatis #{}）→ PostgreSQL

定时任务（@Scheduled，虚拟线程，复用 SchedulingConfig）
  └─ AiCleanupJob：每日清理过期会话/消息/待确认动作（D35）
```

### 3.2 包结构增量（在 M3 骨架上生长）

```
src/main/java/wyq/pocket/money/
├── common/
│   ├── ai/                               # 【新增】AI 抽象层（纯契约，无业务依赖）
│   │   ├── ChatPort.java                 # 对话 + Function Calling 端口（§4.2）
│   │   ├── EmbeddingPort.java            # 向量化端口（预留，D38）
│   │   ├── SpeechToTextPort.java         # 语音识别端口（预留，D28，javadoc 固化「不落盘」）
│   │   ├── TextToSpeechPort.java         # 语音合成端口（预留，D28）
│   │   ├── StubChatPort.java             # 确定性意图路由桩（默认 Bean）
│   │   ├── ToolDefinition.java           # 工具定义（名称/描述/参数 schema）
│   │   ├── IntentResult.java             # 解析结果（工具名 + 原始参数 + 意图）
│   │   ├── AiProperties.java             # pocket-money.ai 配置
│   │   └── AiConfig.java                 # 端口 Bean 装配（@ConditionalOnMissingBean）
│   ├── resilience/
│   │   ├── ResilienceConfig.java         # 【修改】真正注册 aiCircuitBreaker/aiTimeLimiter/aiRetry（§8.1）
│   │   └── ResilienceProperties.java     # 【修改】+ ai 段配置
│   └── audit/AuditAction.java            # 【修改】+ AI 动作段（§9.2）
├── ai/                                   # 【填充】AI 模块（M4 实现）
│   ├── controller/AiController.java      # POST /chat、/actions/{id}/confirm、/cancel；GET 会话历史
│   ├── service/AiOrchestrator.java       # 编排：意图解析 → 分支执行（§5.3/§6）
│   ├── service/IntentCatalog.java        # 封闭意图枚举 + 参数 schema 校验（§5.1/§5.2）
│   ├── service/AiToolRegistry.java       # 意图 → 工具（执行业务 service，§5.3）
│   ├── service/PendingActionService.java # 二次确认状态机（§6）
│   ├── service/AiSessionService.java     # 会话/消息落库与读取
│   ├── service/AiRateLimitService.java   # AI 调用限流（复用 RateLimitService 模式，§8.2）
│   ├── domain/AiSession.java / AiMessage.java / AiPendingAction.java / AiIntent.java
│   ├── mapper/AiSessionMapper.java / AiMessageMapper.java / AiPendingActionMapper.java
│   ├── dto/AiChatRequest.java / AiChatResponse.java / AiConfirmResponse.java / AiErrorCode.java
│   ├── eval/AiAccuracyEvaluator.java     # 评测集 + 评测器（§10.3）
│   └── job/AiCleanupJob.java             # 会话/消息/待确认动作清理（D35）

src/main/resources/db/migration/
└── V8__create_ai_tables.sql              # 【新增】ai_session/ai_message/ai_pending_action（§11.1）
```

### 3.3 与 M3 基线的衔接

| M3 交付物 | M4 变更 |
|---|---|
| `MoneyOperationService.deposit/withdraw`（经 `FamilyAccessChecker.requireMember` + `requireSelfIfChild` + `IdempotencyContext.currentKey()` 回填 `requestId`） | AI 资金写工具**直接复用**，不新写记账路径（§5.3） |
| `MoneyQueryService.page/totalBalance/accountTotals/sumByDirectionSince`、`AccountService.find/requireAccount`、`DashboardService.getDashboard`、`TrendService.trend`、`LeaderboardService.leaderboard`、`RuleService.list/detail`、`LearningTaskService.list`、`WorkValueService.list`、`ReportService.statistics` | AI 查询工具**直接复用**，全部以会话绑定的 `UserIdPrincipal` 为身份入参 |
| `common/resilience/ResilienceConfig`（仅 `@EnableConfigurationProperties`，CB/TimeLimiter/Retry **未落地**） | **真正注册** aiCircuitBreaker/aiTimeLimiter/aiRetry（§8.1）；M3 设计 §7.3 承诺的骨架在此兑现 |
| `RateLimitService`（每用户令牌桶，非阻塞 `tryAcquire`） | 复用其模式新增 `AiRateLimitService`（§8.2） |
| `IdempotencyFilter`/`IdempotencyContext`（M3 全写端点幂等） | AI 写端点自动纳入；资金写执行以**会话操作 ID** 回填 `requestId`（§6.3） |
| `CommonErrorCode`（javadoc 预留 AI 60xxxx / 通知 70xxxx） | 新 `AIIErrorCode` 落 60xxxx 段，不侵入 CommonErrorCode（§9.1） |
| `AuditAction`（16+12 动作） / `AuditService.record(REQUIRES_NEW)` | 增补 AI 动作段 8 项（§9.2） |
| `SchedulingConfig`（虚拟线程 TaskScheduler）、`ClockConfig`、`Result`、`GlobalExceptionHandler`、`TraceIds` | 原样复用；`GlobalExceptionHandler` 增补 Resilience4j AI 异常映射（§8.3） |

> **M3 实现差异勘误**：M3 设计 §7.3 描述「`Resilience4jConfig` 预注册 CircuitBreaker/TimeLimiter/Retry」，实际代码仅 `@EnableConfigurationProperties`（仅 RateLimiter 落地）。本设计 §8.1 以「真正注册」为口径补齐，不假设已就绪。

### 3.4 模块依赖方向（ArchUnit 规则增补）

- `common/ai` 属 common 层：仅依赖 `common/web`（错误码）、`common/security`（principal）；`StubChatPort` 自包含（接收 `ToolDefinition` 入参，**不依赖任何业务模块**），避免 common→business 反向依赖
- `ai` 模块依赖 `common/ai`（端口）、`common/audit`、`common/idempotency`、`common/resilience`，以及 `money/rule/finance/user` 的 **service 层**；`ai` 不得触碰各模块的 mapper/domain
- `ArchitectureTest` 增补：`common/ai` 不得依赖 money/rule/finance/user/ai 包；`ai` 不得依赖各业务模块的 mapper 包

---

## 4. AI 抽象层（D27/D28/D38）

### 4.1 Spring AI 兼容性 spike

- 目标版本 `spring-ai` 2.0.0（tech-stack §6）；按 M0/M1 spike 流程验证其对 Boot 4.1.0 的兼容性与实际发布状态，结论记入 `docs/version-matrix.md`
- **回退预案**：2.0.0 不存在/不兼容 → 生产适配器 `SpringAiChatPort` 改用最新稳定版 Spring AI（1.0.x）或直接以 provider SDK 实现 `ChatPort`，端口契约不变，业务零感知
- 关键：**M4 默认构建不引入 Spring AI 依赖**——`ChatPort` 为项目自有接口，默认 `StubChatPort` 零新依赖；Spring AI 仅在提供商拍板后作为适配器实现随 pom 引入（镜像 M1 OAuth2「仅结构预留」）

### 4.2 端口契约

```java
/** 对话 + Function Calling 端口。实现方：StubChatPort（默认）/ SpringAiChatPort（生产，后置）。 */
public interface ChatPort {
    /** 解析用户指令为结构化意图。返回工具名 + 原始参数（未做业务解析）。 */
    IntentResult parseIntent(String userText, List<ToolDefinition> tools);
}

/** 文本向量化端口（预留，D38；M4 无真实实现）。 */
public interface EmbeddingPort { /* 向量化契约，后续里程碑填充 */ }

/**
 * 语音识别端口（预留，D28）。
 * <p>约束（mission 隐私）：音频数据不落盘持久化、不写入任何表/文件，仅驻留内存完成转写。
 */
public interface SpeechToTextPort { String transcribe(byte[] audio); }

/** 语音合成端口（预留，D28）。 */
public interface TextToSpeechPort { byte[] synthesize(String text); }
```

- `ToolDefinition`：`{name, description, paramsSchema}` —— 意图目录中的每个意图对应一条工具定义（§5.2），由 `AiOrchestrator` 在每次解析时组装传入
- `IntentResult`：`{toolName, rawParams(Map<String,String>), confidence}` —— 原始参数由 `AiOrchestrator` 做业务解析（成员名→userId、金额 schema 校验）
- `AiConfig`：`@ConditionalOnMissingBean(ChatPort.class)` 注册 `StubChatPort`（`ai.mock=true` 默认）；`EmbeddingPort`/`SpeechToTextPort`/`TextToSpeechPort` 不注册 Bean（仅契约，真实接入后装配）

### 4.3 StubChatPort（确定性意图路由桩）

- 自包含的确定性解析器：对 `ToolDefinition` 列表做关键词/模式匹配（如「余额」「流水」「存入」「提取」→ 对应工具名），金额/成员等以正则抽取为原始参数字符串
- 无网络、无外部依赖、可复现 —— 支撑单测、H2/PG 集成测试与评测集（stub 下封闭意图集准确率确定性 100%）
- 提供可注入的故障开关（`ai.stub.fail=true` 或 `FailingChatPort` 测试桩）用于降级演练（§8.3/§10.2）
- **业务解析边界**：`StubChatPort` 只产出「原始参数」（如金额字符串「50」、成员名「小明」），不解析 userId/账号 —— 该解析在 `AiOrchestrator` 经 `FamilyAccessChecker`/`FamilyService` 完成，保持 common 层纯净（§3.4）

---

## 5. 意图理解与工具注册（D29/D30）

### 5.1 封闭意图目录（IntentCatalog）

| 意图码 | 自然语言示例 | 类型 | 目标 service 方法 | 二次确认 |
|---|---|---|---|---|
| BALANCE_QUERY | 「查一下余额」「还有多少钱」 | 查询 | `MoneyQueryService.totalBalance` + `AccountService.find/accountTotals` | 否 |
| TRANSACTION_QUERY | 「看看流水」「最近花销」 | 查询 | `MoneyQueryService.page` | 否 |
| DASHBOARD | 「家庭看板」 | 查询 | `DashboardService.getDashboard` | 否 |
| TREND | 「这个月趋势」 | 查询 | `TrendService.trend` | 否 |
| LEADERBOARD | 「排行榜」 | 查询 | `LeaderboardService.leaderboard` | 否 |
| RULE_QUERY | 「有哪些规则」「规则详情」 | 查询 | `RuleService.list/detail` | 否 |
| TASK_QUERY | 「学习任务列表」 | 查询 | `LearningTaskService.list` | 否 |
| WORK_VALUE_QUERY | 「工作价值记录」 | 查询 | `WorkValueService.list` | 否 |
| STATISTICS_QUERY | 「本月统计」 | 查询 | `ReportService.statistics` | 否 |
| DEPOSIT | 「给小明存 50」「存 50」 | 资金写 | `MoneyOperationService.deposit` | **是** |
| WITHDRAW | 「取 20」「提现 20」 | 资金写 | `MoneyOperationService.withdraw` | **是** |

- 非资金写（规则/任务 CRUD、工作价值记录）**不纳入**（D29）；意图收敛到有限闭集是「准确率 ≥95%」与「确定性路由」的共同前提
- 兜底：若真实 LLM 接入后准确率不达标，收窄至仅查询（roadmap §5 风险应对），资金写退化为手动入口

### 5.2 参数 schema 与校验

- 每个意图定义参数 schema（`ToolDefinition.paramsSchema`）：查询类参数为可选项（`userId` 目标成员、`scope`、`month` 等）；资金写类为必填（`targetUserName` 或 `targetUserId`、`amount`、`remark` 可选）
- `IntentCatalog.validate(intent, rawParams)`：金额为正数 DECIMAL 校验、成员存在性校验、必填项完备性校验；失败 → 600002（意图无法识别）或 100001（参数校验失败）
- **模型不直接触碰 mapper**：所有参数经 service 层解析与执行（D30）

### 5.3 工具执行（AiToolRegistry）

- 每个意图注册一个 `AiTool`（函数式接口）：`Object execute(UserIdPrincipal principal, Map<String,String> rawParams)`
- **安全关键**：工具以**会话绑定的 `UserIdPrincipal`** 为身份执行，**不接受模型提供的任意 userId**；成员名→userId 由 `AiOrchestrator` 经 `FamilyService`/`FamilyAccessChecker` 解析，`FamilyAccessChecker.requireMember(familyId, targetUserId)` + `requireSelfIfChild` 自然继承（CHILD 仅能操作本人账户，越权 100004）
- 查询工具返回值即真实数据（DTO），`AiOrchestrator` 据此组装自然语言回复（stub 用模板；真实 provider 由 LLM 转述，D32）
- 资金写工具不直接执行：产出待确认动作参数快照，转 §6 流程

---

## 6. Function Calling 编排与二次确认（D31）

### 6.1 AiOrchestrator 主流程

```
answer(principal, text)
  1. 记录 USER 消息（ai_message role=USER，content=text）
  2. 组装 ToolDefinition 列表（来自 IntentCatalog）
  3. ChatPort.parseIntent(text, tools)（外包 CB/TimeLimiter/Retry，§8）
     → IntentResult{toolName, rawParams}
     失败/超时/熔断 → 600001；意图不识别 → 600002
  4. IntentCatalog.validate + 业务解析（成员名→userId）
  5. 分支：
     查询意图 → AiToolRegistry.execute → 真实数据 → 组装回复
     资金写意图 → §6.2 二次确认
  6. 记录 ASSISTANT 消息 + 审计 AI_INTENT（含意图码/参数/工具名）
  7. 返回 AiChatResponse{reply, pendingActionId?}
```

### 6.2 二次确认状态机（ai_pending_action）

```
资金写指令（已解析 targetUserId/amount/remark）
  → 权限预校验（CHILD 仅本人账户，越权 → 100004，不产生 pending）
  → 生成 ai_pending_action(PENDING, params 快照, TTL 60s)
  → 回复「确认给 小明 存入 50.00 元？」（附 pendingActionId）
  → 审计 AI_ACTION_CONFIRM_REQUEST

客户端 POST /ai/actions/{id}/confirm
  → 校验：存在、属当前用户、status=PENDING、未过期（缺失/过期 → 600003）
  → 执行：MoneyOperationService.deposit/withdraw（与业务同事务；requestId 回填，§6.3）
  → 成功：status=EXECUTED；审计 AI_ACTION_EXECUTED + 既有 MONEY_DEPOSIT/MONEY_WITHDRAW
  → 业务失败（如余额不足 300001）：status=REJECTED；审计 AI_ACTION_REJECTED；回复对应业务错误码
  → 回复执行结果（transactionId/balanceAfter）

客户端 POST /ai/actions/{id}/cancel
  → status=CANCELED；审计 AI_ACTION_CANCELED；回复「已取消」

超时（AiCleanupJob 扫描 status=PENDING 且 expires_at < now）
  → status=EXPIRED；审计 AI_ACTION_EXPIRED

新资金写指令时若已存在同会话 PENDING → 600004（先确认/取消再发起）
```

- 状态：`PENDING / EXECUTED / REJECTED / CANCELED / EXPIRED`；`EXECUTED`/`REJECTED`/`CANCELED`/`EXPIRED` 为终态，confirm 与执行同事务（不引入瞬时中间态）
- TTL 60s 可配（`AiProperties.pending-ttl`）

### 6.3 幂等衔接（承接 M3）

- AI 写端点（`POST /chat`、`/confirm`、`/cancel`）经 M3 `IdempotencyFilter` 统一幂等键协议
- 资金写执行时，以**会话操作 ID**（`sessionId + '-' + pendingActionId` 或独立 `requestId`）经 `IdempotencyContext` 回填 `TxCommand.requestId` → `uk_mtxn_request` 账务级兜底：确认操作被重复提交也不会产生两笔流水（同 M3 §5.4 第二道防线）

---

## 7. 五项可信度机制映射（mission §AI 交互可信度约束）

### 7.1 准确性（D32）

| mission 约束 | 落地机制 |
|---|---|
| 响应基于实际账户数据，不捏造余额 | 所有数据经 `AiToolRegistry` 调既有 service 实时查询；`StubChatPort`/prompt 模板无自由数值生成；LLM 仅转述工具返回数据 |
| 交易操作实时确认 | 资金写经二次确认 + 同事务执行 + `uk_mtxn_request` 幂等兜底 |
| 建议符合家庭规则 | 规则内容仅来自 `RuleService` 确定性字段（D33） |
| 准确率 ≥95% | `AiAccuracyEvaluator` 评测集（§10.3）；stub 下封闭意图集确定性 100%，真实 LLM 达标待接入复跑（R2） |

### 7.2 安全性（D29/D31 承接）

- AI 端点强制认证（JWT → `UserIdPrincipal`）；未认证请求被 Security 链拒绝
- 工具执行权限 = 既有接口权限：`FamilyAccessChecker.requireMember` + `requireSelfIfChild`（CHILD 仅本人账户），不接受模型提供的任意 userId
- 资金写语音指令强制二次确认（D31），防止误操作

### 7.3 一致性（D33）

- 规则解释文案由 rule 服务既有确定性字段/文案承载（`RuleService.detail`/`list` 的返回内容），AI **不即兴生成**规则含义
- 任何财务变动实时反映：资金写走 `AccountTransactionService`，余额实时更新，查询工具即时读到新值

### 7.4 可解释性（D34）

- `ai_message.tool_call_json` 落「意图 → 参数 → 工具调用 → 结果」全程调用链
- `AuditAction` AI 动作段 + `AuditService`（REQUIRES_NEW）独立落审计
- `GET /ai/sessions/{id}/messages` 可检索任一会话执行路径（DoD「审计可完整追溯」支撑）

### 7.5 隐私（D35）

- 语音不落盘：无音频列、无文件存储；`SpeechToTextPort` javadoc 固化「音频仅驻留内存，不持久化」
- 会话/消息/待确认动作按 TTL 定期清理（`AiCleanupJob`，每日执行）
- 会话内容加密传输（TLS 1.3，既有链路）；敏感字段日志脱敏（既有 `MaskingJsonEncoder`）

---

## 8. 稳定性设计（D36）

### 8.1 Resilience4j 真正落地

- `ResilienceConfig` 由空壳改为注册三个实例（`@ConfigurationProperties` 前缀 `pocket-money.resilience.ai`）：
  - `aiCircuitBreaker`：滑动窗口 10、失败率阈值 50%、开启后半开探测
  - `aiTimeLimiter`：30s（对齐 tech-stack「AI 调用超时默认 30 秒」）
  - `aiRetry`：最多 2 次、指数退避，**仅限幂等查询类 LLM 调用**（`parseIntent` 属只读，可安全重试；资金执行不套用，D25 语义一致）
- `AiOrchestrator` 以 `CircuitBreaker.decorateCheckedSupplier(TimeLimiter.decorate(Retry.decorate(...)))` 包裹 `ChatPort.parseIntent` 调用
- M4 以 stub + 单测验证配置与回退语义；真实 provider 接入后自动生效（§4.1）

### 8.2 AI 调用限流

- `AiRateLimitService`：每用户令牌桶（`RateLimiter`，`ConcurrentHashMap<Long, RateLimiter>`），复用 `RateLimitService` 的 `timeoutDuration=ZERO` 非阻塞模式
- 配置 `pocket-money.ai.rate-limit`（默认 10 次/60s/用户）；耗尽 → 复用 `CommonErrorCode.RATE_LIMITED`(100007) + `Retry-After`（`GlobalExceptionHandler` 既有映射）

### 8.3 降级

- ChatPort 异常/超时/熔断 → `AIIErrorCode.AI_UNAVAILABLE`(600001)，message 提示客户端回落手动操作入口
- **降级演练**：以 `FailingChatPort` 测试桩（或 `ai.stub.fail=true` 开关）模拟 provider 不可用 → 断言 600001 + 核心（非 AI）端点不受影响（非 AI 端点不触碰 ChatPort，天然隔离）
- `GlobalExceptionHandler` 增补映射：`CallNotPermittedException`/`RequestNotPermitted`（熔断）→ 600001、`TimeoutException` → 600001（AI 段统一 600001，区分于下游通用 900002）

---

## 9. 错误码与审计

### 9.1 AIIErrorCode（60xxxx 段）

| 错误码 | 含义 | 客户端处理建议 | retryable |
|---|---|---|---|
| 600001 | AI 服务不可用（降级/超时/熔断） | 回落手动操作入口 | 否（用户改走手动） |
| 600002 | 意图无法识别 | 重新表述指令 | 否 |
| 600003 | 待确认动作不存在或已过期 | 重新发起指令 | 否 |
| 600004 | 已有未完成待确认操作 | 先确认/取消再发起新指令 | 否 |

- 落 `ai/dto/AiErrorCode.java` 实现 `ErrorCode`；`isRetryable()` 继承默认（60 段不可重试），经 `AiErrorCodeTest` 段位与唯一性单测
- 段位 60xxxx 与 `CommonErrorCode` javadoc 预留一致（「AI 60xxxx（M4）」）

### 9.2 AuditAction 增补（AI 动作段）

| 动作 | 含义 |
|---|---|
| AI_SESSION_START | AI 会话创建 |
| AI_INTENT | 意图解析成功（意图码 + 参数 + 工具名） |
| AI_ACTION_CONFIRM_REQUEST | 资金写待确认动作生成 |
| AI_ACTION_EXECUTED | 待确认动作确认执行成功 |
| AI_ACTION_REJECTED | 待确认动作执行被拒（余额不足等业务失败） |
| AI_ACTION_CANCELED | 待确认动作取消 |
| AI_ACTION_EXPIRED | 待确认动作超时过期 |
| AI_DEGRADED | AI 降级（600001 触发） |

- 全部名称 ≤ 48 字符（`audit_log.action` VARCHAR(48) 约束）
- 资金写执行成功仍复用既有 `MONEY_DEPOSIT`/`MONEY_WITHDRAW`（账务侧动作不变），AI 动作段负责「AI 特有链路」的可追溯

---

## 10. 数据模型与迁移（V8）

### 10.1 V8__create_ai_tables.sql

```sql
CREATE TABLE ai_session (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES app_user (id),
    family_id      BIGINT NOT NULL REFERENCES family (id),
    channel        VARCHAR(8) NOT NULL DEFAULT 'TEXT',   -- TEXT / VOICE
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',-- ACTIVE / CLOSED
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_session_user ON ai_session (user_id, last_active_at);

CREATE TABLE ai_message (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES ai_session (id),
    role          VARCHAR(16) NOT NULL,        -- USER / ASSISTANT / SYSTEM
    content       TEXT,                        -- 用户原文 / AI 回复文本
    intent        VARCHAR(32),                 -- 意图码（可空）
    tool_call_json JSONB,                      -- 意图→参数→工具调用→结果 调用链
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_message_session ON ai_message (session_id, created_at);

CREATE TABLE ai_pending_action (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT NOT NULL REFERENCES ai_session (id),
    user_id      BIGINT NOT NULL REFERENCES app_user (id),
    intent       VARCHAR(32) NOT NULL,          -- DEPOSIT / WITHDRAW
    params_json  JSONB NOT NULL,                -- {targetUserId, amount, remark} 快照
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                                               -- PENDING/EXECUTED/REJECTED/CANCELED/EXPIRED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    executed_at  TIMESTAMPTZ
);
CREATE INDEX idx_ai_pending_status ON ai_pending_action (status, expires_at);
CREATE INDEX idx_ai_pending_session ON ai_pending_action (session_id);
```

- `tool_call_json` 用 JSONB（PG 原生 JSON，M0 约束），落调用链供可解释性追溯（§7.4）
- 迁移脚本一经提交永不修改，回滚走新脚本前向修复（M0 既定规范）

### 10.2 清理任务（AiCleanupJob）

- 每日执行（`@Scheduled` + `SchedulingConfig` 虚拟线程），`@ConditionalOnProperty(pocket-money.ai.cleanup-enabled)` 控制启停（同 M2 结算任务模式）
- 清理范围：`status=EXPIRED/CANCELED/EXECUTED` 且超 TTL 的 pending action、超会话 TTL 的 `ai_session`（级联消息）；`idx_ai_pending_status`/`idx_ai_session_user` 支撑扫描

---

## 11. 测试设计

### 11.1 单元测试

| 测试类 | 覆盖点 |
|---|---|
| StubChatPortTest | 确定性路由：11 意图关键词/模式匹配、金额与成员原始参数抽取、未知输入 → 意图不识别 |
| IntentCatalogTest | 封闭意图枚举完备、参数 schema 校验（金额正数/必填项/成员存在）、非法参数 600002/100001 |
| AiOrchestratorTest | 查询意图直行、资金写意图生成 pending、CHILD 越权 100004、parseIntent 失败 600001、意图不识别 600002 |
| PendingActionServiceTest | 状态机全迁移：confirm 成功执行/过期 600003/取消/重复 confirm 600003/同会话新指令 600004/EXECUTED 后不可再确认 |
| AiErrorCodeTest | 600001–600004 段位 60xxxx、无重复值、`isRetryable()`=false |
| ResilienceConfigTest | aiCircuitBreaker/aiTimeLimiter/aiRetry 实例注册、超时/熔断回退语义 |
| AuditActionTest（增补） | AI 动作段名称 ≤48 字符、与既有动作无重复 |

### 11.2 集成测试套件（RestAssured + Testcontainers PG，沿用 M1 形态；H2 本地 + PG 双形态）

| 套件 | 场景 |
|---|---|
| AiChatH2IntegrationTest | 全链路「查余额→回复真实数据」；「存入→pending→confirm→恰好一笔流水」；「存入→cancel→零流水」 |
| AiPendingActionPgIntegrationTest | confirm 超时过期 600003；同会话第二笔资金写 600004；重复 confirm 幂等（仅一笔流水，`uk_mtxn_request` 兜底） |
| AiSecurityPgIntegrationTest | 未认证请求拒绝；CHILD 越权目标他人账户 → 100004；模型参数不可指定任意 userId |
| AiDegradationPgIntegrationTest | `FailingChatPort` 模拟 provider 不可用 → 600001；非 AI 端点（看板/流水）不受影响 |
| AiAuditPgIntegrationTest | 任一会话执行路径可经 `ai_message` + `audit_log` 完整追溯 |
| 既有套件回归 | M2/M3 套件全量回归通过（AI 不侵入既有链路） |

### 11.3 评测集与评测器（AiAccuracyEvaluator）

- `ai/eval` 下：golden 评测集（每条 = 自然语言指令 + 期望意图码 + 期望参数），覆盖 11 意图 × 常见表述变体（如「查余额/还有多少钱/我还剩多少」）
- `AiAccuracyEvaluator`：provider 无关，注入任一 `ChatPort` 跑全集 → 计算意图准确率 + 参数准确率 → 报告存档（`docs/eval/`）
- stub 下封闭意图集确定性 100%（作为评测框架与基线验证）；**真实 LLM ≥95% 达标待提供商接入后复跑**（R2，report 口径明示）

### 11.4 覆盖率与门禁

JaCoCo 80% BUNDLE 门禁沿用；`common/ai`、`ai` 模块新增类全部计入；`AiAccuracyEvaluator`（评测工具）与 `StubChatPort`（默认实现）计入主代码，不豁免。

---

## 12. 配置增量

```yaml
pocket-money:
  ai:
    mock: ${AI_MOCK:true}                       # 默认 StubChatPort；false 需装配真实 ChatPort
    channel-default: ${AI_CHANNEL_DEFAULT:TEXT}  # TEXT / VOICE（M4 仅 TEXT）
    pending-ttl: ${AI_PENDING_TTL:PT60S}         # 待确认动作 TTL
    session-ttl: ${AI_SESSION_TTL:P7D}           # 会话/消息保留期
    cleanup-enabled: ${AI_CLEANUP_ENABLED:true}
    cleanup-cron: ${AI_CLEANUP_CRON:0 43 4 * * *}  # 每日 04:43
    rate-limit:
      limit-for-period: ${AI_RATE_LIMIT_FOR_PERIOD:10}
      limit-refresh-period: ${AI_RATE_LIMIT_REFRESH_PERIOD:PT1M}
    stub:
      fail: ${AI_STUB_FAIL:false}                # 降级演练开关（模拟 provider 不可用）
  resilience:
    ai:
      timeout: ${AI_TIMEOUT:30s}                 # 对齐 tech-stack AI 调用超时
      circuit-breaker-failure-rate: ${AI_CB_FAILURE_RATE:50}
      circuit-breaker-sliding-window: ${AI_CB_SLIDING_WINDOW:10}
      retry-max-attempts: ${AI_RETRY_MAX_ATTEMPTS:2}
```

- `AiProperties`（`@ConfigurationProperties` 前缀 `pocket-money.ai`）、`ResilienceProperties` 增补 `ai` 段
- 全部经环境变量注入，无硬编码（mission 禁止项）

---

## 13. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 |
|---|---|---|---|
| T1 | AI 抽象层 + Spring AI 兼容性 spike（四端口、StubChatPort、AiProperties/AiConfig、version-matrix 记录） | — | 2 人天 |
| T2 | 意图目录 + 工具注册（IntentCatalog 11 意图、参数 schema、AiToolRegistry、StubChatPort 路由表） | T1 | 2 人天 |
| T3 | 编排器 + 会话 + 待确认动作（AiOrchestrator、V8 迁移、AiSessionService、PendingActionService 状态机、AiCleanupJob） | T2 | 3.5 人天 |
| T4 | 稳定性落地（ResilienceConfig 注册 CB/TimeLimiter/Retry、AiRateLimitService、600001 降级、异常映射） | T2 | 2 人天 |
| T5 | 审计 + 错误码（AuditAction AI 段、AIIErrorCode、AiErrorCodeTest） | T2 | 1.5 人天 |
| T6 | 评测集 + 评测器（golden 集、AiAccuracyEvaluator、报告存档） | T2 | 1.5 人天 |
| T7 | 集成测试全集 + DoD 验证收尾（§11.2 套件 + 既有套件回归） | T3–T6 | 3.5 人天 |

合计约 **16 人天**。roadmap 排期 4 周（20 工作日/人）：

- **2 人投入**：约 8 人天/人，舒适（推荐，账务类 AI 操作强制双人评审）
- **1 人投入**：16 人天 < 20 人天，可行但无缓冲；候选裁剪项：EmbeddingPort/SpeechToTextPort/TextToSpeechPort 降为纯文档、评测集收窄至查询类意图

关键路径：T1 → T2 → T3/T4/T5 并行；T7 为收尾闸门。

---

## 14. 验收标准（DoD，与 roadmap 一致并细化）

- [ ] 语音指令「查询余额 → 二次确认 → 执行 → 结果反馈」全链路演示通过（AiChatH2IntegrationTest + 演示脚本；M4 以文本通道驱动，语音通道经 `SpeechToTextPort`/`TextToSpeechPort` 抽象就绪）
- [ ] AI 评测集准确率达标：评测框架 + golden 集就绪，stub 下 100%（确定性）；**真实 LLM ≥95% 报告待提供商接入后复跑存档**（R2 口径明示）
- [ ] AI 服务不可用时系统核心功能不受影响（AiDegradationPgIntegrationTest：600001 + 非 AI 端点正常，降级演练通过）
- [ ] AI 操作审计日志可完整追溯任一会话执行路径（AiAuditPgIntegrationTest：ai_message.tool_call_json + audit_log AI 动作段）
- [ ] 语音数据持久化检查为「零残留」（无音频列/文件；AiCleanupJob 清理测试通过）
- [ ] 单测覆盖率 ≥ 80%（JaCoCo）；`mvn clean verify` 全门禁绿（Checkstyle/PMD/SpotBugs，SonarQube 归 M6）
- [ ] 既有 M2/M3 集成测试套件全量回归通过

---

## 15. 风险与遗留事项

| # | 风险/事项 | 影响 | 应对 |
|---|---|---|---|
| R1 | Spring AI 2.0.0 不存在或未适配 Boot 4.1.0 | 生产适配器接入受阻 | 兼容性 spike 前置（§4.1）；回退最新稳定版或 provider SDK 实现 `ChatPort`，契约不变；结论记入 version-matrix |
| R2 | 真实 LLM 准确率/降级演练无法在 M4 验证（provider 未定） | 准确率 DoD、真实降级演练后置 | 以确定性 stub（封闭意图集 100%）托底评测框架；真实 ≥95% 与真实降级归提供商接入后复跑（report 口径明示，§10.3/§14） |
| R3 | 意图收敛不足 / 模型自由发挥导致误操作 | 资金误操作 | 封闭意图目录 + 本地 IntentCatalog 校验兜底 + 资金写二次确认 + `uk_mtxn_request` 幂等；兜底收窄至仅查询（roadmap 风险应对） |
| R4 | 待确认动作残留（用户不确认也不取消） | 表膨胀 | TTL 60s + `AiCleanupJob` 每日清理 + `idx_ai_pending_status` 扫描 |
| R5 | 儿童隐私合规（语音/会话数据） | 法律风险 | 语音不落盘、数据最小化、会话 TTL 清理（D35）；COPPA 类条款在 M6 合规自查复核 |
| R6 | 单用户 AI 滥用（高频调用消耗 token/额度） | 成本失控 | `AiRateLimitService` 每用户限流（§8.2）；真实 provider 计费方案随选型评审 |

遗留至后续阶段：真实 LLM/ASR/TTS 接入与准确率复跑（提供商拍板后）、Embedding 真实应用（后续）、AI 操作结果通知（M5）、JMeter 10 TPS 压测含 AI 链路（M6）。

---

## 附录 A：AI 链路时序（文本）

**查询链路（查余额）**

```
客户端 → POST /ai/chat {text:"查一下余额"}
  → IdempotencyFilter（写端点幂等）→ AiRateLimitService.tryAcquire
  → AiOrchestrator.answer: 记 USER 消息 → ChatPort.parseIntent("查一下余额", tools)
      → StubChatPort: 路由 BALANCE_QUERY
  → IntentCatalog 校验 → AiToolRegistry: MoneyQueryService.totalBalance(familyId)
  → 组装回复「当前家庭总余额 520.00 元」→ 记 ASSISTANT 消息 + AI_INTENT
客户端 ← 200 {reply}
```

**资金写二次确认链路（存入）**

```
客户端 → POST /ai/chat {text:"给小明存 50"}
  → parseIntent → DEPOSIT{targetUserName:"小明", amount:"50"}
  → 业务解析: 小明→userId（FamilyAccessChecker 校验成员）+ CHILD 仅本人校验
  → 生成 ai_pending_action(PENDING, TTL 60s) + 审计 AI_ACTION_CONFIRM_REQUEST
客户端 ← 200 {reply:"确认给 小明 存入 50.00 元？", pendingActionId}

客户端 → POST /ai/actions/{id}/confirm
  → PendingActionService.confirm: 校验 PENDING 未过期
  → MoneyOperationService.deposit(principal, targetUserId, request)
      → IdempotencyContext(会话操作 ID) → TxCommand.requestId → uk_mtxn_request 兜底
  → status=EXECUTED + 审计 AI_ACTION_EXECUTED + MONEY_DEPOSIT
客户端 ← 200 {transactionId, balanceAfter}
```

**降级链路（AI 不可用）**

```
客户端 → POST /ai/chat
  → ChatPort.parseIntent 抛异常 / 超时 / 熔断
  → 映射 600001 AI 服务不可用 + 审计 AI_DEGRADED
客户端 ← 200 {code:600001, message:"AI 服务暂不可用，请使用手动操作"}
  → 客户端回落手动入口（/deposits、/withdrawals 等既有端点，不受影响）
```

## 附录 B：权限矩阵增量

| AI 端点 / 能力 | 未认证 | 家长 | 孩子 |
|---|---|---|---|
| POST /ai/chat（查询类意图） | ❌ 拒绝 | ✅ | ✅（本家庭数据） |
| POST /ai/chat（资金写意图 → 生成 pending） | ❌ 拒绝 | ✅ 本家庭任意成员 | ⚠️ 仅本人账户（越权 100004） |
| POST /ai/actions/{id}/confirm / cancel | ❌ 拒绝 | ✅ 本会话本人 | ✅ 本会话本人 |
| GET /ai/sessions/{id}/messages | ❌ 拒绝 | ✅ 本会话本人 | ✅ 本会话本人 |

- AI 工具权限 = 既有接口权限（`FamilyAccessChecker.requireMember` + `requireSelfIfChild`），不新增独立权限模型
- 模型不可指定任意 userId：目标成员一律经 `FamilyAccessChecker` 校验归属本家庭

## 附录 C：与 roadmap M4 任务/DoD 映射

| roadmap M4 条目 | 设计章节 |
|---|---|
| 任务 1 Spring AI 2.0.0 接入（统一抽象、JSON 响应） | §4 AI 抽象层（D27）+ §4.1 spike |
| 任务 2 意图理解（NLU） | §5 意图理解与工具注册（D29/D30） |
| 任务 3 AI Function Calling（走既有事务与权限） | §5.3 + §6（D31） |
| 任务 4 ASR/TTS 集成 | §4.2 端口契约（D28，抽象预留） |
| 任务 5 可信度机制（与 mission 五项一一对应） | §7（D32–D35） |
| 任务 6 稳定性（30s 超时/限流/降级） | §8（D36） |
| 任务 7 Embedding 库接入 | §4.2 EmbeddingPort（D38，抽象预留） |
| DoD 全部五项 | §14 验收标准（逐条映射，含 R2 口径说明） |

## 附录 D：文档变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-27 | M4 开发基线初稿：AI 抽象层（D27/D28/D38）、意图目录与 Function Calling（D29/D30）、二次确认（D31）、五项可信度映射（D32–D35）、稳定性落地（D36）、错误码（D37）；V8 迁移、评测集、测试设计、WBS 与 DoD |

---

*本设计作为 M4 开发基线；实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*
