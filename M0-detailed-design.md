# M0 工程基建与骨架 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（M0 章节）
> 文档版本：v1.0（2026-08-17，草案，待评审）
> 适用范围：M0 阶段（第 1–2 周，08-17 ~ 08-30）

---

## 1. 概述

### 1.1 目标
建立可持续交付的工程底座：可启动的 Spring Boot 4.1.0 服务骨架、统一的 Web 公共层、本地数据库环境、质量工具链与 CI 流水线，使后续所有业务开发在统一规范与自动化门禁下进行。

### 1.2 范围（In Scope）
- Maven 工程与依赖基线（含版本兼容性 spike）
- 模块优先分层的包结构
- 统一响应体、数字分段错误码、全局异常处理、输入校验、TraceId
- 配置分层（profiles + 环境变量）
- PostgreSQL 18 本地环境（Docker Compose）+ Flyway 迁移机制
- 结构化日志与敏感信息脱敏
- Actuator 健康检查、SpringDoc API 文档
- Checkstyle / PMD / SpotBugs / JaCoCo 质量门禁
- 云效 CI 流水线、Git 工作流

### 1.3 非目标（Out of Scope）
| 事项 | 归属阶段 |
|---|---|
| 任何业务功能代码（用户/家庭/零花钱等） | M1 及以后 |
| Spring Security 认证体系 | M1 |
| Docker 镜像构建与阿里云部署 | M7 |
| SonarQube 部署 | M6 前接入（本期仅预留插件与门禁占位） |
| 性能调优 / GC 选型评估 | M3 |
| Testcontainers 集成测试基建 | M1 引入 |

---

## 2. 决策记录（已确认）

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D1 | 包结构组织方式 | **模块优先分层** | `wyq.pocket.money.<module>.<layer>`；与 code-style-guide 示例冲突处，以本设计为准并回写规范（见 §16） |
| D2 | 数据库脚本管理 | **Flyway** | SQL 脚本版本化，随应用启动自动迁移 |
| D3 | CI/CD 平台 | **阿里云云效** | 与 M7 阿里云部署链集成 |
| D4 | API 文档 | **SpringDoc OpenAPI** | 注解驱动，自动生成 OpenAPI 3 |
| D5 | 配置管理 | **Spring profiles + 环境变量** | 敏感值一律环境变量注入，零硬编码 |
| D6 | 新版本兼容风险 | **先做 spike 验证** | M0 前 2~3 天完成，产出 `version-matrix.md` |
| D7 | 错误码格式 | **6 位数字分段码** | 按模块划分段位（§6.2） |
| D8 | SonarQube 落地 | **M6 前接入** | M0 以 Checkstyle/PMD/SpotBugs + 云效自带检测为门禁 |

---

## 3. 总体设计

### 3.1 目标形态（M0 结束时）

```
鸿蒙 APP（未来）
    │ HTTPS / TLS 1.3
    ▼
┌──────────────────────────────────────────────┐
│  Spring Boot 4.1.0 单体服务（JDK 25，虚拟线程） │
│  ┌────────────────────────────────────────┐  │
│  │ common 公共层                            │  │
│  │ 统一响应 / 错误码 / 全局异常 / 校验 / 日志 │  │
│  ├────────────────────────────────────────┤  │
│  │ 业务模块包（M0 仅建空骨架）：              │  │
│  │ user / money / rule / finance / ai / notify │
│  ├────────────────────────────────────────┤  │
│  │ MyBatis + HikariCP + Flyway             │  │
│  └────────────────────────────────────────┘  │
│  Actuator 健康检查 │ SpringDoc 文档            │
└──────────────────────────────────────────────┘
    │
    ▼
PostgreSQL 18（本地 Docker Compose；生产为阿里云 RDS，M7）
```

### 3.2 Maven 工程形态
- **单 Maven 模块**（单体架构，模块边界用包结构约束，不拆子模块）
- 沿用现有坐标：`wyq.pocket.money : PorketMoneyServer : 1.0-SNAPSHOT`
- 构建入口统一为 `mvn clean verify`（编译 → 校验 → 测试 → 质量门禁一条链）

---

