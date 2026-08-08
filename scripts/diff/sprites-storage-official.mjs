#!/usr/bin/env node
// sprites.js getSpritesPath / importRisuSprites → JSON fixture。
// 函数体照官方实现；fs/writeFileAtomicSync 打桩，只输出纯逻辑结果。

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const officialRef = process.env.OFFICIAL_REF || join(repoRoot, '..', 'sillytavern-ref');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'sprites-storage.json');

const funcs = `
const sanitize = (name) => String(name || '').replace(/[\\/?<>\\\\:*|"]/g, '');
const path = {
    join: (...parts) => parts.filter(x => x !== '' && x != null).join('/'),
};
let writtenFiles = [];

function getSpritesPath(directories, name, isSubfolder) {
    if (isSubfolder) {
        const nameParts = name.split('/');
        const characterName = sanitize(nameParts[0]);
        const subfolderName = sanitize(nameParts[1]);
        if (!characterName || !subfolderName) return null;
        return path.join(directories.characters, characterName, subfolderName);
    }
    name = sanitize(name);
    if (!name) return null;
    return path.join(directories.characters, name);
}

function importRisuSprites(directories, data) {
    try {
        const name = data?.data?.name;
        const risuData = data?.data?.extensions?.risuai;
        if (!risuData || !name) return;
        let images = [];
        if (Array.isArray(risuData.additionalAssets)) images = images.concat(risuData.additionalAssets);
        if (Array.isArray(risuData.emotions)) images = images.concat(risuData.emotions);
        if (images.length === 0) return;
        const spritesPath = getSpritesPath(directories, name, false);
        if (!spritesPath) return;
        const files = [];
        for (const [label, fileBase64] of images) {
            if (files.includes(label)) continue;
            const filename = label + '.png';
            writtenFiles.push({ spritesPath, filename });
            files.push(label);
        }
        delete data.data.extensions.risuai.additionalAssets;
        delete data.data.extensions.risuai.emotions;
    } catch {}
}
`;

const runCase = new Function([
    funcs,
    'return async (request) => {',
    '    writtenFiles = [];',
    '    const directories = { characters: "/chars" };',
    '    if (request.body.method === "path") return getSpritesPath(directories, request.body.name, request.body.isSubfolder ?? false);',
    '    if (request.body.method === "risu") {',
    '        const data = JSON.parse(JSON.stringify(request.body.data ?? null));',
    '        importRisuSprites(directories, data);',
    '        return { writtenFiles, data };',
    '    }',
    '    throw new Error("unknown method");',
    '};',
].join('\n'));

const cases = [];
async function add(id, body) {
    const expected = await runCase()({ body });
    cases.push({ id, args: { body }, expected });
}

await add('path-simple', { method: 'path', name: 'Alice' });
await add('path-subfolder', { method: 'path', name: 'Alice/礼服', isSubfolder: true });
await add('path-invalid', { method: 'path', name: 'a/b:c' });
await add('risu-basic', {
    method: 'risu',
    data: { data: { name: 'Alice', extensions: { risuai: { additionalAssets: [['happy', 'SEA='], ['sad', 'U0FE']], emotions: [['angry', 'QU5H']] } } } },
});
await add('risu-duplicate', {
    method: 'risu',
    data: { data: { name: 'Bob', extensions: { risuai: { additionalAssets: [['joy', 'Sk9Z'], ['joy', 'Sk9Z']], emotions: [] } } } },
});
await add('risu-empty', {
    method: 'risu',
    data: { data: { name: 'Carol', extensions: { risuai: { additionalAssets: [], emotions: [] } } } },
});
await add('risu-no-name', {
    method: 'risu',
    data: { data: { extensions: { risuai: { additionalAssets: [['x', 'WA==']] } } } },
});


await add('path-empty', { method: 'path', name: '' });
await add('risu-existing', {
    method: 'risu',
    data: { data: { name: 'Bob', extensions: { risuai: { additionalAssets: [['joy', 'Sk9Z']], emotions: [] } } } },
});
writeFileSync(outFile, JSON.stringify({ source: 'sprites.js getSpritesPath/importRisuSprites', cases }, null, 2));
console.log('sprites-storage:', cases.length, 'cases ->', outFile);
