# 开发指南（M7）

面向新成员的本地开发、编码规范、测试与贡献流程。上游约束以 `mission.md` / `tech-stack.md` / `code-style-guide.md` 为准。

---

## 1. 环境要求

- JDK 25（已验证 25.0.3 LTS）
- Maven ≥ 3.9.16
- Docker（本地 PostgreSQL 18；Docker 不可用时 Testcontainers PG 套件自动跳过，`mvn verify` 仍常绿）

## 2. 本地启动

```bash
# 1. 启动本地 PostgreSQL 18（db=pocket_money user=pocket password=pocket_local_only）
docker compose -f config/docker/docker-compose.yml up -d

# 2. 注入环境变量（与 compose PG 对齐 + 两枚必填密钥），启动（默认 local profile）
export DB_URL=jdbc:postgresql://localhost:5432/pocket_money
export DB_USERNAME=pocket
export DB_PASSWORD=pocket_local_only
export JWT_SECRET=<Base64 编码的 ≥32 字节随机密钥>
export DATA_ENCRYPTION_KEY=<Base64 编码的 32 字节 AES 密钥>
mvn spring-boot:run
```

> 密钥无默认值、缺失即启动失败（fail-fast）。本地可用任意满足长度的随机 Base64（仅开发用，禁止用于任何真实环境）。

启动后验证：

| 入口 | 地址 |
|---|---|
| 存活探针 | http://localhost:8080/actuator/health/liveness |
| 就绪探针 | http://localhost:8080/actuator/health/readiness |
| Swagger UI（仅非生产） | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

## 3. 包结构与分层

命名空间 `wyq.pocket.money`，按业务模块 + 技术分层：

```
common/   安全(JWT/SecurityConfig/过滤器)、加密(AES-256-GCM)、审计、Web(异常/校验)、
          日志(脱敏)、幂等、韧性(限流/熔断)、持久化
user/     认证 + 用户 + 家庭
money/    账户、收支流水、看板
rule/     规则 CRUD 与结算
finance/  收支报表、统计
ai/       意图解析、Function Calling、二次确认状态机、四端口抽象
notify/   站内信、外部投递、事件驱动
```

模块内分层：`controller → service → mapper → domain`，DTO/VO 分离；模块间经 Spring Event 解耦（M5），禁止跨层直接调用。

## 4. 编码规范（要点）

- 完整规则见 `code-style-guide.md`；Checkstyle/PMD/SpotBugs 在 `verify` 阶段强制。
- DO/DTO/VO 分层；禁止魔法值（常量/枚举）；禁止空 catch；圈复杂度 ≤ 5。
- MyBatis 一律 `#{}` 参数化，**禁止** `${}` 拼接 SQL（M6 SQLi 专项已核验）。
- 敏感信息零硬编码；日志经 `MaskingRules`/`MaskingJsonEncoder` 脱敏。
- 公共 API 写 Javadoc（Checkstyle Javadoc 为 error 级）。

## 5. 测试

```bash
mvn clean verify          # 全门禁：编译 → 单测/集成测试 → JaCoCo(≥80%) → Checkstyle/PMD/SpotBugs
mvn -Dtest=XxxTest test   # 单跑
```

| 层 | 形态 | 说明 |
|---|---|---|
| 单元测试 | JUnit 5 + Mockito + AssertJ | 覆盖率门禁主力，AAA 模式 + 测试数据工厂 |
| H2 集成测试 | `@SpringBootTest(RANDOM_PORT)` + RestAssured | PG 兼容内存库，Flyway 全量迁移，常跑 |
| PG 集成测试 | Testcontainers PostgreSQL 18 | `disabledWithoutDocker=true`，无 Docker 自动跳过 |
| 安全专项 | `src/test/.../security/` | SQLi/XSS/CSRF/IDOR/敏感数据（M6） |
| HTTP 故障 | WireMock（`support/AiProviderWireMock`） | AI/语音 HTTP 依赖故障注入（M6 D49） |
| 性能基准 | `@Tag("performance")`（surefire 默认排除） | 正式 10 TPS 压测见 `scripts/jmeter/` |

- 功能与测试同提交，不欠测试；覆盖率报告 `target/site/jacoco/index.html`。
- 生产数据禁止用于测试，一律 `PerformanceDataSeeder` / 测试工厂数据。

## 6. 数据库迁移（Flyway）

- 脚本：`src/main/resources/db/migration/V{序号}__{描述}.sql`（当前 V1–V10）。
- 已发布脚本**永不修改**；变更用新脚本前向修复（`Vn__xxx.sql`），回滚也走新脚本。
- 应用启动自动迁移；本地 H2 与生产 PostgreSQL 均经 Flyway。

## 7. 配置

- 非敏感：`application.yml` + `application-{local,dev,test,prod}.yml`（`${ENV:default}` 占位）。
- 敏感：环境变量注入（`JWT_SECRET` / `DATA_ENCRYPTION_KEY` / `DB_*`）；生产走云效凭据库（见 `../deploy/deployment-guide.md`）。
- 硬编码扫描：`bash scripts/secret-scan.sh`。

## 8. 贡献流程

1. 分支：`feature/<issue>-x` / `bugfix/...` / `hotfix/...`；`main` 保护。
2. 提交：约定式提交 `<type>(<scope>): <subject>`。
3. 合入：MR + ≥1 人评审 + CI 全绿（secret-scan / `mvn verify` / 云效代码检测）。
4. 文档随代码更新（API 变更同步 OpenAPI 注解与文档，不积压）。
5. 资金/安全类代码强制双人评审。
