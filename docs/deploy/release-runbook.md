# 发布与回滚 Runbook（M7）

| 项 | 值 |
|---|---|
| 里程碑 | M7（部署与发布） |
| 依据 | `M7-detailed-design.md` §7（D59）；流水线 `yunxiao-pipeline.yml` 阶段 5–7 |
| 适用 | 生产环境金丝雀发布、蓝绿切换、一键回滚 |

---

## 1. 前置条件

- 阶段 1–4 全绿：`secret-scan`（无硬编码密钥）、`mvn clean verify`（JaCoCo ≥80% + Checkstyle/PMD/SpotBugs 0 违规）、云效代码检测（D48，无阻断/严重、重复率 ≤3%）。
- 镜像已推 ACR：`<ACR_NAMESPACE>/pocket-money-server:<BUILD_TAG>`。
- 测试环境已部署并通过冒烟 + JMeter 10 TPS 终验（阶段 6，P95 ≤500ms / 错误率 ≤0.5%）。
- 上一稳定 tag 已记录（回滚目标 `PREV_STABLE_TAG`）。
- 灰度期间新旧版本共享同一 RDS（V1–V10 迁移向前兼容，旧版本可继续运行）。

## 2. 金丝雀发布流程（脚本化于阶段 7）

| 步骤 | 动作 | 判定 |
|---|---|---|
| 1 | ECS-A 部署新版本，SLB 权重 5%（ECS-B 旧版本 95%） | `slb-set-weight.sh <LB> <ECS_A> 5` |
| 2 | 观察 ≥15min 或 ≥1000 请求 | `observe-canary.sh <ARMS_APP_ID> --window 15m` |
| 3 | 达标 → 权重 50%，再观察 10min | 错误率 ≤0.5%、P95 ≤500ms、无 5xx 突增 |
| 4 | 达标 → ECS-B 滚动更新为新版本，权重 100% | 全量切换 |
| 5 | 归档发布记录（tag、指标快照、操作人） | — |

**观察指标来源**：ARMS 应用监控（P95/错误率/QPS）+ SLS 日志（5xx/异常栈）+ `/actuator/metrics/hikaricp.connections.active`（连接池回落，无泄漏）。

## 3. 中止与一键回滚

**触发条件（任一即回滚）**：错误率 > 0.5%；5xx 突增；P95 持续 > 500ms；健康检查失败/实例被摘除；登录失败激增；资金写错误。

**回滚动作**（`scripts/deploy/rollback.sh <PREV_STABLE_TAG>`）：

1. 两台 ECS 依次回退到上一稳定镜像（先 B 后 A，保证随时有健康实例）。
2. SLB 权重全量切回回退后实例（新版本权重归零/摘除）。
3. 健康检查确认回退后实例 UP。
4. 归档回滚记录 + 开问题处置单（定位根因后再走修复发布）。

> 数据库不做反向迁移：Flyway V1–V10 为前向兼容设计，回滚仅回应用镜像。

## 4. 蓝绿切换（备选/大版本）

- ECS-A/B 互为蓝绿：新版本全量部署到 standby 环境 → 预热（健康检查 + 冒烟）→ SLB 一键切流 → 旧环境保留为回滚兜底。
- 零停机：切流由 SLB 权重完成，应用无重启中断。

## 5. 发布检查清单

- [ ] 阶段 1–4 门禁全绿
- [ ] 测试环境 JMeter 终验报告存档（`docs/performance/`）
- [ ] SLS 日志脱敏抽检无明文手机号/密钥（`operations-handbook.md` §4）
- [ ] `PREV_STABLE_TAG` 已确认可回滚
- [ ] 灰度观察人到位，回滚脚本可执行
- [ ] 发布/回滚记录归档
