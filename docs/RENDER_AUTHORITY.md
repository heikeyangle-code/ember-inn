# 阶段一：消息渲染权威清单（以官方源码为准）

> 源码基线：`/workspace/SillyTavern`（官方 ST 仓库副本）。
> 依赖版本：`dompurify ^3.4.2`、`showdown ^2.1.0`、`@adobe/css-tools ^4.4.4`（package.json）。
> 本文所有结论均带源码出处；与任务书/旧对话说法冲突处，以本文为准。

---

## 1. 完整处理顺序（messageFormatting）

入口：`public/script.js:1753-1912` `export function messageFormatting(mes, ch_name, isSystem, isUser, messageId, sanitizerOverrides, isReasoning)`。

按序执行（每步为真源码）：

| # | 步骤 | 源码出处 | 说明 |
|---|---|---|---|
| 0 | 宏替换（仅第一条 AI 消息） | script.js:1758-1765 | `Number(messageId) === 0 && !isSystem && !isUser && !isReasoning` 时才执行 `substituteParams`。**其余消息的宏在生成/落库时已替换**（生成侧处理），显示侧不再替换 |
| 1 | prompt bias 前缀剥离 | script.js:1780-1783 | `substituteParams(user_prompt_bias)` 替换后作为前缀剥除（默认 show_user_prompt_bias=关） |
| 2 | 正则脚本 | script.js:1808-1813 | `getRegexedString(mes, regexPlacement, {characterOverride: ch_name, isMarkdown: true, depth})`；位点见 `extensions/regex/engine.js:281-290`（REASONING=6 / USER_INPUT=1 / SLASH_COMMAND=3 / AI_OUTPUT=2） |
| 3 | 自动修复 Markdown | script.js:1816-1818 | `power_user.auto_fix_generated_markdown`（默认开）→ `fixMarkdown` |
| 4 | encode_tags | script.js:1820-1824 | 仅非系统消息；`<`→`&lt;`，行首 `>` 保留 |
| 5 | reasoning 前后缀首处转义 | script.js:1826-1835 | `escapeHtml(reasoning.prefix/suffix)` 首处替换 |
| 6 | 引号转换（6 种→`<q>`） | script.js:1837-1871 | `"…"`/“…”/«»/「」/『』/＂＂→`<q>`；代码块/`<style>` 先保护 |
| 7 | `\begin{align*}`→`$$` | script.js:1878-1879 | |
| 8 | **Markdown 转换** | script.js:1880 | `converter.makeHtml(mes)` |
| 9 | 代码块换行修复 | script.js:1882-1892 | Firefox `<br>` 问题、`&amp;`→`&` |
| 10 | name2 前缀剥离 | script.js:1894-1896 | `allow_name2_display=关` 时剥 `角色名:` 前缀（HTML 文本上执行） |
| 11 | **HTML 消毒** | script.js:1907-1909 | `encodeStyleTags → DOMPurify.sanitize → decodeStyleTags` |

**结论：宏替换 → 正则脚本 → Markdown → 消毒，顺序与任务书描述一致；但注意宏替换在官方显示侧只对第一条消息发生，其余在生成侧完成。**

### 1.1 展示文本取用（display_text）
`script.js:1977` `const text = message?.extra?.display_text ?? message.mes;` —— 翻译/改写覆盖用 `extra.display_text`。

---

## 2. HTML 消毒规则（DOMPurify）

### 2.1 配置
`script.js:1898-1906`：

```js
const config = {
    RETURN_DOM: false,
    RETURN_DOM_FRAGMENT: false,
    RETURN_TRUSTED_TYPE: false,
    MESSAGE_SANITIZE: true,          // 驱动自定义钩子
    ADD_TAGS: ['custom-style'],
    ...sanitizerOverrides,
};
```

