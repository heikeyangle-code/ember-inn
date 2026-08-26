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

// ============ 第三批：图生 LLM 后端 5 个 + comfy 纯函数 ============
// 官方函数逐字摘自 SillyTavern 1.18.0 release 8172dcd：
//   stable-diffusion.js falai.post L1643-L1651（服务端加工后 requestBody）
//   index.js generateGoogleImage L4610-L4623（客户端 fetch('/api/google/generate-image') body，非 veo 分支）
//   index.js generateZaiImage L4688-L4699（客户端 image 分支 body，不含 round/clamp while 预处理）
//   index.js generateOpenRouterImage L4722-L4730（客户端 body）
//   index.js generateWorkersAIImage L4745-L4755（客户端 JSON body，服务端另翻译为 Cloudflare form）
//   index.js generateComfyImageCommon L4231-L4261（纯函数占位符替换核心段）
//
// 注：google 服务端 google.js /generate-image 再做 isVertex/isDeprecated/getConfigValue 映射
//   → {instances:[{prompt}],parameters:{...}}，该映射属服务端实现，此处差分客户端 body；
//   zai 有 while(w*h>2^21) 多值递减（while 不纯），此处差分最终 "WxH" 字段形态；
//   workersai 服务端翻译为 Cloudflare form，此处差分客户端 JSON body；
//   drawthings：macOS only，登记不实现（App 返回 null）。

// ---------- 官方 FalAI 服务端 requestBody（stable-diffusion.js falai.post L1643-L1651 逐字摘） ----------
function falaiServerBody(prompt, width, height, steps, guidance, seed) {
    return {
        prompt: prompt,
        image_size: { 'width': clamp(width, 256, 1440), 'height': clamp(height, 256, 1440) },
        num_inference_steps: clamp(steps, 1, 50),
        seed: seed ?? null,
        guidance_scale: clamp(guidance, 1.5, 5),
        enable_safety_checker: false,
        safety_tolerance: 6,
    };
}

// ---------- 官方 Google 客户端 body（index.js generateGoogleImage L4610-L4623 逐字摘，非 veo） ----------
// aspect_ratio = getClosestAspectRatio(w,h,'google') 结果由调用方计算后传入（纯函数）。
// seed < 0 省略，enhance=undefined 省略，api 空字符串省略，vertex* 空省略。
function googleClientBody(prompt, aspectRatio, negativePrompt, model, enhance, api, seed,
                          vertexAuthMode, vertexRegion, vertexProject) {
    const body = {
        prompt: prompt,
        aspect_ratio: aspectRatio,
        negative_prompt: negativePrompt,
        model: model,
    };
    if (enhance !== undefined) body.enhance = enhance;
    if (String(api || '').trim() !== '') body.api = api;
    if (seed >= 0) body.seed = seed;
    if (String(vertexAuthMode || '').trim() !== '') body.vertexai_auth_mode = vertexAuthMode;
    if (String(vertexRegion || '').trim() !== '') body.vertexai_region = vertexRegion;
    if (String(vertexProject || '').trim() !== '') body.vertexai_express_project_id = vertexProject;
    return body;
}

// ---------- 官方 ZAI 客户端 image 分支 body（index.js generateZaiImage L4688-L4699 逐字摘，不含 while 预处理） ----------
// quality = '' → String('').trim()='' → falsy → 省略
function zaiClientBody(prompt, model, quality, width, height) {
    const body = {
        prompt: prompt,
        model: model,
        size: `${width}x${height}`,
    };
    if (String(quality || '').trim() !== '') body.quality = quality;
    return body;
}

// ---------- 官方 OpenRouter 客户端 body（index.js generateOpenRouterImage L4722-L4730 逐字摘） ----------
function openRouterBody(model, prompt, aspectRatio) {
    return {
        model: model,
        prompt: prompt,
        aspect_ratio: aspectRatio,
    };
}

// ---------- 官方 WorkersAI 客户端 JSON body（index.js generateWorkersAIImage L4745-L4755 逐字摘） ----------
function workersAiClientBody(prompt, negativePrompt, model, width, height, steps, scale, seed, accountId) {
    const body = {
        prompt: prompt,
        negative_prompt: negativePrompt,
        model: model,
        width: width,
        height: height,
        steps: steps,
        scale: scale,
        account_id: accountId,
    };
    if (seed >= 0) body.seed = seed;
    return body;
}

