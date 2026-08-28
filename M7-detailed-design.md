# M7 部署与发布 — 详细设计文档

> 上游依据：`mission.md`、`tech-stack.md`、`code-style-guide.md`、`roadmap.md`（M7 章节）、`M0-detailed-design.md`、`M1-detailed-design.md`、`M3-detailed-design.md`、`M4-detailed-design.md`、`M5-detailed-design.md`、`M6-detailed-design.md`、`docs/version-matrix.md`
> 文档版本：v1.0（2026-08-28，M7 开发基线）
> 适用范围：M7 阶段（第 18–19 周，12-14 ~ 12-27），GA（1.0 正式发布）前的最后一个里程碑
> M6 基线：`mvn clean verify` 全绿（Checkstyle/PMD/SpotBugs 0 违规，JaCoCo ≥ 80% BUNDLE）；安全测试套件 8/8 通过、WireMock 降级专项 4/4 通过；OWASP 自查 / 认证加密审查 / 数据合规 / 渗透机制四份报告齐套（`docs/security/`）；JMeter 压测计划与运行说明就绪（`scripts/jmeter/`）；云效流水线四阶段（secret-scan → build-and-verify → code-inspection → archive）就绪（D48）；迁移 V1–V9 就绪；幂等键（M3）/ 限流熔断（M3/M4）/ 审计（M1）/ 日志脱敏（M0/M6）全部就绪

---

## 1. 概述

### 1.1 目标

M7 是 GA 前最后一个里程碑：在 M0–M6 交付的全部功能与加固基线之上，完成 mission「部署约束 / 可靠性约束 / 文档约束」的全部收口项，把可运行服务转化为**阿里云生产环境可运营的 1.0 发布**，交付：

- **容器化**：多阶段 Docker 镜像构建、JVM 参数与 GC 落地（承接 M3 D22/D23）、容器健康检查
- **阿里云资源**：ECS / RDS（PostgreSQL 18）/ SLB / OSS / 容器镜像服务（ACR）就绪，网络与安全组隔离
- **环境隔离**：开发 / 测试 / 生产三套环境与配置管理（环境变量注入 + 密钥托管，零硬编码）
- **CI/CD 与发布**：构建 → 测试 → 镜像推送 → 部署全链路；灰度发布（金丝雀）与一键回滚；蓝绿部署验证零停机
- **监控告警**：Actuator 指标接入阿里云 ARMS、SLS 结构化日志接入（含脱敏验证）、告警规则基线、可用性 ≥ 99.5%
- **备份容灾**：RDS 每日增量 + 每周全量备份策略生效，恢复演练一次
- **文档齐套**：API 文档 / 部署文档 / 运维手册 / 开发指南 / 安全操作手册五件套评审归档

### 1.2 范围（In Scope）

- Docker 镜像构建：多阶段 Dockerfile、`.dockerignore`、JVM 参数（ZGC + 堆内存）、非 root 运行、分层镜像
- 阿里云资源：ECS ×2（跨可用区）、RDS PostgreSQL 18（主备）、SLB（HTTPS/TLS 1.3 终结）、OSS（日志/备份归档）、ACR（镜像托管）
- 环境隔离：dev / test / prod 三套配置与隔离（`SPRING_PROFILES_ACTIVE` 切换 + 环境变量注入）
- CI/CD：`yunxiao-pipeline.yml` 扩展（镜像构建推送 + 部署阶段）；灰度发布与一键回滚；蓝绿部署验证
- 监控告警：ARMS 接入、SLS 日志采集与脱敏验证、告警规则基线、核心接口可用性监控
- 备份容灾：RDS 自动备份策略生效、恢复演练、多可用区高可用
- 文档：五件套（API / 部署 / 运维 / 开发指南 / 安全操作手册）

### 1.3 非目标（Out of Scope）

