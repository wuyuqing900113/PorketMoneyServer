# 依赖版本矩阵（Spike 产出）

> 对应 M0-detailed-design.md §4（Spike 验证设计）。
> 状态说明：✅ 已验证 ｜ ⚠️ 验证通过但有保留事项
>
> **Spike 已完成**：2026-08-17 `mvn clean verify` 全绿（44 测试通过，
> Checkstyle/PMD/SpotBugs 0 违规，JaCoCo 行覆盖率 91.0% ≥ 80% 门禁）。
> 构建基线：JDK 25.0.3 + Maven 3.9.16（D:\soft\apache-maven-3.9.16）。

## 1. 核心运行时

| 组件 | 版本 | 状态 | 验证方式 |
|---|---|---|---|
| JDK | 25.0.3 LTS | ✅ | `java -version`；Enforcer 强制 [25,26) |
| Maven | 3.9.16 | ✅ | Enforcer 强制 [3.9.16,)；本机经 MAVEN_HOME 引用 |
| Spring Boot Parent | 4.1.0 | ✅ | 构建解析 + 上下文启动（Spring v7.0.8） |
| spring-boot-starter-webmvc | 随 Boot 4.1.0 | ✅ | Boot 4 中 web starter 确认更名为 **webmvc** |
| spring-boot-starter-validation / actuator / jdbc | 随 Boot 4.1.0 | ✅ | SmokeIntegrationTest 探针与校验用例 |

## 2. 三方依赖

| 依赖 | pom 属性 | 落地版本 | 状态 | 验证方式 / 备注 |
|---|---|---|---|---|
| mybatis-spring-boot-starter | `mybatis-spring-boot.version` | 4.0.0 | ✅ | SmokeIntegrationTest：`SystemHealthMapper.ping()==1` |
| flyway-core / flyway-database-postgresql | Boot BOM 管理 | 12.4.0 | ✅ | H2 PostgreSQL 兼容模式下完成迁移链路验证 |
| postgresql 驱动 | Boot BOM 管理 | — | ⚠️ | 依赖解析通过；真实 PG 运行验证待 Docker 就绪后执行（Testcontainers 于 M1 接入） |
| commons-lang3 | Boot BOM 管理 | 3.20.0 | ✅ | 解析通过 |
| springdoc-openapi-starter-webmvc-ui | `springdoc.version` | 3.0.0 | ✅ | SmokeIntegrationTest：`/v3/api-docs` 返回 200 |
| logstash-logback-encoder | `logstash-logback-encoder.version` | 9.0 | ✅ | MaskingJsonEncoderTest 编码输出验证 |
| archunit-junit5 | `archunit.version` | **1.5.0**（原 1.4.0） | ✅ | ArchitectureTest；1.4.0 的 ASM 不识别 class file 69，升级后通过 |
| h2（test） | Boot BOM 管理 | 2.x | ✅ | SmokeIntegrationTest 内存库 |

## 3. 质量工具链

| 工具 | pom 属性 | 落地版本 | 状态 | 备注 |
|---|---|---|---|---|
| maven-enforcer-plugin | `maven-enforcer-plugin.version` | 3.5.0 | ✅ | JDK/Maven 基线校验通过 |
| maven-checkstyle-plugin | `maven-checkstyle-plugin.version` | 3.6.0 | ✅ | 0 违规 |
| Checkstyle | `checkstyle.version` | 10.21.2 | ✅ | — |
| maven-pmd-plugin | `maven-pmd-plugin.version` | 3.26.0 | ✅ | **PMD 核心经插件依赖覆盖升级**（见 D5） |
| PMD 核心 | `pmd.version` | **7.26.0** | ✅ | 规则集按 PMD 7 编写；7.7.0 无法读取 JDK 25 运行时类 |
| spotbugs-maven-plugin | `spotbugs-maven-plugin.version` | **4.10.3.0**（原 4.9.3.0） | ✅ | 4.9.3.0 的 ASM 不识别 class file 69，升级后通过；定点豁免见 `config/spotbugs/exclude.xml` |
| JaCoCo | `jacoco.version` | 0.8.14 | ✅ | JDK 25 插桩正常；行覆盖率 91.0%，≥ 80% 门禁 |

## 4. 已决策记录（Spike 结论）