// ---------- 官方 Comfy 占位符替换（index.js generateComfyImageCommon L4231-L4261 逐字摘） ----------
// 搜索串全部含外层双引号 '"%xxx%"'，替换值 = JSON.stringify(...)：
//   字符串 → "…（转义）"；整值数字无小数点（JSON.stringify(7)="7"、JSON.stringify(0.7)="0.7"）。
// seed/denoise/clip_skip 三行官方为内联表达式，此处拆成等价纯函数：
//   - resolveComfySeed：L4235 `settings.seed >= 0 ? settings.seed : Math.round(Math.random()*MAX_SAFE)`
//     （Math.random 注入为 random01 参数以便差分确定性；App 运行时传真随机）
//   - denoise：L4238 `denoising_strength === undefined ? 1.0 : 值`
//   - clip_skip：L4240 `isNaN(clip_skip) ? -1 : -clip_skip`
function resolveComfySeed(seedSetting, random01) {
    return seedSetting >= 0 ? seedSetting : Math.round(random01 * Number.MAX_SAFE_INTEGER);
}

// placeholders 即官方 generateComfyImage（L4304-L4313）/ generateComfyRunPodImage（L4326-L4331）
// 的两份列表；settings 模拟 extension_settings.sd 的对应键。
// 不差分（登记）：comfy_placeholders 自定义替换（官方默认 []，App 无该 UI）、
//   %user_avatar%/%char_avatar%（需 fetch 头像转 base64，接线层行为）。
function replaceComfyWorkflow(workflow, seed, denoisingStrength, clipSkip, settings, placeholders, prompt, negativePrompt, customPlaceholders = []) {
    let w = workflow.replaceAll('"%prompt%"', JSON.stringify(prompt));
    w = w.replaceAll('"%negative_prompt%"', JSON.stringify(negativePrompt));
    w = w.replaceAll('"%seed%"', JSON.stringify(seed));

    const denoiseVal = denoisingStrength === undefined ? 1.0 : denoisingStrength;
    w = w.replaceAll('"%denoise%"', JSON.stringify(denoiseVal));

    const clipSkipVal = isNaN(clipSkip) ? -1 : -clipSkip;
    w = w.replaceAll('"%clip_skip%"', JSON.stringify(clipSkipVal));

    placeholders.forEach(ph => {
        w = w.replaceAll(`"%${ph}%"`, JSON.stringify(settings[ph]));
    });
    // 官方 L4248-L4250：comfy_placeholders 自定义 {find,replace}，replace 已过 substituteParams
    customPlaceholders.forEach(ph => {
        w = w.replaceAll(`"%${ph.find}%"`, JSON.stringify(ph.replace));
    });
    return w;
}

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

// ============ FalAI server cases ============
// 重点锁：image_size 嵌套对象、seed=null（seed ?? null 语义）、clamp 边界、num() 数字语义 3.0→3
add('falai-server-defaults', 'falai-server',
    { prompt: 'a cat', width: 1024, height: 1024, steps: 30, scale: 3.0, seed: 42 },
    falaiServerBody('a cat', 1024, 1024, 30, 3.0, 42));
add('falai-server-seed-null', 'falai-server',
    // seed undefined → null（JS null 序列化保留）
    { prompt: 'x', width: 512, height: 512, steps: 20, scale: 2.0, seed: undefined },
    falaiServerBody('x', 512, 512, 20, 2.0, undefined));
add('falai-server-clamp-bounds', 'falai-server',
    // steps=0 → 1；scale=10 → 5；width=10 → 256；height=9999 → 1440
    { prompt: 'clamp', width: 10, height: 9999, steps: 0, scale: 10, seed: -1 },
    falaiServerBody('clamp', 10, 9999, 0, 10, -1));

