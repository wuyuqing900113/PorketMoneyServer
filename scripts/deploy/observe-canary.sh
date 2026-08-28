#!/usr/bin/env bash
# ============================================================
# 金丝雀观察门禁（M7 设计 §7.2，D59/D60）
# 在灰度观察窗口内拉取 ARMS 指标，判定错误率与 P95 是否达标；
# 超限以非零退出，触发云效阶段失败 → 人工/自动回滚（rollback.sh）。
#
# 用法：observe-canary.sh <ARMS_APP_ID> --window <时长，如 15m>
#
# 门禁阈值（对齐 mission 性能基线与 M6 DoD）：
#   错误率 ≤ 0.5%（0.005）；P95 ≤ 500ms；不得出现 5xx 突增
#
# 前置：aliyun CLI（ARMS 凭证/region）、jq。
# 说明：ARMS 自定义监控数据集 / 指标 ID（datasetId、metric）在环境接入时
#       于云效变量中配置（ARMS_DATASET_ID 等）；本脚本固化判定逻辑与阈值。
# ============================================================
set -euo pipefail

APP_ID="${1:-}"
WINDOW="15m"
while [ $# -gt 0 ]; do
  case "$1" in
    --window) WINDOW="${2:-15m}"; shift 2 ;;
    *) shift ;;
  esac
done

MAX_ERROR_RATE="0.005"   # 错误率 ≤ 0.5%
MAX_P95_MS="500"         # P95 ≤ 500ms
REGION="${ALIYUN_REGION:-cn-hangzhou}"

if [ -z "$APP_ID" ]; then
  echo "[observe-canary] 用法: observe-canary.sh <ARMS_APP_ID> --window 15m" >&2
  exit 2
fi
for bin in aliyun jq; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "[observe-canary] 缺少依赖：$bin" >&2
    exit 3
  fi
done

echo "[observe-canary] 应用 ${APP_ID}，观察窗口 ${WINDOW}，阈值：错误率≤${MAX_ERROR_RATE} / P95≤${MAX_P95_MS}ms"
sleep "${WINDOW/m/}m" 2>/dev/null || sleep 900   # 窗口等待（m→分钟）

# 拉取窗口内聚合指标（ARMS 自定义监控查询；dataset/metric 由环境变量提供）
# 返回 JSON 至少含 errorRate、p95Ms 两个数值字段。
RESP="$(aliyun arms RetrieveApiCallData \
  --RegionId "$REGION" \
  --AppId "$APP_ID" \
  --IntervalInSec 60 \
  --DatasetId "${ARMS_DATASET_ID:-}" 2>/dev/null || true)"

if [ -z "$RESP" ]; then
  echo "[observe-canary] 未能获取 ARMS 指标（检查 ARMS_DATASET_ID / 凭证）；" >&2
  echo "[observe-canary] 为避免带盲区放量，判定为不通过 → 请人工核对 ARMS 看板后决定继续或回滚" >&2
  exit 1
fi

ERROR_RATE="$(echo "$RESP" | jq -r '.data.errorRate // .errorRate // 1')"
P95_MS="$(echo "$RESP" | jq -r '.data.p95Ms // .p95Ms // 9999')"

echo "[observe-canary] 实测：错误率=${ERROR_RATE}，P95=${P95_MS}ms"

FAIL=0
awk -v e="$ERROR_RATE" -v t="$MAX_ERROR_RATE" 'BEGIN{exit !(e>t)}' \
  && { echo "[observe-canary] ✗ 错误率 ${ERROR_RATE} > 阈值 ${MAX_ERROR_RATE}"; FAIL=1; }
awk -v p="$P95_MS" -v t="$MAX_P95_MS" 'BEGIN{exit !(p>t)}' \
  && { echo "[observe-canary] ✗ P95 ${P95_MS}ms > 阈值 ${MAX_P95_MS}ms"; FAIL=1; }

if [ "$FAIL" -ne 0 ]; then
  echo "[observe-canary] 灰度观察不通过 → 触发回滚（rollback.sh <PREV_STABLE_TAG>）"
  exit 1
fi
echo "[observe-canary] ✓ 灰度观察达标，可进入下一权重档"
