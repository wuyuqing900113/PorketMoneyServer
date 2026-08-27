# M3 移动端适配与可靠性 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（M3 章节）、`M0-detailed-design.md`、`M1-detailed-design.md`、`M2-detailed-design.md`、`docs/version-matrix.md`
> 文档版本：v1.0（2026-08-27，M3 开发基线）
> 适用范围：M3 阶段（第 9–10 周，10-12 ~ 10-25）
> M2 基线：`mvn clean verify` 全绿（Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥ 80% 门禁通过，Testcontainers PG 套件就绪）

---

## 1. 概述

### 1.1 目标

在 M2 零花钱核心业务之上，针对移动弱网环境与 mission 的可靠性约束做专项建设，交付：

- **同步协议**：客户端离线操作队列 → 幂等键提交 → 服务端去重与冲突处理的一套协议约定（供鸿蒙端同步实现）
- **API 幂等性**：全部业务写操作（POST/PUT/DELETE）幂等键协议，资金变动类为硬保证，重复提交/离线重放不产生重复账务
- **重试与降级**：引入 Resilience4j，落地写接口限流 + 外部依赖降级骨架（M4 复用）
- **性能专项**：全 API 时延基准、慢查询治理、慢接口剖析
- **JVM 调优基线**：GC 选型（ZGC）、堆内存参数、虚拟线程使用规范
- **错误处理完善**：错误码增补 + 重试约定固化，客户端可按错误码安全重试

### 1.2 范围（In Scope）

- 幂等键协议端到端：`Idempotency-Key` 头、`idempotency_record` 表（V7 迁移）、拦截/重放、资金写接口 `request_id` 回填
- 离线同步协议文档（客户端操作幂等键、冲突分类与处理策略、重放语义）
- Resilience4j 集成：依赖 spike + RateLimiter 写接口限流 + CircuitBreaker/TimeLimiter/Retry 配置骨架
- 全 API 时延基准扩展、慢查询日志、请求计时埋点（`RequestTimingFilter`）
- JVM 调优基线文档 + 本地 ZGC 冒烟验证
- 错误码 100008/100009 增补、`Retry-After` 头、OpenAPI 逐端点 retryable 标注
- 集成测试：幂等并发、弱网重放、限流、冲突场景

### 1.3 非目标（Out of Scope）

| 事项 | 归属阶段 |
|---|---|
| 鸿蒙端离线队列/同步 SDK 实现 | 端侧（本设计仅定义协议，服务端按协议实现） |
| JMeter 10 TPS 并发压测 | M6（roadmap M6 任务 3；M3 以本地基准数据量全 API P95 验证，见 §15 口径说明） |
| AI/ASR/TTS 外部依赖真实接入与降级演练 | M4（M3 仅落地 Resilience4j 骨架与限流，D21） |
| Docker 镜像 JVM 参数落地 | M7（M3 出基线，§9） |
| 通知推送 | M5 |
| 红冲/退款/负向调账 | 不实现（M2 既定限制不变） |

---

## 2. 决策记录（已确认）

> D16/D17/D20 经项目评审确认；D18–D26 为本设计新增决策。

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D16 | 幂等覆盖范围 | **全部业务写操作**（POST/PUT/DELETE，需认证的 family/money/rule/learning/work 端点）统一幂等键协议；auth 端点（登录/注册/刷新/登出）豁免 | 资金类为硬保证；登录/刷新重试语义为"重新签发"，不可重放旧响应，故豁免（§5.1） |
| D17 | 幂等实现机制 | **独立 `idempotency_record` 表**：请求指纹 + 原始响应缓存，重放返回原响应；`money_transaction.request_id` 回填 + `uk_mtxn_request` 作为账务级第二道防线 | 承接 M2 D12 预留 |
| D18 | 幂等键载体 | **`Idempotency-Key` 请求头**，UUID v4，客户端在重试/离线重放时复用同一值；唯一作用域 `(user_id, idem_key)` | 与 M2 D12 透传头命名一致 |
| D19 | 离线同步冲突策略 | **服务端权威校验，不做 LWW 合并**：① 无冲突类直接应用；② 余额依赖类按当前余额判定（不足→300001 终态）；③ 状态依赖类按当前状态判定（不匹配→300006/400002 终态）；`clientCreatedAt` 仅作审计/展示 | 家庭场景写者少，合并复杂度收益低（§6） |
| D20 | 韧性框架 | **引入 Resilience4j**（Boot 4 兼容性 spike）；M3 落地 RateLimiter（写接口限流→100007+Retry-After），CircuitBreaker/TimeLimiter/Retry 配置骨架供 M4 复用 | 依赖 spike 结论记入 version-matrix（§7.1） |
| D21 | 降级模式 | M3 无外部同步依赖（AI/ASR/TTS 归 M4）；降级以 Resilience4j 骨架 + 配置实例落地，M4 接入时直接引用；DB 超时沿用 HikariCP `connection-timeout=5000` → 900003 可重试 | 镜像 M1 OAuth2「仅结构预留」做法 |
| D22 | GC 选型 | **ZGC**（JDK 21+ 分代 ZGC 默认、亚毫秒停顿、免堆大小调优，最匹配 500ms P95 低延迟诉求）；Shenandoah 作备选已评估 | Shenandoah 停顿特征相近，选 ZGC 因其为 JDK 默认、OpenJDK 25 上验证最充分（§9.1） |
| D23 | 堆内存基线 | 容器感知 `-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0`；本地开发 `-Xms256m -Xmx512m`；`-XX:+ExitOnOutOfMemoryError`；GC 日志 `-Xlog:gc*` 供 M7 SLS 采集 | Docker 镜像参数落地归 M7（§9.2） |
| D24 | 虚拟线程规范 | 维持 `spring.threads.virtual.enabled=true`（Tomcat 虚拟线程）；HikariCP 保持平台线程小池（maximum-pool-size=10）；禁止业务自建无界虚拟线程池与 ThreadLocal 作缓存滥用；`@Scheduled` 已虚拟线程（SchedulingConfig） | §9.3 |
| D25 | 重试语义 | **客户端**按错误码重试：90xxxx 可重试→指数退避+携带幂等键+最多 3 次；100007 延迟重试+Retry-After；其余不可重试；**服务端不自动重试资金写**（防重复入账，幂等由幂等键保证） | 复用 `ErrorCode.isRetryable()` 约定（§10.2） |
| D26 | 错误码增补 | `CommonErrorCode` 新增 `100008` 缺少幂等键 / `100009` 幂等键冲突（同 key 不同请求体）；沿用 90xxxx 可重试约定，OpenAPI 逐端点标注 | §10.1 |

