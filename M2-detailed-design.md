# M2 零花钱核心业务 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（M2 章节）、`M0-detailed-design.md`、`M1-detailed-design.md`、`docs/version-matrix.md`
> 文档版本：v1.2（2026-08-26，M2 测试补全后回改；v1.1 为 2026-08-19 评审通过的开发基线，变更记录见附录 D）
> 适用范围：M2 阶段（第 5–8 周，09-14 ~ 10-11）
> M1 基线：`mvn clean verify` 全绿（62 类，Checkstyle/PMD/SpotBugs 0 违规，JaCoCo 覆盖率门禁通过，Testcontainers PG 套件可用）

---

## 1. 概述

### 1.1 目标

在 M1 认证与家庭域之上，交付 APP 首页与"我的"页面的全部非 AI 业务能力：

- **账户与账务**：家庭成员零花钱账户（余额快照 + 流水台账），全部账务变动事务化、可审计、可对账
- **零花钱收支**：手动增加（家长/孩子）、自由提取、流水分页查询
- **家庭看板**：余额汇总、日/周双粒度趋势、周收入榜单
- **规则模块**：包月规则 CRUD、个性化配置（多规则/可配置发放日）、规则引擎每日幂等结算
- **学习未来价值零花钱**：学习任务定义 → 孩子提交完成 → 家长确认 → 奖励发放全链路
- **工作价值零花钱**：父母按月记录工资收入并发放自身零花钱
- **财务模块**：月度收支报表、家庭统计汇总（同步聚合，决策见 D7）

### 1.2 范围（In Scope）

- money / rule / finance 三个业务模块从空骨架到完整实现
- V4–V6 数据库迁移脚本（账户、流水、规则、发放记录、学习任务、工作价值记录）
- 金额一律 `DECIMAL(12,2)` + `BigDecimal`；账户乐观锁；DB 级余额非负兜底
- 定时结算任务 + 补发机制 + 每周对账任务（`@Scheduled`，幂等设计）
- M1 成员移除链路的账务联动（Spring Event：规则暂停、账户冻结、任务取消）
- 看板/记录查询性能达标（基准数据量下 P95 ≤ 500ms）
- RestAssured + Testcontainers 集成测试覆盖关键路径、金额精度边界、并发取款
- OpenAPI 文档 M2 部分产出

### 1.3 非目标（Out of Scope）

| 事项 | 归属阶段 |
|---|---|
| 客户端幂等键协议与重复提交拦截逻辑 | M3（M2 仅预留 `request_id` 列与透传头，见 D12） |
| 离线同步协议 | M3 |
| 通知推送（规则到期提醒、审批提醒） | M5（M2 仅产出事件与审计锚点） |
| AI 语音记账/语音增加零花钱 | M4 |
| 退款/红冲/负向调账 | 不实现（错账以新流水更正，见 §5.5 已知限制） |
| 多币种 | 不支持（固定 CNY 两位小数） |
| 报表导出（Excel/PDF） | 另期规划 |
| 孩子零花钱消费限额/分类预算 | 产品后续迭代，表结构不受影响 |

---

## 2. 决策记录（已确认）

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D1 | 余额模型 | **余额快照 + 流水台账**：`money_account.balance` 与 `money_transaction` 同事务双写，每周对账任务兜底 | 读路径 O(1) 支撑看板 P95；流水 `balance_after` 列支撑趋势计算与对账 |
| D2 | 提取流程 | **自由提取**：家长与孩子均可直接提取，孩子限本人账户余额内，无审批环节 | 监管通过看板全透明 + 流水可见实现（D8） |
| D3 | 包月结算 | **可配置发放日 + 每日定时任务**：每条规则配置 `grant_day`（1–28，默认 1），结算任务按"当月应发未发即补"语义幂等发放 | 天然覆盖停机错过、月中新建规则、月末兜底；单一任务逻辑，无需独立补发任务 |
| D4 | 学习任务链路 | **定义 → 孩子提交 → 家长确认**：PENDING → SUBMITTED → APPROVED/REJECTED；驳回后可重新提交；发放前可取消 | 双段确认契合教育场景与 mission 二次确认理念 |
| D5 | 工作价值语义 | **父母也是零花钱账户主体**：每月记录工资收入（参考信息）+ 手动填写发放金额，入账父母本人账户 | 发放金额手动填写，不做比例规则（各家庭分配习惯不同） |
| D6 | 趋势与榜单 | **流水实时聚合**：日粒度（近 30 日余额曲线）+ 周粒度（近 12 周收支与周末余额）；周榜单 = **本周收入榜** | 不引入快照表；家庭级数据量（万级流水）下索引聚合满足 P95 |
| D7 | 报表形态 | **同步聚合，不异步** | ⚠️ 对 roadmap M2 任务 7"耗时操作（报表统计等）异步化"的**决策偏差**：家庭数据量下聚合查询实测为亚百毫秒级，不构成"耗时操作"；mission 约束"耗时操作必须异步处理"的前提（耗时）不成立。**红线**：M6 性能测试若出现 >500ms 的报表查询，必须回补异步化（任务表模式预案见 §10.5）。roadmap M2 任务表述已于 2026-08-19 同步修订 |
| D8 | 数据可见性 | **家庭内全透明**：孩子可见全家看板、全部成员流水/报表/榜单；**写操作**以家长为主，孩子仅可操作本人账户（存取）与提交本人任务 | 接口级权限矩阵见附录 B |
| D9 | 模块归属 | 学习任务与工作价值归 **money** 模块；规则引擎归 **rule** 模块；报表归 **finance** 模块 | 与 roadmap 模块划分（user/money/rule/finance/ai/notify）一致；finance 只经 money 的 service 层取数（M0 依赖方向规则） |
| D10 | 账户生命周期 | **惰性开户**：首次入账时同事务创建账户（余额 0）；无账户成员看板显示余额 0 | 避免对 M1 存量家庭做数据回补；唯一约束 + 冲突重试防并发开户竞态 |
| D11 | 成员移除联动 | **引入 Spring Event**：user 模块发布 `MemberRemovedEvent`，rule/money 模块监听（暂停规则、冻结账户、取消在途任务） | 解决"已移除成员继续被结算"的正确性问题；为 M5 事件驱动改造落地第一个真实场景 |
| D12 | 幂等预留 | 流水表预留 `request_id` 列 + 部分唯一索引；存取接口透传 `Idempotency-Key` 头入库 | 去重判定与客户端协议在 M3 实现；M2 重复提交仍可能产生重复流水（已知限制，端上 M2 阶段不暴露重试入口） |
| D13 | 并发控制 | **账户乐观锁**（`version` 列 + 条件更新 + 行数校验，冲突重试 3 次） | 10 TPS 低争用场景避免持锁等待；与虚拟线程模型无 pinning 顾虑 |
| D14 | 定时任务框架 | Spring `@Scheduled`（cron 可配置） | 单体单实例部署前提（roadmap 垂直扩展）；结算/对账均为幂等设计，M7 蓝绿发布短双实例窗口重跑无副作用 |
| D15 | 时间来源 | 统一注入 `java.time.Clock` Bean，禁止业务代码直接 `LocalDate.now()` | 结算日、周榜单、趋势窗口均可测试（注入固定时钟） |

---

## 3. 总体设计

### 3.1 请求处理链路（M2 结束时）