// ============ Google client cases ============
add('google-client-full-vertex', 'google-client',
    // vertex 全字段：enhance=true, api=vertexai, seed=42, vertex* 全非空
    { prompt: 'a cat', aspectRatio: '1:1', negativePrompt: 'blurry', model: 'imagegeneration@005',
      enhance: true, api: 'vertexai', seed: 42,
      vertexAuthMode: 'express', vertexRegion: 'us-central1', vertexProject: 'my-proj' },
    googleClientBody('a cat', '1:1', 'blurry', 'imagegeneration@005',
        true, 'vertexai', 42, 'express', 'us-central1', 'my-proj'));
add('google-client-minimal-makersuite', 'google-client',
    // makersuite 最简：enhance=undefined 省略、api='' 省略、seed=-1 <0 省略、vertex* 空省略
    { prompt: 'portrait', aspectRatio: '16:9', negativePrompt: '', model: 'imagen-3.0-generate-002',
      enhance: undefined, api: '', seed: -1,
      vertexAuthMode: '', vertexRegion: '', vertexProject: '' },
    googleClientBody('portrait', '16:9', '', 'imagen-3.0-generate-002',
        undefined, '', -1, '', '', ''));

// ============ ZAI cases ============
add('zai-with-quality', 'zai',
    // quality='hd' → 传
    { prompt: 'a cat', model: 'cogview-3', quality: 'hd', width: 1024, height: 1024 },
    zaiClientBody('a cat', 'cogview-3', 'hd', 1024, 1024));
add('zai-no-quality-empty', 'zai',
    // quality='' → 省略；size 模板字符串 512x768
    { prompt: 'x', model: 'glm-image-4', quality: '', width: 512, height: 768 },
    zaiClientBody('x', 'glm-image-4', '', 512, 768));

// ============ OpenRouter cases ============
add('openrouter-defaults', 'openrouter',
    // aspect_ratio 取 stability 集合
    { model: 'flux-pro', prompt: 'a cat', aspectRatio: '1:1' },
    openRouterBody('flux-pro', 'a cat', '1:1'));
add('openrouter-wide', 'openrouter',
    { model: 'recraft-v3', prompt: 'wide', aspectRatio: '16:9' },
    openRouterBody('recraft-v3', 'wide', '16:9'));

// ============ WorkersAI client cases ============
add('workersai-client-with-seed', 'workersai-client',
    { prompt: 'a cat', negativePrompt: 'blurry', model: 'flux-1-dev',
      width: 1024, height: 1024, steps: 4, scale: 3.5, seed: 77, accountId: 'acct123' },
    workersAiClientBody('a cat', 'blurry', 'flux-1-dev', 1024, 1024, 4, 3.5, 77, 'acct123'));
add('workersai-client-no-seed', 'workersai-client',
    // seed<0 → 省略
    { prompt: 'x', negativePrompt: '', model: 'stable-diffusion-xl-base-1.0',
      width: 768, height: 1024, steps: 20, scale: 7.0, seed: -1, accountId: 'myacct' },
    workersAiClientBody('x', '', 'stable-diffusion-xl-base-1.0', 768, 1024, 20, 7.0, -1, 'myacct'));

// ============ Comfy replaceComfyWorkflow cases ============
// 官方占位符搜索串含外层双引号；模板模拟真实 ComfyUI workflow 的 "键": "%xxx%" 形态。
// seed/denoisingStrength/clipSkip 为调用方已解析值（resolveComfySeed / undefined→1.0 / isNaN→-1 在函数内）。
const TMPL_FULL = '{"3":{"inputs":{"seed":"%seed%","steps":"%steps%","cfg":"%scale%","sampler_name":"%sampler%","scheduler":"%scheduler%","denoise":"%denoise%"},"class_type":"KSampler"},"4":{"inputs":{"ckpt_name":"%model%","vae":"%vae%"},"class_type":"CheckpointLoader"},"5":{"inputs":{"width":"%width%","height":"%height%","text":"%prompt%","negative":"%negative_prompt%","clip_skip":"%clip_skip%"},"class_type":"EmptyLatent"}}';
const COMFY_SETTINGS_DEFAULTS = {
    model: '', vae: '', sampler: 'DDIM', scheduler: 'normal',
    steps: 20, scale: 7, width: 512, height: 512,
};
const PH_COMFY = ['model', 'vae', 'sampler', 'scheduler', 'steps', 'scale', 'width', 'height'];
const PH_RUNPOD = ['steps', 'scale', 'width', 'height'];

