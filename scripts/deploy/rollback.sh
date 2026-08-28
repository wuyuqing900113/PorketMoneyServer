#!/usr/bin/env bash
# ============================================================
# 一键回滚（M7 设计 §7.3，D59）
# 将两台 ECS 回退到上一稳定镜像 tag，并把 SLB 权重全量切回旧版本，
# 完成后做健康检查。数据库不做反向迁移（V1–V9 向前兼容，单向）。
#
# 用法：rollback.sh <PREV_STABLE_TAG>
# 前置：到两台生产 ECS 的 SSH 免密部署账号；slb-set-weight.sh 同目录可用。
# ============================================================
set -euo pipefail

PREV_TAG="${1:-}"
PROD_ECS_A="${PROD_ECS_A:-}"
PROD_ECS_B="${PROD_ECS_B:-}"
LB_ID="${SLB_ID:-}"
ECS_A_ID="${ECS_A_INSTANCE:-}"
ECS_B_ID="${ECS_B_INSTANCE:-}"

if [ -z "$PREV_TAG" ]; then
  echo "[rollback] 用法: rollback.sh <PREV_STABLE_TAG>" >&2
  exit 2
fi
if [ -z "$PROD_ECS_A" ] || [ -z "$PROD_ECS_B" ]; then
  echo "[rollback] 请设置 PROD_ECS_A / PROD_ECS_B 环境变量" >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[rollback] 回滚目标 tag：${PREV_TAG}"

# 1) 两台 ECS 拉旧镜像并起容器（蓝绿保底：先 B 后 A，确保随时有健康实例承载流量）
for HOST in "$PROD_ECS_B" "$PROD_ECS_A"; do
  echo "[rollback] → ${HOST} 部署 ${PREV_TAG}"
  ssh "deploy@${HOST}" \
    "cd /opt/pocket-money && APP_TAG=${PREV_TAG} docker compose -f config/docker/docker-compose.prod.yml up -d"
done

# 2) SLB 权重全量切到回退后的实例（新版本权重归零 / 摘除）
if [ -n "$LB_ID" ] && [ -n "$ECS_A_ID" ] && [ -n "$ECS_B_ID" ]; then
  "${SCRIPT_DIR}/slb-set-weight.sh" "$LB_ID" "$ECS_A_ID" 100
  "${SCRIPT_DIR}/slb-set-weight.sh" "$LB_ID" "$ECS_B_ID" 100
fi

# 3) 健康检查确认回退后实例就绪
echo "[rollback] 等待健康检查..."
for HOST in "$PROD_ECS_A" "$PROD_ECS_B"; do
  "${SCRIPT_DIR}/wait-health.sh" "http://${HOST}:8080" 120
done

echo "[rollback] ✓ 已回滚到 ${PREV_TAG}，请归档回滚记录并开问题处置单"
