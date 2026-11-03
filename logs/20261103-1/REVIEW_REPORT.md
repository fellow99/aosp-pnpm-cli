# REVIEW_REPORT.md — 代码评审（specs-based-devflow Step 3.5）

> 评审对象：`5fa7509..d035e96`（7 提交：规范四件套 ×3 模块 + fetch/build 实现 + CI 流水线 + lock 首产回写）
> 评审方式：requesting-code-review → code-reviewer 子代理（read-only）→ receiving-code-review 逐条核实处置
> 日期：2026-11-03

## 结论

**0 Critical / 4 Important / 9 Minor**。整体质量良好（宪法 C1–C8 全过，C9 部分欠账）。
Important 1–3 + 精选 Minor 已在本轮修复；其余登记为后续项。

## 处置明细

### Important（全部处置）

| # | 发现 | 处置 | 落点 |
|---|---|---|---|
| I-1 | tar 解析器 pad-only/rest 状态机丢字节（潜伏流错位；现不可达——依赖 zlib 16KB 分块不变量，未成文） | ✅ 修复：重写为单一 `consume` 状态机——buf 永只追加+头部消费，挂起态不清空/替换 buf；顺带修 paxPath 残留（Minor#1 同批） | fetch-pnpm-cli-source.mjs extractTarGz 重写 |
| I-2 | cargo-license 失败=告警后继续，空清单可蒙混过归档清单核对（违反 FR-002-012 无警告后继续） | ✅ 双修：build 失败即删空文件 + package 对 LICENSES-cargo.txt 加 `-s` 非空校验（G2 纪律） | build:③ / package:清单核对 |
| I-3 | fetch 幂等快路径绕过 SHA-256 拒收契约（meta.sha256≠期望仍 exit 0） | ✅ 修复：期望哈希存在且不符 ⇒ 落入正常下载/校验流程重取（不再 exit 0） | fetch 幂等块 |
| I-4 | draft 态门禁语义三方不一致（lock 已填实值但 provision:197 文案称「无锁值仅记录」） | ✅ 修复文案（选择「实现比宪法更严」口径：值存在即比对；released 翻转语义不变），规划/plan 以 as-built 登记为准 | provision.mjs:197 |

### Minor（本轮修复 5 项）

| # | 发现 | 处置 |
|---|---|---|
| M-1 | pax 覆盖路径在 dir/skip 分支失效/残留 | ✅ 随 I-1 重写（每头处理后统一清理 paxPath） |
| M-2 | download 无超时；写盘错误 uncaught（exit 1 非 2）；下载异常不清理 tmp | ✅ AbortSignal.timeout(300s) + out.on('error')→die(2) + catch 清理 tmpTar |
| M-4 | openProbes gate 固定读 releases[0] | ✅ 改按 resolved version 查找（兜底 releases[0]） |
| M-6 | C9 欠账：002 spec FR-002-011/012、003 spec FR-003-030 与实现/P5 实测不一致 | ✅ 三处 spec 条目修订（as-built 口径 + 实测证据引用） |
| M-7/M-9 | build:121 注释 180s 与代码 300s 不符；`command -v cargo ndk` 语义误导 | ✅ 均修正 |

### Minor（登记后续，不在本轮）

| # | 发现 | 登记理由 |
|---|---|---|
| M-3 | workflow `${{ inputs.* }}` 直插 shell（建议 env: 传参） | dispatch 需写权限，风险低；下次 workflow 触碰时顺带 |
| M-5 | release job 未 needs: config-check（tag 路径 config-check 红 Release 仍可发）；plan.md 已登记并行为有意设计 | 两段式首走（打 tag）前建议加依赖；随 002-T6 执行 |
| M-8 | Actions 浮动 tag 引用（供应链加固：钉 commit SHA） | 同 M-3 时机 |
| M-4b | openProbes gate 判定词表（closed/frozen/triggered）未在 spec 登记为措辞契约 | 改 lock 探针文案会静默解除门禁——建议 002 spec 增补词表契约 |
| M-10 | tar 解析器缺「pad 跨 chunk / x+symlink 序列」合成 tarball 回归用例 | 随 001 plan §6 补 TC-B09（构造用例） |

## 修复验证

- `node --check fetch` / `bash -n build/package/deploy` 全过
- fetch 端到端重取（真实 tarball，新解析器）：幂等命中（哈希一致）——见下执行记录
- `provision --check` exit=2（未投放）语义不变；新 draft 文案输出
- workflow 结构断言（TC-W01 断言集）重跑通过
