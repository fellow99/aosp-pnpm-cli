# aosp-pnpm-cli

[English](README.md) · [**中文**](README_zh.md)

**The Android `pnpm` command-line executable, packaged as an AAR** — pnpm 12+ is a
**Rust standalone binary**; this repo cross-compiles it from the official source into
`libpnpm_cli.so` (arm64-v8a, exec-form PIE). Launched from the terminal by node-pty via
`forkpty` + `execve`, providing a real `pnpm` command.

> **Runtime dependency (read first)**: package lifecycle scripts are JS, executed by node on
> `PATH` — consumers must also include [`aosp-node-cli`](https://github.com/fellow99/aosp-node-cli)
> and inject `PATH`. This AAR is not an island.

This repository contains no embedding API — only the reproducible build, ELF gates and
release pipeline for the native binary. Planning baseline:
[`docs/000-工程规划.md`](docs/000-工程规划.md); workspace-level rationale: the
node-bridge-workspace doc `docs/aosp+node+python+git运行方案.md` (§9.3 decision + open probes).

## Version: follows upstream pnpm (independent semver)

The artifact version = the pnpm upstream version. No pairing with other repos (but a
**runtime dependency** on node-cli). **AArch64 (arm64-v8a) only**. Discipline: always build
from source, never download upstream prebuilt binaries.

### Releases — Maven Central (`io.github.fellow99:pnpm-cli`)

| Version | Status | Link |
|---|---|---|
| 12.0.0 | ⬜ draft (after the first M3.5 build; the CI-pinned pnpm version prevails) | — |

## Pre-M3.5 exit probes (lock.openProbes)

1. Upstream android-arm64 prebuilt availability (esbuild/biome-style distribution: most
   likely absent) — CI self-builds regardless;
2. Rust deps (napi-rs? ring? tokio?) compilability probe on `aarch64-linux-android`;
3. **If the premise is falsified** (NAPI hybrid loaded by node): FREEZE this repo plan and
   re-evaluate as a pty-style node-paired artifact (NODE_MODULE_VERSION pairing + napi
   symbol resolution inside the node-cli process).

## Usage (consumers)

```kotlin
dependencies {
    val nodeVer = "26.13.0"
    runtimeOnly("io.github.fellow99:node-runtime:$nodeVer") // libc++_shared provider
    runtimeOnly("io.github.fellow99:node-cli:$nodeVer")     // [required] lifecycle scripts need node
    runtimeOnly("io.github.fellow99:pnpm-cli:12.0.0")       // independent version
}
```

Terminal side: with `PATH` pointing at `nativeLibraryDir`, `pnpm install` / `pnpm -v` work
directly.

## Exec-form packaging points

- **`useLegacyPackaging=true`** (must be physically extracted to `nativeLibraryDir`,
  otherwise `execve` ENOENT);
- PT_INTERP must be `/system/bin/linker64` (hard gate);
- DT_NEEDED whitelist (system libs + `libc++_shared.so`, supplied by node-runtime);
- 16KB page alignment.

## How to build (000-scaffold skeleton; real pipeline lands in M3.5)

```bash
node scripts/fetch-pnpm-cli-source.mjs --pnpm 12.0.0 --out .dist/src   # M3.5
bash scripts/build-pnpm-cli-android.sh --pnpm 12.0.0 --api 24 \
  --src .dist/src/pnpm-src --ndk "$ANDROID_NDK_HOME" --out build/out   # M3.5
node tools/provision.mjs --out pnpm-cli/src/main/jniLibs --from build/out/libpnpm_cli.so
node tools/provision.mjs --check
```

## Lock & verification

[`pnpm-cli.lock.json`](pnpm-cli.lock.json) (single source of truth, currently draft, with
openProbes decision record). Exit codes: `0` pass · `2` gate failure / not provisioned ·
`1` usage/IO error.

## License

[MIT](LICENSE). pnpm is MIT — see
[`pnpm-cli/THIRD-PARTY-NOTICES.md`](pnpm-cli/THIRD-PARTY-NOTICES.md).
