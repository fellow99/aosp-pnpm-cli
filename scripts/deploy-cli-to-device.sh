#!/usr/bin/env bash
# scripts/deploy-cli-to-device.sh —— 003-cli 测试载体搭建（specs/003-cli/plan.md §1/§2）
#
# 职责：把首产 libpnpm_cli.so 部署到设备的 /data/local/tmp/pnpm-cli/（shell 域允许 exec），
#       建立 HOME/TMPDIR/XDG_DATA_HOME/bin 目录树与 pnpm symlink——等价消费方引导层的
#       直接 exec 载体（规划 §7 注入表的最小落地）。
#
# 用法：
#   scripts/deploy-cli-to-device.sh [--artifact <libpnpm_cli.so 路径>] [--serial <adb serial>]
#
# 退出码：0 成功 · 2 部署/校验失败 · 1 用法错误
set -euo pipefail

ART=""; SERIAL=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact) ART="$2"; shift 2;;
    --serial)   SERIAL="$2"; shift 2;;
    -h|--help) echo "用法: $0 [--artifact <so>] [--serial <adb serial>]"; exit 0;;
    *) echo "未知参数: $1" >&2; exit 1;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ -z "$ART" ]] && ART="$REPO_ROOT/.dist/out/libpnpm_cli.so"
[[ -f "$ART" ]] || { echo "[deploy] ✘ 产物不存在: $ART（先跑 build）" >&2; exit 2; }

ADB="adb"
command -v adb >/dev/null 2>&1 || ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
command -v "$ADB" >/dev/null 2>&1 || { echo "[deploy] ✘ adb 不可用" >&2; exit 2; }

DEV_ARGS=()
[[ -n "$SERIAL" ]] && DEV_ARGS=(-s "$SERIAL")

echo "[deploy] 设备列表:"
"$ADB" "${DEV_ARGS[@]}" devices

DEV_BASE=/data/local/tmp/pnpm-cli
echo "[deploy] 清理旧载体…"
"$ADB" "${DEV_ARGS[@]}" shell "rm -rf $DEV_BASE"
echo "[deploy] 建目录树…"
"$ADB" "${DEV_ARGS[@]}" shell "mkdir -p $DEV_BASE/home $DEV_BASE/tmp $DEV_BASE/xdg $DEV_BASE/bin $DEV_BASE/work $DEV_BASE/store"
echo "[deploy] push 产物…"
"$ADB" "${DEV_ARGS[@]}" push "$ART" "$DEV_BASE/libpnpm_cli.so" >/dev/null
"$ADB" "${DEV_ARGS[@]}" shell "chmod 755 $DEV_BASE/libpnpm_cli.so"
echo "[deploy] pnpm symlink（PATH 发现路径验证）…"
"$ADB" "${DEV_ARGS[@]}" shell "ln -s $DEV_BASE/libpnpm_cli.so $DEV_BASE/bin/pnpm"

echo "[deploy] 校验（sha256 一致性）…"
LOCAL_SHA="$(sha256sum "$ART" | cut -d' ' -f1)"
DEV_SHA="$("$ADB" "${DEV_ARGS[@]}" shell "sha256sum $DEV_BASE/libpnpm_cli.so" | tr -d '\r' | cut -d' ' -f1)"
[[ "$LOCAL_SHA" == "$DEV_SHA" ]] || { echo "[deploy] ✘ sha256 不一致: local=$LOCAL_SHA dev=$DEV_SHA" >&2; exit 2; }
echo "[deploy] 设备信息:"
"$ADB" "${DEV_ARGS[@]}" shell getprop ro.product.cpu.abi | tr -d '\r'
"$ADB" "${DEV_ARGS[@]}" shell getprop ro.build.version.sdk | tr -d '\r'

cat <<EOF

[deploy] 载体就绪 ✅  ($DEV_SHA)

设备端环境（等价规划 §7 注入表的最小集）：
  export HOME=$DEV_BASE/home
  export TMPDIR=$DEV_BASE/tmp
  export XDG_DATA_HOME=$DEV_BASE/xdg
  export PATH=$DEV_BASE/bin:\$PATH
  PNPM=$DEV_BASE/libpnpm_cli.so

快速验证：
  $ADB ${DEV_ARGS[*]} shell "$DEV_BASE/libpnpm_cli.so --version"
EOF
exit 0