```
鸿蒙 APP
  │ HTTPS / TLS 1.3
  ▼
TraceIdFilter → Spring Security（M1 链路不变）→ JwtAuthenticationFilter → UserIdPrincipal
  ▼
Controller（@PreAuthorize 接口级）
  ▼
Service
  ├── FamilyAccessChecker.requireMember（数据级，复用 M1）
  ├── 业务编排（rule / money / finance）
  ├── AccountTransactionService.apply(...)   ← 全部账务变动唯一入口（同事务：流水 + 余额 + 乐观锁）
  ├── AuditService.record(...)（REQUIRES_NEW，复用 M1）
  └── ApplicationEventPublisher（MemberRemovedEvent 等）
  ▼
Mapper（MyBatis，一律 #{}）→ PostgreSQL

定时任务（@Scheduled，虚拟线程执行器）
  ├── RuleSettlementJob：每日扫描"当月应发未发"规则 → AccountTransactionService
  └── ReconciliationJob：每周对账 account.balance ↔ 最新流水 balance_after
```

### 3.2 包结构增量（在 M1 骨架上生长）

```
src/main/java/wyq/pocket/money/
├── common/
│   ├── scheduling/                      # 【新增】定时任务配置
│   │   └── SchedulingConfig.java        # @EnableScheduling + 虚拟线程 TaskScheduler
│   ├── time/                            # 【新增】时间基准
│   │   └── ClockConfig.java             # Clock Bean（Asia/Shanghai 业务时区，可被测试替换）
│   └── audit/AuditAction.java           # 【修改】追加 M2 审计动作（§9）
├── user/
│   ├── event/                           # 【新增】领域事件契约（生产者持有）
│   │   └── MemberRemovedEvent.java
│   ├── service/FamilyService.java       # 【修改】移除成员时发布 MemberRemovedEvent
│   └── service/UserService.java         # 【修改】新增批量昵称查询（报表/流水展示用，防 N+1）
├── money/                               # 零花钱核心域（含学习任务、工作价值，D9）
│   ├── controller/MoneyController.java, LearningTaskController.java, WorkValueController.java
│   ├── service/   AccountService, AccountTransactionService, MoneyQueryService,
│   │              DashboardService, TrendService, LeaderboardService,
│   │              LearningTaskService, WorkValueService,
│   │              event/MemberRemovedMoneyListener
│   ├── mapper/    MoneyAccountMapper, MoneyTransactionMapper,
│   │              LearningTaskMapper, WorkValueRecordMapper
│   ├── domain/    MoneyAccount, MoneyTransaction, LearningTask, WorkValueRecord,
│   │              TxDirection, TxBizType, LearningTaskStatus
│   └── dto/       DepositRequest, WithdrawRequest, TransactionPageResponse,
│                  DashboardResponse, TrendResponse, LeaderboardResponse,
│                  LearningTask 各请求/响应, WorkValue 各请求/响应, MoneyErrorCode
├── rule/                                # 规则模块
│   ├── controller/RuleController.java
│   ├── service/   RuleService, RuleSettlementService,
│   │              event/MemberRemovedRuleListener
│   ├── job/       RuleSettlementJob.java, RuleExpiryJob.java
│   ├── mapper/    MoneyRuleMapper, RuleGrantRecordMapper
│   ├── domain/    MoneyRule, RuleGrantRecord, RuleStatus
│   └── dto/       RuleRequest, RuleResponse, GrantRecordResponse, RuleErrorCode
└── finance/                             # 财务模块（只经 money.service 取数，D9）
    ├── controller/FinanceController.java
    ├── service/   ReportService
    └── dto/       IncomeExpenseReportResponse, StatisticsSummaryResponse, FinanceErrorCode

src/main/resources/db/migration/
├── V4__create_money_account_transaction.sql   # 【新增】账户 + 流水
├── V5__create_rule_grant.sql                  # 【新增】包月规则 + 发放记录
└── V6__create_learning_task_work_value.sql    # 【新增】学习任务 + 工作价值记录
```

### 3.3 与 M1 基线的衔接

| M1 交付物 | M2 变更 |
|---|---|
| `UserIdPrincipal`（userId/familyId/role） | 全部 M2 接口的身份入参来源；孩子本人账户判定 = `principal.userId == targetUserId` |
| `FamilyAccessChecker.requireMember` | 所有 `/families/{familyId}/**` 入口第一步校验，原样复用 |
| 错误码段位表（30/40/50xxxx 归 M2 定义） | 新增 `MoneyErrorCode` / `RuleErrorCode` / `FinanceErrorCode` 三枚举（§8.4） |
| `AuditService` / `AuditAction` | 追加 16 个 M2 审计动作（§9） |
| `FamilyService.removeMember` | 追加事件发布（D11）；原移除语义不变 |
| 权限矩阵测试 `PermissionMatrixIT` | 扩展 M2 全部端点（附录 B） |
| `MaskingRules` / Result / TraceId | 原样复用；金额输出为数字（两位小数），不做脱敏 |

### 3.4 模块依赖方向（ArchUnit 规则增补）

```
finance ──→ money ──→ user
   │          ↑
   └──────────┴──→ common
rule ──→ user；rule ──→ money（结算入账经 AccountTransactionService）
user ──✗ 不依赖 money/rule/finance（仅发布事件，事件契约在 user.event）
```

- `ArchitectureTest` 增补两条规则：① finance 不得直接访问 money/rule 的 mapper；② user 不得依赖 money/rule/finance 的任何类（事件监听方向单向）
- 跨模块取数契约：`UserService.findNicknameMap(Collection<Long>)`、`FamilyService.listMembers(long, UserIdPrincipal)`（全家庭成员，报表 memberRows 全员上榜与看板成员余额行用）、`MoneyQueryService`（聚合查询门面，供 finance 使用）

---

## 4. 账户与流水设计（money 模块核心）

### 4.1 账户（money_account）

- 每个家庭成员至多一个账户（`uk_money_account_user(user_id)`），**惰性开户**（D10）：
  - 首次入账（手动增加/规则发放/任务奖励/工作价值）时，同事务 `INSERT ... ON CONFLICT (user_id) DO NOTHING` + 回查，消除并发开户竞态
  - 提取时账户不存在等价于余额 0 → 直接 300001（不建账户）
- `balance DECIMAL(12,2) NOT NULL CHECK (balance >= 0)`：DB 级透支兜底，应用层校验失败（300001）先于 DB 约束触发
- 冗余统计列 `total_income` / `total_expense`：随流水同事务维护，供统计摘要 O(1) 读取；对账任务一并校验
- `status`：`ACTIVE` / `FROZEN`（成员被移除时冻结，禁止一切出入账，看板余额仍可见）
- `version BIGINT`：乐观锁（D13）

### 4.2 流水（money_transaction）

**不可变原则**：流水一经写入禁止 UPDATE/DELETE（mapper 层不提供 update/delete 方法，代码评审红线）。错账更正以新流水冲抵（M2 仅支持以新的手动增加/提取更正，见 §5.5）。

| 列 | 说明 |
|---|---|
| `direction` | `IN` / `OUT`（CHECK 约束） |
| `biz_type` | `MONTHLY_RULE` / `MANUAL_ADD` / `LEARNING_REWARD` / `WORK_VALUE` / `WITHDRAW`（CHECK 约束，新增类型须走迁移脚本修订约束） |
| `amount` | 恒正（CHECK > 0），方向由 direction 表达 |
| `balance_after` | 该笔完成后账户余额快照 —— 趋势计算与对账的核心依据 |
| `ref_type` / `ref_id` | 业务凭证关联：`RULE_GRANT` / `LEARNING_TASK` / `WORK_VALUE_RECORD`；手动收支为 NULL |
| `operator_user_id` | 操作者；定时结算为 NULL（审计另有记录） |
| `request_id` | M3 幂等键预留（D12），部分唯一索引 `WHERE request_id IS NOT NULL` |

