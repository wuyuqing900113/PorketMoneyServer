# M5 通知与事件驱动 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（M5 章节）、`M0-detailed-design.md`、`M1-detailed-design.md`、`M2-detailed-design.md`、`M3-detailed-design.md`、`M4-detailed-design.md`、`docs/version-matrix.md`
> 文档版本：v1.0（2026-08-28，M5 开发基线）
> 适用范围：M5 阶段（第 15 周，11-23 ~ 11-29）
> M4 基线：`mvn clean verify` 全绿（Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥ 80% 门禁通过）；AI 抽象层四端口 + `StubChatPort`、意图目录 11 意图、二次确认状态机、Resilience4j CB/TimeLimiter/Retry、`AiCleanupJob` 就绪；`MemberRemovedEvent`（M2 D11）已落地首个 Spring Event 场景

---

## 1. 概述

### 1.1 目标

在 M4 AI 交互与 M2/M3 账务、规则、幂等基线之上，补齐通知模块，并以 Spring Event 机制消除「账务变动 → 通知」这一新引入的跨模块耦合，交付：

- **通知模块**：站内信通知中心（`notification` 接收人维度）+ 通知类型目录（账务变动 / 余额不足 / 规则到期）+ 外部推送通道抽象（`PushPort`，鸿蒙 Push 待选型）
- **事件驱动改造**：`MoneyTransactionCreatedEvent`（记账成功后发布）与 `RuleArchivedEvent`（规则到期归档发布）为事件源，通知模块以监听器消费，生产方零感知通知存在
- **可靠投递**：站内信与账务同事务原子落库；外部通道经 `notification_delivery` + `NotificationRelayJob` 异步投递，失败退避重试 + 死信记录（mission「耗时操作异步化」+ M5 任务 3）
- **依赖治理**：ArchUnit 固化「通知单向消费事件、生产方不得依赖 notify」的分层规则（DoD 依赖关系审查）

### 1.2 范围（In Scope）

- 事件契约：`money/event/MoneyTransactionCreatedEvent`、`rule/event/RuleArchivedEvent`（生产者持有，同 `MemberRemovedEvent` 惯例）
- 事件发布点：`AccountTransactionService.apply`（记账成功后）、`RuleExpiryJob`（规则到期归档）
- 通知模块：V9 迁移（`notification` / `notification_delivery`）、`NotificationType` 目录、文案模板、接收人解析、`NotificationService`、`NotificationController`（4 端点）、`NotifyErrorCode`（70xxxx）
- 投递与重试：`PushPort` 端口 + `NoopPushPort` 默认、`NotificationRelayJob`、退避重试与死信、已读通知清理任务
- 审计增补：`NOTIFY_DELIVERED` / `NOTIFY_DELIVERY_FAILED`
- ArchUnit 规则增补 + 集成测试全集

### 1.3 非目标（Out of Scope）

| 事项 | 归属阶段 |
|---|---|
| 真实鸿蒙 Push / 短信 / 邮件通道接入与选型 | 前置决策清单 #4（第 12 周前，实际未拍板）；接入归通道拍板后（D39） |
| 通知偏好设置（用户按类型开关、免打扰时段） | 后续里程碑（M5 v1 统一推送，不做偏好） |
| 账户级个性化余额阈值（M5 v1 用家庭级统一阈值） | 后续里程碑（D44） |
| 规则「即将到期」提前提醒（M5 v1 仅到期归档时通知） | 后续里程碑（D41） |
| 看板缓存化 + 缓存失效事件下游 | 非 M5 范围（看板当前同步聚合 M2 D7，无缓存可失效，D41） |
| 审计链路事件化迁移 | 不做（REQUIRES_NEW 内联已具备解耦语义，D41） |
| JMeter 10 TPS 压测（通知链路） | M6（M5 以本地基准 + 集成测试落地） |

---

## 2. 决策记录（已确认）

