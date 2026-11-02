#!/usr/bin/env node
/*
 * scripts/fetch-pnpm-cli-source.mjs —— 取构建输入（pnpm 官方源码，不改源）
 *
 * 计划输出目录（默认 .dist/src）：
 *   <out>/pnpm-src/            pnpm <ver> 源码 tarball 解压内容（pnpm 12+：Rust workspace）
 *
 * 源码不改；依赖经锁定文件钉版 + registry 拉取（CI 缓存），sha 记录在 lock。
 *
 * 用法（规划接口，M1/M3.5 实现）：
 *   node scripts/fetch-pnpm-cli-source.mjs --pnpm 12.0.0 --out .dist/src
 *
 * 状态：000-scaffold 只固化接口与纪律；下载/解压/校验逻辑在 M3.5 前落地。
 *
 * 退出码：0 成功 · 2 SCAFFOLD 未实现 · 1 用法/IO/下载错误
 */

import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
function argOf(name, fallback = null) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
}

function usage() {
  process.stderr.write(
    [
      '用法: node scripts/fetch-pnpm-cli-source.mjs --pnpm <pnpm 版本> [--out <dir>]',
      '  --pnpm  pnpm 上游版本（= CLI_VERSION）',
      '  --out   输出目录（默认 .dist/src）',
    ].join('\n') + '\n',
  );
}

const pnpmver = argOf('--pnpm');
const out = argOf('--out') ?? path.join(ROOT, '.dist', 'src');

if (args.includes('--help') || !pnpmver) {
  usage();
  process.exit(pnpmver ? 0 : 1);
}

process.stderr.write(
  [
    `[fetch] SCAFFOLD: 下载/解压/SHA-256 校验逻辑在 M3.5 前实现（000-scaffold 只固化接口）。`,
    `[fetch] 计划输入: pnpm ${pnpmver} @ https://github.com/pnpm/pnpm`,
    `[fetch] 计划输出: ${out}/pnpm-src/`,
  ].join('\n') + '\n',
);
process.exit(2);
