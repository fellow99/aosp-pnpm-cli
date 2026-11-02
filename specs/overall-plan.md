# overall-plan.md —— `io.github.fellow99:pnpm-cli` 整体技术方案

> **性质**：项目级**实现计划**（技术上下文、宪法符合性检查、横切关注点、测试途径、发布策略）。
> **上位约束**：[`constitution.md`](./constitution.md)（C1–C9 / N1–N5）· [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（唯一权威，下称「规划 §」）。本文引用其 § 号，不复制正文。
> **标记**：✅ 已实现 / ⬜ 规划未实现（规划引用）/ ⚠️ 冲突或风险。
> **版本**：v1.0 · 2026-11-02 · 状态：**M0 骨架完成**；M3.5 未开工（工作区 M2 完成后接入，规划头部）。

---

## 1. Technical Context

### 1.1 运行环境（as-built 实值）

| 项 | 值 | 证据 |
|---|---|---|
| 构建链 | Gradle 9.4.1（CI 钉版）+ AGP 9.2.1（版本目录）+ JDK Temurin 21 | workflow:23-32 ✅ |
| 发布链 | vanniktech `maven.publish.base` 0.37.0（Central Portal 直连；`requiresToken=false`/`validation=NONE`） | `settings.gradle.kts:3` · `publish.gradle.kts:42-56` ✅ |
| 模块形态 | `:pnpm-cli` Android library，**无 Kotlin/Java 源**（纯 AAR 容器，D8）；namespace `org.fellow99.pnpmcli`；minSdk 24 / compileSdk 36；ABI arm64-v8a | `pnpm-cli/build.gradle.kts:29-37` ✅ + debug AAR 实测 |
| exec 打包 | `useLegacyPackaging=true`（物理落盘 nativeLibraryDir，D1） | `pnpm-cli/build.gradle.kts:38-45` ✅ |
| host 工具 | Node（ESM，`node:fs/path/crypto/url`）+ bash，零第三方依赖 | `tools/provision.mjs:27-30` ✅ |
| 交叉编译（规划） | rustup stable + `aarch64-linux-android` tier2 target + cargo-ndk + NDK（对齐线 ⬜） | ⬜ 规划 §4.2/§9；lock `builtFrom.ndk="TBD"` |
| 目标制品 | `libpnpm_cli.so`（exec 型 PIE：ET_DYN + PT_INTERP=`/system/bin/linker64` + 16KB 对齐） | 规划 §5/§6 ✅ 门禁侧 |

### 1.2 决策前提（方案的基石）

**「pnpm 12+ 为 Rust 独立二进制」是本仓存在的决策前提（2026-10-30 拍板，D4）**；该前提的可编性试编（探针②）是 M3.5 出口门禁，若证实为 NAPI 混合形态 ⇒ **冻结本仓计划**（宪法 C8 / openProbes ③ / R2）。M2 期间 pnpm 命令由 corepack/JS 基线兜底，本线首产后替换（规划 §1.2）。

---

## 2. 宪法符合性检查（C1–C9 逐条）

| 原则 | 判定 | 理由（含证据） | 待处置 |
|---|---|---|---|
| **C1 fetch 不改源** | ✅ 符合（骨架级） | fetch 脚本文件头明示「源码不改」并只固化接口；交叉参数经 cargo 配置注入（规划 §4.2）；下载/校验实现 ⬜ M3.5-T3 | M3.5 实现后复核 |
| **C2 lock 唯一事实源** | ✅ 符合 | provision 只从 lock 读期望值（`provision.mjs:184-197`），无硬编码版本/哈希；draft 态 `size=0`/`sha256=""` 且显式警示「仅记录不比对」（`:197`） | M3.5 首产回写（规划 §8） |
| **C3 二进制不入库** | ✅ 符合 | `.gitignore:22-28` 覆盖 jniLibs `.so`、`.dist/`、`build/out/`、`target/`；投放目标当前不存在（⬜ 未投放） | 持续把守 |
| **C4 门禁强制** | ✅ 符合 | 七项门禁硬失败 `exit 2`（`provision.mjs:146-168`）；兄弟文件唯一性（`:221-225`）+ `BANNED_IN_AAR`（`:38,246-250`）；唯一软化档位「draft 只记录」有显式警示；`--allow-page-align-4k` 仅 bring-up 且打印警告（`:162-167`） | 首产正样本回归 ⬜ |
| **C5 不下载上游二进制 / 运行期不下载可执行** | ✅ 符合（声明面） | build 脚本七条纪律第 7 条（`:13`）；README 双语「一律源码自建」；self-update 禁用声明（D7）；平台二进制包能力边界声明 ⬜ M3.5 口径（R10） | M3.5-T7 回写消费方文档 |
| **C6 发布卫生** | ✅ 符合 | `RELEASE=false` 默认（`gradle.properties:19`）；发布/签名任务双门禁禁用（`publish.gradle.kts:71-73,133-139`）；凭据仅用户级（模板 `gradle.properties.local.example`） | 两段式首走 ⬜ |
| **C7 未实测标 ⬜** | ✅ 符合 | 规划文件头纪律（`:11`）；本规格体系全量采用 ✅/⬜/🔶/⚠️ 标记 | 评审持续执行 |
| **C8 前提推翻即冻结** | ✅ 符合（登记面） | lock `openProbes[2]` 出口判据 + build 脚本纪律第 1 条 + 规划 §3.1 D4 | 探针② 结论回写 ⬜ |
| **C9 先改文档再改实现** | ⚠️ **部分符合** | 规划 v2.0 已就位且承载 M3.5 设计 ✅；但仓内两处 scaffold 措辞仍为旧「M1」口径（workflow:6、THIRD-PARTY-NOTICES.md:8）——规划 §4.1 自己已登记该偏差待回写 | M3.5 顺手项回写 |

**结论**：0 条原则违背（无 ❌）；1 条部分符合（C9，措辞滞后，已由规划登记）；核心纪律（lock/门禁/不入库/发布卫生）全部有实现级证据。

---

## 3. 横切关注点

### 3.1 错误处理与退出码纪律（全链统一 0/2/1）

| 工具 | 0 | 2 | 1 |
|---|---|---|---|
| `tools/provision.mjs` | 门禁全过/投放完成 | 门禁不合格、**未投放**（`--check` 无产物）、`--inspect` 不合格 | 用法/IO：缺 lock、lock 无该版本、缺 `--from`、`BANNED` 文件在位、读文件失败（顶层 catch，`provision.mjs:271-275`） |
| `scripts/fetch-pnpm-cli-source.mjs` | —（⬜ M3.5） | SCAFFOLD 未实现（`:53`） | 用法错误（`:41-44`） |
| `scripts/build-pnpm-cli-android.sh` | —（⬜ M3.5） | SCAFFOLD 未实现（`:59`） | 用法错误（`:51-54`；`set -euo pipefail` `:25`） |

失败信息走 stderr（`provision.mjs:274`），成功/进度走 stdout 且带 `[provision]` 前缀（`:52-54`）——可 grep、可 CI 断言。

### 3.2 CI 与 shell 语义（⚠️ 本批最重要的发现）

GitHub Actions Linux `run:` 默认 shell 为 `bash -e`：任何非零命令立即中止 step。因此 `workflow:37-45` 中「先跑 `provision --check` 再取 `$?` 判断是否为预期 2」的写法**不可达**——exit 2 时 step 已失败。含义：

- 门禁**仍会拦截**（任何非零都红），安全性质未失；
- 但 FR-000-041 要求的「2=预期、其它非零=缺陷」区分**不成立**，且每次 CI 运行该 step 必然红（与「配置期全绿」的 scaffold CI 定位矛盾）。
- **修法（M3.5-T6 重写 CI 时）**：`code=0; node tools/provision.mjs --check || code=$?; if [ "$code" -ne 2 ]; then … fi`。✅ 本会话实测 `bash -e -c 'false; echo unreachable'` 验证中断语义（STRUCTURE §7-2）。

### 3.3 版本与坐标（防漂移）

版本解析链是**双保险反射覆写**：`publish.gradle.kts` 用 `coordinates("io.github.fellow99", artifactId, CLI_VERSION)` 显式定版（`:36-39`）；模块 `build.gradle.kts` 另设 `project.version` 并在 `afterEvaluate` 反射覆写 publication version（`:17-27`）。`gradle.properties:3` 的 `VERSION_NAME=0.1.0` 不参与坐标（模板遗留 ⚠️，STRUCTURE §7-1）。升级路径：改 `CLI_VERSION` + lock 增项 → CI 重建 → lock 回写（规划 §2/§8）。

### 3.4 安全 / 凭据 / 供应链

| 项 | 策略 | 现状 |
|---|---|---|
| 发布凭据 | 仅用户级 `~/.gradle/gradle.properties`；仓库内只有 8 行模板 | ✅ |
| 误发布 | `RELEASE=false` 默认 + Token 缺失 ⇒ signMaven/publishToMavenCentral 任务禁用 | ✅ `publish.gradle.kts:133-139` |
| 上游二进制 | 一律不下载（C5）；源钉选 tarball + SHA-256（⬜ M3.5-T3） | 声明面 ✅ |
| 运行期可执行下载 | self-update 禁用（D7）；平台二进制包为能力边界 | 声明面 ✅（M3.5 口径 ⬜） |
| 许可 | pnpm MIT + Rust 工具链 MIT OR Apache-2.0；cargo 依赖许可清单随 Release 归档 | THIRD-PARTY-NOTICES ✅（措辞 M1 ⚠️）+ 归档 ⬜ |

### 3.5 可复现性

钉版源 tarball + SHA-256（⬜）、Cargo.lock 随源树、依赖 sha 记录粒度 ⬜ M3.5-T3 定案（规划 §4.2 取向 a+b+c / R9）、NDK/rust 线首产钉版（lock `builtFrom` 回写）⇒ 「干净 runner 单条 workflow 重出同口径产物」（规划 §12 可复现行）。当前 as-built 只能保证**门禁与配置期**可复现（`node` + `bash` + Gradle 钉版即可）。

---

## 4. 测试途径

| 层 | 内容 | 需要真机 | 现状 |
|---|---|---|---|
| **L1 host 门禁** | provision `--check`/`--inspect` 七项门禁、退出码断言、负样本（篡改/白名单外/4KB 对齐） | ❌ | ✅ 工具在位；未投放态已验（exit 2）；正样本 ⬜ 待首产物 |
| **L2 构建配置** | 无 .so 时 `:pnpm-cli:tasks` / `assembleDebug` | ❌ | ✅ TC-01/02（2026-11-02 执行记录） |
| **L3 CI 配置期** | workflow 三段（配置门 / 退出码纪律 ⚠️ / 语法门） | ❌ | 步骤在位；⚠️ §3.2 缺陷；CI 运行记录 ⬜ |
| **L4 真机回程** | P1–P7：`pnpm -v`、lifecycle 经 node 完整回程、store 落点、self-update 失败路径、16KB 设备、TLS（规划 §7.1） | ✅ | ⬜ M3.5-T7 |

用例索引：[`overall-test-cases.md`](./overall-test-cases.md) → [`000-scaffold/test-cases.md`](./000-scaffold/test-cases.md)。

---

## 5. 发布策略

### 5.1 两段式（规划 §10.1，⬜ 首走）

1. **GitHub Release 先行**（M3.5-T6）：tag `v<pnpmver>` push 触发（前置 openProbes ①② 通过）；归档 `pnpm-cli-v<ver>-android-arm64-v8a.tar.gz`（`libpnpm_cli.so` + BUILD/FETCH-METADATA + LICENSES + cargo 依赖许可清单）+ `.sha256`；lock 回写 `status=released`。
2. **Maven Central 随后**（M4）：`-PCLI_VERSION=<pnpmver> -PRELEASE=true` 走 runbook（§2 一次性凭据 → §4 发布前检查 → §5 上传 → §6 核验回填 `mavenCentral.published=true`）。runbook：[`../../docs/发布到maven-central.md`](../../docs/发布到maven-central.md)（M3.5 首次实走后回填经验，规划 §10.1）。

### 5.2 当前 as-built 发布状态

发布面就绪（vanniktech 0.37.0 + 坐标/签名/POM 补丁 + RELEASE 门禁 ✅）；`mavenCentral.published=false`、lock `status=draft`（12.0.0 计划版本，M3.5 以 CI 实际可构建并过门禁的最新稳定版钉选，规划 §2）。

### 5.3 scaffold CI → 真实流水线的替换边界（规划 §9）

scaffold CI 三段（配置门 / 退出码纪律 / 语法门）在 M3.5-T6 替换/扩展为真实流水线（可保留 config-check job + 新增 release job）；替换时顺手修复 §3.2 的 `bash -e` 缺陷与两处「M1」措辞。**替换前不得删除配置期门禁**（FR-000-040）。

---

## 6. 已登记偏差与待办（本批 as-built 评审产出）

| # | 偏差 / 待办 | 等级 | 归属 |
|---|---|---|---|
| 1 | CI 退出码步骤 `bash -e` 语义缺陷（FR-000-041 意图不可达；2 与其它非零码不可区分） | ⚠️ 行为与文档不符 | M3.5-T6 修（§3.2） |
| 2 | workflow:6 / THIRD-PARTY-NOTICES.md:8 的「M1」措辞滞后于规划 v2.0「M3.5」 | ⚠️ 文档口径 | M3.5 顺手项（规划 §4.1 已登记） |
| 3 | `gradle.properties:3` `VERSION_NAME=0.1.0` 模板遗留（不参与坐标） | ⚠️ 易误读 | M3.5 收口清理 |
| 4 | provision 消费可选字段 `pairedRuntime`（打印 + META-INF）而 lock 无该字段 | ⬜ 兼容预留（as-built 行为：`无配对信息`） | 登记于 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §3.3 |
| 5 | 门禁①–⑦与投放路径的正样本回归（无真实产物） | ⬜ | M3.5-T5 |
| 6 | scaffold CI 无仓内运行记录 | ⬜ | 下次 push/PR 时观察 |