## 4. Spike 验证设计（D6，M0 第 1–3 天）

### 4.1 验证清单
在独立 spike 分支（`spike/version-compat`）上用最小工程逐项验证：

| # | 验证项 | 通过标准 |
|---|---|---|
| S1 | Spring Boot 4.1.0 + JDK 25 启动 | 应用正常启动；`spring.threads.virtual.enabled=true` 生效 |
| S2 | MyBatis Spring Boot Starter 兼容版本 | Mapper 扫描 + 一次真实 CRUD 冒烟通过 |
| S3 | Flyway（flyway-core + flyway-database-postgresql） | 对 PostgreSQL 18 执行迁移成功 |
| S4 | SpringDoc OpenAPI（Boot 4 兼容版本） | `/v3/api-docs` 与 Swagger UI 可访问 |
| S5 | logstash-logback-encoder | JSON 结构化日志正常输出 |
| S6 | Checkstyle / PMD / SpotBugs Maven 插件 | 在 Maven 3.9.16 + JDK 25 下正常运行并可失败构建 |
| S7 | JaCoCo | JDK 25 字节码插桩与覆盖率报告正常 |
| S8 | Lombok（若使用） | JDK 25 编译兼容；**若不兼容则放弃 Lombok，用 record / 手写替代**（code-style-guide 需同步） |
| S9 | 云效构建环境 JDK 25 可用性 | 公共构建集群有 JDK 25；否则构建自定义构建镜像 |

### 4.2 产出物
- `docs/version-matrix.md`：锁定全部依赖的**已验证版本组合**、不兼容项与替代方案
- spike 结论评审通过后，才允许合入主干基建代码
- 若关键依赖（如 SpringDoc）暂无 Boot 4 兼容版本：记录降级预案（如 API 文档临时改为导出静态 JSON），**不得**为此私自降低 Spring Boot 版本（违反 tech-stack 需走变更评审）

---

## 5. 包结构设计（D1）

### 5.1 总体规则
- 顶层：`wyq.pocket.money`
- 二级：**业务模块**（user、money、rule、finance、ai、notify）+ **公共层**（common）
- 三级：模块内分层（controller / service / mapper / domain / dto）
- 模块间调用：只允许经对方 `service` 层接口，禁止跨模块直接访问 mapper / controller

### 5.2 目录树（M0 交付形态）

```
src/main/java/wyq/pocket/money/
├── PocketMoneyApplication.java          # 启动类（替换现有 Main.java）
├── common/                              # 公共层（唯一可被所有模块依赖的包）
│   ├── web/
│   │   ├── Result.java                  # 统一响应体
│   │   ├── ErrorCode.java               # 错误码接口
│   │   ├── CommonErrorCode.java         # 通用段错误码枚举
│   │   └── GlobalExceptionHandler.java  # 全局异常处理
│   ├── exception/
│   │   └── BusinessException.java       # 业务异常基类（携带错误码）
│   ├── trace/
│   │   └── TraceIdFilter.java           # TraceId 注入（MDC + 响应头）
│   ├── validation/                      # 校验扩展（自定义注解等，按需）
│   └── log/
│       └── MaskingJsonEncoder.java      # 日志脱敏编码器
├── user/      {controller, service, mapper, domain, dto}   # 空骨架 + package-info.java
├── money/     ...
├── rule/      ...
├── finance/   ...
├── ai/        ...
└── notify/    ...

src/main/resources/
├── application.yml                      # 公共配置
├── application-local.yml                # 本地开发
├── application-dev.yml                  # 开发环境
├── application-test.yml                 # 测试环境
├── application-prod.yml                 # 生产环境（仅非敏感配置）
├── db/migration/                        # Flyway 脚本（D2）
│   └── V1__baseline.sql
└── logback-spring.xml                   # 日志配置

config/
├── checkstyle/checkstyle.xml
├── pmd/pmd-ruleset.xml
└── docker/docker-compose.yml            # 本地 PostgreSQL 18
```