> 续 M4 D38 编号。D39/D41/D44 涉及「推送通道是否落地、事件下游范围、余额阈值形态」三处可裁量点，为设计基线决策。

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D39 | 通知通道选型 | **站内信（in-app）为 M5 必做落地通道**；外部推送（鸿蒙 Push）待选型，仅抽象 `PushPort` 端口（notify 模块内契约）+ `NoopPushPort` 默认（`pocket-money.notify.push.enabled=false`）；不引入真实推送 SDK | 镜像 M4 D27/D28「仅抽象、真实提供商后置」；roadmap 决策 #4 未拍板 |
| D40 | 通知模型 | 单表 `notification`（接收人维度，站内信即该行本身，`read_at` 表达已读）+ `notification_delivery`（外部通道投递与重试记录）；类型目录 `NotificationType` 枚举（4 类）；站内信不产生 delivery 行 | §9 V9 迁移 |
| D41 | 事件驱动改造范围 | 新增 `MoneyTransactionCreatedEvent`（记账成功后发布）+ `RuleArchivedEvent`（规则到期归档发布），**下游收敛为「通知」**；审计保持现状（`AuditService` REQUIRES_NEW 内联已具备解耦与不阻断语义，不迁移避免审计丢失风险）；看板当前同步聚合（M2 D7）无缓存可失效，故「看板刷新」不列入事件下游 | §6；对齐 M2 D7 与 mission「关键操作审计日志」优先于形式化解耦 |
| D42 | 事件发布与事务边界 | 沿用 M2 D11 同步 `@EventListener` 模式：事件在记账事务内发布，监听器 `try-catch` 不回滚主流程；监听器委托 `@Transactional NotificationService`（请求路径 join 现有事务，Job 路径无事务则自开事务）；外部通道投递异步化（`NotificationRelayJob`） | §6.2/§7；同 `MemberRemovedMoneyListener` 已证惯例 |
| D43 | 通知类型与接收人 | 4 类：账务变动 `TX_*`（入账/出账按 `TxBizType` 细分文案）、余额不足 `LOW_BALANCE`、规则到期 `RULE_EXPIRED`；接收人：账务变动 → 账户主人；余额不足 / 规则到期 → 账户主人 + 家庭全部家长（PARENT，经 `user.service` 只读方法解析，不触碰 `user.mapper`）；文案服务端确定性模板生成（对齐 D33 一致性，AI 不即兴、通知也不即兴） | §5.1/§5.3 |
| D44 | 余额不足提醒阈值 | **家庭级统一阈值** `pocket-money.notify.low-balance-threshold`（默认 `0` = 关闭）；触发 = 出账成功且 `balance_after < 阈值`；账户级个性化阈值非目标 | §5.2；避免改动冻结的 `money_account` 表 |
| D45 | AI 操作结果通知 | 由 `MoneyTransactionCreatedEvent` **自然覆盖**：AI 资金写复用 `MoneyOperationService → AccountTransactionService` 同一记账原语，确认执行即触发账务变动通知；AI 特有链路（拒绝/取消/过期）不额外推送（同步响应 + `ai_message` 会话记录已覆盖，避免通知轰炸） | §6.1；衔接 M4 D31 |
| D46 | 投递与重试 | `NotificationRelayJob` 扫描 `notification_delivery`（PENDING）→ `PushPort.send` → 成功 SENT / 失败退避重试 → 超 `max-retry` 置 DEAD（死信）+ 审计 `NOTIFY_DELIVERY_FAILED`；站内信即时可见无需投递 | §7；满足 M5 任务 3「失败重试与记录」 |
| D47 | 错误码与审计 | `NotifyErrorCode` 70xxxx（700001 通知不存在）；`AuditAction` 增补 `NOTIFY_DELIVERED` / `NOTIFY_DELIVERY_FAILED`；段位与 `CommonErrorCode` javadoc 预留一致（「通知 70xxxx（M5）」） | §8 |

---

## 3. 总体设计

### 3.1 请求处理链路（M5 结束时）

```
鸿蒙 APP
  │ HTTPS / TLS 1.3
  ▼
TraceIdFilter → Spring Security → JwtAuthenticationFilter → UserIdPrincipal（M1 链路不变）
  ▼
IdempotencyFilter（通知写端点 POST /read、/read-all 走统一幂等键协议，M3 不变）
  ▼
RequestTimingFilter（每端点计时，>500ms WARN + traceId，M3 不变）
  ▼
NotificationController（@PreAuthorize 仅要求 authenticated；通知为接收人维度，无 familyId 路径）
  ▼
NotificationService（读：分页/未读数；写：标记已读，校验通知归属本人）
  ▼
Mapper（MyBatis #{}）→ PostgreSQL

—— 事件链路（业务写触发，与请求异步解耦于外部通道）——

MoneyOperationService.deposit/withdraw / RuleGrantExecutor.settle /
LearningTaskService.approve / WorkValueService.record / AI confirm
  └─ AccountTransactionService.apply 记账成功
       └─ eventPublisher.publishEvent(MoneyTransactionCreatedEvent)  ← 事务内发布
            └─ notify MoneyTransactionNotifyListener（try-catch）
                 └─ NotificationService.create（@Transactional，同事务 join）
                      ├─ notification 行（站内信，即时可见）＋ 审计 NOTIFY_DELIVERED
                      └─ 外部通道 enabled 时：notification_delivery(PENDING)
                            └─ NotificationRelayJob（@Scheduled 异步）→ PushPort.send
                                 → SENT / 退避重试 / DEAD + 审计 NOTIFY_DELIVERY_FAILED

RuleExpiryJob（@Scheduled 每日 01:23）到期归档
  └─ 逐条 publishEvent(RuleArchivedEvent)
       └─ notify RuleArchivedNotifyListener（try-catch）
            └─ NotificationService.create（自开事务）→ RULE_EXPIRED 通知家长

定时任务（@Scheduled，虚拟线程，复用 SchedulingConfig）
  ├─ RuleSettlementJob / RuleExpiryJob / ReconciliationJob（M2 不变）
  ├─ AiCleanupJob（M4 不变）
  ├─ NotificationRelayJob（M5 新增：投递重试）
  └─ NotificationCleanupJob（M5 新增：已读通知清理）
```

### 3.2 包结构增量（在 M4 骨架上生长）

