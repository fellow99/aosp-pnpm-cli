# 101-probe-app 任务拆解

> 对应：[spec.md](./spec.md)（FR-101-PROBE-001…009）· [plan.md](./plan.md)（as-built）。
> 本模块**已是实现态**：tasks 记录的是「已完成的建仓/跑批动作 + 本批补充项 + 遗留执行项」，不是规划。

## T1 载体建仓（已闭环）

- [x] T1.1 独立 Gradle 工程 `probe-app/`（根 `build.gradle.kts` + `settings.gradle.kts` + `app/`），
  不在根 settings 内（000 单模块红线；`probe-app/settings.gradle.kts:17-19` 注释声明）
- [x] T1.2 `app/build.gradle.kts`：仅 arm64 ABI（`abiFilters`）+ `useLegacyPackaging=true`（D1）+
  容器 AAR 依赖——`app/build.gradle.kts:17,20-23,37-39`
- [x] T1.3 `MainActivity.java`：载体初始化 / env 注入（baseEnv + exec 追加）/ 直接 exec 执行器 /
  19 条探针表 / JSON 报告（全量，见 plan §4）
- [x] T1.4 manifest：仅 INTERNET + `extractNativeLibs=true` + 单一 LAUNCHER activity
  （`AndroidManifest.xml`）

## T2 探针正典映射与既有跑批（已闭环）

- [x] T2.1 实现 003-cli 正典 App 内可执行子集 19 条（C00/C00b/C01/C01b/C02/C03/C04/C05/C06a/C06b/
  C07/C08a/C08b/C09/C10/C11/C14/C15/C16，`MainActivity.probes()`）
- [x] T2.2 既有先例（2026-11-04，003-cli 阶段）：App 探针 19 条 = 17 PASS + 2 BLOCKED
  （C06a node 缺件 / C15 无 16KB 设备），x86_64+arm64 翻译层——见
  [`../003-cli/test-cases.md`](../003-cli/test-cases.md) App 探针执行记录节
- [x] T2.3 事实回填：P5 修正（self-update 会运行期下载不物理失败）、L1 增强（构建脚本默认拦截）、
  R5 定案（store 推荐案 b）——均已回写 003-cli test-cases 事实区与规划 §7/§7.1

## T3 本批：as-built 归档 + autorun + APK 构建验证（2026-11-05）

- [x] T3.1 `MainActivity.java` 新增 `--ez autorun true` 无头触发（FR-101-PROBE-009）：`onCreate`
  读 intent extra，`autorun=true` 立即跑批、缺省保留 1s 自动跑（对齐 aosp-git-cli 统一测试队列）——
  `MainActivity.java:83-92`
- [x] T3.2 as-built 文档归档（spec.md/plan.md/test-cases.md/tasks.md 四件套，逆向既有实现）
- [x] T3.3 host 编译验证：`gradle :app:assembleDebug --console=plain`（项目自带 Gradle 9.4.1 发行版）
  BUILD SUCCESSFUL，APK `app/build/outputs/apk/debug/app-debug.apk`——
  `logs/20261105-1/DEV_CHECKLIST.md`
- [x] T3.4 索引更新：`SPECS_CHECKLIST.md` 新增 101-probe-app 模块行 + `specs/README.md` 模块索引

## T4 遗留执行项（非文档缺口）

- [ ] T4.1 设备跑批 19 探针（由编排者统一排队）→ 结论回填本模块 test-cases 事实区 +
  `003-cli/test-cases.md` App 探针执行记录节
- [ ] T4.2 autorun 设备行为确认（编译已验；随 T4.1 跑批验证）
- [ ] T4.3 原生 arm64 环境复测（正式 G3；翻译模拟器结果仅记录，`003-cli/test-cases.md` C7 口径）
- [ ] T4.4 App 进程域 P2（lifecycle 完整回程）真机复测（node 注入受 W^X + 翻译层 symlink 双重限制；
  adb 层已实证 shim 路径，App 域 ⏳）