---

## 3. 总体设计

### 3.1 请求处理链路（M3 结束时）

```
鸿蒙 APP
  │ HTTPS / TLS 1.3
  ▼
TraceIdFilter → Spring Security（M1/M2 链路不变）→ JwtAuthenticationFilter → UserIdPrincipal
  ▼
IdempotencyFilter（写操作：读取 Idempotency-Key → 指纹/预占 → 重放判定）
  ▼
RequestTimingFilter（每端点计时，>500ms WARN + traceId）
  ▼
Controller（@PreAuthorize 接口级）
  ▼
Service（业务编排不变，资金写经 AccountTransactionService；写接口经 Resilience4j RateLimiter 限流）
  │
  └─ IdempotencyFilter（afterCompletion：成功回填响应 / 失败释放记录）
  ▼
Mapper（MyBatis，一律 #{}）→ PostgreSQL

定时任务（@Scheduled，虚拟线程执行器，复用 M2 SchedulingConfig）
  ├─ RuleSettlementJob / RuleExpiryJob / ReconciliationJob（M2 不变）
  └─ IdempotencyCleanupJob：每日清理过期幂等记录（M3 新增）
```

### 3.2 包结构增量（在 M2 骨架上生长）

```
src/main/java/wyq/pocket/money/
├── common/
│   ├── idempotency/                      # 【新增】幂等键协议
│   │   ├── IdempotencyFilter.java        # OncePerRequestFilter：头校验/预占/重放/回填
│   │   ├── IdempotencyService.java       # 两阶段预占/重放/回填/释放（§5.3）
│   │   ├── IdempotencyContext.java       # 请求级幂等键上下文（同 TraceIds 的 thread-local 模式）
│   │   ├── IdempotencyRecord.java        # domain：幂等记录
│   │   ├── IdempotencyRecordMapper.java
│   │   ├── IdempotencyProperties.java    # 键长度/TTL/接管阈值配置
│   │   └── job/IdempotencyCleanupJob.java
│   ├── resilience/                       # 【新增】Resilience4j 集成
│   │   ├── Resilience4jConfig.java       # RateLimiter/CB/TimeLimiter/Retry 注册
│   │   └── ResilienceProperties.java
│   ├── timing/                           # 【新增】慢接口剖析
│   │   └── RequestTimingFilter.java
│   └── web/
│       ├── CommonErrorCode.java          # 【修改】+100008 / 100009
│       └── GlobalExceptionHandler.java   # 【修改】Resilience4j 异常映射 + Retry-After 头
├── money/
│   ├── service/MoneyOperationService.java    # 【修改】applyManual 回填 requestId
│   ├── service/LearningTaskService.java      # 【修改】approve 回填 requestId
│   └── service/WorkValueService.java         # 【修改】记录发放回填 requestId
└── rule/...                               # 无结构性变更（写接口走统一幂等层）

src/main/resources/db/migration/
└── V7__create_idempotency_record.sql     # 【新增】幂等记录表（§11.1）
```

### 3.3 与 M2 基线的衔接

| M2 交付物 | M3 变更 |
|---|---|
| `money_transaction.request_id` 列 + `uk_mtxn_request` 部分唯一索引（D12 预留） | 由 null 改为回填幂等键；作为账务级第二道防线（§5.4） |
| `TxCommand.requestId`（record 已含，调用方传 null） | 三处资金写（手动存取/任务批准/工作价值）经 `IdempotencyContext` 回填 |
| `CommonErrorCode`（100006 重复请求 / 100007 限流已定义未用） | 幂等重放/限流正式启用；新增 100008/100009 |
| `ErrorCode.isRetryable()`（90xxxx 可重试约定，M0） | 原样复用，客户端重试语义固化（§10.2） |
| `SchedulingConfig`（虚拟线程 TaskScheduler） | 原样复用，新增幂等清理任务 |
| `Hashes`（common/crypto，SHA-256） | 复用为幂等指纹算法 |
| `ClockConfig`（Asia/Shanghai）、`TraceIds`、`Result`、`GlobalExceptionHandler`、`AuditService` | 原样复用 |
| `PerformanceBaselineIT` + `PerformanceDataSeeder`（M2 §12.5） | 扩展到全量 API（§8.1） |

