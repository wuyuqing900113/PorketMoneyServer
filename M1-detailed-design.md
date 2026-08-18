# M1 认证授权与家庭域 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（M1 章节）、`M0-detailed-design.md`、`docs/version-matrix.md`
> 文档版本：v1.0（2026-08-18，草案，待评审）
> 适用范围：M1 阶段（第 3–4 周，08-31 ~ 09-13）
> M0 基线：`mvn clean verify` 全绿（44 测试，Checkstyle/PMD/SpotBugs 0 违规，JaCoCo 行覆盖率 91.0%）

---

## 1. 概述

### 1.1 目标

在 M0 工程底座之上建立认证授权体系与用户/家庭业务域：

- JWT 双令牌无状态认证（签发、校验、刷新、主动失效），未认证请求一律拒绝
- PARENT / CHILD 角色模型与访问控制（接口级 + 数据级），孩子无法访问家长专属接口
- 用户注册与信息管理、家庭创建与维护、孩子账号全生命周期管理
- 敏感字段 AES-256-GCM 加密存储、密码 BCrypt 单向哈希，落实安全约束
- 审计日志落库 + 安全日志与告警，登录防暴力破解
- RestAssured 集成测试覆盖异常场景，Testcontainers + PostgreSQL 关闭 M0 遗留的 PG 运行验证 ⚠️ 项

### 1.2 范围（In Scope）

- Spring Security 无状态过滤器链 + JWT 双令牌（access / refresh）
- 用户模块：家长注册、登录/登出/刷新、个人信息查看与编辑、密码修改、家长重置孩子密码
- 登录防护：固定次数临时锁定 + 安全日志告警
- 家庭域：注册即建家庭、家庭信息编辑、添加/管理孩子账号、成员列表与移除
- 敏感数据加密：手机号 AES-256-GCM 加密存储（TypeHandler 统一加解密）
- 审计日志基础设施（登录、权限相关操作落库）；安全日志（认证失败、越权尝试、令牌重用）
- OAuth2 第三方登录扩展点预留（接口 + 表结构，不做 provider 实现）
- GlobalExceptionHandler 完成 M0 预留的 `TODO(M1)`（Security 异常映射）
- OpenAPI 文档认证部分产出（SpringDoc 注解）

### 1.3 非目标（Out of Scope）

| 事项 | 归属阶段 |
|---|---|
| 短信验证码（注册/登录验证、密码找回带外通道） | M5（notify），M1 以文档标注已知限制 |
| OAuth2 第三方登录实现（华为账号等） | 本期仅预留扩展点（D9 决策见 §2） |
| 忘记密码自助重置 | M5（依赖短信/邮件） |
| 多家庭、一个家庭多家长 | 表结构可容纳，M1 约束为单家庭单家长 |
| 邀请码加入家庭 | 不实现（见 D4） |
| 分布式限流 / Redis | M3 限流专项；M1 锁定机制基于 DB 计数 |
| SLS 告警规则配置 | M7（M1 仅产出结构化安全日志，约定接入点） |
| Docker 镜像构建与阿里云部署 | M7 |

---

## 2. 决策记录（已确认）

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D1 | JWT 令牌架构 | **双令牌 access + refresh** | access 短 TTL（15 分钟）无状态鉴权；refresh 长 TTL（14 天）服务端持久化、可吊销；满足 roadmap「刷新与失效」要求 |
| D2 | 密码与敏感字段存储 | **密码 BCrypt 单向哈希；手机号等可逆敏感字段 AES-256-GCM 加密** | 落实 roadmap「AES-256 加密敏感数据」约束，同时符合 OWASP 密码存储实践（密码不可逆、仅可重置） |
| D3 | 孩子账号来源 | **家长在家庭内创建孩子账号** | 家长同意机制天然内置（账号由家长创建），COPPA 类合规路径最短，孩子无独立注册入口 |
| D4 | 家庭创建与加入 | **家长注册即自动创建家庭，家长直接添加成员** | 流程最短、权限最清晰，匹配「家庭为单位」产品形态；邀请码不实现 |
| D5 | 登录标识 | **家长用手机号；孩子用家长设定的登录名** | 儿童数据最小化采集（孩子不收集手机号）；孩子登录名全局唯一 |
| D6 | 集成测试数据库 | **Testcontainers + PostgreSQL 18 为主** | 关闭 M0 遗留 PG 运行验证 ⚠️；Docker 未就绪期间 `disabledWithoutDocker` 自动跳过，H2 切片测试托底（§12.2） |
| D7 | 登录防护 | **固定次数临时锁定**：连续 5 次失败锁定 15 分钟 + 安全日志告警 | 零三方依赖，可完整实现与测试 |
| D8 | refresh 令牌轮转 | **刷新即轮转**：每次刷新旧令牌立即作废；已作废令牌被再次使用判定为窃取，告警并吊销该用户全部令牌 | OAuth 2.0 安全最佳实践（RFC 6819 / OAuth 2.1 草案） |
| D9 | 家庭域归属 | **家庭域归入 user 模块**，不新增顶层模块 | 与 roadmap 模块划分（user/money/rule/finance/ai/notify）保持一致 |

---

## 3. 总体设计

### 3.1 请求处理链路（M1 结束时）

```
鸿蒙 APP
  │ HTTPS / TLS 1.3（生产由 SLB 终结，见 §8.4）
  ▼
TraceIdFilter（M0，最高优先级：X-Trace-Id 白名单校验 + MDC）
  ▼
Spring Security FilterChain（STATELESS，CSRF 关闭）
  ├── 白名单路径 → 直接放行（register / login / refresh / health / api-docs）
  └── 其余路径 → JwtAuthenticationFilter
        ├── Bearer 解析 + HS256 验签 + 过期校验
        ├── mcp（首次改密）声明强制执行（§4.6）
        └── SecurityContext 注入 UserIdPrincipal（ROLE_PARENT / ROLE_CHILD）
  ▼
Controller（@PreAuthorize 接口级鉴权）→ Service（数据级归属校验 + 审计埋点）→ Mapper → PostgreSQL
  │                                    ↘ AuditService（REQUIRES_NEW 落库）
  认证/授权拒绝 → EntryPoint(401 + Result 100003) / DeniedHandler(403 + Result 100004)
```

