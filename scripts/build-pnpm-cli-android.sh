#!/usr/bin/env bash
# scripts/build-pnpm-cli-android.sh —— Rust 交叉编译 pnpm（12+ 独立二进制）为 Android
# arm64 的 exec 型 pnpm 命令行（libpnpm_cli.so，PIE 可执行）。
#
# 关键纪律（docs/000-工程规划.md §4；specs/001-building/）：
#   1) 决策前提：pnpm 12+ 为 Rust 独立二进制（工作区方案 §9.3，2026-10-30 拍板）；
#      若探针推翻前提（NAPI 混合形态）⇒ 冻结本仓并重评（lock.openProbes ③）；
#   2) Rust 官方 tier2 target aarch64-linux-android + cargo-ndk（NDK linker）；
#      上游 rust-toolchain.toml 钉的 Rust 线由 rustup 自动解析（源码不改，C1）；
#   3) -C link-arg=-Wl,-z,max-page-size=16384（16KB 页设备硬要求，经 RUSTFLAGS 注入）；
#   4) 产物 exec 型 ELF：PIE + PT_INTERP=/system/bin/linker64（provision --inspect 门禁）；
#   5) 输出命名 libpnpm_cli.so（Android 打包只认 lib*.so）；
#   6) 【运行期依赖】lifecycle scripts 由 PATH 上的 node 执行——消费方必须配 node-cli；
#   7) 不下载上游预编译二进制（C5 纪律），一律源码自建；源码树零写入（C1）：
#      cargo --target-dir 与元数据全部落在 --out（源树外）。
#
# 产物：<out>/libpnpm_cli.so（strip）+ <out>/BUILD-METADATA.txt
#       <out>/LICENSES-cargo.txt + <out>/Cargo.lock（依赖盘点，随归档）
#
# 用法：
#   scripts/build-pnpm-cli-android.sh \
#     --pnpm 12.16.1 --api 24 \
#     --src .dist/src/pnpm-src \
#     --ndk "$ANDROID_NDK_HOME" \
#     --out .dist/out
#
# 退出码：0 成功（含门禁全过） · 2 构建/门禁失败 · 1 用法/IO 错误
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

fail2() { echo "[build] ✘ $*" >&2; exit 2; }