### 3.4 模块依赖方向（ArchUnit 规则增补）

- `common/idempotency` 与 `common/resilience` 属 common 层，仅依赖 `common/web`（错误码）、`common/security`（principal）、`common/crypto`（指纹哈希）；不依赖任何业务模块
- 业务服务对幂等键的读取经 `IdempotencyContext`（common 层），不反向依赖 web 层请求对象
- `ArchitectureTest` 增补：`common/idempotency`、`common/resilience` 不得依赖 money/rule/finance/user 包

---

## 4. 离线同步协议（roadmap 任务 1）

### 4.1 协议定位

服务端不感知客户端是否离线；协议解决的核心问题是——**客户端把"本地发生的操作"以何种方式提交，服务端才能既保证幂等、又给出确定性的冲突结果**。协议由两部分组成：

1. **幂等键协议**（§5）：每次业务写携带 `Idempotency-Key`，服务端去重并重放原响应
2. **冲突处理策略**（§6）：客户端重放时服务端以当前权威状态判定，返回确定性的成功/失败结果

### 4.2 客户端操作模型

客户端为每个离线/在线写操作生成一条待同步记录：

```json
{
  "operationId": "6ba7b810-9dad-4d1d-80b4-1a2b3c4d5e6f",   // UUID v4，即 Idempotency-Key
  "clientCreatedAt": 1787654321000,                        // 客户端操作发生时刻（毫秒）
  "method": "POST",
  "path": "/api/v1/families/1/withdrawals",
  "payload": { "targetUserId": 3, "amount": 20.00, "remark": "买文具" }
}
```

- 提交时把 `operationId` 置于 `Idempotency-Key` 头，`clientCreatedAt` 置于 `X-Client-Time` 头（可选，仅作审计/展示，不参与冲突裁决，D19）
- 在线请求与离线重放使用**同一协议**（弱网超时后的自动重试 = 同 key 重发；离线重放 = 队列逐条同 key 重发）

### 4.3 重放语义

- 客户端按 FIFO 逐条重放待同步操作，每条独立携带自己的 key
- 服务端判定结果：
  - **同 key 同请求体 → 重放成功**：返回原始响应（200 + 原 data），客户端从队列移除该操作
  - **同 key 不同请求体 → 100009**：客户端本地数据异常，移除操作并告警
  - **业务失败（非重试错误）→ 对应错误码**：客户端移除操作并按错误码提示用户（§6）
  - **可重试错误（90xxxx）→ 指数退避重试**：操作留在队列，按 `isRetryable()` 约定重试
- 服务端不做跨操作的全局顺序保证；操作间无依赖时结果与重放顺序无关

### 4.4 幂等记录生命周期

- TTL 7 天（可配），覆盖"孩子离线数天后重放"的窗口；`IdempotencyCleanupJob` 每日清理过期记录
- 过期后同 key 重放视为新请求正常执行（资金类仍受 `request_id` 唯一索引兜底，但 7 天窗口内不重复）

---

## 5. 幂等设计（roadmap 任务 2，D16/D17/D18）

### 5.1 覆盖范围

| 类别 | 端点 | 幂等 |
|---|---|---|
| 资金写（硬保证） | POST `/deposits`、`/withdrawals`、`/learning-tasks/{id}/approve`、`/work-values` | ✅ 强制 |
| 业务写（统一协议） | 规则 CRUD/暂停/恢复/归档/删除、学习任务创建/提交/驳回/取消、工作价值、用户改昵称/改密等 POST/PUT/DELETE | ✅ 强制 |
| 读 | 全部 GET | — 不涉及 |
| auth 写（豁免） | POST `/auth/register`、`/auth/login`、`/auth/refresh`、`/auth/logout` | ❌ 豁免 |

**auth 豁免理由**（D16）：登录/刷新每次重试都应重新签发令牌（重放旧令牌既不合理也无意义）；注册/登出与离线同步无关联。`IdempotencyFilter` 按认证状态与路径白名单跳过 auth 端点（未认证请求本就被 Security 拒绝，不达 filter）。

### 5.2 表结构（V7）

```sql
CREATE TABLE idempotency_record (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES app_user (id),  -- 幂等键作用域（操作主体）
    idem_key     VARCHAR(64) NOT NULL,                      -- Idempotency-Key（UUID v4）
    method       VARCHAR(8) NOT NULL,                       -- 请求方法
    path         VARCHAR(128) NOT NULL,                     -- 请求路径（不含 query）
    payload_hash CHAR(64) NOT NULL,                         -- SHA-256(method+path+body) 十六进制
    resp_code    INT,                                       -- 原始响应 code（0 成功）
    resp_body    JSONB,                                     -- 原始响应 data
    status       VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',-- IN_PROGRESS / PROCESSED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_idem_user_key UNIQUE (user_id, idem_key)
);
CREATE INDEX idx_idem_expires ON idempotency_record (expires_at);
```

