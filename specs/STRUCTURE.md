# STRUCTURE.md —— `aosp-pnpm-cli` 目录与文件结构（as-built）

> **性质**：项目级结构规格（现状快照 + 规划落点）。只描述「文件在哪、干什么、进不进 git/包」。
> **依据**：[`constitution.md`](./constitution.md)（C1–C9 / N1–N5）· [`../docs/000-工程规划.md`](../docs/000-工程规划.md)（唯一权威，下称「规划 §」）· 本仓库工作区实际文件清单 ✅实测（2026-11-02）。
> **标记**：✅实测（本仓可复核）/ ⬜规划未实现（引用时标注规划 §）/ ⚠️冲突或风险。
> **一句话边界**：`pnpm-cli/` 是**唯一发布物**（纯二进制 AAR 容器，D8）；`tools/` `scripts/` `.github/` `specs/` `docs/` 是**配套物**，彼此不反向依赖。

---

## 1. 顶层目录树（逐文件一行用途，行数 ✅实测）

```
aosp-pnpm-cli/
├── LICENSE                                # MIT（本仓自身）；pnpm 本体 MIT 另见 THIRD-PARTY-NOTICES
├── README.md                              # 英文总览：定位 / 运行期依赖必读 / 版本表 / openProbes / exec 打包要点 / 构建用法（81 行）
├── README_zh.md                           # 中文总览（73 行；与 README.md 同构）
├── docs/
│   └── 000-工程规划.md                     # ★ 唯一规划基线（v2.0，308 行）：D1–D8 决策 + M3.5 实现设计 + 里程碑
├── settings.gradle.kts                    # rootName=aosp-pnpm-cli、include(":pnpm-cli")、vanniktech 0.37.0 插件管理（21 行）
├── gradle.properties                      # GROUP / CLI_VERSION=12.0.0 draft / POM_* / RELEASE=false（19 行）
├── gradle.properties.local.example        # 用户级凭据模板（8 行；真值文件不入库）
├── gradle/
│   └── libs.versions.toml                 # AGP 9.2.1 版本目录（5 行）
├── publish.gradle.kts                     # vanniktech 发布面：coordinates/publishToMavenCentral(false)/签名/RELEASE 门禁（139 行）
├── pnpm-cli.lock.json                     # ★ 唯一事实源（draft；59 行；openProbes ①②③ 决策记录 + gates ①–⑦ + matrix 白名单）
├── pnpm-cli/                              # ★★ 唯一发布物模块：AAR 容器（无任何 Kotlin/Java 源）
│   ├── build.gradle.kts                   # android library + namespace/minSdk/compileSdk + abiFilters + useLegacyPackaging=true（46 行）
│   ├── THIRD-PARTY-NOTICES.md             # 第三方许可声明（18 行；⚠️ :8 仍有「M1 措辞」，见 §6）
│   ├── src/main/                          # 空目录树（jniLibs/resources 由 provision 投放时创建；.so 不入库）
│   └── build/outputs/aar/pnpm-cli-debug.aar   # 本地产物（不入库；✅ 实测 4 条目、无 jni/——未投放状态的容器骨架）
├── tools/
│   └── provision.mjs                      # ★ exec 门禁 + 投放（276 行；退出码 0/2/1；纯 Node 零第三方依赖）
├── scripts/
│   ├── fetch-pnpm-cli-source.mjs          # 骨架：固化接口，exit 2（53 行；下载/解压/校验 ⬜ M3.5，规划 §4.3 ②）
│   └── build-pnpm-cli-android.sh          # 骨架：固化接口 + 七条纪律注释，exit 2（59 行；cargo 链 ⬜ M3.5，规划 §4.3 ①–⑦）
├── .github/workflows/
│   └── build-pnpm-cli-android.yml         # scaffold 配置期 CI（50 行；真实流水线 ⬜ M3.5-T6 替换，规划 §9）
├── .gitignore                             # jniLibs/**/*.so、.dist/、build/out/、target/ 等（31 行）
└── specs/                                 # 规格体系（本目录，见 §5）
```

> **本仓无 Gradle Wrapper**：CI 用 `gradle/actions/setup-gradle@v4` 钉 `gradle-version: '9.4.1'`（workflow:29-32）；本地构建用 Gradle 9.4.1 跑过（`.gradle/9.4.1/` 缓存目录 ✅ + `specs/000-scaffold/test-cases.md` 执行记录）。⚠️ 开发机自备 9.4.x，其它版本未验证（AGP 9.2.1 有 Gradle 下限要求）。

