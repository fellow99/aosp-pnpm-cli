# 101-probe-app 测试用例（probe-app 实际实现的探针正典，as-built）

> 对应：[spec.md](./spec.md)（FR-101-PROBE-001…009）· 上游判据源：[`../003-cli/test-cases.md`](../003-cli/test-cases.md)（TC-C00…C16）。
> **本表 = `probe-app/app/src/main/java/org/fellow99/pnpmprobe/MainActivity.java` `probes()` 实际实现清单**
> （as-built，行号 = 2026-11-05 快照，442 行）。
> **编号说明**：本载体沿用 003-cli 正典 id（TC-C00…C16）；aosp-git-cli 把同一 003 正典另编号为
> TC-003-xx，本仓以代码实际 id 为准（C 系列），逐条交叉引用 003-cli 全表。
> **环境口径**：`device` = 需容器 AAR + env 注入（规划 §7）+ 网络（TLS 用例）；`host` = 构建机。
> **设备判据纪律**：arm64 真机/arm64 模拟器为准；x86_64 模拟器经翻译层只记录（TC-C16），不替代判据；
> 期望值引用 lock（pnpm 12.16.1 / size 47317088 / sha256 `e5378cfa…`，C2 不写死副本）。

## 一、探针全表（device，MainActivity.probes() 逐条，19 条）

