#!/usr/bin/env node
/**
 * 令牌门禁（docs/DESIGN_SYSTEM.md §六 / REFACTOR_V2_PLAN P7）：
 * 业务 UI 层禁止直接读取 Material3 colorScheme——必须走 EmberTokens（EmberTheme.colors/.type）。
 * 基础层豁免：ui/design/**（M3 桥接与 EmberTheme 实现本体）、ui/icons/**。
 * 存量违规登记在 colorscheme-allowlist.json（ratchet）：只许减少不许新增，
 * 新文件/新行出现 colorScheme 直读且未登记 → 本脚本 exit 1，CI 红。
 */
import fs from 'node:fs';
import path from 'node:path';
import url from 'node:url';

const ROOT = path.join(path.dirname(url.fileURLToPath(import.meta.url)), '..', '..');
const UI = path.join(ROOT, 'app/src/main/java/com/emberinn/app/ui');
const SCAN_DIRS = ['chat', 'components', 'home', 'onboarding', 'sessions', 'settings'];
const ALLOWLIST_PATH = path.join(path.dirname(url.fileURLToPath(import.meta.url)), 'colorscheme-allowlist.json');

const RE = /MaterialTheme\.colorScheme\s*\.\s*\w+/g;

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt')) out.push(p);
  }
  return out;
}

const allowlist = new Set(JSON.parse(fs.readFileSync(ALLOWLIST_PATH, 'utf8')));
// 允许从名单中自然淘汰：扫描后同步收缩名单（删掉已修复的条目），保持 ratchet 单调
const stillViolating = new Set();

let fresh = [];
for (const dir of SCAN_DIRS) {
  for (const file of walk(path.join(UI, dir))) {
    const rel = path.relative(ROOT, file).split(path.sep).join('/');
    const lines = fs.readFileSync(file, 'utf8').split('\n');
    let hasHit = false;
    lines.forEach((line, i) => {
      if (RE.test(line)) {
        hasHit = true;
        if (!allowlist.has(rel)) {
          fresh.push(`${rel}:${i + 1}: ${line.trim().slice(0, 100)}`);
        }
        RE.lastIndex = 0;
      }
    });
    if (hasHit) stillViolating.add(rel);
  }
}

if (fresh.length > 0) {
  console.error('✗ 新增 MaterialTheme.colorScheme 直读（业务层禁令，DESIGN_SYSTEM §六）：');
  for (const f of fresh) console.error('  ' + f);
  console.error(`\n如确属基础层豁免范围请调整 SCAN_DIRS；否则改用 EmberTheme.colors。`);
  process.exit(1);
}

// 收缩 allowlist：已清零的文件移出名单（防止名单虚胖掩盖回退）
const pruned = JSON.parse(fs.readFileSync(ALLOWLIST_PATH, 'utf8')).filter((f) => stillViolating.has(f));
if (pruned.length !== allowlist.size) {
  fs.writeFileSync(ALLOWLIST_PATH, JSON.stringify(pruned.sort(), null, 2) + '\n');
}
console.log(`✓ 令牌门禁通过：业务层无新增 colorScheme 直读（存量 ${pruned.length} 文件在案，待渐进迁移）`);