### 3.2 包结构增量（在 M0 骨架上生长）

```
src/main/java/wyq/pocket/money/
├── common/
│   ├── security/                      # 【新增】认证授权基础设施（跨模块公共能力）
│   │   ├── config/SecurityConfig.java
│   │   ├── jwt/JwtTokenService.java, JwtProperties.java, TokenPair.java
│   │   ├── filter/JwtAuthenticationFilter.java
│   │   ├── handler/RestAuthenticationEntryPoint.java, RestAccessDeniedHandler.java
│   │   └── UserIdPrincipal.java       # SecurityContext 主体（userId/familyId/role）
│   ├── crypto/                        # 【新增】敏感数据加密
│   │   ├── DataEncryptor.java         # AES-256-GCM 加解密
│   │   └── EncryptedFieldTypeHandler.java  # MyBatis TypeHandler
│   ├── audit/                         # 【新增】审计日志基础设施
│   │   ├── AuditService.java, AuditAction.java, AuditEntry.java
│   │   └── mapper/AuditLogMapper.java
│   └── web/GlobalExceptionHandler.java   # 【修改】补 TODO(M1) Security 异常映射
├── user/                              # 用户 + 家庭域（D9：家庭不独立成模块）
│   ├── controller/AuthController, UserController, FamilyController
│   ├── service/   AuthService, UserService, FamilyService,
│   │              LoginGuardService, RefreshTokenService
│   ├── mapper/    UserMapper, FamilyMapper, FamilyMemberMapper, RefreshTokenMapper
│   ├── domain/    User, Family, FamilyMember, RefreshToken
│   └── dto/       RegisterRequest, LoginRequest, TokenPairResponse, …, UserErrorCode
└── （money/rule/finance/ai/notify 保持空骨架）

src/main/resources/db/migration/
├── V1__baseline.sql                   # M0 已交付
├── V2__create_user_family.sql         # 【新增】app_user / family / family_member
└── V3__create_auth_token_audit.sql    # 【新增】user_refresh_token / audit_log / user_oauth_binding
```

### 3.3 与 M0 基线的衔接

| M0 交付物 | M1 变更 |
|---|---|
| `GlobalExceptionHandler` 中 `TODO(M1)` | 补 `AccessDeniedException → 100004`、`AuthenticationException → 100003`（§4.8） |
| 错误码段位表（20xxxx = 用户与家庭，M1 定义） | 新增 `UserErrorCode` 枚举（§10.4） |
| `ArchitectureTest` 四条规则（allowEmptyShould） | 保留不动；user 模块有真实类后规则自动产生约束力 |
| `MaskingRules` 手机号脱敏 | 复用：解密后的手机号在日志面仍有脱敏兜底 |
| `Result` / TraceId / 日志体系 | 原样复用；logback 增加 SECURITY logger（§9.2） |
| `docs/version-matrix.md` postgresql ⚠️ | Testcontainers 套件通过后更新为 ✅（§12.2、DoD） |

---

## 4. 认证授权设计

### 4.1 技术选型

| 项 | 选型 | 说明 |
|---|---|---|
| 安全框架 | `spring-boot-starter-security`（Boot 4 / Security 7） | 无状态会话（`SessionCreationPolicy.STATELESS`）；纯 API 服务关闭 CSRF |
| JWT 签发/校验 | `spring-security-oauth2-jose`（NimbusJwtEncoder / NimbusJwtDecoder） | Spring Security 生态内工件，版本随 Boot BOM 管理，不引入额外三方坐标；spike S2 验证不通过则预案切换 jjwt（§13） |
| 签名算法 | **HS256**，密钥长度 512 bit | 单体服务自签自验，对称密钥足够；密钥经环境变量 `JWT_SECRET`（Base64）注入，**严禁硬编码** |
| 密码哈希 | BCrypt（spring-security-crypto，strength=10） | 随 starter-security 自带，无额外依赖 |
| 方法级鉴权 | `@EnableMethodSecurity` + `@PreAuthorize("hasRole('PARENT')")` | 接口级权限声明在 Controller 方法上，权限矩阵见附录 B |

### 4.2 过滤器链配置（SecurityConfig）

```java
http
  .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
  .csrf(AbstractHttpConfigurer::disable)
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                       "/actuator/health/**", "/error", "/v3/api-docs/**").permitAll()
      .anyRequest().authenticated())
  .exceptionHandling(e -> e
      .authenticationEntryPoint(restAuthenticationEntryPoint)   // 401 + Result(100003)
      .accessDeniedHandler(restAccessDeniedHandler))            // 403 + Result(100004)
  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

要点：

- **白名单最小化**：仅注册/登录/刷新与健康检查、OpenAPI 元数据匿名可达；Swagger UI 页面资源沿用 M0 的 profile 控制（prod 关闭）
- **HTTP 状态码约定（对 M0 总则的补充）**：认证/授权拒绝返回 **HTTP 401/403 + Result 包裹体**（code=100003/100004），便于端上拦截器统一触发「跳登录/提示无权限」；其余业务错误维持 HTTP 200 + code 的 M0 约定。该补充写入 OpenAPI 文档总则
- EntryPoint / DeniedHandler 输出 JSON 时同步记安全日志（§9.2）并携带 traceId

### 4.3 JWT Claims 与令牌生命周期

| Claim | access | refresh | 说明 |
|---|---|---|---|
| `sub` | ✅ userId | ✅ userId | 主体 |
| `fam` | ✅ familyId | — | 数据级归属校验快路径 |
| `role` | ✅ PARENT/CHILD | — | 授权角色 |
| `mcp` | ✅（孩子首次改密前为 true） | — | must-change-password 强制位（§4.6） |
| `jti` | ✅ | ✅ | refresh 的 jti 用于落库关联 |
| `typ` | `access` | `refresh` | 防止两类令牌混用（验签通过也拒绝 typ 不符） |
| `iat` / `exp` | ✅ | ✅ | access TTL 15 分钟；refresh TTL 14 天（均可环境变量覆盖） |

refresh 令牌**不落明文**：服务端仅存 `SHA-256(refreshToken)`（`user_refresh_token.token_hash`，唯一索引），校验时以哈希命中行为准。

**吊销时机表**：

| 触发事件 | 吊销范围 |
|---|---|
| 登出 | 本次提交的 refresh 令牌 |
| 修改密码（自助） | 该用户**全部** refresh 令牌 |
| 家长重置孩子密码 | 该孩子全部 refresh 令牌 |
| 孩子被移出家庭 | 该孩子全部 refresh 令牌 |
| refresh 重用检测（D8） | 该用户全部 refresh 令牌 + ERROR 级安全告警 |

吊销语义：`revoked_at` 置当前时间（软删除，保留审计线索），不做物理删除。

### 4.4 刷新流程与重用检测（D8）

```
POST /auth/refresh {refreshToken}
  │ 验签 + exp + typ=refresh
  │ 按 SHA-256(refreshToken) 查 user_refresh_token
  ├─ 未命中 / 已过期            → 100003（登录态失效，端上跳登录）
  ├─ revoked_at 非空（已作废）   → 重用检测：SECURITY ERROR 告警
  │                                + 吊销该用户全部令牌 → 100003
  └─ 有效 → 旧行 revoked_at=now（轮转）
           → 签发新 access + 新 refresh（新行落库）→ 200 TokenPair
