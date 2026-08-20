#!/usr/bin/env node
// 官方 stable-diffusion 扩展请求体纯逻辑 → fixture 生成器。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   extensions/stable-diffusion/index.js generateAutoImage / generateSdcppImage（仅 payload 构造，不含 fetch/abort）
// 打桩（脚本头部登记）：getSdRequestBody={url}（App 直连，等价官方服务端代理转发）；settings=extension_settings.sd 传入对象。
// 边界（不移植，登记）：drawthings/vlad/novel/openai/horde/hf/comfy 等其余后端另开差分。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'imagegen.json');

const placeholderVae = 'Automatic';
const defaults = {
    sampler: 'DDIM',
    scheduler: 'normal',
    steps: 20,
    scale: 7,
    width: 512,
    height: 512,
    restore_faces: false,
    enable_hr: false,
    hr_upscaler: 'Latent',
    hr_scale: 1.0,
    denoising_strength: 0.7,
    hr_second_pass_steps: 0,
    seed: -1,
    clip_skip: 1,
    vae: '',
    model: '',
    adetailer_face: false,
};

function buildAutoPayload(settings, prompt, negativePrompt) {
    const isValidVae = settings.vae && !['N/A', placeholderVae].includes(settings.vae);
    let payload = {
        url: 'http://localhost:7860',
        prompt: prompt,
        negative_prompt: negativePrompt,
        sampler_name: settings.sampler,
        scheduler: settings.scheduler,
        steps: settings.steps,
        cfg_scale: settings.scale,
        width: settings.width,
        height: settings.height,
        restore_faces: !!settings.restore_faces,
        enable_hr: !!settings.enable_hr,
        hr_upscaler: settings.hr_upscaler,
        hr_scale: settings.hr_scale,
        hr_additional_modules: [],
        denoising_strength: settings.denoising_strength,
        hr_second_pass_steps: settings.hr_second_pass_steps,
        seed: settings.seed >= 0 ? settings.seed : undefined,
        override_settings: {
            CLIP_stop_at_last_layers: settings.clip_skip,
            sd_vae: isValidVae ? settings.vae : undefined,
            forge_additional_modules: isValidVae ? [settings.vae] : undefined,
        },
        override_settings_restore_afterwards: true,
        clip_skip: settings.clip_skip,
        save_images: true,
        send_images: true,
        do_not_save_grid: false,
        do_not_save_samples: false,
    };
    // Conditionally add the ADetailer if adetailer_face is enabled
    // （官方 index.js generateAutoImage L3830-3845 deepMerge，deepMerge 仅新增键，等价条件赋值）
    if (settings.adetailer_face) {
        payload = deepMerge(payload, {
            alwayson_scripts: {
                ADetailer: {
                    args: [
                        true, // ad_enable
                        true, // skip_img2img
                        {
                            'ad_model': 'face_yolov8n.pt',
                        },
                    ],
                },
            },
        });
    }
    return JSON.parse(JSON.stringify(payload));
}

// 官方 stable-diffusion/index.js L2832 deepMerge（仅深层对象合并，数组整体替换）。
function deepMerge(target, source) {
    for (const key of Object.keys(source)) {
        if (typeof source[key] === 'object' && source[key] !== null && !Array.isArray(source[key]) &&
            typeof target[key] === 'object' && target[key] !== null && !Array.isArray(target[key])) {
            target[key] = deepMerge(target[key], source[key]);
        } else {
            target[key] = source[key];
        }
    }
    return target;
}

function buildSdcppPayload(settings, prompt, negativePrompt) {
    const payload = {
        url: settings.url || 'http://127.0.0.1:1234',
        model: settings.model || undefined,
        prompt: prompt,
        negative_prompt: negativePrompt,
        steps: settings.steps,
        cfg_scale: settings.scale,
        width: settings.width,
        height: settings.height,
        batch_size: 1,
        seed: settings.seed >= 0 ? settings.seed : undefined,
    };
    if (settings.sampler && settings.sampler !== 'N/A') {
        payload.sampler_name = settings.sampler;
    }
    if (settings.scheduler && settings.scheduler !== 'N/A') {
        payload.scheduler = settings.scheduler;
    }
    if (Number.isFinite(settings.clip_skip)) {
        payload.clip_skip = settings.clip_skip;
    }
    return JSON.parse(JSON.stringify(payload));
}

const cases = [];
let id = 0;
function add(name, kind, settings, prompt, negativePrompt) {
    const merged = Object.assign({}, defaults, settings);
    const expected = kind === 'auto'
        ? buildAutoPayload(merged, prompt, negativePrompt)
        : buildSdcppPayload(merged, prompt, negativePrompt);
    cases.push({ id: String(++id).padStart(3, '0') + '-' + name, kind, args: { settings: merged, prompt, negativePrompt }, expected });
}

add('auto-defaults', 'auto', {}, 'a cat', 'blurry');
add('auto-seed-vae-hr', 'auto', { seed: 42, vae: 'vae-ft-mse', enable_hr: true, hr_scale: 2, hr_second_pass_steps: 15, denoising_strength: 0.5, restore_faces: true, clip_skip: 2, sampler: 'Euler a', scheduler: 'karras', steps: 28, scale: 9, width: 768, height: 512 }, 'portrait', 'lowres');
add('auto-vae-placeholder', 'auto', { vae: 'Automatic' }, 'x', '');
add('auto-vae-na', 'auto', { vae: 'N/A' }, 'x', '');
add('auto-seed-minus-one', 'auto', { seed: -1 }, 'x', '');
add('auto-adetailer-defaults', 'auto', { adetailer_face: true }, 'a cat', 'blurry');
add('auto-adetailer-preferred', 'auto', { adetailer_face: true, seed: 42, vae: 'vae-ft-mse', enable_hr: true, hr_scale: 2, hr_second_pass_steps: 15, denoising_strength: 0.5, restore_faces: true, clip_skip: 2, sampler: 'Euler a', scheduler: 'karras', steps: 28, scale: 9, width: 768, height: 512 }, 'portrait', 'lowres');
add('sdcpp-defaults', 'sdcpp', {}, 'a cat', 'blurry');
add('sdcpp-model-seed', 'sdcpp', { model: 'sd-turbo', seed: 7 }, 'x', '');
add('sdcpp-na-sampler-scheduler', 'sdcpp', { sampler: 'N/A', scheduler: 'N/A' }, 'x', '');
add('sdcpp-no-clip', 'sdcpp', { clip_skip: undefined }, 'x', '');
add('sdcpp-blank-model', 'sdcpp', { model: '' }, 'x', '');

writeFileSync(outFile, JSON.stringify({ cases }, null, 2) + '\n');
console.log(`imagegen fixtures: ${cases.length} cases -> ${outFile}`);