| 事项 | 归属阶段 |
|---|---|
| 真实 LLM / ASR / TTS provider 接入与 ≥95% 准确率复跑 | provider 接入后（M4 R2 口径，不阻塞 M7） |
| 真实鸿蒙 Push / 短信 / 邮件通道接入 | 通道拍板后（M5 D39，`NOTIFY_PUSH_ENABLED` 生产默认 false） |
| 微服务拆分 / Kubernetes 编排 | 本版本单体 + 垂直扩展（mission「可扩展微服务架构」为演进方向，非 M7 目标） |
| 新业务功能、新数据库表 | 无（M7 不新增业务表，无 V10 迁移） |
| SonarQube 独立部署 | 已由 D48 改为云效代码检测替代（M6） |
| 多地域（跨 Region）容灾 | 本版本单 Region 多可用区；跨 Region 属后续演进 |

---

## 2. 决策记录（已确认）

> 续 M6 D55 编号。D56–D62 为 M7 设计基线决策，均承接 mission「部署约束 / 可靠性约束」与 M0/M3/M6 挂起至 M7 的遗留项。

| # | 决策点 | 结论 | 备注 |
|---|---|---|---|
| D56 | Docker 镜像形态 | **多阶段构建**：builder 用 `maven:3.9.16-eclipse-temurin-25`（`mvn clean verify` 全门禁），runtime 用 `eclipse-temurin:25-jre`（glibc，支持 ZGC 与虚拟线程）；非 root 用户运行；启用 Spring Boot layered jar（分层镜像加速 ACR 推送与部署）；`HEALTHCHECK` 指向 `/actuator/health` 探针 | §4；基础镜像 tag 经 spike 锁定写入 `docs/version-matrix.md`（S 系列惯例） |
| D57 | 部署拓扑与高可用 | **单 Region 多可用区**：SLB（HTTPS/TLS 1.3 终结，`forward-headers-strategy: native` 还原代理头）→ ECS ×2（跨可用区，Docker 运行单体）→ RDS PostgreSQL 18（主备，同 VPC）；ACR 托管镜像、OSS 归档日志/备份。可用性 ≥ 99.5% 由「双可用区 + SLB 健康检查自动摘除 + 一键回滚」共同保障 | §5；对齐 mission「阿里云基础设施 + 99.5% 可用性 + 垂直扩展」 |
| D58 | 环境隔离与配置管理 | **三环境隔离**（dev/test/prod，独立 ECS/RDS/ACR namespace）；配置走**环境变量注入**（`application.yml` 既有 `${...}` 占位）+ 云效流水线「变量组/凭据库」托管敏感值；**不引入 Spring Cloud Config / Nacos**（单体 + 小团队，配置量小，环境变量 + 云效凭据库已满足「零硬编码」红线） | §6；承接 M0 §7 配置分层与 M0 §8「敏感值环境变量注入」 |
| D59 | 发布策略 | **金丝雀灰度 + 蓝绿保底 + 一键回滚**：灰度 = SLB 后端权重分流（新版本 5% → 观察 ARMS 指标 → 50% → 100%），任一阈值（错误率/5xx/P95 超限）即中止并一键回滚；蓝绿 = 保留一套 standby 环境，切换 SLB 指向实现零停机；回滚 = 镜像 tag 回切上一稳定版 + 权重归零 | §7；对齐 mission「灰度发布和回滚机制」与 tech-stack「蓝绿部署」 |
| D60 | 监控告警形态 | **Actuator 指标 → ARMS**（Java agent 免侵入 + 关键业务指标自定义埋点）；**SLS 采集 MaskingJsonEncoder 脱敏 JSON 日志 + GC 日志**（`-Xlog:gc*`，承接 M3 D23）；告警规则基线：核心接口 P95 > 500ms / 错误率 > 0.5% / 5xx 突增 / 健康检查失败 / 熔断 OPEN / 登录失败激增 / DB 连接池耗尽 | §8；承接 M1 §9.2（SLS 归 M7）、M0 §9.2（JSON 日志为 SLS 打底）、M6（脱敏已验证） |
| D61 | 备份与容灾 | **RDS 自动备份**：每日增量（PITR 日志）+ 每周全量，保留 7 天，备份加密；**恢复演练一次**（恢复到指定时间点 PITR）；主备跨可用区自动故障切换。备份文件定期归档 OSS 冷存 | §9；对齐 mission「每日增量 + 每周全量备份」与「故障转移和灾难恢复」 |
| D62 | 文档五件套形态 | **API 文档**（OpenAPI/springdoc，生产 swagger-ui 关、api-docs JSON 保留）+ **部署文档**（`docs/deploy/`）+ **运维手册**（`docs/ops/`，含备份恢复/回滚 Runbook）+ **开发指南**（`docs/dev/`）+ **安全操作手册**（`docs/security/` 扩展生产安全操作）。五件套评审归档 | §10；对齐 mission「文档五件套」 |