### 5.3 分层职责与依赖方向
```
controller → service → mapper → DB
     │          │
     ▼          ▼
   dto        domain(DO)
```
- DTO/VO/DO 严格区分（code-style-guide §4）；controller 层禁止出现 DO
- 每个空模块放 `package-info.java` 说明模块职责（为 M1+ 开发定界）
- **可选加固**：引入 ArchUnit 单测固化"模块间依赖方向"规则，防止分层腐化（建议 M0 加入，成本约 0.5 人天）

---

## 6. Web 公共层设计

### 6.1 统一响应体 `Result<T>`

```java
public record Result<T>(
    int code,            // 0 = 成功；非 0 = 错误码（6 位数字分段码）
    String message,      // 面向客户端的提示语（可直接展示给用户）
    T data,              // 业务数据，失败时为 null
    String traceId,      // 链路追踪 ID，排障入口
    long timestamp       // 服务端毫秒时间戳
) { }
```

成功示例：
```json
{ "code": 0, "message": "success", "data": { }, "traceId": "8f3a2b1c", "timestamp": 1787654321000 }
```

失败示例：
```json
{ "code": 100001, "message": "参数校验失败：金额不能为空", "data": null, "traceId": "8f3a2b1c", "timestamp": 1787654321000 }
```

### 6.2 数字分段错误码（D7）

**编码规则**：6 位数字 = `AB CCCC`，AB 为模块段，CCCC 为段内序号；`0` 恒为成功。

| 段位 | 模块 | 定义阶段 |
|---|---|---|
| 0 | 成功 | M0 |
| 10xxxx | 通用（校验/认证/权限/系统交互） | M0（本节） |
| 20xxxx | 用户与家庭 | M1 |
| 30xxxx | 零花钱 | M2 |
| 40xxxx | 规则 | M2 |
| 50xxxx | 财务 | M2 |
| 60xxxx | AI | M4 |
| 70xxxx | 通知 | M5 |
| 90xxxx | 系统级错误 | M0（本节） |

**M0 基线错误码表**：

| 错误码 | 含义 | 客户端处理建议 |
|---|---|---|
| 100001 | 参数校验失败 | 修正输入，不可重试 |
| 100002 | 请求格式错误 | 不可重试 |
| 100003 | 未认证或登录态失效 | 跳转登录（M1 启用） |
| 100004 | 无权限执行该操作 | 提示用户，不可重试 |
| 100005 | 资源不存在 | 不可重试 |
| 100006 | 重复请求（幂等拦截） | 视为已受理，不可重试 |
| 100007 | 请求过于频繁（限流） | 延迟后重试 |
| 900001 | 系统内部错误 | **可重试**（携带幂等键） |
| 900002 | 下游服务超时 | **可重试** |
| 900003 | 数据库访问异常 | **可重试** |
| 900004 | 服务维护中 | **延迟后重试** |

**重试约定**（支撑 M3"客户端按错误码重试"）：
- **90xxxx 段为可重试错误**，其余段位默认不可重试；如需段内例外，在错误码定义处显式标注
- 客户端重试必须携带幂等键（幂等键协议在 M3 设计），服务端重试策略：指数退避，最多 3 次
- 错误码以 Java 枚举集中维护（`ErrorCode` 接口 + 各模块枚举），禁止散落魔法值（code-style-guide §4）

### 6.3 全局异常处理 `GlobalExceptionHandler`

`@RestControllerAdvice` 统一拦截，映射规则：

| 异常类型 | 映射错误码 | 说明 |
|---|---|---|
| `MethodArgumentNotValidException` / `ConstraintViolationException` | 100001 | 聚合全部字段错误进 message |
| `HttpMessageNotReadableException` | 100002 | 请求体解析失败 |
| `BusinessException` | 异常自带错误码 | 业务层显式抛出 |
| `AccessDeniedException` | 100004 | M1 接入 Security 后生效，M0 预留 |
| `NoResourceFoundException` | 100005 | 路径不存在 |
| `Exception`（兜底） | 900001 | 对外不暴露堆栈细节；完整堆栈 + traceId 落日志 |

