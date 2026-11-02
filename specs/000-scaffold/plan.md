# 000-scaffold 技术方案（plan.md · As-Built）

> **模块**：000-scaffold 工程搭建（Gradle 骨架 + exec 门禁 + draft lock + 骨架脚本 + 配置期 CI）
> **对应规格**：[spec.md](./spec.md)（功能需求与验收）· [tasks.md](./tasks.md)（任务拆解）· [test-cases.md](./test-cases.md)（用例与执行记录）
> **依据**：[`../constitution.md`](../constitution.md)（C1–C9 / N1–N5）· [`../../docs/000-工程规划.md`](../../docs/000-工程规划.md)（唯一权威，下称「规划 §」）· 本仓源码 ✅实测（行号即当前行号，2026-11-02 逐文件读过）。
> **标记**：✅ 已实现 / ⬜ 规划未实现（规划引用，标注 §）/ ⚠️ 冲突或风险。
> **版本**：v1.0 · 2026-11-02 · 状态：M0 已落地；M3.5（交叉编译/首产/真实 CI）⬜。
> **本文行号口径**：`provision.mjs:N` 指 `tools/provision.mjs`；`lock:N` 指 `pnpm-cli.lock.json`；`build:N` 指 `pnpm-cli/build.gradle.kts`；`wf:N` 指 `.github/workflows/build-pnpm-cli-android.yml`。

---

## 1. 技术上下文

### 1.1 运行环境

| 项 | 值 | 证据 |
|---|---|---|
| Gradle | 9.4.1（CI `setup-gradle@v4` 钉版；本仓**无 wrapper**，开发机自备 9.4.x） | `wf:29-32` ✅ · `.gradle/9.4.1/` 缓存 ✅ |
| AGP | 9.2.1（版本目录唯一条目，**零第三方库依赖**） | `gradle/libs.versions.toml:2,5` ✅ |
| 发布插件 | `com.vanniktech.maven.publish.base` 0.37.0（经 settings pluginManagement 加载） | `settings.gradle.kts:2-4` ✅ |
| JDK | Temurin 21 | `wf:23-27` ✅ |
| host 工具 | Node（ESM）+ bash；provision 仅 `node:fs/path/crypto/url` 四个内置模块，零第三方 | `provision.mjs:27-30` ✅ |
| 模块 | `:pnpm-cli` Android library，无任何 Kotlin/Java 源（`pnpm-cli/src/main` 为空目录树 ✅） | `build:1-12` 注释 + 实测 |
| 目标 ABI | arm64-v8a（唯一） | `build:34-36` · `lock:45-47` |

### 1.2 依赖

- **构建期**：AGP + vanniktech 插件（无库依赖）⇒ 工程完全离线可配置（FR-000-005 的机制基础）。
- **host 工具期**：Node ≥ 18（ESM）。
- **交叉编译期（⬜ 规划 M3.5）**：rustup stable、`aarch64-linux-android` target、cargo-ndk（钉版 ⬜）、NDK（对齐线 ⬜，`lock:19` `builtFrom.ndk="TBD"`）。
- **运行期（消费方，⬜ 规划 §7）**：`io.github.fellow99:node-cli`（lifecycle scripts 需要 node，D5）+ `io.github.fellow99:node-runtime`（`libc++_shared.so` 供给方，D6）。

---

## 2. 宪法合规检查（C1–C9 逐条，实现级证据）

