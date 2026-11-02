# 规格文档索引 —— `aosp-pnpm-cli`

**项目名称：** `aosp-pnpm-cli`（Maven 坐标 `io.github.fellow99:pnpm-cli`，当前 draft `12.0.0`）
**需求：** **000-scaffold 工程搭建** · 规范文档存放目录：`specs/`
**库形态：** 纯二进制 AAR——Android 版 `pnpm` 命令行（pnpm 12+ Rust 独立二进制，arm64-v8a，exec 型 PIE，`libpnpm_cli.so`）；仓库只含**可复现构建、exec 门禁与发布流水线**，不含任何嵌入 API
**技术栈：** Gradle 9.4.1 + AGP 9.2.1 + vanniktech 0.37.0（发布面）+ 纯 Node ESM 工具链（provision 门禁/投放）+ ⬜规划：rust `aarch64-linux-android` + cargo-ndk（M3.5）
**文档生成时间：** 2026-11-02 · **状态：** M0 骨架已完成；M3.5（交叉编译/首产/真实 CI）未开工

> **权威规划文档是 [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（v2.0，含 D1–D8 决策与 M3.5 实现设计）。**
> 本目录是它的**收敛/展开版**：只做细化与验收，**不复制粘贴**，所有结论回引规划 § 号。
> as-built 纪律：源码即真相；规划（M3.5 交叉编译、openProbes 探针、真机回程 P1–P7 等）只以「规划引用」出现并标注 ⬜，绝不写成既成事实。

---

## 一、文档总览

| 层级 | 分类 | 文档数量 | 说明 |
|---|---|---|---|
| 整体 | 项目级顶层文档 | 5 | `constitution.md` · `ARCHITECTURE.md` · `TECH.md` · `STRUCTURE.md` · `SPECS_CHECKLIST.md` |
| 整体 | 整体规格文档 | 3 | `overall-spec.md` · `overall-plan.md` · `overall-test-cases.md` |
| 模块 | 000-scaffold（工程搭建） | 4 | `spec.md` · `plan.md`（本批新增）· `tasks.md` · `test-cases.md` |
| 索引 | 本索引 | 1 | `README.md` |
| **合计** | **2 目录 / 13 文件** | | 模块目录 1 个 |

> **刻意不生成** `API.md` / `overall-api.md` / `overall-data-model.md`：本仓不含任何嵌入 API（AAR 无 Kotlin/Java 源）；唯一数据实体 `pnpm-cli.lock.json` 的逐字段模型在 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §3。

---

## 二、项目级顶层文档

全局性的架构、技术、宪法与结构文档，定义项目基线与开发准则。

| 文档 | 路径 | 说明 |
|---|---|---|
| **项目宪法** | [constitution.md](./constitution.md) | 9 条不可协商原则（C1 fetch 不改源 · C2 lock 唯一事实源 · C3 二进制不入库 · C4 门禁强制 · C5 不下载上游二进制/运行期不下载可执行 · C6 发布卫生 · C7 未实测标 ⬜ · C8 前提推翻即冻结 · C9 先改文档再改实现）+ 命名打包纪律 N1–N5 + 评审清单 + 变更流程 |
| **整体架构** | [ARCHITECTURE.md](./ARCHITECTURE.md) | 三层 ASCII 图（门禁层 / AAR 制品层 / 与 node-cli·node-runtime 的运行期组合〔规划标注〕）、AAR 实测组成、exec 门禁数据流与 pty 门禁差异、D1–D8 决策落点对照、已知缺口 |
| **技术选型** | [TECH.md](./TECH.md) | 技术/版本/用途/**已实现 vs 规划状态列**（Gradle 9.4.1 · AGP 9.2.1 · vanniktech 0.37.0 · 纯 Node exec 门禁 ✅；rust aarch64-linux-android + cargo-ndk · 发布两段式 ⬜）、被否方案、版本矩阵、风险 |
| **项目结构** | [STRUCTURE.md](./STRUCTURE.md) | 实际目录树（逐文件行数）+ 目录↔FR 映射 + 构建产物落点 + 不入库清单 + 规划路径防误引用 + 3 条结构发现 |
| **检查清单** | [SPECS_CHECKLIST.md](./SPECS_CHECKLIST.md) | 规格文档完成度追踪（项目级 + 模块级）+ 质量检查 + 未闭合项 |

### 整体规格文档

| 文档 | 路径 | 说明 |
|---|---|---|
| **整体规格** | [overall-spec.md](./overall-spec.md) | 技术无关：目的/用户/用户故事/边界——**self-update 禁用（D7）、生态包平台二进制不可用（C2/C5）、lifecycle scripts 依赖 node（D5）**、FR-000-xxx 沿用（RFC 2119，每条带可证伪判据）、NFR、验收出口 |
| **整体方案** | [overall-plan.md](./overall-plan.md) | 技术上下文、宪法符合性逐条检查（C1–C9）、横切关注点（退出码全链 0/2/1、CI shell 语义发现、版本防漂移、凭据/供应链、可复现性）、测试途径、两段式发布策略 |
| **测试用例索引** | [overall-test-cases.md](./overall-test-cases.md) | 四层测试分层（L1 门禁 / L2 配置 / L3 CI / L4 真机）+ TC-01…05 与 TC-EXT 契约用例索引 + 规划期 P1–P7 探针索引 + 禁止的判据 |

---

## 三、模块级文档

### 000 — 工程搭建 (`000-scaffold`)

> M0 已建成：Gradle 骨架（rootName=aosp-pnpm-cli、`:pnpm-cli` AAR 容器、`useLegacyPackaging=true`、vanniktech 0.37.0、`CLI_VERSION=12.0.0` draft 独立 semver）、`tools/provision.mjs`（exec 门禁七项 + 投放禁区 + 退出码 0/2/1）、draft `pnpm-cli.lock.json`（含 **openProbes ①②③ 决策记录**）、fetch/build 脚本骨架（exit 2）、配置期 CI、README 中英、docs/000-工程规划.md（v2.0）。**pnpm 本体尚未构建（M3.5）。**

| 文档 | 链接 | 说明 |
|---|---|---|
| 功能规格 | [000-scaffold/spec.md](./000-scaffold/spec.md) | 模块目的、功能需求摘要（FR-000-001…042）、验收场景（既有件） |
| 技术方案 | [000-scaffold/plan.md](./000-scaffold/plan.md) | **as-built 实现方案（本批新增）**：技术上下文、宪法合规逐条、lock JSON 数据模型（含 openProbes 字段与回写规则）、provision CLI 与退出码契约、Gradle/发布面、脚本与 CI（含 ⚠️ bash -e 发现）、FR 交叉引用表、文件清单（真实行数）、M3.5 待实现清单 |
| 任务拆解 | [000-scaffold/tasks.md](./000-scaffold/tasks.md) | T1–T8 全部完成（既有件） |
| 测试用例 | [000-scaffold/test-cases.md](./000-scaffold/test-cases.md) | TC-01…TC-05 与执行记录（2026-11-02，既有件） |

---

## 四、模块编号一览

| 编号 | 模块名 | 英文名 | 分类 | 规划出处 | 状态 |
|---|---|---|---|---|---|
| 000 | 工程搭建 | scaffold | 骨架 | 规划 §10（M0） | ✅ 已落地 |
| — | Rust 交叉编译流水线 | （M3.5 接入，不新开编号） | 构建 | 规划 §4/§9/§10 | ⬜ 规划（M3.5） |
| — | 发布（GitHub Release → Maven Central） | （两段式，随 M3.5/M4） | 发布 | 规划 §10.1 | ⬜ 规划 |

> 编号约定：M3.5 里程碑工作在现有 `000-scaffold` 规格与规划 §10 任务表内推进，**不预建模块目录**；待出现独立可验收的模块边界时再按 `NNN-<名>` 扩展（C9：先改规划再改结构）。

---

## 五、快速导航

| 目标读者 | 推荐阅读顺序 |
|---|---|
| **新加入开发者** | [`constitution.md`](./constitution.md) → [`STRUCTURE.md`](./STRUCTURE.md) → [`overall-spec.md`](./overall-spec.md) → [`ARCHITECTURE.md`](./ARCHITECTURE.md) → [`000-scaffold/spec.md`](./000-scaffold/spec.md) |
| **架构师 / Tech Lead** | [`ARCHITECTURE.md`](./ARCHITECTURE.md) → [`TECH.md`](./TECH.md) → [`overall-plan.md`](./overall-plan.md) → 规划 §3/§4 |
| **实现者（M3.5）** | 规划 §4/§9/§10 → [`000-scaffold/plan.md`](./000-scaffold/plan.md) §3/§4/§6/§10 → [`constitution.md`](./constitution.md) C1–C9 |
| **消费方（接入 AAR）** | [`../README_zh.md`](../README_zh.md)（运行期依赖必读）→ [`overall-spec.md`](./overall-spec.md) §5（边界与已知限制） |
| **测试 / QA** | [`overall-test-cases.md`](./overall-test-cases.md) → [`000-scaffold/test-cases.md`](./000-scaffold/test-cases.md) → [`SPECS_CHECKLIST.md`](./SPECS_CHECKLIST.md) §五 |
| **评审 / 合规** | [`constitution.md`](./constitution.md) §三（评审清单）→ [`overall-plan.md`](./overall-plan.md) §2/§6 → 规划 §12 |

### 快照命令（先看证据，再看结论）

```bash
gradle :pnpm-cli:tasks                 # 配置期门禁（无 .so 必须过）
gradle :pnpm-cli:assembleDebug         # AAR 容器产出
node tools/provision.mjs --check       # 退出码 0/2/1；未投放=2（当前 draft 态即此）
node tools/provision.mjs --inspect <libpnpm_cli.so>   # 只读门禁报告
node --check tools/provision.mjs && node --check scripts/fetch-pnpm-cli-source.mjs && bash -n scripts/build-pnpm-cli-android.sh
```

---

**文档维护者：** `aosp-pnpm-cli` 开发团队 · **权威来源：** [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（v2.0）