- 允许标签：DOMPurify 默认白名单（全部标准 HTML5 标签，含 `details`/`summary`/`img`/`video`/`audio`/`style` 之外的内联元素；`style`/`script`/`iframe` 等默认**不在**白名单）＋自定义 `custom-style`。
- 允许属性：DOMPurify 默认白名单（含 `class`、`style`、`href`、`src`、`target`、`data-*` 等），外加 `afterSanitizeAttributes` 钩子强制链接 `target=_blank rel=noopener`（chats.js:1901-1908）。

### 2.2 class 属性处理（chats.js:1921-1934）
`uponSanitizeAttribute`：`class` 值逐 token 前缀 `custom-`，保留 `fa-*`、`note-*`、`monospace` 三类（供 UI 交互）。注意仅对 `BUTTON`/`DIV` 且 `MESSAGE_ALLOW_SYSTEM_UI` 时保留 `menu_button`。

### 2.3 未知元素（chats.js:1937-1972）
`uponSanitizeElement`：`HTMLUnknownElement` 内换行→`<br>`（`<pre>` 内跳过），保留文本。

### 2.4 独立 `<style>` 样式块（chats.js:536-626，已按源码 3.4.2 修正）
**DOMPurify 默认标签白名单含 `style`（tags.js:261），但其内容在 `DEFAULT_FORBID_CONTENTS` 中（purify.js:563）→ DOMPurify 会剥空 `<style>` 内容。** 因此官方用 `<custom-style>` 往返保护 CSS：
1. `encodeStyleTags`（chats.js:536-541）：`<style>…</style>` → `<custom-style>${encodeURIComponent(…)}</custom-style>`（先于 DOMPurify，保护内容不被剥；`custom-style` 在 `ADD_TAGS` 白名单）。
2. `decodeStyleTags`（chats.js:551-626，**messageFormatting 中无条件执行**，script.js:1907-1909）：
   - 用 `@adobe/css-tools` 解析 AST；
   - 选择器全部加前缀 `.mes_text custom-`（`sanitizeSimpleSelector`，`custom-` 开头类不重复加）；`:has/not/where/is/matches/any` 内嵌选择器递归处理（chats.js:569-597）；
   - `@import` 规则删除（chats.js:606）；
   - **外部媒体禁用时，含 `://` 的声明全部过滤**（chats.js:564-566）；
   - 解析失败输出 `CSS ERROR: …`。

> 结论：**消息里的 `<style>` 块恒被保留**（选择器前缀化 + 删 @import + 禁外部资源声明）。`AllowGlobalStyles`（chats.js:644 StylesPreference）**只影响 creator notes**（formatCreatorNotes，chats.js:687：允许时前缀 `''`、否则 `#creator_notes_spoiler `），与消息渲染无关。任务书说的"style 安全过滤"即这套前缀化＋@import 剔除＋外部资源剔除。

### 2.5 style 属性（内联，已按源码 3.4.2 修正）
DOMPurify 默认允许 `style` 属性；`style` 在 `DEFAULT_URI_SAFE_ATTRIBUTES`（purify.js:569，视为惰性属性），`_isValidAttribute` 不校验其值。**DOMPurify 3.4.2 不对 style 属性值做任何 CSS 解析/过滤（源码无 CSS 解析器），原样放行**，安全性依赖浏览器 CSS 引擎（`expression()` 已死、`behavior` 仅 IE、CSS `url(javascript:)` 不执行）。
> 对路线 A 的意义：我们是原生 CSS 解释器（扮演浏览器），**不能**照抄"原样放行"，必须自实现保守 CSS 消毒（剔除 `url()`/`expression()`/`behavior`/`-moz-binding`/`@import`/`javascript:`/`vbscript:` 协议等）——这正是任务书要求保留的那层防护。

### 2.6 媒体加载规则（chats.js:1974-2028 + 852-867）
`isExternalMediaAllowed()`：默认 `!power_user.forbid_external_media`（**默认禁用外部媒体**），角色可覆盖。
`uponSanitizeElement` 中：当外部媒体禁用时，`AUDIO/VIDEO/SOURCE/TRACK/EMBED/OBJECT/IMG` 节点的 `src`/`data`/`srcset` 若为外部 URL（`://` 或 `//` 开头且非本 origin）→ 整节点删除（`node.remove()`），音视频停止播放。
→ **外部 URL 媒体默认不加载；仅内嵌 data URI / 本 origin 资源放行。**