```
src/main/java/wyq/pocket/money/
├── money/
│   └── event/MoneyTransactionCreatedEvent.java   # 【新增】记账成功事件（生产者持有，§4.1）
│   └── service/AccountTransactionService.java    # 【修改】记账成功后发布事件（§6.1）
├── rule/
│   ├── event/RuleArchivedEvent.java              # 【新增】规则到期归档事件（生产者持有，§4.2）
│   ├── job/RuleExpiryJob.java                    # 【修改】先查后归档，逐条发事件（§6.1）
│   └── mapper/MoneyRuleMapper.java               # 【修改】增补 findExpired 查询（§6.1）
├── user/
│   └── service/FamilyService.java                # 【修改】+ listParentUserIds 只读方法（§5.3）
├── notify/                                       # 【填充】通知模块（M5 实现）
│   ├── controller/NotificationController.java    # GET 分页/未读数；POST 已读/全部已读（§5.4）
│   ├── service/NotificationService.java          # 通知创建/查询/已读（@Transactional，§5）
│   ├── service/NotificationType.java             # 类型目录枚举（§5.1）
│   ├── service/NotificationTemplateService.java  # 确定性文案模板（§5.2）
│   ├── service/NotifyRecipientResolver.java      # 接收人解析（账户主人 + 家长，§5.3）
│   ├── service/event/MoneyTransactionNotifyListener.java    # 账务变动 → 通知（§6.2）
│   ├── service/event/RuleArchivedNotifyListener.java        # 规则到期 → 通知（§6.2）
│   ├── service/push/PushPort.java                # 外部推送端口（D39，§7.1）
│   ├── service/push/NoopPushPort.java            # 默认空实现（@ConditionalOnMissingBean）
│   ├── service/NotificationRelayService.java     # 投递重试引擎（§7.2）
│   ├── domain/Notification.java                  # DO（§9.1）
│   ├── domain/NotificationDelivery.java          # DO（§9.1）
│   ├── domain/DeliveryStatus.java                # PENDING/SENT/FAILED/DEAD
│   ├── mapper/NotificationMapper.java
│   ├── mapper/NotificationDeliveryMapper.java
│   ├── dto/NotificationPageResponse.java / NotificationItemResponse.java / UnreadCountResponse.java
│   ├── dto/NotifyErrorCode.java                  # 70xxxx（§8.1）
│   └── job/NotificationRelayJob.java             # 投递任务（§7.2）
│   └── job/NotificationCleanupJob.java           # 已读清理（§7.3）
│   └── config/NotifyProperties.java              # pocket-money.notify 配置（§11）

src/main/resources/db/migration/
└── V9__create_notification.sql                   # 【新增】notification / notification_delivery（§9.1）
```

### 3.3 与 M4 基线的衔接

| M4/M3 交付物 | M5 变更 |
|---|---|
| `AccountTransactionService.apply`（记账原语，乐观锁 + `uk_mtxn_request` 幂等） | 记账成功后追加 `publishEvent(MoneyTransactionCreatedEvent)`；**不改变记账语义**（§6.1） |
| `MoneyOperationService.deposit/withdraw`（手动存取）、`RuleGrantExecutor.settle`（规则发放）、`LearningTaskService.approve`（任务奖励）、`WorkValueService.record`（工作价值） | 全部经同一记账原语，**零改动**即获得通知能力（§6.1） |
| `MemberRemovedEvent` + `MemberRemovedMoneyListener`/`MemberRemovedRuleListener`（M2 D11 同步监听 + try-catch） | 新事件的监听器**沿用同一模式**（D42）；既有链路不动 |
| `RuleExpiryJob`（`MoneyRuleMapper.archiveExpired(month)` 返回归档条数） | 改为先 `findExpired(month)` 取列表、逐条归档并发布 `RuleArchivedEvent`（§6.1） |
| `AuditService`（REQUIRES_NEW） / `AuditAction`（36 动作） | 增补 `NOTIFY_DELIVERED` / `NOTIFY_DELIVERY_FAILED`（§8.2），不迁移现有审计链路（D41） |
| `CommonErrorCode`（javadoc 预留「通知 70xxxx（M5）」） | 新 `NotifyErrorCode` 落 70xxxx 段，不侵入 CommonErrorCode（§8.1） |
| `SchedulingConfig`（虚拟线程 TaskScheduler）、`ClockConfig`、`Result`、`GlobalExceptionHandler`、`TraceIds` | 原样复用；`NotificationRelayJob`/`NotificationCleanupJob` 挂载 `@Scheduled` |
| `FamilyAccessChecker`（数据级守卫） | 原样复用；通知读端点以 `principal.userId()` 校验归属，无需 familyId 路径 |

### 3.4 模块依赖方向（ArchUnit 规则增补）

- `notify` 依赖：`money.event` / `rule.event`（事件契约）、`user.service`（接收人解析，仅 service 层）、`common/audit`、`common/web`；`notify` **不得**依赖 `money/rule/finance/ai/user` 的 mapper 与 domain（只经事件 payload + user.service 读方法取数）
- 生产方反向隔离：`money` / `rule` / `ai` / `user` **不得**依赖 `notify` 的任何类（事件单向消费，生产方零感知通知存在，镜像 M2 D11「user 不得依赖 money/rule/finance」）
- `ArchitectureTest` 增补两条：① `notify` 不得被 `money/rule/finance/ai/user` 依赖；② `notify` 不得依赖 `money/rule/finance/ai/user` 的 `..mapper..` / `..domain..` 包

---

## 4. 事件契约（D41/D42）

> 事件对象由**生产者模块持有**（同 `MemberRemovedEvent` 在 `user.event`），record 不可变、携带下游所需的完整快照，监听方**不回查**生产者 mapper。

### 4.1 MoneyTransactionCreatedEvent（money.event）

```java
/**
 * 记账成功领域事件（M5 设计 §4.1）：由 AccountTransactionService.apply 在事务内发布。
 * 监听方：通知模块（账务变动通知 + 余额不足提醒）。监听器 try-catch，不回滚记账主流程。
 * 携带账户主人与操作人双身份，供接收人解析与文案区分「自己操作 / 他人代操作」。
 *
 * 方向 / 业务类型以 String 承载（发布方 enum.name()），而非引用 money.domain 的枚举类型：
 * 事件为跨模块契约，通知模块仅按事件 payload 取数、不得依赖 money.domain（§3.4 规则 ②），
 * 字符串快照既保证契约自足，也避免通知模块因取枚举而产生对 money.domain 的编译依赖。
 *
 * @param familyId       家庭 ID
 * @param userId         账户主人用户 ID
 * @param operatorUserId 操作人用户 ID（定时结算为 null）
 * @param direction      流水方向 "IN" / "OUT"
 * @param bizType        业务类型（"MONTHLY_RULE"/"MANUAL_ADD"/"LEARNING_REWARD"/"WORK_VALUE"/"WITHDRAW"）
 * @param amount         金额（>0）
 * @param balanceAfter   记账后余额
 * @param transactionId  流水 ID（biz_ref 指向）
 * @param remark         备注（可空）
 */
public record MoneyTransactionCreatedEvent(long familyId, long userId, Long operatorUserId,
        String direction, String bizType, java.math.BigDecimal amount,
        java.math.BigDecimal balanceAfter, long transactionId, String remark) {
}
```