---

## 3. 总体设计

### 3.1 部署拓扑图

```
                互联网 / 鸿蒙 APP
                       │ HTTPS（TLS 1.3，SLB 终结）
                       ▼
            ┌─────────────────────────┐
            │  阿里云 SLB（负载均衡）    │  健康检查 /actuator/health
            │  权重分流 → 灰度/蓝绿     │  异常摘除
            └───────────┬─────────────┘
                        │ VPC 内网 :8080
            ┌───────────┴───────────┐
            │                       │
   ┌────────▼────────┐   ┌─────────▼────────┐
   │ ECS-A（可用区 1） │   │ ECS-B（可用区 2） │   Docker 运行单体
   │  pocket-money    │   │  pocket-money    │   虚拟线程 + ZGC
   └────────┬─────────┘   └─────────┬────────┘
            │  JDBC（内网，安全组白名单）│
            └───────────┬────────────┘
                        ▼
            ┌─────────────────────────┐
            │  RDS PostgreSQL 18（主备）│  每日增量 + 每周全量备份
            └───────────┬─────────────┘   PITR，多可用区主备
                        │ 归档
                        ▼
               ┌────────────────┐   ┌──────────────────┐
               │  OSS（备份/日志） │   │  ACR（容器镜像托管） │
               └────────────────┘   └──────────────────┘

   监控：ECS/应用 → ARMS（指标 + 告警）｜ 日志 → SLS（Logtail 采集）
```

### 3.2 M7 定位：不改业务运行时，交付「容器 + 资源 + 流水线 + 文档」资产

与 M6 一致，M7 **不新增 Controller/Service/迁移**（V1–V9 复用）；产出物是「容器化资产 + 阿里云资源 + 部署流水线 + 监控/备份配置 + 文档」。运行时代码仅在「灰度观测发现缺陷」时做最小修复，并回到 M6 已固化的测试/门禁链路（`mvn clean verify`）验证后再发。

### 3.3 交付物全景（M7 结束时）

```
Dockerfile                              # 【新增】多阶段镜像构建（§4）
.dockerignore                           # 【新增】镜像构建上下文瘦身
config/docker/
└── docker-compose.prod.yml             # 【新增】生产编排参考（ECS Docker 部署基线，§5）

yunxiao-pipeline.yml                    # 【修改】追加镜像构建/推送 + 部署阶段（§7）

docs/deploy/
├── deployment-guide.md                 # 【新增】部署文档（§10）
└── release-runbook.md                  # 【新增】灰度发布 + 一键回滚 Runbook（§7 附录 B）

docs/ops/
├── operations-handbook.md              # 【新增】运维手册（巡检/告警处置，§8/§9）
└── backup-recovery-runbook.md          # 【新增】备份恢复 Runbook（§9）

docs/dev/
└── development-guide.md                # 【新增】开发指南（本地环境/编码/贡献，§10）

docs/security/
└── production-security-operations.md   # 【新增】生产安全操作手册（§10，扩展 M6 既有安全文档）

docs/version-matrix.md                  # 【修改】追加 Docker 基础镜像 tag 锁定（§4）
```

### 3.4 与 M6 基线的衔接

