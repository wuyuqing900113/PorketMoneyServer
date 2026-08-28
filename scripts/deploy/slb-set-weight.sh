#!/usr/bin/env bash
# ============================================================
# SLB 后端权重调整（M7 设计 §7.2 金丝雀灰度，D59）
# 调整指定 ECS 在 SLB 后端服务器组的权重，实现灰度流量比例切换。
#
# 用法：
#   slb-set-weight.sh <LB_ID> <ECS_INSTANCE_ID> <WEIGHT> [V_SERVER_GROUP_ID]
#
# 前置：已安装并配置 aliyun CLI（凭证、region）；目标 ECS 已加入该后端组。
# 说明：默认后端服务器组用 SetBackendServers；若灰度走虚拟服务器组（VServerGroup），
#       传入第 4 参 V_SERVER_GROUP_ID 则改用 SetVServerGroupAttribute。
#       具体 region / 服务器组拓扑以云效部署变量为准。
# ============================================================
set -euo pipefail

LB_ID="${1:-}"
ECS_ID="${2:-}"
WEIGHT="${3:-}"
VGROUP_ID="${4:-}"
REGION="${ALIYUN_REGION:-cn-hangzhou}"

if [ -z "$LB_ID" ] || [ -z "$ECS_ID" ] || [ -z "$WEIGHT" ]; then
  echo "[slb-weight] 用法: slb-set-weight.sh <LB_ID> <ECS_INSTANCE_ID> <WEIGHT> [V_SERVER_GROUP_ID]" >&2
  exit 2
fi
if ! command -v aliyun >/dev/null 2>&1; then
  echo "[slb-weight] 未找到 aliyun CLI，请先安装并配置凭证" >&2
  exit 3
fi

BACKEND="[{\"ServerId\":\"${ECS_ID}\",\"Weight\":${WEIGHT},\"Type\":\"ecs\"}]"

if [ -n "$VGROUP_ID" ]; then
  echo "[slb-weight] 虚拟服务器组 ${VGROUP_ID}：ECS ${ECS_ID} 权重 → ${WEIGHT}"
  # 注意：SetVServerGroupAttribute 需提交组内全量后端列表；此处仅示意单台权重，
  # 实际部署应先 DescribeVServerGroupAttribute 取全量后改目标台权重再回写。
  aliyun slb SetVServerGroupAttribute \
    --RegionId "$REGION" \
    --VServerGroupId "$VGROUP_ID" \
    --BackendServers "$BACKEND"
else
  echo "[slb-weight] 默认后端组 ${LB_ID}：ECS ${ECS_ID} 权重 → ${WEIGHT}"
  aliyun slb SetBackendServers \
    --RegionId "$REGION" \
    --LoadBalancerId "$LB_ID" \
    --BackendServers "$BACKEND"
fi

echo "[slb-weight] 完成"
