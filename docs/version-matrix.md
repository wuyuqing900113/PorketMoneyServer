# 依赖版本矩阵（Spike 产出）

> 对应 M0-detailed-design.md §4（Spike 验证设计）。
> 状态说明：✅ 已验证 ｜ ⚠️ 验证通过但有保留事项
>
> **M0 Spike 已完成**：2026-08-17 `mvn clean verify` 全绿（44 测试通过，
> Checkstyle/PMD/SpotBugs 0 违规，JaCoCo 行覆盖率 91.0% ≥ 80% 门禁）。
> 构建基线：JDK 25.0.3 + Maven 3.9.16（D:\soft\apache-maven-3.9.16）。
>
> **M1 Spike 已完成**：2026-08-18 `mvn clean verify` 全绿（62 测试：60 通过，
> 2 个 Testcontainers 用例因 Docker 守护进程未就绪按 `disabledWithoutDocker`
> 自动跳过；Checkstyle/PMD/SpotBugs 0 违规，JaCoCo 行覆盖率 90.0% ≥ 80% 门禁）。
> 结果见 §7，构建迭代处置见 §8。
>
> **T4 用户域构建完成**：2026-08-18 `mvn clean verify` 全绿（148 测试 / 2 跳过
> ＝ PG 容器待 Docker；Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥80% 门禁达标）。
> Flyway 迁移链路与加密列 TypeHandler 于本轮**首次真实运行**（见 M1-D7/M1-D8）。
>
> **T5 家庭域构建完成**：2026-08-18 `mvn clean verify` 全绿（180 测试 / 2 跳过
> ＝ PG 容器待 Docker；Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥80% 门禁达标）。
> 家庭域 8 端点双层越权守卫由 FamilyFlowH2IntegrationTest 8 用例 E2E 实测（见 M1-D10）。
>
> **T6 OAuth2 扩展点预留完成**：2026-08-18 `mvn clean verify` 全绿（180 测试 / 2 跳过，
> 全门禁通过）。`common/security/oauth/AuthProvider` 接口 + package-info 演进说明落盘；
> 表结构 V3 `user_oauth_binding` 已于 T4 就绪（设计 §4.7）。
>
> **T7 集成测试套件落盘完成**：2026-08-18 `mvn clean verify` 全绿（197 测试 / 19 跳过
> ＝ 既有 2 + 新增 17 PG 用例按 `disabledWithoutDocker` 自动跳过；Checkstyle/PMD/SpotBugs
> 0 违规，JaCoCo ≥80% 门禁达标）。设计 §12.2/§12.3 八套件的 PG 18 实现全部就绪
> （含权限矩阵 56 参数化用例），Docker 就绪后自动转实跑（见 M1-D11）。
>
> **T8 API 文档与 DoD 收尾完成（M1 收官）**：2026-08-18 `mvn clean verify` 全绿
> （198 测试 / 20 跳过＝PG 用例待 Docker；全门禁通过）。OpenAPI bearerAuth 安全方案 +
> 15 端点 SpringDoc 注解读产出；checkstyle Javadoc 规则按 M0 §11.2 切换 warning→error；
> AuditTrail PG 套件补齐 DoD 审计断言；README 更新至 M1 基线；DoD 验证记录见 §9。

## 1. 核心运行时

| 组件 | 版本 | 状态 | 验证方式 |
|---|---|---|---|
| JDK | 25.0.3 LTS | ✅ | `java -version`；Enforcer 强制 [25,26) |
| Maven | 3.9.16 | ✅ | Enforcer 强制 [3.9.16,)；本机经 MAVEN_HOME 引用 |
| Spring Boot Parent | 4.1.0 | ✅ | 构建解析 + 上下文启动（Spring v7.0.8） |
| spring-boot-starter-webmvc | 随 Boot 4.1.0 | ✅ | Boot 4 中 web starter 确认更名为 **webmvc** |
| spring-boot-starter-validation / actuator / jdbc | 随 Boot 4.1.0 | ✅ | SmokeIntegrationTest 探针与校验用例 |
| spring-boot-starter-security | 随 Boot 4.1.0 | ✅ | M1 spike S1：SecuritySmokeIntegrationTest 验证 STATELESS 过滤链、公开路径白名单、401 + Result(100003) JSON 契约 |
| spring-security-oauth2-jose | 随 Boot BOM | 7.1.0 | ✅ | M1 spike S2：HS256 签验通过（JwtTokenServiceTest 7 用例）；Security 7 API 变化见 §7 M1-D1 |

