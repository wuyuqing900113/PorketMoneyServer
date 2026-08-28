#!/usr/bin/env bash
# ============================================================
# 健康检查等待（M7 设计 §7.1 阶段 6）
# 轮询 /actuator/health，等待 Spring Boot readiness 转 UP。
# 用法：wait-health.sh <BASE_URL> [超时秒数，默认 120]
# 退出码：0=UP；1=超时未就绪
# ============================================================
set -euo pipefail

BASE_URL="${1:-}"
TIMEOUT="${2:-120}"

if [ -z "$BASE_URL" ]; then
  echo "[wait-health] 用法: wait-health.sh <BASE_URL> [超时秒数]" >&2
  exit 2
fi

HEALTH_URL="${BASE_URL%/}/actuator/health"
echo "[wait-health] 等待 ${HEALTH_URL} 转 UP（超时 ${TIMEOUT}s）..."

ELAPSED=0
INTERVAL=3
while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
  # status 字段为 "UP" 即就绪（liveness/readiness 探针已在 application.yml 启用）
  BODY="$(curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null || true)"
  if echo "$BODY" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    echo "[wait-health] 服务已就绪（UP），耗时 ${ELAPSED}s"
    exit 0
  fi
  sleep "$INTERVAL"
  ELAPSED=$((ELAPSED + INTERVAL))
done

echo "[wait-health] 超时：${TIMEOUT}s 内未就绪" >&2
exit 1
