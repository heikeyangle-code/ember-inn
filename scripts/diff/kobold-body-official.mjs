#!/usr/bin/env node
// 官方 Kobold 请求体差分：src/endpoints/backends/kobold.js /generate 的请求体构建。
// 提取源（SillyTavern 1.18.0 / 8172dcd）：函数体逐字；打桩：无（纯请求体构建）。
// 覆盖：gui_settings=false 全字段 / gui_settings=true 基础体 / stop_sequence 条件 / localhost→127.0.0.1 / 流式与非流式 URL。
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ---- 官方函数（逐字，摘自 kobold.js） ----
function buildKoboldRequest(body) {
    let api_server = body.api_server;
    if (api_server.indexOf('localhost') != -1) {
        api_server = api_server.replace('localhost', '127.0.0.1');
    }
    let this_settings = {
        prompt: body.prompt,
        use_story: false,
        use_memory: false,
        use_authors_note: false,
        use_world_info: false,
        max_context_length: body.max_context_length,
        max_length: body.max_length,
    };
    if (!body.gui_settings) {
        this_settings = {
            prompt: body.prompt,
            use_story: false,
            use_memory: false,
            use_authors_note: false,
            use_world_info: false,
            max_context_length: body.max_context_length,
            max_length: body.max_length,
            rep_pen: body.rep_pen,
            rep_pen_range: body.rep_pen_range,
            rep_pen_slope: body.rep_pen_slope,
            temperature: body.temperature,
            tfs: body.tfs,
            top_a: body.top_a,
            top_k: body.top_k,
            top_p: body.top_p,
            min_p: body.min_p,
            typical: body.typical,
            sampler_order: body.sampler_order,
            singleline: !!body.singleline,
            use_default_badwordsids: body.use_default_badwordsids,
            mirostat: body.mirostat,
            mirostat_eta: body.mirostat_eta,
            mirostat_tau: body.mirostat_tau,
            grammar: body.grammar,
            sampler_seed: body.sampler_seed,
        };
        if (body.stop_sequence) {
            this_settings.stop_sequence = body.stop_sequence;
        }
    }
    const url = body.streaming ? `${api_server}/extra/generate/stream` : `${api_server}/v1/generate`;
    return { api_server, url, body: JSON.stringify(this_settings) };
}

const cases = [];
function add(id, body) {
    const result = buildKoboldRequest(body);
    cases.push({ id, args: { body }, expected: { api_server: result.api_server, url: result.url, body: result.body } });
}

const full = {
    api_server: 'http://localhost:5001',
    prompt: 'hello',
    max_context_length: 2048,
    max_length: 128,
    rep_pen: 1.1,
    rep_pen_range: 1024,
    rep_pen_slope: 0.7,
    temperature: 0.8,
    tfs: 1,
    top_a: 0,
    top_k: 40,
    top_p: 0.9,
    min_p: 0.05,
    typical: 1,
    sampler_order: [0, 1, 2, 3, 4, 5, 6],
    singleline: false,
    use_default_badwordsids: false,
    mirostat: 0,
    mirostat_eta: 0.1,
    mirostat_tau: 5,
    grammar: '',
    sampler_seed: -1,
};

add('full', { ...full });
add('full-stop', { ...full, stop_sequence: '\nUser:' });
add('full-stream', { ...full, streaming: true });
add('gui-settings', { ...full, gui_settings: true });
add('gui-stream', { ...full, gui_settings: true, streaming: true, stop_sequence: 'x' });
add('singleline-on', { ...full, singleline: true });
add('no-localhost', { ...full, api_server: 'https://kobold.example.com' });
add('localhost-https', { ...full, api_server: 'https://localhost:5001' });
add('bannedwords', { ...full, use_default_badwordsids: true, grammar: 'gram', mirostat: 2 });
add('stop-empty', { ...full, stop_sequence: '' });
add('typical-zero', { ...full, typical: 0, top_a: 0.5, min_p: 0 });
add('seed-42', { ...full, sampler_seed: 42 });

const out = join(__dirname, '..', '..', 'engine/src/test/resources/diff/kobold-body.json');
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify(cases, null, 1) + '\n');
console.log('kobold-body cases:', cases.length, '->', out);
