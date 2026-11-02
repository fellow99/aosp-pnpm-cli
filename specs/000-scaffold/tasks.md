# 000-scaffold 任务拆解

> 对应：[spec.md](./spec.md)。原则：骨架完整、简单编译通过；真实交叉编译属 M3.5。

- [x] T1 Gradle 骨架（settings/toml/properties/local.example/publish/模块 build，
      **useLegacyPackaging=true**，CLI_VERSION=12.0.0 draft）
- [x] T2 `pnpm-cli.lock.json`（draft；openProbes 三项 + 运行期依赖登记）
- [x] T3 `tools/provision.mjs`（exec profile 同款；禁入 libnode.so；META-INF/pnpm-cli.properties）
- [x] T4 脚本骨架：`fetch-pnpm-cli-source.mjs` / `build-pnpm-cli-android.sh`（七条纪律注释）
- [x] T5 scaffold CI（配置期门禁 + 自检）
- [x] T6 `.gitignore`（含 target/）/ THIRD-PARTY / README 中英（运行期依赖醒目声明）/ docs/000
- [x] T7 specs（三件套）
- [x] T8 验证（assembleDebug + provision 退出码 + 语法门）+ 提交（父工程不提交）

## 移交登记（2026-11-03，规划 §10.2）

M3.5 工作已拆分至新模块，本目录任务表（T1–T8）保持闭合不再追加：

- → [001-building/tasks.md](../001-building/tasks.md)：探针①②③、fetch/交叉编译/strip 实现、门禁首产
- → [002-building-workflow/tasks.md](../002-building-workflow/tasks.md)：真实 CI 流水线、`bash -e` 缺陷修复、lock 回写
- → [003-cli/tasks.md](../003-cli/tasks.md)：真机回程 P1–P7、store 注入定案、测试计划