### 2.7 特殊原生交互标签
`<details>/<summary>` 是标准 HTML5 标签，DOMPurify 默认放行；浏览器原生提供无 JS 折叠交互。官方在 `mes_reasoning_details`（index.html:7429-7447）就用 `<details><summary>` 实现推理折叠。→ **路线 A 必须原生支持 `details/summary`，不做成纯文本、也不丢给路线 B。**

---

## 3. 宏系统完整语法

### 3.1 新引擎（实验宏引擎，`power_user.experimental_macro_engine`）
架构：`macros/macro-system.js` 聚合 `engine/`（MacroEngine/MacroRegistry/MacroParser/MacroLexer/MacroCstWalker）。入口 `MacroEngine.evaluate(input, env)`（MacroEngine.js:117-159）：预处理处理器 → 词法/语法解析（chevrotain）→ CST 求值 → 后处理。

语法（MacroLexer.js:46-165 词法定义 + core-macros.js 用法）：
- 基础调用：`{{macro}}`、`{{macro::arg1::arg2}}`、`{{macro arg1 arg2}}`
- 命名参数：`{{macro::name=value}}`
- 输出修饰符（管道）：`{{macro::arg|modifier}}`（`FilterFlag >` 存在时 `|` 视为管道，MacroLexer.js:68）
- 宏标志（MacroLexer.js:63）：`!` 立即解析、`?` 延迟解析、`~` 重求值、`/` 闭合块、`#` 保留空白（不自动 trim 作用域内容）
- 变量简写：`.var`（局部）、`$var`（全局），支持运算符 `++ -- ??= ?? ||= || -= == != >= > <= < += =`（MacroLexer.js:117-147）
- 嵌套宏：参数内可再 `{{…}}`（MacroLexer.js:214）
- 作用域宏：`{{if condition}}then{{else}}other{{/if}}`（core-macros.js:130-225）
- 注释：`{{// …}}`（core-macros.js:282）
- 条件宏：`{{if}}`，条件支持宏名（自动解析）、变量简写、`!` 取反；falsy 定义：空串、`"false"`、`"off"`、`"0"`（core-macros.js:136-193）；`{{else}}` 仅在 `if` 作用域内生效
- 动态宏：env 里可传 string / 函数 / MacroDefinitionOptions（MacroEngine.js:173-213）
- 未知宏：**保留原文宏语法**（MacroEngine.js:216-217）——对"零效果"诊断很关键

注册宏清单（`definitions/*`）：`space newline noop trim if else input maxPrompt maxContext maxResponse reverse // roll random pick banned outlet`（core）；`user char group groupNotMuted notChar charPrompt charInstruction charDescription charPersonality charScenario persona mesExamplesRaw mesExamples charDepthPrompt charCreatorNotes charFirstMessage charVersion model original isMobile`（env）；`lastGenerationType hasExtension`（state）；`lastMessage lastMessageId lastUserMessage lastCharMessage firstIncludedMessageId firstDisplayedMessageId lastSwipeId currentSwipeId allChatRange`（chat）；`time date weekday isotime isodate datetimeformat idleDuration timeDiff`（time）；`setvar addvar incvar decvar getvar hasvar deletevar setglobalvar addglobalvar incglobalvar decglobalvar getglobalvar hasglobalvar deleteglobalvar`（variable）；`systemPrompt` 及 instruct 宏（instruct）。

