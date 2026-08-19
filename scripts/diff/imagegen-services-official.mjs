#!/usr/bin/env node
// 官方 stable-diffusion 扩展其余后端（services）服务端路由请求体/URL 纯逻辑 → fixture 生成器。
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   src/endpoints/stable-diffusion.js 各 <backend>.post('/generate') 路由内 body/URL 构造段
//
// 打桩（脚本头部登记）：
// - readSecret / SECRET_KEYS.XXX = 常量字符串 apiKey（不影响 body 形态）
// - fetch / response / console = 不调用（脚本只跑 body/URL 构造）
// - request.body = 入参，由 cases 注入
//
// 边界（不差分，登记）：
// - stability 多 form-data 字段（multipart）单独差分；aimlapi/electronhub/nanogpt/xai
//   body 形态简单但 vendor 头/响应解析另开；bfl 异步轮询属行为差分（轮询循环不纯）；
//   drawthings/comfyrunpod 等 LLM 后端另开。
//
// 差分目标（3 后端首批）：
// - together: JSON body {prompt,negative_prompt,height,width,model,steps,n=1,seed>=0?seed:random 0..10^7}
// - pollinations: URL path = encodeURIComponent(prompt)；URLSearchParams query = {model,negative_prompt,seed,width ?? 1024,height ?? 1024,enhance?}
// - chutes: JSON body {model,prompt,negative_prompt,guidance_scale || 7.0,width || 1024,height || 1024,num_inference_steps: steps || 10}
//
// 重要语义：
// - JS `Math.floor(Math.random() * 10_000_000)` 在 fixture 生成中必须打桩为常量
//   （此处用 0），Kotlin 端 DiffTest 也用 0；App 实际运行时仍走真随机。
// - JS `encodeURIComponent` 与 Java `URLEncoder.encode` 行为不同（space=%20 vs +），
//   本差分专门锁这一边界：Pollinations path 必须用 encodeURIComponent 语义。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'imagegen-services.json');

// ---------- 官方 TogetherAI 服务端 body 构造（stable-diffusion.js together.post('/generate') L788-L799 逐字摘） ----------
// 原：body: JSON.stringify({ ... })；这里只保留对象字面量（fixture 不关心 JSON 序列化字符串而关心结构）。
function togetherBody(prompt, negativePrompt, model, steps, width, height, seed) {
    return {
        prompt: prompt,
        negative_prompt: negativePrompt,
        height: height,
        width: width,
        model: model,
        steps: steps,
        n: 1,
        // Limited to 10000 on playground, works fine with more.
        seed: seed >= 0 ? seed : Math.floor(Math.random() * 10_000_000),
    };
}

// ---------- 官方 Pollinations 服务端 URL+query 构造（L1045-L1056 逐字摘） ----------
// 原：const promptUrl = new URL(`https://gen.pollinations.ai/image/${encodeURIComponent(request.body.prompt)}`);
//     const params = new URLSearchParams({ ... });
//     if (request.body.enhance) { params.set('enhance', String(true)); }
//     promptUrl.search = params.toString();
// fixture 输出 { url: promptUrl.toString() }，要求 Kotlin 用 encodeURIComponent 语义（path）+ URLSearchParams 语义（query）。
function pollinationsUrl(prompt, negativePrompt, model, seed, width, height, enhance) {
    const promptUrl = new URL(`https://gen.pollinations.ai/image/${encodeURIComponent(prompt)}`);
    const params = new URLSearchParams({
        model: String(model),
        negative_prompt: String(negativePrompt),
        seed: String(seed >= 0 ? seed : Math.floor(Math.random() * 10_000_000)),
        width: String(width ?? 1024),
        height: String(height ?? 1024),
    });
    if (enhance) {
        params.set('enhance', String(true));
    }
    promptUrl.search = params.toString();
    return promptUrl.toString();
}

// ---------- 官方 Chutes 服务端 body 构造（L1356-L1364 逐字摘） ----------
// 注意 `||` 短路：guidance_scale=0 会被替换为 7.0（JS falsy），width=0 替换为 1024。
function chutesBody(model, prompt, negativePrompt, guidanceScale, width, height, steps) {
    return {
        model: model,
        prompt: prompt,
        negative_prompt: negativePrompt,
        guidance_scale: guidanceScale || 7.0,
        width: width || 1024,
        height: height || 1024,
        num_inference_steps: steps || 10,
    };
}