```

轮转保证任一时刻同一用户**只有一个有效 refresh 令牌**；被吊销令牌再次出现即意味着令牌泄露，按安全事件处置。

### 4.5 登录锁定（LoginGuardService，D7）

- 计数落库：`app_user.failed_attempts` / `app_user.locked_until`（重启不丢失，10 TPS 场景 DB 压力可忽略）
- 规则：同一账号连续失败 **5 次** → `locked_until = now + 15 分钟`，计数归零重新累计；锁定期间登录直接返回 **200003**（不提示剩余次数细节）
- 成功登录清零计数；每次失败与锁定事件写审计 + 安全日志（§9）
- 阈值与时长可配置（`LOGIN_MAX_ATTEMPTS` / `LOGIN_LOCK_DURATION`），默认 5 / PT15M
- 统一错误文案：账号不存在、密码错误、已锁定之外的情形一律返回 **200002「账号或密码错误」**，防止用户名枚举

### 4.6 角色与访问控制

- **角色枚举**：`PARENT`（家长）、`CHILD`（孩子），存储于 `app_user.role`，登录后写入 JWT `role` claim；M1 不提供角色变更接口（孩子账号永远是 CHILD）
- **接口级**：`@PreAuthorize("hasRole('PARENT')")` 标注家长专属接口；完整矩阵见**附录 B**，并以参数化集成测试固化（§12.3）
- **数据级**：孩子仅能访问**自己所在家庭**的数据。service 层统一经 `FamilyAccessChecker.requireMember(familyId, currentUserId)` 校验；越权尝试记安全日志（100004 照常返回）
- **首次改密强制（mcp）**：家长创建孩子时生成 `must_change_password=true` 的账号，access 令牌携带 `mcp=true`；`JwtAuthenticationFilter` 对 `mcp=true` 的请求仅放行 `POST /users/me/password` 与 `/auth/logout`，其余返回 **200010**，直到密码修改成功后新发令牌 `mcp=false`

### 4.7 OAuth2 扩展点预留（roadmap 要求）

M1 只做结构预留，不实现任何 provider：

- 接口预留：`common/security/oauth/AuthProvider`（`supports(provider)` / `authenticate(externalCredential)` / 返回绑定用户），`package-info` 说明演进方式
- 表结构预留：`user_oauth_binding(provider, external_id, user_id)`，唯一约束 `(provider, external_id)`（V3 脚本建表）
- 未来接入：新增 provider 实现 + `POST /api/v1/auth/oauth/{provider}` 端点即可，不动现有认证链路

### 4.8 GlobalExceptionHandler 收尾（M0 遗留 TODO(M1)）

| 异常 | 映射 | HTTP 状态 |
|---|---|---|
| `AccessDeniedException`（Security） | 100004 | 403 |
| `AuthenticationException` 及子类 | 100003 | 401 |

与 EntryPoint/DeniedHandler 的分工：过滤器链之前的拒绝走 handler；进入 MVC 之后（如 `@PreAuthorize`、service 内显式抛出）走 GlobalExceptionHandler。二者输出同一 Result 契约。

---

## 5. 用户模块设计

### 5.1 家长注册

`POST /api/v1/auth/register`（匿名）

```json
{ "phone": "13800001234", "password": "Passw0rd!", "nickname": "妈妈",
  "childPrivacyPolicyAccepted": true }
```

- 校验：手机号格式（`@Pattern`）、密码强度（自定义 `@StrongPassword`：≥8 位且含字母与数字）、昵称 1–32 字、`childPrivacyPolicyAccepted` 必须为 true（`@AssertTrue`，COPPA 家长同意留痕，落 `consented_at`）
- **同事务完成**：插入 `app_user`（PARENT，phone_hash + phone_encrypted，BCrypt 哈希）→ 创建 `family`（默认名「{nickname}的家庭」）→ 插入 `family_member`（创建者）→ 审计 REGISTER / FAMILY_CREATE
- 手机号已存在（phone_hash 命中唯一约束）→ **200001**
- M1 无短信验证码，注册不验证手机号持有情况 —— 文档标注为已知限制，M5 补验证码

### 5.2 登录

`POST /api/v1/auth/login`（匿名）：`{ "identifier": "...", "password": "..." }`

- identifier 为 11 位数字串按手机号处理（phone_hash 查找），否则按孩子登录名（username 查找）——单端点统一入口
- 校验顺序：锁定检查（→200003）→ 停用检查（→200004）→ 密码比对（失败计数，→200002）
- 成功：签发 TokenPair（refresh 落库），审计 LOGIN_SUCCESS；孩子账号返回 `mustChangePassword` 标志
- 响应体（示意）：

```json
{ "code": 0, "message": "success", "data": {
    "accessToken": "eyJ…", "refreshToken": "eyJ…", "expiresIn": 900,
    "mustChangePassword": false,
    "user": { "userId": 1, "nickname": "妈妈", "role": "PARENT", "familyId": 1 } },
  "traceId": "8f3a2b1c…", "timestamp": 1787654321000 }
