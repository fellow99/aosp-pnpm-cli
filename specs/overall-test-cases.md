# overall-test-cases.md —— `aosp-pnpm-cli` 全模块测试用例索引

> **性质**：**索引文档**（不是逐条用例全集）。逐条用例的完整步骤与执行记录在 [`000-scaffold/test-cases.md`](./000-scaffold/test-cases.md)；本文负责①分层口径 ②用例族索引 ③规划期真机探针索引（规划引用）④判据纪律。
> **依据**：[`constitution.md`](./constitution.md)（C7 未实测标 ⬜）· [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（唯一权威，下称「规划 §」）。
> **标记**：✅ 已执行通过 / ⬜ 规划未实现（真机/首产依赖）/ ⚠️ 风险项。
> **版本**：v1.0 · 2026-11-02。**判据纪律：每条判据必须可证伪；无「通过/不通过」二元出口的条目不得进表。**

---

## 1. 测试分层

| 层 | 代号 | 是否需要真机/产物 | 跑在哪 | 目的 | 现状 |
|---|---|---|---|---|---|
| **host 静态/门禁** | **L1** | ❌（门禁正样本需要产物） | 开发机 / CI（node + bash） | 语法门、lock/退出码契约、七项 exec 门禁、禁区（兄弟文件 / libnode.so） | ✅ 工具在位；未投放态已验；正样本 ⬜ M3.5-T5 |
| **构建配置** | **L2** | ❌ | 开发机 / CI（JDK 21 + Gradle 9.4.1） | 无 .so 可配置、AAR 容器可产出、坐标正确 | ✅ TC-01/02（2026-11-02 记录） |
| **CI 配置期** | **L3** | ❌ | GitHub Actions（ubuntu-24.04） | 三段门禁（配置门 / 退出码纪律 / 语法门） | ⚠️ 步骤在位；退出码步骤 `bash -e` 缺陷（[`overall-plan.md`](./overall-plan.md) §3.2）；运行记录 ⬜ |
| **真机回程** | **L4** | ✅ | 真机（消费方终端，PTY 内） | 规划 P1–P7 探针（§3） | ⬜ M3.5-T7 |

**顺序纪律**：L1/L2/L3 不需要产物即可跑，是 scaffold 期的全部出口；L4 必须等 M3.5 首产物（G1）之后（规划 §10）。

---

## 2. 用例族索引（→ [`000-scaffold/test-cases.md`](./000-scaffold/test-cases.md)）

| 用例 ID | 名称 | 层 | 期望（可证伪） | 状态 |
|---|---|---|---|---|
| TC-01 | 干净工程可配置 | L2 | `gradle :pnpm-cli:tasks` BUILD SUCCESSFUL（无 .so） | ✅（2026-11-02） |
| TC-02 | AAR 容器可产出 | L2 | `gradle :pnpm-cli:assembleDebug` 产出 aar（实测 4 条目、无 jni/） | ✅（2026-11-02） |
| TC-03 | provision 未投放语义 | L1 | `node tools/provision.mjs --check` 退出码 = 2，文案含「未投放」 | ✅（2026-11-02 记录；本批规格会话复验 ✅） |
| TC-04 | 语法门 | L1 | `node --check` ×2 + `bash -n` 全过 | ✅（2026-11-02；本批复验 ✅） |
| TC-05 | 二进制不入库 | L1 | `git status --porcelain` 无 jniLibs/target 条目 | ✅（2026-11-02 记录） |

### 2.1 补充退出码契约用例（as-built 工具契约，[`000-scaffold/plan.md`](./000-scaffold/plan.md) §4）

| ID | 场景 | 期望 | 状态 |
|---|---|---|---|
| TC-EXT-01 | `provision --version 99.0.0`（lock 无该版本） | exit 1 + 列出现有条目 | ✅（本批复验：`现有：12.0.0/arm64-v8a`） |
| TC-EXT-02 | `provision --inspect <白名单外 DT_NEEDED 产物>` | 门禁④ FAIL + exit 2 | ⬜ 需合成/首产产物 |
| TC-EXT-03 | `provision --inspect <PT_INTERP≠/system/bin/linker64 产物>`（如 dlopen 库） | 门禁② FAIL + exit 2 | ⬜ 同上（可用同族仓产物做负样本） |
| TC-EXT-04 | 4KB 对齐产物 + `--allow-page-align-4k` | 门禁⑦被放行但打印 bring-up 警告；不加该开关则 exit 2 | ⬜ 同上 |
| TC-EXT-05 | jniLibs 混入第二 .so / `libnode.so` | `--check` 报兄弟文件不唯一（exit 2）；`--out` 投放遇 BANNED 文件报错（exit 1） | ⬜ 需构造目录 |
| TC-EXT-06 | 投放后 `--check` 正样本 | 门禁①–⑦全过（draft 态 ⑤⑥ 只记录）+ 兄弟文件唯一 + exit 0 | ⬜ M3.5-T5 |

> TC-EXT-01 以外均为工具契约的**可执行判据**，登记于此供 M3.5-T5 首产回归时逐条执行；当前因无产物/构造目录，未跑不构成缺口（C7：⬜ 如实标注）。

### 2.2 新模块用例族（2026-11-03 立项，规划 §10.2 拆分）

| 用例族 | 模块 | 覆盖 | 逐条清单 |
|---|---|---|---|
| TC-B01…B08 | 001-building（L1） | fetch 校验/幂等/不改源、门禁首产正样本、元数据、lock 回写实值 | [`001-building/test-cases.md`](./001-building/test-cases.md) |
| TC-W01…W07 | 002-building-workflow（L3） | workflow 结构、`bash -e` 语义修复、措辞清零、归档 sha256、lock draft→released 语义、发布卫生、探针防线 | [`002-building-workflow/test-cases.md`](./002-building-workflow/test-cases.md) |
| TC-C00…C16 | 003-cli（L4） | P1–P7 探针可执行化 + CLI 主用例（version/help/退出码/UTF-8/install/lifecycle/store/self-update/dlx/registry 透传/流分离） | [`003-cli/test-cases.md`](./003-cli/test-cases.md) |

> TC-C 族即规划 §7.1 真机探针的**用例化落点**（P1→TC-C01、P2→TC-C06、P3→TC-C08、
> P4→TC-C11、P5→TC-C10、P6→TC-C15、P7→TC-C05）；§3 索引表保留规划原文口径作对照。

---

## 3. 规划期真机探针索引（**规划引用**，规划 §7.1；⬜ 全部未实测）

| # | 探针 | 判据（规划 §7.1 原文口径） |
|---|---|---|
| P1 | `pty.spawn($NLDIR/libpnpm_cli.so, ['-v'])` | 版本串回程、exit/close 齐全 |
| P2 | `pnpm install <含 lifecycle script 的纯 JS 包>` | install 成功 + lifecycle script 经 PATH 上 node 执行成功（pnpm → sh → node → script 完整回程） |
| P3 | `pnpm store path` | store 落点 = filesDir（store 注入方式 a/b 的定案依据，R5） |
| P4 | `pnpm dlx <纯 JS 工具>` | 行为记录（不作为出口判据） |
| P5 | `pnpm self-update` | **必须失败**且无部分写入（D7 验证） |
| P6 | 16KB 页设备 exec | 正常执行（C4 硬要求） |
| P7 | `pnpm install` 走 TLS | 握手成功；根证书来源结论回填规划 §7（R6） |

---

## 4. 判据纪律（禁止的判据）

1. **「`--check` 全过 ⇒ 真机可用」**——门禁只覆盖 ELF 形态与锁值，不覆盖 SELinux/PATH 注入/lifecycle 回程；真机判据以 P1–P7 为准（⬜）。
2. **「CI 绿 ⇒ 门禁语义正确」**——必须核对退出码断言步骤是否真的区分了 2 与其它非零码（当前 ⚠️ 不可区分，[`overall-plan.md`](./overall-plan.md) §3.2）。
3. **「draft lock 通过校验 ⇒ 哈希已验证」**——draft 态 ⑤⑥ 是「只记录不比对」，任何引用必须注明 draft；released 态才有比对语义。
4. **未实测项一律 ⬜**，不得写成 ✅（C7）。
