# overall-spec.md —— `io.github.fellow99:pnpm-cli` 整体规格（技术无关）

> **性质**：项目级**需求规格**。只写「要什么、验收判据是什么、不在范围内的是什么」，**不写实现**（不出现框架名与文件路径；实现落点见 [`overall-plan.md`](./overall-plan.md) 与 [`000-scaffold/plan.md`](./000-scaffold/plan.md)）。
> **上位约束**：[`constitution.md`](./constitution.md)（C1–C9 / N1–N5）与 [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（唯一权威，下称「规划 §」）。本文只引用其 § 号，不复制正文。
> **标记**：✅ 已实现（附验收出处）/ ⬜ 规划未实现（规划引用）/ ⚠️ 冲突或风险。
> **版本**：v1.0 · 2026-11-02 · 状态：**M0 骨架完成**；M3.5（交叉编译与首产）未开工。
> **FR 编号**：沿用 [`000-scaffold/spec.md`](./000-scaffold/spec.md) 的 `FR-000-xxx`（区间编号按 spec.md 摘要粒度沿用），RFC 2119 语义（MUST / MUST NOT / SHOULD）。

---

## 1. 系统用途与目标用户

### 1.1 一句话

本系统是一条**可复现的 Android 制品线**：把 pnpm 12+（Rust 独立二进制）从官方源码交叉编译成 arm64-v8a 的 exec 型命令行 `libpnpm_cli.so`，以**纯二进制 AAR** 发布，并提供「锁文件唯一事实源 + exec 门禁 + 幂等投放 + 发布流水线」——**不含任何嵌入 API**。它让消费方 App 的终端（PTY）里出现一个真正的 `pnpm` 命令（规划 §1.1/§1.3）。

### 1.2 目标用户

| 用户 | 他们要什么 | 本系统给什么 |
|---|---|---|
| **终端类 App 消费方**（主用户） | 在 Android 终端里跑真 `pnpm`（install / -v / dlx），不碰交叉编译与 ELF 细节 | 一个 AAR（含 pnpm 本体）+ 一份运行期依赖/注入契约文档 |
| **消费方构建流水线作者** | 一条命令把校验过的二进制投进工程、重复执行无副作用 | 锁文件 + 投放/校验工具（退出码 0/2/1 纪律） |
| **制品线维护者** | 版本跟随 pnpm 上游、可审计、可回滚 | 独立 semver（`CLI_VERSION`）+ lock 决策记录（openProbes） |
| **上架合规责任人** | 证明「不下载上游二进制、运行期不下载可执行代码、许可齐备」 | 宪法 C5/C6 + THIRD-PARTY-NOTICES + Release 归档清单（⬜ M3.5） |

### 1.3 非目标（明确不做，规划 §1.3）

1. **不做任何嵌入 API / 进程内加载形态**（实为 NAPI 混合形态即触发宪法 C8 冻结重评，D4）。
2. **不分发 JS 版 pnpm**（corepack / node-cli assets 的 M2 基线归 node-cli 线）；**不分发 npm / npx / corepack 本体**（同归 node-cli）。
3. **不做环境引导层实现**（PATH/HOME 注入、symlink 农场、bridge 载荷——消费方职责，规划 §7）。
4. **不做 node 本体与 `libc++_shared.so` 的供给**（node-cli / node-runtime 线，D5/D6）。
5. **不做 pnpm store / 缓存的长期治理**（运行期生成于消费方 filesDir，自管，规划 §1.3）。
6. **不发布 arm64-v8a 以外的 ABI**；**不支持 `pnpm self-update`**（D7）。

---

## 2. 用户故事

| # | 角色 | 想要 | 以便 |
|---|---|---|---|
| US-1 | 消费方开发者 | 在终端里 `pnpm install`，lifecycle scripts 能被 node 跑起来 | 端上包管理闭环（规划 §7.1 P2）⬜ |
| US-2 | 流水线作者 | 「构建 → 门禁 → 投放 → 出包」每步有明确退出码，篡改/缺失即硬失败 | CI 可断言、不被人肉步骤卡住 |
| US-3 | 维护者 | pnpm 出新版时「改版本源 + CI 重建 + lock 回写」即可升级 | 跟随上游、决策留痕（openProbes） |
| US-4 | 评审者 | 只看仓库就能回答「这版 pnpm 从哪来、多大、哈希多少、前提是否成立」 | diff 即审计（lock 唯一事实源，C2） |
| US-5 | 合规责任人 | 证明无上游预编译二进制、无运行期可执行下载、许可清单随 Release | 上架/合规材料可生成（C5 / G 红线） |

---

## 3. 能力总览与系统边界

### 3.1 六个能力面

| # | 能力面 | 必须做到 | 现状 |
|---|---|---|---|
| F1 | **工程骨架** | 干净工程可配置可出 AAR 容器；无二进制不阻塞 | ✅（TC-01/02，`000-scaffold/test-cases.md`） |
| F2 | **版本纪律** | 制品版本 = pnpm 上游版本（独立 semver），坐标/发布面就绪 | ✅（机制；实际发布 ⬜ M4） |
| F3 | **事实源与门禁** | 版本/哈希/门禁/出处单一出处；exec 七项门禁硬失败 | ✅（draft 态「只记录不比对」） |
| F4 | **投放** | 校验过的产物幂等投放进 AAR 容器；禁区强制 | ✅（工具完整；待首产物 ⬜） |
| F5 | **构建流水线** | 钉版源码 → 交叉编译 → strip → 门禁 → 归档/发布，可复现 | ⬜ 规划（M3.5，规划 §4.3/§9） |
| F6 | **运行期契约** | env 注入表、依赖前提、能力边界（三条已知限制）文档化 | ✅（声明面，规划 §7）；实测判据 ⬜ M3.5-T7 |

### 3.2 在范围内（规划 §1.3）

`libpnpm_cli.so` 的 Rust 交叉编译、exec 门禁、AAR 打包、发布；版本事实源 lock（独立 semver，含 openProbes）；运行期 env 注入契约与回程探针定义（规划 §7）；pnpm 生态行为边界声明（self-update 禁用、平台二进制包限制）。

### 3.3 不在范围内

JS 版 pnpm 分发（M2 基线归 node-cli）· npm/npx/corepack 本体 · 任何嵌入 API / 进程内加载形态 · 环境引导层实现 · node 本体 · store/缓存长期治理（§1.3）。

### 3.4 交付边界（一次分发到底含什么）

AAR **必须**含：`jni/arm64-v8a/libpnpm_cli.so` + `META-INF/pnpm-cli.properties`（版本元数据）。AAR **不得**含：node 本体、`libc++_shared.so`（由消费方 node-runtime 供给，D6）、任何 assets / 壳脚本（D8）、`libnode.so`（禁区）。Release 归档 **必须**含：`libpnpm_cli.so` + BUILD/FETCH-METADATA + LICENSES + cargo 依赖许可清单（⬜ 规划 §9/§10.1）。

---

## 4. 功能需求（RFC 2119，FR-000-xxx 沿用）

> 「验收判据」全部**可证伪**；「现状」列 as-built。模块级落点见 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §7 交叉引用表。

### 4.1 工程骨架（F1/F2）

| 编号 | 级别 | 需求 | 可证伪的验收判据 | 现状 |
|---|---|---|---|---|
| FR-000-001 | MUST | root 工程名 `aosp-pnpm-cli`，仅包含 `:pnpm-cli` 一个模块 | 任务求值输出中有且只有该模块 | ✅（TC-01） |
| FR-000-002 | MUST | `:pnpm-cli` 为 Android library，namespace `org.fellow99.pnpmcli`，minSdk 24、compileSdk 36，ABI 仅 arm64-v8a | AAR manifest 与 metadata 反映上述值 | ✅（✅ 实测 debug AAR manifest） |
| FR-000-003 | MUST | 坐标 `io.github.fellow99:pnpm-cli:<CLI_VERSION>`；CLI_VERSION = pnpm 上游版本，独立 semver | 坐标版本随版本源属性变化；与 node 系版本无配对约束 | ✅（机制） |
| FR-000-004 | MUST | **useLegacyPackaging = true**（exec 型硬要求：库必须物理落盘 `nativeLibraryDir`） | 构建配置含该开关；不满足则 `execve` ENOENT（D1） | ✅ |
| FR-000-005 | MUST | 无 `.so` 时构建配置/任务求值**必须成功**（二进制缺失不阻碍配置期） | 干净工程任务求值 BUILD SUCCESSFUL | ✅（TC-01 + CI 配置门） |

### 4.2 事实源与 lock 纪律（F3）

| 编号 | 级别 | 需求 | 可证伪的验收判据 | 现状 |
|---|---|---|---|---|
| FR-000-010~013 | MUST | **lock 纪律**：版本/大小/哈希/门禁/出处单一权威出处；**draft 不伪造哈希**（draft 态 size/sha256 为空、只记录不比对）；**openProbes 登记三项遗留探针**；「前提被推翻即冻结重评」作为出口判据登记在案 | lock 可 diff 审计；draft 条目 sha256 为空串而非占位哈希；openProbes 含 ①②③；未投放时校验不因「无哈希可比对」而假过（打警示） | ✅（draft；`provision --check` exit 2 ✅ 复现） |

### 4.3 投放与 exec 门禁（F4）

| 编号 | 级别 | 需求 | 可证伪的验收判据 | 现状 |
|---|---|---|---|---|
| FR-000-030~033 | MUST | **provision 退出码契约 0/2/1**：全过=0；门禁不合格/未投放=2；用法/IO 错误=1。七项 exec 门禁（PIE、PT_INTERP=系统 linker64、AArch64、DT_NEEDED 白名单、sha256、size、16KB）任一不过即硬失败 | 各场景退出码可断言（未投放=2 ✅ 复现；未知版本=1 ✅ 复现） | ✅ |
| FR-000-034~036 | MUST | **仅投放 `libpnpm_cli.so`**（单一原生件）；jniLibs **禁入 `libnode.so`** 且兄弟文件唯一；投放同时生成版本元数据；draft 态「只记录不比对」需显式警示 | 投放后 jniLibs 仅一个 .so；混入他件/禁件即失败；`--check` 输出含兄弟文件检查行 | ✅（工具逻辑；待首产物回归 ⬜） |

### 4.4 配置期 CI（F1）

| 编号 | 级别 | 需求 | 可证伪的验收判据 | 现状 |
|---|---|---|---|---|
| FR-000-040 | MUST | CI 在**无 .so** 时执行构建配置/任务求值必须通过 | CI 该步绿 | ✅（步骤在位；⚠️ 仓内无 CI 运行记录，⬜） |
| FR-000-041 | MUST | CI 校验「provision 未投放 = 退出码 2」，其它非零码视为工具缺陷 | 步骤对 2 与其它非零码可区分 | ⚠️ **步骤语义缺陷**：shell `-e` 语义使 2 与其它非零码不可区分且 step 必失败（详见 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §6） |
| FR-000-042 | MUST | CI 对脚本做语法门（Node 语法 ×2 + shell 语法） | 语法门全过即绿 | ✅（本会话复现等价命令全过） |

---

## 5. 边界与已知限制（消费方契约，规划 §7）

| # | 限制 | 级别 | 说明与判据 |
|---|---|---|---|
| L1 | **必须配 node**（lifecycle scripts 是 JS，由 PATH 上的 node 执行） | MUST（消费方） | 未注入 `node` 命令时，带 lifecycle scripts 的包 `pnpm install` 失败；判据 = 真机回程 P2 ⬜（规划 §7.1） |
| L2 | **`pnpm self-update` 禁用**（D7） | MUST NOT | 依赖 self-update 升级被禁止（运行期下载可执行 C5 红线 + nativeLibraryDir 只读物理不可能）；判据 = P5：必须失败且无部分写入 ⬜ |
| L3 | **生态包平台二进制不可用**（C2/C5 能力边界） | MUST（声明） | esbuild/biome 式 optionalDependencies 会在安装时运行期下载平台 .so ⇒ 含平台二进制的包不可用；纯 JS 包安全；声明口径 ⬜ M3.5 评估（R10） |
| L4 | 全局安装包**自带的二进制**不可直接 exec（落 filesDir） | MUST（声明） | 其 JS 入口经 pnpm exec 走 node 可用 ⬜（规划 §7 已知限制 4） |
| L5 | store / 缓存必须落消费方可写目录，绝不落只读库目录 | MUST | 注入方式 a/b 择一 ⬜ 由 P3 探针定案（R5） |

---

## 6. 非功能需求

| 编号 | 需求 | 判据 |
|---|---|---|
| NFR-REPRO-001 | 构建**可复现**：钉版源 + 锁定工具链 + 门禁，干净 runner 单条流水线重出同口径产物 | CI 重建 sha256 与 lock 一致（⬜ M3.5，规划 §12） |
| NFR-SEC-001 | **运行期不下载可执行代码**：本体随包预置；self-update 禁用；平台二进制包为能力边界声明 | 发布物与消费方文档核查（C5，规划 §12） |
| NFR-SEC-002 | 仓库与发布配置**不含凭据**（Portal Token / GPG 仅用户级） | 仓库内只有模板文件；发布任务默认禁用（C6）✅ |
| NFR-SIZE-001 | 体积首产实测回填 lock（参照同线 uv 25–40MB 量级，仅量级参照） | lock `size` 回写（⬜ R4） |
| NFR-LIC-001 | pnpm MIT + Rust 工具链 MIT OR Apache-2.0 + cargo 依赖许可清单随 Release 归档 | THIRD-PARTY-NOTICES ✅ + Release 归档清单（⬜ M3.5-T6） |
| NFR-HYG-001 | 发布卫生：`RELEASE=false` 默认；两段式发布（GitHub Release 先行 → Maven Central 随后） | 无 `-PRELEASE=true` 时发布/签名任务不可执行 ✅ |
| NFR-DOC-001 | 未实测项一律 ⬜ 标注；规划不得写成事实 | 本规格体系逐条核查 ✅ |

---

## 7. 验收判据（as-built 出口与规划出口）

**as-built（M0）出口**——`specs/000-scaffold/test-cases.md` 五条用例（TC-01…TC-05，2026-11-02 执行记录 ✅）：干净工程可配置、AAR 可产出、provision 未投放 exit 2、脚本语法门全过、二进制不入库。索引见 [`overall-test-cases.md`](./overall-test-cases.md)。

**M3.5 出口（规划引用，规划 §10 目标 G1–G4）**⬜：G1 交叉编译过门禁并 GitHub Release；G2 归档完整（元数据 + cargo 许可清单）；G3 真机 PTY 内 `pnpm -v` / `pnpm install <纯JS包>`（lifecycle 经 node 完整回程）/ `pnpm store path` 回程成功；G4 lock=released。

---

## 8. 与实现的一致性说明（as-built 提示）

| 项 | 现状 |
|---|---|
| FR-000-040/041（CI 配置期） | ⚠️ 步骤在位但 041 存在 shell `-e` 语义缺陷（2 与其它非零码不可区分）；scaffold CI 尚无仓内运行记录 ⬜ |
| FR-000-034~036（投放/禁区） | ✅ 工具逻辑完整；因尚无真实产物，投放路径与门禁①–⑦的**正样本**尚未跑过（⬜ M3.5-T5 首产回归） |
| lock 状态 | draft（12.0.0 计划版本）：`size=0`、`sha256=""`、`ndk/flags=TBD`、`mavenCentral.published=false`——全部符合「draft 不伪造哈希」（C2） |
| 措辞滞后 | workflow 头注释与 THIRD-PARTY-NOTICES 仍有「M1」措辞（规划 §4.1 已登记为 M3.5 顺手回写项）⚠️ |
