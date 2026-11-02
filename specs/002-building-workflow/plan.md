# 002-building-workflow 技术方案（plan.md）

> **模块**：002-building-workflow（CI 真实流水线 + 归档 + GitHub Release + lock 回写 + 偏差修复）
> **对应规格**：[spec.md](./spec.md)（FR-002-xxx）· [tasks.md](./tasks.md) · [test-cases.md](./test-cases.md)
> **依据**：[`../constitution.md`](../constitution.md) · [`../../docs/000-工程规划.md`](../../docs/000-工程规划.md) §8/§9/§10.1/§10.2
> **上游**：[001-building](../001-building/plan.md)（脚本接口 §3——本模块直接编排其命令）
> **标记**：✅ 已实现 / ⬜ 规划未实现 / ⚠️ 风险。**版本**：v1.0 · 2026-11-03。

---

## 1. 技术上下文

| 项 | 取值 | 状态 |
|---|---|---|
| workflow 文件 | `.github/workflows/build-pnpm-cli-android.yml`（现有 50 行 scaffold 版 → 扩展为双 job） | ⬜ 重写 |
| runner | `ubuntu-24.04`（规划 §9） | ⬜ |
| 装机 | rustup stable + target + cargo-ndk（钉版）+ NDK + JDK 21（temurin）+ Node 20 + cargo-license | ⬜ |
| 现有 scaffold CI | 配置门（`wf:23-35`）/ 退出码纪律（`wf:37-45` ⚠️ 缺陷）/ 语法门（`wf:47-50`）三段 | ✅ 在位（保留为 config-check job） |
| 触发 | push(main)/PR → config-check；tag `v*` / dispatch → release | ⬜ |
| 归档 | `pnpm-cli-v<ver>-android-arm64-v8a.tar.gz` + `.sha256` | ⬜ 首产时组装 |

## 2. workflow 结构设计（目标形态）

```yaml
jobs:
  config-check:                    # scaffold 三段全保留（FR-002-014）
    on: push(main)/PR/dispatch     # 无 .so 必过（FR-000-040）+ 退出码纪律（修复后）+ 语法门
  release:                         # 仅 tag v<pnpmver> / dispatch 触发（FR-002-011）
    needs: []                      # 与 config-check 并行（tag push 时两者都跑）
    steps:
      - checkout（tag 钉 commit）
      - 装机（rustup/target/cargo-ndk 钉版/NDK/JDK21/Node20/cargo-license）
      - 探针结论检查（lock.openProbes ①② 已回写、③ 未触发 ⇒ 否则 fail）
      - node scripts/fetch-pnpm-cli-source.mjs --pnpm <ver> --sha256 <lock 值>
      - cargo-license 清单 + Cargo.lock 副本
      - bash scripts/build-pnpm-cli-android.sh ...（内含 provision --inspect 门禁）
      - 组装归档 tar.gz + .sha256（FR-002-013 清单）
      - GitHub Release（softprops/action-gh-release 或官方 CLI，needs: contents: write）
      - artifact 上传
```

要点：
- **退出码纪律写法**（FR-002-001，修 overall-plan §3.2 缺陷）：

```yaml
- name: provision check (expect exit 2 when not provisioned)
  run: |
    code=0
    node tools/provision.mjs --check || code=$?
    if [ "$code" -ne 2 ]; then
      echo "::error::expected exit 2 (not provisioned), got $code"
      exit 1
    fi
```

  显式 `shell: bash`（不用 `bash -e` 默认语义下的裸命令+`$?` 写法）。
- release job 的探针结论检查是 C8 的流水线侧防线（探针未闭环 ⇒ 不许 release）。

## 3. lock 回写 runbook（FR-002-021，人工步骤，规划 §8）

1. Release 成功后取归档 `.sha256` 与产物实测 size；
2. 回写 `releases[]`：size/sha256（命令取值）、builtFrom.ndk/flags、status=released；
3. openProbes ①② 改写结论（闭环即删条目，规划 §8）；
4. 重跑 `node tools/provision.mjs --check`（未投放态应仍 exit 2——哈希已锁但产物未投放；
   投放后 exit 0）；draft→released 语义变化（⑤⑥ 从只记录转比对）在 TC-W05 验证；
5. `mavenCentral.published` 保持 false（M4 核验后才翻转）。

## 4. 宪法合规要点

| 原则 | 本模块动作 |
|---|---|
| C2 | 归档 sha256/lock 回写全部命令取值；workflow 内不硬编码版本（从 lock/tag resolve） |
| C3 | CI 产物只走 Release/artifact；repo 内零二进制（FR-002-023） |
| C4 | 流水线内嵌 `provision --inspect` 门禁步骤，失败即 red（无绕过路径） |
| C6 | workflow 无发布凭据 secrets；Maven Central 不入 CI（M4 runbook）；Release 权限最小化（job 级 contents: write） |
| C7 | GitHub 远端运行记录 ⬜ 标注；本地只声明「静态面已验」 |

## 5. FR 交叉引用

| FR | 实现落点 | 判据 |
|---|---|---|
| FR-002-001/002 | config-check job 退出码步骤重写 + 两处「M1」措辞 | TC-W01/W02 |
| FR-002-010~013 | release job（§2 结构） | TC-W03/W04 |
| FR-002-014 | config-check job 保留 | workflow 结构审查 |
| FR-002-015 | 缓存策略登记（⬜ 首轮实测后定） | plan 回填 |
| FR-002-020~023 | Release 步骤 + §3 runbook + 权限检查 | TC-W04/W05/W06 |

## 6. 测试考虑（→ [test-cases.md](./test-cases.md)）

本地可验证面：yaml 语法/结构、`bash -e` 语义模拟、退出码区分逻辑、归档组装与 sha256、
lock 回写语义；远端运行 ⬜（push 后观察，C7 如实标注）。
