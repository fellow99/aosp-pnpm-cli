# ARCHITECTURE.md —— `aosp-pnpm-cli` 整体架构（as-built + 规划引用）

> **性质**：项目级架构规格。回答「分几层、制品怎么组成、门禁怎么强制、和 node-cli/node-runtime 怎么组合、边界在哪」。
> **依据**：[`constitution.md`](./constitution.md)（C1–C9 / N1–N5）· [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（唯一权威，下称「规划 §」，其 §3 架构图与 D1–D8 决策是本文的提取源）· 源码 ✅实测（行号即本仓当前行号，2026-11-02）。
> **标记**：✅ 已实现 / ⬜ 规划未实现（只以「规划」字样出现，标注 §）/ ⚠️ 冲突或风险。
> **版本**：v1.0 · 2026-11-02 · 状态：**M0 骨架完成**（Gradle 骨架 + exec 门禁 + draft lock + 骨架脚本 + 配置期 CI）；M3.5 交叉编译未开工。

---

## ① 分层架构

```
┌─ 消费方 App 进程（运行期组合 —— 规划引用，规划 §3 图 / §7 契约表；非本仓代码）──────────┐
│  环境引导层（bridge 载荷或 Kotlin，消费方职责）                                        │
│   注入 PATH / HOME / TMPDIR / XDG_DATA_HOME⬜ / LD_LIBRARY_PATH / npm_config_*        │
│   node ← aosp-node-cli 供给：libnode_cli.so 与 libpnpm_cli.so 同在 nativeLibraryDir； │
│   命令名 `node` 经 symlink 农场 $HOME/bin 提供（缺失则 lifecycle scripts 失败）        │
│        │ pty.spawn(<nativeLibraryDir>/libpnpm_cli.so, …)                              │
│        ▼                                                                              │
│  pnpm 子进程（exec 型 PIE；独立生命周期；forkpty → setsid → TIOCSCTTY）                │
│   package lifecycle scripts（JS）⇒ 经 PATH 上的 node 再启动子进程                     │
│   store / 缓存 / 配置 ⇒ filesDir（nativeLibraryDir 只读）                             │
└───────────────────────────────────────────────────────────────────────────────────────┘
              ▲ AAR 供给（useLegacyPackaging=true ⇒ 物理落盘 nativeLibraryDir，D1）
┌─ 本仓制品层（AAR 容器 —— as-built ✅）────────────────────────────────────────────────┐
│  pnpm-cli-<CLI_VERSION>.aar（模块 :pnpm-cli，无任何 Kotlin/Java 源，D8）               │
│   ├── jni/arm64-v8a/libpnpm_cli.so        ← tools/provision.mjs --out 投放（不入库）⬜│
│   └── META-INF/pnpm-cli.properties        ← 投放时生成的版本元数据 ⬜                 │
│  （无 assets、无壳脚本；npm/corepack 归 node-cli 线）                                 │
└───────────────────────────────────────────────────────────────────────────────────────┘
              ▲ 门禁全过才可投放（exit 2 硬失败）
┌─ 门禁层（host 侧 —— as-built ✅）─────────────────────────────────────────────────────┐
│  pnpm-cli.lock.json（唯一事实源：版本/大小/sha256/门禁/出处/openProbes，C2）           │
│  tools/provision.mjs（唯一投放入口）：                                                │
│   门禁① ET_DYN ② PT_INTERP=/system/bin/linker64 ③ AArch64                             │
│       ④ DT_NEEDED⊆白名单 ⑤ sha256 ⑥ size（draft 只记录）⑦ 16KB                        │
│   投放禁区：jniLibs 兄弟文件唯一；BANNED_IN_AAR=[libnode.so]                          │
│  scripts/fetch（⬜ M3.5）→ scripts/build（⬜ M3.5）→ provision ✅                      │
│  CI（scaffold）：gradle :pnpm-cli:tasks + provision 退出码纪律⚠️ + 语法门（✅/⚠️）     │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

| 层 | 归属 | 进哪个发布物 | 现状 |
|---|---|---|---|
| 门禁层（lock + provision + 脚本 + CI） | **本仓** | 仓库配套物（不进 AAR） | ✅（fetch/build 逻辑 ⬜ M3.5） |
| 制品层（AAR 容器 + libpnpm_cli.so） | **本仓** | `io.github.fellow99:pnpm-cli` AAR | 容器 ✅ / 本体 ⬜ M3.5 |
| 环境引导层（PATH/HOME 注入、symlink 农场、pty.spawn） | **消费方** | 消费方 APK | ⬜ 规划（规划 §3 图 / §7，本仓只定义契约） |
| node 本体 / `libc++_shared.so` | **node-cli / node-runtime 线** | 消费方依赖 | ⬜（运行期依赖声明 ✅ README + lock note，D5/D6） |

---

## ② 制品组成（as-built 实测）

发布物 = 纯二进制 AAR（D8）。✅ 实测 debug AAR（未投放状态）仅 4 条目，证明容器本身零业务内容：

| AAR 条目（`pnpm-cli-debug.aar`） | 大小 | 说明 |
|---|---|---|
| `AndroidManifest.xml` | 207 B | `package=org.fellow99.pnpmcli`，`minSdkVersion=24` |
| `R.txt` / `classes.jar` | 0 B / 22 B | 空资源/空类（无源码模块的必然形态） |
| `META-INF/com/android/build/gradle/aar-metadata.properties` | 157 B | `minCompileSdk=36` |

投放后预期形态（规划 §5）：`jni/arm64-v8a/libpnpm_cli.so`（exec 型 PIE：`ET_DYN` + `PT_INTERP=/system/bin/linker64` + 16KB 对齐）+ `META-INF/pnpm-cli.properties`（`tool.version/abi/name/size/sha256/provisionedAt`，`tools/provision.mjs:252-267`）。

---

## ③ 门禁层：exec profile 与投放纪律（as-built）

```
fetch(⬜M3.5) ──→ build(⬜M3.5，cargo 交叉编译+strip) ──→ provision --out --from ──→ AAR
                                                             │
                              pnpm-cli.lock.json（唯一事实源）┘
门禁七项（任一不过 ⇒ exit 2，无「警告后继续」档位，C4）：
  ① e_type = ET_DYN（PIE 可执行）                 provision.mjs:146
  ② PT_INTERP 恰 1 个且 = /system/bin/linker64    provision.mjs:148-150   ← 与 dlopen 库门禁相反
  ③ e_machine = AArch64                           provision.mjs:151
  ④ DT_NEEDED ⊆ matrix.allowedNeeded             provision.mjs:153-155
  ⑤ SHA-256 与锁一致（draft：只记录不比对）        provision.mjs:156-157
  ⑥ 大小与锁一致（draft：同上）                    provision.mjs:158-159
  ⑦ 16KB 页对齐（min LOAD align ≥ 0x4000）        provision.mjs:160（--allow-page-align-4k 仅 bring-up，:162-167）
投放禁区：
  jniLibs 只含 libpnpm_cli.so（兄弟文件唯一性）    provision.mjs:221-225
  libnode.so 绝不进入本 AAR（BANNED_IN_AAR）       provision.mjs:38,246-250
```

**为什么 exec 门禁②与 pty 相反**：pty 是 dlopen 进宿主进程的库，PIE 形态（有解释器段）必崩；本仓 pnpm 是 `execve` 启动的独立进程，**必须**由系统动态链接器 `/system/bin/linker64` 启动——两种形态判据互为镜像（`provision.mjs:9-11` 文件头明示）。**为什么④是白名单而不是必含**：独立进程只允许依赖系统库 + 登记的随行库；`libc++_shared.so` 由消费方 node-runtime 供给（D6），重复打包会包内同名冲突。

---

## ④ 与 node-cli / node-runtime 的运行期组合（规划引用）

**本 AAR 不是孤岛（D5）**：package lifecycle scripts 是 JS，由 PATH 上的 node 执行。运行期组合关系（全部为**规划契约**，规划 §3 图 + §7 注入表；本仓 as-built 只有 lock `matrix.note` 与 README 的声明面）：

| 件 | 供给方 | 组合点 |
|---|---|---|
| `libpnpm_cli.so`（pnpm 本体） | **本仓** AAR | `nativeLibraryDir`（useLegacyPackaging=true 物理落盘，D1） |
| `libnode_cli.so`（node 本体）+ 命令名 `node` | 消费方引入 `io.github.fellow99:node-cli` | 同在 `nativeLibraryDir`；`node` 命令经 symlink 农场 `$HOME/bin` 上 PATH |
| `libc++_shared.so` | 消费方引入 `io.github.fellow99:node-runtime` | 同在 `nativeLibraryDir`（若首产 DT_NEEDED 出现）；本 AAR 白名单登记、不随包（D6） |
| `PATH`/`HOME`/`TMPDIR`/store/`npm_config_*` 注入 | 消费方环境引导层 | 规划 §7 注入表；store 必须落 filesDir（P3 探针定案 ⬜） |

**边界三条（消费方文档必须写明，规划 §7 已知限制）**：① 未注入 node 时带 lifecycle scripts 的包 `pnpm install` 失败；② `pnpm self-update` 禁用（D7：C5 红线 + nativeLibraryDir 只读）；③ 生态包自带平台二进制（运行期下载 .so）触碰 C5 红线 ⇒ 纯 JS 包可用、含平台二进制的包不可用（能力边界声明 ⬜ M3.5 评估口径，规划 §7 已知限制 3 / R10）。

---

## ⑤ 关键决策落点对照（规划 §3.1 D1–D8 → 实现）

| # | 决策（规划 §3.1） | 实现落点 | 状态 |
|---|---|---|---|
| D1 | exec 打包 `useLegacyPackaging=true` | `pnpm-cli/build.gradle.kts:38-45`；lock `matrix.execPackaging` 登记（`:56`） | ✅ |
| D2 | 版本 = pnpm 上游版本，独立 semver（CLI_VERSION） | `gradle.properties:7` · `publish.gradle.kts:36-39` · `pnpm-cli/build.gradle.kts:17-27` · lock `active` | ✅ |
| D3 | 源码自建（钉版 tarball + SHA-256，fetch 不改源） | `scripts/fetch-pnpm-cli-source.mjs`（骨架 ✅ 接口/纪律；实现 ⬜ M3.5-T3） | 部分 |
| D4 | 前提：pnpm 12+ 为 Rust 独立二进制；推翻 ⇒ 冻结重评 | lock `openProbes[2]`（`:25`）+ `scripts/build-pnpm-cli-android.sh:6-7` | ✅（登记面） |
| D5 | 运行期依赖 node-cli（本 AAR 不是孤岛） | `gradle.properties:9` POM description · lock `builtFrom.runtimeDeps`（`:18`）· `pnpm-cli.lock.json:57` note · README 双语必读声明 | ✅ |
| D6 | `libc++_shared.so` 只入白名单、不随包 | lock `matrix.allowedNeeded[5]` + note（`:54,57`）；无打包 assets | ✅（声明面；首产实际 DT_NEEDED ⬜） |
| D7 | `pnpm self-update` 禁用 | README（规划 §7 已知限制 2 的声明面）；M3.5-T7 回写消费方文档 ⬜ | ✅（声明面） |
| D8 | 纯二进制 AAR：无 assets、无壳脚本 | 模块无源码 ✅；debug AAR 4 条目实测 ✅ | ✅ |

---

## ⑥ 构建与发布数据流（M3.5 部分 ⬜ 为规划引用）

```
[pnpm 上游] --tarball+sha256--> .dist/src/pnpm-src        fetch（⬜ M3.5-T3，C1 不改源）
     │
     └--> cargo build --release --target aarch64-linux-android
          RUSTFLAGS 16KB link-arg --> strip(llvm-strip)     build（⬜ M3.5-T4/T5，规划 §4.3 ①–⑦）
              │ libpnpm_cli.so + BUILD/FETCH-METADATA
              ▼
          provision --inspect（全门禁） --> GitHub Release（tar.gz + sha256，⬜ M3.5-T6）
              │ --out pnpm-cli/src/main/jniLibs
              ▼
          gradle assemble --> pnpm-cli-<ver>.aar --> Maven Central（⬜ M4，RELEASE=true 两段式第二段）
              │
              └--> lock 回写 size/sha256/ndk/flags/status=released（openProbes 逐项回写结论）
```

- **发布两段式**（规划 §10.1）：GitHub Release 先行（tag `v<pnpmver>` 触发，前置 openProbes ①② 通过）→ Maven Central 随后（runbook；`RELEASE=false` 默认，`publish.gradle.kts:133-139` 卫生门禁 ✅）。
- **as-built 已通段**：`provision` 全链 ✅（`--check` 未投放 exit 2 ✅ 本会话复现）、Gradle 配置/构建 ✅（TC-01/02，`000-scaffold/test-cases.md` 执行记录）、CI scaffold 配置期 ✅（⚠️ 退出码步骤语义缺陷，TECH §5-1）。

---

## ⑦ 架构层面的已知缺口（Phase 归属）

| 缺口 | 归属 | 影响 |
|---|---|---|
| fetch/build 真实实现（tarball 下载/校验、cargo 交叉编译、strip、元数据归档） | M3.5-T3/T4/T5（规划 §10） | 无制品可产出（当前 lock 为 draft 的根因） |
| CI 真实流水线（rust/ndk 装机 + Release + 归档）替换 scaffold CI | M3.5-T6（规划 §9） | 无法一条流水线重出同口径产物 |
| CI 退出码步骤 `bash -e` 语义缺陷 | M3.5-T6 顺手修 | 「未投放=2 是预期」的门禁在 CI 上实际表现是 step 失败（✅ 机制仍拦截，但意图不可达，TECH §5-1） |
| 仓内 scaffold「M1」措辞回写（workflow:6、THIRD-PARTY-NOTICES.md:8） | M3.5 顺手项（规划 §4.1） | 文档口径与规划 v2.0 不一致 |
| lock 回写（size/sha256/ndk/flags/status；openProbes 结论） | M3.5 首产（规划 §8） | draft 态维持「只记录不比对」 |
| 真机回程探针 P1–P7（pnpm -v / lifecycle 经 node / store 落点 / self-update 失败路径 / 16KB 设备 / TLS） | M3.5-T7（规划 §7.1） | 运行期契约无实测判据 |
| store 注入方式 a/b 定案、TLS 根证书来源 | M3.5-T7 + P3/P7 探针（规划 §7/§11 R5/R6） | 运行期注入表留 ⬜ |
