#!/usr/bin/env bash
# ============================================================
# 敏感信息扫描（M0 设计 §7.1/T4：CI 拦截硬编码敏感值）
# 基线实现：正则扫描源码与配置中的疑似硬编码密码/密钥。
# 占位符形式 ${ENV_VAR} / ${ENV_VAR:default} 不视为违规。
# 局限：无法识别变体混淆，正式接入云效后可叠加平台密钥扫描能力。
# ============================================================
set -uo pipefail

FAIL=0

echo "[secret-scan] 扫描硬编码敏感信息..."

# 密码/密钥类键值对：值非占位符且长度 >= 6
if grep -RInE "(password|passwd|secret|api[_-]?key|access[_-]?key)[\"']?[[:space:]]*[:=][[:space:]]*[\"']?[^$'\"{}[:space:]]{6,}" \
    --include='*.java' --include='*.yml' --include='*.yaml' --include='*.properties' \
    src/ config/ 2>/dev/null; then
    echo "[secret-scan] 发现疑似硬编码敏感信息（见上方行号）"
    FAIL=1
fi

# 私钥文件内容
if grep -RInE "BEGIN (RSA|EC|OPENSSH|DSA) PRIVATE KEY" src/ config/ 2>/dev/null; then
    echo "[secret-scan] 发现私钥内容"
    FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
    echo "[secret-scan] 通过"
fi
exit $FAIL
