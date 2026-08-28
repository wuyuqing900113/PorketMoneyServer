#!/usr/bin/env bash
# ============================================================
# 版本切割（GA 设计 §4.3，D63）
# 在 release 分支上把 1.0-SNAPSHOT 切割为正式版并回切下一迭代 SNAPSHOT：
#   1. 前置校验：release 分支 / 工作区洁净 / tag 未占用
#   2. mvn clean verify 全门禁（覆盖率 + Checkstyle/PMD/SpotBugs + 测试）
#   3. pom 版本置为正式版 → 提交 → 打 annotated tag v<RELEASE>
#   4. pom 版本回切 <NEXT_SNAPSHOT> → 提交
# 不自动 push：结束后打印推送指令，由发布负责人核对后执行（外向动作留痕）。
#
# 用法：cut-release.sh <RELEASE_VERSION> <NEXT_SNAPSHOT>
#   例：cut-release.sh 1.0.0 1.1.0-SNAPSHOT        # GA 首版（在 release/1.0.x 上）
#       cut-release.sh 1.0.1 1.0.2-SNAPSHOT        # 1.0.x 补丁线 hotfix
# 退出码：0=成功；1=前置校验/门禁失败；2=用法错误
# ============================================================
set -euo pipefail

RELEASE="${1:-}"
NEXT_SNAPSHOT="${2:-}"

if [ -z "$RELEASE" ] || [ -z "$NEXT_SNAPSHOT" ]; then
  echo "[cut-release] 用法: cut-release.sh <RELEASE_VERSION> <NEXT_SNAPSHOT>" >&2
  echo "[cut-release] 例:   cut-release.sh 1.0.0 1.1.0-SNAPSHOT" >&2
  exit 2
fi

# 版本号格式校验：正式版 x.y.z；下一迭代必须是 x.y.z-SNAPSHOT
if ! echo "$RELEASE" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "[cut-release] 正式版本号格式应为 x.y.z，实际：${RELEASE}" >&2
  exit 1
fi
if ! echo "$NEXT_SNAPSHOT" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$'; then
  echo "[cut-release] 下一迭代版本应为 x.y.z-SNAPSHOT，实际：${NEXT_SNAPSHOT}" >&2
  exit 1
fi

TAG="v${RELEASE}"

# 必须在 release/* 分支执行（GA §4.2：release/1.0.x 冻结分支；hotfix 同线）
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if ! echo "$BRANCH" | grep -Eq '^release/'; then
  echo "[cut-release] 请在 release/* 分支执行（当前分支：${BRANCH}）" >&2
  echo "[cut-release] 先切冻结分支：git checkout -b release/${RELEASE%.*}.x master" >&2
  exit 1
fi

# 工作区必须洁净（防止把未评审改动带入发布）
if [ -n "$(git status --porcelain)" ]; then
  echo "[cut-release] 工作区存在未提交改动，请先提交或暂存：" >&2
  git status --short >&2
  exit 1
fi

# tag 不得已存在
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "[cut-release] tag ${TAG} 已存在，请勿重复切割" >&2
  exit 1
fi

echo "[cut-release] 1/4 全量质量门禁（mvn clean verify）..."
mvn -B -ntp clean verify

echo "[cut-release] 2/4 切正式版 ${RELEASE} 并提交..."
mvn -B -ntp versions:set -DnewVersion="$RELEASE" -DgenerateBackupPoms=false
git add pom.xml
git commit -m "chore(release): cut ${RELEASE}"

echo "[cut-release] 3/4 打 tag ${TAG}..."
git tag -a "$TAG" -m "Release ${RELEASE}"

echo "[cut-release] 4/4 回切下一迭代 ${NEXT_SNAPSHOT} 并提交..."
mvn -B -ntp versions:set -DnewVersion="$NEXT_SNAPSHOT" -DgenerateBackupPoms=false
git add pom.xml
git commit -m "chore(release): next development iteration ${NEXT_SNAPSHOT}"

cat <<EOF

[cut-release] 本地切割完成：
  分支：${BRANCH}
  正式版提交 + tag：${TAG}
  下一迭代：${NEXT_SNAPSHOT}

[cut-release] 核对无误后由发布负责人执行（脚本不自动 push）：
  git push origin ${BRANCH} --tags
  # 随后在云效以 BUILD_TAG=${RELEASE} 触发流水线阶段 5/6/7（镜像推 ACR → 测试部署 → 生产金丝雀）
  # 生产金丝雀/回滚按 docs/deploy/release-runbook.md 执行
EOF