- 携带 `userId`（账户主人）与 `operatorUserId`（操作人）双身份，供接收人解析（§5.3）与文案区分「自己操作 / 他人代操作」
- `direction` / `bizType` 用 `String`（发布方 `TxDirection.name()` / `TxBizType.name()` 序列化），令通知模块零依赖 `money.domain`（§3.4 规则②）；通知端以 `"IN".equals(event.direction())` 等字面量比对
- 不含任何敏感信息；金额 DECIMAL 精度与 `MoneyTransaction` 同源

### 4.2 RuleArchivedEvent（rule.event）

```java
/**
 * 规则到期归档领域事件（M5 设计 §4.2）：由 RuleExpiryJob 到期归档时发布。
 * 监听方：通知模块（规则到期提醒家长）。
 *
 * @param familyId         家庭 ID
 * @param beneficiaryUserId 受益人用户 ID
 * @param ruleId           规则 ID
 * @param ruleName         规则名（文案用，随事件携带免回查）
 * @param endMonth         结束月份（YYYY-MM）
 */
public record RuleArchivedEvent(long familyId, long beneficiaryUserId,
        long ruleId, String ruleName, String endMonth) {
}
```

---

## 5. 通知模块设计（D39/D40/D43/D44）

### 5.1 通知类型目录（NotificationType）

| 类型码 | 触发源 | 文案示例（确定性模板，§5.2） | 接收人（§5.3） |
|---|---|---|---|
| TX_IN | 入账（`TxBizType.MONTHLY_RULE` / `MANUAL_ADD` / `LEARNING_REWARD` / `WORK_VALUE`） | 「你收到 50.00 元零花钱（包月规则发放）」 | 账户主人 |
| TX_OUT | 出账（`TxBizType.WITHDRAW`） | 「你提取了 20.00 元零花钱」 | 账户主人 |
| LOW_BALANCE | 出账成功且 `balance_after < 阈值`（D44） | 「小明账户余额仅剩 5.00 元，已低于提醒阈值」 | 账户主人 + 家长 |
| RULE_EXPIRED | 规则到期归档（D41） | 「规则「每周零花钱」已于 2026-11 到期」 | 账户主人 + 家长 |

- 类型码落 `notification.type` 列（VARCHAR(32)），枚举名即存储值
- `TX_IN` 内按 `bizType` 细分文案（规则发放/手动存入/任务奖励/工作价值），枚举不因文案细分而膨胀

### 5.2 文案模板（NotificationTemplateService）

- **服务端确定性模板**：文案由 `NotificationTemplateService` 按 `类型 + bizType + 参数` 拼接，不引入 LLM 生成、不即兴发挥（对齐 M4 D33 一致性原则的推广）
- 模板常量内聚于 `NotificationTemplateService`（消魔法值，code-style §4）；`title`/`content` 长度约束 VARCHAR(128)/VARCHAR(512)
- 金额格式化统一 `0.00` 两位小数（与 `MoneyTransaction` DECIMAL(12,2) 口径一致）

### 5.3 接收人解析（NotifyRecipientResolver）

- 账务变动（`TX_IN`/`TX_OUT`）：接收人 = 账户主人（`event.userId`）
- 余额不足（`LOW_BALANCE`）：接收人 = 账户主人 + 家庭全部家长（`user.service.FamilyService.listParentUserIds(familyId)`，新增只读方法，查 `family_member` join `app_user` 过滤 `role=PARENT`），去重
- 规则到期（`RULE_EXPIRED`）：接收人 = 受益人 + 家庭家长（同上），去重
- **不触碰 `user.mapper`**：notify 经 `FamilyService` service 层取家长列表（ArchUnit §3.4）；`FamilyService.listParentUserIds` 为无鉴权的只读方法（供定时/事件路径，区别于需 principal 的 `listMembers`）

### 5.4 端点（NotificationController）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/notifications` | 本人通知分页（未读优先，`page` 默认 1、`size` 默认 20 上限 50） | authenticated（本人） |
| GET | `/api/v1/notifications/unread-count` | 未读数 | authenticated（本人） |
| POST | `/api/v1/notifications/{id}/read` | 标记已读（校验归属本人，越权/不存在 700001） | authenticated（本人） |
| POST | `/api/v1/notifications/read-all` | 全部标记已读 | authenticated（本人） |

- 通知为**接收人维度**，无 `familyId` 路径；归属校验 = `notification.user_id == principal.userId()`（不匹配 → 700001）
- 写端点走 M3 `IdempotencyFilter` 统一幂等键协议；读端点无幂等要求

---

## 6. 事件驱动改造（D41/D42）

### 6.1 发布点改造

**记账成功（AccountTransactionService.apply）**

- 在 `transactionMapper.insert(tx)` 成功返回后追加：

```java
eventPublisher.publishEvent(new MoneyTransactionCreatedEvent(tx.getFamilyId(), tx.getUserId(),
        tx.getOperatorUserId(), tx.getDirection().name(), tx.getBizType().name(),
        tx.getAmount(), tx.getBalanceAfter(), tx.getId(), tx.getRemark()));
```

- `apply` 已 `@Transactional`，事件在**记账事务内**发布；`AccountTransactionService` 注入 `ApplicationEventPublisher`（构造器 +1 参数）
- **覆盖全部账务来源零改动**：手动存取（`MoneyOperationService`）、规则发放（`RuleGrantExecutor`）、任务奖励（`LearningTaskService`）、工作价值（`WorkValueService`）、AI 资金写（`PendingActionService.confirm → MoneyOperationService`）共用该原语，通知能力自动生效（D45）