add('comfy-replace-full-defaults', 'comfy-replace',
    // 官方默认设置全量替换（denoise 默认 0.7、clip_skip 1 → -1）
    { workflow: TMPL_FULL, prompt: 'a cat', negativePrompt: 'blurry',
      seed: String(resolveComfySeed(42, 0.5)), denoisingStrength: 0.7, clipSkip: 1,
      settings: COMFY_SETTINGS_DEFAULTS, runPod: false },
    { result: replaceComfyWorkflow(TMPL_FULL, resolveComfySeed(42, 0.5), 0.7, 1,
        COMFY_SETTINGS_DEFAULTS, PH_COMFY, 'a cat', 'blurry') });
add('comfy-replace-fractional-and-negative-clipskip', 'comfy-replace',
    // scale 7.5/denoise 0.65 带小数原样（无 .0）；clip_skip 12 → -12 取负
    { workflow: TMPL_FULL, prompt: 'portrait', negativePrompt: '',
      seed: String(777), denoisingStrength: 0.65, clipSkip: 12,
      settings: { ...COMFY_SETTINGS_DEFAULTS, scale: 7.5, sampler: 'euler' }, runPod: false },
    { result: replaceComfyWorkflow(TMPL_FULL, 777, 0.65, 12,
        { ...COMFY_SETTINGS_DEFAULTS, scale: 7.5, sampler: 'euler' }, PH_COMFY, 'portrait', '') });
add('comfy-replace-quote-escape', 'comfy-replace',
    // prompt 含双引号 → JSON.stringify 转义 \"；单引号不转义
    { workflow: TMPL_FULL, prompt: 'say "hi" \\ ok', negativePrompt: "don't",
      seed: String(0), denoisingStrength: 1, clipSkip: NaN,
      settings: { ...COMFY_SETTINGS_DEFAULTS, model: 'flux.safetensors', steps: 1, width: 64, height: 64 },
      runPod: false },
    { result: replaceComfyWorkflow(TMPL_FULL, 0, 1, NaN,
        { ...COMFY_SETTINGS_DEFAULTS, model: 'flux.safetensors', steps: 1, width: 64, height: 64 },
        PH_COMFY, 'say "hi" \\ ok', "don't") });
add('comfy-replace-runpod-subset', 'comfy-replace',
    // runpod placeholders 仅 4 项：%model%/%vae%/%sampler%/%scheduler% 保持原样
    { workflow: TMPL_FULL, prompt: 'rp', negativePrompt: 'ng',
      seed: String(5), denoisingStrength: 0.7, clipSkip: 1,
      settings: { ...COMFY_SETTINGS_DEFAULTS, steps: 30, scale: 6.5 }, runPod: true },
    { result: replaceComfyWorkflow(TMPL_FULL, 5, 0.7, 1,
        { ...COMFY_SETTINGS_DEFAULTS, steps: 30, scale: 6.5 }, PH_RUNPOD, 'rp', 'ng') });
add('comfy-replace-repeated-occurrence', 'comfy-replace',
    // 同一占位符出现两次全部替换（replaceAll 全局语义）
    { workflow: '"%prompt%" and "%prompt%" cfg "%scale%" scale "%scale%"', prompt: 'dup', negativePrompt: '',
      seed: String(9), denoisingStrength: 0.8, clipSkip: 2,
      settings: { ...COMFY_SETTINGS_DEFAULTS, scale: 4 }, runPod: false },
    { result: replaceComfyWorkflow('"%prompt%" and "%prompt%" cfg "%scale%" scale "%scale%"', 9, 0.8, 2,
        { ...COMFY_SETTINGS_DEFAULTS, scale: 4 }, PH_COMFY, 'dup', '') });