| 原则 | 判定 | 证据 |
|---|---|---|
| C1 fetch 不改源 | ✅（骨架级） | `fetch-pnpm-cli-source.mjs:2-3` 文件头「取构建输入（pnpm 官方源码，不改源）」；实现 ⬜ M3.5-T3 |
| C2 lock 唯一事实源 | ✅ | `provision.mjs:5` 文件头声明 + `readLock()`（`:177-180`）只读 `pnpm-cli.lock.json`；无硬编码版本/哈希（版本取 `lock.active`/`--version`，`:185-187`） |
| C3 二进制不入库 | ✅ | `.gitignore:22-28`（`pnpm-cli/src/main/jniLibs/**/*.so`、`.dist/`、`build/out/`、`target/`）；投放目标当前不存在 |
| C4 门禁强制 | ✅ | 七项门禁硬失败 `exit 2`（`gates()` `:132-168`；三处消费点 `:205,228,237`）；兄弟文件唯一（`:221-225`）；`BANNED_IN_AAR=['libnode.so']`（`:38,246-250`）；draft 软化档位显式警示（`:197`）；`--allow-page-align-4k` 打印 bring-up 警告（`:162-167`） |
| C5 不下载上游二进制 / 运行期不下载可执行 | ✅（声明面） | `build-pnpm-cli-android.sh:13` 纪律 7「不下载上游预编译二进制（C2 纪律），一律源码自建」；README 双语「never download upstream prebuilt binaries」（`README.md:22-23`） |
| C6 发布卫生 | ✅ | `gradle.properties:19` `RELEASE=false`；`publish.gradle.kts:133-139` 任务禁用门禁；`:71-73` Token 检查；凭据仅用户级（`:13-14` + `gradle.properties.local.example:1`） |
| C7 未实测标 ⬜ | ✅ | lock draft 字段如实为空/TBD（`lock:13-14,19-20`）；规划文档全量 ⬜ 纪律（规划 `:11`） |
| C8 前提推翻即冻结 | ✅（登记面） | `lock:25` openProbes[2] 出口判据 + `build-pnpm-cli-android.sh:6-7` 纪律 1 |
| C9 先改文档再改实现 | ⚠️ 部分 | 规划 v2.0 在位 ✅；`wf:6` 与 `pnpm-cli/THIRD-PARTY-NOTICES.md:8` 仍是「M1」旧口径（规划 §4.1 已登记为回写项） |

---

## 3. 数据模型：`pnpm-cli.lock.json` 逐字段（59 行）

### 3.1 顶层

| 字段 | 类型 | 当前值（行号） | 谁读（provision.mjs） |
|---|---|---|---|
| `$comment` | string | 决策记录：版本=上游 pnpm、二进制不入库、**draft 占位说明**、D4 前提（`:2`） | ⚠️ 无工具读（人读审计字段） |
| `active` | object | `{version:"12.0.0", abi:"arm64-v8a"}`（`:3-6`） | `:185-186`（`--version/--abi` 缺省时的选择） |
| `releases[]` | array | 1 条（`:7-42`，见 3.2） | `:187`（按 version+abi 查找） |
| `matrix` | object | 见 3.4（`:43-58`） | `:184`（`matrix?.allowedNeeded`，缺省白名单兜底） |

### 3.2 `releases[i]` 字段表