| M6/M3/M0 交付物 | M7 变更 |
|---|---|
| `yunxiao-pipeline.yml`（secret-scan → build-and-verify → code-inspection → archive，D48） | archive 之后**追加** docker-build-push → deploy-test → deploy-prod（灰度）阶段（§7） |
| M3 D22（ZGC）/ D23（堆内存参数 `-XX:MaxRAMPercentage=75.0` 等） | 写入 Dockerfile `JAVA_OPTS`，本地 ZGC 冒烟已有（M3），镜像落地验证（§5） |
| M3 D24（虚拟线程规范）+ `spring.threads.virtual.enabled=true` | 原样保留，容器 CPU 配额按虚拟线程场景核数设定（§5） |
| `application.yml` `${...}` 环境变量占位 + profiles（local/dev/test/prod） | 生产以环境变量注入真实值，prod profile 关闭 Swagger UI、`show-details: when-authorized` 保留（§6） |
| `MaskingJsonEncoder` / `MaskingRules`（M0 §9.2，M6 已验证） | SLS 侧做一次「抽检无明文手机号/密钥」的脱敏落地验证（§8） |
| M6 云效代码检测（D48）+ `scripts/secret-scan.sh` | 部署流水线前置于 secret-scan 阶段保持拦截（§7） |
| `PerformanceDataSeeder` / JMeter（M6） | GA 前在测试环境跑一次 10 TPS 压测作为发布前终验（§7） |
| 迁移 V1–V9（Flyway） | 生产 RDS 首启自动迁移，`baseline-on-migrate: true` 已就绪（§5） |

---

## 4. Docker 镜像构建（roadmap M7 任务 1，D56）

### 4.1 多阶段 Dockerfile 基线

```dockerfile
# ---- Stage 1：构建（含全质量门禁） ----
FROM maven:3.9.16-eclipse-temurin-25 AS builder
WORKDIR /build
COPY pom.xml .
# 依赖层缓存：先拉依赖，源变更不重拉
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
COPY config ./config
RUN mvn -B -ntp clean verify

# ---- Stage 2：运行时 ----
FROM eclipse-temurin:25-jre
# 非 root 运行（mission 安全红线）
RUN groupadd -r pocket && useradd -r -g pocket pocket
WORKDIR /app
COPY --from=builder /build/target/pocket-money-server-*.jar app.jar
USER pocket

# JVM 参数（M3 D22 ZGC / D23 堆内存）：虚拟线程 + ZGC + 容器感知堆 + GC 日志（供 SLS）
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Xlog:gc=info,gc+heap=info:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

# 容器健康检查（对齐 application.yml 已启用 health probes）
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=40s \
    CMD curl -fs http://localhost:8080/actuator/health || exit 1
```

### 4.2 分层镜像与推送优化

- Spring Boot layered jar（`spring-boot-maven-plugin` 分层）：依赖层与业务层分离，ACR 增量推送与 ECS 增量拉取只传变更层，缩短灰度窗口。
- `.dockerignore`：排除 `target/`、`.git/`、`docs/`、`scripts/jmeter/`、本地 IDE 文件，缩小构建上下文。
- 基础镜像 tag（`maven:3.9.16-eclipse-temurin-25`、`eclipse-temurin:25-jre`）经 spike 锁定后写入 `docs/version-matrix.md`，杜绝「latest」漂移。

### 4.3 健康检查

- 运行态探针：`/actuator/health/liveness`（存活，容器不重启即真）、`/actuator/health/readiness`（就绪，依赖 RDS/Flyway 就绪后转 UP，未就绪期间 SLB 不引入流量）。
- `application.yml` 已启用 `management.endpoint.health.probes.enabled=true`，M7 只需在 SLB 后端健康检查与 ECS 容器编排侧配置指向 readiness 探针。

---

## 5. 阿里云资源与网络（roadmap M7 任务 2，D57）

### 5.1 资源清单

| 资源 | 规格/配置 | 用途 |
|---|---|---|
| ECS ×2 | 跨可用区（同 VPC），2 vCPU / 4 GB 起步（10 TPS 场景，垂直扩展） | 运行 Docker 单体 |
| SLB | 公网 HTTPS（TLS 1.3 终结），后端 8080，健康检查 `/actuator/health/readiness` | 负载均衡 + 灰度权重分流 |
| RDS PostgreSQL | 18，主备（高可用版，跨可用区） | 生产数据库 |
| ACR | 企业版个人/共享实例，镜像仓库 `pocket-money-server` | 镜像托管 |
| OSS | 标准 + 低频，日志/备份归档桶 | 备份归档、日志留存 |
| 安全组 | 仅 SLB 安全组可访问 ECS:8080；RDS 仅 ECS 安全组白名单；ECS 不暴露公网 | 网络隔离 |

### 5.2 关键配置要点