// ---------- cases ----------
const cases = [];
let id = 0;
function add(name, kind, input, expected) {
    cases.push({ id: String(++id).padStart(3, '0') + '-' + name, kind, args: input, expected });
}

// 固定随机数打桩：Math.random() 在 togetherBody/pollinationsUrl 内被调用，
// 这里 monkey-patch Math.random 返回 0，保证 fixture 确定性。
const originalRandom = Math.random;
Math.random = () => 0;

// TogetherAI cases
add('together-defaults', 'together',
    { prompt: 'a cat', negativePrompt: 'blurry', model: 'flux.1', steps: 4, width: 1024, height: 1024, seed: -1 },
    togetherBody('a cat', 'blurry', 'flux.1', 4, 1024, 1024, -1));
add('together-seed-positive', 'together',
    { prompt: 'portrait', negativePrompt: 'lowres', model: 'flux.1-schnell', steps: 8, width: 768, height: 1024, seed: 42 },
    togetherBody('portrait', 'lowres', 'flux.1-schnell', 8, 768, 1024, 42));
add('together-seed-zero', 'together',
    { prompt: 'x', negativePrompt: '', model: 'm', steps: 1, width: 512, height: 512, seed: 0 },
    togetherBody('x', '', 'm', 1, 512, 512, 0));

// Pollinations cases — 重点锁 encodeURIComponent（path） vs URLSearchParams（query）边界
add('pollinations-defaults', 'pollinations',
    { prompt: 'a cat', negativePrompt: 'blurry', model: 'flux', seed: -1, width: undefined, height: undefined, enhance: false },
    pollinationsUrl('a cat', 'blurry', 'flux', -1, undefined, undefined, false));
add('pollinations-seed-positive', 'pollinations',
    { prompt: 'portrait', negativePrompt: 'lowres', model: 'flux-realism', seed: 7, width: 1024, height: 1024, enhance: false },
    pollinationsUrl('portrait', 'lowres', 'flux-realism', 7, 1024, 1024, false));
add('pollinations-space-in-prompt', 'pollinations',
    // path 中空格必须编码为 %20（encodeURIComponent 语义），不能是 +
    { prompt: 'a cat and dog', negativePrompt: '', model: 'flux', seed: -1, width: 512, height: 512, enhance: false },
    pollinationsUrl('a cat and dog', '', 'flux', -1, 512, 512, false));
add('pollinations-special-chars', 'pollinations',
    // ! * ' ( ) 不被 encodeURIComponent 编码，但 URLEncoder.encode 会编码这些
    { prompt: "hello!*'()", negativePrompt: '', model: 'm', seed: 0, width: 512, height: 512, enhance: false },
    pollinationsUrl("hello!*'()", '', 'm', 0, 512, 512, false));
add('pollinations-enhance-true', 'pollinations',
    { prompt: 'x', negativePrompt: '', model: 'm', seed: 1, width: 512, height: 512, enhance: true },
    pollinationsUrl('x', '', 'm', 1, 512, 512, true));
add('pollinations-unicode', 'pollinations',
    { prompt: '猫娘', negativePrompt: '犬', model: 'flux', seed: -1, width: 1024, height: 1024, enhance: false },
    pollinationsUrl('猫娘', '犬', 'flux', -1, 1024, 1024, false));

// Chutes cases — 锁 || 短路：0/undefined 都被替换为默认
add('chutes-defaults', 'chutes',
    { model: 'flux', prompt: 'a cat', negativePrompt: 'blurry', guidanceScale: undefined, width: undefined, height: undefined, steps: undefined },
    chutesBody('flux', 'a cat', 'blurry', undefined, undefined, undefined, undefined));
add('chutes-zero-fallback', 'chutes',
    // 0 应被替换为 7.0/1024/1024/10（JS || 短路）
    { model: 'flux', prompt: 'x', negativePrompt: '', guidanceScale: 0, width: 0, height: 0, steps: 0 },
    chutesBody('flux', 'x', '', 0, 0, 0, 0));
add('chutes-explicit-values', 'chutes',
    { model: 'flux.1', prompt: 'portrait', negativePrompt: 'lowres', guidanceScale: 9.5, width: 768, height: 1024, steps: 20 },
    chutesBody('flux.1', 'portrait', 'lowres', 9.5, 768, 1024, 20));

Math.random = originalRandom;

