# 002-building-workflow 测试用例

> 对应：[spec.md](./spec.md)。本地验证「静态面 + 语义模拟」；GitHub 远端运行 ⬜（C7）。
> 状态口径：⬜ 待执行；执行记录随开发阶段回填。

| # | 用例 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|
| TC-W01 | workflow 语法与结构 | yaml 解析（node js-yaml 或 python yaml）+ 结构断言（双 job、触发、权限） | 解析无误；config-check 保留三段语义；release 仅 tag/dispatch 触发 | ⬜ |
| TC-W02 | 退出码区分逻辑（`bash -e` 语义模拟） | `bash -e -c '<修复后的代码块>'` 分别注入 exit 2 / exit 1 / exit 0 模拟 provision | exit 2 ⇒ step 成功；exit 1/其它 ⇒ step 失败（区分逻辑**可达**）；对照旧写法 exit 2 必失败（证明缺陷已修） | ⬜ |
| TC-W03 | 「M1」措辞清零 | `grep -rn "M1" .github/workflows/ pnpm-cli/THIRD-PARTY-NOTICES.md` | 无「M1」里程碑口径残留（M3.5 口径一致） | ⬜ |
| TC-W04 | 归档组装与 sha256 | 用 001 首产产物本地执行归档脚本 | tar.gz 内容 = FR-002-013 清单逐项；`.sha256` 与 `sha256sum` 实测一致；解包复核 | ⬜ |
| TC-W05 | lock 回写语义（draft→released） | 回写后 `node tools/provision.mjs --inspect <产物>` | released 态 ⑤⑥ 从「只记录」转**比对**：哈希一致 ⇒ ✔；篡改产物 ⇒ ✘ + exit 2（负样本） | ⬜ |
| TC-W06 | 发布卫生检查 | workflow 全文 grep：secrets/Token/GPG/RELEASE | 无凭据注入；Maven 发布步骤不在 CI；权限最小化（contents: read 为默认） | ⬜ |
| TC-W07 | 探针结论检查步骤 | 模拟 lock.openProbes 未回写态跑 release job 的检查步骤（本地等价） | 未闭环 ⇒ 步骤失败（C8 流水线防线） | ⬜ |

## 执行记录

（开发阶段回填；GitHub 远端运行记录 ⬜ 随 push tag 后观察补充）

## 执行记录（2026-11-03，开发阶段——本地静态面）

- TC-W01 ✅：workflow 结构断言全过（双 job / tag `v*` 触发 / dispatch / 权限分离 contents: read→write / 修复写法 / 装机清单 / fetch→build→归档→Release 步骤链）。
- TC-W02 ✅：`bash -e` 语义模拟——修复写法下 exit 2 被 `|| code=$?` 捕获且断言通过（step 成功）；exit 1 ⇒ step 失败；**对照旧写法 exit 2 必失败（缺陷实证）**。
- TC-W03 ✅：`grep -rn "M1" .github/workflows/ pnpm-cli/THIRD-PARTY-NOTICES.md` 无残留（THIRD-PARTY-NOTICES 已更新为探针②初步结论口径）。
- TC-W04…W07：⬜ 待首产物（W04/W05 依赖 001 产出；W06/W07 随 workflow 定稿跑）。GitHub 远端运行记录 ⬜（push 后观察）。