| 字段 | 当前值（行号） | 语义 / 谁读 |
|---|---|---|
| `version` | `"12.0.0"`（`:9`） | 与 `active.version`、`--version` 配对选择条目（`:187`）；打印于输出首行（`:196`） |
| `abi` | `"arm64-v8a"`（`:10`） | 同上；决定投放子目录（`:241`）与 `--check` 目标路径（`:213`） |
| `androidApi` | `24`（`:11`） | 与 minSdk/linker target 对应；⚠️ 工具不读（规划 §4.2 的 linker target 依据，M3.5 用） |
| `name` | `"libpnpm_cli.so"`（`:12`） | 产物名；`rel.name \|\| DEFAULT_BINARY`（`:194`）；投放名与 `--check` 目标名 |
| `size` | `0`（`:13`） | **draft：无锁值**；`expectSize = size>0 ? size : null`（`:142`）⇒ 门禁⑥ 转「只记录」（`:159`） |
| `sha256` | `""`（`:14`） | **draft：不伪造哈希（C2）**；`expectSha = 长度 64 才生效`（`:141`）⇒ 门禁⑤ 转「只记录」（`:157`） |
| `builtFrom` | `{source, toolchain, runtimeDeps, ndk:"TBD", flags:"TBD"}`（`:15-21`） | 出处审计；⚠️ 无工具读（人读；M3.5 回写 ndk/flags） |
| `openProbes[]` | 3 条（`:22-26`，见 3.3） | 决策记录；⚠️ 无工具读（人读） |
| `gates` | 7 项全 `true`（`:27-35`） | **期望值存档**；⚠️ 工具每次**重新解析产物**，不读本字段（同族仓同口径）——改产物必须同步改这里，否则文件自相矛盾且无人拦截 ⚠️ |
| `mavenCentral` | `{coordinates:"io.github.fellow99:pnpm-cli:12.0.0", published:false}`（`:36-39`） | 发布状态；⚠️ 无工具读（人读；M4 回填 `published=true`） |
| `status` | `"draft"`（`:40`） | draft 语义开关的**文档面**（工具实际以 size/sha256 是否有值判定，`:141-142`；并按 `status==='draft'` 打印警示，`:197`） |
| `pairedRuntime` | **当前不存在** | 可选字段：`rel.pairedRuntime?.coordinates` 被读取（`:196` 输出行、`:263` META-INF `paired.runtime` 行）；缺省时输出「无配对信息」且不写该行——同族仓（pty）预留的兼容读取 ⚠️ 登记 |

### 3.3 `openProbes` 字段详解（决策记录，随代码走）

| # | 内容（`lock:23-25`） | 语义 | 出口判据 |
|---|---|---|---|
| ① | 上游 android-arm64 预编译可得性（esbuild/biome 式分发大概率没有） | **记录性**核实 | 无论结论如何都 CI 自建（不下载上游二进制，C5）；结论回写本字段 |
| ② | Rust 依赖（napi-rs？ring？tokio？）在 `aarch64-linux-android` 下的可编性试编 | **M3.5 出口门禁**（不阻塞建仓） | 可编/不可编/裁剪清单回写；napi-rs 硬依赖 ⇒ 触发 ③（规划 §4.1 ②/R1） |
| ③ | **若前提被推翻**（实为 NAPI 混合形态、由 node 加载） | **冻结重评出口判据**（D4/C8） | 冻结本仓计划，按 pty 式伴生件重评（NODE_MODULE_VERSION 配对 + node-cli 进程内 napi 符号解析） |

回写纪律：闭环即删条目/改写结论，不留「永远为真的句子」（规划 §8）。

### 3.4 `matrix` 字段表

| 字段 | 当前值（行号） | 谁读 |
|---|---|---|
| `minSdk` | `24`（`:44`） | ⚠️ 无工具读（与 `build:33` 人肉对齐） |
| `abi` | `["arm64-v8a"]`（`:45-47`） | ⚠️ 无工具读 |
| `allowedNeeded` | `[libc.so, libm.so, libdl.so, libz.so, liblog.so, libc++_shared.so]`（`:48-55`） | **门禁④ 白名单**（`:184` 读入 `:153-155` 比对）；缺省兜底仅 4 个系统库（`:184`） |
| `execPackaging` | useLegacyPackaging 说明（`:56`） | ⚠️ 无工具读（D1 的人读声明） |
| `note` | 运行期依赖 node + libc++_shared 不随包声明（`:57`） | ⚠️ 无工具读（D5/D6 的人读声明） |

### 3.5 写入规则（M3.5 首产回写清单，规划 §8）

1. `size`/`sha256` 以真实产物实测回填（`sha256sum`/`stat`，不许手抄）；`status` → `released`。
2. `builtFrom.ndk`/`flags` 首产回写（16KB link-arg + strip 实值）。
3. `openProbes` ①② 逐项回写结论；③ 若触发则按 C8 冻结（不回写「继续推进」）。
4. `mavenCentral.published` 仅在 M4 核验后置 `true`。
5. 回写后必须重跑 `node tools/provision.mjs --check`（期望 exit 0）。
6. 可选新增 `builtFrom.cargoLock`（依赖 sha 粒度定案后，规划 §4.2/§8）。