设计要点：
- 所有响应（含异常）统一包裹为 `Result`，HTTP 状态码：业务错误 200（错误信息走 code），仅网关层错误使用 4xx/5xx —— **该约定写入 API 文档总则**，供鸿蒙端统一处理
- 兜底异常日志必须打印 traceId，禁止空 catch（code-style-guide §8）
- M0 提供 `BusinessException` + `CommonErrorCode`；各模块错误码枚举在对应阶段新增

### 6.4 输入校验框架
- 基于 Jakarta Bean Validation：Controller 入参 `@Validated`，DTO 字段注解（`@NotNull`、`@Size`、`@DecimalMin` 等）
- 金额类字段统一使用 `BigDecimal` + `@Digits(integer=10, fraction=2)`（为 M2 DECIMAL 精度约束打底）
- 校验失败由全局异常处理器转 100001，message 列出具体字段

### 6.5 TraceId 链路追踪
- `TraceIdFilter`（Servlet Filter，最高优先级）：请求头 `X-Trace-Id` 有值则沿用（支持端上透传），否则生成 8 字节十六进制短 ID
- 写入 MDC，日志 pattern 自动携带；写入响应头 `X-Trace-Id` 与响应体 `traceId` 字段
- 为 M4"AI 操作执行路径可追溯"提供底层支撑

---

## 7. 配置管理设计（D5）

### 7.1 分层原则
```
application.yml          公共配置（端口、应用名、MyBatis/Flyway/日志基础项）
application-{profile}.yml 环境差异配置（仅非敏感）
环境变量                  全部敏感值 + 环境相关连接串
```
- profiles：`local`（本地开发，默认）/ `dev` / `test` / `prod`
- **红线**：任何密码、密钥、Token 禁止出现在代码与配置文件中（mission 禁止项）；`application-*.yml` 中出现敏感词（password/secret/key 的值非占位符）时，由 CI 扫描拦截

### 7.2 环境变量清单（基线）

| 环境变量 | 说明 | 示例 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 激活 profile | `prod` |
| `DB_URL` | JDBC 连接串 | `jdbc:postgresql://host:5432/pocket_money` |
| `DB_USERNAME` | 数据库账号 | — |
| `DB_PASSWORD` | 数据库密码 | — |
| `SERVER_PORT` | 服务端口（默认 8080） | `8080` |

`application.yml` 中以占位符引用并提供 local 默认值：
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/pocket_money}
    username: ${DB_USERNAME:pocket}
    password: ${DB_PASSWORD:}   # local 环境 .env 提供，prod 必须显式注入
```

### 7.3 后续扩展
- M4 增加 AI 供应商 API Key 变量；M7 增加 OSS/ARMS/SLS 凭据变量，均遵循同一规则
- 如未来引入配置中心（Nacos），仅替换配置来源，配置键命名保持不变

---

## 8. 数据访问层设计

### 8.1 本地 PostgreSQL 18（Docker Compose）

`config/docker/docker-compose.yml`：
```yaml
services:
  postgres:
    image: postgres:18
    container_name: pocket-money-db
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: ${DB_NAME:-pocket_money}
      POSTGRES_USER: ${DB_USER:-pocket}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-pocket_local_only}
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-pocket}"]
      interval: 5s
      retries: 10
volumes: { pgdata: {} }
```
- 本地凭据仅用于本机开发，**不是**任何环境的真实凭据；`.env` 加入 `.gitignore`
- README 提供一键启动命令

### 8.2 Flyway 迁移机制（D2）
- 依赖：`flyway-core` + `flyway-database-postgresql`（具体版本以 spike 为准）
- 脚本目录：`src/main/resources/db/migration/`
- **命名规范**：`V{顺序号}__{snake_case_描述}.sql`，顺序号单调递增、一经提交永不修改
  - M0：`V1__baseline.sql`（仅注释占位，验证迁移链路）
  - M1 起：`V2__create_user_family.sql` 等真实建表脚本
- 应用启动自动执行迁移；`baseline-on-migrate=true` 预留存量库接入能力
- **禁止**：修改已发布脚本；回滚通过新脚本（`Vn__rollback_xxx.sql`）前向修复

### 8.3 MyBatis 配置基线
- `mybatis-spring-boot-starter`（版本以 spike 为准）
- Mapper XML 位置：`classpath*:mapper/**/*.xml`（与 Java Mapper 接口同名同路径结构）
- 开启下划线转驼峰：`map-underscore-to-camel-case: true`
- 安全红线固化：**一律 `#{}` 参数化**；`${}` 使用需在代码评审中专项说明（tech-stack 安全约束）
- M0 用一个冒烟 Mapper（如 `SELECT 1`）验证集成