**规则到期归档（RuleExpiryJob）**

- 现行为 `ruleMapper.archiveExpired(month)` 单条 SQL 返回条数；改为：

```java
List<MoneyRule> expired = ruleMapper.findExpired(month);   // 增补查询：end_month < 当月 且非 ARCHIVED
for (MoneyRule rule : expired) {
    if (ruleMapper.archiveById(rule.getId()) == 1) {         // 增补：单条归档（幂等）
        eventPublisher.publishEvent(new RuleArchivedEvent(rule.getFamilyId(),
                rule.getBeneficiaryUserId(), rule.getId(), rule.getRuleName(),
                rule.getEndMonth()));
    }
}
```

- Job 运行在无事务上下文，监听器委托 `@Transactional NotificationService` 自开事务（D42）

### 6.2 监听器（notify.service.event）

- `MoneyTransactionNotifyListener.onTransactionCreated(event)`：`@EventListener` 同步执行（默认），`try-catch` 包裹（镜像 `MemberRemovedMoneyListener`），内部：
  1. `NotificationService.createTxNotification(event)` → 账务变动通知（§5.2/§5.3）
  2. 若 `"OUT".equals(event.direction()) && event.balanceAfter() < lowBalanceThreshold` → 追加 `LOW_BALANCE` 通知（阈值来自 `NotifyProperties`，默认 0 关闭，D44）
- `RuleArchivedNotifyListener.onRuleArchived(event)`：同上模式，生成 `RULE_EXPIRED` 通知
- 监听器失败仅 `LOG.error` 留痕（`NOTIFY_LISTENER_FAILED`），**不回滚**记账/归档主流程（与 M2 D11 语义一致；站内信丢失可容忍，外部通道失败另有 delivery 重试兜底，§7）

### 6.3 事务边界小结

| 事件来源 | 发布上下文 | 监听器事务 |
|---|---|---|
| 记账（请求路径 / AI confirm） | 记账事务内（`@Transactional`） | `@Transactional NotificationService` join 现有事务，通知与账务**同提交同回滚** |
| 规则到期归档（Job） | 无事务 | `@Transactional NotificationService` 自开事务，逐条提交 |

- 账务路径下通知行与流水同事务：账务回滚则通知一并回滚（无幻影通知）；账务提交则站内信必达（原子性）

---

## 7. 通知投递与重试（D39/D46）

### 7.1 PushPort 端口（notify.service.push）

```java
/**
 * 外部推送通道端口（D39，鸿蒙 Push 待选型）。
 * 实现方：NoopPushPort（默认，push.enabled=false）/ 真实 Push 适配器（通道拍板后）。
 * 契约约束：投递幂等（同 notification 重复 send 不得重复推送，实现方保证）。
 */
public interface PushPort {
    /** 推送一条通知。返回 true = 已受理；false / 异常 = 投递失败（由 relay 记 FAILED）。 */
    boolean send(long notificationId, long userId, String title, String content);
}
```

- `NoopPushPort`：`@ConditionalOnMissingBean(PushPort.class)` 注册，直接返回 `false`（`push.enabled=false` 时根本不产生 delivery 行，见 §7.2）
- 真实 Push 适配器在通道拍板后实现 `PushPort` 并置 `pocket-money.notify.push.enabled=true`，业务零感知（镜像 M4 `ChatPort`/`StubChatPort`）

### 7.2 NotificationRelayJob + NotificationRelayService

```
通知创建（外部通道 enabled 时）→ notification_delivery(status=PENDING, retry_count=0, next_retry_at=now)

NotificationRelayJob（@Scheduled 虚拟线程，默认每日 02:17 + 按需更密集）
  → NotificationRelayService.drainPending()
      → 扫描 status=PENDING 且 next_retry_at <= now（idx_ndelivery_pending）
      → PushPort.send(...)
          成功 → SENT + sent_at + 审计 NOTIFY_DELIVERED
          失败 → retry_count+1、next_retry_at = now + backoff（指数退避）
                 retry_count < max-retry → 保持 PENDING（下次再试）
                 retry_count >= max-retry → DEAD（死信）+ last_error + 审计 NOTIFY_DELIVERY_FAILED
```

- `max-retry` 默认 3、退避初始 `PT1M` 指数增长（配置项，§11）；单条失败不阻断其余投递（`LOG.error` 留痕）
- 死信（DEAD）**不自动重投**，供人工排查（运维手册 M7 覆盖）；站内信（`notification` 行）不受 delivery 状态影响，始终可见

### 7.3 NotificationCleanupJob

- 每日执行（`@Scheduled` + `SchedulingConfig`），`@ConditionalOnProperty(pocket-money.notify.cleanup-enabled)` 控制启停（同 M2 结算任务模式）
- 清理范围：`read_at` 非空且超保留期（默认 P30D）的 `notification`（级联删除其 `notification_delivery`）；`idx_notify_user_time` 支撑扫描

---

## 8. 错误码与审计

### 8.1 NotifyErrorCode（70xxxx 段）

| 错误码 | 含义 | 客户端处理建议 | retryable |
|---|---|---|---|
| 700001 | 通知不存在或非本人 | 刷新通知列表 | 否 |

- 落 `notify/dto/NotifyErrorCode.java` 实现 `ErrorCode`；`isRetryable()` 继承默认（70 段不可重试）
- 段位 70xxxx 与 `CommonErrorCode` javadoc 预留一致；投递失败为**服务端内部状态**（`notification_delivery.status`），不对外暴露为业务错误码（客户端只感知「有没有这条站内信」）

### 8.2 AuditAction 增补