---

## 4. provision CLI 与退出码契约（`tools/provision.mjs`，276 行）

### 4.1 命令面

| 用法 | 行为 | 落点 |
|---|---|---|
| `provision --check` | 校验**已投放**产物 `<root>/pnpm-cli/src/main/jniLibs/<abi>/<name>`；不存在 ⇒ 打印「未投放」+ **exit 2**；存在则跑门禁 + 兄弟文件唯一性 | `:208-229` |
| `provision --inspect <so>` | 只打印门禁报告，不写任何文件 | `:199-206` |
| `provision --out <dir> --from <so>` | 过门后投放唯一 `.so` 至 `<resolve(out)/<abi>/<name>>`（目录递归创建），并写版本元数据 | `:231-268` |
| `provision --version <v> --abi <a>` | 选择 lock 条目（缺省 `active`）；无该条目 ⇒ 抛错（exit 1）并列出现有条目 | `:185-193` |
| `--allow-page-align-4k` | 仅放行门禁⑦（bring-up）；打印「上架/16KB 设备不合格」警告 | `:162-167` |

### 4.2 门禁①–⑦（exec 型 profile；顺序 = 代码顺序）

| # | 判据 | 落点 | draft 语义 |
|---|---|---|---|
| ① | `e_type == ET_DYN(3)` | `:146` | — |
| ② | **PT_INTERP 恰 1 个且内容 = `/system/bin/linker64`**（读程序头取解释器串，`:83-88`） | `:148-150` | — |
| ③ | `e_machine == 183 (AArch64)` | `:151` | — |
| ④ | `DT_NEEDED` 全部 ∈ `matrix.allowedNeeded`（`.dynamic` 解析 `:115-127`；tag 1=NEEDED、14=SONAME） | `:153-155` | — |
| ⑤ | SHA-256 与锁一致（`node:crypto`，`:139`） | `:156-157` | 无锁值 ⇒ 记录实测哈希，恒过 |
| ⑥ | 大小与锁一致 | `:158-159` | 无锁值 ⇒ 记录实测大小，恒过 |
| ⑦ | 16KB 页对齐（min `PT_LOAD` p_align ≥ 0x4000，`:140`） | `:160` | `--allow-page-align-4k` 可放行（仅此一项可软化） |

ELF 解析边界（`parseElf` `:58-130`）：仅 **ELF64 小端**（magic `:59`、端序 `:60-61`，否则抛「不是 ELF 文件/只支持小端」→ exit 1）；`.dynamic`/`.dynstr` 经段表按名定位（`:109-110`）；**不解析** `.dynsym`（exec 型无 NAPI 门禁，文件头 `:14`）。

### 4.3 退出码契约（0/2/1）

| 退出码 | 场景 | 落点 |
|---|---|---|
| **0** | `--check`/`--inspect` 门禁全过（含兄弟文件唯一）；投放完成 | `:205,228,268` |
| **2** | 门禁不合格（`--inspect`）；**未投放**（`--check` 无产物）；投放前门禁不合格（「拒绝投放」） | `:205,216-217,235-238` |
| **1** | 用法/IO：缺 lock 文件、lock 非法 JSON、lock 无该 version/abi、缺 `--from`、投放后目标目录发现 `BANNED_IN_AAR` 文件、读文件失败（顶层 catch 兜底，stderr 一行 `[provision] 失败：<msg>`） | `:178-179,188-193,231,248,271-275` |

> ⚠️ 注意：`libnode.so` 已在投放目标目录时（`:246-250`）走 `throw` ⇒ **exit 1**（IO/用法类）而非 2——as-built 语义如此，评审时勿误判为门禁档位；兄弟文件唯一性在 `--check` 路径则是 **exit 2**（`:223-228`）。

### 4.4 投放与元数据

