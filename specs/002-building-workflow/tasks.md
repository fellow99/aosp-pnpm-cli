# 002-building-workflow 任务拆解

> 对应：[spec.md](./spec.md)。原则：先修偏差（000 遗留）再扩展；config-check 门禁不回退。

- [x] T1 修复 `bash -e` 退出码缺陷（FR-002-001）✅（TC-W02 本地模拟 + CI config-check 真实绿）
- [x] T2 「M1」措辞回写 ×2（FR-002-002）✅（TC-W03 grep 清零）
- [x] T3 workflow 重构为双 job ✅（release 扩展 main push 构建验证；tag 才创建 Release）
- [x] T4 release 步骤链编排（FR-002-012）✅（run 41568096296 全链跑通）
- [x] T5 归档组装脚本（FR-002-013）✅（TC-W04 真产物验证 + 本地哑文件冒烟）
- [ ] T6 lock 回写 runbook 执行（FR-002-021）：**size/sha256/ndk/flags 已回写（draft 态）**；status=released 翻转待 GitHub Release（打 tag v12.16.1 触发两段式第一段后执行）
- [x] T7 缓存策略结论登记（FR-002-015）：⬜ 未启用缓存——首产单次 25min 可接受；高频重建时再加 actions/cache（cargo registry/git/target），登记为后续项
- [x] T8 验证：TC-W01…W07（W05 ⬜ 随 Release）✅；分批提交
