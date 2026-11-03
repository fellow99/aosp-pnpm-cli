#!/usr/bin/env bash
# scripts/package-release-artifacts.sh —— 组装 GitHub Release 归档（FR-002-013）
#
# 归档名：pnpm-cli-v<ver>-android-arm64-v8a.tar.gz + 旁挂 .sha256
# 内容清单（规划 §9 / §10.1）：
#   libpnpm_cli.so          exec 型 PIE 产物（已过 provision 七项门禁）
#   BUILD-METADATA.txt      工具链/NDK/link-arg/size/sha256 实值
#   FETCH-METADATA.json     源 URL/tag/sha256/时间/跳过链接清单
#   LICENSES-cargo.txt      cargo 依赖许可清单（cargo license）
#   Cargo.lock              依赖钉版副本（整文件 sha256 见 BUILD-METADATA）
#
# 用法：
#   scripts/package-release-artifacts.sh --pnpm 12.16.1 --dist .dist/out --out .dist/release
#
# 退出码：0 成功 · 2 清单缺件/校验失败 · 1 用法错误
set -euo pipefail

usage() {
  cat <<'EOF'
用法: scripts/package-release-artifacts.sh --pnpm <ver> --dist <构建产物目录> --out <归档输出目录>
  --pnpm  pnpm 上游版本
  --dist  build 脚本产物目录（含 libpnpm_cli.so 与元数据）
  --out   归档输出目录
EOF
}

PNPMVER=""; DIST=""; OUT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --pnpm) PNPMVER="$2"; shift 2;;
    --dist) DIST="$2"; shift 2;;
    --out)  OUT="$2";  shift 2;;
    -h|--help) usage; exit 0;;
    *) echo "未知参数: $1" >&2; usage; exit 1;;
  esac
done
[[ -z "$PNPMVER" || -z "$DIST" || -z "$OUT" ]] && { usage; exit 1; }

fail2() { echo "[package] ✘ $*" >&2; exit 2; }

command -v cygpath >/dev/null 2>&1 && { DIST="$(cygpath -u "$DIST")"; OUT="$(cygpath -u "$OUT")"; }

# —— 清单核对（缺件即失败，无「警告后继续」）——
REQUIRED=(libpnpm_cli.so BUILD-METADATA.txt FETCH-METADATA.json LICENSES-cargo.txt Cargo.lock)
# FETCH-METADATA.json 由 fetch 脚本写在其输出目录（.dist/src），dist 是构建目录——
# 允许从 ../src 兜底复制（同一次流水线的相对布局）。
if [[ ! -f "$DIST/FETCH-METADATA.json" ]]; then
  CAND="$(dirname "$DIST")/src/FETCH-METADATA.json"
  [[ -f "$CAND" ]] && cp "$CAND" "$DIST/FETCH-METADATA.json"
fi
for f in "${REQUIRED[@]}"; do
  [[ -f "$DIST/$f" ]] || fail2 "归档缺件: $DIST/$f"
done
# 评审加固：许可清单不得为空文件（cargo-license 失败时 build 只告警，此处拦住空清单进归档）
[[ -s "$DIST/LICENSES-cargo.txt" ]] || fail2 "LICENSES-cargo.txt 为空（cargo-license 未产出有效清单，G2 判据不满足）"

# —— 组装 ——
STAGE="$OUT/stage/pnpm-cli-v${PNPMVER}-android-arm64-v8a"
rm -rf "$OUT/stage"; mkdir -p "$STAGE"
cp "${DIST}/${REQUIRED[0]}" "${DIST}/${REQUIRED[1]}" "${DIST}/${REQUIRED[2]}" "${DIST}/${REQUIRED[3]}" "${DIST}/${REQUIRED[4]}" "$STAGE/"

# 归档内额外放 LICENSE 与本仓 THIRD-PARTY-NOTICES（消费方可及性）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
[[ -f "$REPO_ROOT/LICENSE" ]] && cp "$REPO_ROOT/LICENSE" "$STAGE/LICENSE-repo"
[[ -f "$REPO_ROOT/pnpm-cli/THIRD-PARTY-NOTICES.md" ]] && cp "$REPO_ROOT/pnpm-cli/THIRD-PARTY-NOTICES.md" "$STAGE/"

mkdir -p "$OUT"
ARCHIVE="$OUT/pnpm-cli-v${PNPMVER}-android-arm64-v8a.tar.gz"
(cd "$OUT/stage" && tar -czf "../$(basename "$ARCHIVE")" "$(basename "$STAGE")")
rm -rf "$OUT/stage"

# —— 旁挂 .sha256（与 tar.gz 同目录；内容为「<sha256>  <文件名>」）——
SHA="$(sha256sum "$ARCHIVE" | cut -d' ' -f1)"
printf '%s  %s\n' "$SHA" "$(basename "$ARCHIVE")" > "$ARCHIVE.sha256"

# —— 自检：解包复核 + sha256 复算 ——
VERIFY_DIR="$OUT/.verify"
rm -rf "$VERIFY_DIR"; mkdir -p "$VERIFY_DIR"
tar -xzf "$ARCHIVE" -C "$VERIFY_DIR"
for f in "${REQUIRED[@]}"; do
  [[ -f "$VERIFY_DIR/$(basename "$STAGE")/$f" ]] || fail2 "解包复核缺件: $f"
done
VSHA="$(sha256sum "$ARCHIVE" | cut -d' ' -f1)"
[[ "$VSHA" == "$SHA" ]] || fail2 "sha256 复算不一致"
rm -rf "$VERIFY_DIR"

SIZE="$(stat -c%s "$ARCHIVE" 2>/dev/null || wc -c < "$ARCHIVE")"
echo "[package] 归档: $ARCHIVE (size=$SIZE)"
echo "[package] sha256: $SHA"
echo "[package] 清单: ${REQUIRED[*]} + LICENSE-repo + THIRD-PARTY-NOTICES.md"
exit 0
