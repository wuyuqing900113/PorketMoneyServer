# 运维手册（M7）

| 项 | 值 |
|---|---|
| 里程碑 | M7（部署与发布） |
| 依据 | `M7-detailed-design.md` §8（D60）；mission「基础监控能力 / 数据备份恢复 / 故障转移」 |
| 适用 | 生产环境日常巡检、监控告警、日志与脱敏、应急处置 |

---

## 1. 监控架构

- **指标**：Spring Boot Actuator（`/actuator/health`、`/actuator/metrics`，application.yml 已暴露 health/info/metrics）→ 阿里云 **ARMS** 应用监控（Java agent 免侵入 + 关键业务自定义埋点）。
- **日志**：Logback `MaskingJsonEncoder` 输出**脱敏 JSON 日志** → 宿主机 Logtail 采集 → 阿里云 **SLS**；GC 日志（`-Xlog:gc*`，M3 D23）单独采集。
- **告警通道**：ARMS / 云监控 → 短信 / 邮件 / IM 群。

## 2. 日常巡检

| 项 | 方式 | 正常基线 |
|---|---|---|
| 应用健康 | SLB 后端健康检查 / `/actuator/health` | 全部 UP，无实例摘除 |
| 核心接口 | ARMS 概览（`/dashboard` `/transactions` `/deposits` `/withdrawals` `/ai/chat`） | P95 ≤ 500ms，错误率 ≤ 0.5% |
| DB 连接池 | `hikaricp.connections.active` / `.pending` | 活跃连接稳态回落，pending ≈ 0 |
| JVM 堆/GC | ARMS JVM 监控 / GC 日志 | 堆不持续攀升，ZGC 停顿亚毫秒 |
| RDS | 云监控（CPU/连接/慢查询/主备状态） | 无慢查询突增，主备正常 |
| 定时任务 | SLS 日志（settlement/reconcile/cleanup/relay cron） | 按 cron 执行，无异常栈 |
| AI 降级 | ARMS 自定义指标（熔断状态/降级率） | 非 OPEN；降级率无异常抬升 |

## 3. 告警规则基线（D60）

| 告警 | 触发条件 | 级别 | 初判处置 |
|---|---|---|---|
| 核心接口 P95 > 500ms | 连续 ≥5 分钟 | P2 | 看慢查询/连接池；触发 D51 回补评估（报表异步化/连接池） |
| 错误率 > 0.5% / 5xx 突增 | 阈值 + 环比 | P1 | 灰度期 → 立即回滚；稳态 → 查异常栈与依赖（RDS/AI） |
| 健康检查失败 / 实例摘除 | 即时 | P1 | 查容器状态与启动日志；SLB 自动摘除后排查，必要时回滚 |
| 熔断 OPEN / AI 降级率异常 | 即时 | P2 | 查 AI provider 依赖；核心功能不受影响（600001 降级） |
| 登录失败激增（疑似爆破） | 阈值 + 环比 | P1（安全） | 核对登录锁定（M1 D7，5 次/15 分钟）；`SecurityLogger` 溯源 IP |
| DB 连接池耗尽 / RDS 主备切换 | 即时 | P1 | 查 HikariCP 配置与慢查询；主备切换后确认应用自愈 |

> 告警链路演练：M7 DoD 要求「触发 → 通知 → 处置」全链路演练一次（人为制造 5xx/健康检查失败，确认告警送达与响应）。

## 4. SLS 日志与脱敏验证

- **采集**：Logtail 采集容器 `/app/logs/`（应用 JSON 日志 + `gc.log`）至 SLS Project/Logstore。
- **脱敏落地验证（M7 专项，DoD）**：SLS 侧按 auth/money/rule/ai/notify 各链路抽检日志，断言无：
  - 明文手机号（应为 `1xx****xxxx`）、身份证（前 3 后 4 打码）、银行卡号（仅后 4）；
  - 明文密码/密钥（password/secret/api_key/token/authorization 值应为 `******`）。
- 规则依据：`MaskingRules`（身份证/手机号/银行卡/密钥四类）+ `MaskingJsonEncoder`（对整行 JSON 日志脱敏，覆盖 message 与 MDC）。抽检不通过即补脱敏规则（不改业务逻辑）。
- **日志留存**：容器 json-file 轮转 50m×5；SLS 按合规策略留存（审计 `audit_log` 随数据库备份）。

## 5. ARMS 接入要点

- Java agent 以 `-javaagent` 挂载（部署脚本经 `JAVA_TOOL_OPTIONS` 注入 licenseKey/应用名，不入镜像、不硬编码）。
- 关键业务自定义埋点（Micrometer）：资金写成功率、幂等命中率、熔断状态、AI 降级率——按 M7 接入时补埋点（属最小可观测增强，不改业务语义）。
- 可用性统计：月度可用性 ≥ 99.5%（双可用区 + SLB 摘除 + 快速回滚共同保障），报告存档。

## 6. 常见应急处置

| 现象 | 处置 |
|---|---|
| 新版本灰度异常 | `scripts/deploy/rollback.sh <PREV_STABLE_TAG>`（见 `../deploy/release-runbook.md`） |
| RDS 故障 | 主备自动切换；确认应用连接自愈（HikariCP 重连）；不恢复则走容灾 Runbook |
| AI 依赖故障 | 熔断自动 OPEN → 600001 降级，核心功能不受影响；依赖恢复后自动 HALF_OPEN→CLOSED |
| 磁盘/日志膨胀 | 容器日志已轮转；SLS 留存策略控制；`/app/logs` 为挂载卷 |