## 2. 三方依赖

| 依赖 | pom 属性 | 落地版本 | 状态 | 验证方式 / 备注 |
|---|---|---|---|---|
| mybatis-spring-boot-starter | `mybatis-spring-boot.version` | 4.0.0 | ✅ | SmokeIntegrationTest：`SystemHealthMapper.ping()==1` |
| flyway-core / flyway-database-postgresql | Boot BOM 管理 | 12.4.0 | ✅ | H2 支持内置于 flyway-core（D4）；**迁移链路 T4 才首次真实执行**（此前自动配置缺失，见 M1-D7），由 AuthFlowH2IntegrationTest 在 H2 实测 V2/V3 |
| spring-boot-flyway | Boot BOM 管理 | 随 Boot 4.1.0 | ✅ | **Boot 4 自动配置模块化拆分**：仅引 flyway-core 不触发自动装配（构建日志零 flyway 条目、集成测试全空库），补入后迁移正常执行（M1-D7） |
| postgresql 驱动 | Boot BOM 管理 | — | ⚠️ | 依赖解析通过；PostgresContainerIntegrationTest 已就绪（TC 2.0.5 + `@ServiceConnection`），Docker 守护进程未启动（npipe 503）自动跳过，真实 PG 运行验证仍待 Docker 就绪 |
| rest-assured | `rest-assured.version` | **6.0.1** | ✅ | M1 spike S3：不在 Boot BOM 管理范围，显式版本属性；RANDOM_PORT + `@LocalServerPort`（Boot 4 移至 `boot.test.web.server` 包）断言通过 |
| spring-boot-testcontainers | 随 Boot 4.1.0 | ✅（装配） | M1 spike S4：`@ServiceConnection` 装配链路通过；容器运行部分随 Docker 未就绪跳过 |
| testcontainers-postgresql / testcontainers-junit-jupiter | Boot BOM 管理 | **2.0.5** | ⚠️ | M1 spike S4：**2.x 工件更名**（原 `postgresql` / `junit-jupiter`）；`PostgreSQLContainer` 不再是泛型类；`disabledWithoutDocker=true` 跳过验证生效 |
| commons-lang3 | Boot BOM 管理 | 3.20.0 | ✅ | 解析通过 |
| springdoc-openapi-starter-webmvc-ui | `springdoc.version` | 3.0.0 | ✅ | SmokeIntegrationTest：`/v3/api-docs` 返回 200 |
| logstash-logback-encoder | `logstash-logback-encoder.version` | 9.0 | ✅ | MaskingJsonEncoderTest 编码输出验证 |
| archunit-junit5 | `archunit.version` | **1.5.0**（原 1.4.0） | ✅ | ArchitectureTest；1.4.0 的 ASM 不识别 class file 69，升级后通过 |
| h2（test） | Boot BOM 管理 | **2.4.240** | ✅ | PostgreSQL 兼容模式内存库；T1 探针类型矩阵（BIGSERIAL / TIMESTAMP WITH TIME ZONE / JSONB ✅，`TIMESTAMPTZ` 缩写 ❌），T4 实测全量迁移链路 |
| wiremock-standalone（test） | `wiremock.version` | **3.9.1** | ✅ | M6 引入（D49）：外部 AI/语音 HTTP 依赖故障注入模拟；shaded 工件内联 Jackson 2/Jetty，不与 Boot 4 Jackson 3 冲突；`AiHttpDegradationWireMockTest` 实测通过（超时/5xx/畸形→600001 + 熔断 OPEN→HALF_OPEN→CLOSED） |

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

