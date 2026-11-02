# constitution.md —— `aosp-pnpm-cli` 项目宪法（治理原则）

> 本文件是 `io.github.fellow99:pnpm-cli`（Android 版 pnpm 命令行，exec 型制品线）的**最高约束**。
> 任何 `spec.md` / `plan.md` / 实现 / CI / 评审都必须先过这一关。
> 原则**不是发明的**，而是从唯一规划基线 [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（v2.0，其 §13「与工作区纪律的对应」是本表的提取源）与**已落地实现**中提取的（依据附在每条末尾）。
> - 工作区级动机与路线取舍：[`../../docs/aosp+node+python+git运行方案.md`](../../docs/aosp+node+python+git运行方案.md)（§2 平台硬约束 C1–C6 · §4.2 exec 门禁 · §9.3 pnpm 决策与遗留探针）
> - 版本：v1.0 · 2026-11-02 · 状态：**生效**
> - 适用：`pnpm-cli/`（AAR 容器）· `tools/` · `scripts/` · `.github/workflows/` · `pnpm-cli.lock.json` · `specs/`

---

## 一、九条不可协商的原则

### C1. fetch 不改源（Fetch Never Patches）

**构建输入一律是上游官方源码 tarball（钉版 + SHA-256），逐字节原样使用。**

- 交叉编译参数（16KB link-arg、NDK linker、feature 裁剪）只经 **cargo 配置注入**，禁止修改上游源码或打源码补丁（规划 §4.2/§4.3/§13）；
- `scripts/fetch-pnpm-cli-source.mjs` 的唯一职责是「下载 → 校验 → 解压」，不改写任何文件。

依据：规划 §4.2（pnpm 源钉选行）· §13「fetch 不改源」· 决策 D3 · `scripts/fetch-pnpm-cli-source.mjs:2-3`（文件头「取构建输入（pnpm 官方源码，不改源）」）。

---

### C2. lock 唯一事实源（Single Source of Truth）

**版本、大小、SHA-256、门禁结论、构建出处，只有一个权威出处：仓库根 `pnpm-cli.lock.json`。**

- 工具（`tools/provision.mjs`）只从 lock 读期望值，**不在代码里硬编码**任何版本/哈希；
- lock 的当前条目是 **draft**（`status=draft`、`size=0`、`sha256=""`）——**draft 不伪造哈希**，首个真实 CI 构建后回写并以 CI 实际钉住的 pnpm 版本为准（规划 §2/§8）；
- lock 内的 `openProbes` 是**随代码走的决策记录**：三项遗留探针与「前提被推翻即冻结重评」的出口判据必须登记在案（规划 §4.1/§8）。

依据：规划 §2（版本规则）· §8（唯一事实源）· `pnpm-cli.lock.json:2`（$comment）· `tools/provision.mjs:5`（「唯一事实源是仓库根的 pnpm-cli.lock.json」）。

---

### C3. 二进制不入库（Binaries Never Committed）

**`libpnpm_cli.so` 及一切构建产物不进版本库；库里只有锁文件、工具与元数据。**

- 投放目标 `pnpm-cli/src/main/jniLibs/**/*.so` 被 `.gitignore` 忽略（`.gitignore:23`）；`.dist/`（fetch 输出）、`build/out/`（构建输出）、`target/`（Rust）一并忽略（`.gitignore:26-28`）；
- 二进制由 CI 产出、由 `tools/provision.mjs` **幂等投放**进模块 jniLibs——仓库可随时按 lock 重建。

依据：规划 §13「二进制不入库」· `.gitignore:22-28` · `tools/provision.mjs:6`。

---

### C4. 门禁强制（Gates Are Hard）

**exec 型 ELF 门禁七项（①–⑦）任一不过即硬失败（`exit 2`），没有警告后继续档位。**

- ① `ET_DYN`（PIE）· ② **PT_INTERP 恰 1 个且 = `/system/bin/linker64`**（与 dlopen 库门禁相反）· ③ `AArch64` · ④ `DT_NEEDED` 全部在 lock `matrix.allowedNeeded` 白名单 · ⑤ SHA-256 与锁一致（draft 只记录不比对）· ⑥ 大小与锁一致（同前）· ⑦ 16KB 页对齐（`--allow-page-align-4k` 仅 bring-up 放行，上架/16KB 设备不合格）（规划 §6）；
- 投放面两条禁区：jniLibs **只允许** `libpnpm_cli.so` 一个原生件（兄弟文件唯一性）；`libnode.so` 绝不进入本 AAR（`BANNED_IN_AAR`，由 node-runtime 供给方统一供给）；
- 「draft 只记录不比对」是**唯一**的软化档位，且必须显式打印警示，不许静默。

依据：规划 §6（exec 型门禁 profile）· §12（质量门禁汇总）· `tools/provision.mjs:9-16,146-167,221-225,246-250`。

---

### C5. 不下载上游二进制 / 运行期不下载可执行代码（No Upstream Prebuilts, No Runtime Exec Downloads）

**构建期：一律源码自建（C1），无论上游有无 android-arm64 预编译分发。运行期：不做任何「下载可执行代码」的设计。**

- 探针①（上游 esbuild/biome 式平台二进制可得性）是**记录性**核实——结论无论如何都 CI 自建（规划 §4.1 ①）；
- `pnpm self-update` **禁用**（决策 D7）：既是「运行期下载可执行代码」红线，物理上也不可能成功（nativeLibraryDir 只读）；
- pnpm 生态包自带的平台二进制（esbuild/biome 式 optionalDependencies 会在安装时运行期下载 .so）触碰同一红线 ⇒ 声明为**能力边界**：纯 JS 包可用，含平台二进制的包不可用，绝不静默降级（规划 §7 已知限制 3）。

依据：规划 §4.1 ① · §7 已知限制 2/3 · D3/D7 · `README_zh.md:20`（「一律源码自建，不下载上游预编译二进制」）· `scripts/build-pnpm-cli-android.sh:13`。

---

### C6. 发布卫生（Release Hygiene）

**默认不发布；凭据永不入库；发布走两段式且每步可核验。**

- `RELEASE=false` 默认：Central 上传与签名任务在无 `-PRELEASE=true` 且无 Token 时**被禁用**（防误发，`publish.gradle.kts:133-139`）；
- Central Portal Token / GPG 私钥只从**用户级** `~/.gradle/gradle.properties` 读取（`gradle.properties.local.example` 是唯一入库的模板；`publish.gradle.kts:13-14`）；
- 发布两段式：GitHub Release 先行（M3.5-T6）→ Maven Central 随后（M4，runbook §2→§4→§5→§6）；lock `mavenCentral.published` 只在核验回填后置 `true`（规划 §10.1/§8）。

依据：规划 §10.1 · §12（发布卫生行）· `gradle.properties:18-19` · `publish.gradle.kts:12-14,133-139`。

---

### C7. 未实测标 ⬜（Evidence Discipline）

**⬜ 为未实测项，绝不当作已定事实引用。**

- 四种标记贯穿全部规格与结论：✅ 实测（本仓可复核，附 `path:line` 或命令输出）/ ⬜ 未实测（规划项，引用时必须带「规划」字样）/ 🔶 沿用（同族仓一手记录，本仓未复现）/ ⚠️ 冲突或风险；
- 规划（M3.5 rust 交叉编译、openProbes 探针、真机回程 P1–P7 等）只能以「规划引用」出现并标注出处 § 号，**不得**写成既成事实；
- 规格文档未确认处以 `[NEEDS CLARIFICATION]` 标注（每份最多 3 处），禁止占位内容。

依据：规划文件头纪律（`docs/000-工程规划.md:11`）· 本规格体系（specs-as-built 方法论）。

---

### C8. 前提推翻即冻结（Falsified Premise ⇒ Freeze）

**本仓存在的决策前提是「pnpm 12+ 为 Rust 独立二进制（exec 型）」；该前提被推翻 ⇒ 冻结本仓计划并重评，不得带病推进。**

- 出口判据 = lock `openProbes` ③：若探针②证实 pnpm 实为 **NAPI 混合形态、由 node 进程内加载**，则本线作废，按 pty 式伴生件重评（NODE_MODULE_VERSION 配对 + node-cli 进程内 napi 符号解析）——属另一条制品线（规划 §4.1 ③ / D4 / R2）；
- 探针②（Rust 依赖在 `aarch64-linux-android` 下的可编性试编）是 M3.5 的**出口门禁**，不阻塞建仓但阻塞首产（规划 §4.1 ②）。

依据：规划 §3.1 D4 · §4.1 · §11 R1/R2 · `pnpm-cli.lock.json:25`（openProbes[2]）· `scripts/build-pnpm-cli-android.sh:6-7`。

---

### C9. 先改文档再改实现（Docs Before Code）

**`docs/000-工程规划.md` 是唯一规划基线（v2.0）；变更走「先改本文件，再改实现」，偏差随里程碑直接回写。**

- 规格文档（specs/）是规划的**收敛/展开版**：只做细化与验收，不复制粘贴，结论回引规划 § 号；
- 实现与本文件的偏差必须随里程碑回写规划，或在本规格体系登记为「文档化偏差」并给理由；
- 版本规则：制品版本 = pnpm 上游版本（`CLI_VERSION`，独立 semver，D2）——任何版本语义变更先改规划 §2 再改 `gradle.properties`/lock。

依据：规划文件头（`docs/000-工程规划.md:3,7`「变更走先改本文件再改实现」）· 规划 §13 末行。

---

## 二、命名与打包纪律（实现必须一致）

| # | 纪律 | 内容 | 依据 |
|---|---|---|---|
| N1 | 产物命名 | `libpnpm_cli.so`（`lib<工具>_cli.so` 约定）；**禁 `libnode.so`** | 规划 §4.2/§6 · `tools/provision.mjs:36,38` |
| N2 | 打包 | `useLegacyPackaging=true`（exec 必须物理落盘 `nativeLibraryDir`；targetSdk≥29 SELinux 禁从可写目录 exec） | D1 · `pnpm-cli/build.gradle.kts:38-45` |
| N3 | 纯二进制 AAR | 无 assets、无壳脚本；pnpm 本体即命令入口；npm/corepack 归 node-cli 线 | D8 · 规划 §5 |
| N4 | 依赖白名单 | DT_NEEDED ⊆ 系统库 + `libc++_shared.so`（lock 登记、**不随包**，由消费方 node-runtime 供给） | D6 · `pnpm-cli.lock.json:48-57` |
| N5 | 版本源 | 坐标版本唯一取 `CLI_VERSION`（= pnpm 上游版本）；与 node 系无配对约束 | D2 · `publish.gradle.kts:33-39` |

---

## 三、评审清单（Reviewer Checklist）

评审任何 `spec.md` / `plan.md` / PR / workflow 时逐条问：

- [ ] 是否修改了上游源码或引入源码补丁？（C1）
- [ ] 版本/哈希/出处是否只来自 lock？draft 是否仍「只记录不比对」？（C2）
- [ ] 是否有任何二进制/产物试图入库？（C3）
- [ ] 门禁是否被绕过或软化（白名单外 DT_NEEDED、4KB 对齐带入上架、兄弟文件混入）？（C4）
- [ ] 是否出现「下载上游预编译」或「运行期下载可执行」路径？（C5）
- [ ] 发布动作是否被 RELEASE/Token 双门禁挡住？凭据是否只在用户级？（C6）
- [ ] 每条非显然结论是否带 ✅/⬜/🔶/⚠️ 标记？规划是否被误写成事实？（C7）
- [ ] 是否触碰「前提被推翻」场景而未触发冻结重评？（C8）
- [ ] 改动是否先落规划文档、再落实现？（C9）

---

## 四、变更流程

1. 改版本语义 / 决策 ⇒ **先**改 `docs/000-工程规划.md`（§2/§3），再改 `gradle.properties`、`pnpm-cli.lock.json` 与实现（C9）。
2. lock 回写（M3.5 首产）⇒ `size`/`sha256`/`ndk`/`flags`/`status=released` 逐项回填，重跑 `node tools/provision.mjs --check` 全过；`openProbes` 逐项回写结论（闭环即删条目）。
3. 改 `tools/provision.mjs` 门禁口径 ⇒ 必须同步规划 §6 门禁 profile 表与本规格 `000-scaffold/plan.md` §4，退出码契约（0/2/1）不变更需显式提版本。
4. 改 CI ⇒ scaffold CI（配置期）与 M3.5 真实流水线的边界以规划 §9 为准；替换前不得删除配置期门禁（FR-000-040）。