// ============ 第二批：6 云端后端（stability/aimlapi/electronhub/nanogpt/bfl/xai） ============
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   public/scripts/extensions/stable-diffusion/index.js generate{Stability|Aimlapi|ElectronHub|NanoGPT|Bfl|XAI}Image 客户端 body 构造段
//   src/endpoints/stable-diffusion.js {stability|electronhub|nanogpt|bfl|xai|aimlapi}.post('/generate'...) 服务端加工段
//
// 重要语义：
// - stability/nanogpt/aimlapi：服务端直接 JSON.stringify(request.body) 转发 → 差分客户端 body 形态
// - electronhub：服务端构造 bodyParams = {model, prompt, response_format:'b64_json', [size], [quality]}
//   → 差分服务端 bodyParams 形态（size 由 getClosestSize 网络获取后传入，差分时打桩）
// - bfl：服务端加工 requestBody（加 safety_tolerance/output_format、按 model 后缀 delete + 加 aspect_ratio）
//   → 差分服务端加工后最终 body（App 直连 BFL 端点拼此 body）
// - xai：服务端加工 requestBody（加 response_format、aspect_ratio/resolution 仅 grok-imagine）
//   → 差分服务端加工后最终 body

// ---------- 官方 getClosestAspectRatio（index.js L3568-L3635 逐字摘，纯函数） ----------
// stability/xai 比例表 + 最近匹配；for...in 遍历顺序与 Object.keys 一致
function getClosestAspectRatio(width, height, source) {
    function getAspectRatios() {
        switch (source) {
            case 'stability':
                return {
                    '16:9': 16 / 9,
                    '1:1': 1,
                    '21:9': 21 / 9,
                    '2:3': 2 / 3,
                    '3:2': 3 / 2,
                    '4:5': 4 / 5,
                    '5:4': 5 / 4,
                    '9:16': 9 / 16,
                    '9:21': 9 / 21,
                };
            case 'xai':
                return {
                    '1:1': 1,
                    '3:4': 3 / 4,
                    '4:3': 4 / 3,
                    '9:16': 9 / 16,
                    '16:9': 16 / 9,
                    '2:3': 2 / 3,
                    '3:2': 3 / 2,
                    '9:19.5': 9 / 19.5,
                    '19.5:9': 19.5 / 9,
                    '9:20': 9 / 20,
                    '20:9': 20 / 9,
                    '1:2': 1 / 2,
                    '2:1': 2 / 1,
                };
            default:
                return null;
        }
    }
    const aspectRatios = getAspectRatios() || { '1:1': 1 };
    const aspectRatio = width / height;
    let closestAspectRatio = Object.keys(aspectRatios)[0];
    let minDiff = Math.abs(aspectRatio - aspectRatios[closestAspectRatio]);
    for (const key in aspectRatios) {
        const diff = Math.abs(aspectRatio - aspectRatios[key]);
        if (diff < minDiff) {
            minDiff = diff;
            closestAspectRatio = key;
        }
    }
    return closestAspectRatio;
}

// ---------- 官方 getClosestSize（index.js L3644-L3701 逐字摘，纯逻辑部分；网络段打桩为 sizes 参数） ----------
function getClosestSize(width, height, sizes = []) {
    const sizesData = [];
    if (Array.isArray(sizes) && sizes.length > 0) {
        sizesData.push(...sizes);
    } else {
        // 官方此处调 /api/sd/electronhub/sizes 拉取；差分打桩：无 sizes → null
        return null;
    }
    const targetWidth = Number(width);
    const targetHeight = Number(height);
    if (isNaN(targetWidth) || isNaN(targetHeight)) {
        return null;
    }
    const targetAspect = targetWidth / targetHeight;
    const targetResolution = targetWidth * targetHeight;
    const closestSize = sizesData.reduce((closest, size) => {
        if (!size || typeof size !== 'string') {
            return closest;
        }
        const sizeParts = size.split('x');
        if (sizeParts.length !== 2) {
            return closest;
        }
        const sizeWidth = Number(sizeParts[0]);
        const sizeHeight = Number(sizeParts[1]);
        if (isNaN(sizeWidth) || isNaN(sizeHeight)) {
            return closest;
        }
        const aspectDiff = Math.abs((sizeWidth / sizeHeight) - targetAspect) / targetAspect;
        const resolutionDiff = Math.abs(sizeWidth * sizeHeight - targetResolution) / targetResolution;
        const diff = aspectDiff + resolutionDiff;
        return diff < closest.diff ? { size, diff } : closest;
    }, { size: null, diff: Infinity });
    const size = closestSize.size;
    return size;
}

