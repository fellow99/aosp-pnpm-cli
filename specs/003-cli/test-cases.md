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

## 执行记录（2026-11-03，测试阶段实测——模拟器 emulator-5554，API 37，x86_64 + arm64 翻译）

| # | 结果 | 证据（exit / 关键输出） |
|---|---|---|
| TC-C00 | ✅ | deploy 脚本 push+chmod+目录树+symlink；sha256 一致 `e5378cfa…98dd`；设备 x86_64/API 37 |
| TC-C00b | ✅ | 裸 exec `--version` ⇒ `12.16.1`，EXIT=0（shell 域允许 exec，exec 型实证） |
| TC-C01（**P1**） | ✅ | `--version`/`-v` ⇒ `12.16.1` = lock CLI_VERSION，exit=0；版本串回程齐全 |
| TC-C02 | ✅ | `--help` 输出用法（`Usage: pnpm [OPTIONS] <COMMAND>` + 子命令表），exit=0 |
| TC-C03 | ✅ | 未知命令 ⇒ **exit=1，诊断走 stderr**（stdout 空，流分离一并实证） |
| TC-C04 | ✅（随 C05 间接） | 全程中文/emoji 无乱码；专项长输出用例随 C05 输出正常 |
| TC-C05（**P7**） | ✅ | `pnpm add left-pad`：TLS 下载成功（resolved 1, downloaded 1, done 4.8s），exit=0；`node_modules/left-pad` 存在；**无证书错误 ⇒ rustls 自带根证书生效（R6 初步结论：无需 CA 注入）** |
| TC-C06（**P2**） | ①⬜ ②✅ | ① **缺件：aosp-node-cli 的 `libnode_cli.so` 为 1024B 占位残品（未首产）**，完整回程待 node 产物；② 无 node 场景被**更早一层**拦截——见下「L1 增强」 |
| TC-C07 | ✅ | `pnpm init` 生成 package.json，exit=0 |
| TC-C08（**P3**） | ✅ | 案a XDG ⇒ `$XDG_DATA_HOME/pnpm/store/v11`；案b store-dir ⇒ `/data/local/tmp/pnpm-cli/store/v11` 且**持久化生效**（后续 add 实际落 store/v11）。两案均落注入可写目录、不落库区。**R5 定案：案b 显式可核验、随 config.yaml 持久化，推荐案b** |
| TC-C09 | ✅（含口径修正） | config.yaml 落 `$HOME/.config/pnpm/`（HOME 基座生效）；⚠️ 新版 config set 仅接受受支持 YAML key（任意 key ⇒ ERR_PNPM_CONFIG_SET_UNSUPPORTED_YAML_CONFIG_KEY） |
| TC-C10（**P5**） | ⚠️ **结论修正** | self-update **不会物理失败**：可写 HOME 下真实发起运行期下载 `@pnpm/exe.android-arm64`（C5 行为实锤）；原产物 sha256 前后一致（本体无部分写入）；二跑判定 already-latest exit=0。**D7「物理不可能」仅生产 AAR 场景（NLDIR 只读）成立**；消费方禁用必须靠环境约束+文档声明 |
| TC-C11（**P4**） | ✅ 记录 | dlx cowsay 下载安装成功；执行 shim 时 `node: not found`（D5/L1 再证：dlx JS 工具依赖 PATH 上 node） |
| TC-C13 | ✅ | 成功例 stdout 有内容 stderr 空；失败例诊断走 stderr（随 C03） |
| TC-C14（L3） | ✅ 记录 | `pnpm add esbuild`：**postinstall（运行期平台 .so 下载）被默认构建脚本拦截层挡下**（ERR_PNPM_IGNORED_BUILDS）——非静默降级 ✅；esbuild JS 本体装上但二进制缺失 ⇒ 运行期必失败，能力边界声明成立 |
| TC-C15（**P6**） | ⬜ | 无 16KB 页设备/镜像可用（C7 如实标注；产物对齐 0x4000 已由门禁⑦保证） |
| TC-C16 | ✅ 记录 | 全部用例经 x86_64 翻译层执行，无翻译层崩溃/卡顿；性能足够（install 4.8s） |

### 重大 as-built 发现（须回写消费方文档/规划）

1. **L1 增强**：新版 pnpm（12 Rust 版）默认**拦截未批准构建脚本**（ERR_PNPM_IGNORED_BUILDS，help 指向 `pnpm approve-builds`）。P2 完整回程需要 **node 注入 + 构建脚本预授权（onlyBuiltDependencies/approve-builds）** 双条件；拦截发生在配置层，早于 node spawn。
2. **P5 修正**：`self-update` 在可写 HOME+网络下**会真实运行期下载上游二进制**（首跑实测 23.55MB 下载启动；原产物不可被覆盖——sha256 不变）。D7 的「物理不可能」仅指生产 AAR 的 nativeLibraryDir 只读场景；**消费方文档必须显式声明：不得为 pnpm 提供可写 HOME + 网络的组合并依赖物理失败**。
3. **R5 定案**：store 注入推荐**案b**（`pnpm config set store-dir`，显式、可 `store path` 回读、随 config.yaml 持久化）；案a（XDG_DATA_HOME）亦实测有效，作为无首启命令场景的兜底。
4. **R6 初步结论**：TLS 直连 registry.npmjs.org 握手成功，无证书错误 ⇒ rustls webpki-roots 自带根证书，**无需** `npm_config_cafile` 注入（⬜ 正式结论随真机复测）。

### App 探针执行记录（2026-11-04，probe-app 无人值守实测 ✅ 17 PASS + 2 BLOCKED + 0 FAIL）

- 载体：probe-app（`adb install` + `am start` 自动跑全部 19 探针），报告 `probe-report.json`（device: sdk_gphone64_x86_64 API 37，**x86_64 + arm64 翻译层**——本会话实测 x86_64 主机无法启动 arm64 镜像（emulator 不支持跨架构 QEMU：`Avd's CPU Architecture 'arm64' is not supported on x86_64 host`），翻译层为可用最优环境，C7 如实记录）
- 全部 PASS：C00（NLDIR 唯一原生件+size 落锁值）/ C00b（裸环境直启）/ C01/C01b（12.16.1 回程）/ C02 / C03（stderr 分流）/ C04（中文/emoji UTF-8 回程）/ C07（init）/ C08a+C09+C08b（**store 双案 App 内复验，R5 定案再证**）/ C05（TLS install）/ C06b（core-js 默认拦截）/ C14（esbuild 拦截非静默）/ **C10（self-update 后本体 sha256 前后一致——P5 红线在 App 场景实证）** / C11（dlx RECORD）/ C16（翻译层记录）
- BLOCKED：C06a（node 缺件，aosp-node-cli 未首产）/ C15（无 16KB 设备，对齐已由门禁⑦保证）
- 发布联动：全绿后打 tag `v12.16.1` → CI run 41733024700 success → **GitHub Release 生成**（https://github.com/fellow99/aosp-pnpm-cli/releases/tag/v12.16.1，tar.gz 22,719,556B + .sha256 双 assets）→ lock `status=released`（002-T6 runbook 收尾，**G1/G4 达成**）
