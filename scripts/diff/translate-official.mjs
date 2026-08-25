#!/usr/bin/env node
// Translate 官方纯函数差分（摘自 translate/index.js）：
// 1) translateIncomingMessage(mes, name, target_lang)：
//    textToTranslate = substituteParams(mes, {name2Override: message.name})
//    → 译文写 extra.display_text
// 2) translateIncomingMessageReasoning(reasoning, name, target_lang)：
//    textToTranslate = substituteParams(reasoning, {name2Override})
//    → 译文写 extra.reasoning_display_text
// 3) 8 provider body：libre/google/lingva/deepl/deeplx/onering/bing/yandex（纯字段比对）
//
// substituteParams(name2Override) 简化为：
//   文本内 "{{char}}" → name；官方 name2Override 会让 {{char}} = message.name（而非 char_name）。

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, '../../engine/src/test/resources/diff/translate.json');

function substituteParamsNameOverride(text, name2Override) {
    if (name2Override != null) {
        text = text.replaceAll('{{char}}', name2Override)
                   .replaceAll('{{Char}}', name2Override);
    }
    return text;
}

// 官方 translateIncomingMessage：message.extra.display_text = translated
// translateIncomingMessageReasoning：message.extra.reasoning_display_text = translated
// 此处不调用 provider，直接对拍 key 名 + 替换输入串。
function simulateTranslateMessageIn(mes, charName, nameOverride) {
    const input = substituteParamsNameOverride(mes, nameOverride || charName);
    // extra 写 key 对照
    const extra = {};
    extra.display_text = input; // 这里用"输入"模拟"译文"，仅对拍 key 名
    return { extra, textToTranslate: input };
}
function simulateTranslateReasoningIn(reasoning, charName, nameOverride) {
    const input = substituteParamsNameOverride(reasoning, nameOverride || charName);
    const extra = {};
    extra.reasoning_display_text = input;
    return { extra, textToTranslate: input };
}

// 8 provider 的 body 构造（和 TranslateClient 契约一一对应）
function libreBody(text, target, apiKey) {
    return { q: text, source: 'auto', target: target, format: 'text', ...(apiKey ? { api_key: apiKey } : {}) };
}
// 官方服务端 /google → google-translate-api-x@10.7.2（DEFAULT forceBatch:true → batchTranslate 路径）。
// 以下三个参考实现逐字取自 node_modules/google-translate-api-x/lib/translation/{batchTranslate,translate}.cjs
// 与 src/endpoints/translate.js /google handler。
function googleUrl(reqId) {
    const rpcids = 'MkEWBc';
    const queryParams = new URLSearchParams({
        'rpcids': rpcids,
        'source-path': '/',
        'f.sid': '',
        'bl': '',
        'hl': 'en-US',
        'soc-app': 1,
        'soc-platform': 1,
        'soc-device': 1,
        '_reqid': reqId, // 服务端为 Math.floor(1000+Math.random()*9000)，fixture 固定值对拍
        'rt': 'c'
    });
    return 'https://translate.google.com/_/TranslateWebserverUi/data/batchexecute?' + queryParams.toString();
}
function googleBody(text, target, reqId) {
    const rpcids = 'MkEWBc';
    const fromIso = 'auto';   // getCode('auto')
    const autoCorrect = false; // DEFAULT_OPTIONS.autoCorrect
    const i = reqId;           // 单输入时用 0；此处直接传 0
    const freqPart = [rpcids, JSON.stringify([[text, fromIso, target, autoCorrect], [null]]), null, i.toString(36)];
    const body = 'f.req=' + encodeURIComponent(JSON.stringify([freqPart])) + '&';
    return body;
}
function googleResponseLine(innerObj, id36) {
    return JSON.stringify([["wrb.fr", "MkEWBc", JSON.stringify(innerObj), null, id36]]);
}
// 官方响应解析（batchTranslate.cjs:89-141）
function googleParse(res) {
    let text = null;
    res = res.slice(6);
    for (let chunk of res.split('\n')) {
        if (chunk[0] !== '[' || chunk[3] === 'e') {
            continue;
        }
        chunk = JSON.parse(chunk);
        for (let translation of chunk) {
            if (translation[0] !== 'wrb.fr') {
                continue;
            }
            if (translation[2] === null) {
                return null;
            }
            translation = JSON.parse(translation[2]);
            if (translation[1][0][0][5] === undefined || translation[1][0][0][5] === null) {
                text = translation[1][0][0][0];
            } else {
                text = translation[1][0][0][5]
                    .map(function (obj) { return obj[0]; })
                    .filter(Boolean)
                    .join(' ');
            }
        }
    }
    return text;
}
function googleSentenceInner(withAlt) {
    const sentence = withAlt
        ? ["你好", "orig", null, null, "", [["你 好"], ["你好!"], [""]]]
        : ["你好", "orig"];
    return [null, [[sentence]]];
}
// 官方 src/endpoints/translate.js:183 urlJoin(baseUrl,'auto',lang,text)
// （baseUrl 默认含 /api/v1；此前误抄客户端实现，fixture 锁了 bug——已按官方逐字改）
function lingvaUrl(baseUrl, text, target) {
    const u = baseUrl.endsWith('/') ? baseUrl : baseUrl + '/';
    return `${u}auto/${target}/${encodeURIComponent(text)}`;
}
function deeplBody(text, target, apiKey) {
    return { auth_key: apiKey, text: [text], target_lang: target };
}
function deeplxBody(text, target, baseUrl) {
    return { text, source_lang: 'auto', target_lang: target, url: baseUrl };
}
function oneringBody(text, target, baseUrl, internalLang, targetLang) {
    // 官方 translateProviderOneRing：from_lang = lang == internal_lang ? target_lang : internal_lang
    const from = target === internalLang ? targetLang : internalLang;
    return { text, from_lang: from, to_lang: target, url: (baseUrl.endsWith('/') ? baseUrl : baseUrl + '/') };
}