- `payload_hash` 复用 `Hashes`（SHA-256）计算 `method + '\n' + path + '\n' + bodyBytes`，同 key 同体判定、异体冲突判定的依据
- `resp_body` 存 `Result.data`（JSONB），`resp_code` 存 `Result.code`；重放时以新 traceId/timestamp 重建 `Result`（traceId/timestamp 属请求级，不复用）
- `uk_idem_user_key` 为幂等去重的唯一锚点，并发预占由唯一约束兜底（§5.3）

### 5.3 两阶段预占算法（IdempotencyService）

```
写请求（已认证）
  1. 读 Idempotency-Key（缺失 → 100008）
  2. 计算 payload_hash
  3. 预占：INSERT idempotency_record(status=IN_PROGRESS)（独立短事务，立即提交）
       uk 冲突 → 读既有记录：
         PROCESSED 且指纹一致  → 重放：返回 200 + 原 resp_body（新 traceId/timestamp）
         PROCESSED 且指纹不一致 → 100009
         IN_PROGRESS 未超阈值(60s) → 100006（并发处理中，客户端稍后以同 key 重试即重放）
         IN_PROGRESS 超阈值      → 接管：DELETE 后重试 INSERT（防崩溃残留阻塞）
  4. 执行控制器/服务业务（自身事务；资金写 requestId 回填，§5.4）
  5. 成功 → UPDATE resp_code/resp_body/status=PROCESSED（独立短事务）
  6. 业务异常 → DELETE 记录（释放 key 供修正后重试），异常照常由 GlobalExceptionHandler 返回
```

**事务边界**：幂等记录三处写操作（预占/回填/释放）均为独立短事务，与业务事务解耦——避免幂等记录持锁阻塞并发同 key 请求，也避免业务回滚连带幂等记录回滚（回填仅在成功后，失败由步骤 6 显式清理）。

**无 key 的非强制场景**：本协议对业务写一律强制（D16），故"无 key"只出现在 auth 豁免路径（被跳过）或客户端违规（→ 100008）。

### 5.4 资金写的 request_id 回填（D17 第二道防线）

- `IdempotencyFilter` 将 key 写入 `IdempotencyContext`（thread-local，finally 清理，模式同 `TraceIds`）
- 三处资金写经 `IdempotencyContext.currentKey()` 回填 `TxCommand.requestId`：
  - `MoneyOperationService.applyManual`（手动存取，当前传 null）
  - `LearningTaskService.approve`（任务批准发放）
  - `WorkValueService`（工作价值记录发放）
- `money_transaction.request_id` 落库后，`uk_mtxn_request` 部分唯一索引构成**账务级兜底**：即使幂等记录被误清理或并发穿透预占，同一幂等键也不可能产生两笔资金流水（重复插入触发唯一约束异常 → 按已处理返回 100006）

---

## 6. 冲突处理策略（D19）

服务端**权威校验、不合并**。客户端 `clientCreatedAt`（`X-Client-Time` 头）仅记录于审计/展示，不参与冲突裁决。

| 冲突类别 | 典型操作 | 服务端判定 | 结果 |
|---|---|---|---|
| ① 无冲突类 | 手动存入、工作价值记录、规则创建、任务创建 | 直接应用（无前置状态依赖） | 幂等去重，重放成功 |
| ② 余额依赖类 | 自由提取 | 按**服务端当前余额**判定：`balance >= amount` 才放行 | 不足 → 300001（终态失败，客户端移除并提示） |
| ③ 状态依赖类 | 任务批准/驳回/取消、规则修改/归档/删除、规则暂停/恢复 | 按**服务端当前状态**判定：状态机迁移合法才放行 | 不匹配 → 300006 / 400002 / 400005 等对应错误码（终态） |

**示例——离线提取冲突**：

```
孩子离线时提交"提取 20"（余额当时 50）→ 入本地队列
期间家长提取 40（另一设备在线）→ 余额变 10
孩子恢复联网，重放"提取 20"（同 Idempotency-Key）
  → 服务端按当前余额 10 判定 → 300001 余额不足（终态）
  → 客户端移除该操作，提示"余额不足（当前 10 元）"
```

**示例——离线任务批准冲突**：

```
家长离线时提交"批准任务 7"→ 入本地队列
期间另一位家长已驳回任务 7（状态 REJECTED）
家长恢复联网，重放"批准任务 7"
  → 服务端按当前状态 REJECTED 判定 → 300006（终态）
  → 客户端移除该操作，刷新任务状态
```

**不做 LWW 的理由**：家庭场景同一时刻写者极少（≤ 2 人）；金额与状态均以服务端权威为准能保证账务与状态机正确性，LWW 合并会引入"以客户端时间戳覆盖服务端已结算状态"的错账风险，收益远低于复杂度。

---

## 7. Resilience4j 集成（roadmap 任务 3，D20/D21）

