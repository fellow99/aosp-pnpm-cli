# 003-cli 任务拆解

> 对应：[spec.md](./spec.md)。原则：先载体后用例；红线用例（self-update）必跑；
> ⬜ 条件项（16KB、node 产物）缺环境时如实标注不硬凑（C7）。

- [ ] T0 测试载体搭建：adb 设备确认（真机优先，否则模拟器）→ push 产物 + chmod 755 + HOME/TMPDIR/XDG/bin 目录树 + `pnpm` symlink
- [ ] T1 基础行为用例（TC-C01…C04）：version/help/错误退出码/UTF-8 流分离
- [ ] T2 网络用例（TC-C05/12）：TLS install 纯 JS 包 + registry 透传；根证书行为记录（R6）
- [ ] T3 lifecycle 回程（TC-C06）：node 注入 PATH（aosp-node-cli 产物或等价 node ⬜）；缺席场景记录 L1 证据
- [ ] T4 store 用例（TC-C08/09）：方式 a/b 双案实测 → R5 定案结论回写规划 §7 + 本模块 plan
- [ ] T5 红线用例（TC-C10/14）：self-update 失败路径 + 平台二进制包行为记录
- [ ] T6 附加用例（TC-C07/11/13/16）：init/dlx/长输出/arm 翻译记录
- [ ] T7 16KB 条件用例（TC-C15）：有 16KB 设备则跑，无则 ⬜ 登记
- [ ] T8 汇总：测试报告（exit/输出/文件证据逐条）+ 结论回写（规划 §7 R5/R6、README 已知限制）；提交（父工程不提交）
