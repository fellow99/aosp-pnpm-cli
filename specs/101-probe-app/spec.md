# 101-probe-app · 设备回程探针载体（probe-app）功能规格

> 模块：101-probe-app · 状态：⚠️ 已实现（as-built 归档 2026-11-05；APK 构建已验证，设备运行由编排者统一排队）·
> 所属工程：aosp-pnpm-cli · 规划基线：[`docs/000-工程规划.md`](../../docs/000-工程规划.md) §3（消费方环境引导层）/ §7（运行期契约表）/ §7.1（回程探针 P1–P7）/ §10.2（M3.5-T7）
> 对应里程碑任务：M3.5-T7（真机回程探针，G3）；上游用例正典：[`003-cli`](../003-cli/test-cases.md)（TC-C00…C16）
> 逆向事实源（as-built）：`probe-app/` 全部代码 + `specs/003-cli/test-cases.md` + `pnpm-cli.lock.json` +
> `scripts/build-pnpm-cli-android.sh`。

## 1. 目的

定义「设备侧回程探针载体」的行为契约：一个独立 Android app（`probe-app/`）把 `003-cli` 模块的
**主测试用例正典 TC-C00…C16 的 App 内可执行子集**落到真实设备上，对被测 `libpnpm_cli.so`
（pnpm 12.16.1 本体）逐条给出 PASS / FAIL / BLOCKED 判定并产出机器可读的 JSON 报告
（`filesDir/probe-report.json`），作为 M3.5-T7 真机验收（G3）的可重复执行载体。本模块**不新增产品能力**：
它是「规划 §3 消费方环境引导层 + §7 运行期契约 + §7.1 P1–P7 探针判据」在 App 进程域的验证实现。

> 范围边界说明（2026-11-05 本批）：pnpm **不需要**实测问题修复（[`docs/000-工程规划.md`](../../docs/000-工程规划.md)
> §7 已明确 pnpm registry 仅是环境配置注入、属修复设计范围外），也**不需要补充探针**；本模块仅
> 逆向归档既有 19 条探针载体实现（[`test-cases.md`](./test-cases.md) 全表）。

## 2. 范围

**在范围内（101-probe-app）：** 探针载体的原生件就绪检查（`useLegacyPackaging` 物理落盘
`nativeLibraryDir` + NLDIR 唯一原生件）、环境注入（规划 §7 全变量子集）、命令执行
（`ProcessBuilder` 直接 exec `libpnpm_cli.so`，非 PTY 子集）、判据与报告（PASS/FAIL/BLOCKED
+ JSON）、store 双案顺序编排（案 a 先于 config set 案 b）、self-update 红线前后比对（sha256）、
无头自动化（启动即跑 + `--ez autorun true` intent）。

**不在范围内（101-probe-app）：** pnpm 本体行为（→ 003-cli 用例正典，本模块只承载执行）；
PTY/真终端交互语义（→ 终端 App / 消费方联调，规划 §7.1 P1 完整形态）；pnpm registry 问题修复
（环境配置注入，修复设计范围外）；探针 App 的发布形态（不发布，`PROBE_APP_VERSION=12.16.1` =
被测 pnpm 版本，`probe-app/gradle.properties:4`）。

## 3. 功能需求

> 术语：探针（Probe）= `MainActivity.probes()` 中一条带 `id`/`argv`/`判据 check` 的记录；
> 判据 check 返回 `null` = PASS；`"BLOCKED:…"` 前缀 = BLOCKED（不计失败）；`"PASS-"`/`"RECORD:"`
> 前缀 = 事实形态 PASS（只记录不断言）；`"FAIL-"` 前缀 = FAIL；其余 = FAIL。
> （as-built：`MainActivity.java:213-215` 接口、`402-405` runner 分类）

### 3.1 载体与注入域

- **FR-101-PROBE-001（MUST）**：载体就绪——exec 型原生件以 `useLegacyPackaging=true` 物理落盘
  `nativeLibraryDir`；`libpnpm_cli.so` 为 NLDIR **唯一** `.so` 原生件（兄弟文件唯一，镜像
  `provision BANNED_IN_AAR` 口径）；被测件尺寸 == lock 落锁值（47317088，C2 不写死副本之外的
  基准）。本载体**无 assets 物化步骤**（对比 node/git/python 载体的 D8 纯二进制差异，pnpm 本体即命令入口）。
  判据：TC-C00。（as-built：`MainActivity.java:56-57` 目录初始化 / `237-248` 判定 /
  `probe-app/app/build.gradle.kts:20-23` `useLegacyPackaging`）
