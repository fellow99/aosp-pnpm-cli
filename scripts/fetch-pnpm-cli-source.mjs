#!/usr/bin/env node
/*
 * scripts/fetch-pnpm-cli-source.mjs —— 取构建输入（pnpm 官方源码，不改源）
 *
 * 输出目录（默认 .dist/src）：
 *   <out>/pnpm-src/            pnpm <ver> 源码 tarball 解压内容（pnpm 12+：Rust workspace）
 *   <out>/FETCH-METADATA.json  源 URL / tag / 版本 / sha256 / 时间 / 跳过清单（随产物归档）
 *
 * 纪律（宪法 C1）：本脚本只做「下载 → SHA-256 校验 → 解压」，不改写任何源码文件；
 *   交叉编译参数经 cargo 配置/环境注入（源树外）。期望哈希来源（优先级）：
 *   1) --sha256 参数；2) lock releases[].builtFrom.sourceSha256。
 *   两者皆无 ⇒ 首次定版模式（醒目警告，成功后打印实测哈希供回写 lock；CI 必须传期望值）。
 *
 * 解压实现：纯 Node（zlib + 512 字节 tar 头解析），不用系统 tar——
 *   Windows 下 GNU tar 把 "D:\..." 解释为远程主机、bsdtar 对 tarball 内 symlink
 *   报 Can't create（Windows 无特权建 symlink）。自解器跳过 symlink/hardlink
 *   （上游 .claude/、fixtures 的装饰性链接，不参与 cargo build），跳过清单
 *   记录进 FETCH-METADATA.json 的 skippedLinks，保持 C1 可审计。
 *
 * 用法：
 *   node scripts/fetch-pnpm-cli-source.mjs --pnpm 12.16.1 [--sha256 <hex>] [--out .dist/src]
 *
 * 退出码：0 成功（含幂等命中） · 2 校验失败/产物不可用（拒收，不产生目标目录） · 1 用法/IO 错误
 */

import path from 'node:path';
import fs from 'node:fs';
import crypto from 'node:crypto';
import zlib from 'node:zlib';
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
      '用法: node scripts/fetch-pnpm-cli-source.mjs --pnpm <pnpm 版本> [--sha256 <hex>] [--out <dir>]',
      '  --pnpm    pnpm 上游版本（= CLI_VERSION，如 12.16.1）',
      '  --sha256  源 tarball 期望 SHA-256（缺省读 lock builtFrom.sourceSha256；皆无=首次定版模式）',
      '  --out     输出目录（默认 .dist/src）',
    ].join('\n') + '\n',
  );
}

function die(code, msgs) {
  for (const m of msgs) process.stderr.write(m + '\n');
  process.exit(code);
}

function readLockSourceSha(ver) {
  try {
    const lock = JSON.parse(fs.readFileSync(path.join(ROOT, 'pnpm-cli.lock.json'), 'utf8'));
    const rel = (lock.releases || []).find((r) => r.version === ver);
    const sha = rel?.builtFrom?.sourceSha256;
    return typeof sha === 'string' && /^[0-9a-f]{64}$/.test(sha) ? sha : null;
  } catch {
    return null;
  }
}

async function download(url, dest) {
  const res = await fetch(url, { redirect: 'follow' });
  if (!res.ok || !res.body) die(2, [`[fetch] 下载失败: HTTP ${res.status} ${url}`]);
  const hash = crypto.createHash('sha256');
  const out = fs.createWriteStream(dest);
  for await (const chunk of res.body) {
    hash.update(chunk);
    out.write(chunk);
  }
  await new Promise((resolve, reject) => { out.end(resolve); out.on('error', reject); });
  return hash.digest('hex');
}

/* ---------- 纯 Node tar.gz 解压（ustar + pax path 覆盖；symlink/hardlink 跳过） ---------- */

function parseTarHeader(buf) {
  const str = (off, len) => buf.toString('utf8', off, off + len).replace(/\0+$/, '');
  const oct = (off, len) => parseInt(buf.toString('ascii', off, off + len).replace(/[\0 ]+$/, ''), 8) || 0;
  let name = str(0, 100);
  const size = oct(124, 12);
  const typeflag = String.fromCharCode(buf[156] || 0x30);
  const prefix = str(345, 155);
  if (prefix) name = `${prefix}/${name}`;
  return { name, size, typeflag };
}