| # | 决策 | 结论 | 依据 |
|---|---|---|---|
| D1 | Lombok 是否引入 | **不引入**，统一使用 record / 手写样板 | 设计 §4 S8：JDK 25 兼容性不确定，M0 骨架代码量小，规避风险 |
| D2 | 模块间防腐 | 引入 ArchUnit 测试固化分层规则 | 设计 §5.3 可选项落地 |
| D3 | 测试数据库 | H2 内存库 PostgreSQL 兼容模式（test scope） | tech-stack 测试栈约定；Testcontainers 延至 M1 |
| D4 | Flyway H2 支持 | **flyway-database-h2 工件不存在**（Maven Central 确认），H2 支持内置于 flyway-core 12.4.0 | 构建实测 + 中央仓库元数据查询；SmokeIntegrationTest 迁移链路通过 |
| D5 | PMD 核心版本 | maven-pmd-plugin 3.26.0 默认捆绑 PMD 7.7.0，无法读取 JDK 25 运行时类（major 69），经插件 `<dependencies>` 覆盖 pmd-core/pmd-java 至 7.26.0 | 构建日志中 ParseLock 解析错误；升级后消失 |
| D6 | ArchUnit 空包规则 | M0 业务模块仅 package-info 骨架（无类），规则启用 `allowEmptyShould(true)`；M1+ 有真实类后自动生效 | ArchUnit 默认对零匹配规则报错 |
| D7 | SpotBugs HRS 误报处置 | TraceIdFilter 的外部 traceId 已加白名单校验（`TraceIds.isAcceptable`，`[A-Za-z0-9_-]{1,64}`），CRLF 注入不可能；SpotBugs 数据流无法识别该校验，按评审结论定点豁免（`config/spotbugs/exclude.xml`），并由测试 `shouldRejectCrlfInjectionAttempt` 固化防护 | SpotBugs 4.10.3.0 实测仍报 HRS_REQUEST_PARAMETER_TO_HTTP_HEADER |
| D8 | 日志器常量命名 | `LOG`（全大写），Logger 不做小写例外 | code-style-guide.md §常量 UPPER_SNAKE_CASE；Checkstyle ConstantName 强制 |

## 5. 构建迭代处置记录（2026-08-17，共 11 轮）

| # | 现象 | 根因 | 处置 |
|---|---|---|---|
| 1 | `flyway-database-h2` version missing / 404 | 该工件从未发布 | 移除依赖，H2 支持由 flyway-core 提供（D4） |
| 2 | 测试编译失败：`AutoConfigureMockMvc` 包不存在 | Boot 4 测试切片包重构 | SmokeIntegrationTest 改用 `MockMvcBuilders.webAppContextSetup` |
| 3 | `NoResourceFoundException` 构造器不匹配 | Boot 4 改为三参构造 | 改用 `(HttpMethod, String, String)` |
| 4 | ArchUnit 3 条规则 "failed to check any classes" | ArchUnit 1.4.0 不识别 class file 69，导入全部跳过 | 升级 1.5.0 + `allowEmptyShould(true)`（D6） |
| 5 | MaskingJsonEncoderTest NPE（MDCAdapter null） | 单测未初始化 SLF4J，LoggingEvent 惰性取 MDC | 测试改用 `event.setMDCPropertyMap(...)` 注入 MDC |
| 6 | Checkstyle ConstantName 违规 | 日志器常量用了小写 `log` | 更名 `LOG`（D8） |
| 7 | PMD 规则集加载失败 | PMD 7 更名：`DoNotCallSystemExit`→`DoNotTerminateVM`；`reportLevel`→`methodReportLevel` | 更新规则集 |
| 8 | SpotBugs 全量解析失败（major 69） | 插件 4.9.3.0 的 ASM 过旧 | 升级 4.10.3.0 |
| 9 | SpotBugs 报 TraceIdFilter HRS | 外部 traceId 未校验直写响应头（真实安全问题） | 增加白名单校验 `TraceIds.isAcceptable` + CRLF 注入测试；残余误报定点豁免（D7） |
| 10 | PMD 日志大量 ParseLock 错误 | 捆绑 PMD 7.7.0 读不了 JDK 25 运行时类 | 覆盖升级 PMD 至 7.26.0（D5） |

## 6. Spike 执行清单（已完成）

1. ✅ `mvn -v`：Maven 3.9.16（MAVEN_HOME=D:\soft\apache-maven-3.9.16，未入 PATH，构建时按路径引用）
2. ✅ `mvn clean verify` 一键执行：依赖解析 → 编译（51 主源/8 测试源）→ 44 测试 → Checkstyle/PMD/SpotBugs/JaCoCo 全绿
3. ✅ 本文件已按实测更新；所有版本变更均记录于 §5
4. ✅ Spike 完成，可提交并标注 "Spike 完成"
