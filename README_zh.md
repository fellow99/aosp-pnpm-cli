# aosp-pnpm-cli

[English](README.md) · [**中文**](README_zh.md)

**以 AAR 形式发布的、Android 版 `pnpm` 命令行可执行**——pnpm 12+ 已是 **Rust 独立二进制**，
本仓从官方源码 Rust 交叉编译出 `libpnpm_cli.so`（arm64-v8a，exec 型 PIE）。终端里由
node-pty 经 `forkpty` + `execve` 启动，提供真正的 `pnpm` 命令。

> **运行期依赖（必读）**：package lifecycle scripts 是 JS，由 PATH 上的 node 执行——
> 消费方必须同时引入 [`aosp-node-cli`](https://github.com/fellow99/aosp-node-cli)
> 并注入 `PATH`。本 AAR 不是孤岛。

本仓库不含任何嵌入 API——只有原生二进制的可复现构建、ELF 门禁与发布流水线。
规划基线见 [`docs/000-工程规划.md`](docs/000-工程规划.md)；工作区级方案背景见
node-bridge-workspace 的 `docs/aosp+node+python+git运行方案.md`（§9.3 决策与遗留探针）。

## 版本：跟随 pnpm 上游（独立 semver）

制品版本 = pnpm 上游版本。与其它仓无配对关系（但对 node-cli 有**运行期依赖**）。
**只发布 AArch64（arm64-v8a）**。纪律：一律源码自建，不下载上游预编译二进制。

### 发布版本 —— Maven Central（`io.github.fellow99:pnpm-cli`）

| 版本 | 状态 | 链接 |
|---|---|---|
| 12.0.0 | ⬜ draft（M3.5 首产后发布；以 CI 实际钉住的 pnpm 版本为准） | — |

## M3.5 出口前置探针（lock.openProbes）

1. 上游 android-arm64 平台二进制可得性（esbuild/biome 式分发大概率没有）——无论如何 CI 自建；
2. Rust 依赖（napi-rs？ring？tokio？）在 `aarch64-linux-android` 下的可编性试编；
3. **若前提被推翻**（实为 NAPI 混合形态、由 node 加载）：冻结本仓计划并按 pty 式伴生件
   重新评审（NODE_MODULE_VERSION 配对 + node-cli 进程内 napi 符号解析问题）。

## 用法（消费方）

```kotlin
dependencies {
    val nodeVer = "26.13.0"
    runtimeOnly("io.github.fellow99:node-runtime:$nodeVer") // libc++_shared 供给方
    runtimeOnly("io.github.fellow99:node-cli:$nodeVer")     // 【必需】lifecycle scripts 需要 node
    runtimeOnly("io.github.fellow99:pnpm-cli:12.0.0")       // 独立版本
}
```

终端侧：`PATH` 指向 `nativeLibraryDir` 后 `pnpm install` / `pnpm -v` 即可用。

## exec 型打包要点

- **`useLegacyPackaging=true`**（必须物理落盘 `nativeLibraryDir`，否则 `execve` ENOENT）；
- PT_INTERP 必须为 `/system/bin/linker64`（门禁强制）；
- DT_NEEDED 白名单（系统库 + `libc++_shared.so`，后者由 node-runtime 供给）；
- 16KB 页对齐。

## 如何构建（000-scaffold 骨架；M3.5 接入真实交叉编译）

```bash
node scripts/fetch-pnpm-cli-source.mjs --pnpm 12.0.0 --out .dist/src   # M3.5
bash scripts/build-pnpm-cli-android.sh --pnpm 12.0.0 --api 24 \
  --src .dist/src/pnpm-src --ndk "$ANDROID_NDK_HOME" --out build/out   # M3.5
node tools/provision.mjs --out pnpm-cli/src/main/jniLibs --from build/out/libpnpm_cli.so
node tools/provision.mjs --check
```

## 锁定与校验

[`pnpm-cli.lock.json`](pnpm-cli.lock.json)（唯一事实源，当前 draft，含 openProbes 决策记录）。
退出码：`0` 全过 · `2` 门禁不合格/未投放 · `1` 用法/IO 错误。

## 许可

[MIT](LICENSE)。pnpm 为 MIT——见
[`pnpm-cli/THIRD-PARTY-NOTICES.md`](pnpm-cli/THIRD-PARTY-NOTICES.md)。