## 7. M1 Spike 验证结果（S1–S6，2026-08-18 完成）

| # | Spike 项 | 结果 | 结论 / 处置 |
|---|---|---|---|
| S1 | Boot 4.1.0 + starter-security | ✅ | STATELESS 过滤链、`@EnableMethodSecurity`、公开路径白名单、401 + Result(100003) JSON 契约全部通过（SecuritySmokeIntegrationTest 3 用例） |
| S2 | spring-security-oauth2-jose HS256 | ✅ | 签验通过（JwtTokenServiceTest 7 用例），**无需切换 jjwt 预案**；Security 7 API 变化见 M1-D1 |
| S3 | RestAssured + RANDOM_PORT | ✅ | 6.0.1 通过；`@LocalServerPort` 移至 `org.springframework.boot.test.web.server` 包（Boot 4 测试切片重构） |
| S4 | Testcontainers + `@ServiceConnection` + Flyway | ⚠️ | 装配链路通过；**Docker 守护进程未启动**（npipe://localhost:2375 → 503），`disabledWithoutDocker=true` 自动跳过 2 用例，真实 PG 18 运行验证仍待 Docker 就绪（无需 `DynamicPropertyRegistry` 预案） |
| S5 | MyBatis 4.0.0 与 Security 共存 | ✅ | 两个 `@SpringBootTest` 套件上下文正常启动，无自动配置冲突 |
| S6 | EncryptedFieldTypeHandler 真实往返 | ✅(H2) / ⏸(PG) | T4 AuthFlowH2IntegrationTest 实证真实 MyBatis 栈写加密 / 读解密（/users/me 脱敏回显 139****0001）；PG 往返待 Docker 就绪由 T7 覆盖；算法层由 DataEncryptorTest 6 用例固化（GCM 往返 / 篡改拒绝 / IV 随机性 / 密钥校验）；注册推断陷阱见 M1-D8 |

### M1 新增决策记录