### 8.4 HikariCP 参数基线（面向 10 TPS 场景，宁小勿大）

| 参数 | 值 | 说明 |
|---|---|---|
| maximumPoolSize | 10 | 10 TPS 场景足够，预留突发余量 |
| minimumIdle | 2 | 低峰期收缩 |
| connectionTimeout | 5000 ms | 与 P95 ≤ 500ms 目标匹配，快速失败 |
| idleTimeout | 300000 ms | — |
| maxLifetime | 1800000 ms | 小于常见 LB/防火墙空闲断开阈值 |
| leakDetectionThreshold | 30000 ms | 仅 local/dev/test 开启 |

---

## 9. 日志设计

### 9.1 框架与格式
- Logback + `logstash-logback-encoder`，**JSON 结构化输出**（为 M7 SLS 接入打底）
- 输出目标：控制台（local）+ 滚动文件（按天 + 100MB 切片，保留 14 天）；prod 仅文件 + stdout（由容器采集）
- JSON 字段：`@timestamp`、`level`、`logger`、`thread`、`traceId`（MDC）、`message`、`stack_trace`
- 级别策略：root=INFO；`wyq.pocket.money`=DEBUG（local）/ INFO（其他）；MyBatis SQL 日志仅 local 开启

### 9.2 敏感信息脱敏（mission 安全约束）
- 实现 `MaskingJsonEncoder`：对 message 与 MDC 值按正则脱敏后输出
- 脱敏规则基线：

| 数据类型 | 规则 | 示例 |
|---|---|---|
| 手机号 | 保留前 3 后 4 | `138****1234` |
| 身份证号 | 保留前 3 后 4 | `110***********1234` |
| 银行卡号 | 保留后 4 | `************5678` |
| 密码/密钥/Token | 整体替换 | `******` |

- 脱敏规则以常量/配置集中维护；M6 安全专项中做脱敏覆盖率验证
- 审计日志（M1 落库）与运行日志分离：审计走数据库，运行走日志系统

---

## 10. 可观测性基础

### 10.1 Actuator
- 暴露端点：`health`、`info`、`metrics`（最小化暴露，其余关闭）
- 开启存活/就绪探针：`management.endpoint.health.probes.enabled=true`（`/actuator/health/liveness`、`/actuator/health/readiness`），供 M7 容器健康检查与 SLB 探测
- health 详情仅当认证请求可见（`show-details: when-authorized`，M1 接入认证后生效；M0 期间仅 local 开放 details）

### 10.2 SpringDoc API 文档（D4）
- `springdoc-openapi-starter-webmvc-ui`（版本以 spike 为准）
- 端点：`/v3/api-docs`（JSON，供鸿蒙端工具导入）、Swagger UI（仅 local/dev/test 开启）
- **prod profile 关闭 Swagger UI**（`springdoc.swagger-ui.enabled=false`）
- OpenAPI 元信息：标题、版本、描述统一在 `OpenApiConfig` 中维护
- API 文档总则（写入文档描述）：统一 `Result` 包裹、code=0 为成功、90xxxx 可重试、traceId 排障指引

---

## 11. 质量工具链设计

### 11.1 工具矩阵（均绑定 `verify` 阶段，`mvn clean verify` 一键执行）

| 工具 | 职责 | 失败策略 |
|---|---|---|
| Checkstyle | 代码格式（对齐 code-style-guide：4 空格缩进、120 列、K&R 大括号、禁通配符 import、公共类/方法 Javadoc） | 违规即失败构建 |
| PMD | 代码缺陷 + **圈复杂度 ≤ 5**（tech-stack 约束，`CyclomaticComplexity` 阈值 5） | 违规即失败构建 |
| SpotBugs | 字节码缺陷扫描（effort=default，threshold=medium） | 违规即失败构建 |
| JaCoCo | 行覆盖率统计与门禁 | M0 配置 80% 门禁规则，但排除启动类/config 类；骨架代码量小，须实际达标 |
| Maven Enforcer | 锁定 JDK 25、Maven ≥ 3.9.16、依赖收敛 | 不满足即失败 |