- **TLS 终结**：SLB 终结 HTTPS（TLS 1.3），ECS 内部明文；`server.forward-headers-strategy: native`（M1 §8.4 已配）还原客户端 scheme/client-ip，供审计与安全日志使用。
- **RDS 首启迁移**：Flyway `baseline-on-migrate: true`（application.yml 已配），V1–V9 自动执行；生产 RDS 账号仅授予 DML + 迁移所需 DDL（最小权限）。
- **连接池对齐**：HikariCP `maximum-pool-size: 10`（M0 §8.4 基线）与 RDS 连接数上限匹配，`connection-timeout: 5000` 保证依赖故障快速失败（对齐 M4 降级语义）。
- **垂直扩展**：单体按需升配 ECS（虚拟线程多核利用），不引入横向扩容复杂度（D57）。

---

## 6. 环境隔离与配置管理（roadmap M7 任务 3，D58）

### 6.1 三环境隔离

| 环境 | Profile | 数据 | 用途 | Swagger UI |
|---|---|---|---|---|
| dev | `dev` | 独立 RDS（或本地） | 开发联调 | 开 |
| test | `test` | 独立 RDS + `PerformanceDataSeeder` 种子 | 集成/压测/灰度前终验 | 开 |
| prod | `prod` | 生产 RDS（主备） | 线上 | 关（api-docs JSON 保留） |

- `application-prod.yml` 已配：`springdoc.swagger-ui.enabled: false`、`logging.level.root: info`、数据源无默认值（缺失即启动失败）。
- 三环境独立 ECS/RDS/ACR namespace，VPC 网络隔离，测试环境数据不得复用于生产（对齐 M6 §10.3 测试数据脱敏）。

### 6.2 配置与密钥管理

- **非敏感配置**：环境变量注入（`application.yml` `${...}` 已占位），云效流水线变量组分环境维护。
- **敏感配置（密钥）**：`JWT_SECRET` / `DATA_ENCRYPTION_KEY` / `DB_PASSWORD` / OSS/ARMS/SLS 凭据 —— 云效**凭据库**托管，仅在流水线部署阶段注入容器环境变量，**不落代码仓库、不入镜像**（镜像内无密钥，符合 mission 零硬编码红线）。
- **防泄漏**：`scripts/secret-scan.sh` 在流水线阶段 1 持续拦截（已就绪）；M6 `SensitiveDataExposureTest` 保证出参/日志无密钥。
- **不引入配置中心**（D58）：单体 + 环境变量 + 云效凭据库已满足需求，避免为小配置量引入 Nacos/Spring Cloud Config 的运维成本。

---

## 7. CI/CD 与灰度发布/回滚（roadmap M7 任务 4，D59）

### 7.1 云效流水线扩展（在 archive 之后追加）

```yaml
    # ---- 阶段 5：镜像构建与推送（ACR） ----
    - name: docker-build-push
      jobs:
        - name: build-and-push
          steps:
            - run: docker build -t ${ACR_NAMESPACE}/pocket-money-server:${BUILD_TAG} .
            - run: docker push ${ACR_NAMESPACE}/pocket-money-server:${BUILD_TAG}

    # ---- 阶段 6：测试环境部署 + 冒烟 ----
    - name: deploy-test
      jobs:
        - name: deploy-test-smoke
          steps:
            - run: ssh deploy@${TEST_ECS} "docker pull ... && docker run -d ..."
            - run: 健康检查 + 登录/幂等冒烟（复用 RestAssured smoke 或 curl 断言 code=0）

    # ---- 阶段 7：生产灰度发布（金丝雀） ----
    - name: deploy-prod-canary
      jobs:
        - name: canary-release
          steps:
            - run: 发布新版本至 ECS-A，SLB 权重 5% 分流
            - run: 观察 ARMS 指标（P95/错误率/5xx）满 N 分钟
            - run: 达标 → 50% → 100%；超限 → 一键回滚（权重归零 + tag 回切）
```

- **阶段门禁**：secret-scan / build-and-verify（含 JaCoCo + Checkstyle/PMD/SpotBugs + 云效代码检测 D48）任一失败即阻断，镜像不得推送。
- **发布前终验**：测试环境跑一次 JMeter 10 TPS 压测（复用 `scripts/jmeter/`，M6 交付）作为 GA 前终验，报告存档 `docs/performance/`。