索引设计（§11.1 详述）：`(family_id, created_at)` 覆盖看板/趋势/报表；`(account_id, created_at)` 覆盖成员流水分页；`(ref_type, ref_id)` 覆盖凭证反查。

### 4.3 记账原语 AccountTransactionService

全部账务变动的**唯一入口**，方法签名（示意）：

```java
MoneyTransaction apply(TxCommand cmd);   // cmd: accountId或userId(惰性开户), direction, bizType, amount, ref, operator, remark, requestId
```

执行步骤（单事务 `REQUIRED`，与调用方业务同事务提交）：

1. 取账户（不存在且 direction=IN → 惰性开户；OUT 且不存在 → 300001）
2. 账户 FROZEN → 300002
3. 校验 amount > 0（业务兜底 300004，Bean Validation 已在入口拦截）
4. OUT 校验 `balance >= amount`（应用层先判，DB CHECK 兜底）→ 不足 300001
5. 条件更新：
   `UPDATE money_account SET balance = balance ± :amount, total_income/total_expense ..., version = version + 1, updated_at = now() WHERE id = :id AND version = :version`
   行数 = 0 → 乐观锁冲突，重试（最多 3 次，重读 version）；重试耗尽 → 900003（可重试错误码，M0 约定）
6. 插入流水（含 `balance_after` = 更新后余额）
7. 返回流水实体（调用方回填凭证的 `transaction_id`）

**事务边界规则**：`apply` 不开新事务（`REQUIRED` 加入调用方事务），保证"凭证状态变更 + 记账 + 审计触发"原子提交；审计落库沿用 M1 `REQUIRES_NEW` 独立事务。

### 4.4 乐观锁与并发语义（D13）

- 冲突重试 3 次（指数退避 10/20/40ms），10 TPS 场景冲突概率极低，重试耗尽按系统可重试错误 900003 返回
- 并发取款正确性由集成测试固化：两笔并发取款之和 > 余额时，**恰好一笔成功**（ConcurrencyWithdrawIT）

---

## 5. 零花钱收支与查询

### 5.1 手动增加（deposit）

`POST /api/v1/families/{familyId}/deposits`（PARENT 对任意成员；CHILD 仅本人）

```json
{ "targetUserId": 3, "amount": 50.00, "remark": "生日红包" }
```

- 金额校验：`@DecimalMin("0.01")` + `@Digits(integer = 10, fraction = 2)`，失败 100001
- 孩子操作他人 → 数据级越权 100004（与 M1 口径一致）
- 同事务：记账（MANUAL_ADD/IN）+ 审计 MONEY_DEPOSIT
- 响应返回新流水（id、balanceAfter）

### 5.2 自由提取（withdraw，D2）

`POST /api/v1/families/{familyId}/withdrawals`（权限同上）

```json
{ "targetUserId": 3, "amount": 20.00, "remark": "买文具" }
```

- 余额不足 → 300001（message 含当前余额提示语，金额脱敏规则不涉及）
- 同事务：记账（WITHDRAW/OUT）+ 审计 MONEY_WITHDRAW

### 5.3 流水分页查询

`GET /api/v1/families/{familyId}/transactions?userId=&direction=&bizType=&from=&to=&page=&size=`

- 家庭成员均可（全透明 D8）；数据级校验仅针对 familyId
- 过滤项全部可选；`from/to` 为日期（闭区间）；默认按 `created_at DESC`，页长上限 50
- **防 N+1**：单条 SQL JOIN `app_user` 取账户主人昵称与操作者昵称；禁止 MyBatis 嵌套 select
- 响应 record：`{id, userId, nickname, direction, bizType, amount, balanceAfter, refType, refId, operatorNickname, remark, createdAt}`

### 5.4 错账更正（已知限制）

M2 不支持红冲/负向流水。操作错误（如金额录错）的更正路径：以新的手动增加/提取流水冲抵差额，remark 注明更正原因。该限制写入 API 文档；若产品后续要求红冲，扩展 `biz_type=REVERSAL` + 原流水 ref，模型无需变更。

---

## 6. 看板、趋势与榜单

### 6.1 看板

`GET /api/v1/families/{familyId}/dashboard`（家庭成员均可）

```json
{ "code": 0, "data": {
    "familyId": 1,
    "totalBalance": 386.50,
    "weeklyIncome": 60.00, "weeklyExpense": 12.50,
    "monthlyIncome": 260.00, "monthlyExpense": 45.00,
    "members": [
      { "userId": 1, "nickname": "妈妈", "role": "PARENT", "balance": 200.00 },
      { "userId": 3, "nickname": "小明", "role": "CHILD",  "balance": 86.50 }
    ] }, "traceId": "…" }
```

- 成员列表 = `family_member` 全量 LEFT JOIN `money_account`（未开户余额 0，D10）
- 周/月收支为流水单查询聚合（`family_id + created_at` 索引），周起点为周一 00:00（Clock 注入，D15）
- 单 SQL 完成成员 + 余额；收支两条聚合 SQL；共 3 条 SQL，无 N+1

### 6.2 趋势（D6）

`GET /api/v1/families/{familyId}/trends?granularity=DAY|WEEK&periods=&userId=`

- `granularity=DAY`：近 `periods` 日（默认 30，上限 90）每日**期末余额**曲线
- `granularity=WEEK`：近 `periods` 周（默认 12，上限 52，ISO 周一起）每周收支 + 期末余额
- `userId` 可选：缺省为家庭维度（全家账户合计），指定则为成员维度；孩子查询任意成员均放行（D8）

**计算算法**（不依赖快照表）：

```
1. 取 scope（家庭/成员）当前余额 B_now
2. 一次拉取窗口内全部流水（按 created_at 升序）
3. 窗口净流入 W = Σ(IN) − Σ(OUT)；窗口起点余额 B0 = B_now − W
4. 顺序扫描流水累计，输出每个周期末的余额；周粒度同时聚合各周收支
```

- 窗口内流水行数 = O(窗口内交易数)，家庭规模下（30 日通常 < 数百行）内存聚合开销可忽略
- 响应：`{granularity, scope, series: [{period, startDate, endDate, endingBalance, income?, expense?}]}`

### 6.3 周收入榜

`GET /api/v1/families/{familyId}/leaderboards/weekly-income`

- 统计口径：本周一 00:00 至当前，各成员 `direction=IN` 流水金额合计
- 单 SQL：`SELECT user_id, SUM(amount) ... WHERE family_id=? AND direction='IN' AND created_at >= :weekStart GROUP BY user_id`
- 无收入成员以 0 上榜；金额降序，**并列同名次**（dense ranking，service 层计算）
- 响应：`{weekStart, weekEnd, entries: [{rank, userId, nickname, income}]}`

---

## 7. 规则模块（rule）

### 7.1 包月规则模型

- 一条规则 = 家庭内某成员的每月固定发放：`beneficiary_user_id`、`amount`、`grant_day`（1–28，上限 28 规避月末日期差异）、`start_month`（`YYYY-MM`，生效起始月）、`end_month`（可空 = 长期）
- 状态机：`ACTIVE ⇄ PAUSED`；`ACTIVE/PAUSED → ARCHIVED`（归档终态，保留历史）
- **个性化配置**（roadmap 任务 1）：同一受益人可配置多条规则（基础月钱 + 兴趣班专项等），每成员上限 **10** 条（400004）；规则名家庭内唯一（400006）
- 规则 CRUD 仅 PARENT；列表/详情家庭成员可见（全透明）
- 删除约束：存在发放记录 → 400005（引导暂停/归档，保护账务链路完整）；无发放记录可物理删除