| 动作 | 含义 |
|---|---|
| NOTIFY_DELIVERED | 通知投递成功（站内信创建 / 外部通道发送成功） |
| NOTIFY_DELIVERY_FAILED | 外部通道重试耗尽（死信） |

- 名称 ≤ 48 字符（`audit_log.action` VARCHAR(48) 约束）；站内信创建与外部投递成功共用 `NOTIFY_DELIVERED`（避免动作枚举随通道膨胀）
- 余额不足 / 规则到期的**生成事实**由 `notification` 行本身承载，不单列审计动作（避免审计与通知双写冗余）

---

## 9. 数据模型与迁移（V9）

### 9.1 V9__create_notification.sql

```sql
-- M5：通知（站内信）+ 外部通道投递记录（M5-detailed-design.md §9.1）
-- 设计基线：站内信即 notification 行本身（read_at 表达已读）；
-- 外部通道投递与重试独立 notification_delivery（站内信不产生 delivery 行）。
-- 时间类型统一写作 TIMESTAMP WITH TIME ZONE（H2 2.4.240 不识别 TIMESTAMPTZ，约定同 V2/V4/V7）。

CREATE TABLE notification (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES app_user (id),   -- 接收人
    family_id    BIGINT NOT NULL REFERENCES family (id),
    type         VARCHAR(32) NOT NULL
                 CHECK (type IN ('TX_IN', 'TX_OUT', 'LOW_BALANCE', 'RULE_EXPIRED')),
    title        VARCHAR(128) NOT NULL,
    content      VARCHAR(512) NOT NULL,
    biz_ref_type VARCHAR(24),                                -- MONEY_TRANSACTION / MONEY_RULE
    biz_ref_id   BIGINT,
    read_at      TIMESTAMP WITH TIME ZONE,                   -- NULL = 未读
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_notify_user_time ON notification (user_id, created_at);
CREATE INDEX idx_notify_user_read ON notification (user_id, read_at);

COMMENT ON TABLE notification IS '站内信通知：接收人维度，read_at 表达已读';
COMMENT ON COLUMN notification.biz_ref_type IS '业务锚点类型（流水/规则），可追溯通知来源';

CREATE TABLE notification_delivery (
    id              BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notification (id),
    channel         VARCHAR(16) NOT NULL DEFAULT 'PUSH'
                    CHECK (channel IN ('PUSH')),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD')),
    retry_count     INTEGER NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_error      VARCHAR(256),
    sent_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ndelivery_pending ON notification_delivery (status, next_retry_at);
CREATE INDEX idx_ndelivery_notification ON notification_delivery (notification_id);

COMMENT ON TABLE notification_delivery IS '外部通道投递与重试记录：PENDING→SENT/DEAD，站内信无此记录';
```

- `type` 的 CHECK 约束与 `NotificationType` 枚举对应（新增类型需同步迁移，M0 既定规范：迁移一经提交永不修改，回滚走前向修复）
- `idx_notify_user_read` 支撑未读计数（`read_at IS NULL` 走普通索引，H2 不支持部分索引，约定同 V4）
- `biz_ref_type`/`biz_ref_id` 锚点：账务变动指向 `MONEY_TRANSACTION`、规则到期指向 `MONEY_RULE`，供客户端点击跳转与可解释追溯

---

## 10. 测试设计

### 10.1 单元测试

| 测试类 | 覆盖点 |
|---|---|
| MoneyTransactionCreatedEventTest | record 组件不可变、字段完备（含 operatorUserId 可空） |
| RuleArchivedEventTest | record 组件不可变、文案字段（ruleName/endMonth） |
| NotificationTypeTest | 4 类枚举完备、`type` 与 CHECK 约束一致 |
| NotificationTemplateServiceTest | 各类型 + bizType 文案拼接、金额两位小数格式化、长度约束（≤128/≤512） |
| NotifyRecipientResolverTest | 账务变动→账户主人；LOW_BALANCE/RULE_EXPIRED→主人+家长去重；家长列表来自 FamilyService（mock） |
| NotificationServiceTest | 创建通知落库 + delivery 行生成条件（push enabled）；标记已读校验归属（他人/不存在 700001）；未读数 |
| MoneyTransactionNotifyListenerTest | 入账→TX_IN；出账→TX_OUT；出账低于阈值→LOW_BALANCE（阈值 0 关闭时不触发）；监听器异常不回滚（try-catch 语义） |
| RuleArchivedNotifyListenerTest | 到期事件→RULE_EXPIRED 通知家长；监听器异常不回滚 |
| NotificationRelayServiceTest | PENDING→SENT；失败退避（next_retry_at 递增）；超 max-retry→DEAD + 死信审计；单条失败不阻断其余 |
| NotifyErrorCodeTest | 700001 段位 70xxxx、`isRetryable()`=false |
| AuditActionTest（增补） | NOTIFY_DELIVERED / NOTIFY_DELIVERY_FAILED 名称 ≤48 字符、与既有动作无重复 |
| NoopPushPortTest | send 恒返回 false（默认通道不投递） |

### 10.2 集成测试套件（RestAssured + Testcontainers PG，沿用 M1 形态；H2 本地 + PG 双形态）

| 套件 | 场景 |
|---|---|
| NotifyFlowH2IntegrationTest | 存入→账户主人收到 TX_IN 站内信；未读数/分页/标记已读/全部已读；读他人通知 700001 |
| NotifyEventPgIntegrationTest | 规则发放/任务奖励/工作价值/手动存取/AI confirm 各类记账 → 通知类型与接收人正确；出账低于阈值→LOW_BALANCE；阈值 0 不触发 |
| NotifyRuleExpiryPgIntegrationTest | 规则到期归档→家长收到 RULE_EXPIRED；受益人 + 家长去重 |
| NotifyRelayPgIntegrationTest | PENDING delivery 经 relay 投递成功→SENT；`PushPort` 失败（注入 `FailingPushPort` 测试桩）→退避重试→超 max-retry 置 DEAD + 审计 NOTIFY_DELIVERY_FAILED |
| NotifyAuditPgIntegrationTest | 通知生成/投递成功/死信三类动作落 `audit_log` 可追溯 |
| ArchitectureTest（增补） | §3.4 两条规则（notify 不被生产方依赖；notify 不得依赖各模块 mapper/domain） |
| 既有套件回归 | M1–M4 套件全量回归通过（通知不侵入既有链路） |

