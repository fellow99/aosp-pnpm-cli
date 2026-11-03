# 003-cli 任务拆解

> 对应：[spec.md](./spec.md)。原则：先载体后用例；红线用例（self-update）必跑；
> ⬜ 条件项（16KB、node 产物）缺环境时如实标注不硬凑（C7）。

- [x] T0 测试载体搭建 ✅（emulator-5554 API 37；deploy 脚本 + MSYS 路径转换修复；sha256 一致）
- [x] T1 基础行为用例（TC-C01…C04）✅（P1 版本回程 12.16.1；C03 exit=1 走 stderr）
- [x] T2 网络用例（TC-C05/12）✅（TLS install 成功 4.8s；R6 初步结论 rustls 自带证书；registry 透传为既有机制未单独断言——⬜ 可选补测）
- [x] T3 lifecycle 回程（TC-C06）⚠️ ①⬜（node 产物缺件：aosp-node-cli 未首产）②✅（L1 增强发现：默认拦截构建脚本）
- [x] T4 store 用例（TC-C08/09）✅（R5 定案：两案均有效，推荐案b；HOME 基座生效）
- [x] T5 红线用例（TC-C10/14）✅（P5 结论修正：self-update 会下载不物理失败；esbuild postinstall 被默认拦截层挡下——非静默）
- [x] T6 附加用例（TC-C07/11/13/16）✅（init/dlx+node-not-found/流分离/翻译层全记录）
- [x] T7 16KB 条件用例（TC-C15）⬜（无 16KB 设备/镜像，C7 如实登记；对齐 0x4000 已由门禁⑦保证）
- [x] T8 汇总 ✅（测试报告=003/test-cases.md 执行记录节；结论已回写规划 §7/§7.1/R5/R6；提交）