add('comfy-replace-custom-placeholders', 'comfy-replace',
    // comfy_placeholders 自定义占位符：标准组之后按序替换；replace 含引号/换行走 JSON 转义
    { workflow: '{"1":{"text":"%prompt%","custom":"%my_lora%","other":"%aspect%"},"2":{"custom":"%my_lora%"}}',
      prompt: 'p', negativePrompt: 'n', seed: String(3), denoisingStrength: 0.7, clipSkip: 1,
      settings: COMFY_SETTINGS_DEFAULTS, runPod: false,
      customPlaceholders: [
        { find: 'my_lora', replace: 'lora "x"\\n' },
        { find: 'aspect', replace: '9:16' },
      ] },
    { result: replaceComfyWorkflow(
        '{"1":{"text":"%prompt%","custom":"%my_lora%","other":"%aspect%"},"2":{"custom":"%my_lora%"}}',
        3, 0.7, 1, COMFY_SETTINGS_DEFAULTS, PH_COMFY, 'p', 'n',
        [{ find: 'my_lora', replace: 'lora "x"\\n' }, { find: 'aspect', replace: '9:16' }]) });
add('comfy-replace-custom-empty-list', 'comfy-replace',
    // 无自定义占位符 → 与原行为一致（默认参数路径）
    { workflow: TMPL_FULL, prompt: 'plain', negativePrompt: '',
      seed: String(11), denoisingStrength: 1.0, clipSkip: 0,
      settings: COMFY_SETTINGS_DEFAULTS, runPod: true },
    { result: replaceComfyWorkflow(TMPL_FULL, 11, 1.0, 0, COMFY_SETTINGS_DEFAULTS, PH_RUNPOD, 'plain', '') });

// ============ Comfy resolveComfySeed cases ============
// 官方 L4235：settings.seed >= 0 ? seed : Math.round(random01 * Number.MAX_SAFE_INTEGER)
add('comfy-seed-nonnegative-passthrough', 'comfy-seed-resolve',
    // seed>=0 原样返回，random 不参与
    { seed: String(42), random01: '0.9999' },
    { result: String(resolveComfySeed(42, 0.9999)) });
add('comfy-seed-random-half', 'comfy-seed-resolve',
    // -1 → Math.round(0.5 * 9007199254740991) = 4503599627370496（半值向上，JS/Java 一致）
    { seed: String(-1), random01: '0.5' },
    { result: String(resolveComfySeed(-1, 0.5)) });
add('comfy-seed-random-zero', 'comfy-seed-resolve',
    { seed: String(-5), random01: '0' },
    { result: String(resolveComfySeed(-5, 0)) });
add('comfy-seed-random-one-max-safe', 'comfy-seed-resolve',
    // random01=1 → Number.MAX_SAFE_INTEGER 本身
    { seed: String(-1), random01: '1' },
    { result: String(resolveComfySeed(-1, 1)) });

// ============ NovelAI getNovelParams cases ============
// 官方 stable-diffusion/index.js L4002-L4059 逐字（steps=min(设置,50)；ddim/v4 模型强制关 SMEA；
// anlas guard 尺寸/步数钳制）。
function getNovelParams(steps, width, height, sampler, model, novelSm, novelSmDyn, anlasGuard) {
    steps = Math.min(steps, 50);
    let sm = novelSm;
    let sm_dyn = novelSmDyn;

    if (sampler === 'ddim' ||
        ['nai-diffusion-4-curated-preview', 'nai-diffusion-4-full'].includes(model)) {
        sm = false;
        sm_dyn = false;
    }

    if (!anlasGuard) {
        return { steps, width, height, sm, sm_dyn };
    }

    const MAX_STEPS = 28;
    const MAX_PIXELS = 1024 * 1024;

    if (width * height > MAX_PIXELS) {
        const ratio = Math.sqrt(MAX_PIXELS / (width * height));

        let newWidth = Math.round(width * ratio);
        let newHeight = Math.round(height * ratio);

        if (newWidth % 64 !== 0) {
            newWidth = newWidth - newWidth % 64;
        }
        if (newHeight % 64 !== 0) {
            newHeight = newHeight - newHeight % 64;
        }
        while (newWidth * newHeight > MAX_PIXELS) {
            if (newWidth > newHeight) {
                newWidth -= 64;
            } else {
                newHeight -= 64;
            }
        }
        width = newWidth;
        height = newHeight;
    }

    if (steps > MAX_STEPS) {
        steps = MAX_STEPS;
    }

    return { steps, width, height, sm, sm_dyn };
}