- 投放**只复制一个文件**（`:240-245`），绝不携带 `libc++_shared.so`/`libnode.so`（D6）。
- 元数据 `pnpm-cli/src/main/resources/META-INF/pnpm-cli.properties`（`:252-267`）：`tool.version` / `abi` / `name` / `size` / `sha256` /（可选 `paired.runtime`）/ `provisionedAt`（ISO 时间）。
- 输出契约：stdout 行首 `[provision] `（`:52-54`）；门禁逐行 `✔/✘ <判据> — <实测>`（`printGates` `:171-173`）；结尾 `门禁：全过/不合格`。

---

## 5. Gradle 骨架与发布面（as-built）

| 面 | 机制 | 落点 |
|---|---|---|
| 模块/坐标 | `rootProject.name="aosp-pnpm-cli"` + `include(":pnpm-cli")`；vanniktech 0.37.0 经 pluginManagement | `settings.gradle.kts:20-21,3` |
| 版本源 | `version = CLI_VERSION`（缺失即 error）；`afterEvaluate` 反射覆写 publication version（双保险） | `build:17-27` |
| 坐标 | `coordinates("io.github.fellow99", artifactId, CLI_VERSION)` + `pomFromGradleProperties` | `publish.gradle.kts:36-40` |
| 发布目标 | `publishToMavenCentral(false, NONE\|PUBLISHED)`——配置期不强制 Token；`AUTOMATIC_RELEASE=true` 才校验即发 | `publish.gradle.kts:42-56` |
| 组件形态 | 单 variant release + sources jar、无 Javadoc | `publish.gradle.kts:58-69` |
| 签名 | GPG 私钥经用户级属性；`keyData` 内存导入或 `secretKeyRingFile` 临时导出（临时文件 finally 删除） | `publish.gradle.kts:75-113` |
| 误发防护 | `RELEASE=false` 或 Token 缺失 ⇒ `signMaven*/publishToMavenCentral` 任务禁用 | `publish.gradle.kts:133-139` |
| POM 补丁 | `generatePomFile*` 后往 `<scm>` 注入 `<url>`（Portal 强制要求） | `publish.gradle.kts:115-131` |
| exec 打包 | `packaging.jniLibs.useLegacyPackaging = true`（D1；注释写明与 dlopen 库相反的原理） | `build:38-45` |
| ⚠️ 遗留 | `VERSION_NAME=0.1.0` 不参与坐标（模板遗留） | `gradle.properties:3` |

---

## 6. 脚本骨架与 CI（as-built + ⚠️ 发现）

### 6.1 脚本契约（exit 2 = SCAFFOLD 未实现）

- `scripts/fetch-pnpm-cli-source.mjs`（53 行）：`--pnpm <ver> [--out <dir>]`；用法错误 exit 1（`:41-44`）、SCAFFOLD exit 2（`:53`）；计划输入 `https://github.com/pnpm/pnpm`、输出 `<out>/pnpm-src/`（Rust workspace）。
- `scripts/build-pnpm-cli-android.sh`（59 行）：`--pnpm --api(24) --src --ndk --out`；缺参 exit 1（`:51-54`）、SCAFFOLD exit 2（`:59`）；文件头**七条纪律**（`:5-13`）= D4 前提/tier2+cargo-ndk/16KB link-arg/exec ELF/命名/运行期依赖 node/不下载上游。

### 6.2 scaffold CI 三段（`build-pnpm-cli-android.yml`，50 行）

| 段 | 步骤 | 落点 | 状态 |
|---|---|---|---|
| 触发/权限 | push(main)/PR/dispatch；`contents: read` | `wf:7-14` | ✅ |
| 配置门 | JDK 21 + Gradle 9.4.1 → `gradle :pnpm-cli:tasks --console=plain`（无 .so 必须过，FR-000-040/005） | `wf:23-35` | ✅（步骤）；运行记录 ⬜ |
| 退出码纪律 | `node --check tools/provision.mjs` → 跑 `provision --check` 期望 exit 2（FR-000-041） | `wf:37-45` | ⚠️ **语义缺陷**（下） |
| 语法门 | `node --check scripts/fetch-pnpm-cli-source.mjs` + `bash -n scripts/build-pnpm-cli-android.sh`（FR-000-042） | `wf:47-50` | ✅（本批复验等价命令全过） |