---

## 2. 目录 ↔ 功能 ↔ FR 映射

| 目录 / 文件 | 功能 | FR（见 [000-scaffold/spec.md](./000-scaffold/spec.md)） | 现状 |
|---|---|---|---|
| `settings.gradle.kts` | root 名 + 单模块 include + 插件/仓库管理 | FR-000-001 | ✅ |
| `gradle/libs.versions.toml` | AGP 版本目录 | FR-000-002 | ✅ |
| `pnpm-cli/build.gradle.kts` | 模块形态（library/namespace/minSdk/compileSdk/ABI）+ exec 打包 | FR-000-002 / FR-000-004 | ✅ |
| `gradle.properties` + `publish.gradle.kts` | 坐标与版本源（CLI_VERSION）、发布面与卫生 | FR-000-003 / C6 | ✅ |
| `pnpm-cli.lock.json` | 版本/哈希/门禁/出处/openProbes | FR-000-010~013 | ✅（draft） |
| `tools/provision.mjs` | exec 门禁 + 幂等投放 + jniLibs 禁区 | FR-000-030~036 | ✅ |
| `.github/workflows/build-pnpm-cli-android.yml` | 配置期门禁 CI | FR-000-040~042 | ✅（⚠️ 退出码步骤缺陷，见 §6） |
| `scripts/fetch-pnpm-cli-source.mjs` | 源钉选获取（接口固化） | 规划 §4.3 ②（M3.5） | 骨架 exit 2 ✅ / 实现 ⬜ |
| `scripts/build-pnpm-cli-android.sh` | cargo 交叉编译 + strip（接口固化） | 规划 §4.3 ①–⑦（M3.5） | 骨架 exit 2 ✅ / 实现 ⬜ |
| `.gitignore` | 二进制/产物忽略 | C3 | ✅ |

---

## 3. 构建产物落点（as-built）

| 产物 | 落点 | 现状 | 说明 |
|---|---|---|---|
| AAR 容器 | `pnpm-cli/build/outputs/aar/pnpm-cli-{debug,release}.aar` | ✅ debug 已产出 | ✅ 实测 debug 包 4 条目：`R.txt`(0B) / `AndroidManifest.xml`(207B, `package=org.fellow99.pnpmcli`, minSdk 24) / `classes.jar`(22B) / `aar-metadata.properties`(157B, `minCompileSdk=36`)；**无 `jni/` 条目**（未投放状态的正确形态） |
| 投放目标 | `pnpm-cli/src/main/jniLibs/arm64-v8a/libpnpm_cli.so` | ⬜ 未投放（`provision --check` = exit 2「未投放」） | 由 `provision --out` 创建；`.gitignore:23` 忽略 |
| 版本元数据 | `pnpm-cli/src/main/resources/META-INF/pnpm-cli.properties` | ⬜ 投放时生成 | `tool.version/abi/name/size/sha256/provisionedAt`（`tools/provision.mjs:252-267`） |
| fetch 输出 | `.dist/src/pnpm-src/` | ⬜ M3.5（规划 §4.3 ②） | `.gitignore:26` |
| 构建输出 | `build/out/libpnpm_cli.so` | ⬜ M3.5（规划 §4.3 ⑤） | `.gitignore:27` |
| Rust cargo 输出 | `target/` | ⬜ M3.5 | `.gitignore:28` |

---

## 4. 「不入库」清单（C3）

| 对象 | 位置 | 机制 |
|---|---|---|
| `libpnpm_cli.so` | `pnpm-cli/src/main/jniLibs/**/*.so` | `.gitignore:23`；lock 只记 `size/sha256` |
| fetch / 构建 / cargo 工作区 | `.dist/` · `build/out/` · `target/` | `.gitignore:26-28` |
| Gradle/IDE 产物 | `.gradle/` · `build/` · `.kotlin/` · `*.apk` `*.aab` · `local.properties` | `.gitignore:2-15` |
| 签名/凭据 | `*.jks` `*.keystore`；Portal Token/GPG 只在用户级 `~/.gradle/gradle.properties` | `.gitignore:18-20` + `gradle.properties.local.example:1`（「用户级，不提交」）+ `publish.gradle.kts:13-14` |

---

## 5. `specs/`（本目录）文件清单

