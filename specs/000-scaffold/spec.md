# 000 · 工程搭建（scaffold）功能规格

> 模块：000-scaffold · 状态：已实现（scaffold 级）· 所属工程：aosp-pnpm-cli
> 规划基线：[`docs/000-工程规划.md`](../../docs/000-工程规划.md)（pty 四件套折叠为三件）

## 1. 目的

确立 `aosp-pnpm-cli`（Android `pnpm` 命令行，pnpm 12+ Rust 独立二进制，exec 型 PIE）的
仓库结构、构建系统、版本纪律、exec 门禁工具与 CI（配置期）。

## 2. 功能需求（摘要）

- FR-000-001：root 工程名 `aosp-pnpm-cli`，仅 include `:pnpm-cli`。
- FR-000-002：`:pnpm-cli` Android library，namespace `org.fellow99.pnpmcli`，minSdk 24、
  compileSdk 36，ABI 仅 arm64-v8a。
- FR-000-003：坐标 `io.github.fellow99:pnpm-cli:<CLI_VERSION>`；CLI_VERSION = pnpm 上游
  版本，独立 semver。
- FR-000-004：**useLegacyPackaging = true**（exec 型硬要求）。
- FR-000-005：无 `.so` 时 Gradle 配置/任务求值必须成功。
- FR-000-010~013：lock 纪律（draft 不伪造哈希；`openProbes` 登记三项遗留探针与
  "前提被推翻即冻结重评"的出口判据）。
- FR-000-030~036：provision（退出码 0/2/1；仅投放 `libpnpm_cli.so`；jniLibs 禁入
  `libnode.so`）。
- FR-000-040~042：CI 配置期门禁（`gradle :pnpm-cli:tasks`；provision `--check` 未投放=2；
  语法门）。

## 3. 验收场景

- 干净工程可配置；provision 未投放退出码 = 2；二进制不入库。