```

### 5.3 登出与刷新

- `POST /api/v1/auth/logout`（需认证，携带 Bearer access）：吊销请求体中提交的 refresh 令牌（`{ "refreshToken": "…" }`）；access 本身短 TTL 自然过期。审计 LOGOUT
- `POST /api/v1/auth/refresh`：见 §4.4

### 5.4 密码管理

| 场景 | 接口 | 说明 |
|---|---|---|
| 自助修改 | `POST /api/v1/users/me/password` `{oldPassword,newPassword}` | 旧密码错误 → 200008；成功后吊销该用户全部 refresh（§4.3）；清除 `must_change_password`；审计 PASSWORD_CHANGE |
| 家长重置孩子 | `POST /api/v1/families/{familyId}/children/{userId}/password-reset` | 仅 PARENT；新临时密码由请求传入（家长线下告知）并置 `mcp=true`；吊销孩子全部 refresh；审计 CHILD_PASSWORD_RESET。M1 无短信通道，找回依赖家长重置 |

密码策略：`@StrongPassword`（≥8 位、至少含字母与数字各一），Bean Validation 失败走 100001；BCrypt strength=10。

### 5.5 个人信息管理

- `GET /api/v1/users/me`：返回 userId、nickname、role、familyId、maskedPhone（家长，`138****1234`，复用 `MaskingRules`；**解密结果不出 service 层**）
- `PUT /api/v1/users/me` `{nickname}`：家长与孩子均可改自己的昵称；登录名与手机号不可变（M1 无换绑通道）

---

## 6. 家庭域设计（user 模块内，D9）

### 6.1 模型与约束

- 一个用户**仅属于一个家庭**（`family_member.user_id` 唯一约束）；注册即建（§5.1），M1 无加入/迁移他途
- 家庭创建者为唯一家长（`family.owner_user_id`）；**M1 不支持移除家长**（约束见 6.4）
- 成员数上限 **8**（超限 → 200006），家庭名 ≤ 32 字
- 孩子登录名（username）**全局唯一**（登录按全局查找，避免歧义；冲突 → 200007），仅小写字母与数字，4–20 位

### 6.2 家庭信息管理

- `GET /api/v1/families/{familyId}`：家庭成员均可查看自己家庭（数据级校验）
- `PUT /api/v1/families/{familyId}` `{familyName}`：仅 PARENT；审计 FAMILY_UPDATE

### 6.3 添加孩子账号（D3/D5 落地）

`POST /api/v1/families/{familyId}/children`（仅 PARENT）：

```json
{ "username": "xiaoming", "password": "Init1234", "nickname": "小明" }
```

- 同事务：插入 `app_user`（CHILD，username 唯一校验，`must_change_password=true`，`consented_at=now`，`consented_by=家长id`）→ 插入 `family_member` → 审计 CHILD_CREATE
- 孩子无手机号、无邮箱 —— 儿童个人信息最小化采集（COPPA 类合规要求，mission 合规约束）

### 6.4 成员管理

- `GET /api/v1/families/{familyId}/members`：家庭成员均可（孩子可见家庭花名册）
- `PUT /api/v1/families/{familyId}/children/{userId}` `{nickname}`：仅 PARENT，编辑孩子资料；审计 CHILD_UPDATE
- `DELETE /api/v1/families/{familyId}/members/{userId}`：仅 PARENT，约束：
  - 目标是家庭创建者 → **200012**（M1 无家长移除/转让）
  - 目标不在本家庭 → **200011**
  - 移除孩子：删成员关系 + 吊销其全部 refresh + 置 `status=DISABLED`（账号数据保留供审计，禁止再登录 → 200004）；审计 MEMBER_REMOVE
- 孩子不能移除任何成员（接口级 100004）

---

## 7. 数据模型与迁移

### 7.1 V2__create_user_family.sql

```sql
CREATE TABLE app_user (
    id                     BIGSERIAL PRIMARY KEY,
    username               VARCHAR(20),              -- 孩子登录名（全局唯一）；家长为 NULL
    phone_hash             CHAR(64),                 -- 家长手机号 SHA-256 hex（查找/唯一）；孩子为 NULL
    phone_encrypted        VARCHAR(512),             -- 家长手机号 AES-256-GCM Base64（回显用）
    key_version            SMALLINT  NOT NULL DEFAULT 1,   -- 加密密钥版本（轮换预留，§8.3）
    password_hash          VARCHAR(72) NOT NULL,     -- BCrypt(60)
    nickname               VARCHAR(32) NOT NULL,
    role                   VARCHAR(16) NOT NULL,     -- PARENT / CHILD
    status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / DISABLED
    must_change_password   BOOLEAN   NOT NULL DEFAULT FALSE,
    consented_at           TIMESTAMPTZ NOT NULL DEFAULT now(),      -- 儿童隐私政策同意留痕
    consented_by           BIGINT,                    -- 孩子：创建其账号的家长 id；家长：NULL
    failed_attempts        SMALLINT  NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_app_user_username  UNIQUE (username),
    CONSTRAINT uk_app_user_phone     UNIQUE (phone_hash),
    CONSTRAINT chk_app_user_identifier CHECK (
        (username IS NOT NULL AND phone_hash IS NULL)
     OR (username IS NULL AND phone_hash IS NOT NULL))
);