const np = (o) => ({ steps: o.steps, width: o.width, height: o.height, sm: o.sm, smDyn: o.sm_dyn });
add('novel-params-default-no-guard', 'novel-params',
    // 默认设置：仅 steps min50；sm 原样透传
    { steps: 20, width: 512, height: 768, sampler: 'euler', model: 'nai-diffusion-3',
      novelSm: false, novelSmDyn: false, anlasGuard: false },
    np(getNovelParams(20, 512, 768, 'euler', 'nai-diffusion-3', false, false, false)));
add('novel-params-steps-clamp-50', 'novel-params',
    // 步数超 50 截断
    { steps: 150, width: 832, height: 1216, sampler: 'k_euler_ancestral', model: 'nai-diffusion-3',
      novelSm: true, novelSmDyn: true, anlasGuard: false },
    np(getNovelParams(150, 832, 1216, 'k_euler_ancestral', 'nai-diffusion-3', true, true, false)));
add('novel-params-ddim-forces-sm-off', 'novel-params',
    // ddim → sm/sm_dyn 强制 false
    { steps: 28, width: 1024, height: 1024, sampler: 'ddim', model: 'nai-diffusion-3',
      novelSm: true, novelSmDyn: true, anlasGuard: false },
    np(getNovelParams(28, 1024, 1024, 'ddim', 'nai-diffusion-3', true, true, false)));
add('novel-params-v4-full-forces-sm-off', 'novel-params',
    // nai-diffusion-4-full 模型 → 强制关；4-curated-preview 同规则
    { steps: 30, width: 512, height: 512, sampler: 'euler', model: 'nai-diffusion-4-full',
      novelSm: true, novelSmDyn: true, anlasGuard: false },
    np(getNovelParams(30, 512, 512, 'euler', 'nai-diffusion-4-full', true, true, false)));
add('novel-params-anlas-guard-large-shrink', 'novel-params',
    // 1536x1536=2359296 > 1048576：ratio=sqrt(1048576/2359296)，取整到 64 后仍可能超 → while 下调
    { steps: 40, width: 1536, height: 1536, sampler: 'euler', model: 'nai-diffusion-3',
      novelSm: false, novelSmDyn: false, anlasGuard: true },
    np(getNovelParams(40, 1536, 1536, 'euler', 'nai-diffusion-3', false, false, true)));
add('novel-params-anlas-guard-small-untouched', 'novel-params',
    // 832x1216=1011712 ≤ 1048576：尺寸不动，仅 steps>28 截为 28
    { steps: 45, width: 832, height: 1216, sampler: 'euler', model: 'nai-diffusion-3',
      novelSm: true, novelSmDyn: true, anlasGuard: true },
    np(getNovelParams(45, 832, 1216, 'euler', 'nai-diffusion-3', true, true, true)));
add('novel-params-anlas-guard-exact-multiple', 'novel-params',
    // 2048x1408 恰好 ratio 取整后仍需 while 循环下调的边界形态
    { steps: 10, width: 2048, height: 1408, sampler: 'euler', model: 'nai-diffusion-2',
      novelSm: false, novelSmDyn: false, anlasGuard: true },
    np(getNovelParams(10, 2048, 1408, 'euler', 'nai-diffusion-2', false, false, true)));

// ============ NovelAI calculateSkipCfgAboveSigma cases ============
// 官方 src/endpoints/novelai.js L120-L128（魔数 19 / V4.5 为 58；参考像素 1011712=832*1216）。
function calculateSkipCfgAboveSigma(width, height, modelName) {
    const magicConstant = modelName?.includes('nai-diffusion-4-5')
        ? 58
        : 19;
    const pixelCount = width * height;
    const ratio = pixelCount / 1011712;
    return Math.pow(ratio, 0.5) * magicConstant;
}
add('skip-cfg-sigma-reference-size-v3', 'skip-cfg-sigma',
    // 参考尺寸 832*1216、V3 模型 → 魔数 19 本身
    { width: 832, height: 1216, model: 'nai-diffusion-3' },
    { result: calculateSkipCfgAboveSigma(832, 1216, 'nai-diffusion-3') });
