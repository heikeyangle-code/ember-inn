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
function googleEndpoint(target, text) {
    const lang = target === 'pt-BR' ? 'pt' : target;
    // Google FormBody 的 key
    return { url: `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${lang}&dt=t`, formQ: text };
}
function lingvaUrl(baseUrl, text, target) {
    const u = baseUrl.endsWith('/') ? baseUrl : baseUrl + '/';
    return `${u}api/v1/${encodeURIComponent(text)}/${target}/auto`;
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
    out.push({id:id++,name:'tra-prov-google',_tag:'prov-google',input:{target:'pt-BR',text:'hi'},expected: googleEndpoint('pt-BR','hi')});
    out.push({id:id++,name:'tra-prov-lingva',_tag:'prov-lingva',input:{baseUrl:'https://lingva.example',text:'hi',target:'zh'},expected: lingvaUrl('https://lingva.example','hi','zh')});
    out.push({id:id++,name:'tra-prov-deepl',_tag:'prov-deepl',input:{text:'hi',target:'ZH',apiKey:'dk'},expected: deeplBody('hi','ZH','dk')});
    out.push({id:id++,name:'tra-prov-deeplx',_tag:'prov-deeplx',input:{text:'hi',target:'es',baseUrl:'http://localhost:1188'},expected: deeplxBody('hi','es','http://localhost:1188')});
    out.push({id:id++,name:'tra-prov-onering-from',_tag:'prov-onering',input:{text:'hi',target:'zh',baseUrl:'http://onering.local',internalLang:'en',targetLang:'zh'},expected: oneringBody('hi','zh','http://onering.local','en','zh')});
    out.push({id:id++,name:'tra-prov-onering-reverse',_tag:'prov-onering',input:{text:'你好',target:'en',baseUrl:'http://onering.local',internalLang:'en',targetLang:'zh'},expected: oneringBody('你好','en','http://onering.local','en','zh')});
    // bing/yandex：当前 TranslateClient.kt 直接用免费端点 query（非 JSON body），登记为"直连接线，无 body 构造差异"。为填满 8：补 lingva 带尾斜杠
    out.push({id:id++,name:'tra-prov-lingva-slash',_tag:'prov-lingva',input:{baseUrl:'https://lingva.example/',text:'hi there',target:'ja'},expected: lingvaUrl('https://lingva.example/','hi there','ja')});
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