// ---------- 官方 clamp（index.js 内联，与 Math.min/Math.max 一致） ----------
function clamp(number, lower, upper) {
    return Math.min(Math.max(number, lower), upper);
}

// ---------- Stability（index.js generateStabilityImage L3720-L3730 客户端 body 构造 1:1） ----------
// body = { model, payload: { prompt: slice(0,10000), negative_prompt: slice(0,10000), aspect_ratio: getClosestAspectRatio(w,h,'stability'), seed: seed>=0?seed:undefined, style_preset, output_format: 'png' } }
function stabilityBody(model, prompt, negativePrompt, width, height, seed, stylePreset) {
    const PROMPT_LIMIT = 10000;
    return {
        model: model,
        payload: {
            prompt: prompt.slice(0, PROMPT_LIMIT),
            negative_prompt: negativePrompt.slice(0, PROMPT_LIMIT),
            aspect_ratio: getClosestAspectRatio(width, height, 'stability'),
            seed: seed >= 0 ? seed : undefined,
            style_preset: stylePreset,
            output_format: 'png',
        },
    };
}

// ---------- Aimlapi（index.js generateAimlapiImage L4184-L4196 客户端 body 构造 1:1） ----------
// isSdLike = model.startsWith('flux/') || model.startsWith('stable') || model==='recraft-v3' || model==='triposr'
// isSdLike: {prompt, model, steps: clamp(steps,1,50), guidance: clamp(scale,1.5,5), width: clamp(w,256,1440), height: clamp(h,256,1440), seed?: seed>=0}
// 否则: {prompt, model, n:1, size: "${w}x${h}", quality: openaiQuality, style: openaiStyle}
function aimlapiBody(prompt, model, steps, scale, width, height, seed, openaiQuality, openaiStyle) {
    const lowerModel = String(model).toLowerCase();
    const isSdLike =
        lowerModel.startsWith('flux/') ||
        lowerModel.startsWith('stable') ||
        lowerModel === 'recraft-v3' ||
        lowerModel === 'triposr';
    const body = { prompt: prompt, model: model };
    if (isSdLike) {
        body.steps = clamp(steps, 1, 50);
        body.guidance = clamp(scale, 1.5, 5);
        body.width = clamp(width, 256, 1440);
        body.height = clamp(height, 256, 1440);
        if (seed >= 0) body.seed = seed;
    } else {
        body.n = 1;
        body.size = `${width}x${height}`;
        body.quality = openaiQuality;
        body.style = openaiStyle;
    }
    return body;
}

// ---------- ElectronHub（stable-diffusion.js electronhub.post L1232-L1244 服务端 bodyParams 构造 1:1） ----------
// bodyParams = { model, prompt, response_format: 'b64_json', size?, quality? }
// size 由客户端 getClosestSize 获取（网络，差分时由参数传入打桩值）
// quality = String(electronhub_quality||'').trim() || undefined（差分时由参数传入）
function electronhubBody(model, prompt, size, quality) {
    const bodyParams = {
        model: model,
        prompt: prompt,
        response_format: 'b64_json',
    };
    if (size) {
        bodyParams.size = size;
    }
    if (quality) {
        bodyParams.quality = quality;
    }
    return bodyParams;
}

// ---------- NanoGPT（index.js generateNanoGPTImage L4436-L4447 客户端 body 构造 1:1） ----------
// body = { model, prompt, negative_prompt, num_steps: parseInt(steps), scale: parseFloat(scale), width: parseInt(width), height: parseInt(height), resolution: "${w}x${h}", showExplicitContent: true, nImages: 1 }
// 注意 parseInt/parseFloat 语义：parseInt(4.5)=4，parseFloat(4.5)=4.5；输入为数字时 parseInt(4)=4, parseFloat(4)=4
function nanogptBody(model, prompt, negativePrompt, steps, scale, width, height) {
    return {
        model: model,
        prompt: prompt,
        negative_prompt: negativePrompt,
        num_steps: parseInt(steps),
        scale: parseFloat(scale),
        width: parseInt(width),
        height: parseInt(height),
        resolution: `${width}x${height}`,
        showExplicitContent: true,
        nImages: 1,
    };
}

