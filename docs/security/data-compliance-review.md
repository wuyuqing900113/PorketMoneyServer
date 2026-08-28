# 数据合规自查记录（M6）

| 项 | 值 |
|---|---|
| 里程碑 | M6（测试加固与安全） |
| 依据 | `M6-detailed-design.md` §10（D54） |
| 日期 | 2026-08-28 |
| 结论 | COPPA 类复核、日志脱敏覆盖、测试数据脱敏三项均通过 |

---

## 1. 儿童个人信息保护（COPPA 类）复核

| 条款 | 落地依据 | 复核结论 |
|---|---|---|
| 家长同意（verifiable consent） | 孩子账号由家长创建，家长同意内置（M1 D3） | ✅ 注册/加孩子链路同意路径已落地并留痕 |
| 数据最小化 | 儿童仅采集登录名/昵称/零花钱（M1 D5） | ✅ `app_user` 儿童字段无超采 |
| 语音隐私 | 语音不落盘，无音频列/文件；`AiCleanupJob` 按 TTL 清理会话/消息/待确认动作 | ✅ `AiCleanupJob.java`（`ai/job/AiCleanupJob.java`，cleanup cron `04:43` 每日） |
| 会话清理 | 会话/消息/待确认动作按 TTL 定期清理 | ✅ `AiCleanupJob.java` 覆盖清理范围与触发 |
| 数据删除 | 家庭成员移除级联清理 | ✅ `MemberRemovedMoneyListener.java`（`money/service/event/`）级联清理，无孤儿数据残留 |

## 2. 日志脱敏覆盖率验证

- **规则**：`MaskingRules.java:28-39` 四类规则按序执行 —— 身份证（18 位，前 3 后 4）、手机号（11 位，前 3 后 4）、银行卡（16~19 位，仅后 4）、密钥类键值对（password/secret/api_key/token/authorization 值整体替换为 `******`）。
- **编码器**：`MaskingJsonEncoder.java:18,21-27,37` 继承 `LogstashEncoder`，对 JSON 日志整体执行 `MaskingRules.mask`，覆盖 `message` 与 MDC 值；`logback-spring.xml` 已挂载。
- **抽检结论**：auth/money/rule/ai/notify 各链路日志输出经统一编码器脱敏，无明文手机号/密码/密钥/身份证放大。M6 自动化侧由 `SensitiveDataExposureTest` 佐证出参无密钥与明文手机号（`owasp-self-check-report.md` §3.4）。

## 3. 测试数据脱敏流程

- **规则**：生产数据禁止导入测试环境；测试数据一律经 `PerformanceDataSeeder` / 测试工厂生成（确定性、无真实个人信息）。
- **依据**：`src/test/java/wyq/pocket/money/integration/support/PerformanceDataSeeder.java` 提供确定性种子；H2/Postgres 测试均以工厂数据驱动，不含真实手机号/身份证/姓名。
- **验证结论**：测试库无真实个人信息；`docs/` 与测试资源无真实样例数据（测试密钥为全零固定占位值，注释明确「仅测试用」）。