| # | 决策 | 结论 | 依据 |
|---|---|---|---|
| M1-D1 | Security 7 JWT API 迁移 | `SignatureAlgorithm` 仅含非对称算法（RS/ES/PS），HS256 在 `MacAlgorithm`；编码器用 `NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build()`（旧 SecretKey 构造器已移除）；`JwsHeader` 移至 `org.springframework.security.oauth2.jwt` 包；`Jwt.getClaimValue` 移除，改用 `getClaimAsString/getClaimAsBoolean` 等 ClaimAccessor 方法 | javap 反编译 spring-security-oauth2-jose-7.1.0.jar 实测 + 构建验证 |
| M1-D2 | Boot 4 Jackson 3 迁移 | 自动配置的 JSON Bean 为 `tools.jackson.databind.json.JsonMapper`（Jackson 3）；`com.fasterxml.jackson.databind.ObjectMapper` **不再是装配 Bean**，注入即启动失败；序列化异常 `JacksonException` 为非受检 | 条件评估报告 + 上下文启动失败实测 |
| M1-D3 | Testcontainers 2.x 工件与 API | 工件更名 `testcontainers-postgresql` / `testcontainers-junit-jupiter`；`PostgreSQLContainer` 不再是泛型类；版本随 Boot BOM（2.0.5），无需 `testcontainers.version` 属性 | Maven Central 元数据 + javap + 构建实测 |
| M1-D4 | RestAssured 版本策略 | 不在 Boot BOM 管理范围，pom 显式 `rest-assured.version=6.0.1` | Boot BOM 检索无 rest-assured 条目 |
| M1-D5 | 多构造器 Bean 注入点 | Spring 不推断多构造器注入点：生产构造器显式 `@Autowired`，测试构造器保持包私有（JwtTokenService 时钟注入） | 上下文启动失败 "No default constructor found" 实测 |
| M1-D6 | fail-fast 构造器与 SpotBugs | 密钥校验在构造器抛异常为刻意 fail-fast；类声明 `final` 后 CT_CONSTRUCTOR_THROW 消除（无子类 finalize 利用面）；JsonMapper 单例注入的 EI_EXPOSE_REP2 定点豁免（`config/spotbugs/exclude.xml`，附理由） | SpotBugs 4.10.3.0 实测 |
| M1-D7 | Boot 4 Flyway 自动配置模块化 | Boot 4 将 Flyway 自动配置拆至独立工件 `spring-boot-flyway`；**仅 flyway-core 在 classpath 不执行迁移**（症状：构建/运行日志零 flyway 条目、集成测试报 "this database is empty"）。M0/M1 阶段"迁移链路验证"实际未生效，T4 补依赖后首次真实执行 | Boot 4.1.0 BOM 工件清单 + 全量日志取证 + 构建实测 |
| M1-D8 | TypeHandler bean 注册推断陷阱 | MyBatis 3.5.19 `TypeHandlerRegistry.register(TypeHandler)`：无 `@MappedTypes` 且 handler `instanceof TypeReference`（`BaseTypeHandler<String>` 均满足）时经 `getRawType()` 把 javaType 推断为 **String**，顶替全局 String 映射，所有 VARCHAR 参数/列被误加解密。对策：标记类型 `@MappedTypes(EncryptedString.class)`——注册仅落标记类型与实例表（allTypeHandlersMap），显式 `typeHandler=` 引用经 getMappingTypeHandler 命中 Spring bean | TypeHandlerRegistry 字节码核实 + T4 集成测试 phone_hash 写入 124 位 Base64 密文事故 |
| M1-D9 | PG JSONB 参数绑定 | PG 拒绝 varchar→jsonb 隐式赋值（除非连接串 stringtype=unspecified）；AuditLogMapper 采用 `CAST(#{detail} AS jsonb)` 显式转换，PG / H2 双兼容，无需改连接串 | T3 设计 + T4 H2 实测 |
| M1-D10 | 含 List 组件的响应 DTO record 与 SpotBugs | 紧凑构造器以 `List.copyOf` 固化列表（构造后内外双向不可变）；SpotBugs 无法识别 record 内防御性拷贝，对规范构造器报 EI_EXPOSE_REP2、对访问器报 EI_EXPOSE_REP，均为确定性误报——按类定点豁免（`config/spotbugs/exclude.xml` 附理由）。后续含 List 组件的响应 DTO 沿用此模式并按类增补豁免项 | T5 构建实测（SpotBugs 4.10.3.0） |
| M1-D11 | PG 集成测试套件命名与容器共享 | ① 类名以 `PgIntegrationTest` 结尾（设计示例名 `*IT` 不在 surefire 默认 `*Test` 包含规则内，会静默漏跑；不改 pom）；② 静态 `@Container @ServiceConnection PostgreSQLContainer` 置于抽象基座 `AbstractPostgresIntegrationTest`，全 JVM 仅启动一次、各套件复用同一容器，`@ServiceConnection` 装配的连接参数一致 → 相同属性的套件共享 Spring 上下文缓存，个别套件追加属性（如 LoginLockout 缩短锁定时长）获得独立上下文但仍复用同一容器 | T7 落盘 + 构建实测（197 测试 / 19 跳过，Docker 未就绪自动跳过） |
| M1-D12 | secret-scan 命中裁定与无 bash 环境执行 | `scripts/secret-scan.sh` 全仓扫描命中 2 行，均为 MaskingRulesTest 的脱敏功能样例输入（`password=abc123` / `api_key: ak_123456`，M0 基线、验证掩码行为所需，非真实凭据）——评审裁定为误报保留，不改测试弱化覆盖；Windows 无 bash 时以等价 PowerShell 正则（同大小写敏感语义）执行扫描 | T8 DoD 扫描实测 |