### 7.2 灰度发布（金丝雀）

1. 新版本镜像推 ACR；ECS-A 拉新镜像起新容器，SLB 后端权重调至 5%（旧容器 95%）。
2. 观察窗口（建议 ≥ 15 分钟或 ≥ 1000 请求）：核心接口 P95 ≤ 500ms、错误率 ≤ 0.5%、无 5xx 突增、无连接泄漏（HikariCP 活跃连接回落）。
3. 达标逐步放大 5% → 50% → 100%；不达标立即中止（权重归零 + 回滚）。
4. 灰度期间新旧版本**共享同一 RDS**（V1–V9 迁移已兼容旧版本），确保数据一致。

### 7.3 蓝绿部署与一键回滚

- **蓝绿**：ECS-A/B 互为蓝绿，发布时 standby 环境部署新版本、预热、SLB 一键切流，实现零停机切换；异常立即切回。
- **一键回滚**：云效「回滚」任务 = 重发上一稳定镜像 tag + SLB 权重归一（全量回旧），RDS 不做反向迁移（向前兼容，V1–V9 单向）。
- **回滚触发条件**（对齐 D59）：错误率 > 0.5%、5xx 突增、P95 持续超 500ms、健康检查失败、关键业务异常（登录失败激增 / 资金写错误）。

---

## 8. 监控告警（roadmap M7 任务 5，D60）

### 8.1 ARMS 接入

- 应用指标：Java agent 免侵入（CPU/内存/GC/线程/HTTP RT/QPS）+ Micrometer 自定义埋点（资金写成功率、幂等命中率、熔断状态、AI 降级率）。
- 核心接口可用性监控：`/dashboard` `/transactions` `/deposits` `/withdrawals` `/ai/chat` 等按 ARMS 自定义监控（P95 / 错误率 / QPS）。

### 8.2 SLS 日志接入（含脱敏验证）

- Logback 已输出 JSON 结构化日志（`MaskingJsonEncoder` 脱敏，M0 §9.2 / M6 已验证）→ Logtail 采集 → SLS。
- **脱敏落地验证（M7 专项）**：SLS 侧抽检 auth/money/rule/ai/notify 各链路日志，断言无明文手机号/密码/密钥/身份证（复用 M6 `MaskingRules` 四类规则口径），抽检记录存档 `docs/security/`。
- GC 日志（`-Xlog:gc*`，D23）单独采集至 SLS，供停顿与内存趋势分析。

### 8.3 告警规则基线

| 告警 | 触发 | 级别 |
|---|---|---|
| 核心接口 P95 > 500ms | 连续 N 分钟 | P2 |
| 错误率 > 0.5% / 5xx 突增 | 阈值 + 环比 | P1 |
| 健康检查失败 / 实例摘除 | 即时 | P1 |
| 熔断 OPEN / AI 降级率异常 | 即时 | P2 |
| 登录失败激增（疑似爆破） | 阈值 + 环比 | P1（安全） |
| DB 连接池耗尽 / RDS 主备切换 | 即时 | P1 |

- 告警链路：ARMS/云监控 → 短信/邮件/IM 群（告警链路演练见 §14 DoD）。
- 可用性 ≥ 99.5%：双可用区 + SLB 健康检查自动摘除 + 一键回滚 + 灰度观察共同保障；月度可用性报告存档。

---

## 9. 备份与容灾（roadmap M7 任务 6，D61）

### 9.1 备份策略

| 类型 | 频率 | 保留 | 说明 |
|---|---|---|---|
| 增量（PITR） | 实时（WAL 日志） | 随全量 | 支持恢复到指定时间点 |
| 全量 | 每周 | 7 天 | RDS 自动备份 |
| 归档 | 每周 | 长期 | 备份文件转 OSS 低频/冷存，异地留档 |

- 备份加密开启；备份文件由 OSS 生命周期策略归档。
- 关键操作审计日志（`audit_log`，M1）随数据库一起备份，满足合规追溯。

### 9.2 恢复演练（M7 执行一次）

- 演练目标：从 RDS 备份**恢复到指定时间点（PITR）**，验证备份可用性与恢复流程。
- 演练环境：独立演练实例（非生产主库），恢复后校验关键表行数与业务一致性。
- 产出：`docs/ops/backup-recovery-runbook.md` + 演练记录（结论与处置项），满足 mission「灾难恢复演练」。