async function extractTarGz(tarGzPath, destDir) {
  const skipped = [];
  const gunzip = zlib.createGunzip();
  fs.createReadStream(tarGzPath).pipe(gunzip);
  const HEADER = 512;
  let buf = Buffer.alloc(0);
  let pending = null; // { name, size, typeflag, consumed }
  let paxPath = null;
  let stripPrefix = null;

  const resolveName = (raw) => {
    let name = raw;
    if (stripPrefix === null) {
      // 第一个条目决定顶层目录前缀（GitHub tarball 为 pnpm-<ver>/）
      const slash = name.indexOf('/');
      stripPrefix = slash >= 0 ? name.slice(0, slash + 1) : '';
    }
    if (stripPrefix && name.startsWith(stripPrefix)) name = name.slice(stripPrefix.length);
    return name;
  };

  const consume = (head) => {
    // 进入一条目：分类处理；数据区由外层按 size 消费
    const type = head.typeflag;
    if (type === 'g') return 'skip'; // pax global header
    if (type === 'x') return 'pax';
    if (type === '5') { // 目录
      fs.mkdirSync(path.join(destDir, resolveName(head.name)), { recursive: true });
      return 'done';
    }
    if (type === '0' || type === '\0' || type === '7') return 'file';
    skipped.push(`${type}:${head.name}`); // '1' hardlink / '2' symlink / 其他
    return 'skip';
  };

  for await (const chunk of gunzip) {
    buf = pending && pending.buf ? Buffer.concat([pending.buf, chunk]) : Buffer.concat([buf, chunk]);
    for (;;) {
      if (pending && pending.type === 'file') {
        if (buf.length < pending.size) break;
        const name = resolveName(paxPath ?? pending.name);
        const target = path.join(destDir, name);
        if (!target.startsWith(destDir + path.sep)) throw new Error(`tar 路径逃逸: ${name}`);
        fs.mkdirSync(path.dirname(target), { recursive: true });
        fs.writeFileSync(target, buf.subarray(0, pending.size));
        paxPath = null;
        buf = buf.subarray(pending.size);
        const pad = (512 - (pending.size % 512)) % 512;
        if (buf.length < pad) { pending = { type: 'pad-only', size: 0, buf: buf.subarray(0, 0), need: pad, rest: buf }; break; }
        buf = buf.subarray(pad);
        pending = null;
      } else if (pending && pending.type === 'pax') {
        if (buf.length < pending.size) break;
        const body = buf.subarray(0, pending.size).toString('utf8');
        const m = body.match(/^\d+ path=([^\n]*)\n/m);
        if (m) paxPath = m[1];
        buf = buf.subarray(pending.size);
        const pad = (512 - (pending.size % 512)) % 512;
        if (buf.length < pad) { pending = { type: 'pad-only', need: pad, rest: buf }; break; }
        buf = buf.subarray(pad);
        pending = null;
      } else if (pending && pending.type === 'pad-only') {
        if (buf.length < pending.need) break;
        buf = buf.subarray(pending.need);
        pending = null;
      }
      if (buf.length < HEADER) break;
      if (buf[0] === 0) { buf = Buffer.alloc(0); break; } // 结尾全零块
      const head = parseTarHeader(buf.subarray(0, HEADER));
      buf = buf.subarray(HEADER);
      const kind = consume(head);
      if (kind === 'file') pending = { type: 'file', name: head.name, size: head.size };
      else if (kind === 'pax') pending = { type: 'pax', name: head.name, size: head.size };
      else if (kind === 'skip') {
        // 消费其数据区（symlink 头后无数据块，但 x/g 有；'1'/'2' 无数据）
        if (head.size > 0) {
          const pad = (512 - (head.size % 512)) % 512;
          const total = head.size + pad;
          if (buf.length < total) { pending = { type: 'pad-only', need: total - buf.length, skip: head.size, rest: buf }; buf = Buffer.alloc(0); break; }
          buf = buf.subarray(total);
        }
      }
      // kind === 'done'（目录）：无数据区，继续
    }
  }
  if (pending && pending.type === 'file') throw new Error(`tar 数据不完整（条目 ${pending.name}）`);
  return { skipped };
}

/* ---------- 主流程 ---------- */