### 3.2 旧引擎（`macros.js:610-715 evaluateMacros`，substituteParamsLegacy）
正则逐个替换，顺序：**preEnv 宏 → env 变量 → postEnv 宏**（macros.js:694）。
- preEnv（macros.js:622-636）：`<USER> <BOT> <CHAR> <CHARIFNOTGROUP> <GROUP>`、骰子 `{{roll::…}}`、instruct 宏、变量宏、`{{newline}} {{trim}} {{noop}} {{input}}`
- env（macros.js:681-692）：`{{name}}`（user/char/description/personality/scenario/persona/mesExamples/mesExamplesRaw/charVersion… 由 script.js:2805-2895 注入；**user/char 最后替换**，保证 `{{description}}` 内部也生效）
- postEnv（macros.js:642-673）：`{{maxPrompt}} {{maxContext}} {{maxResponse}} {{lastMessage}} {{lastMessageId}} {{lastUserMessage}} {{lastCharMessage}} {{firstIncludedMessageId}} {{firstDisplayedMessageId}} {{lastSwipeId}} {{currentSwipeId}} {{allChatRange}} {{reverse:…}} {{//…}} {{time}} {{date}} {{weekday}} {{isotime}} {{isodate}} {{datetimeformat …}} {{idle_duration}} {{time_UTC±N}} {{outlet::…}} {{timeDiff::…::…}} {{banned}} {{random}} {{pick}} {{roll}}`

新旧引擎切换：`substituteParams` 检测 `power_user.experimental_macro_engine`（script.js:2937-2940）。

---

## 4. Markdown 转换

库：**showdown ^2.1.0**。配置（script.js:521-536）：

```js
converter = new showdown.Converter({
    emoji: true,
    literalMidWordUnderscores: true,
    parseImgDimensions: true,
    tables: true,
    underline: true,
    simpleLineBreaks: true,          // 单换行 → <br>
    strikethrough: true,
    disableForced4SpacesIndentedSublists: true,
    extensions: [markdownUnderscoreExt()],
});
converter.addExtension(markdownExclusionExt(), 'exclusion');
```

另 `addShowdownPatch(showdown)`（script.js:729）。扩展：`showdown-underscore.js`（`~text~`→`<u>`）、`showdown-exclusion.js`、`showdown-patch.js`。

---

## 5. 推理/思维链展示

- 存储：`message.extra.reasoning`（原始文本）、`extra.reasoning_duration`、`extra.reasoning_type`、`extra.reasoning_display_text`（翻译覆盖）（reasoning.js:330-349）。
- 展示结构（index.html:7429-7447）：`<details class="mes_reasoning_details"><summary class="mes_reasoning_summary">…标题/动作…</summary><div class="mes_reasoning"></div></details>` —— 即**折叠块**，无 JS 原生交互（details/summary）。
- 渲染：`messageFormatting(reasoning, '', false, false, messageId, {}, true)`（reasoning.js:555），即推理文本也走完整管线（isReasoning=true → REASONING 位点正则）。
- 状态机：`ReasoningState = None/Thinking/Done/Hidden`（reasoning.js:245-250）；隐藏推理模型（Hidden）默认折叠（reasoning.js:569-571）。
- 展开策略：`power_user.reasoning.auto_expand`（reasoning.js:80-85）；流式期间显示"Thinking…"，结束后"Thought for …"（reasoning.js:610-634）。
- 编辑/复制/折叠全部/收起：index.html:7437-7444 动作按钮。

---

## 6. 对重建的硬约束（由以上推导）

1. 处理顺序固定：宏 → 正则 → Markdown → 消毒（官方顺序）。
2. `style` 属性：允许放行（官方语义），但路线 A 自实现保守 CSS 消毒（去 `url()`/`expression`/`behavior`/`-moz-binding`/`@import`/危险协议）。
3. 独立 `<style>` 块：消息内**恒保留**（选择器前缀化 `.mes_text custom-` + 删 `@import` + 禁外部资源声明）；路由 A 原生侧按简单选择器匹配应用。
4. `<details>/<summary>` 原生支持，属路线 A。
5. 媒体：外部 URL 默认禁（本地/内嵌 data 允许）；资源加载按 `forbid_external_media` 收紧。
6. 宏：未知宏保留原文；`{{if/else}}` 作用域语义；变量简写；管道修饰符。
7. 推理链：独立折叠展示，走同一格式化管线（REASONING 位点）。