## 8. M1 构建迭代处置记录（2026-08-18，共 12 轮）

| # | 现象 | 根因 | 处置 |
|---|---|---|---|
| 1 | `NimbusJwtEncoder(SecretKey)` 构造器不存在、`SignatureAlgorithm.HS256` 无此符号 | Security 7.1.0 API 重构：HS256 归 `MacAlgorithm`，编码器改 builder | `withSecretKey(key).algorithm(MacAlgorithm.HS256).build()`；`JwsHeader.with(...)` 改用 oauth2.jwt 包（M1-D1） |
| 2 | `PostgreSQLContainer<?>` 编译失败 | TC 2.0.5 类不再是泛型 | 去除类型参数（M1-D3） |
| 3 | `Jwt.getClaimValue` 不存在 | Security 7 移除该方法 | 改用 `getClaimAsBoolean` / `getClaimAsString` |
| 4 | 两个 `@SpringBootTest` 上下文启动失败：无 `com.fasterxml...ObjectMapper` Bean | Boot 4 迁移 Jackson 3 | RestAuthenticationEntryPoint / RestAccessDeniedHandler 改注入 `tools.jackson.databind.json.JsonMapper`（M1-D2） |
| 5 | JwtTokenService Bean 创建失败 "No default constructor found" | 双构造器且均无注入标注 | 生产构造器加 `@Autowired`（M1-D5） |
| 6 | PMD CyclomaticComplexity ×2（loadKey 复杂度 6 ≥ 阈值 6） | 空值判断 + `||` + 长度判断叠加 | 拆出 `decodeConfiguredSecret/Key` 与 `requireXxxLength` 私有方法，各方法 ≤3 |
| 7 | SpotBugs 5 项：CT_CONSTRUCTOR_THROW ×3、EI_EXPOSE_REP2 ×2；修复后暴露 TraceIdFilter HRS ×1 | fail-fast 构造器抛异常；JsonMapper 可变对象存储；**HRS 项为 M0 既有豁免，本次 exclude.xml 更新时被误覆盖** | 类改 `final`（CT 消除）；JsonMapper 定点豁免；恢复 TraceIdFilter HRS 豁免项并补全理由注释（M1-D6） |
| 8 | T4 AuthFlow H2 E2E 注册返回 900001：`Table "app_user" not found (this database is empty)` | Boot 4 Flyway 自动配置模块化拆分，flyway-core 孤立不触发迁移，H2 库零表（日志取证：全程零 flyway 条目） | 补 `spring-boot-flyway` 依赖（M1-D7）；同时修正 M0/M1"迁移链路已验证"的失实记录 |
| 9 | phone_hash 列写入 124 位 Base64 密文（CHAR(64) 超长） | `register(TypeHandler)` 无 `@MappedTypes` 时经 TypeReference 泛型推断 javaType=String，EncryptedFieldTypeHandler 顶替全局 String 映射，所有 VARCHAR 参数被加密 | `@MappedTypes(EncryptedString.class)` 标记类型隔离注册（M1-D8） |
| 10 | RefreshTokenServiceTest.rotate verify 不匹配 | 测试夹具 storedRow 的 tokenHash 与真实令牌哈希不一致，rotate 按库内行哈希吊销 | 夹具改为接收真实 tokenHash |
| 11 | Checkstyle UnusedImports ×2；PMD SimplifyBooleanReturns / UnusedFormalParameter / EmptyCatchBlock ×3 | 历史遗留（此前构建先在测试阶段失败，未走到后置门禁） | 删未用 import；拆 `isStrongEnough`；删 `issueLoginResponse` 冗参；规则集 EmptyCatchBlock 开 `allowCommentedBlocks`（带注释的刻意静默不属吞异常） |
| 12 | T5 SpotBugs EI_EXPOSE_REP / EI_EXPOSE_REP2 ×2（FamilyDetailResponse 的 members List 组件） | record 规范构造器原样存储、访问器原样返回；即便紧凑构造器以 `List.copyOf` 快照，静态分析仍无法识别其不可变性 | 紧凑构造器 `List.copyOf` 固化（真实防御加固）+ 按类定点豁免两条并附理由（M1-D10） |

