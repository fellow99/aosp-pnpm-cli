# 001-building 任务拆解

> 对应：[spec.md](./spec.md)。原则：探针前置（C8 出口门禁）、fetch 先于 build、门禁先于移交。
> 顺序：T1→T2→T3（探针②依赖工具链）→T4→T5→T6→T7→T8；T0 为环境准备。

- [x] T0 环境准备：rustup stable + `aarch64-linux-android` target + cargo-ndk + cargo-license；NDK 28.2（对齐线核实）；Gradle 9.4.1（验证 000 门禁用）✅（Rust 1.97.0/1.99.1 @ D 盘、cargo-ndk 4.1.2、cargo-license 0.7.0；Gradle 9.4.1 由 CI 钉版使用）
- [x] T1 探针①：上游 android-arm64 分发核实（记录性），结论回写 `lock.openProbes` ① ✅（存在 @pnpm/exe.android-arm64；有也不用仍自建）
- [x] T2 工具链钉版登记：rustc/cargo/cargo-ndk/NDK 实值回写 `lock.builtFrom`（toolchain/ndk）✅（rustc 1.97.0 / cargo-ndk 4.1.2 / NDK 28.2.13676358）
- [x] T3 探针②：Rust 依赖 `aarch64-linux-android` 试编 → 结论回写 `lock.openProbes` ② ✅（**全部可编**，17m42s，零裁剪；③ 前提确认成立未触发）
- [x] T4 fetch 实现 ✅（TC-B01/02/03 过；纯 Node tar 解压，Windows 兼容）
- [x] T5 build 实现 ✅（CI run 41568096296；cargo-ndk -P 语法、16KB link-arg、路径绝对化修复 ×2）
- [x] T6 门禁首产 ✅（七项全过；DT_NEEDED=[libc,libm,libdl]，libc++_shared 未出现）
- [x] T7 依赖盘点 ✅（粒度 a+c 定案：Cargo.lock 整文件 sha256 + LICENSES-cargo.txt 随归档）
- [x] T8 首产实测回写 ✅（size=47317088/sha256=e5378cfa… 已回写 lock；TC-B01…B08 全过；分批提交）