### 7.1 依赖与版本 spike

- 候选：`spring-cloud-starter-circuitbreaker-resilience4j` 或 `resilience4j-spring-boot3`；对 Boot 4.1.0 的兼容性按 M0/M1 spike 流程验证，结论记入 `docs/version-matrix.md`
- **回退预案**：starter 未适配 Boot 4 时，改用 `resilience4j-core` + 程序化 `Resilience4jConfig`（`RateLimiterRegistry`/`CircuitBreakerRegistry` Bean），功能等价、少一层自动配置
- 版本纳入 `pom.xml` properties 管理（同 mybatis-spring-boot 等既有约定）

### 7.2 M3 落地：写接口限流

- **RateLimiter**（每用户维度）：资金写与业务写共用一只 `writeLimiter`，`limit-for-period` / `limit-refresh-period` 经 `ResilienceProperties` 可配（默认 30 次/60s/用户，家庭场景宽松、防误操作与滥用）
- 采用 `@RateLimiter(name = "writeLimiter", fallbackMethod = ...)` 标注于写服务方法（或统一以 AOP/Filter 维度实现，实现期择一，语义一致）
- 限流耗尽 → fallback 抛出 `BusinessException(CommonErrorCode.RATE_LIMITED)` → 100007，并附 `Retry-After` 头（§10.3）

### 7.3 降级骨架（供 M4 复用，D21）

- `Resilience4jConfig` 预注册：
  - `CircuitBreaker`：`aiCircuitBreaker`（滑动窗口 10、失败率阈值 50%、开启后半开探测）
  - `TimeLimiter`：`aiTimeLimiter`（30s，对齐 tech-stack「AI 调用超时默认 30 秒」）
  - `Retry`：`externalRetry`（最多 2 次、指数退避，仅限**幂等读/查询类外部调用**，资金写不套用，D25）
- M3 无真实外部依赖，上述实例仅由单测验证配置与回退语义（镜像 M1 OAuth2「仅结构预留」做法）；M4 接入 AI/ASR/TTS 时直接引用
- `GlobalExceptionHandler` 增补 Resilience4j 异常映射：`RequestNotPermitted`（限流/熔断开启）→ 100007、`TimeoutException` → 900002、其余 → 900001

---

## 8. 性能专项（roadmap 任务 4）

### 8.1 全 API 时延基准

- 扩展 `PerformanceBaselineIT`（`@Tag("performance")`，surefire `excludedGroups=performance` 默认排除，手动触发命令沿用 M2 §12.5）：从 M2 的 4 类读端点扩展到**全量 API**（读 9 类 + 写 6 类，含幂等链路），断言 P95 ≤ 500ms
- `PerformanceDataSeeder`（5 万条流水种子）沿用；写端点基准每轮以新 `Idempotency-Key` 执行（模拟真实流量）
- **口径说明**：roadmap M3 DoD「10 TPS 压测条件」在 M3 以本地基准数据量下全 API P95 ≤ 500ms 落地；正式 JMeter 10 TPS 并发压测归 M6（roadmap M6 任务 3，与 M2 DoD 的 SonarQube 口径同一处理方式）。若需 M3 内并发口径，以 Testcontainers + 并发驱动做轻量 10 TPS 级基准（可选）

### 8.2 慢接口剖析

- 新增 `RequestTimingFilter`：记录每端点耗时（`System.nanoTime` 差值），> 500ms 打 WARN 结构化日志（含 traceId、method、path、耗时），供慢接口定位；同时暴露 Micrometer `Timer`（Actuator `/metrics`，`http.server.requests`）供 M7 ARMS 采集
- 口径与 `MaskingJsonEncoder` 一致：路径不含 query（防 token 泄露进指标维度）

### 8.3 慢查询治理

- PostgreSQL `log_min_duration_statement=200ms`（dev/prod 配置层，经环境变量注入）记录慢 SQL
- 新增查询 `EXPLAIN` 审计存档：幂等查询（`WHERE user_id=? AND idem_key=?` 走 `uk_idem_user_key` 唯一索引，O(1)）、清理查询（`WHERE expires_at < ?` 走 `idx_idem_expires`）；既有 M2 核心查询不重复审计
- 全量查询仍是 `#{}` 参数化（M0 红线不变），新 SQL 走 PR 评审清单

---

## 9. JVM 调优基线（roadmap 任务 5，D22/D23/D24）

### 9.1 GC 选型评估

| 维度 | ZGC（选型 ✅） | Shenandoah（备选） |
|---|---|---|
| JDK 25 可用性 | OpenJDK 默认，分代 ZGC 自 JDK 21 起默认启用 | OpenJDK 提供（`-XX:+UseShenandoahGC`） |
| 停顿 | 亚毫秒级，与堆大小解耦 | 亚毫秒级，特征相近 |
| 调优成本 | 免堆大小调优，默认参数即可 | 需关注 region 与并发线程 |
| 生态验证 | OpenJDK 主推，JDK 25 验证最充分 | 红帽主推 |

**结论（D22）**：ZGC。本项目 API P95 ≤ 500ms 对停顿敏感，ZGC 亚毫秒停顿 + 免调优最匹配；堆小（GB 级以下）也无需手动分代调参。