### 9.3 高可用

- RDS 主备跨可用区，主库故障自动切换；ECS 双可用区 + SLB 自动摘除异常实例。

---

## 10. 文档齐套（roadmap M7 任务 7，D62）

| 文档 | 落点 | 内容基线 |
|---|---|---|
| API 文档 | OpenAPI/springdoc（M1 已就绪）+ `docs/` 索引 | 15+ 端点 + bearerAuth 安全方案；生产 swagger-ui 关、api-docs JSON 保留 |
| 部署文档 | `docs/deploy/deployment-guide.md` | 阿里云资源创建、镜像构建、流水线部署步骤、环境变量清单 |
| 运维手册 | `docs/ops/operations-handbook.md` | 日常巡检、告警处置、备份恢复、回滚 Runbook |
| 开发指南 | `docs/dev/development-guide.md` | 本地环境（Docker Compose + 启动）、编码规范（`code-style-guide.md`）、贡献流程 |
| 安全操作手册 | `docs/security/production-security-operations.md` | 生产密钥轮换、安全事件响应、季度渗透测试衔接（M6 D53）、数据删除/合规操作 |

- 五件套评审归档（M7 DoD）；文档变更同步更新（mission「文档约束」）。

---

## 11. 配置增量

- **运行时配置：无新增**（M7 不新增业务开关；所有部署/监控配置走环境变量 + 云效变量组，不入 `application.yml`）。
- **新增环境变量（供运维注入，不入 pom/不入代码）**：`OSS_ENDPOINT` / `OSS_ACCESS_KEY` / `OSS_SECRET_KEY` / `ARMS_*` / `SLS_*`（M0 §8 预告 M7 增加 OSS/ARMS/SLS 凭据变量）。
- **新增文件**：`Dockerfile`、`.dockerignore`、`config/docker/docker-compose.prod.yml`、`docs/deploy/`、`docs/ops/`、`docs/dev/`、`docs/security/production-security-operations.md`。
- **修改文件**：`yunxiao-pipeline.yml`（追加 docker-build-push / deploy-test / deploy-prod-canary 阶段）、`docs/version-matrix.md`（追加 Docker 基础镜像 tag 锁定）。
- **依赖增量：无**（Docker 构建外置，不引入 pom 依赖；`spring-boot-maven-plugin` 分层配置 Boot 4 默认已启用）。
- **迁移增量：无 V10**（M7 不新增业务表）。

---

## 12. 任务分解（WBS）与工作量

| # | 任务 | 前置 | 预估 |
|---|---|---|---|
| T1 | Docker 镜像构建：Dockerfile + `.dockerignore` + 分层 jar + JVM 参数（D22/D23 落地）+ 健康检查 + 基础镜像 spike 锁定 | — | 2 人天 |
| T2 | 阿里云资源就绪：ECS/SLB/RDS/ACR/OSS + 安全组 + 网络 + RDS 首启 Flyway 迁移 | T1 | 2 人天 |
| T3 | 环境隔离 + 配置管理：dev/test/prod 三环境 + 环境变量注入 + 云效凭据库 | T2 | 1 人天 |
| T4 | CI/CD 扩展 + 发布：镜像构建推送 + 测试部署冒烟 + 灰度/蓝绿 + 一键回滚（`yunxiao-pipeline.yml` + Runbook） | T2 | 2.5 人天 |
| T5 | 监控告警：ARMS 接入 + SLS 采集与脱敏验证 + 告警规则 + 可用性监控 | T2 | 2 人天 |
| T6 | 备份容灾：RDS 备份策略生效 + 恢复演练一次 | T2 | 1.5 人天 |
| T7 | 文档五件套 + 评审归档 + DoD 收尾 | T3–T6 | 2 人天 |

合计约 **13 人天**。roadmap 排期 2 周（10 工作日/人）：

- **2 人投入**：约 6.5 人天/人，舒适（发布与安全/资金链路建议双人评审）。
- **1 人投入**：13 人天 > 10 人天，略超排期；候选裁剪：蓝绿降为「金丝雀为主 + 蓝绿仅文档化」、OSS 归档延后、文档五件套中运维/开发指南并行续补。