### 11.2 规则文件
- `config/checkstyle/checkstyle.xml`：逐条映射 code-style-guide §1–§4 的可机器检查项；Javadoc 缺失首月设为 warning，M1 起转 error（避免骨架期噪音）
- `config/pmd/pmd-ruleset.xml`：选取 `bestpractices`、`errorprone`、`design`（含复杂度）子集；**显式包含空 catch 块规则**（对应 code-style-guide §8 禁止项）
- 规则文件变更必须走 PR 评审，禁止为消除违规而放宽规则

### 11.3 SonarQube 预留（D8）
- M0 仅在 pom 中预留 `sonar-maven-plugin` 与 `sonar.*` 属性占位（不接服务器）
- M6 前完成接入（自建/SonarCloud 届时决策），门禁基线：无阻断/严重问题、覆盖率 ≥ 80%、重复率 ≤ 3%

---

## 12. CI 流水线设计（阿里云云效，D3）

### 12.1 流水线阶段

```
代码检出 → 构建&单测（mvn clean verify）→ 云效代码检测（并行）→ 制品归档
                │ 含 Checkstyle/PMD/SpotBugs/JaCoCo 门禁
```

| 阶段 | 内容 | 通过条件 |
|---|---|---|
| 检出 | Codeup/镜像仓库拉取 | — |
| 构建&单测 | JDK 25 环境执行 `mvn clean verify` | 全部插件门禁通过、单测 100% 通过 |
| 云效代码检测 | 平台自带安全/规范扫描 | 无阻断级问题 |
| 制品归档 | jar 包归档（M7 前不构建镜像） | 归档成功 |

### 12.2 触发策略
- `master`/`main` push：全量执行
- 合并请求（MR）：全量执行，**门禁不通过禁止合入**（mission：代码审查 + 质量门禁强制）
- Maven 依赖缓存启用，控制构建时长

### 12.3 构建环境风险（S9 对应）
- 云效公共构建集群若不提供 JDK 25 镜像：构建自定义构建镜像（JDK 25 + Maven 3.9.16）托管于 ACR —— 该项在 spike 期间确认

---

## 13. Git 工作流

| 项 | 约定 |
|---|---|
| 主干 | `main`（保护分支，禁止直推） |
| 功能分支 | `feature/<issue号>-<简述>`，如 `feature/12-flyway-setup` |
| 缺陷分支 | `bugfix/<issue号>-<简述>`；线上紧急 `hotfix/<issue号>-<简述>` |
| 提交规范 | 约定式提交 `<type>(<scope>): <subject>`（feat/fix/build/ci/docs/test/refactor…） |
| 合并方式 | MR + ≥1 人评审通过 + CI 全绿后 Squash 合入 |
| 进度跟踪 | Issue 跟踪，MR 关联 Issue（mission 团队协作约束） |
| PR 模板 | 变更说明 / 测试情况 / 自查清单（规范、安全、文档三项） |

---

## 14. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 |
|---|---|---|---|
| T1 | Spike：版本兼容性验证 + `version-matrix.md` | — | 2.5 人天 |
| T2 | pom 改造：Boot 4.1.0 parent、依赖基线、Enforcer | T1 | 1 人天 |
| T3 | 包骨架 + common 公共层（Result/错误码/异常/校验/TraceId） | T2 | 2 人天 |
| T4 | 配置分层（profiles + 环境变量 + 敏感词 CI 扫描） | T2 | 0.5 人天 |
| T5 | Docker Compose + Flyway + MyBatis + HikariCP 集成冒烟 | T2 | 1 人天 |
| T6 | 日志体系（JSON 结构化 + 脱敏编码器） | T2 | 1 人天 |
| T7 | Actuator + SpringDoc | T3 | 0.5 人天 |
| T8 | 质量工具链（Checkstyle/PMD/SpotBugs/JaCoCo 规则与门禁） | T2 | 1.5 人天 |
| T9 | 云效流水线搭建（含构建镜像确认） | T8 | 1 人天 |
| T10 | Git 规范 + PR 模板 + README 快速开始 + ArchUnit 分层守护 | T3 | 1 人天 |