- **FR-101-PROBE-002（MUST）**：环境注入契约——执行前注入规划 §7 表子集：`HOME`/`TMPDIR`/
  `XDG_DATA_HOME`（store 案 a 兜底）/`PATH`（`nativeLibraryDir:$HOME/bin:/system/bin`）；`exec()`
  层追加 `ANDROID_ROOT=/system`/`ANDROID_DATA=/data`/`LD_LIBRARY_PATH=nativeDir`（exec 子进程默认
  linker namespace，规划 §7）。进程环境整表替换（`env.clear()+putAll`）保证注入面干净、无宿主残留。
  判据：TC-C00b（裸环境直启）/TC-C08a/TC-C09。（as-built：`MainActivity.java:104-111` `baseEnv()`
  / `130-134` exec 追加）
- **FR-101-PROBE-003（MUST）**：执行器语义——`ProcessBuilder` **直接 exec**
  `nativeLibraryDir/libpnpm_cli.so`（非 PTY 子集；无 `/system/bin/sh` 包装——D8 约定 pnpm 本体即
  条目，与 node 载体三壳差异）；stdout/stderr 分流（`redirectErrorStream(false)`）、退出码采集、
  独立读泵、超时 `destroyForcibly` 兜底、启动异常捕获进 `failReason`。判据：TC-C01/C01b/C02/C03/C04/
  C05/C07/C08a/C08b/C09 全部经此执行器。（as-built：`MainActivity.java:121-153` `exec()`）

### 3.2 判据与报告域

- **FR-101-PROBE-004（MUST）**：判据与大类纪律——runner 只做 verdict 分类，**不得在分类层抢先判
  FAIL**；「预期失败才是通过」的红线类（C06b/C14 拦截生效）与缺件类（C06a/C15 BLOCKED）交由 check
  返回形态区分；记录性类（C11/C16）以 `RECORD:` 形态只记录不断言。判据：全部 19 条探针统一过
  `runAll()` 的 verdict 门。（as-built：`MainActivity.java:381-405`）
- **FR-101-PROBE-005（MUST）**：报告——`filesDir/probe-report.json`：头部 `runAt`/`device`
  （MODEL/RELEASE/SDK/ABI）/`nativeDir`/`soSha256` + `probes[]` 逐条
  `id/title/verdict/detail/exit/ms/timedOut/stdout(截断800)/stderr(截断800)`。判据：跑批结束报告可
  `adb pull` 取回且 JSON 可解析。（as-built：`MainActivity.java:411-417` 逐条 JSON / `421-436` 汇总写盘）
- **FR-101-PROBE-006（MUST）**：store 双案顺序编排——案 a（`XDG_DATA_HOME` 兜底，TC-C08a）**先于**
  `config set store-dir`（案 b，TC-C09），案 b **后于** set（TC-C08b）回读——保证双案落点独立可证，
  不被相互污染（R5 定案：推荐案 b 显式持久化，案 a 作对照探针）。判据：TC-C08a→C09→C08b 依序执行且
  落点分别带 xdgDir/storeDir 前缀。（as-built：`MainActivity.java:292-308` probes() 顺序）
- **FR-101-PROBE-007（MUST）**：self-update 红线判据（P5 实测口径）——判据 = **本体 sha256 前后一致**
  （`soShaBefore` vs 执行后 `sha256(so)`），**不是 exit 断言**（可写 HOME+网络下工具真实发起运行期下载
  上游二进制、退出码可能为 0，但原产物不可被覆盖）；`soShaBefore` 在 `runAll()` 前置统一采集。
  判据：TC-C10。（as-built：`MainActivity.java:335-342` 判定 / `366` 前置采集）