关键路径：T1 → T2 →（T3/T4/T5/T6 并行）→ T7 收尾闸门。

---

## 13. 验收标准（DoD，与 roadmap M7 一致并细化）

- [ ] 生产环境健康检查通过；灰度发布（金丝雀 5%→50%→100%）→ 全量流程演练成功，任一阈值超限可一键回滚（回滚演练通过）
- [ ] 备份恢复演练成功：从 RDS 备份恢复到指定时间点（PITR），关键表校验通过
- [ ] 可用性监控覆盖核心接口（P95/错误率/QPS），告警链路演练通过（触发 → 通知 → 处置）
- [ ] 五类文档（API / 部署 / 运维 / 开发指南 / 安全操作手册）评审归档
- [ ] SLS 日志脱敏落地验证通过（无明文手机号/密码/密钥/身份证）
- [ ] GA 前测试环境 10 TPS 压测终验通过（P95 ≤ 500ms、错误率 ≤ 0.5%，复用 M6 JMeter）
- [ ] 零硬编码复核通过：`scripts/secret-scan.sh` 拦截 + 凭据库托管，无密钥入镜像/入仓库
- [ ] 三环境隔离验证通过（dev/test/prod 配置与数据互不串扰）

---

## 附录 A：阿里云资源清单

| 资源 | 数量 | 关键配置 | 备注 |
|---|---|---|---|
| ECS | 2 | 跨可用区，2 vCPU/4 GB 起步 | 运行 Docker 单体 |
| SLB | 1 | HTTPS TLS 1.3，后端 8080 | 健康检查 + 灰度权重 |
| RDS PostgreSQL 18 | 1（主备） | 高可用版，跨可用区 | 每日增量 + 每周全量备份 |
| ACR | 1 | 镜像仓库 `pocket-money-server` | 镜像托管 |
| OSS | 2 桶 | 日志桶 / 备份桶 | 归档 + 生命周期策略 |

## 附录 B：发布与回滚 Runbook 摘要

**发布（金丝雀）**：构建门禁 → 镜像推 ACR → ECS-A 部署新版本 → SLB 权重 5% → 观察 ARMS（P95/错误率/5xx）→ 达标 50% → 100% → ECS-B 滚动更新 → 归档发布记录。

**回滚（一键）**：云效「回滚」任务 → 重发上一稳定 tag → SLB 权重归零新版本/全量回旧 → 验证健康检查 → 归档回滚记录 + 问题处置单。

**触发条件**：错误率 > 0.5% / 5xx 突增 / P95 持续 > 500ms / 健康检查失败 / 登录失败激增 / 资金写错误。

## 附录 C：与 roadmap M7 任务/DoD 映射

| roadmap M7 条目 | 设计章节 |
|---|---|
| 任务 1 Docker 镜像构建（多阶段/JVM/GC/健康检查） | §4 / §5（D56） |
| 任务 2 阿里云资源就绪（ECS/RDS/SLB/OSS/ACR） | §5（D57） |
| 任务 3 环境隔离 + 配置管理 | §6（D58） |
| 任务 4 CI/CD + 灰度/回滚/蓝绿 | §7（D59） |
| 任务 5 监控告警（Actuator/ARMS/SLS/脱敏） | §8（D60） |
| 任务 6 备份容灾 + 恢复演练 | §9（D61） |
| 任务 7 文档齐套 | §10（D62） |
| DoD 1 健康检查 + 灰度演练 | §7 / §13 |
| DoD 2 备份恢复演练 | §9 / §13 |
| DoD 3 可用性监控 + 告警链路演练 | §8 / §13 |
| DoD 4 五类文档评审归档 | §10 / §13 |

## 附录 D：文档变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-28 | M7 开发基线初稿：镜像形态（D56）、部署拓扑与高可用（D57）、环境隔离与配置（D58）、灰度/蓝绿/回滚（D59）、监控告警（D60）、备份容灾（D61）、文档五件套（D62）；部署拓扑图、WBS 与 DoD |

---

*本设计作为 M7 开发基线；实现过程中如与 mission/tech-stack 冲突，以上游文档为准并回改本设计。*