合计约 **12 人天**，1 人投入 ≈ 2 周（08-17 ~ 08-30），与 roadmap 排期一致；关键路径：T1 → T2 → T8 → T9。

---

## 15. 验收标准（DoD，与 roadmap 一致并细化）

- [ ] `mvn clean verify` 一键通过；空壳服务可启动，`/actuator/health/liveness` 返回 UP
- [ ] 任一违规代码（格式/复杂度/空 catch）提交被本地构建与云效流水线双重拦截（实测验证）
- [ ] JaCoCo 覆盖率报告生成且门禁生效
- [ ] `docs/version-matrix.md` 产出并评审通过（spike 结论）
- [ ] 包结构树 + 命名规范 + 错误码表 + API 总则文档化（README / 本文档引用）
- [ ] 全仓库敏感信息扫描零命中（代码 + 配置文件 + 提交历史抽检）
- [ ] 本地一条命令（docker compose up + mvn spring-boot:run）可跑通"启动 → 健康检查 → Swagger UI"全流程

---

## 16. 遗留行动项（跨文档同步）

| # | 行动项 | 责任阶段 |
|---|---|---|
| A1 | 更新 `code-style-guide.md` 包名示例为模块优先分层（与 D1 对齐）；确认 Lombok 兼容性结论（S8）后同步 §5 | M0 收尾 |
| A2 | roadmap 前置决策清单 #1（Flyway）、#6（云效）标记为已决策 | M0 启动时 |
| A3 | 云效组织/项目、Codeup 仓库、ACR 命名空间开通（依赖云账号权限） | T9 前 |
| A4 | 若 spike 发现 Boot 4.1.0 关键依赖不兼容，发起 tech-stack 变更评审 | T1 |

---

## 附录 A：M0 依赖基线清单（版本以 spike 锁定为准）

| 依赖 | 目标版本 | 用途 |
|---|---|---|
| JDK | 25 | 运行时（虚拟线程） |
| Maven | ≥ 3.9.16 | 构建 |
| Spring Boot Parent | 4.1.0 | 应用框架 |
| spring-boot-starter-web | 随 Boot | REST API |
| spring-boot-starter-validation | 随 Boot | 输入校验 |
| spring-boot-starter-actuator | 随 Boot | 健康检查/指标 |
| mybatis-spring-boot-starter | spike 锁定 | ORM |
| flyway-core / flyway-database-postgresql | spike 锁定 | 数据库迁移 |
| postgresql（驱动） | 随 Boot 管理 | JDBC 驱动 |
| HikariCP | 随 Boot 管理 | 连接池 |
| commons-lang3 | 随 Boot 管理 | 工具库 |
| springdoc-openapi-starter-webmvc-ui | spike 锁定 | API 文档 |
| logstash-logback-encoder | spike 锁定 | JSON 日志 |
| lombok | spike 决定去留 | 样板代码精简 |
| junit5 / mockito / assertj | 随 Boot 管理 | 单测 |
| jacoco / checkstyle / pmd / spotbugs / enforcer 插件 | 最新稳定版 | 质量门禁 |
| archunit（可选） | 最新稳定版 | 分层守护 |

## 附录 B：配置项基线（application.yml 摘要）

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application: { name: pocket-money-server }
  threads: { virtual: { enabled: true } }        # JDK 25 虚拟线程
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/pocket_money}
    username: ${DB_USERNAME:pocket}
    password: ${DB_PASSWORD:}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 5000
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration: { map-underscore-to-camel-case: true }

management:
  endpoints: { web: { exposure: { include: health,info,metrics } } }
  endpoint: { health: { probes: { enabled: true }, show-details: when-authorized } }

springdoc:
  swagger-ui: { enabled: true }   # prod profile 中覆盖为 false
```

---
*本设计经评审通过后作为 M0 开发基线；实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*