const pnpmver = argOf('--pnpm');
const out = argOf('--out') ?? path.join(ROOT, '.dist', 'src');
if (args.includes('--help') || args.includes('-h')) { usage(); process.exit(0); }
if (!pnpmver) { usage(); process.exit(1); }
if (!/^\d+\.\d+\.\d+(-[\w.]+)?$/.test(pnpmver)) die(1, [`[fetch] 版本格式非法: ${pnpmver}`]);

const tag = `v${pnpmver}`;
const url = `https://codeload.github.com/pnpm/pnpm/tar.gz/refs/tags/${tag}`;
const srcDir = path.join(out, 'pnpm-src');
const metaPath = path.join(out, 'FETCH-METADATA.json');
const expectSha = argOf('--sha256') ?? readLockSourceSha(pnpmver);

// 幂等：已解压 + 元数据版本一致 ⇒ 直接成功（重复 fetch 无副作用）
if (fs.existsSync(srcDir) && fs.existsSync(metaPath)) {
  try {
    const meta = JSON.parse(fs.readFileSync(metaPath, 'utf8'));
    if (meta.version === pnpmver && meta.sha256 && fs.existsSync(path.join(srcDir, 'Cargo.toml'))) {
      const note = expectSha && meta.sha256 !== expectSha
        ? `（警告：实测 ${meta.sha256} ≠ 期望 ${expectSha}，如需强制重取请删除 ${srcDir}）`
        : '';
      console.log(`[fetch] 已存在且版本一致: pnpm ${pnpmver} @ ${srcDir} ${note}`);
      process.exit(0);
    }
  } catch { /* 元数据损坏则重取 */ }
}

fs.mkdirSync(out, { recursive: true });
const tmpTar = path.join(out, `.pnpm-${pnpmver}.tar.gz.tmp`);
console.log(`[fetch] 下载: ${url}`);
let actualSha;
try {
  actualSha = await download(url, tmpTar);
} catch (e) {
  die(2, [`[fetch] 下载异常（拒收）: ${e.message}`]);
}
const size = fs.statSync(tmpTar).size;
console.log(`[fetch] tarball: size=${size} sha256=${actualSha}`);

if (expectSha && actualSha !== expectSha.toLowerCase()) {
  fs.rmSync(tmpTar, { force: true });
  die(2, [
    '[fetch] SHA-256 校验失败（拒收，不产生目标目录）:',
    `[fetch]   期望: ${expectSha}`,
    `[fetch]   实测: ${actualSha}`,
  ]);
}
if (!expectSha) {
  console.log('[fetch] ⚠ 首次定版模式：无期望哈希，本次实测即基准。');
  console.log(`[fetch] ⚠ 请回写 pnpm-cli.lock.json: releases[version=${pnpmver}].builtFrom.sourceSha256 = "${actualSha}"（CI 必须带期望值运行）`);
}

// 解压（纯 Node；顶层目录 pnpm-<ver>/ 剥离）
fs.rmSync(srcDir, { recursive: true, force: true });
fs.mkdirSync(srcDir, { recursive: true });
let skipped = [];
try {
  const result = await extractTarGz(tmpTar, srcDir);
  skipped = result.skipped;
} catch (e) {
  fs.rmSync(tmpTar, { force: true });
  fs.rmSync(srcDir, { recursive: true, force: true });
  die(2, [`[fetch] 解压失败: ${e.message}（目标目录已清理）`]);
}
fs.rmSync(tmpTar, { force: true });

// 完整性自检：Rust workspace 根标志
if (!fs.existsSync(path.join(srcDir, 'Cargo.toml'))) {
  die(2, [`[fetch] 解压后缺失 Cargo.toml（非 Rust workspace？），目标目录保留供排查: ${srcDir}`]);
}
if (skipped.length > 0) {
  console.log(`[fetch] 跳过 ${skipped.length} 个链接/特殊条目（symlink/hardlink，Windows 不建链；不参与构建，清单见元数据）`);
}

const meta = {
  source: 'github.com/pnpm/pnpm',
  tag,
  version: pnpmver,
  url,
  sha256: actualSha,
  size,
  fetchedAt: new Date().toISOString(),
  unmodified: true,
  skippedLinks: skipped,
};
fs.writeFileSync(metaPath, JSON.stringify(meta, null, 2) + '\n');
console.log(`[fetch] 完成: ${srcDir}（源不改，C1）`);
console.log(`[fetch] 元数据: ${metaPath}`);