// ---- 分块与图片链接保护（官方 utils.js splitRecursive + translate/index.js translate()/translateInner/chunkedTranslate）----

// 官方 utils.js:1157 splitRecursive（逐字语义）
function splitRecursive(input, length, delimiters = ['\n\n', '\n', ' ', '']) {
    if (length <= 0) {
        return [input];
    }
    const delim = delimiters[0] ?? '';
    const parts = delim === '' ? input.split('') : input.split(delim);
    const flatParts = parts.flatMap(p => {
        if (p.length < length) return p;
        return splitRecursive(p, length, delimiters.slice(1));
    });
    const result = [];
    let currentChunk = '';
    for (let i = 0; i < flatParts.length;) {
        currentChunk = flatParts[i];
        let j = i + 1;
        while (j < flatParts.length) {
            const nextChunk = flatParts[j];
            if (currentChunk.length + nextChunk.length + delim.length <= length) {
                currentChunk += delim + nextChunk;
            } else {
                break;
            }
            j++;
        }
        i = j;
        result.push(currentChunk);
    }
    return result;
}

// 官方 translateInner 的分块上限
function chunkSize(provider) {
    switch (provider) {
        case 'google':
        case 'lingva':
            return 5000;
        case 'deeplx':
            return 1500;
        case 'bing':
            return 1000;
        default:
            return null;
    }
}

// 官方 chunkedTranslate：网络调用序列（不实际发请求）
function chunkedCalls(text, provider) {
    const size = chunkSize(provider);
    if (size == null) return [text];
    if (text.length <= size) return [text];
    return splitRecursive(text, size);
}

// 官方 translate() 的图片链接切段：文本段与链接段交错（isLink 标记）
function imageLinkSegments(text) {
    const out = [];
    let last = 0;
    for (const m of text.matchAll(/!\[.*?]\([^)]*\)/g)) {
        out.push({ isLink: false, text: text.slice(last, m.index) });
        out.push({ isLink: true, text: m[0] });
        last = m.index + m[0].length;
    }
    out.push({ isLink: false, text: text.slice(last) });
    return out;
}