### 7.2 结算引擎（RuleSettlementService，D3）

**核心语义：当月应发未发即补。** 每日任务不做"今天是否发放日"的等值判断，而是扫描全部欠账：

```
RuleSettlementJob  每日 01:07（cron 可配置）
  for rule in (status=ACTIVE
               AND grant_day <= day(today)
               AND start_month <= thisMonth
               AND (end_month IS NULL OR end_month >= thisMonth)):
      if exists rule_grant_record(rule_id, thisMonth): continue      # 幂等
      if !familyMember(beneficiary): WARN + skip                     # 等待事件清理（§7.4）
      单事务：
        INSERT rule_grant_record(rule_id, thisMonth, amount)          # uk 兜底并发/重跑
        AccountTransactionService.apply(MONTHLY_RULE/IN, ref=RULE_GRANT/recordId)
        AuditService.record(RULE_GRANT_EXECUTED)
      唯一约束冲突（并发/重跑）→ 视为已发放，跳过
      其他异常 → 事务回滚无残留，ERROR 日志（含 traceId），次日任务自动重试
```

该语义天然覆盖三类场景：① 发放日当天正常发放；② 服务停机错过发放日后补发；③ 月中新建规则（`grant_day` 已过）当月即时补发。

**到期维护**（RuleExpiryJob，每日 01:23）：`end_month < 当月` 的 ACTIVE/PAUSED 规则置 `ARCHIVED`，结算扫描自然排除。

**多实例安全性**（D14）：`uk_grant_rule_month(rule_id, grant_month)` 保证同规则同月仅一条发放记录，蓝绿发布短双实例窗口重跑无副作用。

### 7.3 发放记录（rule_grant_record）

结算幂等锚点 + 发放历史查询源。列语义（DDL 见 §11.2）：

| 列 | 说明 |
|---|---|
| `rule_id` + `grant_month` | 幂等锚点（`uk_grant_rule_month`），同规则同月仅一条 |
| `amount` | 发放金额快照（记账时的规则金额，规则后续修改不影响历史） |
| `transaction_id` | 回填对应流水（附录 A 双向可追溯），定时结算发放时 `operator_user_id` 为 NULL |
| `status` | 恒 `SUCCESS`（失败即回滚不落记录，重试由次日任务承担；避免 FAILED 记录阻塞唯一键重试） |
| `granted_at` | 实际发放时刻（补发场景晚于当月发放日） |

规则详情页展示近 12 个月发放记录；审计动作 RULE_GRANT_EXECUTED 无操作人（`audit_log.user_id` 为 NULL）。

### 7.4 成员移除联动（D11）

```
user.FamilyService.removeMember
  └─ publish MemberRemovedEvent(familyId, userId)
       ├─ rule.MemberRemovedRuleListener：该成员全部 ACTIVE 规则 → PAUSED（审计 RULE_PAUSE，detail 标注触发源）
       └─ money.MemberRemovedMoneyListener：账户 FROZEN + 该成员 PENDING/SUBMITTED 学习任务 → CANCELED
```

- 监听器默认同步执行（与移除同事务，保证"移除即停发"无窗口期）；监听异常不回滚移除主流程（ERROR 日志 + 结算任务 §7.2 的成员校验兜底）
- 结算任务中的成员校验为第二道防线（事件丢失时欠发而非错发，宁漏勿错）

---

## 8. API 设计

### 8.1 总则（沿用 M0/M1）

- 统一 `Result` 包裹 + traceId；业务错误 HTTP 200 + code；100003/100004 → HTTP 401/403
- 受保护接口 `Authorization: Bearer {accessToken}`
- 金额字段 JSON 序列化为两位小数数字（`BigDecimal` + `@JsonFormat(shape=NUMBER)`）；端上按字符串接收处理由鸿蒙端规范约束（API 文档注明）

### 8.2 端点清单（24 个）

| # | 方法与路径 | 说明 | 鉴权 |
|---|---|---|---|
| 1 | GET `/api/v1/families/{familyId}/dashboard` | 家庭看板 | 家庭成员 |
| 2 | GET `/api/v1/families/{familyId}/transactions` | 流水分页查询 | 家庭成员 |
| 3 | GET `/api/v1/families/{familyId}/trends` | 趋势（日/周粒度） | 家庭成员 |
| 4 | GET `/api/v1/families/{familyId}/leaderboards/weekly-income` | 周收入榜 | 家庭成员 |
| 5 | POST `/api/v1/families/{familyId}/deposits` | 手动增加 | PARENT 任意成员；CHILD 仅本人 |
| 6 | POST `/api/v1/families/{familyId}/withdrawals` | 自由提取 | PARENT 任意成员；CHILD 仅本人 |
| 7 | POST `/api/v1/families/{familyId}/rules` | 创建包月规则 | PARENT |
| 8 | GET `/api/v1/families/{familyId}/rules` | 规则列表（含当月发放状态） | 家庭成员 |
| 9 | GET `/api/v1/families/{familyId}/rules/{ruleId}` | 规则详情 + 发放记录 | 家庭成员 |
| 10 | PUT `/api/v1/families/{familyId}/rules/{ruleId}` | 修改规则（金额/发放日/名称/备注/起止月） | PARENT |
| 11 | POST `/api/v1/families/{familyId}/rules/{ruleId}/pause` | 暂停 | PARENT |
| 12 | POST `/api/v1/families/{familyId}/rules/{ruleId}/resume` | 恢复 | PARENT |
| 13 | POST `/api/v1/families/{familyId}/rules/{ruleId}/archive` | 归档 | PARENT |
| 14 | DELETE `/api/v1/families/{familyId}/rules/{ruleId}` | 删除（无发放记录时） | PARENT |
| 15 | POST `/api/v1/families/{familyId}/learning-tasks` | 创建学习任务 | PARENT |
| 16 | GET `/api/v1/families/{familyId}/learning-tasks` | 任务列表（status/assignee 过滤） | 家庭成员 |
| 17 | POST `/api/v1/families/{familyId}/learning-tasks/{taskId}/submit` | 孩子提交完成 | CHILD 仅本人被指派任务 |
| 18 | POST `/api/v1/families/{familyId}/learning-tasks/{taskId}/approve` | 家长确认并发放 | PARENT |
| 19 | POST `/api/v1/families/{familyId}/learning-tasks/{taskId}/reject` | 驳回（可附理由） | PARENT |
| 20 | POST `/api/v1/families/{familyId}/learning-tasks/{taskId}/cancel` | 取消（发放前） | PARENT |
| 21 | POST `/api/v1/families/{familyId}/work-values` | 记录工作价值并发放 | PARENT |
| 22 | GET `/api/v1/families/{familyId}/work-values` | 工作价值记录列表（月份过滤） | 家庭成员 |
| 23 | GET `/api/v1/families/{familyId}/reports/income-expense?month=YYYY-MM` | 月度收支报表 | 家庭成员 |
| 24 | GET `/api/v1/families/{familyId}/statistics/summary` | 家庭统计摘要 | 家庭成员 |

### 8.3 关键交互示例

**孩子提交学习任务 → 家长确认发放**：

```json
POST /families/1/learning-tasks/7/submit
{ "note": "本周英语打卡 7 天全部完成" }
→ { "code": 0, "data": { "taskId": 7, "status": "SUBMITTED" }, … }

POST /families/1/learning-tasks/7/approve
→ { "code": 0, "data": { "taskId": 7, "status": "APPROVED",
    "rewardAmount": 20.00, "transactionId": 1093, "balanceAfter": 86.50 }, … }
```