| # | 用例 | 环境 | 前置 | 命令（argv，探针行） | 期望（可证伪 / 判据） | FR | 状态 |
|---|---|---|---|---|---|---|---|
| TC-C00 | 载体就绪（NLDIR 物理落盘 + 唯一原生件） | device | AAR 就位 | （无 exec，纯文件面）`so.isFile()` + `size==47317088` + NLDIR `.so` 数==1（`237-248`） | 本体存在、size==lock 落锁值、NLDIR 无兄弟 `.so`（镜像 BANNED_IN_AAR）；任一不满足 ⇒ FAIL | FR-101-PROBE-001 | ⬜ 待设备跑批 |
| TC-C00b | exec 权限语义（裸环境直启） | device | — | `--version`，`envOverride` 删 HOME/TMPDIR/XDG（`249-254`） | exit==0 **且** stdout 含 `12.16.1`（shell 域/App 域允许 exec，exec 型实证）；ENOENT/EPERM ⇒ FAIL | FR-101-PROBE-002 | ⬜ |
| TC-C01 | 版本回程（P1） | device | env 注入 | `--version`（`257-260`） | exit==0 **且** `stdout.trim()==12.16.1`（== lock `active.version`） | FR-101-PROBE-003 | ⬜ |
| TC-C01b | -v 短旗标等价 | device | 同上 | `-v`（`261-264`） | 同 TC-C01（stdout.trim()==12.16.1） | FR-101-PROBE-003 | ⬜ |
| TC-C02 | --help 用法输出 | device | 同上 | `--help`（`265-268`） | exit==0 **且** stdout 含 `Usage`（非空用法文本） | FR-101-PROBE-003 | ⬜ |
| TC-C03 | 未知子命令：诊断走 stderr | device | 同上 | `definitely-not-a-command`（`269-272`） | exit≠0 **且** stderr 非空（exit/close 齐全、流分离） | FR-101-PROBE-003 | ⬜ |
| TC-C04 | UTF-8 回程 | device | `work/package.json` 前置（中文 description + utf8 脚本） | `run utf8`（`273-276`） | exit==0 **且** stdout 含 `你好世界🦆`（中文/emoji 不乱码） | FR-101-PROBE-003 | ⬜ |
| TC-C07 | init 生成合法 package.json | device | 独立 `init-probe` 目录（防覆盖 work） | `init`，cwd=initDir（`277-289`） | exit==0 **且** initDir/package.json 存在 **且** 含 `"name"` 字段 | FR-101-PROBE-003 | ⬜ |
| TC-C08a | store 落点案 a（XDG_DATA_HOME 兜底） | device | env 注入 XDG | `store path`（`292-295`） | exit==0 **且** stdout 以 xdgDir 前缀开头（落注入可写目录，不落库区） | FR-101-PROBE-002/006 | ⬜ |
| TC-C09 | HOME 基座：config set store-dir（案 b） | device | 同上 | `config set store-dir <storeDir>`（`296-304`） | exit==0 **且** `homeDir/.config/pnpm/config.yaml` 落 HOME 基座 | FR-101-PROBE-002/006 | ⬜ |
| TC-C08b | store 落点案 b（持久化回读） | device | C09 之后 | `store path`（`305-308`） | exit==0 **且** stdout 以 storeDir 前缀开头（config.yaml 持久化生效；R5 推荐案 b 再证） | FR-101-PROBE-006 | ⬜ |
| TC-C05 | TLS install 纯 JS 包（P7） | device | 网络 + P10 | `add left-pad`（`311-318`） | exit==0 **且** workDir/node_modules/left-pad 为目录（TLS/store 链路完整） | FR-101-PROBE-003 | ⬜ |
| TC-C06a | lifecycle 完整回程（P2①） | device | — | （无 exec）缺 node（`319-321`） | **BLOCKED**:node 缺席（aosp-node-cli 未首产，`libnode_cli.so` 占位残品）；完整回程待 node 产物 | FR-101-PROBE-008 | ⬜ |
| TC-C06b | lifecycle 无预授权拦截（P2②/L1 增强） | device | 网络 | `add core-js`（`322-326`） | **预期拦截**：exit≠0 且 stdout/stderr 含 `IGNORED_BUILDS` ＝ PASS（配置层阻断非静默）；意外 exit==0 无拦截 ⇒ FAIL | FR-101-PROBE-008 | ⬜ |
| TC-C14 | 平台二进制包行为（L3） | device | 网络 | `add esbuild`（`327-332`） | 输出含 `IGNORED_BUILDS`（postinstall 被默认拦截）＝ PASS；**exit==0 静默装成功 ⇒ FAIL（C5 红线！）** | FR-101-PROBE-008 | ⬜ |
| TC-C10 | self-update 红线（P5） | device | 前置 `soShaBefore`（`366`） | `self-update`（`335-342`） | **本体 sha256 前后一致**（after==soShaBefore）＝ PASS（非 exit 断言；工具可能发起运行期下载但原产物不可被覆盖） | FR-101-PROBE-007 | ⬜ |
| TC-C11 | dlx 行为记录（P4） | device | 网络 | `dlx cowsay hi`（`343-346`） | **RECORD**:timedOut/exit/out/err 如实记录（R10；需 PATH 上 node，只记录不断言） | FR-101-PROBE-008 | ⬜ |
| TC-C15 | 16KB 页设备 exec（P6） | device | — | （无 exec）页大小判定（`347-349`） | **BLOCKED**:无 16KB 页设备/镜像（产物 min LOAD align=0x4000 已由门禁⑦保证）；有则复跑 P1 判据 | FR-101-PROBE-008 | ⬜ |
| TC-C16 | 翻译层事实记录 | device | — | `/system/bin/getprop ro.product.cpu.abilist`（`350-352`） | **RECORD**:abilist=…（x86_64 跑 arm64 翻译行为只记录，不替代 arm64 判据） | FR-101-PROBE-008 | ⬜ |

> **19 条构成**：载体 2（C00/C00b）+ 基础无网络 6（C01/C01b/C02/C03/C04/C07）+ store 契约 3
> （C08a/C09/C08b）+ 网络安装 4（C05/C06a/C06b/C14）+ 红线与边界 4（C10/C11/C15/C16）。
> 既有实现即为 003-cli 正典的 App 内可执行子集；本批**不补充探针**（pnpm registry 注入为环境配置、
> 修复设计范围外）。

## 二、host 侧用例（构建机——载体 App 本身）

