# 001-building 技术方案（plan.md）

> **模块**：001-building 构建（探针 + fetch + cargo 交叉编译 + strip + 门禁首产）
> **对应规格**：[spec.md](./spec.md)（FR-001-xxx）· [tasks.md](./tasks.md) · [test-cases.md](./test-cases.md)
> **依据**：[`../constitution.md`](../constitution.md)（C1–C9）· [`../../docs/000-工程规划.md`](../../docs/000-工程规划.md) §4/§8/§10/§11（唯一权威）
> **基座复用**：[`../000-scaffold/plan.md`](../000-scaffold/plan.md)（provision 契约 §4、lock 数据模型 §3、脚本骨架契约 §6.1）
> **标记**：✅ 已实现 / ⬜ 规划未实现 / ⚠️ 风险。**版本**：v1.0 · 2026-11-03。

---

## 1. 技术上下文

| 项 | 取值（规划 §4.2 回引） | 状态 |
|---|---|---|
| pnpm 源 | `github.com/pnpm/pnpm` tag `v<pnpmver>` tarball + SHA-256；版本以 CI 实际可构建最新稳定版钉选（draft 12.0.0 为计划版本） | ⬜ 首轮定版 |
| Rust target | `aarch64-linux-android`（官方 tier2，`rustup target add`） | ⬜ 工具链安装时钉版 |
| cargo-ndk | 交叉编译胶水（NDK linker 选择、`-C link-arg` 注入） | ⬜ 版本钉选随首轮 |
| NDK | 与 aosp-node-runtime 对齐线；开发机现值 28.2.13676358（本仓首产实测后回写 lock） | ⬜ 对齐确认 |
| link 参数 | `-C link-arg=-Wl,-z,max-page-size=16384`（16KB，C4） | ⬜ 首产验证 |
| strip | `llvm-strip --strip-unneeded`（NDK 自带） | ⬜ |
| androidApi | 24（minSdk；linker target `aarch64-linux-android24`，lock `androidApi=24`） | ✅ lock 已登记 |
| 产物 | `libpnpm_cli.so`（ET_DYN + PT_INTERP=`/system/bin/linker64`，N1 命名） | ⬜ |
| 落点 | `.dist/src/pnpm-src/`（源树）、`.dist/out/`（产物+元数据）——均在 .gitignore（C3） | ✅ 忽略规则在位 |

## 2. 宪法合规要点（本模块新增动作）

| 原则 | 本模块动作 |
|---|---|
| C1 | fetch 只「下载→校验→解压」；交叉参数经 `.cargo/config.toml`（源树外）或环境变量注入；**禁止**任何写 `.dist/src/pnpm-src/` 内文件的步骤（FR-001-012/022 双判据） |
| C2 | 版本/哈希期望值只来自 lock；**实测值回写**用命令取值（`sha256sum`/`stat`），不许手抄 |
| C3 | 产物/源树全部落 `.dist/`（已忽略）；提交前 `git status --porcelain` 检查（TC-05 口径沿用） |
| C7 | 探针结论未出前，一切「可编/不可编」表述标 ⬜；结论回写 lock.openProbes 后才可转 ✅ |
| C8 | 探针② 发现 napi-rs 硬依赖、或探针③ ELF 形态不符 ⇒ **立即停止**本模块后续任务，回写规划并冻结（流程图见 §4.5） |

## 3. 接口设计（复用 scaffold 骨架接口，[000-scaffold/plan.md](../000-scaffold/plan.md) §6.1）

### 3.1 `scripts/fetch-pnpm-cli-source.mjs`（骨架已有接口，本模块实现）

```
node scripts/fetch-pnpm-cli-source.mjs --pnpm <ver> [--out <dir>=.dist/src]
```

- 步骤：resolve tag `v<ver>` → 下载 `https://github.com/pnpm/pnpm/archive/<tag>.tar.gz`
  → **SHA-256 校验**（期望值来源：命令行 `--sha256` 或交互确认后回写 lock；不许跳过）→ 解压
  `<out>/pnpm-src/` → 输出 FETCH-METADATA.json（url/tag/sha256/时间）；
- 退出码（沿用全链 0/2/1）：0 成功；2 校验失败/网络产物不可用（拒收）；1 用法/IO 错误；
- 幂等：目标已存在且哈希一致 ⇒ 直接成功（重复 fetch 无副作用）。

### 3.2 `scripts/build-pnpm-cli-android.sh`（骨架已有接口，本模块实现）

```
bash scripts/build-pnpm-cli-android.sh --pnpm <ver> --api 24 --src <dir> --ndk <dir> --out <dir>
```