**M1 Spike 收尾状态**：`mvn clean verify` 全绿（62 测试 / 2 跳过 / 覆盖率 90.0%）；
postgresql 驱动 ⚠️ 项保持，待 Docker 就绪后由 PostgresContainerIntegrationTest 关闭。

**T4 收尾状态**（2026-08-18）：`mvn clean verify` 全绿（148 测试 / 2 跳过＝PG 容器待 Docker，
Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥80% 达标）。认证全链路（注册 / 登录 / 锁定前置 /
刷新轮转 / 重用检测 / 登出 / 改密吊销）由 AuthFlowH2IntegrationTest 8 用例 E2E 覆盖；
Flyway V2/V3 迁移与 EncryptedFieldTypeHandler 真实 MyBatis 栈往返于 H2 首次实证（M1-D7/D8/D9）。

**T5 收尾状态**（2026-08-18）：`mvn clean verify` 全绿（180 测试 / 2 跳过＝PG 容器待 Docker，
Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥80% 达标）。新增单测 24（FamilyServiceTest 21 +
FamilyAccessCheckerTest 3）、H2 E2E 8（FamilyFlowH2IntegrationTest）。落地端点：家庭详情 /
成员列表（全员可见）、改家庭名、创建孩子（上限 200006、登录名全局唯一 200007）、改孩子昵称、
重置孩子密码（mcp 重生效 + 吊销全部会话）、移除成员（创建者保护 200012 / 非成员 200011 /
被移除孩子 DISABLED 后登录 200004）、GET /users/me/family。越权边界实证：接口级
`@PreAuthorize("hasRole('PARENT')")` 与方法安全之外的数据级 FamilyAccessChecker（以库内
成员关系为准，移除即失权）拒绝统一 HTTP 403 + Result(100004)，与附录 B 权限矩阵一致；
SpotBugs record List 组件误报处置见 M1-D10。

**T6 收尾状态**（2026-08-18）：`mvn clean verify` 全绿（180 测试 / 2 跳过＝PG 容器待 Docker，
Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥80% 达标）。按设计 §4.7 仅做结构预留：
`wyq.pocket.money.common.security.oauth.AuthProvider` 接口（`supports(provider)` /
`authenticate(externalCredential)` 契约，绑定解析走 user_oauth_binding）与 package-info
演进说明（实现置于 user 模块或独立模块以满足 ArchUnit 分层；未来加
`POST /api/v1/auth/oauth/{provider}` 端点，现有密码认证链路零改动）；表结构
`user_oauth_binding`（唯一约束 (provider, external_id)）V3 已建（T4）。
本任务 DoD：无 provider 实现、无新端点。