- **FR-101-PROBE-008（MUST）**：缺件/条件用例诚实实标——node 缺席（TC-C06a，aosp-node-cli 未首产）
  与 16KB 页设备缺席（TC-C15）标 **BLOCKED 不删不伪造**（C7 未实测 ⬜ / C8 如实登记）；记录性用例
  （TC-C11 dlx / TC-C16 翻译层 abilist）以 `RECORD:` 形态事实记录、不断言形态（C6 判据纪律）。
  判据：TC-C06a/C06b/C14/C11/C15/C16。（as-built：`MainActivity.java:319-352` 判据形态贯穿）

### 3.3 无头自动化域

- **FR-101-PROBE-009（MUST）**：无头自动化——`onCreate` 后启动即自动执行全部探针（`postDelayed`
  1s，`adb shell am start` 即可无人值守跑完，pull 报告即取回）；本批新增 **`--ez autorun true`
  intent 无头触发**（`am start … --ez autorun true` 立即跑批，缺省保留 1s 自动跑——对齐统一测试
  队列，aosp-git-cli 同构）；`ran` 守卫防复跑，点按钮也可触发。判据：无头启动必出报告。
  （as-built：`MainActivity.java:83-92` `onCreate` 触发块）

## 4. 用户故事

- 作为 **测试/QA**，我需要在真实设备上按 003-cli 正典一键跑批（`am start --ez autorun true`），
  得到可证伪的 PASS/FAIL/BLOCKED 与机器可读 JSON，从而在 M3.5-T7 窗口给出 G3 判据。
- 作为 **构建/流水线实现者**，我需要一个可重复载体在首产后验证 exec + env 注入 + 判据三链无回归，
  避免把「host 能编」当成「设备能跑」（`probe-app` 依赖容器 AAR 而非 CI 产物直接投放，隔离干净）。
- 作为 **消费方开发者**，我需要一个可复现的载体观察 pnpm 本体在 App 进程域的真实行为边界
  （含 P5 self-update 运行期下载事实、L1 构建脚本默认拦截事实），以决定环境引导层注入口径。

## 5. 验收场景（Given/When/Then）

1. **载体就绪**：Given 容器 AAR 已就位（`app/libs/pnpm-cli-release.aar`，lock 钉版 12.16.1）；
   When 执行 TC-C00；Then NLDIR 存在 `libpnpm_cli.so` 且 size==47317088、NLDIR 内 `.so` 数==1。
2. **版本回程（P1）**：Given env 注入齐备；When TC-C01 与 TC-C01b；Then 两命令 stdout 均输出
   `12.16.1`（== lock `active.version`）且 exit=0。
3. **store 双案（P3，R5 再证）**：Given 注入 HOME/XDG；When 依序 TC-C08a→C09→C08b；Then
   案 a 落点以 xdgDir 开头、案 b 落点以 storeDir 开头且随 `config.yaml` 持久化回读。
4. **P5 红线（self-update）**：Given 跑批前置 sha256 基准；When TC-C10 执行 `self-update`；
   Then 本体 sha256 前后一致（soShaBefore==after，原产物不可被覆盖），无论 exit 与是否发起下载。
5. **缺件如实（C7）**：Given node 未注入 / 无 16KB 设备；When TC-C06a / TC-C15；
   Then 判据返回 BLOCKED 并附缺件原因，不伪造 PASS、不删用例。
6. **无头报告**：Given `am start … --ez autorun true` 启动；When 自动跑批结束；Then
   `filesDir/probe-report.json` 存在且可 `adb pull` 取回（runAt/device/soSha256/probes[]）。

## 6. 实体表

| 实体 | 字段 | 说明 | as-built |
|---|---|---|---|
| `Probe` | `id`/`title`/`argv`/`envOverride`/`timeoutMs`/`check`/`cwd` | 一条探针的完整定义；`argv=null` 表示纯文件面检查（不 exec）；`envOverride` value 空/`null`=删除基线变量 | `MainActivity.java:217-230` |
| `Out` | `stdout`/`stderr`/`exit`/`ms`/`timedOut`/`failReason` | 单次执行的采集结果 | `MainActivity.java:113-119` |
| `Check` | `String check(Out o)` | 判据函数：null=PASS；`BLOCKED:`/`PASS-`/`RECORD:`/`FAIL-` 前缀按 §3 术语分类 | `MainActivity.java:213-215` |
| verdict | `PASS`/`FAIL`/`BLOCKED` | `runAll()` 统一分类；BLOCKED/RECORD 不按 FAIL 计数 | `MainActivity.java:402-418` |
| 报告 JSON | `runAt`/`device`/`nativeDir`/`soSha256`/`probes[]` | `filesDir/probe-report.json` | `MainActivity.java:421-436` |
| 被测件 | `libpnpm_cli.so`（pnpm 12.16.1，arm64-v8a，exec 型 PIE） | 容器 AAR 随 APK 落 nativeLibraryDir；size/sha256 事实源 = `pnpm-cli.lock.json`（C2） | `app/build.gradle.kts:14-23,37-39` · `pnpm-cli.lock.json` |

