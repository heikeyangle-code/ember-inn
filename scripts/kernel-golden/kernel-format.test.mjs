import { JSDOM } from 'jsdom';
import { readFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';
const K = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'app', 'src', 'main', 'assets', 'kernel');
const dom = new JSDOM(`<!DOCTYPE html><html><body><div id="chat"></div></body></html>`, {
    url: 'https://appassets.androidplatform.net/assets/kernel/kernel.html',
    runScripts: 'dangerously', pretendToBeVisual: true,
});
const { window } = dom;
for (const f of ['js/showdown.min.js','js/css-tools.min.js','js/dompurify.min.js','js/highlight.min.js','js/st-extensions.js','js/render.js'])
    window.eval(readFileSync(`${K}/${f}`, 'utf8'));
const F = (s,o)=>window.Kernel.formatText(s,o);
let pass=0, fail=0;
const t=(n,a,e)=>{ if(a===e){pass++;console.log(`  ✓ ${n}`)} else {fail++;console.log(`  ✗ ${n}\n    实际: ${JSON.stringify(a)}`)} };

t('内核就绪', window.Kernel.ready, true);
t('加粗', F('**hello**',{}), '<p><strong>hello</strong></p>');
t('英文引号→<q>', F('"你好呀。"她说。',{}).includes('<q>"你好呀。"</q>'), true);
t('直角引号→<q>', F('「原来如此」他点头。',{}).includes('<q>「原来如此」</q>'), true);
t('encode_tags 默认关(HTML直通)', F('<b>bold</b>',{}).includes('<b>bold</b>'), true);
t('encode_tags 开启时转义', (()=>{window.KernelConfig.encodeTags=true; const r=F('<b>x</b>',{}).includes('&lt;b&gt;'); window.KernelConfig.encodeTags=false; return r})(), true);
t('script 消毒', F('a<script>alert(1)<\/script>b',{}).includes('alert'), false);
t('onerror 属性消毒', F('<img src="x" onerror="alert(1)">',{}).includes('onerror'), false);
t('style 选择器前缀+类名改写', F('<style>.red{color:red}</style>',{}).includes('.mes_text .custom-red'), true);
t('style 内容保留(css-tools)', F('<style>.red{color:red}</style>',{}).includes('color: red'), true);
t('style 媒体查询保留', F('<style>@media(max-width:600px){.a{color:#fff}}</style>',{}).includes('@media'), true);
t('@import 被过滤', F('<style>@import url(//evil.com);.a{color:red}</style>',{}).includes('import'), false);
t('表格(≥3横线)', F('| a | b |\n| --- | --- |\n| 1 | 2 |',{isUser:true}).includes('<table>'), true);
t('_斜体_→<em>(官方underscore扩展)', F('_italic_',{}).includes('<em>italic</em>'), true);
t('__下划线__不被当粗体', F('__under__',{}).includes('<u>') || !F('__under__',{}).includes('<strong>under'), true);
t('删除线', F('~~gone~~',{isUser:true}).includes('<del>gone</del>'), true);
t('图片尺寸语法', F('![pic](https://x.com/a.png =100x100)',{isUser:true}).includes('width='), true);
t('emoji 扩展', /[\u{1F604}]|smile/u.test(F(':smile:',{isUser:true})), true);
t('name2 首行不剥离(官方怪癖对齐)', F('Alice: 我来了',{chName:'Alice'}).includes('Alice:'), true);
t('name2 行中剥离', F('前言\nAlice: 我来了',{chName:'Alice'}).includes('\n 我来了'), true);
t('fixMarkdown 孤星补全(官方同款:补*后成列表)', F('* 未闭合',{}).includes('未闭合*'), true);
t('代码块内引号不包裹', !F('```\n"quoted"\n```',{isUser:true}).includes('<q>'), true);
t('嵌套details卡', (()=>{const r=F('<details><summary>s</summary><p>c</p></details>',{});return r.includes('<details>')&&r.includes('c')})(), true);
t('LaTeX align 替换(官方同款输出链)', F('\\begin{align*}x\\end{align*}',{isUser:true}), '<p>$x$</p>');
t('空消息返回空串', F('', {}), '');
console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail?1:0);