// ---------- BFL（stable-diffusion.js bfl.post L1481-L1527 服务端 requestBody 构造 + 加工 1:1） ----------
// 初始 requestBody = { prompt, steps, guidance, width, height, prompt_upsampling, seed: seed ?? null, safety_tolerance: 6, output_format: 'jpeg' }
// model.endsWith('-ultra'): 加 aspect_ratio = bflGetClosestAspectRatio(w,h)，delete steps/guidance/width/height/prompt_upsampling
// model.endsWith('-pro-1.1'): delete steps/guidance
function bflGetClosestAspectRatio(width, height) {
    const minAspect = 9 / 21;
    const maxAspect = 21 / 9;
    const currentAspect = width / height;
    const gcd = (a, b) => b === 0 ? a : gcd(b, a % b);
    const simplifyRatio = (w, h) => {
        const divisor = gcd(w, h);
        return `${w / divisor}:${h / divisor}`;
    };
    if (currentAspect < minAspect) {
        const adjustedHeight = Math.round(width / minAspect);
        return simplifyRatio(width, adjustedHeight);
    } else if (currentAspect > maxAspect) {
        const adjustedWidth = Math.round(height * maxAspect);
        return simplifyRatio(adjustedWidth, height);
    } else {
        return simplifyRatio(width, height);
    }
}
function bflBody(model, prompt, steps, scale, width, height, promptUpsampling, seed) {
    const requestBody = {
        prompt: prompt,
        steps: clamp(steps, 1, 50),
        guidance: clamp(scale, 1.5, 5),
        width: clamp(width, 256, 1440),
        height: clamp(height, 256, 1440),
        prompt_upsampling: !!promptUpsampling,
        seed: seed ?? null,
        safety_tolerance: 6,
        output_format: 'jpeg',
    };
    if (String(model).endsWith('-ultra')) {
        requestBody.aspect_ratio = bflGetClosestAspectRatio(width, height);
        delete requestBody.steps;
        delete requestBody.guidance;
        delete requestBody.width;
        delete requestBody.height;
        delete requestBody.prompt_upsampling;
    }
    if (String(model).endsWith('-pro-1.1')) {
        delete requestBody.steps;
        delete requestBody.guidance;
    }
    return requestBody;
}

// ---------- xAI（stable-diffusion.js xai.post L1740-L1746 服务端 requestBody 构造 1:1） ----------
// requestBody = { prompt, model, aspect_ratio, resolution, response_format: 'b64_json' }
// aspect_ratio/resolution 仅当 model 含 grok-imagine 时由客户端计算后传入；否则为 undefined（JSON.stringify 省略）
// 客户端 generateXAIImage: aspect_ratio = getClosestAspectRatio(w,h,'xai'), resolution = (w*h > 1296*864) ? '2k' : '1k'
function xaiBody(prompt, model, aspectRatio, resolution) {
    return {
        prompt: prompt,
        model: model,
        aspect_ratio: aspectRatio,
        resolution: resolution,
        response_format: 'b64_json',
    };
}

// ============ Stability cases ============
add('stability-ultra-defaults', 'stability',
    { model: 'stable-image-ultra', prompt: 'a cat', negativePrompt: 'blurry', width: 1024, height: 1024, seed: -1, stylePreset: undefined },
    stabilityBody('stable-image-ultra', 'a cat', 'blurry', 1024, 1024, -1, undefined));
add('stability-core-with-seed', 'stability',
    { model: 'stable-image-core', prompt: 'portrait', negativePrompt: 'lowres', width: 768, height: 1024, seed: 42, stylePreset: undefined },
    stabilityBody('stable-image-core', 'portrait', 'lowres', 768, 1024, 42, undefined));
add('stability-sd3-with-style', 'stability',
    // stylePreset 非空 → 传
    { model: 'stable-diffusion-3', prompt: 'x', negativePrompt: '', width: 512, height: 512, seed: 0, stylePreset: 'cinematic' },
    stabilityBody('stable-diffusion-3', 'x', '', 512, 512, 0, 'cinematic'));
add('stability-aspect-16-9', 'stability',
    // 验证 getClosestAspectRatio(1920,1080,'stability')='16:9'
    { model: 'stable-image-ultra', prompt: 'wide', negativePrompt: '', width: 1920, height: 1080, seed: -1, stylePreset: undefined },
    stabilityBody('stable-image-ultra', 'wide', '', 1920, 1080, -1, undefined));