## 7. NFR

- **性能**：跑批时长受网络用例支配（TC-C05 add 300s / TC-C06b 300s / TC-C14 300s / TC-C10
  self-update 180s / TC-C11 dlx 180s）；每条探针带独立 timeout（`Probe.timeoutMs`），
  超时 `destroyForcibly` 兜底、不阻塞后续探针。
- **可靠性**：单探针异常/超时不中断整体（`exec()` try/catch + `timedOut` 采集 + read 泵吞异常）；
  `ran` 守卫保证幂等复跑；报告在 `finally` 语义内逐条写盘。
- **可维护性**：探针为声明式数据（`new Probe(...)` 数组），新增用例只加一条记录 + 判据 lambda；
  期望值引用 lock 落锁值（size/version），不内嵌可枚举副本。
- **环境要求**：arm64-v8a 唯一 ABI（`abiFilters`）；x86_64 模拟器经翻译层运行属**辅助记录**
  （TC-C16 如实记录 abilist），不替代 arm64 直执行判据；TLS 网络用例（TC-C05/C06b/C14/C11）需外网。
- **安全/卫生**：探针 App 不发布；唯一权限 `INTERNET`（`AndroidManifest.xml:3`）；报告为纯文本 JSON、
  无敏感数据；敏感面（下载远端起跳）只作记录不断言。

## 8. 依赖

| 依赖 | 锚点 | 说明 |
|---|---|---|
| `003-cli` 用例正典 | [`../003-cli/test-cases.md`](../003-cli/test-cases.md) | TC-C00…C16 判据源（本模块 = App 内可执行子集） |
| `pnpm-cli.lock.json` | [`../../pnpm-cli.lock.json`](../../pnpm-cli.lock.json) | 期望值唯一事实源（C2）：`active.version=12.16.1`、`releases[0].size=47317088`、`sha256=e5378cfa…` |
| 容器 AAR | `probe-app/app/libs/pnpm-cli-release.aar`（不入库） | 探针载体依赖的 exec 型制品 = CI 首产归档解包 |
| 运行期契约 | [`../../docs/000-工程规划.md`](../../docs/000-工程规划.md) §7 / §7.1 | 注入变量表 + P1–P7 判据 |
| 构建脚本（产物事实） | [`../../scripts/build-pnpm-cli-android.sh`](../../scripts/build-pnpm-cli-android.sh) | 产物命名/尺寸/门禁口径（strip 后 47.3MB 回填 lock） |

## 9. 状态与遗留

- 2026-11-05 本批：as-built 文档归档（spec/plan/test-cases/tasks 四件套）+ 新增 `--ez autorun true`
  无头触发（FR-101-PROBE-009）+ `gradle :app:assembleDebug` APK 构建验证（BUILD SUCCESSFUL，
  APK `probe-app/app/build/outputs/apk/debug/app-debug.apk`）——见
  [`logs/20261105-1/DEV_CHECKLIST.md`](../../logs/20261105-1/DEV_CHECKLIST.md)。
- **本批不做设备运行**：真机/模拟器跑批 19 条探针由编排者后续统一排队执行；跑批结论届时回填
  [`test-cases.md`](./test-cases.md) 事实区与 `003-cli/test-cases.md` App 探针执行记录节。
- 遗留执行项（非文档缺口）：P2 完整回程 App 域（node 注入受 W^X 禁 filesDir 脚本 + 翻译层禁 symlink
  双重限制，⏳ 真机复测）；locale/R5/R6 正式结论 ⏳ 真机复测后转正式。
