# TECH.md —— `aosp-pnpm-cli` 技术选型（as-built + 规划引用）

> **性质**：项目级技术选型规格。回答「用了什么、什么版本、为什么、否掉了什么」，并给出**已实现 vs 规划**状态列。
> **依据**：[`constitution.md`](./constitution.md)（C1–C9 / N1–N5）· [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（唯一权威，下称「规划 §」）· 本仓库文件 ✅实测（2026-11-02）。
> **标记**：✅ 已实现（本仓可复核，附 `path:line`）/ ⬜ 规划未实现（**只以规划引用出现**，标注规划 §/任务号）/ ⚠️ 冲突或风险。**「选定」一律给理由，「否掉」一律给替代方案。**
> **版本**：v1.0 · 2026-11-02。

---

## 1. 选型总表（状态列区分 as-built 与规划）

| 类别 | 技术 / 版本 | 用途 | 状态 | 选型理由 / 依据 |
|---|---|---|---|---|
| 构建系统 | **Gradle 9.4.1** | 骨架构建、配置期门禁 | ✅ | CI 用 `setup-gradle@v4` 钉 `gradle-version: '9.4.1'`（workflow:29-32）；本地 `.gradle/9.4.1/` 缓存证明 9.4.1 实跑过（TC-01/02，`000-scaffold/test-cases.md`）；无 wrapper（见 STRUCTURE §1 注） |
| Android 插件 | **AGP 9.2.1** | `com.android.library`（AAR 容器） | ✅ | `gradle/libs.versions.toml:2`；仅此一项依赖声明（无第三方库） |
| 发布插件 | **com.vanniktech:gradle-maven-publish-plugin 0.37.0**（`maven.publish.base`） | Maven Central 发布面（Central Portal 直连） | ✅（发布面）/ ⬜ 实际发布（M4） | `settings.gradle.kts:3` 经 pluginManagement 加载；`requiresToken=false, validation=NONE`（`publish.gradle.kts:42-56`）——配置期不强制 Token，与 aosp-node-pty 同口径（发布面 `publish.gradle.kts:1-7` 自述「直接对齐 pty」） |
| JDK | **Temurin 21** | Gradle/AGP 运行 | ✅ | `workflow:23-27`；`000-scaffold/test-cases.md` 执行环境同 |
| 模块形态 | **纯 AAR 容器**（`com.android.library`，无 Kotlin/Java 源） | 只承载原生件与元数据 | ✅ | D8 纯二进制 AAR（规划 §3.1）；✅ 实测 `pnpm-cli/src/main` 为空目录树、debug AAR 仅 4 条目且无 `jni/` |
| exec 打包 | **`useLegacyPackaging=true`**（AGP `packaging.jniLibs`） | 库物理落盘 `nativeLibraryDir`，`execve` 可达 | ✅ | D1（规划 §3.1）：targetSdk≥29 SELinux 禁从可写目录 exec；不落盘则 `execve` ENOENT——**与 pty（dlopen 库）口径相反**（`pnpm-cli/build.gradle.kts:38-45`） |
| 门禁工具语言 | **纯 Node ESM（`.mjs`）**，仅 `node:fs/path/crypto/url`，**零第三方依赖** | `tools/provision.mjs`（投放 + ELF 门禁） | ✅ | 「一个 node 就能跑」⇒ CI/开发机零安装；✅ 实测本会话 `node --check` + 运行全过（退出码 0/2/1 纪律，`provision.mjs:24`） |
| ELF 解析 | 极简自解析 **ELF64 小端**（magic/头/程序头/段表/`.dynamic`） | 门禁①–⑦ 的全部事实来源 | ✅ | `provision.mjs:56-130`（与 aosp-node-pty 同一套解析底座，exec profile 差异见 §2） |
| 事实源格式 | **JSON**（标准库 `JSON.parse`） | `pnpm-cli.lock.json` | ✅ | C2；lock 结构模型见 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §3 |
| 目标 ABI | **arm64-v8a（唯一）** | 只发布 AArch64 | ✅ | `pnpm-cli/build.gradle.kts:34-36`（abiFilters）· `pnpm-cli.lock.json:45-47`（matrix.abi） |
| SDK 线 | **minSdk 24 / compileSdk 36** | linker target `aarch64-linux-android24`（规划 §4.2） | ✅（工程）/ lock `androidApi=24` ✅ | `pnpm-cli/build.gradle.kts:31,33` · `pnpm-cli.lock.json:11,44` |
| 交叉编译 | **Rust `aarch64-linux-android`（官方 tier2）+ cargo-ndk** | pnpm 12+ Rust 独立二进制 → Android | ⬜ **规划（M3.5）** | 规划 §1.1/§4.2/§4.3（D4 前提）；探针② 可编性试编是 M3.5 出口门禁（§4.1 ② / R1）；lock `builtFrom.toolchain` 已登记（`pnpm-cli.lock.json:17`） |
| 源钉选 | pnpm 上游 `v<pnpmver>` tarball + SHA-256，fetch 不改源 | 可复现构建输入 | ⬜ **规划（M3.5-T3）**（脚本骨架 ✅ exit 2） | C1/D3 · 规划 §4.2/§4.3 ② |
| 16KB 页 | `-C link-arg=-Wl,-z,max-page-size=16384` + strip（`llvm-strip --strip-unneeded`） | 16KB 页设备硬要求 | ⬜ **规划（M3.5）**；**门禁⑦ 校验已实现** ✅ | 规划 §4.2/§4.3 ④⑤ · `provision.mjs:160`（min LOAD align ≥ 0x4000） |
| NDK | 与 aosp-node-runtime 对齐线（版本 ⬜ 首产钉） | 交叉编译工具链 | ⬜ **规划（M3.5）** | lock `builtFrom.ndk = "TBD"`（`pnpm-cli.lock.json:19`）· 规划 §4.2 |
| CI | **GitHub Actions**（`ubuntu-24.04`，scaffold 配置期） | 无 .so 可配置 + provision 退出码纪律 + 语法门 | ✅（scaffold）/ ⚠️ 退出码步骤缺陷（§5-1）/ ⬜ 真实流水线（M3.5-T6） | `workflow` 全文 · 规划 §9 |
| 发布 | **两段式：GitHub Release 先行 → Maven Central 随后** | 制品分发 | ⬜ **规划（§10.1，M3.5-T6 / M4）**；发布面与 `RELEASE=false` 卫生门禁 ✅ | 沿用 aosp-node-pty 发布模式（`publish.gradle.kts:3`）· runbook [`../../docs/发布到maven-central.md`](../../docs/发布到maven-central.md) |
| 版本源 | **`CLI_VERSION` = pnpm 上游版本（独立 semver）**，坐标 `io.github.fellow99:pnpm-cli:<CLI_VERSION>` | 制品版本纪律 | ✅（机制）/ 当前值 `12.0.0` 为 **draft 计划版本** ⬜ | D2（规划 §2）· `gradle.properties:7` · `publish.gradle.kts:36-39` · lock `active.version` |
| 许可 | MIT（本仓）+ pnpm MIT + Rust 工具链 MIT OR Apache-2.0 + cargo 依赖许可清单（随 Release 归档 ⬜） | 合规交付物 | ✅（声明面）/ ⬜（cargo 清单，M3.5-T6） | `pnpm-cli/THIRD-PARTY-NOTICES.md` · 规划 §12 |

---

## 2. exec 型门禁 profile：与 dlopen 库（node-pty）门禁的差异

本仓 provision 与同族仓 `aosp-node-pty/tools/provision.mjs` **同一套极简 ELF64 解析与退出码纪律（0/2/1）**，门禁是 **exec 型 profile**（`tools/provision.mjs:8-16` 文件头自述）：

| 门禁 | 本仓（exec 型）✅ | pty（dlopen 库）🔶 | 差异性质 |
|---|---|---|---|
| ELF 形态 | ① `e_type == ET_DYN`（PIE） | 同 `ET_DYN` | 相同 |
| 解释器 | ② **PT_INTERP 恰 1 个且 = `/system/bin/linker64`** | 要求 **PT_INTERP = 0** | **相反**（exec 必须经系统动态链接器启动；`provision.mjs:148-150`） |
| 依赖 | ④ **DT_NEEDED ⊆ 白名单**（系统库 + `libc++_shared.so`，lock `matrix.allowedNeeded`） | 「必须含 `libnode.so`」+ napi 未定义引用门禁 | **相反**（白名单 vs 必含；`provision.mjs:153-155`） |
| NAPI | **无门禁**（独立进程，不解析 napi_*，`provision.mjs:14`） | 有「napi_* 未定义引用」门禁 | 本仓删除 |
| 页对齐 | ⑦ 16KB（`--allow-page-align-4k` 仅 bring-up） | 同 16KB | 相同 |
| 命名/禁区 | `libpnpm_cli.so` 唯一；`BANNED_IN_AAR=['libnode.so']`；jniLibs 兄弟文件唯一性 | 投放只复制 `libpty_node.so` | 本仓新增兄弟文件检查（`provision.mjs:221-225,246-250`） |
| 出处 | ⑤ sha256 / ⑥ size（draft 只记录不比对）/ 源版本 / NDK / flags | 同 + released 实值 | 机制相同，本仓为 draft 态 |

---

## 3. 被**否掉**的选型及替代方案

| 被否对象 | 否掉理由（一手依据） | 替代方案（现状） |
|---|---|---|
| **下载上游 android-arm64 预编译二进制** | C2/C5 红线（可执行物供应链不可证）；esbuild/biome 式分发大概率没有，**有也不用**（D3） | 源码自建（规划 §4.2/§4.3，M3.5）；探针①仅记录性核实（§4.1 ①） |
| **纯 JS 随 node-cli assets 分发 pnpm** | pnpm 12+ 已是 **Rust 独立二进制**，该方案不适用（规划 §1.2，工作区方案 §3.1 ⚠ pnpm 例外条目） | 独立制品线交叉编译（本仓）；M2 期间 pnpm 命令走 corepack/JS 基线兜底（规划 §1.2/§10） |
| **NAPI 混合形态（由 node 进程内加载）** | 属另一条制品线（pty 式伴生件，需要 NODE_MODULE_VERSION 配对）；若探针②证实 ⇒ **触发 D4 冻结重评**（C8） | 冻结本仓计划，按 pty 式伴生件重评（openProbes ③ 出口判据，`pnpm-cli.lock.json:25`） |
| **`libc++_shared.so` 随本 AAR 打包** | 同目录同名库包内冲突；运行时统一供给方（D6） | 只入 DT_NEEDED 白名单（lock 登记），由消费方 `node-runtime` 供给（`pnpm-cli.lock.json:54,57`） |
| **`pnpm self-update` 作为升级途径** | 「运行期下载可执行代码」红线（C5）+ nativeLibraryDir 只读物理不可能成功（D7） | 升级唯一入口 = 制品线新版本；P5 探针验证失败路径干净（规划 §7.1，⬜） |
| **JS 壳脚本入口（npm/corepack 式）/ stdlib 式 assets** | D8：pnpm 本体即命令入口，无需随包数据树；npm/corepack 归 node-cli 线 | 纯二进制 AAR（as-built：AAR 无 assets，✅ 实测 4 条目） |
| **版本与 node 系（RUNTIME/PTY_VERSION）配对** | 无 NODE_MODULE_VERSION 约束、无 ABI 配对（D2） | `CLI_VERSION` 独立 semver = pnpm 上游版本（✅） |
| **`useLegacyPackaging=false`** | dlopen 库（pty）口径；exec 型必须物理落盘，否则 `execve` ENOENT（D1/C1 SELinux） | `useLegacyPackaging=true`（✅ `pnpm-cli/build.gradle.kts:43`） |
| **进程内 dlopen 形态** | 本线定位就是**独立进程**的 pnpm 命令（PTY 需要，规划 §1.2） | exec 型 PIE + `pty.spawn`（运行期组合属规划，见 ARCHITECTURE §4） |

---

## 4. 版本矩阵（as-built 实值）

| 维度 | 值 | 来源 |
|---|---|---|
| root 工程 | `aosp-pnpm-cli`，仅 `:pnpm-cli` | `settings.gradle.kts:20-21` ✅ |
| Gradle / AGP / 发布插件 | 9.4.1 / 9.2.1 / vanniktech 0.37.0 | workflow:32 · `gradle/libs.versions.toml:2` · `settings.gradle.kts:3` ✅ |
| namespace / minSdk / compileSdk | `org.fellow99.pnpmcli` / 24 / 36 | `pnpm-cli/build.gradle.kts:30,31,33` ✅ |
| ABI | arm64-v8a（唯一） | `pnpm-cli/build.gradle.kts:35` · lock `:46` ✅ |
| 坐标 | `io.github.fellow99:pnpm-cli:12.0.0`（**draft**，`published=false`） | `publish.gradle.kts:36-39` · `gradle.properties:7` · lock `:36-39` ✅ |
| pnpm 上游计划版本 | 12.0.0（draft；M3.5 以 CI 实际钉住为准） | lock `:4,9` · 规划 §2 ✅（draft 语义） |
| 门禁七项期望 | 全 `true`（lock `gates`，draft 态为**期望值存档**） | lock `:27-35` ✅ |
| 依赖白名单 | `libc.so libm.so libdl.so libz.so liblog.so libc++_shared.so` | lock `matrix.allowedNeeded :48-55` ✅ |

---

## 5. 选型层面的未决 / 风险（⚠️ / ⬜）

| # | 项 | 现状 | 处置建议 |
|---|---|---|---|
| 1 | **CI 退出码步骤 shell 语义缺陷** | ⚠️ `workflow:37-45`：GitHub Actions `run:` 默认 `bash -e`，`provision --check` exit 2 会让 step 立即失败，`code=$?` 不可达（✅ 本会话实测 bash -e 行为）；「其它非零码视为缺陷」的区分逻辑是死代码 | M3.5-T6 重写 CI 时改为 `code=0; node tools/provision.mjs --check \|\| code=$?`；详见 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §10 |
| 2 | scaffold 措辞「M1」滞后于规划 v2.0 的 M3.5 | ⚠️ `workflow:6` 与 `pnpm-cli/THIRD-PARTY-NOTICES.md:8` 仍写 M1 | 规划 §4.1 已登记为 M3.5 顺手回写项；本批只登记不改 |
| 3 | `gradle.properties:3` `VERSION_NAME=0.1.0` 模板遗留 | ⚠️ 不参与坐标（版本唯一取 CLI_VERSION），双版本属性并存易误读 | M3.5 收口时清理或加注释（STRUCTURE §7-1） |
| 4 | Rust 依赖可编性（napi-rs？ring？tokio？） | ⬜ R1（规划 §11），M3.5 首项试编；napi-rs 硬依赖 ⇒ 触发 C8 冻结 | 探针②（规划 §4.1 ②） |
| 5 | DT_NEEDED 实际清单 / 体积 / TLS 根证书来源 / store 注入方式 | ⬜ R4/R5/R6（规划 §11），均首产/真机实测回填 | M3.5-T4/T7（规划 §10） |
| 6 | Cargo.lock 依赖 sha 记录粒度 | ⬜ R9（规划 §11），M3.5-T3 定案（取向 a+b+c，规划 §4.2） | 定案后可能新增 `builtFrom.cargoLock` 字段（规划 §8） |

---

## 6. 依赖清单（运行时 / 构建期）

| 依赖 | 谁提供 | 何时需要 | 缺失后果 |
|---|---|---|---|
| Node ≥ 18（ESM） | 开发机 / CI | provision/fetch 运行、语法门 | `tools/`、`scripts/` 不可用（CI `node --check` 失败） |
| bash | 开发机 / CI（Linux runner 自带） | build 脚本、`bash -n` | 语法门失败 |
| JDK 21 + Gradle 9.4.1 + AGP 9.2.1 | 开发机 / CI（setup-java + setup-gradle） | AAR 配置与构建 | 配置期门禁失败 |
| `com.vanniktech.maven.publish.base` 0.37.0 | gradlePluginPortal | 发布面 | 无发布能力（构建不受影响） |
| rustup stable + `aarch64-linux-android` target + cargo-ndk + NDK | ⬜ 规划（M3.5，规划 §9 装机清单） | M3.5 交叉编译 | 首产无法进行（R1） |
| `io.github.fellow99:node-cli` / `node-runtime` | **消费方**（运行期依赖，D5/D6） | 终端运行 pnpm（lifecycle scripts 需要 node；`libc++_shared.so` 供给） | 带 lifecycle scripts 的包 `pnpm install` 失败 / 白名单库缺失（运行期边界，规划 §7） |

> **Gradle 依赖为 0 第三方库**：`gradle/libs.versions.toml` 仅声明 AGP 插件 ⇒ 工程完全离线可配置 ✅（FR-000-005 的机制基础）。
