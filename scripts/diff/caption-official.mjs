#!/usr/bin/env node
// Caption 官方纯函数（摘自 caption/index.js）：
// - resolvePrompt(externalPrompt, settings.prompt, prompt_ask 输入) → prompt
//   （prompt_ask 分支为 UI 交互，差分仅覆盖 prompt 解析链：external || settings || PROMPT_DEFAULT）
// - wrapCaptionTemplate(template, caption, {user, char, dynamicMacros}) → 替换文本
//   （含 poka-yoke：无 {{caption}} 则追加）
// - captionMultimodal messages 结构：官方 getMultimodalCaption(base64Img, prompt) 不走 fixed system。
// - 视频 MIME 判定：isVideoCaptioningAvailable 失败时拒（此处对拍 isVideo）

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, '../../engine/src/test/resources/diff/caption.json');

const PROMPT_DEFAULT = "What's in this image?";
const TEMPLATE_DEFAULT = '[{{user}} sends {{char}} a picture that contains: {{caption}}]';
const VIDEO_EXT = ['mp4','webm','mov','avi','mkv','flv','wmv','m4v'];

// 官方：prompt 优先级：externalPrompt || settings.prompt || PROMPT_DEFAULT
// （prompt_ask 仅决定是否弹框，resolved prompt 规则不变）
function resolvePrompt(external, settingsPrompt) {
    return external || settingsPrompt || PROMPT_DEFAULT;
}

// 官方 wrapCaptionTemplate：
// - poka-yoke：无 {{caption}} 时追加 " {{caption}}"
// - substituteParams(template, { dynamicMacros: { caption } })；这里 simulate：
//   1. {{user}} → user, {{char}} → char
//   2. 最后替换 {{caption}}（这就是 dynamicMacros 的效果：caption 作为最后注入不参与宏展开冲突）
function wrapCaptionTemplate(template, caption, user, char) {
    if (!/{{caption}}/i.test(template)) template += ' {{caption}}';
    let s = template;
    s = s.replaceAll('{{user}}', user).replaceAll('{{User}}', user);
    s = s.replaceAll('{{char}}', char).replaceAll('{{Char}}', char);
    s = s.replace(/\{\{caption\}\}/gi, caption);
    return s;
}

// 官方 captionMultimodal messages：getMultimodalCaption(base64Img, prompt)
// 实际为调用当前 mainApiProvider 的 sendMessage（[image, prompt]，无固定 system）。
// 我们仅记录 messages 结构期望 = [role:user content:prompt images:[dataUrl]]。
function multimodalRequest(prompt, dataUrl) {
    return [{ role: 'user', content: prompt, images: [dataUrl] }];
}

function isVideo(filenameOrUrl) {
    const ext = filenameOrUrl.split('?')[0].split('#')[0].split('.').pop() || '';
    return VIDEO_EXT.includes(ext.toLowerCase());
}

function cases(){
    const out = [];
    let id = 0;
    // prompt resolution 6 例
    out.push({id:id++,name:'cap-prompt-external-wins',    _tag:'prompt', input:{external:'Say hi', sp:''},        expected: resolvePrompt('Say hi','')});
    out.push({id:id++,name:'cap-prompt-settings-fallback',_tag:'prompt', input:{external:null, sp:'Custom?'},    expected: resolvePrompt(null,'Custom?')});
    out.push({id:id++,name:'cap-prompt-default',          _tag:'prompt', input:{external:null, sp:''},          expected: resolvePrompt(null,'')});
    out.push({id:id++,name:'cap-prompt-empty-string-x',   _tag:'prompt', input:{external:'', sp:''},            expected: resolvePrompt('','')});
    out.push({id:id++,name:'cap-prompt-external-empty-trumps',_tag:'prompt', input:{external:'', sp:'SP'},       expected: resolvePrompt('','SP')});
    out.push({id:id++,name:'cap-prompt-all-three',        _tag:'prompt', input:{external:'A', sp:'B'},          expected: resolvePrompt('A','B')});
    // wrap template 6 例
    out.push({id:id++,name:'cap-wrap-standard',        _tag:'wrap', input:{template:TEMPLATE_DEFAULT, caption:'a cat',user:'U',char:'C'}, expected: wrapCaptionTemplate(TEMPLATE_DEFAULT,'a cat','U','C')});
    out.push({id:id++,name:'cap-wrap-poka-yoke',       _tag:'wrap', input:{template:'Caption:',caption:'dog',user:'U',char:'C'}, expected: wrapCaptionTemplate('Caption:','dog','U','C')});
    out.push({id:id++,name:'cap-wrap-case-insensitive-caption',_tag:'wrap', input:{template:'[{{CAPTION}}]',caption:'fish',user:'U',char:'C'}, expected: wrapCaptionTemplate('[{{CAPTION}}]','fish','U','C')});
    out.push({id:id++,name:'cap-wrap-user-char-macros',_tag:'wrap', input:{template:'{{user}}/{{char}}:{{caption}}',caption:'x',user:'Me',char:'Alice'}, expected: wrapCaptionTemplate('{{user}}/{{char}}:{{caption}}','x','Me','Alice')});
    out.push({id:id++,name:'cap-wrap-caption-trim-not-trimmed',_tag:'wrap', input:{template:'{{caption}}',caption:'  hello  ',user:'U',char:'C'}, expected: wrapCaptionTemplate('{{caption}}','  hello  ','U','C')});
    out.push({id:id++,name:'cap-wrap-poka-empty-tpl',  _tag:'wrap', input:{template:'',caption:'hi',user:'U',char:'C'}, expected: wrapCaptionTemplate('','hi','U','C')});
    // multimodal request 3 例
    out.push({id:id++,name:'cap-mm-no-system',          _tag:'mm',   input:{prompt:'Describe',url:'data:image/png;base64,xx'}, expected: multimodalRequest('Describe','data:image/png;base64,xx')});
    out.push({id:id++,name:'cap-mm-default-prompt',     _tag:'mm',   input:{prompt:PROMPT_DEFAULT,url:'data:image/jpeg;base64,yy'}, expected: multimodalRequest(PROMPT_DEFAULT,'data:image/jpeg;base64,yy')});
    // isVideo 3 例
    out.push({id:id++,name:'cap-video-ext',             _tag:'video',input:'selfie.mp4',             expected: isVideo('selfie.mp4')});
    out.push({id:id++,name:'cap-not-video-ext',         _tag:'video',input:'cat.png',               expected: isVideo('cat.png')});
    out.push({id:id++,name:'cap-video-webm-query',      _tag:'video',input:'a.webm?x=1',            expected: isVideo('a.webm?x=1')});
    return out;
}

function main(){
    const fixture = { generatedAt: new Date().toISOString(),
        source: 'caption/index.js: PROMPT_DEFAULT + prompt resolve chain + wrapCaptionTemplate(poka-yoke) + isVideo captioningAvailable gate + captionMultimodal messages',
        cases: cases() };
    writeFileSync(OUT, JSON.stringify(fixture, null, 2));
    console.log('caption fixtures:', fixture.cases.length, '→', OUT);
}
main();