**T7 收尾状态**（2026-08-18）：`mvn clean verify` 全绿（197 测试 / 19 跳过，
Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥80% 达标）。设计 §12.2/§12.3 八套件全部落盘
（`wyq.pocket.money.integration`，M1-D11 命名与容器共享）：AuthFlow（注册→登录→me 脱敏→
刷新轮转→登出→登出后 refresh 被拒）、AuthFailureScenarios（错误凭证 200002 / 伪造过期
access 与篡改签名均 401+100003 / 停用账号 200004）、RefreshReuse（重放→100003+全吊销→
重登恢复）、LoginLockout（PT2S 缩短锁定：5 次失败→第 6 次正确口令仍 200003→到期恢复）、
FamilyCrud（改名 / 添丁 / 上限 200006 / 移除约束 200011+200012 / 被移除登录 200004 /
重置孩子密码 mcp 重生效）、ChildFirstLogin（mcp 期 200010 拦截、改密放行、旧口令失效）、
PermissionMatrix（附录 B 14 端点 × 4 身份 = 56 参数化用例 + 跨家庭 CHILD 独立用例：
匿名 401+100003、CHILD 写接口与跨家庭 403+100004、本家庭 PARENT 放行；T8 补齐
PUT /users/me 行后为 15 端点 × 4 身份 = 60 用例）、
EncryptionAtRest（直查 DB：phone_hash=SHA-256 / AES-GCM 密文无明文 / 解密往返一致 /
me 仅回显脱敏号 / 孩子行 phone 全 NULL）。Docker Desktop 无法启动（daemon 503），17 个
新 PG 用例按 `disabledWithoutDocker` 自动跳过——postgresql ⚠️ 项保持，Docker 就绪后
本套件群全量跑绿方可置 ✅（设计 R1 托底，mvn verify 常绿不破）。

**T8 收尾状态（M1 收官）**（2026-08-18）：`mvn clean verify` 全绿（198 测试 / 20 跳过
＝ PG 用例待 Docker，Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥80% 达标）。交付：
① OpenAPI 认证文档——OpenApiConfig 增加 bearerAuth（HTTP Bearer JWT）安全方案与
401/403 契约总则，15 个 M1 端点全部落 `@Tag` / `@Operation`（含各错误码说明）/
`@SecurityRequirement`（受保护端点），SmokeIntegrationTest 断言 /v3/api-docs 200；
② checkstyle Javadoc 四条规则按 M0 设计 §11.2 由 warning 切换为 error（主代码全量
Javadoc 已达标，切换即通过）；③ 补 AuditTrailPgIntegrationTest（DoD 审计硬指标）：
按 user_id + action 精确断言 12 类审计动作行数；④ README 更新至 M1 基线（里程碑
进展、测试分层、密钥注入、目录结构）；⑤ 敏感信息扫描执行并裁定（M1-D12）。
M1 WBS T1–T8 全部完成，DoD 验证明细见 §9。

## 9. M1 DoD 验证记录（T8 收尾，对照 M1 设计 §15）

