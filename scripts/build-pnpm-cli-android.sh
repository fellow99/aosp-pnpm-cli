#!/usr/bin/env bash
# scripts/build-pnpm-cli-android.sh —— Rust 交叉编译 pnpm（12+ 独立二进制）为 Android
# arm64 的 exec 型 pnpm 命令行（libpnpm_cli.so，PIE 可执行）。
#
# 关键纪律（docs/000-工程规划.md §技术方案；000-scaffold 阶段固化接口，M3.5 实现）：
#   1) 决策前提：pnpm 12+ 为 Rust 独立二进制（工作区方案 §9.3，2026-10-30 拍板）；
#      若探针推翻前提（NAPI 混合形态）⇒ 冻结本仓并重评（lock.openProbes ③）；
#   2) Rust 官方 tier2 target aarch64-linux-android + cargo-ndk（NDK linker）；
#   3) -C link-arg=-Wl,-z,max-page-size=16384（16KB 页设备硬要求）；
#   4) 产物 exec 型 ELF：PIE + PT_INTERP=/system/bin/linker64；
#   5) 输出命名 libpnpm_cli.so（Android 打包只认 lib*.so）；
#   6) 【运行期依赖】lifecycle scripts 由 PATH 上的 node 执行——消费方必须配 node-cli；
#   7) 不下载上游预编译二进制（C2 纪律），一律源码自建。
#
# 产物：<out>/libpnpm_cli.so（strip）+ <out>/BUILD-METADATA.txt
#
# 用法（规划接口，M3.5 实现）：
#   scripts/build-pnpm-cli-android.sh \
#     --pnpm 12.0.0 --api 24 \
#     --src .dist/src/pnpm-src \
#     --ndk "$ANDROID_NDK_HOME" \
#     --out build/out
#
# 状态：000-scaffold 只固化接口与差异纪律；cargo 构建/strip 门禁链在 M3.5 落地。
set -euo pipefail

usage() {
  cat <<'EOF'
用法: scripts/build-pnpm-cli-android.sh --pnpm <ver> --api <api> --src <pnpm 源码树> --ndk <ndk 路径> --out <dir>
  --pnpm  pnpm 上游版本（= CLI_VERSION）
  --api   Android API level（默认 24）
  --src   pnpm 源码树（fetch 脚本产出）
  --ndk   Android NDK 路径
  --out   产物目录
EOF
}

PNPMVER=""; API="24"; SRC=""; NDK=""; OUT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --pnpm) PNPMVER="$2"; shift 2;;
    --api)  API="$2";     shift 2;;
    --src)  SRC="$2";     shift 2;;
    --ndk)  NDK="$2";     shift 2;;
    --out)  OUT="$2";     shift 2;;
    -h|--help) usage; exit 0;;
    *) echo "未知参数: $1" >&2; usage; exit 1;;
  esac
done

if [[ -z "$PNPMVER" || -z "$SRC" || -z "$NDK" || -z "$OUT" ]]; then
  usage
  exit 1
fi

echo "[build] SCAFFOLD: cargo 构建/strip 门禁链在 M3.5 实现（000-scaffold 只固化接口与差异纪律）。"
echo "[build] 计划: pnpm=$PNPMVER api=$API src=$SRC ndk=$NDK out=$OUT"
echo "[build] 产物计划: $OUT/libpnpm_cli.so（exec 型 PIE, Rust 独立二进制, 16KB 对齐）"
exit 2
