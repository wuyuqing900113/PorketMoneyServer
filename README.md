# 零花钱管理系统后端服务（PorketMoneyServer）

为鸿蒙 APP 提供零花钱管理能力的后端服务：家庭零花钱智能管理 + AI 语音交互 + 家长监管。
单体分层架构，Docker 部署至阿里云。

## 文档索引

| 文档 | 内容 |
|---|---|
| `mission.md` | 项目使命与开发约束（上游约束） |
| `tech-stack.md` | 技术栈规范（上游约束） |
| `code-style-guide.md` | 编码规范（上游约束） |
| `roadmap.md` | 开发路线图（M0 ~ GA） |
| `M0-detailed-design.md` | M0 详细设计（当前阶段基线） |
| `docs/version-matrix.md` | 依赖版本矩阵（Spike 产出） |

## 环境要求

- JDK 25（已验证：25.0.3 LTS）
- Maven ≥ 3.9.16（`winget install Apache.Maven`）
- Docker（本地 PostgreSQL 18）

## 快速开始

```bash
# 1. 启动本地 PostgreSQL 18
docker compose -f config/docker/docker-compose.yml up -d

# 2. 启动服务（默认 local profile）
mvn spring-boot:run
```

启动后可验证：

| 入口 | 地址 |
|---|---|
| 存活探针 | http://localhost:8080/actuator/health/liveness |
| 就绪探针 | http://localhost:8080/actuator/health/readiness |
| Swagger UI（仅非生产） | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

## 构建与质量门禁

```bash
mvn clean verify
```

一条命令串联：编译 → 单元测试（JaCoCo 覆盖率 ≥ 80%）→ Checkstyle → PMD → SpotBugs。
任一环节失败即构建失败（CI 中对应拦截合入）。

覆盖率报告：`target/site/jacoco/index.html`

## 配置与敏感信息

- 非敏感配置：`application.yml` + `application-{local,dev,test,prod}.yml`
- 敏感值一律环境变量注入：`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `SERVER_PORT` / `SPRING_PROFILES_ACTIVE`
- 本地可用 `.env.example` 复制为 `config/docker/.env`
- 硬编码敏感信息扫描：`bash scripts/secret-scan.sh`

## 目录结构（M0 基线）

```
src/main/java/wyq/pocket/money/
├── PocketMoneyApplication.java      # 启动类
├── common/                          # 公共层（web/exception/trace/log/persistence/validation）
└── user|money|rule|finance|ai|notify/   # 业务模块骨架（controller/service/mapper/domain/dto）
config/
├── checkstyle/checkstyle.xml        # 代码格式规则
├── pmd/pmd-ruleset.xml              # PMD 规则集（圈复杂度 ≤ 5、禁止空 catch 等）
└── docker/docker-compose.yml        # 本地 PostgreSQL 18
scripts/secret-scan.sh               # 敏感信息扫描
```

## 数据库迁移（Flyway）

脚本位于 `src/main/resources/db/migration/`，命名 `V{顺序号}__{描述}.sql`。
已发布脚本永不修改；回滚用新脚本前向修复。应用启动自动执行迁移。

## 故障排查（首次构建）

依赖版本为 M0 spike 目标值（见 `docs/version-matrix.md`）。常见首建问题：

| 现象 | 处置 |
|---|---|
| `spring-boot-starter-webmvc` 解析失败 | 换回 `spring-boot-starter-web` |
| SpotBugs/JaCoCo 报 class file major version 69 | 升级对应插件版本（pom properties） |
| MyBatis / SpringDoc 与 Boot 4 不兼容 | 按 `docs/version-matrix.md` 失败处置列调整并记录 |

## 协作约定

- 分支：`feature/<issue>-x`、`bugfix/...`、`hotfix/...`；`main` 保护
- 提交：约定式提交 `<type>(<scope>): <subject>`
- 合入：MR + ≥1 人评审 + CI 全绿（模板见 `.gitlab/merge_request_templates/default.md`）