// ============ Aimlapi cases ============
add('aimlapi-flux-sdlike', 'aimlapi',
    // flux/ 前缀 → isSdLike 分支
    { prompt: 'a cat', model: 'flux/1.1', steps: 4, scale: 3.0, width: 1024, height: 1024, seed: -1, openaiQuality: undefined, openaiStyle: undefined },
    aimlapiBody('a cat', 'flux/1.1', 4, 3.0, 1024, 1024, -1, undefined, undefined));
add('aimlapi-stable-prefix', 'aimlapi',
    { prompt: 'x', model: 'stable-diffusion-3', steps: 30, scale: 7.0, width: 768, height: 768, seed: 42, openaiQuality: undefined, openaiStyle: undefined },
    aimlapiBody('x', 'stable-diffusion-3', 30, 7.0, 768, 768, 42, undefined, undefined));
add('aimlapi-recraft-v3', 'aimlapi',
    { prompt: 'y', model: 'recraft-v3', steps: 25, scale: 5.0, width: 1024, height: 1024, seed: -1, openaiQuality: undefined, openaiStyle: undefined },
    aimlapiBody('y', 'recraft-v3', 25, 5.0, 1024, 1024, -1, undefined, undefined));
add('aimlapi-openai-like', 'aimlapi',
    // 非 isSdLike → n/size/quality/style 分支
    { prompt: 'z', model: 'gpt-image-1', steps: 0, scale: 0, width: 1024, height: 1024, seed: -1, openaiQuality: 'hd', openaiStyle: 'vivid' },
    aimlapiBody('z', 'gpt-image-1', 0, 0, 1024, 1024, -1, 'hd', 'vivid'));
add('aimlapi-clamp-bounds', 'aimlapi',
    // steps=100 → clamp 到 50；scale=10 → clamp 到 5；width=10 → clamp 到 256；height=9999 → clamp 到 1440
    { prompt: 'clamp', model: 'flux/1', steps: 100, scale: 10, width: 10, height: 9999, seed: -1, openaiQuality: undefined, openaiStyle: undefined },
    aimlapiBody('clamp', 'flux/1', 100, 10, 10, 9999, -1, undefined, undefined));

// ============ getClosestSize cases ============
add('getclosestsize-empty-sizes', 'getclosestsize',
    // 空 sizes → null
    { width: 1024, height: 1024, sizes: [] },
    { result: getClosestSize(1024, 1024, []) });
add('getclosestsize-single-match', 'getclosestsize',
    { width: 1024, height: 1024, sizes: ['1024x1024'] },
    { result: getClosestSize(1024, 1024, ['1024x1024']) });
add('getclosestsize-pick-closest', 'getclosestsize',
    // 1024x1024 在 ['512x512','1024x1024','2048x2048'] 中应选 '1024x1024'
    { width: 1024, height: 1024, sizes: ['512x512', '1024x1024', '2048x2048'] },
    { result: getClosestSize(1024, 1024, ['512x512', '1024x1024', '2048x2048']) });
add('getclosestsize-aspect-priority', 'getclosestsize',
    // 768x1024 (aspect 0.75) 在 ['512x512'(1.0),'768x1024'(0.75),'1024x768'(1.33)] 中应选 '768x1024'
    { width: 768, height: 1024, sizes: ['512x512', '768x1024', '1024x768'] },
    { result: getClosestSize(768, 1024, ['512x512', '768x1024', '1024x768']) });

// ============ ElectronHub cases ============
add('electronhub-with-size', 'electronhub',
    // size 非空 → 传；quality 空 → undefined 省略
    { model: 'flux-1', prompt: 'a cat', size: '1024x1024', quality: '' },
    electronhubBody('flux-1', 'a cat', '1024x1024', ''));
add('electronhub-null-size', 'electronhub',
    // size=null → 省略
    { model: 'm', prompt: 'x', size: null, quality: '' },
    electronhubBody('m', 'x', null, ''));
add('electronhub-with-quality', 'electronhub',
    { model: 'm', prompt: 'y', size: '768x768', quality: 'hd' },
    electronhubBody('m', 'y', '768x768', 'hd'));

// ============ NanoGPT cases ============
add('nanogpt-defaults', 'nanogpt',
    { model: 'dall-e-3', prompt: 'a cat', negativePrompt: 'blurry', steps: 4, scale: 3.0, width: 1024, height: 1024 },
    nanogptBody('dall-e-3', 'a cat', 'blurry', 4, 3.0, 1024, 1024));
