# 变更日志（Changelog）

本项目遵循[语义化版本](https://semver.org/lang/zh-CN/)（SemVer）与 Keep a Changelog 格式。
里程碑详细设计见 `M0`–`M7-detailed-design.md`、`GA-detailed-design.md`；部署与运维见 `docs/`。

---

## [1.0.0] - 2027-01（GA，计划发布窗口）

零花钱管理系统后端首个正式版本：家庭零花钱管理 + AI 语音交互编排 + 家长监管，
单体分层架构，Docker 部署至阿里云。

### 新增（Added）

- **工程基座（M0）**：Spring Boot 4.1.0 + JDK 25（虚拟线程）；统一响应体/错误码/全局异常处理/入参校验；Flyway 数据库迁移（V1–V10）；Actuator 健康探针（liveness/readiness）；Logback JSON 结构化日志与敏感信息脱敏；Checkstyle/PMD/SpotBugs/JaCoCo（≥80%）质量门禁；云效 CI 流水线。
- **认证与家庭（M1）**：JWT 双令牌（access/refresh）登录、登出、刷新与失效；登录失败锁定；家长/孩子角色与权限矩阵；家庭创建/查看/编辑与成员关系维护；密码 SHA-256 哈希、敏感字段 AES-256-GCM 加密存储；关键操作审计日志与安全日志；OAuth2 第三方登录扩展点预留。
- **零花钱核心业务（M2）**：家庭零花钱看板（余额汇总、趋势）；包月/个性化规则 CRUD 与规则引擎自动结算（定时任务）；零花钱存入/提取流水与历史查询；学习任务定义→完成确认→奖励发放链路；家长工作价值记录与配套分配；家庭维度收支报表（同步聚合）；金额 DECIMAL 精度处理与全事务保证；对账定时任务。
- **移动端适配与可靠性（M3）**：资金变动接口幂等键（`Idempotency-Key` 防重复提交）；接口限流；外部依赖超时/重试/熔断降级（Resilience4j）；HikariCP 连接池调优；ZGC + 容器感知堆（`MaxRAMPercentage`）JVM 基线；客户端可重试的友好错误码。
- **AI 交互编排（M4）**：语音指令意图解析（有限意图集，数据类回答实时查账）；AI Function Calling（工具注册与调用，走既有事务/权限体系）；资金类语音指令二次确认状态机；AI 操作执行路径全程审计；会话与语音数据 TTL 清理（零残留）；AI 调用限流与超时降级；四端口抽象（`ChatPort`/`SpeechToTextPort`/`TextToSpeechPort`/`EmbeddingPort`）；真实大模型 DeepSeek 适配器 `SpringAiChatPort`（经 Spring AI OpenAI 兼容协议，`AI_MOCK=false` 启用，D67）与进程内确定性桩 `StubChatPort`；AI 准确率评测集与评测器（`ai/eval`）。
- **通知与事件驱动（M5）**：站内信通知（未读数、分页、已读）；账务变动→通知/看板/审计的 Spring Event 解耦；外部投递 relay 重试与状态记录（`PushPort` 端口 + `NoopPushPort` 桩）；鸿蒙 Push Kit 服务端适配器 `HarmonyPushPort` + 设备令牌注册（`user_push_token` 表，V10 迁移，D68）；通知清理定时任务。
- **测试加固与安全（M6）**：全模块单测覆盖率 ≥80%（JaCoCo 门禁）；Testcontainers PostgreSQL 18 集成套件（无 Docker 自动跳过托底）；WireMock HTTP 故障注入（AI/语音依赖超时/4xx/5xx/畸形响应）；JMeter 10 TPS 压测计划与断言；OWASP Top 10 自查（SQLi/XSS/CSRF/IDOR）；认证与加密实现审查；儿童隐私（COPPA 类）合规审查；季度渗透测试机制（首轮 GA 前）；敏感数据暴露专项测试。
- **部署与发布（M7）**：多阶段 Docker 镜像（Spring Boot 分层 jar、非 root 运行、ZGC JVM 参数、容器健康检查）；阿里云部署资产（SLB HTTPS/TLS 1.3 → ECS ×2 跨可用区 → RDS PostgreSQL 18 主备；ACR 镜像托管；OSS 归档）；dev/test/prod 三环境隔离（环境变量注入 + 云效凭据库，零硬编码）；CI/CD 镜像构建推送 + 测试环境部署冒烟 + 生产金丝雀灰度（5%→50%→100%）与一键回滚脚本；ARMS 应用监控与 SLS 脱敏日志接入资产、告警规则基线；RDS 每日增量/每周全量备份策略与 PITR 恢复演练 Runbook；文档五件套（部署/运维/开发/安全操作 + API）。

### 安全（Security）

- 全链路 TLS 1.3（SLB 终结）；敏感字段 AES-256-GCM 静态加密；密码 SHA-256 加盐哈希。
- 密钥零硬编码：`JWT_SECRET` / `DATA_ENCRYPTION_KEY` / `DB_*` 仅经环境变量/云效凭据库注入，缺失即启动失败（fail-fast）；`scripts/secret-scan.sh` 硬编码扫描。
- 资金类接口全幂等 + 全事务；越权访问统一 403/100004；日志全量脱敏（手机号/身份证/银行卡/密钥）。
- 生产环境关闭 Swagger UI；actuator 详情 `when-authorized`。

### 升级/部署须知（Upgrade notes）

- 必填环境变量（无默认值）：`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET`（Base64 ≥32 字节）/ `DATA_ENCRYPTION_KEY`（Base64 32 字节）；生产 `SPRING_PROFILES_ACTIVE=prod`。
- 生产 AI / 推送（D67/D68）：`AI_MOCK=false` + `SPRING_AI_MODEL_CHAT=openai` + `DEEPSEEK_API_KEY`（DeepSeek）；`NOTIFY_PUSH_ENABLED=true` + `HARMONY_PUSH_APP_ID` / `HARMONY_PUSH_CLIENT_ID` / `HARMONY_PUSH_CLIENT_SECRET`（鸿蒙 Push Kit）。启用对应能力时缺失即启动失败（fail-fast）。
- 数据库：应用启动自动执行 Flyway 迁移（V1–V10），已发布脚本永不修改；`DATA_ENCRYPTION_KEY` 轮换需走重加密迁移（见 `docs/security/production-security-operations.md` §2），不可直接换值。
- 版本切割与发布流程见 `GA-detailed-design.md` §4 与 `docs/release/go-live-checklist.md`。

### 已知限制（Known limitations）

- ASR 语音识别 / TTS 语音合成 provider 尚未接入：真实大模型对话已接 DeepSeek（D67），语音能力仅有 `SpeechToTextPort` / `TextToSpeechPort` 端口契约（无适配器），provider 选型（roadmap 前置决策 #3）后迭代。
- Embedding 语义匹配仅有端口契约（`EmbeddingPort`），无适配器。
- 短信通道未接入：外部推送已接鸿蒙 Push Kit（D68），短信渠道待后续扩展（`PushPort` 端口按 provider 扩展）。
- Docker 基础镜像 tag 待构建环境 spike 锁定（见 `docs/version-matrix.md` §10）。
- 云上演练项（10 TPS 压测终验、金丝雀/回滚演练、PITR 恢复演练、告警链路演练、SLS 脱敏抽检、首轮渗透测试）在目标环境执行，准入状态以 `docs/release/go-live-checklist.md` 签核为准。