**余额不足提取**：

```json
{ "code": 300001, "message": "余额不足，当前余额 5.00 元", "data": null,
  "traceId": "8f3a2b1c…", "timestamp": 1787654321000 }
```

**规则创建（个性化多规则之一）**：

```json
POST /families/1/rules
{ "beneficiaryUserId": 3, "ruleName": "足球训练月钱", "amount": 30.00,
  "grantDay": 5, "startMonth": "2026-09", "endMonth": null, "remark": "" }
→ { "code": 0, "data": { "ruleId": 4, "status": "ACTIVE", … } }
```

**月度收支报表**：

```json
GET /families/1/reports/income-expense?month=2026-09
{ "code": 0, "data": {
    "month": "2026-09", "totalIncome": 460.00, "totalExpense": 75.50, "net": 384.50,
    "incomeByType": { "MONTHLY_RULE": 280.00, "MANUAL_ADD": 100.00,
                      "LEARNING_REWARD": 40.00, "WORK_VALUE": 40.00 },
    "expenseByType": { "WITHDRAW": 75.50 },
    "members": [ { "userId": 3, "nickname": "小明",
                   "income": 150.00, "expense": 30.50, "net": 119.50 } ] } }
```

### 8.4 错误码（M2 三段，UserErrorCode 模式一致）

**MoneyErrorCode（30xxxx）**

| 错误码 | 含义 | 客户端处理建议 |
|---|---|---|
| 300001 | 余额不足 | 提示当前余额，不可重试 |
| 300002 | 账户已冻结（成员已移出家庭） | 不可重试 |
| 300003 | 流水记录不存在 | 不可重试 |
| 300004 | 金额必须大于 0 | 修正输入 |
| 300005 | 学习任务不存在 | 不可重试 |
| 300006 | 学习任务当前状态不允许该操作 | 刷新任务状态 |
| 300007 | 工作价值记录不存在 | 不可重试 |

**RuleErrorCode（40xxxx）**

| 错误码 | 含义 | 客户端处理建议 |
|---|---|---|
| 400001 | 规则不存在 | 不可重试 |
| 400002 | 规则当前状态不允许该操作 | 刷新规则状态 |
| 400003 | 发放日须在 1–28 之间 | 修正输入 |
| 400004 | 该成员规则数量已达上限（10 条） | 提示归档旧规则 |
| 400005 | 规则已有发放记录，不可删除（请暂停或归档） | 引导暂停/归档 |
| 400006 | 家庭内规则名称重复 | 提示更名 |

**FinanceErrorCode（50xxxx）**

| 错误码 | 含义 | 客户端处理建议 |
|---|---|---|
| 500001 | 报表月份格式无效（应为 YYYY-MM） | 修正输入 |
| 500002 | 报表月份不可晚于当月 | 修正输入 |

三个枚举均需通过段位与唯一性单测（仿 `UserErrorCodeTest` / `CommonErrorCodeTest`）。

---

## 9. 审计动作增量（AuditAction）

| 动作 | 触发点 |
|---|---|
| MONEY_DEPOSIT / MONEY_WITHDRAW | 手动增加 / 提取成功 |
| RULE_CREATE / RULE_UPDATE / RULE_PAUSE / RULE_RESUME / RULE_ARCHIVE / RULE_DELETE | 规则管理链路（事件触发的暂停在 detail 标注 `trigger=MEMBER_REMOVE`） |
| RULE_GRANT_EXECUTED | 结算发放成功（detail：ruleId/month/amount） |
| LEARNING_TASK_CREATE / LEARNING_TASK_SUBMIT / LEARNING_TASK_APPROVE / LEARNING_TASK_REJECT / LEARNING_TASK_CANCEL | 学习任务状态机各迁移 |
| WORK_VALUE_RECORD | 工作价值记录并发放 |
| RECONCILE_MISMATCH | 对账发现不一致（同步 SECURITY ERROR 告警） |

审计落库机制沿用 M1（`REQUIRES_NEW`、失败不阻断业务、traceId 关联）。

---

## 10. 学习任务、工作价值与财务模块

### 10.1 学习任务状态机（D4）

```
              家长创建
                │
                ▼
  ┌───────── PENDING ─────────┐
  │             │ 孩子提交      │ 家长取消
  │             ▼              ▼
  │         SUBMITTED       CANCELED（终态）
  │           │   │
  │  家长确认 │   │ 家长驳回
  │           ▼   ▼
  │      APPROVED  REJECTED ──┐
  │      （同事务发放奖励）      │ 孩子重新提交
  │                            └──→ SUBMITTED
  └── CANCELED（发放前均可取消）
```

- 非法迁移一律 300006（如 APPROVED 后取消 → 拒绝，奖励已发放只能走更正流水）
- `submit` 仅限被指派孩子本人（他人 → 100004）；`approve/reject/cancel` 仅 PARENT
- `approve` 同事务：任务置 APPROVED + 记账（LEARNING_REWARD/IN，ref=LEARNING_TASK/taskId）+ 回填 `transaction_id` + 审计
- 驳回理由 `reject_reason` 回填，孩子可见；重新提交更新 `submit_note` 与 `submitted_at`
- 截止时间 `deadline`（可空）：M2 仅展示与过期标记（列表返回 `overdue` 布尔），不做自动取消

### 10.2 工作价值（D5）

- 父母同为账户主体（M1 家庭创建者本身是 family_member，惰性开户机制自动覆盖）
- `POST /work-values`：`{ workMonth: "2026-09", salaryIncome: 12000.00, allowanceAmount: 200.00, workSummary: "项目交付" }`
  - 操作者即入账对象（记录自己的工资与发放；M1 单家长约束下自然成立，多家长扩展时语义不变）
  - `salary_income` 为参考信息（`DECIMAL(14,2)`，允许 0）；`allowance_amount` 为实际入账金额（> 0）
  - 同月允许多条记录（工资 + 奖金分录），不做唯一约束，保持流水不可变语义
- 同事务：插记录 + 记账（WORK_VALUE/IN，ref=WORK_VALUE_RECORD/recordId）+ 审计
- 列表支持 `?month=` 过滤；全透明，孩子可见（教育意义：理解父母工作创造价值）

### 10.3 财务模块（finance，D7/D9）

- `ReportService` 经 `MoneyQueryService` 聚合门面取数（finance 不直接触碰 money 的 mapper）
- **月度收支报表**：按月聚合流水 —— 总收入/总支出/净额、按 biz_type 分项、按成员分项；month 参数校验（500001/500002）
- **统计摘要**：`{totalBalance, currentMonthIncome, currentMonthExpense, allTimeIncome, allTimeExpense, memberCount}` —— totalBalance 与累计收支由账户表冗余列 O(1) 汇总，当月收支一条聚合 SQL
- 聚合全部走 `(family_id, created_at)` 索引；单家庭单月流水量级 < 数百行，同步聚合满足 P95（D7 决策前提）

### 10.4 报表同步聚合的合规性说明（D7）

mission 约束"耗时操作必须异步处理"——本设计判定 M2 报表**不构成耗时操作**（基准数据量下实测目标 < 100ms，验收门槛 500ms）。为防前提失效：

1. M2 集成测试包含报表端点 P95 基准（§12.5）
2. M6 性能测试复测；超 500ms 即触发异步化回补

### 10.5 异步化回补预案（仅文档化，M2 不实现）

`report_task(id, family_id, params, status PROCESSING/DONE/FAILED, result JSONB)` + `@Async` 虚拟线程执行 + 客户端轮询。表结构与 M3 幂等键、M5 通知均可复用。

