#!/usr/bin/env node
// 官方 renderStoryString（power-user.js:2234-2276）模板渲染 → fixture。
// 函数体逐字摘自 SillyTavern 1.18.0 release 8172dcd；Handlebars 用官方同版本 vendor。
// 打桩登记：validateStoryString 输出不影响结果（仅缓存警告）、substituteParams=恒等、
// storyStringPosition=IN_PROMPT、instructSettings 未启用（尾换行规则简化，官方两分支均补 \n）。
// Handlebars helper 注册对齐官方 macros.js：trim 还原 {{trim}}；helperMissing 还原 {{name}}
// 并交 substituteParams（打桩恒等 → 保留字面量）。

import { createRequire } from 'node:module';
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const outFile = join(repoRoot, 'engine', 'src', 'test', 'resources', 'diff', 'story-string.json');
const require = createRequire(import.meta.url);
const Handlebars = require('./vendor/node_modules/handlebars');

Handlebars.registerHelper('trim', () => '{{trim}}');
Handlebars.registerHelper('helperMissing', function () {
    const options = arguments[arguments.length - 1];
    const macroName = options.name;
    return `{{${macroName}}}`;
});

const funcs = `
function renderStoryString(template, params) {
    const storyStringPosition = 0; // extension_prompt_types.IN_PROMPT
    const instructSettings = { enabled: false, wrap: false, story_string_suffix: '' };
    const compiledTemplate = Handlebars.compile(template, { noEscape: true });
    let output = compiledTemplate(params);
    output = output.replace(/^\\n+/, '');
    if (output.length > 0 && !output.endsWith('\\n') && storyStringPosition !== 1) {
        if (!instructSettings.enabled || (instructSettings.wrap && !instructSettings.story_string_suffix)) {
            output += '\\n';
        }
    }
    return output;
}
`;

const run = new Function([
    'const Handlebars = arguments[0];',
    funcs,
    'return (c) => renderStoryString(c.template, c.params);',
].join('\n'))(Handlebars);

const defaultStoryString = '{{#if system}}{{system}}\n{{/if}}{{#if description}}{{description}}\n{{/if}}{{#if personality}}{{char}}\'s personality: {{personality}}\n{{/if}}{{#if scenario}}Scenario: {{scenario}}\n{{/if}}{{#if persona}}{{persona}}\n{{/if}}';

const cases = [];
function add(id, body) {
    cases.push({ id, args: body, expected: run(body) });
}

const params = {
    system: 'You are Alice.',
    description: 'A curious explorer.',
    personality: 'witty',
    scenario: 'In a library.',
    persona: 'The user is a librarian.',
    char: 'Alice',
    user: 'Bob',
    wiBefore: 'WI before',
    wiAfter: 'WI after',
    mesExamples: '',
    anchorBefore: '',
    anchorAfter: '',
};

add('default-full', { template: defaultStoryString, params });
add('default-empty', { template: defaultStoryString, params: { system: '', description: '', personality: '', scenario: '', persona: '', char: 'Alice', user: 'Bob' } });
add('unknown-field', { template: 'Hi {{unknown}}!', params: { user: 'Bob' } });
add('unknown-macro-kept-empty', { template: '{{random}} text', params: {} });
add('if-else', { template: '{{#if persona}}{{persona}}{{else}}no persona{{/if}}', params });
add('nested-if', { template: '{{#if system}}{{#if description}}{{description}}{{/if}}{{/if}}', params });
add('user-char', { template: '{{user}} talks to {{char}}', params: { user: 'Bob', char: 'Alice' } });
add('leading-newlines', { template: '\n\n{{system}}', params });
add('trailing-newline-added', { template: '{{system}}', params });
add('wi-fields', { template: '{{wiBefore}}|{{wiAfter}}', params });
add('trim-unknown', { template: 'a {{trim}} b', params: {} });

writeFileSync(outFile, JSON.stringify({ source: 'power-user.js renderStoryString（Handlebars noEscape）', cases }, null, 2));
console.log('story-string:', cases.length, 'cases ->', outFile);
