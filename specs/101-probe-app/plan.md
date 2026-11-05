# 101-probe-app — 设备回程探针载体（probe-app）技术方案（plan.md）

> **性质**：**实现特定（as-built）**。本模块的「实现」就是 `probe-app/` 现有代码 + 本批新增的
> autorun 无头触发；本文档逆向记录真实实现与真实文件/行号（对照规格 [spec.md](./spec.md) 的
> `FR-101-PROBE-001…009`）。
> **技术栈事实**：独立 Gradle 工程（不在根 `settings.gradle.kts` 内——000 单模块红线不动，
> `probe-app/settings.gradle.kts:17-19` 注释即声明）；Gradle 9.4.1 + AGP 9.2.1
> （`probe-app/settings.gradle.kts:20-22`）；纯 Java + org.json 报告；无 AndroidX
> （`probe-app/gradle.properties:1-4`）；全符号 Java 17（`probe-app/app/build.gradle.kts:25-28`）。
> **状态**：⚠️ as-built 归档（2026-11-05）；本批 APK 构建已验证，设备运行 ⏳ 由编排者统一排队。

---

## 1. 技术上下文

| 项 | 取值 | 说明 |
|---|---|---|
| 载体模块 | `probe-app/app`（applicationId `org.fellow99.pnpmprobe`） | `app/build.gradle.kts:5-18`；`minSdk 24` / `targetSdk 35` / `compileSdk 36` |
| ABI | 仅 `arm64-v8a`（`ndk.abiFilters`） | `app/build.gradle.kts:17`；x86_64 模拟器经翻译层运行只记录（TC-C16），不替代 arm64 判据 |
| 依赖制品 | `app/libs/pnpm-cli-release.aar`（CI 首产归档解包，不入库） | `app/build.gradle.kts:37-39`；被 `.gitignore` 忽略（`probe-app/.gitignore`） |
| exec 入口 | `nativeLibraryDir/libpnpm_cli.so`（本体即命令入口，D8 无二壳无 assets） | `MainActivity.java:56-57`（nativeDir/pnpmExe） |
| 注入目录 | `filesDir/home`+`tmp`+`xdg`+`work`+`store`+`init-probe` | `MainActivity.java:58-63` |
| 期望值 | pnpm 12.16.1 / size 47317088 / sha256 `e5378cfa…`（引用 lock，C2 不写死副本） | `pnpm-cli.lock.json` `active.version` / `releases[0]` |
| 基准跑批 | ⏳ 本批未跑（编排者排队）；先例：003-cli adb 层 / App 探针 19 条 = 17 PASS + 2 BLOCKED（2026-11-04，`specs/003-cli/test-cases.md` App 探针执行记录节） | `specs/003-cli/test-cases.md:87-91` |

## 2. 宪法合规

| 条目 | 落点 |
|---|---|
| C2 唯一事实源 | 期望值（size/version）全引 `pnpm-cli.lock.json`；探针不内嵌可枚举版本串副本 |
| C5 不运行期下载可执行 | TC-C10（self-update）判据＝sha256 前后一致（P5 实测口径），如实记录运行期下载行为并回填消费方文档 |
| C6 判据纪律 / C7 未实测标 ⬜ | 缺件用例（C06a/C15）BLOCKED 不删不伪造；记录性用例（C11/C16）`RECORD:` 不断言；设备跑批 ⬜ 不标 ✅ |
| C8 先改文档再改实现 | 本批判定纪律修正（P5/L1/R5/R6）均已先落规划 §7/§7.1 与 003-cli test-cases，探针实现为既有件（2026-11-03/04 已回写） |
| C9 先改规划再改规格 | 本模块 as-built 结论（RE 载体差异、autorun、19 探针全集）全部回引规划 §/003-cli 正典，无超前断言 |

## 3. 实现结构（as-built 真实文件与行数，2026-11-05 快照）

| 文件 | 行数 | 职责 |
|---|---|---|
| `probe-app/app/src/main/java/org/fellow99/pnpmprobe/MainActivity.java` | 442（本批 +autorun） | 载体初始化 / 注入 / 执行器 / 19 条探针表 / 报告（详见 §4） |
| `probe-app/app/build.gradle.kts` | 40 | application 配置 + `abiFilters` + `packaging.jniLibs.useLegacyPackaging=true`（D1）+ 容器 AAR 依赖 |
| `probe-app/app/src/main/AndroidManifest.xml` | 20 | 仅 `INTERNET` 权限；`extractNativeLibs=true`；单一 LAUNCHER activity（`exported=true`） |
| `probe-app/build.gradle.kts` | 3 | 根工程（`com.android.application` 插件声明 apply false，独立工程） |
| `probe-app/settings.gradle.kts` | 22 | pluginManagement / dependencyResolutionManagement（google+mavenCentral+gradlePluginPortal）/ include ":app" |
| `probe-app/gradle.properties` | 4 | `org.gradle.jvmargs=-Xmx2g` · `android.useAndroidX=false` · `PROBE_APP_VERSION=12.16.1` |
| `probe-app/local.properties` | 1 | `sdk.dir=C:/Users/Administrator/AppData/Local/Android/Sdk`（本机，不提交） |
| `probe-app/.gitignore` | 5 | `build/` `.gradle/` `local.properties` `app/libs/*.aar` 不入库（C3） |

