# 002 · 构建流水线（building-workflow）功能规格

> 模块：002-building-workflow · 状态：⬜ 规划（2026-11-03 立项）· 所属工程：aosp-pnpm-cli
> 规划基线：[`docs/000-工程规划.md`](../../docs/000-工程规划.md) §9（CI 设计）/ §10.1（两段式发布）/ §8（lock 回写）/ §10.2（模块拆分）
> 上位约束：[`../constitution.md`](../constitution.md) C2/C3/C4/C6/C7；承接 [000-scaffold](../000-scaffold/spec.md) FR-000-040~042 的偏差修复。

## 1. 目的

把 [001-building](../001-building/spec.md) 的构建能力编排为**可复现的 CI 流水线**：
干净 runner 单条 workflow 重出同口径产物 → 过门禁 → 归档 → GitHub Release → lock 回写；
并修复 scaffold 期登记的 CI 偏差（`bash -e` 退出码语义缺陷、「M1」措辞 ×2）。

## 2. 功能需求（FR-002-xxx，RFC 2119）

### 2.1 scaffold 偏差修复（前置，承接 000-scaffold ⚠️ 登记）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-002-001 | MUST | 修复 CI 退出码步骤 `bash -e` 缺陷（FR-000-041 ⚠️）：`provision --check` 的「2=预期、其它非零=缺陷」区分逻辑**可达** | workflow 用 `code=0; cmd \|\| code=$?` 写法；本地 `bash -e` 模拟：exit 2 时 step 不失败、exit 1/其它时 step 失败 |
| FR-002-002 | MUST | 「M1」措辞回写 ×2：workflow 头注释与 THIRD-PARTY-NOTICES 的里程碑口径与规划 v2.0（M3.5）一致 | 两文件 grep 「M1」无残留（M3.5 口径） |

### 2.2 流水线编排（规划 §9）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-002-010 | MUST | runner 装机清单：rustup stable + `aarch64-linux-android` target + cargo-ndk（钉版）+ NDK（对齐线）+ JDK 21 + Node 20 + curl/file/tar + 许可清单工具（cargo-license/cargo-about 择一） | workflow 装机步骤逐项在位；工具版本可从 run log 复核 |
| FR-002-011 | MUST | 触发：tag `v<pnpmver>` push 或 workflow_dispatch；`contents: read` 最小权限（Release 步骤按需提权） | push 普通 commit **不**触发 release job；config-check job 保持 push/PR 触发 |
| FR-002-012 | MUST | 步骤链：resolve version → fetch（钉版+sha256）→ cargo license 清单 → cross build（16KB link-arg）→ strip → `provision --inspect` 全门禁 → 归档 → Release → artifact 上传 | 步骤顺序与规划 §9 一致；任一步失败即流水线失败（无「警告后继续」） |
| FR-002-013 | MUST | 归档物：`pnpm-cli-v<ver>-android-arm64-v8a.tar.gz`（libpnpm_cli.so + BUILD-METADATA + FETCH-METADATA + LICENSES + cargo 依赖许可清单 + Cargo.lock 副本）+ 旁挂 `.sha256` | 归档内容清单逐项在位（G2）；`.sha256` 与 tar.gz 实测一致 |
| FR-002-014 | MUST | 配置期门禁**保留**（scaffold CI 的 config-check job：无 .so 时 `gradle :pnpm-cli:tasks` 必过；宪法变更流程 4） | 替换/扩展后 config-check job 仍在且语义不变（FR-000-040 不回退） |
| FR-002-015 | SHOULD | cargo 缓存策略（registry/git/target）：首轮耗时实测后定；未定前不强求 | 缓存策略结论登记（做/不做+理由）于本模块 plan |

### 2.3 lock 回写与发布纪律（规划 §8/§10.1，C2/C6）

| 编号 | 级别 | 需求 | 可证伪的验收判据 |
|---|---|---|---|
| FR-002-020 | MUST | GitHub Release `v<pnpmver>`（tag 钉 commit）；**前置 openProbes ①② 已回写结论**（③ 未触发） | Release 页 assets 含归档+sha256；Release 前流水线有探针结论检查步骤 |
| FR-002-021 | MUST | lock 回写（人工，M4 评估自动化）：size/sha256/ndk/flags/status=released + openProbes 回写 | 回写后 `provision --check`（未投放）语义正确；released 态 ⑤⑥ 生效比对逻辑就绪 |
| FR-002-022 | MUST | 发布卫生：RELEASE=false 默认不动；CI 内**无**任何凭据；Maven Central 发布不在本模块 CI 内（M4 runbook） | workflow 无 secrets 注入发布凭据；grep 无 Portal Token/GPG |
| FR-002-023 | MUST | 归档物**不入库**（C3）：CI 产物只存在于 Release/artifact | 仓库 git log 无二进制提交；`.gitignore` 覆盖不因 CI 改动而收窄 |

## 3. 验收场景

- 本地 shell 模拟 `bash -e` 语义验证退出码区分（FR-002-001）；
- workflow 语法门（yaml 解析 / actionlint）+ 步骤链 dry-run 检查；
- GitHub 实际运行记录 ⬜（需 push tag 后观察——**CI 运行依赖远端触发，本地只验静态面**）；
- 归档与 `.sha256` 一致性本地可验证（用 001 首产产物本地组装归档验证 FR-002-013）。

## 4. 边界

- **不做**交叉编译本身（001）；**不做**真机运行验证（003）；**不做** Maven Central 发布（M4）；
- CI 真实运行记录属远端依赖：本模块交付 workflow + 本地等价验证，远端绿 ⬜ 随 push 观察回填（C7）。