- 步骤（规划 §4.3 ①–⑦）：env 自检（rustc/cargo-ndk/NDK linker）→ 依赖盘点
  （cargo-license 清单 + Cargo.lock 副本）→ `cargo build --release --target
  aarch64-linux-android`（16KB link-arg，经 `RUSTFLAGS` 或 `--cargo-ndk` 注入）→
  `llvm-strip --strip-unneeded` → 命名 `libpnpm_cli.so` → **调用
  `node tools/provision.mjs --inspect`** → BUILD-METADATA.txt；
- 退出码：0 成功；2 构建/门禁失败；1 用法/IO；
- **不写源树**：cargo 目标目录 `--target-dir <out>/target`（源树外）。

### 3.3 探针执行器（新增 `scripts/probe-pnpm-cli.mjs`，⬜ 实现时定名可调整）

- 探针①：读 pnpm 上游 npm 包 `optionalDependencies` / Release 页，输出结论 JSON（记录性）；
- 探针②：对 Cargo.lock 逐 crate（或关键依赖组：ring/tokio/reqwest/rustls/zstd/napi-rs）
  `cargo build --target aarch64-linux-android` 试编，输出可编/不可编/裁剪清单；
- 探针③：首产产物 ELF 核验（复用 provision.mjs 的 parseELF 结论，不重复造轮子）；
- 输出统一回写 `lock.openProbes`（闭环即改写结论，规划 §8）。

## 4. 关键流程

### 4.1 fetch（C1 执行现场）

版本钉选：`git ls-remote --tags` 取上游最新稳定 `v*`（剔除 rc/beta）→ 与 lock 对比 →
首次定版后回写 lock `active.version` + `releases[].version` + tarball sha256 期望值。

### 4.2 交叉编译

- cargo-ndk 负责 NDK linker 选择与 API level（`--android-platform 24`）；
- 16KB link-arg：`RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"`（或 cargo-ndk 等价参数）；
- 构建日志保留 link-arg 证据（FR-001-021 判据）。

### 4.3 门禁衔接

首产产物先 `--inspect` 只读报告 → 全过后再由 [002](../002-building-workflow/spec.md)
流水线决定投放/归档；**draft 态 ⑤⑥ 只记录**——本模块结束时 lock 仍是 draft，
`status=released` 翻转归 002（发布纪律：Release 先行后翻转，规划 §8/§10.1）。

### 4.4 依赖盘点（FR-001-032，粒度定案 R9）

取向（规划 §4.2）：a) Cargo.lock 整文件 sha256 记入 `builtFrom.cargoLock.sha256`；
b) per-crate checksum 随 Release 归档副本；c) cargo-license 清单归档。**a+c 必做，b 随归档**。

### 4.5 C8 冻结流程

```
探针② 试编
  ├─ 全部可编（或经 cargo feature 裁剪后可编，不改源）→ 继续 T4…
  ├─ napi-rs 为硬依赖且不可绕过 → 探针③ 升级核验 → 确认 NAPI 形态
  │     ⇒ 停止全部构建任务；回写 lock.openProbes ③ + 规划 §3.1 D4；冻结重评（pty 式伴生件路线）
  └─ 个别不可编（ring/zstd 等）→ 逐依赖评估替代或 feature 裁剪 → 回写清单后继续
```

## 5. FR 交叉引用

| FR | 实现落点（⬜ 均为实现目标） | 判据 |
|---|---|---|
| FR-001-001~003 | `scripts/probe-pnpm-cli.mjs`（新增）⬜ + `lock.openProbes` 回写 | spec §2.1 表 |
| FR-001-010~012 | `scripts/fetch-pnpm-cli-source.mjs`（骨架→实现） | spec §2.2 表 |
| FR-001-020 | 工具链安装 + `lock.builtFrom` 回写 | 实值非 TBD |
| FR-001-021~023 | `scripts/build-pnpm-cli-android.sh`（骨架→实现） | spec §2.3 表 |
| FR-001-024 | `provision --inspect` 门禁④ + readelf 复核 | 白名单外即处置 |
| FR-001-030 | `provision --inspect` exit 0 | TC-B05 |
| FR-001-031 | fetch/build 脚本内建元数据输出 | TC-B06 |
| FR-001-032 | cargo-license + Cargo.lock 副本 + 粒度回写 | TC-B06 |

## 6. 风险（回引规划 §11）

R1（依赖可编性，高）→ 探针②前置；R2（前提推翻，高）→ C8 流程 §4.5；R3（上游二进制，
低）→ 探针①记录性；R4（体积，低-中）→ strip 后实测回写；R9（sha 粒度，低）→ §4.4 定案。