---

## 11. 数据模型与迁移

### 11.1 V4__create_money_account_transaction.sql

```sql
CREATE TABLE money_account (
    id            BIGSERIAL PRIMARY KEY,
    family_id     BIGINT NOT NULL REFERENCES family (id),
    user_id       BIGINT NOT NULL REFERENCES app_user (id),
    balance       DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    total_income  DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_expense DECIMAL(14,2) NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE / FROZEN
    version       BIGINT NOT NULL DEFAULT 0,                  -- 乐观锁（§4.3）
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_money_account_user UNIQUE (user_id)
);
CREATE INDEX idx_money_account_family ON money_account (family_id);

CREATE TABLE money_transaction (
    id               BIGSERIAL PRIMARY KEY,
    family_id        BIGINT NOT NULL REFERENCES family (id),
    account_id       BIGINT NOT NULL REFERENCES money_account (id),
    user_id          BIGINT NOT NULL REFERENCES app_user (id),   -- 账户主人（冗余，免 join）
    direction        VARCHAR(8)  NOT NULL CHECK (direction IN ('IN','OUT')),
    biz_type         VARCHAR(24) NOT NULL CHECK (biz_type IN
        ('MONTHLY_RULE','MANUAL_ADD','LEARNING_REWARD','WORK_VALUE','WITHDRAW')),
    amount           DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    balance_after    DECIMAL(12,2) NOT NULL,
    ref_type         VARCHAR(24) CHECK (ref_type IN ('RULE_GRANT','LEARNING_TASK','WORK_VALUE_RECORD')),
    ref_id           BIGINT,
    operator_user_id BIGINT,                                    -- 定时结算为 NULL
    remark           VARCHAR(128),
    request_id       VARCHAR(64),                               -- M3 幂等键预留（D12）
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mtxn_family_time  ON money_transaction (family_id, created_at);
CREATE INDEX idx_mtxn_account_time ON money_transaction (account_id, created_at);
CREATE INDEX idx_mtxn_ref          ON money_transaction (ref_type, ref_id);
CREATE UNIQUE INDEX uk_mtxn_request ON money_transaction (request_id)
    WHERE request_id IS NOT NULL;                               -- 部分唯一索引（M3 启用判定）
```

### 11.2 V5__create_rule_grant.sql

```sql
CREATE TABLE money_rule (
    id                  BIGSERIAL PRIMARY KEY,
    family_id           BIGINT NOT NULL REFERENCES family (id),
    beneficiary_user_id BIGINT NOT NULL REFERENCES app_user (id),
    rule_name           VARCHAR(32) NOT NULL,
    amount              DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    grant_day           SMALLINT NOT NULL CHECK (grant_day BETWEEN 1 AND 28),
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / PAUSED / ARCHIVED
    start_month         CHAR(7) NOT NULL,                        -- YYYY-MM
    end_month           CHAR(7),                                 -- NULL = 长期
    remark              VARCHAR(128),
    created_by          BIGINT NOT NULL REFERENCES app_user (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_rule_family_name UNIQUE (family_id, rule_name)
);
CREATE INDEX idx_rule_settle_scan ON money_rule (status, grant_day);   -- 结算扫描
CREATE INDEX idx_rule_beneficiary ON money_rule (beneficiary_user_id);

-- 结算幂等锚点（§7.2）：同规则同月仅一条
CREATE TABLE rule_grant_record (
    id             BIGSERIAL PRIMARY KEY,
    rule_id        BIGINT NOT NULL REFERENCES money_rule (id),
    grant_month    CHAR(7) NOT NULL,
    amount         DECIMAL(12,2) NOT NULL,
    transaction_id BIGINT REFERENCES money_transaction (id),
    status         VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    granted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_grant_rule_month UNIQUE (rule_id, grant_month)
);
```

### 11.3 V6__create_learning_task_work_value.sql

```sql
CREATE TABLE learning_task (
    id               BIGSERIAL PRIMARY KEY,
    family_id        BIGINT NOT NULL REFERENCES family (id),
    title            VARCHAR(64) NOT NULL,
    reward_amount    DECIMAL(12,2) NOT NULL CHECK (reward_amount > 0),
    assignee_user_id BIGINT NOT NULL REFERENCES app_user (id),
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING',
        -- PENDING / SUBMITTED / APPROVED / REJECTED / CANCELED
    deadline         DATE,
    submit_note      VARCHAR(256),
    submitted_at     TIMESTAMPTZ,
    reviewed_by      BIGINT REFERENCES app_user (id),
    reviewed_at      TIMESTAMPTZ,
    reject_reason    VARCHAR(256),
    transaction_id   BIGINT REFERENCES money_transaction (id),
    created_by       BIGINT NOT NULL REFERENCES app_user (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ltask_family_time    ON learning_task (family_id, created_at);
CREATE INDEX idx_ltask_assignee_state ON learning_task (assignee_user_id, status);

CREATE TABLE work_value_record (
    id               BIGSERIAL PRIMARY KEY,
    family_id        BIGINT NOT NULL REFERENCES family (id),
    parent_user_id   BIGINT NOT NULL REFERENCES app_user (id),
    work_month       CHAR(7) NOT NULL,                      -- 归属月 YYYY-MM
    salary_income    DECIMAL(14,2) NOT NULL DEFAULT 0 CHECK (salary_income >= 0),
    allowance_amount DECIMAL(12,2) NOT NULL CHECK (allowance_amount > 0),
    work_summary     VARCHAR(256),
    transaction_id   BIGINT REFERENCES money_transaction (id),
    recorded_by      BIGINT NOT NULL REFERENCES app_user (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_work_value_family_month ON work_value_record (family_id, work_month);
CREATE INDEX idx_work_value_parent       ON work_value_record (parent_user_id, work_month);
```

设计要点：

- 金额全部 `DECIMAL(12,2)`（工资参考列 14,2）；`CHECK (balance >= 0)` 为 DB 级透支兜底
- 流水 `user_id`/`family_id` 冗余换取查询免 join（第三范式权衡：以查询模式优先，一致性由记账原语同事务保证，对账任务校验）
- 迁移脚本一经提交永不修改，回滚走新脚本前向修复（M0 既定规范）

---

## 12. 测试设计

### 12.1 单元测试

| 测试类 | 覆盖点 |
|---|---|
| AccountTransactionServiceTest | 入账/出账/惰性开户竞态（mock DuplicateKey 重试）/冻结拒绝/乐观锁冲突重试与耗尽/balance_after 正确性 |
| AmountPrecisionTest | 0.1+0.2=0.3（BigDecimal）、连续累加无漂移、DECIMAL(12,2) 上限 9999999999.99、balance=amount 恰好取空、amount+0.01 拒绝 |
| RuleSettlementServiceTest | 发放日当天发放/欠发补发/已发跳过（幂等）/PAUSED 不发/end_month 过期不发/非成员跳过/start_month 未到不发 |
| LearningTaskServiceTest | 状态机全部合法迁移 + 非法迁移 300006（参数化）/重新提交/取消时机 |
| WorkValueServiceTest | 记录 + 入账原子性、多记录同月、salary 0 允许 |
| TrendServiceTest | 日/周序列正确性：跨窗口流水的 B0 推导、空窗口、周一起始边界（固定 Clock） |
| LeaderboardServiceTest | 排序/并列同名次/无收入补 0 |
| DashboardServiceTest | 未开户成员余额 0、周月聚合边界（周一 00:00） |
| ReportServiceTest | 分项聚合、月份校验 500001/500002 |
| MoneyErrorCodeTest / RuleErrorCodeTest / FinanceErrorCodeTest | 段位 30/40/50xxxx、无重复值 |