### 9.2 堆内存参数基线（D23）

```text
# 容器（Docker，M7 落地）
-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0
# 本地开发 / 裸机
-Xms256m -Xmx512m
# 通用
-XX:+ExitOnOutOfMemoryError
-Xlog:gc=info,gc+heap=info:file=logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m
```

- 依据：10 TPS 家庭场景 + 虚拟线程，堆占用低；容器感知百分比避免 Docker 内存限制与固定 `-Xmx` 冲突
- M3 只出基线 + 本地以 ZGC 参数启动冒烟验证；镜像 JVM 参数写入 Dockerfile 归 M7（§1.3）

### 9.3 虚拟线程使用规范（D24）

1. **请求线程**：维持 `spring.threads.virtual.enabled=true`（Tomcat 每请求一虚拟线程），阻塞式 IO 在虚拟线程上高效
2. **连接池**：HikariCP 保持平台线程小池（`maximum-pool-size=10`）——DB 连接是稀缺资源，虚拟线程不能放大连接数
3. **定时任务**：`@Scheduled` 已用虚拟线程执行器（M2 SchedulingConfig），沿用
4. **禁止项**：① 业务代码自建无界虚拟线程池（用 ExecutorService 兜底）；② 将 ThreadLocal 当缓存长期持有（虚拟线程数量大，ThreadLocal 残留即内存浪费；幂等上下文用 finally 清理，§5.4）；③ 长 `synchronized` 临界区（JDK 25 虽不 pinning，仍避免放大锁竞争）
5. 新增虚拟线程相关类若引入 SpotBugs/PMD 告警，按既有 `config/spotbugs/exclude.xml` 评审流程处置

---

## 10. 错误码与重试（roadmap 任务 6，D25/D26）

### 10.1 错误码增补（CommonErrorCode 10xxxx 段）

| 错误码 | 含义 | 客户端处理建议 | retryable |
|---|---|---|---|
| 100006 | 重复请求（幂等拦截/处理中） | 视为已受理；稍后同 key 重试即重放原响应 | 否 |
| 100007 | 请求过于频繁（限流） | 按 `Retry-After` 延迟后重试 | 否（按 Retry-After 延迟） |
| **100008** | **缺少幂等键**（写操作必填 `Idempotency-Key`） | 生成 UUID 并重发，不可重试 | 否 |
| **100009** | **幂等键冲突**（同 key 不同请求体） | 本地数据异常，移除操作并告警 | 否 |

- 100008/100009 加入 `CommonErrorCode` 枚举，经 `CommonErrorCodeTest` 段位与唯一性单测
- 既有 90xxxx（900001/900002/900003/900004）可重试约定不变（`ErrorCode.isRetryable()`）

### 10.2 重试约定固化（D25）

- **客户端**：`code >= 900000`（90xxxx 段）→ 可重试，指数退避（如 1s/2s/4s），最多 3 次，**必须复用同一 `Idempotency-Key`**；100007 → 按 `Retry-After` 延迟重试；其余非 0 code → 不可重试，按 message 提示用户
- **服务端**：不自动重试资金写（防止无 key 重试产生重复账务）；重试职责在客户端 + 幂等键保证。服务端 Retry（Resilience4j）仅限幂等读/查询类外部调用（§7.3）
- 同步协议文档固化此约定（DoD「同步协议 API 文档」组成部分）

### 10.3 `Retry-After` 头

- `GlobalExceptionHandler` 对 100007 / 900004 响应附 `Retry-After: <秒>`（限流器给出建议等待秒数，缺省 60）
- 实现：handler 检测错误码后写响应头（`HttpServletResponse` 注入）或经 `ResponseEntity` 定制

---

## 11. 数据模型与迁移

### 11.1 V7__create_idempotency_record.sql

见 §5.2（完整 DDL）。要点：

- `uk_idem_user_key(user_id, idem_key)`：幂等去重唯一锚点
- `idx_idem_expires(expires_at)`：清理任务扫描
- `payload_hash CHAR(64)`：SHA-256 十六进制；`resp_body JSONB`：原始响应 data（PG 原生 JSON 类型，M0 约束）
- 迁移脚本一经提交永不修改，回滚走新脚本前向修复（M0 既定规范）

---

## 12. 测试设计

### 12.1 单元测试

| 测试类 | 覆盖点 |
|---|---|
| IdempotencyServiceTest | 预占成功/同 key 同体重放/同 key 异体 100009/IN_PROGRESS 未超阈值 100006/超阈值接管/业务失败释放 key |
| IdempotencyFilterTest | 写操作缺 key 100008/读与 auth 路径豁免/幂等上下文写入与 finally 清理 |
| Resilience4jConfigTest | RateLimiter 配置生效、fallback → 100007、CB/TimeLimiter/Retry 实例注册 |
| CommonErrorCodeTest（增补） | 100008/100009 段位 10xxxx、无重复值、`isRetryable()`=false |
| RequestTimingFilterTest | 耗时记录、>500ms WARN、路径不含 query |

### 12.2 集成测试套件（RestAssured + Testcontainers PG，沿用 M1 形态）