| 文件 | 性质 |
|---|---|
| [`constitution.md`](./constitution.md) | 项目宪法（C1–C9 + N1–N5 + 评审清单） |
| [`STRUCTURE.md`](./STRUCTURE.md) | 本文件 |
| [`TECH.md`](./TECH.md) | 技术选型（含已实现 vs 规划状态列） |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 整体架构（AAR 容器 + exec 制品 + 运行期组合 + 门禁层） |
| [`overall-spec.md`](./overall-spec.md) | 整体规格（技术无关，FR-000-xxx 沿用） |
| [`overall-plan.md`](./overall-plan.md) | 整体技术方案 |
| [`overall-test-cases.md`](./overall-test-cases.md) | 测试用例索引 |
| [`SPECS_CHECKLIST.md`](./SPECS_CHECKLIST.md) | 文档完成度追踪 |
| [`README.md`](./README.md) | 规格索引（最后生成，链接全验） |
| [`000-scaffold/spec.md`](./000-scaffold/spec.md) · [`000-scaffold/plan.md`](./000-scaffold/plan.md) · [`000-scaffold/tasks.md`](./000-scaffold/tasks.md) · [`000-scaffold/test-cases.md`](./000-scaffold/test-cases.md) | 模块 000-scaffold 四件套（spec/tasks/test-cases 为既有件，plan 为本批新增） |

> **刻意不生成** `API.md` / `overall-api.md` / `overall-data-model.md`：本仓**不含任何嵌入 API**（`README.md:14`「no embedding API」，AAR 无 Kotlin/Java 源、无对外接口面）；lock JSON 数据模型在 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §3 逐字段给出，不单立 data-model 文档。

---

## 6. 规划中存在、当前不存在 / 待回写的路径（防误引用）

| 路径 / 事项 | 规划锚点 | 现状 |
|---|---|---|
| `pnpm-cli/src/main/jniLibs/arm64-v8a/libpnpm_cli.so` | 规划 §5 | ⬜ 未投放（`--check` exit 2） |
| `pnpm-cli/src/main/resources/META-INF/pnpm-cli.properties` | 规划 §5 | ⬜ 投放时生成 |
| `.dist/` · `build/out/` · `target/` | 规划 §4.3 | ⬜ M3.5 |
| 真实交叉编译 CI（替换/扩展现 workflow） | 规划 §9 · M3.5-T6 | ⬜（现 workflow:6 头注释写「在 **M1** 接入」⚠️ 措辞滞后于规划 v2.0 的 M3.5 口径；规划 §4.1 已把两处「M1 措辞回写」列为 M3.5 顺手项） |
| `pnpm-cli/THIRD-PARTY-NOTICES.md:8` 的「M1 依赖可编性探针」 | 规划 §4.1 | ⚠️ 同上（应随 M3.5 回写为 M3.5 口径） |
| specs 模块级目录扩展（M3.5 及以后的新模块） | C9 | ⬜（当前仅 000-scaffold 一个模块） |

---

## 7. ⚠️ 结构层面发现（as-built 评审必读）

1. **`gradle.properties:3` 的 `VERSION_NAME=0.1.0` 是模板遗留**：坐标版本唯一取 `CLI_VERSION`（`publish.gradle.kts:36-39` 显式 `coordinates(..., versionName)` + `pnpm-cli/build.gradle.kts:17-27` 双保险反射覆写），`VERSION_NAME` 不参与坐标。两个版本属性并存易误读，建议 M3.5 收口时清理或注释（本批只登记，不改实现）。
2. **CI 退出码步骤的 shell 语义缺陷**：`.github/workflows/build-pnpm-cli-android.yml:37-45` 的意图是「`provision --check` 未投放时退出码 2 = 预期通过」，但 GitHub Actions Linux `run:` 默认 shell 是 `bash -e`——`node tools/provision.mjs --check` 以 2 退出时 step **立即失败**，`code=$?` 永不执行，「其它非零码视为缺陷」的区分逻辑不可达。✅ 本会话实测：`bash -e -c 'false; echo unreachable'` 确认中断。修法（M3.5-T6 重写 CI 时）：`code=0; node tools/provision.mjs --check || code=$?`。详见 [`000-scaffold/plan.md`](./000-scaffold/plan.md) §6/§10。
3. **`provision.mjs` 消费可选字段 `rel.pairedRuntime?.coordinates`（`:196,:263`）而 lock 当前无该字段**：as-built 行为是打印「无配对信息」且 META-INF 不写 `paired.runtime` 行——属同族仓（node-pty `pairedRuntime`）预留的兼容读取，lock 模型中登记为可选字段（[`000-scaffold/plan.md`](./000-scaffold/plan.md) §3.3）。
