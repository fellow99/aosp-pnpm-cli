# THIRD-PARTY-NOTICES — aosp-pnpm-cli

本 AAR 随包分发的第三方组件与许可：

- **pnpm**（`libpnpm_cli.so` 本体，pnpm 12+ Rust 独立二进制）—— MIT License：
  https://github.com/pnpm/pnpm/blob/main/LICENSE
- **Rust 标准库 / 工具链产物**—— MIT OR Apache-2.0
- 主要 Rust 依赖（tokio、ring?、reqwest? 等，以 M1 依赖可编性探针结论为准）——各自许可，
  完整清单（cargo license 报告）随 GitHub Release 归档分发
- **node**（运行期依赖，不随包于本 AAR）——由 `io.github.fellow99:node-cli` 供给，MIT

纪律：

- 二进制不入库，由 CI 从官方 pnpm 源码构建（sha256 校验，源码不改；
  交叉编译参数经 cargo 配置注入）；
- 不下载上游预编译二进制（C2 纪律）；
- 完整许可全文随 GitHub Release 归档分发；
- 本仓库自身代码适用仓库根 [LICENSE](../LICENSE)（MIT）。