**⚠️ 退出码步骤缺陷（本批最重要的 as-built 发现）**：GitHub Actions Linux `run:` 默认 shell 为 `bash -e`——`node tools/provision.mjs --check` 以 2 退出时 step **立即失败**，`code=$?`（`wf:42`）永不执行，「其它非零码视为工具缺陷」的区分逻辑不可达；scaffold CI 每次运行该 step 必红，与「配置期全绿」定位矛盾。✅ 本会话实测 `bash -e -c 'false; echo unreachable'` 验证中断语义。**修法（M3.5-T6）**：`code=0; node tools/provision.mjs --check || code=$?`（或显式 `shell: bash` + 同写法）。另外 `wf:6` 头注释「流水线在 M1 接入」为旧口径（规划 §4.1 已登记回写）。

---

## 7. FR 交叉引用（spec.md ↔ 实现落点 ↔ 现状）

| FR（[spec.md](./spec.md)） | 需求（摘要） | 实现落点 | 现状 |
|---|---|---|---|
| FR-000-001 | root 名 + 仅 `:pnpm-cli` | `settings.gradle.kts:20-21` | ✅（TC-01） |
| FR-000-002 | library/namespace/minSdk 24/compileSdk 36/arm64-v8a | `build:30-37` | ✅（AAR manifest 实测） |
| FR-000-003 | 坐标 `io.github.fellow99:pnpm-cli:<CLI_VERSION>`、独立 semver | `gradle.properties:7` · `publish.gradle.kts:36-39` · `build:17-27` | ✅（机制） |
| FR-000-004 | `useLegacyPackaging=true` | `build:38-45` | ✅ |
| FR-000-005 | 无 .so 配置成功 | 模块无源码 + 零依赖 + CI 配置门 | ✅（TC-01/02） |
| FR-000-010~013 | lock 纪律：draft 不伪造哈希 / openProbes 三项 / 冻结出口判据 | `lock:2,13-14,22-26,40` + `provision.mjs:141-142,157-159,197` | ✅（draft） |
| FR-000-030~033 | 退出码 0/2/1 + 七项门禁硬失败 | `provision.mjs:24,132-168,205,216,228,237,275` | ✅（2/1 已实测；0 待首产） |
| FR-000-034~036 | 仅投放 `libpnpm_cli.so`；禁入 `libnode.so`；兄弟文件唯一；元数据 | `provision.mjs:36,38,194,221-225,240-267` | ✅（逻辑）；正样本 ⬜ |
| FR-000-040 | CI 配置门（无 .so 必过） | `wf:23-35` | ✅（步骤）；运行记录 ⬜ |
| FR-000-041 | 退出码纪律（未投放=2 预期） | `wf:37-45` | ⚠️ `bash -e` 缺陷（§6.2） |
| FR-000-042 | 语法门 | `wf:47-50` | ✅（复验） |

---

## 8. 文件清单（真实行数 ✅ 2026-11-02 实测）