### 10.3 覆盖率与门禁

JaCoCo 80% BUNDLE 门禁沿用；`notify` 模块新增类全部计入；`NoopPushPort`（默认实现）与 `NotificationTemplateService`（模板）计入主代码，不豁免。

---

## 11. 配置增量

```yaml
pocket-money:
  # ============================================================
  # M5 通知与事件驱动配置（M5-detailed-design.md §11）
  # ============================================================
  notify:
    # 通知总开关（站内信落库总闸）：集成测试置 false 直调 service 验证
    enabled: ${NOTIFY_ENABLED:true}
    # 余额不足提醒阈值（家庭级统一，0=关闭；出账后 balance_after 低于该值触发）
    low-balance-threshold: ${NOTIFY_LOW_BALANCE_THRESHOLD:0}
    push:
      # 外部推送通道（鸿蒙 Push）开关：通道拍板并实现 PushPort 后置 true
      enabled: ${NOTIFY_PUSH_ENABLED:false}
    relay:
      enabled: ${NOTIFY_RELAY_ENABLED:true}
      cron: ${NOTIFY_RELAY_CRON:0 17 2 * * *}       # 每日 02:17
      max-retry: ${NOTIFY_MAX_RETRY:3}
      retry-backoff: ${NOTIFY_RETRY_BACKOFF:PT1M}   # 指数退避初始间隔
    cleanup:
      enabled: ${NOTIFY_CLEANUP_ENABLED:true}
      cron: ${NOTIFY_CLEANUP_CRON:0 47 4 * * *}     # 每日 04:47
      read-ttl: ${NOTIFY_READ_TTL:P30D}             # 已读通知保留期
```

- `NotifyProperties`（`@ConfigurationProperties` 前缀 `pocket-money.notify`）；全部经环境变量注入，无硬编码（mission 禁止项）
- cron 为 Spring 6 段式（秒 分 时 日 月 周），与既有 `pocket-money.money.settlement.cron` 等一致

---

## 12. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 |
|---|---|---|---|
| T1 | 事件契约与发布点（MoneyTransactionCreatedEvent + RuleArchivedEvent；AccountTransactionService.apply 发布；RuleExpiryJob 改造 + MoneyRuleMapper.findExpired/archiveById） | — | 1.5 人天 |
| T2 | 通知模块骨架（V9 迁移、domain/mapper、NotificationType、模板、接收人解析、NotificationService、FamilyService.listParentUserIds） | T1 | 2.5 人天 |
| T3 | 投递与重试（PushPort + NoopPushPort、NotificationRelayService/Job、delivery 状态机与死信、NotificationCleanupJob） | T2 | 2 人天 |
| T4 | 端点 + 错误码 + 审计（NotificationController 4 端点、NotifyErrorCode、AuditAction 增补、OpenAPI 注解） | T2 | 1.5 人天 |
| T5 | ArchUnit 规则 + 集成测试全集 + DoD 验证收尾（§10.2 套件 + 既有套件回归） | T3、T4 | 2 人天 |

合计约 **9.5 人天**。roadmap 排期 1 周（5 工作日/人）：

- **2 人投入**：约 4.75 人天/人，舒适（推荐，通知涉及资金联动，双人评审）
- **1 人投入**：9.5 人天 > 5 人天，超排期；候选裁剪项：`PushPort` 降纯文档（不写 relay，仅站内信）、余额不足提醒阈值降为固定常量、`NotificationCleanupJob` 并入 M6

关键路径：T1 → T2 → T3/T4 并行；T5 为收尾闸门。

---

## 13. 验收标准（DoD，与 roadmap 一致并细化）

- [ ] 关键业务事件触发通知的集成测试通过：存入 / 规则发放 / 任务奖励 / 工作价值 / 取出 / AI confirm 各路径 → 对应类型通知生成（NotifyEventPgIntegrationTest）；规则到期归档 → RULE_EXPIRED（NotifyRuleExpiryPgIntegrationTest）
- [ ] 通知发送失败重试与记录落地：PENDING → 退避重试 → SENT / DEAD 全状态迁移 + `audit_log` 投递记录可追溯（NotifyRelayPgIntegrationTest + NotifyAuditPgIntegrationTest）
- [ ] 模块间无跨层直接调用（依赖关系审查通过）：ArchUnit 规则——notify 不被 money/rule/finance/ai/user 依赖；notify 不得依赖各模块 mapper/domain（ArchitectureTest 增补绿）
- [ ] 通知读端点归属校验：读他人通知 700001，未读数/已读/全部已读正确（NotifyFlowH2IntegrationTest）
- [ ] 单测覆盖率 ≥ 80%（JaCoCo）；`mvn clean verify` 全门禁绿（Checkstyle/PMD/SpotBugs，SonarQube 归 M6）
- [ ] 既有 M1–M4 集成测试套件全量回归通过

---

## 14. 风险与遗留事项

