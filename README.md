# 零花钱管理系统后端服务（PorketMoneyServer）

为鸿蒙 APP 提供零花钱管理能力的后端服务：家庭零花钱智能管理 + AI 语音交互 + 家长监管。
单体分层架构，Docker 部署至阿里云。

## 里程碑进展

| 里程碑 | 状态 | 说明 |
|---|---|---|
| M0 工程骨架 | ✅ | Boot 4.1.0 + JDK 25 基线、质量门禁、Flyway、健康探针 |
| M1 账号与家庭 | ✅ | JWT 双令牌认证、登录锁定、家庭/孩子账号管理、AES-256-GCM 手机号加密、审计与安全日志 |
| M2 零花钱核心业务 | ✅ | 看板、规则与自动结算、收支流水、学习/工作价值零花钱、家庭报表（全事务 + 余额不变式） |
| M3 移动端适配与可靠性 | ✅ | 资金写幂等键、限流、熔断/重试/超时降级、ZGC + 容器感知堆 JVM 基线 |
| M4 AI 交互集成 | ✅ | 意图解析、Function Calling、资金指令二次确认、操作审计、会话 TTL 清理、四端口抽象（真实大模型 DeepSeek 已接入，D67；Stub 桩保底） |
| M5 通知与事件驱动 | ✅ | 站内信、Spring Event 解耦、投递 relay 重试（外部推送鸿蒙 Push Kit 已接入，D68） |
| M6 测试加固与安全 | ✅ | 覆盖率 ≥80% 门禁、WireMock 故障注入、JMeter 10 TPS 计划、OWASP/合规审查、渗透机制 |
| M7 部署与发布 | ✅ | Docker 镜像、阿里云部署资产、云效灰度/回滚流水线、ARMS/SLS 监控资产、备份 Runbook、文档五件套 |
| GA 1.0 正式发布 | 🚧 准入门就绪 | 发布工程与 Go/No-Go 检查单齐套（`GA-detailed-design.md`）；云上演练/割接待目标环境执行 |

## 文档索引

| 文档 | 内容 |
|---|---|
| `mission.md` / `tech-stack.md` / `code-style-guide.md` | 上游约束（使命 / 技术栈 / 编码规范） |
| `roadmap.md` | 开发路线图（M0 ~ GA + GA 后运营） |
| `M0`–`M7-detailed-design.md`、`GA-detailed-design.md` | 各里程碑详细设计（决策记录 D1–D68） |
| `CHANGELOG.md` | 1.0.0 发布说明（功能清单 / 已知限制 / 部署须知） |
| `docs/release/go-live-checklist.md` | GA 上线 Go/No-Go 检查单（量化基线追溯 + 签核 + 运营节奏） |
| `docs/deploy/` | 部署文档、发布/回滚 Runbook |
| `docs/ops/` | 运维手册、备份与恢复 Runbook |
| `docs/dev/` | 开发指南（本地环境、规范、贡献流程） |
| `docs/security/` | OWASP 自查、认证加密审查、合规审查、渗透计划、生产安全操作手册 |
| `docs/version-matrix.md` | 依赖版本矩阵与构建迭代记录（Spike 产出） |

## 环境要求

- JDK 25（已验证：25.0.3 LTS）
- Maven ≥ 3.9.16（`winget install Apache.Maven`）
- Docker（本地 PostgreSQL 18；Docker 不可用时 Testcontainers 集成套件自动跳过，
  `mvn verify` 保持常绿）

## 快速开始

```bash
# 1. 启动本地 PostgreSQL 18
docker compose -f config/docker/docker-compose.yml up -d

# 2. 注入必需密钥（无默认值，缺失即启动失败）后启动服务（默认 local profile）
export JWT_SECRET=<Base64 编码的 ≥32 字节随机密钥>
export DATA_ENCRYPTION_KEY=<Base64 编码的 32 字节 AES 密钥>
mvn spring-boot:run
```

启动后可验证：

| 入口 | 地址 |
|---|---|
| 存活探针 | http://localhost:8080/actuator/health/liveness |
| 就绪探针 | http://localhost:8080/actuator/health/readiness |
| Swagger UI（仅非生产） | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

Swagger UI 已声明 `bearerAuth`（HTTP Bearer JWT）安全方案：受保护接口点击
Authorize 粘贴登录返回的 access 令牌即可联调。错误码总则见 OpenAPI 文档描述
（认证拒绝 HTTP 401 + 100003；授权拒绝 HTTP 403 + 100004；其余业务错误
HTTP 200 + code）。

## 构建与质量门禁

```bash
mvn clean verify
```

一条命令串联：编译 → 单元/集成测试（JaCoCo 覆盖率 ≥ 80%）→ Checkstyle → PMD → SpotBugs。
任一环节失败即构建失败（CI 中对应拦截合入）。

测试分层：

| 层 | 形态 | 说明 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito | 服务/组件级，覆盖率门禁主力 |
| H2 集成测试 | `@SpringBootTest(RANDOM_PORT)` + RestAssured | PostgreSQL 兼容模式内存库，Flyway 全量迁移，常跑 |
| PG 集成测试 | Testcontainers PostgreSQL 18 | 认证/家庭/资金/规则/AI/通知关键路径、权限矩阵、幂等、静态加密落库；`@Testcontainers(disabledWithoutDocker = true)` 自动跳过托底 |
| HTTP 故障 / 安全专项 | WireMock + `src/test/.../security/` | AI/语音依赖超时/4xx/5xx 故障注入（M6）；SQLi/XSS/CSRF/IDOR/敏感数据暴露专项 |
| 性能基准 | JMeter（`scripts/jmeter/`） | 10 TPS 容量场景，断言 P95 ≤ 500ms（发布前目标环境终验） |