add('skip-cfg-sigma-v45-magic-58', 'skip-cfg-sigma',
    // 模型名含 nai-diffusion-4-5 → 58（includes 子串匹配）
    { width: 832, height: 1216, model: 'nai-diffusion-4-5-full' },
    { result: calculateSkipCfgAboveSigma(832, 1216, 'nai-diffusion-4-5-full') });
add('skip-cfg-sigma-square-sdxl', 'skip-cfg-sigma',
    // 1024x1024 → sqrt(1048576/1011712)*19 非整浮点
    { width: 1024, height: 1024, model: 'nai-diffusion' },
    { result: calculateSkipCfgAboveSigma(1024, 1024, 'nai-diffusion') });
add('skip-cfg-sigma-null-model-defaults-19', 'skip-cfg-sigma',
    // 官方 ?? 'nai-diffusion' 兜底前 includes 判定对 null 安全（?. 可选链）
    { width: 512, height: 512, model: null },
    { result: calculateSkipCfgAboveSigma(512, 512, null) });

// ============ DrawThings body（generateDrawthingsImage index.js L3918-L3944） ============
// url/auth 两键由服务端 spread 后 delete，差分只覆盖设置键；seed>=0 原样否则 undefined（stringify 省略）
function drawthingsBody(prompt, negativePrompt, sampler, steps, scale, width, height,
                        restoreFaces, enableHr, denoisingStrength, clipSkip, hrScale, seed) {
    return JSON.stringify({
        prompt: prompt,
        negative_prompt: negativePrompt,
        sampler_name: sampler,
        steps: steps,
        cfg_scale: scale,
        width: width,
        height: height,
        restore_faces: !!restoreFaces,
        enable_hr: !!enableHr,
        denoising_strength: denoisingStrength,
        clip_skip: clipSkip,
        upscaler_scale: hrScale,
        seed: seed >= 0 ? seed : undefined,
    });
}
const DRAWTHINGS_DEFAULTS = ['DDIM', 20, 7, 512, 512, false, false, 0.7, 1, 1.0];
function drawthingsArgs(prompt, negativePrompt, sampler, steps, scale, width, height,
                        restoreFaces, enableHr, denoisingStrength, clipSkip, hrScale, seed) {
    return {
        prompt, negativePrompt, sampler, steps, scale, width, height,
        restoreFaces, enableHr, denoisingStrength, clipSkip, hrScale, seed,
    };
}
add('drawthings-defaults-seed-random', 'drawthings-body',
    // 官方默认值 + seed=-1 → 键省略
    (() => { const a = [ 'a cat', 'lowres', ...DRAWTHINGS_DEFAULTS, -1 ];
        return drawthingsArgs(...a); })(),
    { result: drawthingsBody('a cat', 'lowres', ...DRAWTHINGS_DEFAULTS, -1) });
add('drawthings-seed-fixed-hr-scale', 'drawthings-body',
    // seed 固定原样、hr_scale=1.5 → upscaler_scale=1.5、restore/enable_hr 双开
    drawthingsArgs('portrait, detailed', 'blurry', 'Euler a', 28, 6.5, 768, 512,
        true, true, 0.55, 2, 1.5, 123456789),
    { result: drawthingsBody('portrait, detailed', 'blurry', 'Euler a', 28, 6.5, 768, 512,
        true, true, 0.55, 2, 1.5, 123456789) });
add('drawthings-denoising-integer-one', 'drawthings-body',
    // denoising_strength=1.0 → JSON.stringify 输出 "1"（整数化数字语义）
    drawthingsArgs('x', '', 'DDIM', 20, 7, 512, 512, false, false, 1.0, 1, 1.0, 7),
    { result: drawthingsBody('x', '', 'DDIM', 20, 7, 512, 512, false, false, 1.0, 1, 1.0, 7) });

writeFileSync(outFile, JSON.stringify({ cases }, null, 2) + '\n');
console.log(`imagegen-services fixtures: ${cases.length} cases -> ${outFile}`);