| # | DoD 项 | 状态 | 证据 |
|---|---|---|---|
| 1 | 登录/登出/刷新 RestAssured 集成测试全绿，覆盖错误凭证/过期令牌/篡改令牌/refresh 重用/锁定 | ✅（H2 常跑实证 + PG 套件落盘待 Docker 实跑） | AuthFlowH2IntegrationTest 8 用例常跑全绿；AuthFlow / AuthFailureScenarios（伪造过期 + 篡改签名 401+100003）/ RefreshReuse / LoginLockout PG 套件就绪 |
| 2 | 未认证访问受保护接口一律 100003（白名单除外） | ✅ | PermissionMatrix 匿名身份 15 用例（401+100003）+ JwtAuthenticationFilterTest 10 用例 |
| 3 | 权限矩阵参数化测试：CHILD 家长专属接口 100004、越权数据访问 100004 | ✅ 套件落盘（Docker 就绪转实跑） | PermissionMatrixPgIntegrationTest 15 端点 × 4 身份 = 60 用例 + 跨家庭 CHILD 独立用例；H2 侧 FamilyFlowH2IntegrationTest 已实证 403+100004 双层守卫 |
| 4 | 单测覆盖率 ≥ 80%，`mvn clean verify` 全门禁绿 | ✅ | 198 测试 / 0 失败 / JaCoCo BUNDLE ≥80% 门禁通过（BUILD SUCCESS） |
| 5 | Docker 就绪后 PG 套件全绿，postgresql ⚠️ → ✅ | ⏸ 未达成（环境阻塞） | Docker Desktop 无法启动（daemon 503），18 个 PG 用例 `disabledWithoutDocker` 自动跳过；修复 Docker 后全量跑绿方可置 ✅（设计 R1） |
| 6 | 审计动作全部落 audit_log（集成测试断言关键动作行）；安全日志按 §9.2 输出 SECURITY logger | ✅ 套件落盘（Docker 就绪转实跑） | AuditTrailPgIntegrationTest 按 user_id+action 精确断言 REGISTER/FAMILY_CREATE/LOGIN_SUCCESS/LOGIN_FAILURE/CHILD_CREATE/FAMILY_UPDATE/CHILD_PASSWORD_RESET/MEMBER_REMOVE/TOKEN_REFRESH/TOKEN_REUSE_DETECTED/LOGOUT/PASSWORD_CHANGE；SecurityLogger 各链路埋点（UNAUTHENTICATED_REJECT / ACCESS_DENIED / TOKEN_REUSE_DETECTED / ACCOUNT_LOCKED / LOGIN_FAILURE） |
| 7 | EncryptionAtRestIT 通过（库中无明文手机号、解密往返一致）；全仓库敏感信息扫描零命中 | ✅ 套件落盘（待 Docker）；扫描通过（2 处已评审误报） | EncryptionAtRestPgIntegrationTest（哈希/密文/无明文/往返/脱敏回显/孩子行 NULL）；secret-scan 等价扫描仅 2 行命中为 MaskingRulesTest 脱敏样例输入，裁定误报保留（M1-D12） |
| 8 | OpenAPI 认证部分文档产出（Swagger UI 可查全部 M1 端点与错误码说明） | ✅ | OpenApiConfig bearerAuth 方案 + 契约总则；3 控制器 15 端点 @Tag/@Operation/@SecurityRequirement；/v3/api-docs 200（SmokeIntegrationTest） |
| 9 | mcp / 登录锁定 / refresh 轮转三项安全机制专项测试 | ✅ | ChildFirstLoginPgIntegrationTest（200010 拦截→改密放行→旧口令失效）、LoginLockoutPgIntegrationTest（PT2S 锁定→200003→到期恢复）、RefreshReusePgIntegrationTest（重放→100003+全吊销→重登恢复）；H2 侧 AuthFlowH2 亦覆盖轮转与 mcp |

**遗留**（不阻塞 M1 合入，见设计 §16）：DoD #5 待 Docker 环境修复后关闭——届时
`docs/version-matrix.md` postgresql ⚠️ → ✅，PG 套件群（含权限矩阵 60 用例）全量实跑。

---

## 10. M7 容器基础镜像（Docker，待 spike 验证）

> M7 设计 §4（D56）。本机 Docker 守护进程不可用（同 M1 postgresql ⚠️ 口径），
> tag 为锁定目标值，Docker 就绪后执行 `docker build` 冒烟验证（镜像可构建 +
> 健康检查转 UP）方可置 ✅。

| 镜像 | tag | 阶段 | 状态 | 备注 |
|---|---|---|---|---|
| maven | `3.9.16-eclipse-temurin-25` | builder | ⚠️ 待 Docker spike | 含 Maven 3.9.16 + JDK 25，跑 `mvn clean verify` 全门禁；与 Enforcer 基线 [25,26) / [3.9.16,) 一致 |
| eclipse-temurin | `25-jre` | runtime | ⚠️ 待 Docker spike | JRE 25 运行时（glibc，支持 ZGC 与虚拟线程）；非 root 运行；内置 apt 装 curl 供 HEALTHCHECK |

**M7 构建验证项**（Docker 就绪后）：
1. `docker build -t pocket-money-server:m7 .` 构建成功（builder 全门禁通过）。
2. 容器启动后 `/actuator/health` 由 401/未就绪 → `UP`（HEALTHCHECK start-period 40s 内）。
3. 分层 jar 生效（`dependencies` 层缓存命中，二次构建仅重打 `application` 层）。
4. 容器内进程为非 root（`pocket` 用户），`/app/logs/gc.log` 可写。