| # | 风险/事项 | 影响 | 应对 |
|---|---|---|---|
| R1 | 鸿蒙 Push 通道未选型 | 外部推送能力后置 | `PushPort` 抽象 + `NoopPushPort` 默认（D39）；站内信为 M5 必做，核心价值（通知中心）不依赖 Push |
| R2 | 同步监听器异常导致通知丢失（站内信落库失败） | 个别通知丢失 | 监听器 try-catch + `LOG.error` 留痕（D42）；站内信与账务同事务，账务提交即通知已提交（§6.3），丢失仅限通知自身 insert 失败（罕见，可容忍，审计 + 流水锚点可补查） |
| R3 | 外部通道投递重复 / 失败 | 用户重复收到或漏收推送 | `notification_delivery` 幂等重试 + `PushPort` 契约「同通知重复 send 不得重复推送」+ 死信人工排查（D46） |
| R4 | 通知表膨胀（已读通知堆积） | 查询变慢 | `idx_notify_user_time` + `NotificationCleanupJob` 每日清理已读超 TTL（§7.3） |
| R5 | 余额阈值过粗（家庭级统一） | 个性化不足 | 家庭级阈值为 v1 基线（D44）；账户级阈值后续里程碑评估（需 `money_account` 增列，届时 V 前向迁移） |
| R6 | 通知轰炸（高频账务操作） | 用户体验下降 | v1 每笔账务仅 1~2 条站内信（接收人收敛 §5.3）；偏好/免打扰/聚合为后续里程碑非目标 |

遗留至后续阶段：真实鸿蒙 Push 接入（通道拍板后）、通知偏好与免打扰、账户级余额阈值、规则「即将到期」提前提醒、看板缓存化 + 缓存失效事件（M6 性能复测若引入缓存再接入）、JMeter 10 TPS 压测含通知链路（M6）。

---

## 附录 A：关键链路时序（文本）

**账务变动 → 站内信（同事务原子）**

```
客户端 → POST /api/v1/families/{id}/deposits {targetUserId, amount:50}
  → MoneyOperationService.deposit → AccountTransactionService.apply
      → 余额快照 + 流水双写（乐观锁）→ transactionMapper.insert(tx)
      → publishEvent(MoneyTransactionCreatedEvent{userId=小明, bizType=MANUAL_ADD, amount=50, ...})
          → MoneyTransactionNotifyListener.onTransactionCreated（try-catch）
              → NotificationService.createTxNotification（@Transactional，join 当前事务）
                  → insert notification(user_id=小明, type=TX_IN, title/content=模板)
                  → auditService.record(NOTIFY_DELIVERED)（REQUIRES_NEW）
  → 事务提交：流水 + 通知 + 审计同提交
客户端 ← 200 {transactionId, balanceAfter}
```

**规则到期 → 家长通知（Job 自开事务）**

```
RuleExpiryJob（每日 01:23）
  → MoneyRuleMapper.findExpired(month) → [ruleA(end_month=2026-10), ...]
  → archiveById(ruleA) → publishEvent(RuleArchivedEvent{familyId, beneficiaryUserId, ruleName, endMonth})
      → RuleArchivedNotifyListener.onRuleArchived（try-catch）
          → NotificationService.create（@Transactional 自开事务）
              → insert notification(user_id=家长, type=RULE_EXPIRED, ...) + 审计
```

**外部通道投递重试（异步）**

```
NotificationRelayJob（每日 02:17 + 按需）
  → NotificationRelayService.drainPending()
      → 扫描 notification_delivery(status=PENDING, next_retry_at<=now)
      → PushPort.send(notificationId, userId, title, content)
          成功 → SENT + sent_at + 审计 NOTIFY_DELIVERED
          失败 → retry_count+1、next_retry_at += backoff（1m/2m/4m...）
                 retry_count >= 3 → DEAD + last_error + 审计 NOTIFY_DELIVERY_FAILED
```

## 附录 B：权限矩阵增量

| 通知端点 | 未认证 | 家长 | 孩子 |
|---|---|---|---|
| GET /api/v1/notifications | ❌ 拒绝（401+100003） | ✅ 本人通知 | ✅ 本人通知 |
| GET /api/v1/notifications/unread-count | ❌ 拒绝 | ✅ 本人 | ✅ 本人 |
| POST /api/v1/notifications/{id}/read | ❌ 拒绝 | ✅ 本人（他人 700001） | ✅ 本人（他人 700001） |
| POST /api/v1/notifications/read-all | ❌ 拒绝 | ✅ 本人 | ✅ 本人 |

- 通知为接收人维度，家长与孩子权限完全一致（各自只看/只改自己的通知）；无家长专属端点
- 通知**生成**不经过接口授权（服务端事件驱动），接收人由 `NotifyRecipientResolver` 按 §5.3 规则确定

## 附录 C：与 roadmap M5 任务/DoD 映射

| roadmap M5 条目 | 设计章节 |
|---|---|
| 任务 1 通知模块（账务变动/规则到期/余额不足/AI 操作结果，推送通道选型待定） | §5 通知模块（D39/D40/D43/D44）+ §4.2 规则到期 + D45 AI 覆盖 |
| 任务 2 Spring Event 事件驱动改造（账务变动→通知/看板刷新/审计解耦） | §4 事件契约 + §6 改造（D41/D42）；看板/审计的处置见 D41 |
| 任务 3 通知发送失败重试与记录 | §7 投递与重试（D46）+ §8.2 审计 |
| DoD 1 关键业务事件触发通知的集成测试通过 | §10.2 NotifyEvent/NotifyRuleExpiry/NotifyRelay + §13 |
| DoD 2 模块间无跨层直接调用（依赖关系审查通过） | §3.4 ArchUnit 规则 + §13 |

## 附录 D：文档变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-28 | M5 开发基线初稿：通知模型与类型目录（D39/D40/D43/D44）、事件契约与改造范围（D41/D42/D45）、投递重试（D46）、错误码与审计（D47）；V9 迁移、测试设计、WBS 与 DoD |

---

*本设计作为 M5 开发基线；实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*