# 002-building-workflow 任务拆解

> 对应：[spec.md](./spec.md)。原则：先修偏差（000 遗留）再扩展；config-check 门禁不回退。

- [ ] T1 修复 `bash -e` 退出码缺陷（FR-002-001）：config 检查步骤改 `code=0; cmd || code=$?` 写法 + 显式 shell
- [ ] T2 「M1」措辞回写 ×2（FR-002-002）：`.github/workflows/build-pnpm-cli-android.yml` 头注释 + `pnpm-cli/THIRD-PARTY-NOTICES.md`
- [ ] T3 workflow 重构为双 job（config-check 保留 + release 新增）：触发/权限/装机清单（FR-002-010/011/014）
- [ ] T4 release 步骤链编排（FR-002-012）：探针结论检查 → fetch → license 清单 → build（含门禁）→ 归档 → Release → artifact
- [ ] T5 归档组装脚本（tar.gz + .sha256，FR-002-013 清单核对）
- [ ] T6 lock 回写 runbook 执行（FR-002-021）：首产 Release 后回写 size/sha256/ndk/flags/status + openProbes 闭环
- [ ] T7 缓存策略结论登记（FR-002-015；首轮实测后回填 plan）
- [ ] T8 验证：TC-W01…W06 全过（本地静态面）；提交（父工程不提交）