| # | 用例 | 环境 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|---|
| TC-101-PROBE-10 | probe-app 编译产出 | host | `gradle :app:assembleDebug --console=plain`（项目自带 Gradle 9.4.1） | `app/build/outputs/apk/debug/app-debug.apk` 产出；BUILD SUCCESSFUL；exit 0 | ✅ 2026-11-05（logs/20261105-1/DEV_CHECKLIST.md） |
| TC-101-PROBE-11 | 探针实现与正典映射核对 | host | Grep `probes()` 各 `new Probe(id,…)` | id 全集（19 条）== 本表一（TC-C00…C16） | ✅ 2026-11-05 |
| TC-101-PROBE-12 | AAR 未替换检查 | host | `ls -l app/libs/pnpm-cli-release.aar` | 容器 AAR 仍在位（首产归档解包件，不入库）；未被本批替换 | ✅ 2026-11-05 |
| TC-101-PROBE-13 | autorun 无头触发支持 | host | Grep `getBooleanExtra("autorun", …)` | `--ez autorun true` 触发路径存在（FR-101-PROBE-009）；缺省 1s 自动跑保留 | ✅ 2026-11-05 |

## 三、事实记录区（回填处，C6）

| 项 | 值 | 记录时间 |
|---|---|---|
| 本批构建 | `:app:assembleDebug` BUILD SUCCESSFUL；APK `app/build/outputs/apk/debug/app-debug.apk` | 2026-11-05（DEV_CHECKLIST） |
| 既有先例（003-cli App 探针 19 条） | 17 PASS + 2 BLOCKED（C06a node 缺件 / C15 无 16KB 设备），x86_64+arm64 翻译层，报告 `probe-report.json` | 2026-11-04（003-cli/test-cases.md App 执行记录节） |
| 受测件事实 | pnpm 12.16.1 / size 47317088 / sha256 `e5378cfa…`（lock released） | lock 首产回写 |
| 本批 autorun 设备行为 | ⏳ 未跑（编译已验）；语义对齐 aosp-git-cli 已验证路径 | 2026-11-05 |

## 四、禁止的判据

1. 不得把 TC-C06a（node 缺席）/TC-C15（无 16KB 设备）当回归缺陷上报——应为 BLOCKED（C7），
   不得硬凑 PASS、不得删用例。
2. 不得在 x86_64 模拟器上执行 arm64 产物探针并记录为原生 arm64 通过（TC-C16 只记录 abilist 事实，
   正式 G3 需原生 arm64 复测）。
3. TC-C11/C16（记录性）只记录不断言——事后不得改写成断言（C6）。
4. TC-C10（self-update）判据是 sha256 前后一致，**不得用 exit 断言**（P5 实测口径：可写 HOME+网络下
   exit 可能为 0 但仍发起下载；判据锚定「原产物不可被覆盖」）。
5. `app/libs/pnpm-cli-release.aar` 不得被本批替换或回退（首产 released 制品；如需换版走 lock/CI 流程）。
6. 本批不补探针、不做 pnpm registry 修复——凡是把「registry 环境配置修复」写成探针判据的都不属于
   本模块范围（规划 §7 明确 pnpm registry 为环境配置注入、修复设计范围外）。

## 附、统一队列跑批记录（2026-11-06）

- 载体：`emulator-5554`（sdk_gphone64_x86_64 API 37 × ndk_translation；App 域 ABI=arm64）；由编排者统一排队串行执行（安装→`--ez autorun true`→`run-as cat` 回读报告）。
- 报告：`logs/20261105-1/report-pnpm.json`（工程内留档，不入库）。
- 汇总：19 探针：PASS 14 / FAIL 3 / BLOCKED 2（TC-C07/C08a FAIL 疑探针卫生——install -r 保留先前 package.json/store-dir；TC-C14 网络；TC-C06a node 缺席 BLOCKED；TC-C15 无 16KB 页 BLOCKED）。
- 判读纪律：翻译模拟器结果**仅记录**，正式 G3 需原生 arm64 复测；FAIL/BLOCKED 均按本节/Spec 既有分类如实记录，未伪造 PASS。
