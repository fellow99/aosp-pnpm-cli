# 003-cli 测试用例（CLI 主要用例全集）

> 对应：[spec.md](./spec.md)。**本文件是 CLI 主测试用例的权威清单**——测试阶段据此编制
> TEST_CHECKLIST 并逐条执行；探针 P1–P7 映射见 [plan.md](./plan.md) §3。
> 环境口径（[plan.md](./plan.md) §1/§2）：`PNPM=/data/local/tmp/pnpm-cli/libpnpm_cli.so`；
> HOME/TMPDIR/XDG_DATA_HOME 注入；node 条件注入（P2）。
> 判据纪律：每条可证伪（exit code / stdout 内容 / 文件存在性 / sha256）；「预期失败」与
> 「意外失败」必须区分（红线用例**失败才通过**）。未跑标 ⬜，绝不标 ✅（C7）。

## 0. 载体与环境（前置）

| # | 用例 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|
| TC-C00 | 载体就绪 | `adb devices`（真机优先，无则模拟器）→ push `libpnpm_cli.so` → chmod 755 → 目录树（home/tmp/xdg/bin）+ `pnpm` symlink → `adb shell ls -l` 核对 | 产物在位、可执行位设置、目录树齐全 | ⬜ |
| TC-C00b | exec 权限语义 | `adb shell /data/local/tmp/pnpm-cli/libpnpm_cli.so --version`（无 env 注入裸跑） | 能 exec（shell 域允许）；即便因 env 缺失行为异常，也不得出现 ENOENT/Permission denied（区别 exec 型与 dlopen 型的实证） | ⬜ |

## 1. 基础行为（无网络）

| # | 用例 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|
| TC-C01 | 版本回程（**P1**） | `$PNPM --version` 与 `$PNPM -v` | 两命令 stdout 输出同一 semver 且 = lock `CLI_VERSION`；exit=0 | ⬜ |
| TC-C02 | 帮助 | `$PNPM --help` | stdout 非空（用法文本）；exit=0 | ⬜ |
| TC-C03 | 未知子命令 | `$PNPM definitely-not-a-command` | exit≠0；诊断信息出现（stderr 或 stdout，如实记录流向） | ⬜ |
| TC-C04 | UTF-8 输出完整性 | 在含中文字段的项目目录跑 `$PNPM install --reporter=append-only` 类长输出命令 | 中文/emoji 不乱码（输出与 registry 元数据一致）；无截断 | ⬜ |

## 2. 网络与安装（**P7**/P2）

| # | 用例 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|
| TC-C05 | TLS install 纯 JS 包（**P7**） | 临时目录 `package.json`（或 `pnpm init`）→ `$PNPM add left-pad`（或 `is-even`） | `node_modules/left-pad` 存在；exit=0；**TLS 握手成功**（无证书错误）——根证书来源结论记录（R6：rustls 自带 vs 需 cafile） | ⬜ |
| TC-C06 | lifecycle 完整回程（**P2**） | ① PATH 上有 `node`：`$PNPM add <含 install/postinstall 脚本的纯 JS 包>`；② PATH 无 `node`：同命令 | ① 脚本执行痕迹（日志）+ 无 node-not-found + exit=0（pnpm→sh→node→script 全链）；② **预期失败**（L1 契约实证），错误信息如实记录 | ⬜ |
| TC-C07 | init 生成清单 | 临时目录 `$PNPM init` | `package.json` 生成且字段合法；exit=0 | ⬜ |
| TC-C11 | dlx 行为（**P4**） | `$PNPM dlx cowsay hi`（纯 JS 工具） | 输出与 exit 如实记录（记录性，不作出口判据） | ⬜ |
| TC-C12 | registry 透传 | `npm_config_registry=<镜像> $PNPM add <pkg>` + 日志核对 | 镜像地址被采纳（请求日志/行为可证） | ⬜ |

## 3. store 与目录契约（**P3**）

| # | 用例 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|
| TC-C08 | store 落点双案（**P3**） | 案 a：`XDG_DATA_HOME=…/xdg $PNPM store path`；案 b：`$PNPM config set store-dir /data/local/tmp/pnpm-cli/store` 后 `$PNPM store path` | 两案落点均以注入前缀开头（filesDir 语义）；**绝不**落 nativeLibraryDir 或系统只读区；a/b 优劣结论回写规划 §7（R5 定案） | ⬜ |
| TC-C09 | HOME 基座 | `$PNPM config set foo bar` → `ls $HOME/.config/pnpm/`（或等价 rc 路径） | 配置文件落注入 HOME 派生路径；`$PNPM config get foo` 回读 = bar | ⬜ |

## 4. 红线与边界（**P5**/L3/L4）

| # | 用例 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|
| TC-C10 | self-update 必须失败（**P5**） | `sha256sum $PNPM`（前）→ `$PNPM self-update`（捕获 exit/输出）→ `sha256sum $PNPM`（后） | exit≠0（**失败才通过**）；前后 sha256 一致（无部分写入）；输出如实记录（回写消费方文档） | ⬜ |
| TC-C14 | 平台二进制包行为（L3） | `$PNPM add esbuild`（esbuild 式 optionalDependencies 平台二进制包） | 行为如实记录：明确失败/降级报错皆可，**禁止静默装成功**；结论供 §7 已知限制 3 的口径评估（R10） | ⬜ |
| TC-C15 | 16KB 页设备（**P6**） | 16KB 页模拟器/真机上复跑 TC-C01 | exec 正常、版本回程（C4 硬要求）；**无 16KB 设备 ⇒ ⬜ 登记**（C7，不硬凑） | ⬜ |
| TC-C16 | arm 翻译记录（x86_64 模拟器） | 全部用例即在本载体跑 | 总体行为记录：arm64 产物经翻译执行的可用面与差异（启动耗时/兼容性），仅记录性 | ⬜ |

## 5. 输出流与稳定性

| # | 用例 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|
| TC-C13 | stdout/stderr 分离 | `$PNPM --version 1>/out.txt 2>/err.txt`（成功）与 `$PNPM bad-cmd 1>/o 2>/e`（失败） | 成功例：out 有内容、err 空；失败例：诊断流与成功流不混淆（如实记录 pnpm 实际流向） | ⬜ |

## 执行记录

（测试阶段回填：每条用例记录 exit code / 关键输出摘录 / 文件证据路径；预期失败用例注明
「✅ 预期失败」；未执行条件项保持 ⬜ 并注明缺件（如 16KB 设备、node 产物））
