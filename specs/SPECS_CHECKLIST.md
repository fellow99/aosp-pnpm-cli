# SPECS_CHECKLIST.md —— 规格文档完成度追踪

> 工程：`aosp-pnpm-cli`（`io.github.fellow99:pnpm-cli`）· 需求：**000-scaffold 工程搭建**
> 规范文档存放目录：`specs/` · 生成日期：**2026-11-02**（specs-as-built 方法论：先项目级、后模块级、最后扫描生成索引）
> 权威规划文档：[`../docs/000-工程规划.md`](../docs/000-工程规划.md)（v2.0，**结论以它为准**）
>
> 状态口径：✅ 已完成 · ⚠️ 已完成但含未闭合项（见备注）· ⬜ 待生成

---

## 一、项目级文档

| # | 文档 | 路径 | 状态 | 说明 |
|---|---|---|---|---|
| P-01 | 项目宪法 | [constitution.md](./constitution.md) | ✅ | 9 条不可协商原则（C1–C9：fetch 不改源 / lock 唯一事实源 / 二进制不入库 / 门禁强制 / 不下载上游二进制 / 发布卫生 / 未实测 ⬜ / 前提推翻即冻结 / 先改文档再改实现）+ 命名打包纪律 N1–N5 + 评审清单 + 变更流程 |
| P-02 | 目录结构 | [STRUCTURE.md](./STRUCTURE.md) | ✅ | 实际目录树（逐文件行数）+ 目录↔FR 映射 + 产物落点 + 不入库清单 + 规划路径防误引用 + 3 条结构发现 |
| P-03 | 技术选型 | [TECH.md](./TECH.md) | ✅ | 选型总表含**已实现 vs 规划**状态列（Gradle 9.4.1/AGP 9.2.1/vanniktech 0.37.0/exec 门禁纯 Node 已实现；rust aarch64-linux-android+cargo-ndk/发布两段式为规划引用）+ 与 pty 门禁差异表 + 被否方案 + 版本矩阵 + 风险 |
| P-04 | 整体架构 | [ARCHITECTURE.md](./ARCHITECTURE.md) | ✅ | 三层 ASCII 图（门禁层/制品层/运行期组合）+ AAR 实测组成 + exec 门禁数据流 + node-cli/node-runtime 组合（规划标注）+ D1–D8 落点对照 + 已知缺口 |
| P-05 | 整体规格 | [overall-spec.md](./overall-spec.md) | ✅ | 技术无关：目的/用户/用户故事/六个能力面/边界（self-update 禁用、生态包平台二进制不可用、lifecycle 依赖 node）/FR-000-xxx 沿用（RFC 2119 + 可证伪判据）/NFR/验收出口 |
| P-06 | 整体技术方案 | [overall-plan.md](./overall-plan.md) | ✅ | 技术上下文 + 宪法符合性逐条（C1–C9，C9 部分）+ 横切关注点（退出码纪律全链 0/2/1、CI shell 语义、版本防漂移、安全/凭据、可复现）+ 测试途径 + 发布策略 + 偏差登记 |
| P-07 | 测试用例索引 | [overall-test-cases.md](./overall-test-cases.md) | ✅ | 四层测试分层 + TC-01…TC-05 索引 + TC-EXT 契约用例 + 规划期 P1–P7 探针索引（规划引用）+ 禁止的判据 |
| P-08 | 检查清单 | SPECS_CHECKLIST.md | ✅ | 本文件 |
| P-09 | 规格索引 | [README.md](./README.md) | ✅ | 最后生成：按实际目录扫描 + 链接全验 |
| — | `API.md` / `overall-api.md` | — | ⛔ 刻意不生成 | 本仓**不含任何嵌入 API**（AAR 无 Kotlin/Java 源、无对外接口面；`README.md:14` 自述）。 |
| — | `overall-data-model.md` | — | ⛔ 刻意不生成 | 唯一数据实体 `pnpm-cli.lock.json` 的逐字段模型在 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §3 给出，单实体不单立文档。 |

---

## 二、模块级文档