| 套件 | 场景 |
|---|---|
| IdempotencyPgIntegrationTest | 同 key 重复提交资金写仅一笔流水；重放返回原响应；同 key 异体 100009；缺 key 100008；并发同 key 恰好一笔（穿透预占的兜底验证） |
| WeakNetworkRetryPgIntegrationTest | 模拟弱网/超时后同 key 重试，余额与流水一致；离线提取冲突（余额被消耗）→ 300001 终态；离线任务批准冲突 → 300006 终态 |
| RateLimitPgIntegrationTest | 短窗口内触发限流 100007 + `Retry-After` 头；窗口刷新后恢复 |
| RequestTimingSmokePgIntegrationTest | 全 API 可正常响应，计时埋点不改变响应契约 |
| 既有套件回归 | M2 全部套件补 `Idempotency-Key` 头后回归通过（协议强制化对既有测试的适配） |

### 12.3 性能基准（全 API，§8.1）

- `PerformanceBaselineIT` 扩展：读端点（看板/流水/趋势/榜单/规则列表详情/任务列表/工作价值/报表/统计）+ 写端点（存取/规则创建/任务创建与批准/工作价值），各 10 次热身 + 200 次计时，断言 P95 ≤ 500ms
- 索引命中 `EXPLAIN` 抽检存档（幂等查询 + 清理查询）

### 12.4 覆盖率与门禁

JaCoCo 80% BUNDLE 门禁沿用；`common/idempotency`、`common/resilience`、`common/timing` 新增类全部计入。

---

## 13. 配置增量

```yaml
pocket-money:
  idempotency:
    key-max-length: ${IDEMPOTENCY_KEY_MAX_LENGTH:64}
    ttl: ${IDEMPOTENCY_TTL:P7D}              # 幂等记录 TTL 7 天
    takeover-grace: ${IDEMPOTENCY_TAKEOVER_GRACE:PT60S}  # IN_PROGRESS 接管阈值
    cleanup-cron: ${IDEMPOTENCY_CLEANUP_CRON:0 37 3 * * *}  # 每日 03:37
  resilience:
    write-limit:
      limit-for-period: ${WRITE_LIMIT_FOR_PERIOD:30}
      limit-refresh-period: ${WRITE_LIMIT_REFRESH_PERIOD:60s}
    ai:
      timeout: ${AI_TIMEOUT:30s}             # M4 使用，M3 仅配置
      circuit-breaker-failure-rate: ${AI_CB_FAILURE_RATE:50}
      circuit-breaker-sliding-window: ${AI_CB_SLIDING_WINDOW:10}
```

- `IdempotencyCleanupJob` 复用 `SchedulingConfig` 虚拟线程执行器，`@ConditionalOnProperty` 控制启停（同 M2 结算任务模式）
- Resilience4j 配置经 `ResilienceProperties` 绑定（`@ConfigurationProperties`，前缀 `pocket-money.resilience`）

---

## 14. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 |
|---|---|---|---|
| T1 | Resilience4j 依赖 spike + 集成（版本验证、Resilience4jConfig/Properties、RateLimiter 落地、异常映射、100007+Retry-After） | — | 2 人天 |
| T2 | 幂等键协议：V7 迁移、IdempotencyFilter/Service/Context/Properties、100008/100009、三处资金写 requestId 回填 | T1 | 3 人天 |
| T3 | 离线同步协议文档 + 冲突处理用例（三类冲突 + 重放幂等，§12.2 前两套件） | T2 | 2 人天 |
| T4 | 错误处理完善：OpenAPI 逐端点 retryable 标注、同步协议 API 文档、错误码段位测试 | T2 | 1 人天 |
| T5 | 性能专项：全 API PerformanceBaselineIT 扩展、RequestTimingFilter、慢查询日志与 EXPLAIN 审计 | T2 | 2.5 人天 |
| T6 | JVM 调优基线：GC 评估文档、堆参数、虚拟线程规范、本地 ZGC 冒烟验证 | — | 1.5 人天 |
| T7 | 集成测试全集（§12.2 剩余套件）+ 既有套件幂等键适配回归 + DoD 验证收尾 | T2–T5 | 3 人天 |

合计约 **15 人天**。roadmap 排期 2 周（10 个工作日/人）：

- **2 人投入**：约 7.5 人天/人，舒适（推荐，账务类代码强制双人评审也要求 ≥2 人）
- **1 人投入**：超载约 50%，需启用 roadmap 弹性条款（时间平移）或裁剪。候选裁剪项：CB/TimeLimiter 骨架降级为纯文档、RequestTimingFilter 用 Micrometer 现成 `@Timed` 注解替代、写端点基准并入读端点抽测

关键路径：T1 → T2 → T3/T5 并行；T7 为收尾闸门。

---

## 15. 验收标准（DoD，与 roadmap 一致并细化）