### 12.2 集成测试套件（RestAssured + Testcontainers PG，沿用 M1 形态）

| 套件 | 场景 |
|---|---|
| MoneyFlowIT | 增加 → 提取 → 流水分页 → 余额与流水逐笔 balance_after 一致 |
| WithdrawBoundaryIT | 余额恰好取空、超额 300001、冻结账户 300002 |
| ConcurrencyWithdrawIT | 两并发取款之和 > 余额 → 恰好一笔成功、余额与流水一致 |
| DashboardTrendIT | 造数后断言看板汇总、日/周趋势序列、榜单名次 |
| RuleCrudIT | CRUD 全分支（含 400004/400005/400006）、权限（CHILD 写 → 100004） |
| RuleSettlementIT | 注入固定 Clock：发放日发放/停机补发/重复执行幂等/移除成员后停发（事件链路） |
| LearningTaskFlowIT | 创建 → 提交 → 批准 → 余额与流水断言；驳回重提；取消；越权提交 100004 |
| WorkValueIT | 记录 → 父母账户入账 → 流水可查 |
| MemberRemoveCascadeIT | 移除孩子 → 规则 PAUSED、账户 FROZEN、任务 CANCELED、结算不再命中 |
| ReportIT | 月度报表分项与手工核算一致；统计摘要 |
| PermissionMatrixIT（扩展） | 附录 B 全端点 × 身份参数化 |
| AuditTrailIT | 关键操作后 `audit_log` 存在对应动作行（§9 全集抽检） |

### 12.3 SQL 安全与 N+1 审查（roadmap DoD 落地方式）

- **参数化**：代码评审清单固化"仅 `#{}`"；集成测试全量跑通即证明 SQL 可执行性；额外在 PR 模板加入 `${}` 使用专项说明栏（M0 红线）
- **N+1**：MyBatis 配置禁用嵌套 select（不配置 association 的 select 属性）；流水/看板/报表查询逐条 SQL 评审

### 12.4 覆盖率与门禁

JaCoCo 80% BUNDLE 门禁沿用；money/rule/finance 新增类全部计入。

### 12.5 性能基准（roadmap DoD：P95 ≤ 500ms）

- **基准数据量**：种子脚本注入 50 家庭 × 8 成员 × 36 个月流水 ≈ **5 万条**（含规则发放/手动/任务/工作价值/提取各类型），为真实家庭数据量的 50 倍冗余
- **测量**：`PerformanceBaselineIT`（`@Tag("performance")`，surefire `excludedGroups=performance` 默认不在 `mvn verify` 执行；手动触发：`mvn test -Dgroups=performance "-Dsurefire.excludedGroups="`，避免拖慢 CI）：种子数据经 `PerformanceDataSeeder`（JdbcTemplate 批量直插、显式 id 段 ≥ 1,000,000 + setval 重对齐、余额不变式）注入；对 dashboard / trends / transactions / income-expense 四类端点各 10 次热身 + 200 次计时，断言 P95 ≤ 500ms
- 索引命中以 `EXPLAIN` 抽检记录存档（四条核心查询）

---

## 13. 配置增量

```yaml
pocket-money:
  money:
    settlement:
      enabled: ${RULE_SETTLEMENT_ENABLED:true}     # 集成测试置 false，直调 service 验证
      cron: ${RULE_SETTLEMENT_CRON:0 7 1 * * *}    # 每日 01:07
    reconcile:
      cron: ${RECONCILE_CRON:0 13 2 * * 0}         # 每周日 02:13
  rule:
    max-per-member: ${RULE_MAX_PER_MEMBER:10}
    expiry-cron: ${RULE_EXPIRY_CRON:0 23 1 * * *}  # 每日 01:23
```

- `SchedulingConfig`：`@EnableScheduling` + 虚拟线程 `TaskScheduler`（JDK 25，`Executors.newVirtualThreadPerTaskExecutor`）；`enabled=false` 时任务 Bean 不注册
- 对账任务实现与告警：`account.balance` ≠ 该账户最新流水 `balance_after` → 审计 RECONCILE_MISMATCH + SECURITY ERROR（SLS 告警规则 M7 配置）；只告警不自动更正（人工核查，资金数据宁可保守）

---

## 14. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 |
|---|---|---|---|
| T1 | 账户与流水基建：V4–V6 脚本、domain、AccountService/AccountTransactionService（乐观锁/惰性开户/记账原语）、Clock 与 Scheduling 配置、AuditAction 增量 | — | 3 人天 |
| T2 | 收支 API：手动增加/自由提取/流水分页（防 N+1） | T1 | 2 人天 |
| T3 | 看板 + 趋势（日/周）+ 周收入榜 | T1, T2 | 3 人天 |
| T4 | 规则 CRUD（含状态机、上限、唯一名、删除约束）+ V5 | T1 | 2.5 人天 |
| T5 | 结算引擎：SettlementJob/ExpiryJob/幂等/成员移除事件链路（user 发布 + rule/money 监听） | T4 | 3 人天 |
| T6 | 学习任务：状态机 + 5 个操作端点 + 发放集成 | T1 | 3 人天 |
| T7 | 工作价值：记录 + 发放 + 列表 | T1 | 1.5 人天 |
| T8 | finance：MoneyQueryService 门面 + 月度报表 + 统计摘要 | T1–T3 | 2 人天 |
| T9 | 集成测试全集（§12.2，含并发/精度/结算时钟注入/权限矩阵扩展）+ 种子数据脚本 + PerformanceBaselineIT | T2–T8 | 4.5 人天 |
| T10 | OpenAPI 注解（M2 端点 + 错误码说明）、API 文档更新、DoD 验证收尾 | T9 | 1 人天 |

合计约 **25.5 人天**。roadmap 排期 4 周（20 个工作日/人）：

- **2 人投入**：约 13 人天/人，余量充足（推荐，账务类代码强制双人评审也要求 ≥2 人，见 roadmap 风险应对）
- **1 人投入**：超载约 28%，需启用 roadmap 弹性条款（阶段顺序不变、时间平移）或在评审时裁剪（候选裁剪项：周趋势与榜单降级为仅日趋势、报表仅保留摘要）

关键路径：T1 → T4 → T5（结算引擎）与 T1 → T6（任务链路）并行；T9 为收尾闸门。

---

## 15. 验收标准（DoD，与 roadmap 一致并细化）

- [ ] 全部核心 API 的 MyBatis 查询为 `#{}` 参数化，PR 评审清单与 `${}` 专项说明记录在案，无 SQL 拼接
- [ ] 金额精度用例（§12.1 AmountPrecisionTest 全集）通过；账务变动 100% 经 `AccountTransactionService` 单入口且全事务化（架构测试断言无旁路）
- [ ] 并发取款测试通过（恰好一笔成功）；账户余额 DB 级 CHECK 兜底验证
- [ ] 结算引擎：发放日发放、欠发补发、重复执行幂等三类用例通过；成员移除后停发链路（事件）测试通过
- [ ] 学习任务全状态机 + 工作价值发放链路集成测试通过
- [ ] 基准数据量（5 万流水）下 dashboard / trends / transactions / income-expense P95 ≤ 500ms（PerformanceBaselineIT 报告存档）
- [ ] 关键路径集成测试全绿（Testcontainers + PostgreSQL，Docker 不可用时沿用 M1 托底策略）
- [ ] 单测覆盖率 ≥ 80%（JaCoCo）；`mvn clean verify` 全门禁绿。说明：roadmap 原表述"SonarQube 门禁通过"按 M0 决策 D8 于 M6 接入 SonarQube，M2 以 Checkstyle/PMD/SpotBugs 门禁等效执行；roadmap M2 DoD 表述已于 2026-08-19 同步修订
- [ ] 对账任务上线（每周执行），RECONCILE_MISMATCH 告警路径演练一次（集成测试注入不一致数据）
- [ ] OpenAPI 文档 M2 部分产出（24 端点 + 三段错误码说明），README 更新
- [ ] 16 个新增审计动作落 `audit_log`（AuditTrailIT 断言）