add('nanogpt-parseInt-semantics', 'nanogpt',
    // parseInt(4.5)=4, parseFloat(3.5)=3.5；输入数字时 parseInt(4.5)=4
    { model: 'm', prompt: 'x', negativePrompt: '', steps: 4.5, scale: 3.5, width: 512.7, height: 512.2 },
    nanogptBody('m', 'x', '', 4.5, 3.5, 512.7, 512.2));
add('nanogpt-unicode', 'nanogpt',
    { model: 'flux', prompt: '猫娘', negativePrompt: '犬', steps: 20, scale: 7.0, width: 768, height: 1024 },
    nanogptBody('flux', '猫娘', '犬', 20, 7.0, 768, 1024));

// ============ BFL cases ============
add('bfl-defaults', 'bfl',
    // 默认 model 无后缀 → 全字段
    { model: 'flux-pro', prompt: 'a cat', steps: 30, scale: 3.0, width: 1024, height: 1024, promptUpsampling: false, seed: 42 },
    bflBody('flux-pro', 'a cat', 30, 3.0, 1024, 1024, false, 42));
add('bfl-ultra-deletes-fields', 'bfl',
    // -ultra → 加 aspect_ratio，delete steps/guidance/width/height/prompt_upsampling
    { model: 'flux-1.1-ultra', prompt: 'wide', steps: 30, scale: 3.0, width: 1920, height: 1080, promptUpsampling: true, seed: -1 },
    bflBody('flux-1.1-ultra', 'wide', 30, 3.0, 1920, 1080, true, -1));
add('bfl-pro-1.1-deletes-steps-guidance', 'bfl',
    // -pro-1.1 → delete steps/guidance（保留 width/height/prompt_upsampling）
    { model: 'flux-pro-1.1', prompt: 'x', steps: 30, scale: 3.0, width: 1024, height: 1024, promptUpsampling: true, seed: 0 },
    bflBody('flux-pro-1.1', 'x', 30, 3.0, 1024, 1024, true, 0));
add('bfl-seed-null', 'bfl',
    // seed=null → null（保持）
    { model: 'flux-pro', prompt: 'y', steps: 20, scale: 4.0, width: 768, height: 1024, promptUpsampling: false, seed: null },
    bflBody('flux-pro', 'y', 20, 4.0, 768, 1024, false, null));
add('bfl-clamp-bounds', 'bfl',
    // steps=100 → clamp 50；scale=10 → clamp 5；width=10 → clamp 256；height=9999 → clamp 1440
    { model: 'flux-pro', prompt: 'clamp', steps: 100, scale: 10, width: 10, height: 9999, promptUpsampling: false, seed: 42 },
    bflBody('flux-pro', 'clamp', 100, 10, 10, 9999, false, 42));

// ============ xAI cases ============
add('xai-non-grok-no-aspect', 'xai',
    // 非 grok-imagine → aspect_ratio/resolution = undefined（JSON.stringify 省略）
    { prompt: 'a cat', model: 'grok-2-vision', aspectRatio: undefined, resolution: undefined },
    xaiBody('a cat', 'grok-2-vision', undefined, undefined));
add('xai-grok-imagine-1k', 'xai',
    // grok-imagine + 客户端传 aspect_ratio/resolution（1k，因 1024*1024 < 1296*864 阈值）
    { prompt: 'wide', model: 'grok-imagine-image', aspectRatio: '16:9', resolution: '1k' },
    xaiBody('wide', 'grok-imagine-image', '16:9', '1k'));
add('xai-grok-imagine-2k', 'xai',
    // 2k 分辨率（客户端 width*height > 1296*864 时传 '2k'）
    { prompt: 'huge', model: 'grok-imagine-image-pro', aspectRatio: '1:1', resolution: '2k' },
    xaiBody('huge', 'grok-imagine-image-pro', '1:1', '2k'));