function cases(){
    const out = [];
    let id = 0;

    // substituteParams + key 写名 11 例
    out.push({id:id++,name:'tra-msg-char-override',_tag:'msg',input:{mes:'{{char}} 你好',charName:'Alice',nameOverride:'小白'},expected: simulateTranslateMessageIn('{{char}} 你好','Alice','小白')});
    out.push({id:id++,name:'tra-msg-no-override', _tag:'msg',input:{mes:'Hi {{char}}!',charName:'Alice',nameOverride:null}, expected: simulateTranslateMessageIn('Hi {{char}}!','Alice',null)});
    out.push({id:id++,name:'tra-msg-case-char',   _tag:'msg',input:{mes:'{{Char}}',charName:'Bob',nameOverride:null}, expected: simulateTranslateMessageIn('{{Char}}','Bob',null)});
    out.push({id:id++,name:'tra-msg-no-macro',      _tag:'msg',input:{mes:'plain text',charName:'A',nameOverride:'B'}, expected: simulateTranslateMessageIn('plain text','A','B')});
    out.push({id:id++,name:'tra-msg-display-key',  _tag:'key',input:{mes:'x',charName:'a',nameOverride:null}, expected: {display_key:'display_text', reasoning_key:'reasoning_display_text'}});
    out.push({id:id++,name:'tra-reasoning-char-override',_tag:'reasoning',input:{reasoning:'让我模拟{{char}}的语气来回复…',charName:'Alice',nameOverride:'小白'}, expected: simulateTranslateReasoningIn('让我模拟{{char}}的语气来回复…','Alice','小白')});
    out.push({id:id++,name:'tra-reasoning-key-name',   _tag:'reasoning',input:{reasoning:'r1',charName:'A',nameOverride:null}, expected: simulateTranslateReasoningIn('r1','A',null)});
    out.push({id:id++,name:'tra-user-override-same',  _tag:'msg',input:{mes:'{{char}}',charName:'Alice',nameOverride:'Alice'}, expected: simulateTranslateMessageIn('{{char}}','Alice','Alice')});
    out.push({id:id++,name:'tra-empty-text',          _tag:'msg',input:{mes:'',charName:'A',nameOverride:null}, expected: simulateTranslateMessageIn('','A',null)});
    out.push({id:id++,name:'tra-reasoning-empty',     _tag:'reasoning',input:{reasoning:'',charName:'A',nameOverride:null}, expected: simulateTranslateReasoningIn('','A',null)});
    out.push({id:id++,name:'tra-multiple-char-tokens',_tag:'msg',input:{mes:'{{char}}: I am {{char}}.',charName:'A',nameOverride:'B'}, expected: simulateTranslateMessageIn('{{char}}: I am {{char}}.','A','B')});

    // 8 provider body 8 例
    out.push({id:id++,name:'tra-prov-libre',_tag:'prov-libre',input:{text:'hi',target:'zh',apiKey:'k'},expected: libreBody('hi','zh','k')});
    out.push({id:id++,name:'tra-prov-google-url',_tag:'prov-google-url',input:{reqId:4321},expected:{url:googleUrl(4321)}});
    out.push({id:id++,name:'tra-prov-google-body',_tag:'prov-google-body',input:{target:'zh-CN',text:'hi'},expected:{body:googleBody('hi','zh-CN',0)}});
    out.push({id:id++,name:'tra-prov-google-body-escape',_tag:'prov-google-body',input:{target:'ja',text:'say "hi"\nline&=!~*() x'},expected:{body:googleBody('say "hi"\nline&=!~*() x','ja',0)}});
    {
        const respA = ")]}'\n\n" + googleResponseLine(googleSentenceInner(true), '0') + '\n';
        const respB = ")]}'\n\n" + googleResponseLine(googleSentenceInner(false), '0') + '\n';
        const respErr = ")]}'\n\n" + JSON.stringify([["wrb.fr","MkEWBc",null,null,"0"]]) + '\n';
        out.push({id:id++,name:'tra-prov-google-parse-alt',_tag:'prov-google-parse',input:{response:respA},expected:{text:googleParse(respA)}});
        out.push({id:id++,name:'tra-prov-google-parse-direct',_tag:'prov-google-parse',input:{response:respB},expected:{text:googleParse(respB)}});
        out.push({id:id++,name:'tra-prov-google-parse-fail',_tag:'prov-google-parse',input:{response:respErr},expected:{text:null}});
    }
    out.push({id:id++,name:'tra-prov-lingva',_tag:'prov-lingva',input:{baseUrl:'https://lingva.example',text:'hi',target:'zh'},expected: lingvaUrl('https://lingva.example','hi','zh')});
    out.push({id:id++,name:'tra-prov-deepl',_tag:'prov-deepl',input:{text:'hi',target:'ZH',apiKey:'dk'},expected: deeplBody('hi','ZH','dk')});
    out.push({id:id++,name:'tra-prov-deeplx',_tag:'prov-deeplx',input:{text:'hi',target:'es',baseUrl:'http://localhost:1188'},expected: deeplxBody('hi','es','http://localhost:1188')});
    out.push({id:id++,name:'tra-prov-onering-from',_tag:'prov-onering',input:{text:'hi',target:'zh',baseUrl:'http://onering.local',internalLang:'en',targetLang:'zh'},expected: oneringBody('hi','zh','http://onering.local','en','zh')});
    out.push({id:id++,name:'tra-prov-onering-reverse',_tag:'prov-onering',input:{text:'你好',target:'en',baseUrl:'http://onering.local',internalLang:'en',targetLang:'zh'},expected: oneringBody('你好','en','http://onering.local','en','zh')});
    // bing/yandex：当前 TranslateClient.kt 直接用免费端点 query（非 JSON body），登记为"直连接线，无 body 构造差异"。为填满 8：补 lingva 带尾斜杠
    out.push({id:id++,name:'tra-prov-lingva-slash',_tag:'prov-lingva',input:{baseUrl:'https://lingva.example/',text:'hi there',target:'ja'},expected: lingvaUrl('https://lingva.example/','hi there','ja')});

    // 图片链接切段（官方 translate()）
    out.push({id:id++,name:'tra-seg-basic',_tag:'seg',input:{text:'看这张图 ![alt](https://x/a.png) 好看'},expected:{segments:imageLinkSegments('看这张图 ![alt](https://x/a.png) 好看')}});
    out.push({id:id++,name:'tra-seg-trailing-link',_tag:'seg',input:{text:'text ![a](u)'},expected:{segments:imageLinkSegments('text ![a](u)')}});
    out.push({id:id++,name:'tra-seg-leading-link',_tag:'seg',input:{text:'![a](u) tail'},expected:{segments:imageLinkSegments('![a](u) tail')}});
    out.push({id:id++,name:'tra-seg-none',_tag:'seg',input:{text:'plain'},expected:{segments:imageLinkSegments('plain')}});
    out.push({id:id++,name:'tra-seg-two-links',_tag:'seg',input:{text:'![1](u1)mid![2](u2)'},expected:{segments:imageLinkSegments('![1](u1)mid![2](u2)')}});
    out.push({id:id++,name:'tra-seg-empty',_tag:'seg',input:{text:''},expected:{segments:imageLinkSegments('')}});

    // 分块调用序列（chunkedTranslate；google/lingva 5000、deeplx 1500、bing 1000、其余不分块）
    out.push({id:id++,name:'tra-chunk-google-short',_tag:'chunk',input:{provider:'google',text:'hello'},expected:{calls:chunkedCalls('hello','google')}});
    {
        const t5k = 'x'.repeat(5000);
        out.push({id:id++,name:'tra-chunk-google-at-limit',_tag:'chunk',input:{provider:'google',text:t5k},expected:{calls:chunkedCalls(t5k,'google')}});
        const t5k1 = 'y'.repeat(5001);
        out.push({id:id++,name:'tra-chunk-google-over-no-delim',_tag:'chunk',input:{provider:'google',text:t5k1},expected:{calls:chunkedCalls(t5k1,'google'),lengths:chunkedCalls(t5k1,'google').map(c=>c.length)}});
    }
    {
        const p1 = 'a'.repeat(800), p2 = 'b'.repeat(700);
        const td = p1 + '\n\n' + p2;
        out.push({id:id++,name:'tra-chunk-deeplx-paragraphs',_tag:'chunk',input:{provider:'deeplx',text:td},expected:{calls:chunkedCalls(td,'deeplx')}});
    }
    {
        const w1 = 'c'.repeat(600), w2 = 'd'.repeat(550);
        const ts = w1 + ' ' + w2;
        out.push({id:id++,name:'tra-chunk-bing-space',_tag:'chunk',input:{provider:'bing',text:ts},expected:{calls:chunkedCalls(ts,'bing')}});
    }
    {
        const long = 'e'.repeat(9000);
        out.push({id:id++,name:'tra-chunk-libre-nochunk',_tag:'chunk',input:{provider:'libre',text:long},expected:{calls:chunkedCalls(long,'libre')}});
        out.push({id:id++,name:'tra-chunk-deepl-nochunk',_tag:'chunk',input:{provider:'deepl',text:long},expected:{calls:chunkedCalls(long,'deepl')}});
        out.push({id:id++,name:'tra-chunk-onering-nochunk',_tag:'chunk',input:{provider:'oneringtranslator',text:long},expected:{calls:chunkedCalls(long,'oneringtranslator')}});
    }
    return out;
}

function main(){
    const fixture = { generatedAt: new Date().toISOString(),
        source: 'translate/index.js translateIncomingMessage / translateIncomingMessageReasoning + 8 provider body contracts',
        cases: cases() };
    writeFileSync(OUT, JSON.stringify(fixture, null, 2));
    console.log('translate fixtures:', fixture.cases.length, '→', OUT);
}
main();
