# 零花钱管理系统后端服务（PorketMoneyServer）

为鸿蒙 APP 提供零花钱管理能力的后端服务：家庭零花钱智能管理 + AI 语音交互 + 家长监管。
单体分层架构，Docker 部署至阿里云。

## 里程碑进展

| 里程碑 | 状态 | 说明 |
|---|---|---|
| M0 工程骨架 | ✅ | Boot 4.1.0 + JDK 25 基线、质量门禁、Flyway、健康探针 |
| M1 账号与家庭 | ✅ | JWT 双令牌认证、登录锁定、家庭/孩子账号管理、AES-256-GCM 手机号加密、审计与安全日志、RestAssured + Testcontainers 集成测试（PG 套件随 Docker 可用性自动启停） |
| M2+ | ⏸ | 见 `roadmap.md` |

## 文档索引

| 文档 | 内容 |
|---|---|
| `mission.md` | 项目使命与开发约束（上游约束） |
| `tech-stack.md` | 技术栈规范（上游约束） |
| `code-style-guide.md` | 编码规范（上游约束） |
| `roadmap.md` | 开发路线图（M0 ~ GA） |
| `M0-detailed-design.md` | M0 详细设计 |
| `M1-detailed-design.md` | M1 详细设计（当前阶段基线） |
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
| PG 集成测试 | Testcontainers PostgreSQL 18 | 认证主链路 / 失败场景 / 令牌重用 / 锁定 / 家庭 CRUD / mcp / 权限矩阵（附录 B 端点×身份）/ 静态加密落库；`@Testcontainers(disabledWithoutDocker = true)` 自动跳过托底 |

覆盖率报告：`target/site/jacoco/index.html`

## 配置与敏感信息

- 非敏感配置：`application.yml` + `application-{local,dev,test,prod}.yml`
- 敏感值一律环境变量注入（无默认值、缺失即启动失败）：
  `JWT_SECRET`（HS256 签名密钥，Base64 ≥ 32 字节）/
  `DATA_ENCRYPTION_KEY`（AES-256-GCM 数据密钥，Base64 32 字节）；
  其余：`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `SERVER_PORT` / `SPRING_PROFILES_ACTIVE`
- 本地可用 `.env.example` 复制为 `config/docker/.env`
- 硬编码敏感信息扫描：`bash scripts/secret-scan.sh`
- 生产令牌仅存于服务端环境变量/密钥服务；鸿蒙端令牌存储指引见
  `M1-detailed-design.md`（refresh 令牌存设备安全存储，如 HUKS）

## 目录结构（M1 基线）

```
src/main/java/wyq/pocket/money/
├── PocketMoneyApplication.java      # 启动类
├── common/                          # 公共层
│   ├── security/                    # M1：SecurityConfig / JWT / 过滤器 / mcp 门禁 / OAuth2 扩展点
│   ├── crypto/                      # M1：AES-256-GCM 加解密、SHA-256 哈希
│   ├── audit/                       # M1：审计落库与 SECURITY 日志
│   └── web/exception/trace/log/persistence/validation/
├── user/                            # M1：认证 + 用户 + 家庭（controller/service/mapper/domain/dto）
└── money/rule/finance/ai/notify/    # 后续里程碑业务模块骨架
config/
├── checkstyle/checkstyle.xml        # 代码格式规则（Javadoc 自 M1 起 error 级）
├── pmd/pmd-ruleset.xml              # PMD 规则集（圈复杂度 ≤ 5、禁止空 catch 等）
├── spotbugs/exclude.xml             # SpotBugs 定点豁免（每条附理由）
└── docker/docker-compose.yml        # 本地 PostgreSQL 18
scripts/secret-scan.sh               # 敏感信息扫描
```

## 数据库迁移（Flyway）

脚本位于 `src/main/resources/db/migration/`，命名 `V{顺序号}__{描述}.sql`。
已发布脚本永不修改；回滚用新脚本前向修复。应用启动自动执行迁移。
M1 脚本：V2（app_user / family / family_member）、V3（user_refresh_token /
audit_log / user_oauth_binding 预留）。

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
