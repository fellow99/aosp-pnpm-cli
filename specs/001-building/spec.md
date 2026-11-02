# 001 · 构建（building）功能规格

> 模块：001-building · 状态：⬜ 规划（2026-11-03 立项）· 所属工程：aosp-pnpm-cli
> 规划基线：[`docs/000-工程规划.md`](../../docs/000-工程规划.md) §4（构建设计）/ §10（M3.5-T1…T5）/ §10.2（模块拆分）
> 上位约束：[`../constitution.md`](../constitution.md) C1–C9；本模块是 C8（前提推翻即冻结）的**执行现场**。

## 1. 目的

把 pnpm 官方源码（钉版 tarball）可靠地变成**过七项 exec 门禁**的 `libpnpm_cli.so`
（arm64-v8a，exec 型 PIE，16KB 对齐），且全程**不改源**（C1）、结论可审计（C2/C7）。
前置三探针（openProbes ①②③）在本模块执行并回写结论——探针②/③ 是本线的**出口门禁**。

## 2. 功能需求（FR-001-xxx，RFC 2119）

### 2.1 前置探针（规划 §4.1）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-001-001 | MUST | 探针①：核实上游 android-arm64 预编译分发可得性（esbuild/biome 式 optionalDependencies） | 结论（有/无）回写 `lock.openProbes` ①；无论结论如何不影响 CI 自建路线（C5） |
| FR-001-002 | MUST | 探针②：Rust 依赖在 `aarch64-linux-android` 下可编性试编（钉版源 → cargo build --target） | 产出**可编 / 不可编 / 需 feature 裁剪**逐依赖清单并回写 `lock.openProbes` ② |
| FR-001-003 | MUST | 探针③（出口判据）：若证实 pnpm 实为 NAPI 混合形态（无 PT_INTERP、需 node 进程内加载）⇒ **冻结本仓计划**（C8），不得带病推进 | 首产产物 ELF 核验记录（ET_DYN + PT_INTERP 恰 1 个）；若触发冻结则停止后续 FR 并回写规划 |

### 2.2 fetch（C1 执行现场）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-001-010 | MUST | 下载 pnpm `v<ver>` 官方源码 tarball，**版本与 SHA-256 先钉后下** | URL/版本/sha256 可从命令输出复核；产物落 `.dist/src/pnpm-src/` |
| FR-001-011 | MUST | SHA-256 校验失败 ⇒ **拒收**（硬失败，exit 2），不得解压 | 篡改/缺哈希场景实测 exit 2 且目标目录不产生 |
| FR-001-012 | MUST | 解压后源码树与 tarball 内容**逐字节一致**（C1 不改源；交叉参数只经 cargo 配置注入） | fetch 后源树内无任何被改写文件的记录；重复 fetch 结果幂等 |

### 2.3 交叉编译与工具链

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-001-020 | MUST | 工具链钉版：rust stable（rustc/cargo 版本）、`rustup target add aarch64-linux-android`、cargo-ndk、NDK——**实值回写 `lock.builtFrom`** | `lock.builtFrom.toolchain/ndk` 不再是 TBD；与构建机 `rustc -vV`/NDK 版本一致 |
| FR-001-021 | MUST | `cargo build --release --target aarch64-linux-android`，link 参数含 `-Wl,-z,max-page-size=16384`（16KB，C4 硬要求） | 构建日志含该 link-arg；产物门禁⑦实测 ≥ 0x4000 |
| FR-001-022 | MUST | **不修改上游源码**：交叉参数经 cargo 配置/环境注入，无源码补丁（C1） | 构建流程 git diff 源树为空（或无任何写源文件步骤） |
| FR-001-023 | MUST | strip：`llvm-strip --strip-unneeded`（NDK 自带）→ `libpnpm_cli.so` | strip 后产物过门禁且体积记录回写（NFR-SIZE-001） |
| FR-001-024 | SHOULD | DT_NEEDED 实际清单核实（`libc++_shared.so` 是否出现），与 lock `matrix.allowedNeeded` 白名单口径核对 | 白名单外依赖 ⇒ 按 §4.3④ 处置（feature 裁剪/静态化），**不改门禁口径** |

### 2.4 门禁首产与元数据

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-001-030 | MUST | 首产产物过 `provision --inspect` 七项门禁（①–⑦）全绿 | exit 0；报告七行全 ✔（draft 态 ⑤⑥ 只记录——如实标注） |
| FR-001-031 | MUST | 生成 `FETCH-METADATA.json`（源 URL/版本/sha256/时间）与 `BUILD-METADATA.txt`（工具链/NDK/flags/产物 size/sha256） | 两个文件随产物归档，字段可复核 |
| FR-001-032 | MUST | 依赖盘点：cargo 依赖许可清单（cargo-license 或等价）+ Cargo.lock 副本归档；依赖 sha 记录粒度按规划 §4.2 取向定案（a+b+c 组合） | 归档含许可清单文件；粒度结论回写规划 §4.2 与本模块 plan |

## 3. 验收场景

- 干净环境（新 runner/新机）按模块文档可重出同口径 `libpnpm_cli.so`（可复现性入口）；
- 探针②若证实 napi-rs 为硬依赖或探针③触发 ⇒ 流程**停止**并冻结重评（C8），其余 FR 不再执行；
- 产物大小/哈希实测值回写 lock（`size`/`sha256` 不许手抄，`sha256sum`/`stat` 取值——规划 §8 写入规则 1）。

## 4. 边界

- **不做** CI 流水线编排、Release/归档投放、lock `status=released` 翻转（归 [002-building-workflow](../002-building-workflow/spec.md)）；
- **不做** 真机运行验证（归 [003-cli](../003-cli/spec.md)）；
- **不做** Maven Central 发布（M4，随 002 扩展）；
- 本模块只产出 `.dist/` 内的产物与元数据，**二进制不入库**（C3）。
