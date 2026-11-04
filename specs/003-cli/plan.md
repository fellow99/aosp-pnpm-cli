# 003-cli 技术方案（plan.md）

> **模块**：003-cli（CLI 本体行为验证：测试载体 + env 注入契约落地 + P1–P7 可执行化）
> **对应规格**：[spec.md](./spec.md)（FR-003-xxx）· [tasks.md](./tasks.md) · [test-cases.md](./test-cases.md)
> **依据**：[`../constitution.md`](../constitution.md) · [`../../docs/000-工程规划.md`](../../docs/000-工程规划.md) §7/§7.1/§11
> **上游**：[001-building](../001-building/plan.md) 首产产物（`.dist/out/libpnpm_cli.so`）
> **标记**：✅ 已实现 / ⬜ 规划未实现 / ⚠️ 风险。**版本**：v1.0 · 2026-11-03。

---

## 1. 技术上下文（测试载体）

| 项 | 取值 | 状态 |
|---|---|---|
| 被测件 | `.dist/out/libpnpm_cli.so`（001 首产） | ✅ 已落锁（size=47317088/sha256=e5378cfa…，provision 投放/`--check` exit 0 实测） |
| 设备 | 真机（adb devices 有实体）或模拟器；当前会话探测：`emulator-5554`（API 37，x86_64，abilist 含 arm64-v8a——arm64 走翻译） | ✅ 已探测 |
| 载体补充（App 探针） | **`probe-app/`**（2026-11-03 新增，参考 aosp-python-cli/probe-app）：独立 Gradle 工程依赖容器 AAR（`app/libs/pnpm-cli-release.aar`，不入库），MainActivity 按规划 §7 注入 HOME/TMPDIR/XDG/PATH/LD_LIBRARY_PATH 后 ProcessBuilder exec NLDIR 本体，TC-C00…C16 逐条 PASS/FAIL/BLOCKED + JSON 报告（filesDir/probe-report.json，adb pull 取回）；D8 纯二进制无 assets 物化；TC-C10 判据＝sha256 前后比对（P5 实测口径）；TC-C07 init 用独立目录防覆盖 | ✅ 已实现（APK 构建通过；测试待指令） |
| 部署点 | `/data/local/tmp/pnpm-cli/libpnpm_cli.so`（chmod 755；shell 域允许 exec） | ⬜ |
| HOME/TMPDIR | `/data/local/tmp/pnpm-cli/home`、`/data/local/tmp/pnpm-cli/tmp`（filesDir 语义等价：可写、非库目录） | ⬜ |
| store 注入 | 方式 a：`XDG_DATA_HOME=/data/local/tmp/pnpm-cli/xdg`；方式 b：`pnpm config set store-dir`（P3 双案实测后定案回写规划 §7） | ⬜ |
| node（P2 用） | PATH 注入 aosp-node-cli 产物——**✅ 已首产 v26.13.0**（sha256 `eb51b439…` 与 node-cli lock 逐字节核对；模拟器上须用 **sh 脚本 shim**（`exec <NLDIR 绝对路径> "$@"`），symlink 被翻译层 exec 包装器拒绝——见规划 §7.1 P2 发现②） | ✅ adb 层完整回程通过（2026-11-04） |
| libc++（若 DT_NEEDED） | `libc++_shared.so` 随测 push 到同目录 + `LD_LIBRARY_PATH`（模拟 node-runtime 供给，D6） | ⬜ 以 001 门禁④ 实测清单为准 |
| TLS（P7） | 直连 registry.npmjs.org；根证书行为（rustls webpki-roots 自带 vs 需 `npm_config_cafile`）实测记录 | ⬜ |

## 2. env 注入契约（规划 §7 表的测试落地）

```bash
# adb shell 内的最小注入（等价消费方引导层的直接 exec 载体）
export HOME=/data/local/tmp/pnpm-cli/home
export TMPDIR=/data/local/tmp/pnpm-cli/tmp
export XDG_DATA_HOME=/data/local/tmp/pnpm-cli/xdg          # store 方式 a
export PATH=/data/local/tmp/pnpm-cli/bin:$PATH             # node symlink 位（P2）
export LD_LIBRARY_PATH=/data/local/tmp/pnpm-cli/lib        # libc++_shared.so（若需要）
PNPM=/data/local/tmp/pnpm-cli/libpnpm_cli.so
$PNPM --version
```

- 命令名对齐：生产形态里命令名是 `pnpm`（nativeLibraryDir + PATH）；测试载体直接 exec
  `.so` 全路径，语义等价（同一 ELF、同一 env 面）；
- `pnpm` 别名：`ln -s $PNPM /data/local/tmp/pnpm-cli/bin/pnpm`（验证 PATH 发现路径）。

## 3. P1–P7 探针 → 用例映射（spec §2 ↔ 规划 §7.1）

| 探针 | 判据（规划原文口径） | 用例 | 备注 |
|---|---|---|---|
| P1 | 版本串回程、exit/close 齐全 | TC-C01（+P6 复验） | adb shell 直接 exec 即可证 |
| P2 | lifecycle 经 node 完整回程 | TC-C06 | node 在 PATH ⇒ 全跑；缺席 ⇒ L1 记录 |
| P3 | store 落点 = filesDir | TC-C08（a/b 双案） | 结论回写规划 §7（R5 定案） |
| P4 | dlx 行为记录 | TC-C11 | 不作出口判据 |
| P5 | self-update 必须失败且无部分写入 | TC-C10 | sha256 前后比对 |
| P6 | 16KB 页设备 exec 正常 | TC-C15 | 需 16KB 设备；无则 ⬜（C7） |
| P7 | TLS 握手成功 + 根证书结论 | TC-C05 + TC-C12 | 结论回填规划 §7 TLS 行 |

## 4. 关键设计决策

1. **直接 exec 而非 PTY**：本模块验证 CLI 本体（ELF 行为/退出码/输出），PTY 交互归消费方
   联调（spec §4 边界）——避免引入 node-pty 依赖闭环；
2. **filesDir 语义等价**：`/data/local/tmp/pnpm-cli/*` 与生产 `filesDir` 同为 app 可写目录
   语义（可写、非 nativeLibraryDir 只读区），判据「store 不落库目录」可证；
3. **arm 翻译记录性**：x86_64 模拟器跑 arm64 是**辅助验证**，判据以 arm64 直执行为准
   （FR-003-032 条件性），TC-C16 如实记录翻译行为；
4. **负样本优先**：self-update（TC-C10）与平台二进制包（TC-C14）是红线验证——**失败才是
   通过**（C5），报告措辞须区分「预期失败 ✅」与「意外失败 ❌」。

## 5. FR 交叉引用

| FR | 用例 | 判据 |
|---|---|---|
| FR-003-001~004 | TC-C01…C04 | spec §2.1 |
| FR-003-010~013 | TC-C05/06/12/11 | spec §2.2 |
| FR-003-020~021 | TC-C08/09 | spec §2.3 |
| FR-003-030~032 | TC-C10/14/15 | spec §2.4 |
| 载体/翻译记录 | TC-C00/C16 | spec §3 |

## 6. 风险（回引规划 §11）

R5（store 落点，中）→ P3 双案实测；R6（TLS 根证书，中）→ P7 记录 + 必要时 `npm_config_cafile`；
R7（node 发现失败，中）→ P2 缺席场景即 L1 契约的实测证据；R10（平台二进制包，中）→
TC-C14 行为记录 + 口径回写评估。