# —— 环境（Windows git-bash：cargo 在 ~/.cargo/bin；NDK 路径转 Windows 格式）——
export PATH="$HOME/.cargo/bin:$PATH"
command -v cygpath >/dev/null 2>&1 && [[ "$NDK" == /* ]] && NDK="$(cygpath -w "$NDK")"
command -v cygpath >/dev/null 2>&1 && [[ "$SRC" == /* ]] && SRC="$(cygpath -w "$SRC")"
export ANDROID_NDK_HOME="$NDK"

HOST_TAG="windows-x86_64"
[[ "$(uname -s)" == Linux* ]] && HOST_TAG="linux-x86_64"
STRIP_BIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin/llvm-strip"
[[ -x "$STRIP_BIN" || -x "$STRIP_BIN.exe" ]] || STRIP_BIN="${STRIP_BIN}.exe"

# —— env 自检（缺件 exit 2：构建输入不可用）——
command -v rustc >/dev/null 2>&1 || fail2 "rustc 不在 PATH（rustup stable 已装？）"
command -v cargo >/dev/null 2>&1 || fail2 "cargo 不在 PATH"
command -v cargo-ndk >/dev/null 2>&1 || fail2 "cargo-ndk 未安装（cargo install cargo-ndk）"
command -v node >/dev/null 2>&1 || fail2 "node 不在 PATH（provision 门禁需要）"
[[ -f "$SRC/Cargo.toml" ]] || fail2 "源码树缺失 Cargo.toml: $SRC（先跑 fetch 脚本）"
[[ -d "$NDK" ]] || fail2 "NDK 目录不存在: $NDK"

CARGO_NDK_VER="$(cargo ndk --version 2>/dev/null | head -1)"
NDK_VER="$(grep -m1 '^Pkg.Revision' "$NDK/source.properties" 2>/dev/null | cut -d= -f2 | tr -d ' ')"
echo "[build] pnpm=$PNPMVER api=$API"
echo "[build] src=$SRC"
echo "[build] ndk=$NDK_VER ($NDK)"
echo "[build] cargo-ndk=$CARGO_NDK_VER"

mkdir -p "$OUT"
OUT_FWD="$OUT"; SRC_FWD="$SRC"
command -v cygpath >/dev/null 2>&1 && { OUT_FWD="$(cygpath -u "$OUT")"; SRC_FWD="$(cygpath -u "$SRC")"; }
# OUT_FWD/SRC_FWD/TARGET_DIR 全部绝对化：构建与重定向子 shell 会 cd 进源码树，
# 相对路径会把产物写到 <源码树>/… 下（CI run 41563075234 产物错位、
# 41565505469 license 重定向即时失败——同一根因两处实证）
OUT_FWD="$(cd "$OUT_FWD" && pwd)"
SRC_FWD="$(cd "$SRC_FWD" && pwd)"
TARGET_DIR="$OUT_FWD/target"
echo "[build] target-dir=$TARGET_DIR"

# —— ① 依赖盘点（快路径）：Cargo.lock 副本 + 整文件 sha256（粒度 a，规划 §4.2）——
# cargo license 清单在构建成功后跑（③）——其 cargo metadata 解析含 git 依赖较慢，
# 不应阻塞主构建链路。
echo "[build] Cargo.lock 副本 + sha256 …"
cp "$SRC_FWD/Cargo.lock" "$OUT_FWD/Cargo.lock"
CARGO_LOCK_SHA="$(sha256sum "$OUT_FWD/Cargo.lock" | cut -d' ' -f1)"
echo "[build] Cargo.lock sha256=$CARGO_LOCK_SHA"

# —— ② cargo 交叉编译（cargo-ndk -P=platform；16KB link-arg 经 RUSTFLAGS 注入；--locked 钉依赖）——
LINK_ARG="-Wl,-z,max-page-size=16384"
echo "[build] cargo ndk build（16KB link-arg: $LINK_ARG）…"
( cd "$SRC_FWD" && RUSTFLAGS="-C link-arg=$LINK_ARG" \
    cargo ndk -t arm64-v8a -P "$API" \
    build --locked -p pnpm-cli --bin pnpm --release --target-dir "$TARGET_DIR" ) \
  || fail2 "cargo 构建失败"

RAW_BIN="$TARGET_DIR/aarch64-linux-android/release/pnpm"
[[ -f "$RAW_BIN" ]] || fail2 "构建产物缺失: $RAW_BIN"

# 实际生效的 Rust 工具链版本（源树 rust-toolchain.toml 解析结果）
RUSTC_ACTUAL="$(cd "$SRC_FWD" && rustc --version | head -1)"
echo "[build] rustc(实际生效)=$RUSTC_ACTUAL"

# —— ③ 依赖许可清单（构建后跑：registry 缓存就绪；300s 超时防网络挂起）——
# 失败时删除空文件 ⇒ package 清单核对必失败（不静默进归档；G2 纪律）
echo "[build] cargo license 清单 …"
if ( cd "$SRC_FWD" && timeout 300 cargo license --avoid-dev-deps > "$OUT_FWD/LICENSES-cargo.txt" 2>"$OUT_FWD/.license-err.log" ); then
  echo "[build] 许可清单: $OUT/LICENSES-cargo.txt"
else
  rm -f "$OUT_FWD/LICENSES-cargo.txt"
  echo "[build] ✘ cargo-license 失败/超时（空清单已删除；package 将按缺件硬失败，错误日志 $OUT/.license-err.log）" >&2
fi

# —— ④ strip（llvm-strip --strip-unneeded）→ libpnpm_cli.so ——
echo "[build] strip → $OUT/libpnpm_cli.so"
"$STRIP_BIN" --strip-unneeded "$RAW_BIN" || fail2 "llvm-strip 失败"
cp "$RAW_BIN" "$OUT_FWD/libpnpm_cli.so"

ART="$OUT_FWD/libpnpm_cli.so"
ART_SIZE="$(stat -c%s "$ART" 2>/dev/null || wc -c < "$ART")"
ART_SHA="$(sha256sum "$ART" | cut -d' ' -f1)"

# —— ⑤ ELF 门禁（provision --inspect 只读报告；任一不过 exit 2）——
echo "[build] provision --inspect 门禁…"
PROVISION="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/tools/provision.mjs"
[[ -f "$PROVISION" ]] || fail2 "provision.mjs 不存在: $PROVISION"
node "$PROVISION" --inspect "$ART" || fail2 "provision 门禁未全过（规划 §6 七项）"

# —— ⑥ BUILD-METADATA.txt ——
cat > "$OUT_FWD/BUILD-METADATA.txt" <<EOF
# libpnpm_cli.so build metadata
artifact=libpnpm_cli.so
pnpm-version=$PNPMVER
android-api=$API
target=aarch64-linux-android
rustc=$RUSTC_ACTUAL
cargo-ndk=$CARGO_NDK_VER
ndk=$NDK_VER
link-arg=$LINK_ARG
strip=llvm-strip --strip-unneeded
size=$ART_SIZE
sha256=$ART_SHA
cargo-lock-sha256=$CARGO_LOCK_SHA
built-at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
source-tree=unmodified (C1; fetch only downloads/verifies/extracts)
EOF

echo "[build] 完成: $ART (size=$ART_SIZE sha256=$ART_SHA)"
echo "[build] 元数据: $OUT/BUILD-METADATA.txt"
exit 0
