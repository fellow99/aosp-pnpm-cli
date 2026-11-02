# 003 · CLI（命令行本体）功能规格

> 模块：003-cli · 状态：⬜ 规划（2026-11-03 立项）· 所属工程：aosp-pnpm-cli
> 规划基线：[`docs/000-工程规划.md`](../../docs/000-工程规划.md) §7（运行期契约）/ §7.1（回程探针 P1–P7）/ §10（M3.5-T7）/ §10.2（模块拆分）
> 上位约束：[`../constitution.md`](../constitution.md) C5（self-update 禁用/平台二进制能力边界）/ C7（未实测 ⬜）；消费方契约 L1–L5（[overall-spec](../overall-spec.md) §5）。

## 1. 目的

验证 `libpnpm_cli.so` 作为 **Android 上的 pnpm 命令行本体**的实际行为：版本回程、包安装、
store 落点、生命周期脚本经 node 的完整回程、self-update 失败路径、TLS——把规划 §7.1 的
P1–P7 探针**可执行化**为测试用例并逐条给出证据，为消费方文档（README 已知限制、注入表）
提供实测背书。

## 2. 功能需求（FR-003-xxx，RFC 2119）

### 2.1 基础行为（无网络）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-003-001 | MUST | `--version`/`-v`：输出版本串且 exit 0（P1 判据：版本串回程、exit/close 齐全） | stdout 含与 lock `CLI_VERSION` 一致的 semver；exit=0 |
| FR-003-002 | MUST | `--help`：输出用法文本且 exit 0 | stdout 非空、exit=0 |
| FR-003-003 | MUST | 未知子命令/错误用法：exit 非零且诊断信息在 stderr | `pnpm definitely-not-a-command` ⇒ exit≠0 |
| FR-003-004 | MUST | 输出流完整性：stdout/stderr 分离；UTF-8（含中文/emoji）回程不乱码 | 定向捕获两流分别断言 |

### 2.2 网络与安装（P2/P7）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-003-010 | MUST | `pnpm install <纯 JS 包>` 成功（P7：TLS 握手 registry.npmjs.org） | `node_modules/<pkg>` 存在；exit=0；TLS 根证书来源结论记录（R6） |
| FR-003-011 | MUST | `pnpm install <含 lifecycle script 的纯 JS 包>`：脚本**经 PATH 上 node** 执行（P2 完整回程 pnpm → sh → node → script） | install 日志含脚本执行痕迹且无「node not found」类错误；node 缺席时该用例**预期失败**（L1 契约） |
| FR-003-012 | MUST | `npm_config_registry` 等 `npm_config_*` 环境变量透传生效 | 指向镜像/代理的 registry 变量被采纳（日志或行为可证） |
| FR-003-013 | SHOULD | `pnpm dlx <纯 JS 工具>` 行为记录（P4，不作出口判据） | 输出与 exit 如实记录 |

### 2.3 store 与目录契约（P3，R5 定案）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-003-020 | MUST | `pnpm store path` 落点在**注入的可写目录**（filesDir 语义），绝不落只读库目录 | 落点路径以注入前缀开头；store 注入方式 a（XDG_DATA_HOME）/b（store-dir）实测结论回写规划 §7（R5 定案） |
| FR-003-021 | MUST | HOME 基座：pnpm 配置/缓存落 `$HOME/.config(pnpm)` 等 HOME 派生路径 | `pnpm config get`/文件落点实测在注入 HOME 下 |

### 2.4 边界行为（红线验证）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-003-030 | MUST | `pnpm self-update` **必须失败**且无部分写入（P5；D7/C5） | exit≠0；原二进制 sha256 前后一致（无部分写入）；输出如实记录回写消费方文档 |
| FR-003-031 | SHOULD | 平台二进制包（esbuild 式）安装行为记录：**不静默降级**（L3 声明验证 ⬜ 口径评估） | 行为如实记录（预期失败或明确报错；禁止「装成功但不可用」的静默态） |
| FR-003-032 | SHOULD | 16KB 页设备 exec 正常（P6；条件性——需 16KB 页模拟器/真机） | 16KB 设备上 P1 判据复验通过；无 16KB 设备 ⇒ ⬜ 如实标注（C7） |

## 3. 验收场景（测试环境口径）

- **载体**：Android 设备/模拟器上直接 exec 产物（`/data/local/tmp` 下 shell 域允许 exec），
  env 注入按规划 §7 表（PATH/HOME/TMPDIR/XDG 或 store-dir/npm_config_*）；
- P2 的 node 依赖：PATH 上有可用 `node`（同工作区 aosp-node-cli 产物 ⬜ 或等价注入）时执行
  完整回程用例；无 node ⇒ 该用例按 L1 契约记录「预期失败」证据；
- x86_64 模拟器跑 arm64 产物走指令翻译（abilist 含 arm64-v8a）——**记录性用例**，不替代
  arm64 真机/16KB 判据（FR-003-032）；
- 全部用例输出（stdout/stderr/exit code/关键文件路径）进测试报告，⬜ 未跑项不得标 ✅（C7）。

## 4. 边界

- **不做**构建与门禁（001）、CI/Release（002）；
- **不做**环境引导层实现（PATH 注入器/symlink 农场是消费方职责，规划 §1.3）——本模块只
  验证**契约**（注入正确时行为正确）；
- PTY 交互（node-pty forkpty 路径）不在本模块直接验证（需消费方终端 App）；本模块以
  `adb shell` 直接 exec 验证 CLI 本体，PTY 回程留消费方联调（规划 §7.1 P1 的完整形态 ⬜）。