CREATE TABLE family (
    id            BIGSERIAL PRIMARY KEY,
    family_name   VARCHAR(32) NOT NULL,
    owner_user_id BIGINT NOT NULL REFERENCES app_user (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE family_member (
    id        BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES family (id),
    user_id   BIGINT NOT NULL REFERENCES app_user (id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_family_member_family UNIQUE (family_id, user_id),
    CONSTRAINT uk_family_member_user   UNIQUE (user_id)      -- M1：一人一家庭
);
```

### 7.2 V3__create_auth_token_audit.sql

```sql
CREATE TABLE user_refresh_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES app_user (id),
    token_hash CHAR(64) NOT NULL,               -- SHA-256(refreshToken)，不落明文
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,                     -- 软吊销（§4.3 吊销时机表）
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_token_user ON user_refresh_token (user_id);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,                         -- 匿名事件（如注册前）可为空
    action      VARCHAR(48) NOT NULL,
    target_type VARCHAR(32),
    target_id   VARCHAR(64),
    detail      JSONB,                          -- 结构化补充信息（脱敏后）
    client_ip   VARCHAR(45),                    -- 兼容 IPv6
    trace_id    VARCHAR(64),                    -- 关联 MDC traceId
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_user_time   ON audit_log (user_id, created_at);
CREATE INDEX idx_audit_action_time ON audit_log (action, created_at);

-- OAuth2 扩展点预留（§4.7）：M1 建表不实现代码
CREATE TABLE user_oauth_binding (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_user (id),
    provider    VARCHAR(32)  NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_oauth_provider_external UNIQUE (provider, external_id)
);
```

设计要点：

- **手机号可查找 + 可回显 + 不泄露**：`phone_hash`（SHA-256，等值查找与唯一约束）与 `phone_encrypted`（AES-256-GCM，仅回显）双列分工；二者均不可反推原文（哈希列）或需密钥解密（加密列）
- 金额类 DECIMAL 精度约束属 M2（money 域），M1 无金额字段
- 脚本一经提交永不修改；回滚走新脚本前向修复（M0 既定规范）

---

## 8. 敏感数据加密设计（D2）

### 8.1 算法与参数

| 项 | 值 |
|---|---|
| 算法 | AES-256-GCM（认证加密，篡改即解密失败） |
| 密钥 | 32 字节，环境变量 `DATA_ENCRYPTION_KEY`（Base64 注入，prod 必须显式提供，缺失则启动失败） |
| IV | 12 字节，每次加密随机生成，随密文前缀存储 |
| 认证标签 | 128 bit |
| 落库格式 | Base64(IV ‖ ciphertext ‖ tag)，VARCHAR(512) |

### 8.2 实现形态

- `common/crypto/DataEncryptor`：`encrypt(plain) / decrypt(cipher)` 纯函数组件，单测覆盖往返、篡改检测（GCM tag）、不同 IV 随机性
- `common/crypto/EncryptedFieldTypeHandler`（MyBatis TypeHandler）：`phone_encrypted` 列写入自动加密、读取自动解密 —— **统一入口，杜绝明文绕过**（spike S6 在真实 PG 上验证）
- 红线：解密后的明文仅在 service 层短暂存在；出参 DTO 一律脱敏值（`MaskingRules`）；日志面 MaskingRules 双保险兜底

### 8.3 密钥轮换预留

- `app_user.key_version` 记录行级密钥版本；M1 仅 v1
- 轮换预案（文档化，不在 M1 实现）：新密钥写入 → 后台任务按 key_version 逐行解密重加密 → 全量完成后旧密钥归档

### 8.4 传输安全（TLS 1.3）

- 生产形态：阿里云 SLB 终结 TLS 1.3，SLB→应用为内网 HTTP；应用配置 `server.forward-headers-strategy=native` 正确还原 scheme/client-ip（审计日志 client_ip 依赖该配置，M1 落实配置项）
- M1 交付配置与文档约束；证书与 TLS 强制在 M7 部署阶段验证
- 本地开发 HTTP，不做证书处理

---

## 9. 审计日志与安全日志（roadmap M1 要求）

### 9.1 审计日志（落库，与运行日志分离）

- `AuditService.record(AuditEntry)`：`REQUIRES_NEW` 独立事务写 `audit_log`；写入失败记 ERROR 运行日志但不阻断业务（10 TPS 场景下以可靠性优先，失败有 ERROR 告警可追溯）
- traceId 自动从 MDC 注入；client_ip 取自请求（forward-headers 还原后）
- M1 不提供审计查询 API（SQL 直查 + DBA 通道）；管理端查询界面另期规划

**审计动作枚举（AuditAction）**：

| 动作 | 触发点 |
|---|---|
| REGISTER / FAMILY_CREATE | 家长注册（同事务两条） |
| LOGIN_SUCCESS / LOGIN_FAILURE / ACCOUNT_LOCKED | 登录链路 |
| LOGOUT / TOKEN_REFRESH / TOKEN_REUSE_DETECTED | 令牌链路 |
| PASSWORD_CHANGE / CHILD_PASSWORD_RESET | 密码链路 |
| FAMILY_UPDATE | 家庭信息编辑 |
| CHILD_CREATE / CHILD_UPDATE / MEMBER_REMOVE | 成员链路 |

### 9.2 安全日志（告警载体）

- 独立 logger：`SECURITY`（常量按 code-style-guide 全大写），logback 配置继承 root appender，M6/M7 由 SLS 按 logger 名 + WARN/ERROR 级别配置告警规则
- 输出：结构化键值（事件、userId/identifier 脱敏值、path、traceId）

| 事件 | 级别 | 触发点 |
|---|---|---|
| LOGIN_FAILURE | WARN | 密码失败（含 identifier 脱敏） |
| ACCOUNT_LOCKED | WARN | 锁定生效 |
| TOKEN_REUSE_DETECTED | **ERROR** | refresh 重用（疑似泄露，D8） |
| ACCESS_DENIED | WARN | 越权尝试（DeniedHandler / @PreAuthorize 拒绝） |
| UNAUTHENTICATED_REJECT | WARN | 无令牌/令牌无效访问受保护资源 |

### 9.3 边界

运行日志（logback JSON，M0 体系）负责排障；审计日志落库负责合规追溯；安全日志负责实时告警。三者字段不重复采集，traceId 贯穿关联。

---

## 10. API 设计

### 10.1 总则补充（对 M0 API 文档总则）

- 受保护接口统一 `Authorization: Bearer {accessToken}`
- 100003 → HTTP 401；100004 → HTTP 403（§4.2）；其余业务错误 HTTP 200 + code
- 所有接口统一 `Result` 包裹与 traceId 返回（M0 约定不变）

### 10.2 端点清单

| # | 方法与路径 | 说明 | 鉴权 |
|---|---|---|---|
| 1 | POST `/api/v1/auth/register` | 家长注册（自动建家庭） | 匿名 |
| 2 | POST `/api/v1/auth/login` | 登录（家长手机号/孩子登录名统一入口） | 匿名 |
| 3 | POST `/api/v1/auth/refresh` | 刷新令牌（轮转） | 匿名（凭有效 refresh） |
| 4 | POST `/api/v1/auth/logout` | 登出（吊销 refresh） | 认证 |
| 5 | GET `/api/v1/users/me` | 当前用户信息 | 认证 |
| 6 | PUT `/api/v1/users/me` | 修改昵称 | 认证 |
| 7 | POST `/api/v1/users/me/password` | 修改密码（孩子首次改密同此） | 认证 |
| 8 | GET `/api/v1/users/me/family` | 我的家庭（= 9 的便捷形式） | 认证 |
| 9 | GET `/api/v1/families/{familyId}` | 家庭详情 | 家庭成员 |
| 10 | PUT `/api/v1/families/{familyId}` | 编辑家庭信息 | PARENT |
| 11 | POST `/api/v1/families/{familyId}/children` | 添加孩子账号 | PARENT |
| 12 | GET `/api/v1/families/{familyId}/members` | 成员列表 | 家庭成员 |
| 13 | PUT `/api/v1/families/{familyId}/children/{userId}` | 编辑孩子资料 | PARENT |
| 14 | POST `/api/v1/families/{familyId}/children/{userId}/password-reset` | 重置孩子密码 | PARENT |
| 15 | DELETE `/api/v1/families/{familyId}/members/{userId}` | 移除成员 | PARENT |

### 10.3 关键交互示例

注册成功：

```json
{ "code": 0, "message": "success",
  "data": { "userId": 1, "familyId": 1, "nickname": "妈妈", "role": "PARENT" },
  "traceId": "8f3a2b1c…", "timestamp": 1787654321000 }
```

刷新令牌已作废被重用（重用检测）：

```json
{ "code": 100003, "message": "登录态已失效，请重新登录", "data": null,
  "traceId": "8f3a2b1c…", "timestamp": 1787654321000 }
```

孩子访问家长专属接口（如 `PUT /families/1`）：

```json
{ "code": 100004, "message": "无权限执行该操作", "data": null,
  "traceId": "8f3a2b1c…", "timestamp": 1787654321000 }
```

### 10.4 用户与家庭段错误码（20xxxx，UserErrorCode 枚举）

| 错误码 | 含义 | 客户端处理建议 |
|---|---|---|
| 200001 | 该手机号已注册 | 提示用户，不可重试 |
| 200002 | 账号或密码错误 | 不可重试（防枚举统一文案） |
| 200003 | 账号已临时锁定，请稍后再试 | 延迟后重试 |
| 200004 | 账号已停用 | 不可重试，提示联系家长 |
| 200005 | 家庭不存在 | 不可重试 |
| 200006 | 家庭成员数量已达上限 | 不可重试 |
| 200007 | 该登录名已被占用 | 提示更换，不可重试 |
| 200008 | 原密码不正确 | 不可重试 |
| 200009 | 刷新令牌无效或已过期 | 跳转登录 |
| 200010 | 请先修改初始密码 | 引导至修改密码页 |
| 200011 | 目标成员不在该家庭中 | 不可重试 |
| 200012 | 不能移除家庭创建者 | 不可重试 |

通用认证/授权错误沿用 100003 / 100004（M0 段）。

---

## 11. 配置与环境变量

### 11.1 新增环境变量

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `JWT_SECRET` | HS256 密钥（Base64，≥64 字节） | 无默认；prod 缺失启动失败 |
| `JWT_ACCESS_TTL` | access 有效期（ISO-8601 Duration） | `PT15M` |
| `JWT_REFRESH_TTL` | refresh 有效期 | `P14D` |
| `DATA_ENCRYPTION_KEY` | AES-256 密钥（Base64，32 字节） | 无默认；prod 缺失启动失败 |
| `LOGIN_MAX_ATTEMPTS` | 锁定阈值 | `5` |
| `LOGIN_LOCK_DURATION` | 锁定时长 | `PT15M` |

红线不变：任何密钥/口令禁止进入代码与配置文件（mission 禁止项），local 开发值仅存本机 `.env`（已 gitignore）。各环境密钥**相互独立**，不得复用。

### 11.2 application.yml 增量（摘要）

```yaml
pocket-money:
  security:
    jwt:
      secret: ${JWT_SECRET:}
      access-ttl: ${JWT_ACCESS_TTL:PT15M}
      refresh-ttl: ${JWT_REFRESH_TTL:P14D}
    login-guard:
      max-attempts: ${LOGIN_MAX_ATTEMPTS:5}
      lock-duration: ${LOGIN_LOCK_DURATION:PT15M}
  crypto:
    data-key: ${DATA_ENCRYPTION_KEY:}

server:
  forward-headers-strategy: native   # SLB 代理下还原 scheme / client-ip（§8.4）
```

配置绑定走 `@ConfigurationProperties`（`JwtProperties` 等），启动时对 prod/test profile 做非空校验（fail-fast）。

---

## 12. 测试设计

### 12.1 单元测试

| 测试类 | 覆盖点 |
|---|---|
| JwtTokenServiceTest | 签发/验签/过期/typ 混用拒绝/claims 完整性 |
| DataEncryptorTest | 加解密往返、GCM 篡改检测、IV 随机性、空值处理 |
| EncryptedFieldTypeHandlerTest | TypeHandler set/get 参数路径 |
| LoginGuardServiceTest | 计数累计、锁定生效与到期、成功清零、锁定中拒绝 |
| PasswordPolicyTest | @StrongPassword 边界（长度/字母/数字） |
| AuthServiceTest / FamilyServiceTest | 业务分支（mock mapper）：重复手机号、登录名冲突、成员上限、移除约束 |
| UserErrorCodeTest | 段位 20xxxx、无重复值（仿 CommonErrorCodeTest） |

### 12.2 集成测试（RestAssured + Testcontainers，D6）

- 形态：`@SpringBootTest(webEnvironment = RANDOM_PORT)` + RestAssured；DB 用 Testcontainers PostgreSQL 18 共享单例容器 + `@ServiceConnection`（spike S4 验证，不通则 `DynamicPropertyRegistry` 手工注入）
- **Docker 未就绪托底**：测试类标注 `@Testcontainers(disabledWithoutDocker = true)` —— Docker 不可用时自动跳过而非失败，`mvn verify` 保持常绿；Mapper 层切片测试继续用 H2 兼容模式托底覆盖率
- **关闭 M0 ⚠️ 项**：Docker 就绪后该套件全量跑绿，`docs/version-matrix.md` 中 postgresql 状态 ⚠️ → ✅（DoD 检查项）

| 套件 | 场景 |
|---|---|
| AuthFlowIT | 注册→登录→me→刷新（轮转）→登出→登出后 refresh 被拒 |
| AuthFailureScenariosIT | 错误凭证 200002、过期 access 100003、篡改签名 100003、停用账号 200004 |
| RefreshReuseIT | 已轮转令牌重用 → 100003 + 该用户全部令牌吊销 + 安全告警 |
| LoginLockoutIT | 连续 5 次失败 → 200003；锁定期过后恢复 |
| FamilyCrudIT | 家庭编辑、添加孩子、成员上限 200006、移除约束 200011/200012 |
| ChildFirstLoginIT | mcp=true 期间访问受限 → 200010；改密后放行 |
| PermissionMatrixIT | 见 §12.3 |
| EncryptionAtRestIT | 注册后直查 DB：phone_hash/phone_encrypted 非空且**无明文手机号**；解密往返一致 |

### 12.3 权限矩阵测试（roadmap DoD）

- `@ParameterizedTest` 遍历附录 B 全部端点 × 身份（匿名 / CHILD / 非本家庭 PARENT / 本家庭 PARENT），断言与矩阵一致（100003 / 100004 / 20xxxx / 0）
- 数据级用例：孩子 A 访问家庭 B 的接口 → 100004 + ACCESS_DENIED 安全日志

### 12.4 覆盖率与门禁

- JaCoCo 80% BUNDLE 门禁沿用（M0 配置不动）；user 模块新增类全部计入
- 注意：Docker 未就绪期间 Testcontainers 套件被跳过，覆盖率由单测 + H2 切片测试支撑，实施时监控报告防止跌破门禁

---

## 13. Spike 验证清单（M1 第 1–2 天）

在 `spike/m1-security` 分支最小验证，结论回写 `docs/version-matrix.md`：

| # | 验证项 | 通过标准 | 预案 |
|---|---|---|---|
| S1 | Boot 4.1.0 + `spring-boot-starter-security` | SecurityFilterChain / STATELESS / `@EnableMethodSecurity` 正常 | — |
| S2 | `spring-security-oauth2-jose` HS256 | NimbusJwtEncoder/Decoder 在 Security 7 签验通过 | 切换 jjwt 0.12.x（新增版本属性） |
| S3 | RestAssured + `@SpringBootTest(RANDOM_PORT)` | Boot 4 下端口注入与 Result JSON 断言可用 | 降级 MockMvc（牺牲 roadmap DoD 的 RestAssured 要求需评审） |
| S4 | Testcontainers postgres + `@ServiceConnection` + Flyway | 真实 PG 18 迁移 + CRUD 冒烟通过 | `DynamicPropertyRegistry` 手工注入；再不通则 D6 降级 H2 并评审 |
| S5 | mybatis-spring-boot-starter 4.0.0 与 Security 共存 | 无自动配置冲突 | 显式排除冲突自动配置 |
| S6 | EncryptedFieldTypeHandler 在 PG 上往返 | 写入加密/读取解密/篡改失败 | 改 service 层显式加解密 |

---

## 14. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 |
|---|---|---|---|
| T1 | Spike：M1 安全与测试栈兼容验证（§13）+ version-matrix 更新 | — | 2 人天 |
| T2 | Security 基础设施：SecurityConfig / JwtTokenService / JwtAuthenticationFilter / EntryPoint 与 DeniedHandler / GlobalExceptionHandler 收尾 | T1 | 2 人天 |
| T3 | 加密与审计基础设施：DataEncryptor / TypeHandler / AuditService / SECURITY 日志 | T1 | 1.5 人天 |
| T4 | 用户域：注册/登录/登出/刷新/轮转/锁定/密码 + V2、V3 迁移脚本 + UserErrorCode | T2, T3 | 3 人天 |
| T5 | 家庭域：家庭 CRUD / 添加孩子 / 成员管理 + 数据级校验 | T4 | 2 人天 |
| T6 | OAuth2 扩展点：AuthProvider 接口 + user_oauth_binding 表结构说明 | T4 | 0.5 人天 |
| T7 | 集成测试：RestAssured 套件 + Testcontainers + 权限矩阵 + 异常场景 | T4, T5 | 2.5 人天 |
| T8 | API 文档（SpringDoc 认证部分注解）+ README 更新 + DoD 验证收尾 | T7 | 0.5 人天 |

合计约 **14 人天**，1 人投入 ≈ 2 周（08-31 ~ 09-13），与 roadmap 排期一致；关键路径：T1 → T2 → T4 → T7。

---

## 15. 验收标准（DoD，与 roadmap 一致并细化）

- [ ] 登录/登出/刷新 API 的 RestAssured 集成测试全绿，覆盖异常场景：错误凭证、过期令牌、篡改令牌、refresh 重用、锁定
- [ ] 未认证请求访问受保护接口一律 100003（白名单除外），集成用例固化
- [ ] 权限矩阵参数化测试通过：CHILD 访问全部家长专属接口返回 100004；越权数据访问返回 100004
- [ ] 用户/家庭模块单测覆盖率 ≥ 80%（JaCoCo 门禁）；`mvn clean verify` 全部门禁保持绿
- [ ] Docker 就绪后 Testcontainers PG 套件全绿；`docs/version-matrix.md` postgresql 行 ⚠️ → ✅（关闭 M0 遗留）
- [ ] 审计动作全部落 `audit_log`（集成测试断言关键动作行存在）；安全日志按 §9.2 表输出至 SECURITY logger
- [ ] EncryptionAtRestIT 通过：库中无明文手机号，解密往返一致；全仓库敏感信息扫描零命中
- [ ] OpenAPI 认证部分文档产出（本地 Swagger UI 可查全部 M1 端点与错误码说明）
- [ ] 首次改密强制（mcp）、登录锁定、refresh 轮转三项安全机制均有专项测试

---

## 16. 风险与遗留事项

| # | 风险/事项 | 影响 | 应对 |
|---|---|---|---|
| R1 | 本机 Docker 未就绪，Testcontainers 套件被跳过 | M0 PG 验证 ⚠️ 无法关闭、真实方言差异延后暴露 | M1 第一优先级修复 Docker 环境；托底机制已内置（§12.2）；H2 切片测试保底 |
| R2 | Boot 4 + Security 7 + RestAssured/Testcontainers 组合兼容风险 | T2/T7 返工 | spike 前置（T1），预案见 §13 |
| R3 | 注册无短信验证码，手机号持有不验证 | 抢注/垃圾注册 | 已知限制文档化；M5 notify 接入验证码后闭环；锁定与限流（M3）兜底 |
| R4 | JWT 密钥泄露将导致伪造令牌 | 认证体系失效 | 密钥仅环境变量注入、各环境独立；轮换预案文档化（§8.3 同思路适用于 JWT_SECRET） |
| R5 | 鸿蒙端令牌存储不当导致泄露 | refresh 被窃取 | 端上指引写入 API 文档：令牌存设备安全存储（如 HUKS），不入普通文件 |
| R6 | 单家庭单家长是 M1 简化 | 离异/双家长场景暂不支持 | 表结构已按多成员容纳（family_member），后续扩展不改核心模型 |

遗留至后续阶段：短信验证码（M5）、忘记密码自助重置（M5）、OAuth2 provider 实现（待定）、审计查询 API（待定）、邀请码加入家庭（待定）、限流专项（M3）。

---

## 附录 A：M1 新增依赖基线

| 依赖 | 目标版本 | scope | 说明 |
|---|---|---|---|
| spring-boot-starter-security | 随 Boot 4.1.0 | compile | 认证授权框架 |
| spring-security-oauth2-jose | 随 Boot BOM | compile | JWT 签发/校验（Nimbus） |
| spring-boot-testcontainers | 随 Boot 4.1.0 | test | `@ServiceConnection` |
| org.testcontainers:testcontainers-postgresql / testcontainers-junit-jupiter | 随 Boot BOM（**2.0.5**，spike S4 锁定） | test | PG 容器化集成测试；**2.x 工件更名**（原 postgresql / junit-jupiter），`PostgreSQLContainer` 不再是泛型类 |
| io.rest-assured:rest-assured | `rest-assured.version`（**6.0.1**，spike S3 锁定） | test | API 集成测试；不在 Boot BOM 管理范围，显式版本属性 |

## 附录 B：权限矩阵（PermissionMatrixIT 的基准）

| # | 端点 | 匿名 | CHILD | 本家庭 PARENT | 跨家庭 PARENT |
|---|---|---|---|---|---|
| 1 | POST /auth/register | ✅ | —（已认证不适用） | — | — |
| 2 | POST /auth/login | ✅ | — | — | — |
| 3 | POST /auth/refresh | ✅（凭有效 refresh） | — | — | — |
| 4 | POST /auth/logout | 100003 | ✅ | ✅ | ✅ |
| 5 | GET /users/me | 100003 | ✅ | ✅ | ✅ |
| 6 | PUT /users/me | 100003 | ✅ | ✅ | ✅ |
| 7 | POST /users/me/password | 100003 | ✅ | ✅ | ✅ |
| 8 | GET /users/me/family | 100003 | ✅ | ✅ | ✅ |
| 9 | GET /families/{id} | 100003 | ✅（本家庭）/ 100004 | ✅ | 100004 |
| 10 | PUT /families/{id} | 100003 | **100004** | ✅ | 100004 |
| 11 | POST /families/{id}/children | 100003 | **100004** | ✅ | 100004 |
| 12 | GET /families/{id}/members | 100003 | ✅（本家庭）/ 100004 | ✅ | 100004 |
| 13 | PUT /families/{id}/children/{uid} | 100003 | **100004** | ✅ | 100004 |
| 14 | POST /families/{id}/children/{uid}/password-reset | 100003 | **100004** | ✅ | 100004 |
| 15 | DELETE /families/{id}/members/{uid} | 100003 | **100004** | ✅（受 §6.4 约束） | 100004 |

注：#1–3 为白名单匿名接口；「—」表示该身份场景不适用。孩子调用注册/登录类接口的组合在矩阵测试中以「已认证访问白名单 → 正常放行」语义覆盖。

## 附录 C：核心时序（文本）

**注册 + 首次登录**

```
APP → POST /auth/register {phone,password,nickname,consent}
     GlobalExceptionHandler 兜底校验 → AuthService.register（单事务）
       ├─ UserMapper.insert（phone_hash/phone_encrypted/BCrypt）
       ├─ FamilyMapper.insert（默认家庭名）
       ├─ FamilyMemberMapper.insert（创建者）
       └─ AuditService.record(REGISTER, FAMILY_CREATE)
APP ← 200 {userId, familyId, role:PARENT}
APP → POST /auth/login {identifier,password}
     LoginGuard 锁定检查 → 密码比对 → JwtTokenService.issue → RefreshTokenMapper.insert(hash)
APP ← 200 TokenPair
```

**刷新轮转与重用检测**

```
APP → POST /auth/refresh {refreshToken}
     验签/exp/typ → SHA-256 查 user_refresh_token
     ├─ 有效：旧行 revoked_at=now → 签发新 TokenPair → 新行落库
     └─ 已作废：SECURITY ERROR（TOKEN_REUSE_DETECTED）→ 吊销该用户全部令牌
APP ← 200 新 TokenPair ｜ 100003
```

**家长添加孩子**

```
PARENT → POST /families/{id}/children {username,password,nickname}
     @PreAuthorize PARENT → FamilyAccessChecker.requireMember
     AuthService 同事务：insert app_user(CHILD, mcp=true, consented_by=家长)
                        + insert family_member + AuditService.record(CHILD_CREATE)
PARENT ← 200 {childUserId, …}
（孩子首次登录 → mcp=true → 仅可改密 → 改密后正常使用）
```

---
*本设计经评审通过后作为 M1 开发基线；实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*