覆盖率报告：`target/site/jacoco/index.html`

## 配置与敏感信息

- 非敏感配置：`application.yml` + `application-{local,dev,test,prod}.yml`
- 敏感值一律环境变量注入（无默认值、缺失即启动失败）：
  `JWT_SECRET`（HS256 签名密钥，Base64 ≥ 32 字节）/
  `DATA_ENCRYPTION_KEY`（AES-256-GCM 数据密钥，Base64 32 字节）/
  `DEEPSEEK_API_KEY`（真实大模型 DeepSeek，D67）/
  `HARMONY_PUSH_APP_ID` / `HARMONY_PUSH_CLIENT_ID` / `HARMONY_PUSH_CLIENT_SECRET`（鸿蒙 Push Kit，D68）；
  其余：`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `SERVER_PORT` / `SPRING_PROFILES_ACTIVE` / `SPRING_AI_MODEL_CHAT`
- 本地可用 `.env.example` 复制为 `config/docker/.env`
- 硬编码敏感信息扫描：`bash scripts/secret-scan.sh`
- 生产令牌仅存于服务端环境变量/密钥服务；鸿蒙端令牌存储指引见
  `M1-detailed-design.md`（refresh 令牌存设备安全存储，如 HUKS）

## 目录结构（GA 基线）

```
src/main/java/wyq/pocket/money/
├── PocketMoneyApplication.java      # 启动类
├── common/                          # 公共层
│   ├── security/                    # SecurityConfig / JWT / 过滤器 / OAuth2 扩展点
│   ├── crypto/                      # AES-256-GCM 加解密、SHA-256 哈希
│   ├── audit/                       # 审计落库与 SECURITY 日志
│   ├── ai/                          # AI 四端口抽象（Chat/ASR/TTS/Embedding）+ SpringAiChatPort（DeepSeek）+ StubChatPort
│   └── web/exception/trace/log/persistence/validation/resilience/idempotency/
├── user/                            # 认证 + 用户 + 家庭
├── money/                           # 账户、收支流水、看板
├── rule/                            # 规则 CRUD 与结算
├── finance/                         # 收支报表、统计
├── ai/                              # 意图解析、Function Calling、二次确认状态机、评测集（eval/）
└── notify/                          # 站内信、事件投递（PushPort + HarmonyPushPort + NoopPushPort）
config/
├── checkstyle/checkstyle.xml        # 代码格式规则（Javadoc error 级）
├── pmd/pmd-ruleset.xml              # PMD 规则集（圈复杂度 ≤ 5、禁止空 catch 等）
├── spotbugs/exclude.xml             # SpotBugs 定点豁免（每条附理由）
└── docker/                          # docker-compose.yml（本地 PG18）/ docker-compose.prod.yml（生产参考）
scripts/
├── secret-scan.sh                   # 敏感信息扫描
├── deploy/                          # wait-health / slb-set-weight / observe-canary / rollback
├── release/cut-release.sh           # 版本切割（1.0.0 tag，GA D63）
└── jmeter/                          # 10 TPS 压测计划
Dockerfile                           # 多阶段镜像（分层 jar / 非 root / ZGC / HEALTHCHECK）
yunxiao-pipeline.yml                 # 云效 CI/CD 七阶段（含镜像推送与生产金丝雀）
docs/                                # 五件套 + 安全报告 + release 检查单（见文档索引）
```

## 数据库迁移（Flyway）

脚本位于 `src/main/resources/db/migration/`，命名 `V{顺序号}__{描述}.sql`，
当前 **V1–V10**（用户/家庭、账户流水、规则发放、学习任务、幂等记录、AI 会话、通知、设备推送令牌）。
已发布脚本永不修改；回滚用新脚本前向修复。应用启动自动执行迁移。

## 故障排查（首次构建）

依赖版本为 M0/M1 spike 目标值（见 `docs/version-matrix.md`）。常见首建问题：

| 现象 | 处置 |
|---|---|
| `spring-boot-starter-webmvc` 解析失败 | 换回 `spring-boot-starter-web` |
| SpotBugs/JaCoCo 报 class file major version 69 | 升级对应插件版本（pom properties） |
| Flyway 迁移未执行（日志零 flyway 条目） | Boot 4 需补 `spring-boot-flyway` 依赖（M1-D7） |
| 启动报 `JWT_SECRET 未配置` | 敏感密钥无默认值，按上文注入环境变量 |
| MyBatis / SpringDoc 与 Boot 4 不兼容 | 按 `docs/version-matrix.md` 失败处置列调整并记录 |

## 协作约定

- 分支：`feature/<issue>-x`、`bugfix/...`、`hotfix/...`；`main` 保护
- 提交：约定式提交 `<type>(<scope>): <subject>`
- 合入：MR + ≥1 人评审 + CI 全绿（模板见 `.gitlab/merge_request_templates/default.md`）
