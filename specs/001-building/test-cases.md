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