// ============ 第三批：图生 LLM 后端（extras 可差分，其余 App 直连厂商登记） ============
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   public/scripts/extensions/stable-diffusion/index.js generateExtrasImage L3524-L3550 body 字段集合
//
// 边界（不差分，登记）：
// - google：App 直连 Vertex AI predict body（{instances, parameters}） vs 官方 ST 服务端 google.js L432-L500
//   转发到 Vertex AI predict 的 requestBody 字段集合（含 isVertex/isDeprecated/getConfigValue 分支），
//   两者字段映射关系属服务端实现，App 简化按准则 2 字段对照登记（待补救：引擎层提取 ST→vertex 映射 + 差分）。
// - falai/zai/openrouter/workersai：App 直连厂商 REST API body vs 官方 ST 服务端转发 body（不同源），
//   厂商 API 字段集合与 ST 转发 body 字段集合不同，按准则 2 字段对照登记。
// - drawthings：macOS only，App 返回 null，登记不实现。
// - comfy_runpod：workflow 字符串替换（replaceComfyWorkflow）纯函数可差分，登记待补救。

// ---------- 官方 Extras body 构造（index.js generateExtrasImage L3524-L3550 逐字摘） ----------
// body: JSON.stringify({ ... })；这里只保留对象字面量（fixture 不关心 JSON 序列化字符串而关心结构）。
// 打桩：horde_karras 默认 false（App ServicesPrefs 无此字段，按官方默认 false 等价）。
function extrasBody(prompt, negativePrompt, sampler, steps, scale, width, height,
                    restoreFaces, enableHr, hordeKarras, hrUpscaler, hrScale,
                    denoisingStrength, hrSecondPassSteps, seed) {
    return {
        prompt: prompt,
        sampler: sampler,
        steps: steps,
        scale: scale,
        width: width,
        height: height,
        negative_prompt: negativePrompt,
        restore_faces: !!restoreFaces,
        enable_hr: !!enableHr,
        karras: !!hordeKarras,
        hr_upscaler: hrUpscaler,
        hr_scale: hrScale,
        denoising_strength: denoisingStrength,
        hr_second_pass_steps: hrSecondPassSteps,
        seed: seed >= 0 ? seed : undefined,
    };
}

// ============ Extras cases ============
add('extras-defaults', 'extras',
    { prompt: 'a cat', negativePrompt: 'blurry', sampler: 'DDIM', steps: 20, scale: 7.0,
      width: 512, height: 512, restoreFaces: false, enableHr: false, hordeKarras: false,
      hrUpscaler: 'Latent', hrScale: 1.0, denoisingStrength: 0.7, hrSecondPassSteps: 0, seed: -1 },
    extrasBody('a cat', 'blurry', 'DDIM', 20, 7.0, 512, 512, false, false, false, 'Latent', 1.0, 0.7, 0, -1));
add('extras-seed-positive', 'extras',
    { prompt: 'portrait', negativePrompt: 'lowres', sampler: 'Euler a', steps: 30, scale: 5.5,
      width: 768, height: 1024, restoreFaces: true, enableHr: false, hordeKarras: false,
      hrUpscaler: 'Latent', hrScale: 1.0, denoisingStrength: 0.7, hrSecondPassSteps: 0, seed: 42 },
    extrasBody('portrait', 'lowres', 'Euler a', 30, 5.5, 768, 1024, true, false, false, 'Latent', 1.0, 0.7, 0, 42));
add('extras-hr-enabled', 'extras',
    // HR 开 + karras 开：!! 双叹号语义（false→false, true→true, 0→false, 1→true）
    { prompt: 'x', negativePrompt: '', sampler: 'DPM++ 2M', steps: 25, scale: 6.0,
      width: 1024, height: 1024, restoreFaces: true, enableHr: true, hordeKarras: true,
      hrUpscaler: '4x-UltraSharp', hrScale: 2.0, denoisingStrength: 0.5, hrSecondPassSteps: 10, seed: 0 },
    extrasBody('x', '', 'DPM++ 2M', 25, 6.0, 1024, 1024, true, true, true, '4x-UltraSharp', 2.0, 0.5, 10, 0));
add('extras-seed-zero', 'extras',
    // seed=0 → 0（>=0 才传，0 也传）
    { prompt: 'y', negativePrompt: '', sampler: 'DDIM', steps: 10, scale: 7.0,
      width: 512, height: 512, restoreFaces: false, enableHr: false, hordeKarras: false,
      hrUpscaler: 'Latent', hrScale: 1.0, denoisingStrength: 0.7, hrSecondPassSteps: 0, seed: 0 },
    extrasBody('y', '', 'DDIM', 10, 7.0, 512, 512, false, false, false, 'Latent', 1.0, 0.7, 0, 0));

writeFileSync(outFile, JSON.stringify({ cases }, null, 2) + '\n');
console.log(`imagegen-services fixtures: ${cases.length} cases -> ${outFile}`);
