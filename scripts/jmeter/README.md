# JMeter 压测脚本（M6 D50）

本目录承载 10 TPS 发布前置压测计划（`M6-detailed-design.md` §6，D50）。

## 文件

| 文件 | 说明 |
|---|---|
| `pocket-money-load.jmx` | 压测计划：10 并发 × 30 分钟、Constant Throughput Timer 锁定 10 TPS、读/写/AI/通知按 60/20/15/5 权重混合 |

## 压测目标（DoD）

- 10 TPS 稳态下全 API **P95 ≤ 500ms**、**错误率 ≤ 0.5%**、无资源泄漏（HikariCP 活跃连接回落、JVM 堆不持续攀升）。

## 前置条件

1. **环境**：目标服务以 `test`/`local` profile 起于干净 PostgreSQL，外部依赖（AI/通知）以进程内桩隔离（`ai.mock=true` + `StubChatPort`；通知 `NoopPushPort`）——压测对象为**本服务自身**。
2. **数据种子**：压测前注入 `PerformanceDataSeeder`（`integration/support/PerformanceDataSeeder.java`，50 家庭 × 8 成员 × 36 月 ≈ 5 万流水）。
3. **登录账号**：`USERNAME` / `PASSWORD` / `MEMBER_ID` 需替换为种子数据中的真实家长手机号、密码与任一孩子成员 ID（默认占位值仅供结构示例，见 `.jmx` 顶部「用户定义变量」）。
4. **JMeter**：5.6+（`docs/version-matrix.md` 外部工具口径，不入 pom）。

## 运行

```bash
# GUI 调试
jmeter -t scripts/jmeter/pocket-money-load.jmx

# 命令行非 GUI 压测 + 结果落盘
jmeter -n -t scripts/jmeter/pocket-money-load.jmx \
       -l docs/performance/m6-load-result-$(date +%F).jtl \
       -e -o docs/performance/m6-load-report-$(date +%F)

# 覆盖变量（命令行 -J 覆盖默认值）
jmeter -n -t scripts/jmeter/pocket-money-load.jmx \
       -JUSERNAME=13900000000 -JPASSWORD=Passw0rd! -JMEMBER_ID=1000002 \
       -JHOST=127.0.0.1 -JPORT=8080 -JPROTOCOL=http
```

## 场景权重

| 场景 | 端点 | 权重 |
|---|---|---|
| 读 | `GET /dashboard` / `/transactions` / `/trends` / `/reports/income-expense` | 60% |
| 写 | `POST /deposits` / `/withdrawals`（带 `Idempotency-Key: ${__UUID}`） | 20% |
| AI 意图 | `POST /api/v1/ai/chat`（走 `StubChatPort`） | 15% |
| 通知 | `GET /notifications/unread-count` / `POST /notifications/read-all` | 5% |

> 全部端点以 `/api/v1` 为前缀；家庭路径 `familyId` 取自登录响应 `data.user.familyId`（`setUp` 线程组提取后以 JMeter 属性 `familyId` 复用）。

## 断言

- HTTP 状态码 200（`JSR223Assertion` 业务断言 `code=0`，任一业务非 0 记为失败）。
- P95 ≤ 500ms、错误率 ≤ 0.5% 在报告中核验（汇总报告 + 聚合报告图表）。

## 无泄漏验证（压测期间抽样）

```bash
# 活跃连接（稳态后应回落并稳定，不随时间单调增长）
curl -s "http://${HOST}:${PORT}/actuator/metrics/hikaricp.connections.active"

# JVM 堆（压测结束不持续攀升）
curl -s "http://${HOST}:${PORT}/actuator/metrics/jvm.memory.used"
```

## 不达标处置

任一断言不达标 → 触发 D51 复测回补表（`M6-detailed-design.md` §7）：C1 报表异步化、C2 看板缓存、C3 幂等预占/连接池、C4 AI 限流/超时参数；回补后复测直至达标。

## 报告存档

- 报告落 `docs/performance/m6-load-report-<date>.md`（TPS 曲线、P95/P99、错误率、活跃连接/堆趋势、结论）。
- 与 `PerformanceBaselinePgIntegrationTest`（本地基准）双报告并存，互为印证（M2 R6 双保险口径）。