| # | 模块 | 目录 | spec.md | plan.md | tasks.md | test-cases.md | 状态 | 备注 |
|---|---|---|---|---|---|---|---|---|
| M-01 | 000-scaffold 工程搭建 | [`000-scaffold/`](./000-scaffold/) | ✅ | ✅ | ✅ | ✅ | ⚠️ | M0 骨架全部落地且 TC-01…TC-05 过（2026-11-02 记录 + 本批复验）；⚠️ 未闭合：CI 退出码步骤 `bash -e` 缺陷、门禁正样本待首产物（⬜ 移交 001）、CI 无仓内运行记录 ⬜；2026-11-03 登记模块边界与移交（spec §4 / plan §10 / tasks 移交节） |
| M-02 | 001-building 构建 | [`001-building/`](./001-building/) | ✅ | ✅ | ✅ | ✅ | ⬜ | 2026-11-03 立项（规划 §10.2）：探针①②③ + fetch + cargo 交叉编译 + strip + 门禁首产；FR-001-xxx；全部待实现（依赖 M3.5-T1…T5） |
| M-03 | 002-building-workflow 构建流水线 | [`002-building-workflow/`](./002-building-workflow/) | ✅ | ✅ | ✅ | ✅ | ⬜ | 2026-11-03 立项：CI 真实流水线 + 归档/Release + lock 回写 + 偏差修复（`bash -e` 缺陷、「M1」措辞）；FR-002-xxx；全部待实现（依赖 M3.5-T6） |
| M-04 | 003-cli CLI 本体 | [`003-cli/`](./003-cli/) | ✅ | ✅ | ✅ | ✅ | ⬜ | 2026-11-03 立项：P1–P7 探针可执行化 + CLI 主用例 TC-C00…C16；FR-003-xxx；全部待执行（依赖 001 首产物 + 测试载体） |
| M-05 | 101-probe-app 设备回程探针载体 | [`101-probe-app/`](./101-probe-app/) | ✅ **本批新增** | ✅ **本批新增** | ✅ **本批新增** | ✅ **本批新增**（TC-C00…C16 实际探针 19 条 + host TC-101-PROBE-10…13） | ⚠️ 已实现（as-built 归档，2026-11-05） | FR-101-PROBE-001…009；逆向既有 19 条探针载体 + 本批加 `--ez autorun true` 无头触发（对齐统一测试队列）+ APK 构建验证（BUILD SUCCESSFUL）；设备跑批 ⬜ 由编排者统一排队；pnpm 不补探针、registry 注入为环境配置范围外 |
**成对纪律**：每个模块目录必须成对提供 `spec.md`（技术无关需求与验收）与 `plan.md`（实现方案，交叉引用本模块 FR-xxx）；`tasks.md` / `test-cases.md` 为可选追加件（本仓模块均有）。当前模块数 5（000 闭合 + 001/002/003 于 2026-11-03 按规划 §10.2 拆分立项 + 101-probe-app 于 2026-11-05 as-built 归档）。

---

## 三、生成顺序与依据

1. **先整体、后模块**：项目级文档（P-01…P-07）先建立宪法、术语、FR 编号与证据口径，模块 `plan.md` 再细化 —— 避免前后引用不存在的文件。
2. **最后生成索引**：`README.md` 与本清单按**实际目录内容**收尾，不预测结构。
3. **不复制粘贴**：所有结论回引 `docs/000-工程规划.md` 的 § 号（§1–§13 / D1–D8 / R1–R10）；新增平台结论必须先落规划文档（C9）。
4. **证据标记**：每条非显然结论带 ✅ / ⬜ / 🔶 / ⚠️（口径见 [`constitution.md`](./constitution.md) C7）。

---

## 四、质量检查

| 检查项 | 结果 |
|---|---|
| 每条结论可回溯到源码 `path:line` 或规划 § 号 | ✅ |
| 无占位内容（`TODO` / `TBD` / 「待补充」；lock 内 `TBD` 是 as-built draft 实值，非文档占位） | ✅ |
| `overall-spec.md` / `spec.md` 不含实现细节（框架名、文件路径） | ✅（spec.md 为既有件，其 §2 摘要含少量既有措辞，本批未改动） |
| `plan.md` 引用真实文件路径与真实行数（逐文件读过） | ✅ |
| `plan.md` 交叉引用本模块 `spec.md` 的 `FR-000-*` | ✅（§7 交叉引用表） |
| as-built 与 planned 逐条区分（⬜ + 「规划引用」字样） | ✅ |
| 规划只以「规划引用」出现（M3.5 rust 交叉编译、openProbes、P1–P7、两段式发布） | ✅ |
| `[NEEDS CLARIFICATION]` 使用 | 0 处（无未确认断言；不确定处均以 ⬜/⚠️ 登记） |
| `README.md` 链接全部指向存在的文件 | ✅（生成时逐一核对） |
| 模块数与 `NNN-*/` 实际目录数一致 | ✅（1/1） |

---

## 五、未闭合项（不属文档缺失，属**实现**阶段工作）

| 项 | 归属 | 依据 |
|---|---|---|
| CI 退出码步骤 `bash -e` 语义缺陷修复（含「M1」措辞回写 ×2） | M3.5-T6 | [`000-scaffold/plan.md`](./000-scaffold/plan.md) §6.2 / [`overall-plan.md`](./overall-plan.md) §6-1/2 |
| 探针①②（上游二进制核实 / Rust 依赖可编性试编）与 ③ 冻结出口 | M3.5-T1/T2 | 规划 §4.1 / §11 R1–R3 |
| fetch/build 真实实现（钉版源 + cargo 交叉编译 + strip） | M3.5-T3/T4/T5 | 规划 §4.3 |
| lock 首产回写（size/sha256/ndk/flags/status=released） | M3.5 首产 | 规划 §8 |
| 门禁/投放正样本回归（TC-EXT-02~06） | M3.5-T5 | [`overall-test-cases.md`](./overall-test-cases.md) §2.1 |
| CI 真实流水线（rust/ndk 装机 + Release + 归档） | M3.5-T6 | 规划 §9 |
| 真机回程 P1–P7 + store 注入 a/b 定案 + self-update 文档回写 | M3.5-T7 | 规划 §7/§7.1 |
| GitHub Release（两段式第一段）与 Maven Central（第二段） | M3.5-T6 / M4 | 规划 §10/§10.1 |
| `VERSION_NAME=0.1.0` 模板遗留清理 | M3.5 收口（顺手） | [`STRUCTURE.md`](./STRUCTURE.md) §7-1 |
| cargo 依赖 sha 记录粒度定案（可能新增 `builtFrom.cargoLock`） | M3.5-T3 | 规划 §4.2/§8 / R9 |