- [ ] 资金类接口重复提交不会产生重复账务（IdempotencyPgIntegrationTest：同 key 重复提交仅一笔流水；并发同 key 恰好一笔）
- [ ] 模拟弱网/超时场景下客户端可安全重试且数据一致（WeakNetworkRetryPgIntegrationTest：同 key 重试余额与流水一致；离线提取/批准冲突返回确定性终态错误码）
- [ ] 全量 API P95 ≤ 500ms（PerformanceBaselineIT 全 API 报告存档；口径说明见 §8.1——正式 10 TPS JMeter 压测归 M6）
- [ ] 同步协议 API 文档完成（幂等键协议 + 冲突处理 + 重试约定 + 逐端点错误码/retryable 标注，README 更新至 M3 基线）
- [ ] 错误码 100008/100009 段位与唯一性单测通过；`Retry-After` 头路径演练一次（限流测试断言）
- [ ] 幂等清理任务上线（每日执行），过期记录清理测试通过
- [ ] 单测覆盖率 ≥ 80%（JaCoCo）；`mvn clean verify` 全门禁绿（Checkstyle/PMD/SpotBugs，SonarQube 归 M6）
- [ ] 既有 M2 集成测试套件补 `Idempotency-Key` 后全量回归通过

---

## 16. 风险与遗留事项

| # | 风险/事项 | 影响 | 应对 |
|---|---|---|---|
| R1 | Resilience4j starter 未适配 Boot 4.1.0 | T1 受阻 | 回退 `resilience4j-core` + 程序化配置（§7.1）；spike 前置，结论记入 version-matrix |
| R2 | 幂等协议强制化对既有 M2 客户端/测试是破坏性变更 | 老客户端不带头 → 100008 | 同步协议文档明示；M2 集成测试统一补头回归；GA 前无历史客户端存量 |
| R3 | IN_PROGRESS 残留（进程崩溃）阻塞同 key 重试 | 重放被 100006 卡住 | 接管阈值（60s）自动清理 + TTL 7 天兜底（§5.3/§11.1） |
| R4 | 幂等记录表增长 | 存储膨胀 | TTL 7 天 + 每日清理任务；`idx_idem_expires` 支撑清理扫描 |
| R5 | 服务端不自动重试 + 客户端重试依赖幂等键 | 客户端误用（换 key 重试）可致重复 | 协议文档强调"重试必须复用同 key"；资金写 `request_id` 唯一索引兜底（§5.4） |
| R6 | 全 API 基准以本地数据量为准，10 TPS 压测口径后置 M6 | M6 才发现并发瓶颈 | M2 D7 同款双保险：M3 基准 + M6 JMeter 复测，超限回补（并发下幂等预占/连接池压力） |

遗留至后续阶段：正式 JMeter 压测（M6）、AI/ASR/TTS 降级演练（M4）、Docker 镜像 JVM 参数（M7）、通知（M5）、红冲/退款（待定）。

---

## 附录 A：幂等键协议时序（文本）

**同 key 重放（重放成功）**

```
客户端（同 Idempotency-Key 重发）
  → IdempotencyFilter: 读 key → payload_hash
  → IdempotencyService.preoccupy: INSERT IN_PROGRESS → uk 冲突 → 读既有 PROCESSED 且指纹一致
  → 返回 200 + 原 resp_body（新 traceId/timestamp）；不执行业务
```

**资金写（首次）**

```
客户端 → IdempotencyFilter → preoccupy(IN_PROGRESS)
  → Controller → MoneyOperationService.deposit
      → IdempotencyContext.currentKey() → TxCommand.requestId
      → AccountTransactionService.apply（流水 request_id 落库，uk_mtxn_request 兜底）
  → IdempotencyFilter.afterCompletion: UPDATE resp(PROCESSED)
客户端 ← 200 {transactionId, balanceAfter}
```

**并发同 key（第二笔穿透预占兜底）**

```
请求 A/B 同 key：A preoccupy 成功；B INSERT 触发 uk 冲突 → 读 IN_PROGRESS 未超阈值 → 100006
（极端：A 在业务提交前崩溃、B 超阈值接管 → DELETE + 重试 INSERT；资金侧 uk_mtxn_request 仍保证不重复入账）
```

## 附录 B：权限矩阵增量

M3 不新增业务端点，权限矩阵沿用 M2 附录 B 不变。新增的是协议层约束：**全部业务写端点要求 `Idempotency-Key` 头**（缺失 100008），读端点与 auth 端点不受影响。

## 附录 C：与 roadmap M3 任务/DoD 映射

| roadmap M3 条目 | 设计章节 |
|---|---|
| 任务 1 离线操作与数据同步方案 | §4 同步协议 + §6 冲突处理 |
| 任务 2 API 幂等性保障 | §5 幂等设计 |
| 任务 3 重试与降级机制 | §7 Resilience4j 集成 |
| 任务 4 性能专项 | §8 性能专项 |
| 任务 5 JVM 调优基线 | §9 JVM 调优基线 |
| 任务 6 错误处理完善 | §10 错误码与重试 |
| DoD 全部四项 | §15 验收标准（逐条映射） |

## 附录 D：文档变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-27 | M3 开发基线初稿：幂等键协议（D16–D18）、冲突策略（D19）、Resilience4j 集成（D20/D21）、JVM 基线（D22–D24）、重试语义（D25）、错误码增补（D26）；V7 迁移、同步协议、测试设计、WBS 与 DoD |

---

*本设计作为 M3 开发基线；实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*