## 4. 关键实现点（每条可回溯）

1. **载体就绪（FR-101-PROBE-001）**：`onCreate`（`MainActivity.java:52-93`）初始化 `nativeDir`/
   `pnpmExe` 与全部 filesDir 子目录；TC-C00（`237-248`）为纯文件面检查（`argv=null` 不 exec）：
   `so.isFile()` + `size==47317088L`（lock 落锁值）+ NLDIR `.so` 数==1（兄弟文件唯一，镜像
   `provision BANNED_IN_AAR`）。`useLegacyPackaging=true`（`app/build.gradle.kts:20-23`，D1）保证
   exec 型产物物理落盘 nativeLibraryDir（targetSdk≥29 禁从 filesDir exec）。
2. **注入（FR-101-PROBE-002）**：`baseEnv()`（`104-111`）注入 `HOME`/`TMPDIR`/`XDG_DATA_HOME`/
   `PATH`（nativeLibraryDir:$HOME/bin:/system/bin）；`exec()`（`130-134`）对子进程追加
   `ANDROID_ROOT=/system`/`ANDROID_DATA=/data`/`LD_LIBRARY_PATH=nativeDir`（exec 子进程默认 linker
   namespace；规划 §7）。`env.clear()+putAll`（`126-127`）整表替换隔离宿主残留；TC-C00b
   （`249-254`）以 `envKv("HOME","","TMPDIR","","XDG_DATA_HOME","")` 删除注入验证裸环境直启。
3. **执行器（FR-101-PROBE-003）**：`exec()`（`121-153`）——`ProcessBuilder` 直接 exec
   `libpnpm_cli.so`（无 sh 包装，D8），`redirectErrorStream(false)` 分流、双 read 泵、`waitFor`
   超时 `destroyForcibly`、启动异常入 `failReason`；`pn(...)`（`169-174`）命令组装。
4. **探针表（FR-101-PROBE-005）**：`probes()`（`232-355`）声明式 `new Probe(...)` 数组，19 条
   TC-C00…C16；顺序 = 载体/环境（C00/C00b）→ 基础无网络（C01/C01b/C02/C03/C04/C07）→ store 双案
   （C08a→C09→C08b，FR-101-PROBE-006 顺序依赖）→ 网络安装（C05/C06a/C06b/C14）→ 红线与边界
   （C10/C11/C15/C16）。`Probe`/`Out`/`Check` 结构见 [spec.md](./spec.md) §6（`213-230`）。
5. **判据形态（FR-101-PROBE-004/008）**：`runAll()`（`359-441`）runner 统一 verdict 分类
   （`402-405`：null→PASS / `BLOCKED:`→BLOCKED / `PASS-`/`RECORD:`→PASS / `FAIL-`→FAIL / 其余→FAIL）；
   **分类层不抢先判失败**，预期失败/缺件/记录性形态全部交 check 返回；前置产物（TC-C04 需
   `work/package.json` 中文+utf8 脚本、TC-C07 需独立 init 目录）在 `runAll()` 前置段生成
   （`369-378`）；`soShaBefore`（`366`）供 TC-C10 前后比对。
6. **报告（FR-101-PROBE-005）**：逐条 JSON（`411-417`：id/title/verdict/detail/exit/ms/timedOut/
   stdout/stderr 截断 800）+ 汇总头（`421-430`：runAt/device/nativeDir/soSha256）写
   `filesDir/probe-report.json`（`432-436`），`adb pull` 取回。
7. **本批新增（FR-101-PROBE-009，2026-11-05）**：autorun 无头触发——`onCreate` 读
   `getBooleanExtra("autorun", false)`（`88-89`），`--ez autorun true` 时 `postDelayed 0` 立即跑批、
   缺省保留 1s 自动跑（`90-92`，对齐 aosp-git-cli 统一测试队列写法）；`ran` 守卫防复跑（`360`）。

## 5. 构建与部署命令（DEV 侧）