| 文件 | 行数 | 作用 / 对应 FR |
|---|---|---|
| `settings.gradle.kts` | 21 | root + include + 插件/仓库（FR-000-001） |
| `gradle.properties` | 19 | GROUP / CLI_VERSION / POM_* / RELEASE=false（FR-000-003 / C6） |
| `gradle.properties.local.example` | 8 | 用户级凭据模板（C6） |
| `gradle/libs.versions.toml` | 5 | AGP 9.2.1（FR-000-002） |
| `publish.gradle.kts` | 139 | 发布面：坐标/签名/SCM 补丁/RELEASE 门禁（FR-000-003 / C6） |
| `pnpm-cli/build.gradle.kts` | 46 | 模块形态 + exec 打包 + 版本双保险（FR-000-002/003/004） |
| `pnpm-cli.lock.json` | 59 | 唯一事实源（draft）（FR-000-010~013 / C2） |
| `tools/provision.mjs` | 276 | exec 门禁 + 投放 + 元数据（FR-000-030~036 / C4） |
| `scripts/fetch-pnpm-cli-source.mjs` | 53 | 骨架 exit 2（规划 §4.3 ②；C1） |
| `scripts/build-pnpm-cli-android.sh` | 59 | 骨架 exit 2 + 七条纪律（规划 §4.3 ①–⑦；C5/C8） |
| `.github/workflows/build-pnpm-cli-android.yml` | 50 | scaffold 配置期 CI（FR-000-040~042；⚠️ §6.2） |
| `.gitignore` | 31 | 二进制/产物忽略（C3） |
| `pnpm-cli/THIRD-PARTY-NOTICES.md` | 18 | 许可声明（⚠️ :8 M1 措辞） |
| `README.md` / `README_zh.md` | 81 / 73 | 消费契约 + 运行期依赖必读（D5/D7 声明面） |
| `docs/000-工程规划.md` | 308 | 唯一规划基线 v2.0（C9） |
| 本模块既有三件 | spec 29 / tasks 13 / test-cases 17 | 未改动 |

---

## 9. 测试考虑（→ [test-cases.md](./test-cases.md) / [../overall-test-cases.md](../overall-test-cases.md)）

- **已执行（2026-11-02 记录 + 本批复验）**：TC-01/02（Gradle 配置与 AAR 产出）、TC-03（`--check` exit 2「未投放」，本批复验 ✅）、TC-04（`node --check` ×2 + `bash -n`，本批复验 ✅）、TC-05（二进制不入库）；本批补充复验 TC-EXT-01（`--version 99.0.0` ⇒ exit 1 + `现有：12.0.0/arm64-v8a`）。
- **待首产回归（⬜）**：门禁①–⑦ 正样本与负样本（TC-EXT-02~06：白名单外 DT_NEEDED、PT_INTERP≠linker64、4KB 对齐、jniLibs 混件、投放后 `--check` exit 0）。
- **Gradle 侧验证说明**：本批规格会话未重跑 Gradle（本机 PATH 为 Gradle 8.12，与 AGP 9.2.1 不匹配；TC-01/02 依赖仓库已有执行记录与 `.gradle/9.4.1` 缓存佐证）——**skipped，原因如上**。

---

## 10. 待实现清单与验收判据（M3.5 出口，规划引用）+ 本批发现

| # | 项 | 验收判据 | 归属 |
|---|---|---|---|
| 1 | 探针①② 执行与结论回写 | `lock.openProbes` ①② 回写；② 产出可编性清单 | M3.5-T1/T2（规划 §10） |
| 2 | fetch/build 真实实现 | `.dist/src` 可复现；`libpnpm_cli.so` 过七项门禁 | M3.5-T3/T4/T5 |
| 3 | CI 真实流水线 + **修复 §6.2 `bash -e` 缺陷** + 「M1」措辞回写 | 一条流水线重出同口径产物；退出码步骤对 2/其它非零可区分 | M3.5-T6 |
| 4 | lock 回写（size/sha256/ndk/flags/status；published 随 M4） | `--check` exit 0（released 态 ⑤⑥ 生效比对） | M3.5 首产 |
| 5 | 真机回程 P1–P7 + store 注入 a/b 定案 | 规划 §7.1 判据逐条过 | M3.5-T7 |
| 6 | `VERSION_NAME=0.1.0` 清理或注释 | 无双版本属性混淆 | M3.5 收口（顺手） |
| 7 | ⚠️ `gates` 存档字段无工具读取（本模块与同族仓同口径） | 若要防漂移：加 lock 自校验或在注释标明「存档」语义 | 触发式 |
| 8 | ⚠️ `pairedRuntime` 可选字段被工具消费而 lock 未登记 | 登记于 §3.2（本批已完成登记）；lock 增加该字段时同步本表 | 触发式 |
