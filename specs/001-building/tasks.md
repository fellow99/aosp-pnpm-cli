# 001-building 任务拆解

> 对应：[spec.md](./spec.md)。原则：探针前置（C8 出口门禁）、fetch 先于 build、门禁先于移交。
> 顺序：T1→T2→T3（探针②依赖工具链）→T4→T5→T6→T7→T8；T0 为环境准备。

- [ ] T0 环境准备：rustup stable + `aarch64-linux-android` target + cargo-ndk + cargo-license；NDK 28.2（对齐线核实）；Gradle 9.4.1（验证 000 门禁用）
- [ ] T1 探针①：上游 android-arm64 分发核实（记录性），结论回写 `lock.openProbes` ①
- [ ] T2 工具链钉版登记：rustc/cargo/cargo-ndk/NDK 实值回写 `lock.builtFrom`（toolchain/ndk）
- [ ] T3 探针②：Rust 依赖 `aarch64-linux-android` 试编 → 可编/不可编/裁剪清单回写 `lock.openProbes` ②；**若 napi-rs 硬依赖 ⇒ 走 C8 冻结，终止后续任务**
- [ ] T4 fetch 实现：`fetch-pnpm-cli-source.mjs`（钉版 + sha256 校验拒收 + 幂等 + FETCH-METADATA.json）；版本钉选回写 lock
- [ ] T5 build 实现：`build-pnpm-cli-android.sh`（cargo-ndk + 16KB link-arg + `--target-dir` 源树外 + llvm-strip + 命名 `libpnpm_cli.so` + BUILD-METADATA.txt）
- [ ] T6 门禁首产：`provision --inspect` 七项全过（draft 态 ⑤⑥ 只记录）；DT_NEEDED 清单核对（FR-001-024）
- [ ] T7 依赖盘点：cargo-license 清单 + Cargo.lock 副本 + 整文件 sha256（粒度 a+c 定案回写规划 §4.2）
- [ ] T8 首产实测回写：产物 size/sha256 命令取值回写 lock；TC-B01…B07 全过；提交（父工程不提交）