```bash
# 项目自带 Gradle 9.4.1 发行版（工作区兄弟仓 aosp-node-cli/.dist/gradle-dist/ 亦刻一份）
distGradle="/d/node-bridge-workspace/aosp-node-cli/.dist/gradle-dist/gradle-9.4.1/bin/gradle"
cd probe-app && "$distGradle" :app:assembleDebug --console=plain
# 产物：probe-app/app/build/outputs/apk/debug/app-debug.apk（BUILD SUCCESSFUL）
# ——设备运行由编排者统一排队（本批不做）——
# adb install -r app/build/outputs/apk/debug/app-debug.apk
# adb shell am start -n org.fellow99.pnpmprobe/.MainActivity            # 缺省 1s 后自动跑
# adb shell am start -n org.fellow99.pnpmprobe/.MainActivity --ez autorun true   # FR-101-PROBE-009
# adb pull /data/data/org.fellow99.pnpmprobe/files/probe-report.json .
```

## 6. 用例设计（与 003-cli 同构纪律）

- 编号沿用上游正典 **TC-C00…C16**（003-cli 主用例 id；本表以代码 `probes()` 实际 id 为准，
  as-built）。说明：aosp-git-cli 载体把同一 003 正典另编号为 TC-003-xx，本仓沿用 pnpm 既有的
  C 系列 id，交叉引用 [`003-cli/test-cases.md`](../003-cli/test-cases.md) 全表。
- 判据「预期失败才是通过」：红线用例（TC-C06b/C14 构建拦截、TC-C10 self-update）失败才通过；
  缺件用例（C06a node / C15 16KB 设备）BLOCKED 不删不伪造（C7）。
- BLOCKED/RECORD 不删：TC-C11/C16 只记录（`RECORD:`），不断言形态（C6）。

## 7. 不一致与风险登记

| # | 项 | 处置 |
|---|---|---|
| 1 | App 进程域 P2（lifecycle 完整回程）受双重限制：W^X 禁 filesDir sh 脚本 + 翻译层 exec 包装器禁 symlink | TC-C06a 标 BLOCKED（node 缺席 aosp-node-cli 未首产）；P2 完整回程已由 adb 层 shim 实证（003-cli test-cases 事实区），App 域 ⏳ 真机复测 |
| 2 | 翻译模拟器 ≠ 原生 arm64（2026-11-03/04 环境限定） | 设备跑批经翻译层时 TC-C16 如实记录 abilist，正式 G3 需原生 arm64 复测 |
| 3 | self-update 可写 HOME+网络下真实下载（C5 行为实锤）而非物理失败 | 判据＝sha256 前后一致（P5 实测口径），不依赖 exit 断言；消费方文档声明禁用需环境约束 |
| 4 | `LD_LIBRARY_PATH` 对 exec 子进程由默认 linker namespace 决定 | 已注入 `nativeLibraryDir`（规划 §7）；libc++ 若 DT_NEEDED 由 node-runtime 供给同在 NLDIR |
| 5 | 本批新增 autorun 未设备实测（编译已验） | 语义对齐 aosp-git-cli 已验证写法；设备行为随编排者跑批回填 |

## 8. FR 交叉引用

| FR | 落点 |
|---|---|
| FR-101-PROBE-001 | `MainActivity.java:56-57,237-248` + `app/build.gradle.kts:20-23` ⇒ TC-C00 |
| FR-101-PROBE-002 | `MainActivity.java:104-111,130-134` ⇒ TC-C00b/C08a/C09 |
| FR-101-PROBE-003 | `MainActivity.java:121-153`（exec 全量）+ C01…C09 探针 |
| FR-101-PROBE-004 | `MainActivity.java:381-405`（runner verdict 分类门） |
| FR-101-PROBE-005 | `MainActivity.java:411-436`（逐条+汇总 JSON） |
| FR-101-PROBE-006 | `MainActivity.java:292-308`（C08a→C09→C08b 顺序） |
| FR-101-PROBE-007 | `MainActivity.java:335-342,366`（TC-C10 sha256 前后比对） |
| FR-101-PROBE-008 | `MainActivity.java:319-352`（C06a/C06b/C14/C11/C15/C16 形态） |
| FR-101-PROBE-009 | `MainActivity.java:83-92`（autorun + 1s 自动 + ran 守卫） |

## 9. 文件清单（本模块，as-built 快照 2026-11-05）

- 新增（本批）：`spec.md`（160 行）/ `plan.md` / `test-cases.md` / `tasks.md` +
  `logs/20261105-1/DEV_CHECKLIST.md`（构建验证记录）。
- 修改：`probe-app/…/MainActivity.java`（442 行，+autorun 无头触发 FR-101-PROBE-009）。
- 既有（as-built 源，未改）：`app/build.gradle.kts`（40）/ `AndroidManifest.xml`（20）/
  `probe-app/build.gradle.kts`（3）/ `settings.gradle.kts`（22）/ `gradle.properties`（4）/
  `local.properties`（1）/ `.gitignore`（5）。
- 未实现清单：无（本模块已是实现态）；遗留仅为「设备跑批 19 探针」（编排者排队）与「原生 arm64
  复测 / P2 App 域真机复测」执行项，非文档缺口。
