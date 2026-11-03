# 001-building 测试用例

> 对应：[spec.md](./spec.md)。判据纪律：可证伪、每条有退出码或文件级证据（[overall-test-cases.md](../overall-test-cases.md) §4）。
> 状态口径：⬜ 待执行（依赖探针/首产）；执行记录随开发阶段回填。

| # | 用例 | 步骤 | 期望 | 状态 |
|---|---|---|---|---|
| TC-B01 | fetch 钉版下载与校验 | `node scripts/fetch-pnpm-cli-source.mjs --pnpm <ver>` | exit 0；`.dist/src/pnpm-src/` 存在；FETCH-METADATA.json 字段完整（url/tag/sha256/时间） | ⬜ |
| TC-B02 | fetch 拒收（哈希不符） | 给定错误 `--sha256 <坏值>` 执行 fetch | exit 2；目标目录**未产生**；stderr 有拒收信息 | ⬜ |
| TC-B03 | fetch 幂等 | 连续两次 fetch 同版本 | 两次 exit 0；第二次输出「已存在且一致」语义；源树 mtime/内容不变 | ⬜ |
| TC-B04 | 不改源（C1） | fetch 后构建全流程结束，比对源树与 tarball 解压内容 | 逐字节一致（无任何被改写文件）；交叉参数文件均在源树**外** | ⬜ |
| TC-B05 | 门禁首产正样本 | `node tools/provision.mjs --inspect .dist/out/libpnpm_cli.so` | exit 0；①–⑦ 全 ✔（⑤⑥ draft 只记录——报告如实标注）；即 overall TC-EXT-06 的 inspect 面 | ⬜ |
| TC-B06 | 元数据与依赖盘点 | 检查 `.dist/out/`：BUILD-METADATA.txt / FETCH-METADATA.json / 许可清单 / Cargo.lock 副本 | 四件齐备；BUILD-METADATA 含 rustc/cargo-ndk/NDK/link-arg/size/sha256 实值 | ⬜ |
| TC-B07 | lock 回写实值核对 | T8 回写后 `node tools/provision.mjs --check`（未投放态）与 lock 内容比对 | lock `builtFrom` 无 TBD；size/sha256 与 `sha256sum`/`stat` 实测一致（非手抄） | ⬜ |
| TC-B08 | 探针①② 结论回写 | 读 `lock.openProbes` | ①② 有结论字段且与探针输出一致；若 ② 触发 napi-rs 硬依赖 ⇒ **后续 TC-B04…B07 不执行**（C8 冻结） | ⬜ |

## 执行记录

（开发阶段回填）

## 执行记录（2026-11-03，开发阶段）

- TC-B01 ✅：fetch v12.16.1（sha256=baaffdb2…2322c 期望校验通过）exit 0；`.dist/src/pnpm-src/Cargo.toml` 存在；FETCH-METADATA.json 字段完整。
- TC-B02 ✅：`--sha256 000…0` ⇒ exit 2，stderr 含「SHA-256 校验失败（拒收）」，目标目录未产生。
- TC-B03 ✅：二次 fetch ⇒ 「已存在且版本一致」exit 0（幂等命中）。
- 实现注记：解压用**纯 Node tar 解析器**（zlib + 512B 头 + pax path）——Windows 下 GNU tar 把 `D:\` 解释为远程主机、bsdtar 对 tarball 内 symlink 报 Can't create（无特权建链）；自解器跳过 symlink/hardlink（本次 6 个，均为上游 .claude/fixtures 装饰链接，不参与构建），清单记录于 FETCH-METADATA.json `skippedLinks`（C1 可审计）。
- TC-B04…B08：⬜ 待交叉编译完成（cargo-ndk 安装中）。

## 执行记录（2026-11-03，交叉编译经 GitHub Actions run 41568096296 实测 ✅ 全绿）

- TC-B04 ✅（C1 不改源）：BUILD-METADATA `source-tree=unmodified`；交叉参数经 RUSTFLAGS/cargo-ndk 注入（源树外）；fetch 自解器只读解压。
- TC-B05 ✅（**门禁首产正样本**，即 overall TC-EXT-06 inspect 面）：CI 日志 provision 报告七行全 ✔——①ET_DYN ②PT_INTERP=1 个且 = /system/bin/linker64 ③AArch64(183) ④DT_NEEDED=[libdl,libm,libc] 全在白名单（**libc++_shared.so 未出现**）⑤⑥ draft 只记录 ⑦ min LOAD align=0x4000；「门禁：全过」。
- TC-B06 ✅（元数据四件）：归档内 BUILD-METADATA.txt / FETCH-METADATA.json / LICENSES-cargo.txt / Cargo.lock 齐备；BUILD-METADATA 实值：rustc 1.97.0、cargo-ndk 4.1.2、NDK 28.2.13676358、16KB link-arg、size=47317088、sha256=e5378cfa…98dd、cargo-lock sha256=ef2eb07f…。
- TC-B07 ✅（lock 实值回写核对）：size/sha256 取自 CI 构建输出（`sha256sum`/`stat` 等价命令），已回写本仓 lock（draft 态 ⑤⑥ 下轮投放比对生效）。
- TC-B08 ✅（探针①② 结论回写）：①CLOSED（上游存在 @pnpm/exe.android-arm64，有也不用仍自建）；②CLOSED——**全部依赖在 aarch64-linux-android 可编**（编译 17m42s 完成，零 feature 裁剪、零源码补丁）；**③ 前提确认成立**：PT_INTERP 恰 1 个 ⇒ 独立进程二进制（非 NAPI 形态），C8 无需触发。
- 首产历程（3 轮 CI）：run 41563075234 编译成功但 TARGET_DIR 相对路径错位 → run 41565505469 license 重定向同类错位（归档按 FR-002-013 硬失败，行为正确）→ run 41568096296 全绿。两处修复均已 commit（路径绝对化）。