---

## 16. 风险与遗留事项

| # | 风险/事项 | 影响 | 应对 |
|---|---|---|---|
| R1 | 流水不可变 + 无红冲，错账只能冲抵流水更正 | 运营纠错体验弱 | API 文档明示更正路径；红冲作为后续迭代候选（模型已兼容） |
| R2 | M2 阶段资金接口无幂等拦截（仅预留列） | 端上异常重试理论可致重复入账 | M2 端上暂不接入自动重试；M3 实现幂等键协议（request_id 列与部分唯一索引已就位） |
| R3 | 结算任务失败仅 ERROR 日志 + 次日重试，无即时告警通道 | 发放延迟最多 1 天 | 10 TPS 家庭场景可接受；SLS 告警规则 M7 接入后升级为实时告警；对账任务二次兜底 |
| R4 | 趋势/榜单依赖窗口内流水全量拉取内存聚合 | 极端活跃家庭窗口流水过大 | periods 上限（日 90/周 52）+ 家庭场景行数评估 < 数千行可控；超限再引入物化视图 |
| R5 | 单家长模型下工作价值仅覆盖创建者 | 双家长场景需扩展 | 表结构（parent_user_id 任意成员）已兼容，M1 R6 同源风险 |
| R6 | 报表同步聚合为决策偏差（D7） | 数据量增长后可能突破 500ms | §10.4 双保险：M2 基准测试 + M6 复测，超限即启用 §10.5 预案 |
| R7 | `@Scheduled` 依赖单实例假设 | 未来水平扩展双跑 | 幂等锚点已保证正确性；横向扩展时引入 ShedLock 即可（文档化） |

遗留至后续阶段：幂等键协议（M3）、离线同步（M3）、AI 语音记账（M4）、通知提醒（M5）、红冲/退款（待定）、消费限额与预算（待定）、多家长工作价值（随双家长扩展）。

---

## 附录 A：业务凭证与流水关联关系

| 业务凭证 | ref_type | biz_type | direction |
|---|---|---|---|
| rule_grant_record | RULE_GRANT | MONTHLY_RULE | IN |
| learning_task（APPROVED） | LEARNING_TASK | LEARNING_REWARD | IN |
| work_value_record | WORK_VALUE_RECORD | WORK_VALUE | IN |
| 手动增加（无凭证） | NULL | MANUAL_ADD | IN |
| 提取（无凭证） | NULL | WITHDRAW | OUT |

凭证表均回填 `transaction_id`（记账返回后同事务更新），形成凭证 ↔ 流水双向可追溯。

## 附录 B：权限矩阵（PermissionMatrixIT 扩展基准）

图例：✅ 允许；`100003` 未认证；`100004` 无权限（含跨家庭数据级拒绝）；「本人」限操作者=资源主体。匿名访问全部 M2 端点一律 100003；跨家庭 PARENT 一律 100004（下表省略该两列，仅列本家庭身份差异）。

| # | 端点 | CHILD（本家庭） | PARENT（本家庭） |
|---|---|---|---|
| 1–4 | 看板 / 流水 / 趋势 / 榜单 | ✅ 全量（全透明 D8） | ✅ |
| 5 | POST deposits | ✅ 仅 targetUserId=本人，否则 100004 | ✅ 任意成员 |
| 6 | POST withdrawals | ✅ 仅本人，否则 100004 | ✅ 任意成员 |
| 7, 10–14 | 规则写操作 | 100004 | ✅（受 40xxxx 业务约束） |
| 8, 9 | 规则读 | ✅ | ✅ |
| 15, 18–20 | 任务创建/批准/驳回/取消 | 100004 | ✅ |
| 16 | 任务列表 | ✅ | ✅ |
| 17 | 任务提交 | ✅ 仅本人被指派任务，否则 100004 | 100004（提交为指派孩子专属） |
| 21 | POST work-values | 100004 | ✅ |
| 22 | work-values 列表 | ✅ | ✅ |
| 23, 24 | 报表 / 统计 | ✅ | ✅ |

## 附录 C：核心时序（文本）

**包月规则结算（每日 01:07）**

```
RuleSettlementJob
  └─ RuleSettlementService.settleDue(today)
       ├─ MoneyRuleMapper.findDueRules(status=ACTIVE, grant_day<=day, 月份区间内)
       ├─ for rule: RuleGrantRecordMapper.exists(ruleId, month)? → skip（幂等）
       │            FamilyMemberMapper.isMember(beneficiary)?   → skip + WARN（防线二）
       └─ 单事务：INSERT rule_grant_record（uk 冲突=已发放, skip）
                 → AccountTransactionService.apply(MONTHLY_RULE/IN)
                     ├─ money_account 条件更新（乐观锁）
                     └─ INSERT money_transaction（balance_after）
                 → AuditService.record(RULE_GRANT_EXECUTED)
```

**学习任务批准发放**

```
PARENT → POST /learning-tasks/{id}/approve
  @PreAuthorize PARENT → FamilyAccessChecker.requireMember
  LearningTaskService.approve（单事务）
    ├─ 状态断言 SUBMITTED（否则 300006）
    ├─ UPDATE learning_task → APPROVED（reviewed_by/at）
    ├─ AccountTransactionService.apply(LEARNING_REWARD/IN, ref=LEARNING_TASK/id)
    │    └─ （账户不存在则惰性开户）
    ├─ 回填 learning_task.transaction_id
    └─ AuditService.record(LEARNING_TASK_APPROVE)
PARENT ← 200 {status:APPROVED, transactionId, balanceAfter}
（孩子看板余额实时增加；流水对全家可见）
```

**并发取款（乐观锁）**

```
TX-A: UPDATE money_account SET balance=balance-80, version=v+1 WHERE id=7 AND version=5  → rows=1
TX-B: UPDATE money_account SET balance=balance-60, version=v+1 WHERE id=7 AND version=5  → rows=0
  └─ 重试：重读 version=6，余额已不足 → 300001
结果：恰好一笔成功，余额与流水一致（ConcurrencyWithdrawIT 固化）
```

## 附录 D：文档变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.1 | 2026-08-19 | 评审通过，作为 M2 开发基线；D7（报表同步）决策与 SonarQube 门禁口径已同步修订至 roadmap |
| v1.2 | 2026-08-26 | M2 测试补全后回改：① §3.2 ClockConfig 时区由 UTC 更正为 Asia/Shanghai 业务时区（与实现一致，业务日边界按北京时间，见 ClockConfig javadoc）；② §3.4 跨模块取数契约补充 `FamilyService.listMembers`（报表成员行全员上榜依赖）；③ §7.3 补齐 rule_grant_record 列语义（amount 快照 / transaction_id 回填 / status 恒 SUCCESS / RULE_GRANT_EXECUTED 无操作人）；④ §12.5 补充性能种子器实现要点与手动触发命令 |

---
*本设计已于 2026-08-19 评审通过，作为 M2 开发基线；D7（报表同步）决策与 SonarQube 门禁口径已同步修订至 roadmap。实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*