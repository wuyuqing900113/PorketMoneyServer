# 部署文档（M7）

| 项 | 值 |
|---|---|
| 里程碑 | M7（部署与发布） |
| 依据 | `M7-detailed-design.md` §4–§7（D56–D59）；mission「Docker + 阿里云 / 灰度回滚 / 环境隔离」 |

---

## 1. 部署架构

SLB（HTTPS/TLS 1.3 终结，健康检查）→ ECS ×2（跨可用区，Docker 运行单体，ZGC + 虚拟线程）→ RDS PostgreSQL 18（主备）；ACR 托管镜像，OSS 归档备份/日志。详见 `M7-detailed-design.md` §3.1 拓扑图。

## 2. 阿里云资源创建（一次性）

| 资源 | 规格 | 关键配置 |
|---|---|---|
| VPC / 交换机 | 2 可用区 | ECS/RDS 同 VPC |
| 安全组 | ECS 安全组 / RDS 安全组 | ECS:8080 仅放行 SLB 来源；RDS 仅放行 ECS 安全组；ECS 不暴露公网 |
| ECS ×2 | 2 vCPU/4 GB 起步 | 跨可用区，安装 Docker + compose |
| SLB | HTTPS 监听 443 | TLS 1.3 证书；后端 8080；健康检查 `/actuator/health/readiness`；后端权重用于灰度 |
| RDS PostgreSQL | 18 高可用版 | 主备跨可用区；开启自动备份（每日增量 + 每周全量）、备份加密；内网地址 |
| ACR | 镜像仓库 `pocket-money-server` | 命名空间按环境隔离（dev/test/prod） |
| OSS | 日志桶 / 备份桶 | 生命周期策略（低频/冷存归档） |
| ARMS / SLS | 应用监控 / 日志服务 | 接入见 `../ops/operations-handbook.md` |

## 3. 镜像构建与推送

```bash
# 本地/构建机（需 Docker；镜像内 builder 会再跑 mvn clean verify 全门禁）
docker build -t <ACR_NAMESPACE>/pocket-money-server:<TAG> .

# 推送 ACR
docker login <ACR_REGISTRY>
docker push <ACR_NAMESPACE>/pocket-money-server:<TAG>
```

镜像要点（`Dockerfile`）：多阶段构建；runtime 为 `eclipse-temurin:25-jre`；非 root 用户 `pocket`；ZGC + 容器感知堆（`-XX:MaxRAMPercentage=75.0`）；`HEALTHCHECK` 指向 `/actuator/health`。

## 4. 环境变量清单（生产必填）

敏感值经云效凭据库注入容器环境变量，**禁止入镜像/入仓库**（`config/docker/.env.prod.example` 为模板）。

| 变量 | 必填 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | 固定 `prod`（关 Swagger UI、info 日志） |
| `DB_URL` | ✅ | RDS 内网 JDBC：`jdbc:postgresql://<rds-host>:5432/<db>` |
| `DB_USERNAME` / `DB_PASSWORD` | ✅ | RDS 应用账号（最小权限） |
| `JWT_SECRET` | ✅ | HS256 密钥 Base64，≥32 字节随机；三环境互不相同 |
| `DATA_ENCRYPTION_KEY` | ✅ | AES-256 密钥 Base64，32 字节随机；轮换见安全操作手册 |
| `AI_MOCK` | 可选 | 生产 `false`（真实大模型 DeepSeek，D67）；`true` 为进程内桩 `StubChatPort`（测试/演示） |
| `SPRING_AI_MODEL_CHAT` | 可选 | 生产 `openai`（启用 Spring AI ChatModel 自动装配，指向 DeepSeek）；`none` 关闭 |
| `DEEPSEEK_API_KEY` | 启用时必填 | DeepSeek API Key（云效凭据库托管）；`AI_MOCK=false` 时缺失即启动失败（fail-fast） |
| `DEEPSEEK_BASE_URL` | 可选 | 默认 `https://api.deepseek.com`（OpenAI 兼容协议 base-url） |
| `DEEPSEEK_MODEL` | 可选 | 默认 `deepseek-v4-pro` |
| `NOTIFY_PUSH_ENABLED` | 可选 | 生产 `true`（鸿蒙 Push Kit，D68）；`false` 为 `NoopPushPort` 桩 |
| `HARMONY_PUSH_APP_ID` | 启用时必填 | 鸿蒙 Push Kit 应用 ID（AppGallery Connect） |
| `HARMONY_PUSH_CLIENT_ID` | 启用时必填 | 鸿蒙 Push Kit OAuth client_id（云效凭据库托管） |
| `HARMONY_PUSH_CLIENT_SECRET` | 启用时必填 | 鸿蒙 Push Kit OAuth client_secret（云效凭据库托管） |
| `HARMONY_PUSH_TOKEN_URL` | 可选 | 默认华为官方 OAuth2 端点（专有云可覆盖） |
| `HARMONY_PUSH_BASE_URL` | 可选 | 默认 `https://push-api.cloud.huawei.com` |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | 可选 | 默认 PT15M / P14D |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_LOCK_DURATION` | 可选 | 默认 5 次 / PT15M |
| `RATE_LIMIT_FOR_PERIOD` / `RATE_LIMIT_REFRESH_PERIOD` | 可选 | 写接口限流，默认 10/PT1M |
| `AI_TIMEOUT` / `AI_CB_*` / `AI_RETRY_MAX_ATTEMPTS` | 可选 | AI 韧性参数（M4） |

> 全部键名以 `src/main/resources/application.yml` `${...}` 占位为准；生产无默认值项缺失即启动失败（fail-fast）。

## 5. 三环境隔离

| 环境 | Profile | 数据 | ACR namespace |
|---|---|---|---|
| dev | `dev` | 独立库 | dev |
| test | `test` | 独立库 + `PerformanceDataSeeder` 种子 | test |
| prod | `prod` | RDS 主备 | prod |

三环境独立 ECS/RDS/凭据，VPC 隔离；测试数据不得复用于生产（M6 §10.3）。

## 6. 部署步骤（云效流水线）

流水线 `yunxiao-pipeline.yml` 阶段 5–7 自动化：

1. **docker-build-push**：构建镜像并推 ACR（`<BUILD_TAG>` + `latest`）。
2. **deploy-test**：测试 ECS `docker compose -f config/docker/docker-compose.prod.yml up -d` → `wait-health.sh` 等 readiness UP → 冒烟 → JMeter 10 TPS 终验。
3. **deploy-prod-canary**（人工审批后）：金丝雀 5%→50%→100%，每步 `observe-canary.sh` 观察 ARMS；超限自动/人工回滚（`rollback.sh`）。

ECS 单机编排参考 `config/docker/docker-compose.prod.yml`（含资源限制、健康检查、日志卷）。发布/回滚操作细则见 `release-runbook.md`。

## 7. 首次上线检查清单

- [ ] RDS 首启 Flyway V1–V10 迁移成功（启动日志无迁移错误）
- [ ] `/actuator/health` 双实例 UP，SLB 健康检查全绿
- [ ] 生产 Swagger UI 关闭（`/swagger-ui` 不可达），`api-docs` 仅内网可达
- [ ] 密钥全部来自凭据库，`secret-scan` 无命中
- [ ] SLS 日志可查且脱敏抽检通过
- [ ] ARMS 指标上报，告警规则启用并演练
- [ ] RDS 备份策略生效，恢复演练完成（`../ops/backup-recovery-runbook.md`）
