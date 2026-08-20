# 阶段二：消息渲染"至今零效果"诊断（证据链）

> 目标：说明为什么此前消息渲染"没有任何效果"，每一条结论都带代码/测试证据，不跳步。
> 涉及文件：`app/.../ui/chat/ChatScreen.kt`、`ChatViewModel.kt`、`engine/.../prompt/MessageFormattingEngine.kt`、`docs/HANDOFF.md`。
> 隔离验证的实操证据（Robolectric 截图 + 引擎单测）见阶段四产物，本文件是根因与顺序核对。

---

## 1. 诊断结论速览

| # | 根因 | 严重度 | 证据 |
|---|---|---|---|
| R1 | **路线 A（HTML DOM→原生 UI 树）从未实现**：现有"原生"路径是 markdown 基（`preprocessOfficialHtml` 把官方 HTML 标记折成 mikepenz 标记），不是任务书约定的 DOM 解析→样式解析→原生组件树 | 高（架构性缺口） | §2.1 |
| R2 | 所有真实 HTML/CSS 一律被路由进 WebView，无原生静态 HTML 兜底；WebView 此前存在两个致白 bug（整文档被切分、base64 不解码）→ 静态卡"零效果" | 高（已修，记录在案） | §2.2 |
| R3 | 处理顺序与官方一致（宏→正则→Markdown→消毒），引擎 805 例差分锁死 | 低（无问题） | §2.3 |
| R4 | 路线 B WebView 网络策略与任务书"收紧"矛盾：当前"网络与外链放开"，未按 `forbid_external_media` 拦截外部媒体 | 中（阶段三修复） | §2.4 |

---

## 2. 分项证据

### 2.1 R1：路线 A 从未实现（grep 证据）

任务书路线 A = HTML 解析库解析 DOM 节点树 → 内联样式解析 → 节点树递归映射原生 UI 组件树，且"解析/样式计算放引擎层、节点→UI 映射放应用层"。

对应用层 `app/src/main/java/com/emberinn/app/ui/chat/` 与引擎层 `engine/src/main/kotlin/com/emberinn/engine/` 全量检索：

- `HtmlToCompose` / `HtmlParser` / `Jsoup` / `HtmlNode` / `RenderNode` / `StyleMap` / `parseHtml` → **0 命中**。
- 现有原生渲染入口是 `ChatMarkdown`（ChatScreen.kt:5079）→ `NativeMarkdown`（:4856），走 mikepenz `parseMarkdown`，组件工厂 `OfficialMarkdownNode`（:4474）只处理官方行内标记（`\uE001-\uE007` 等）。
- `preprocessOfficialHtml`（:4135）做的是"官方 HTML 标记 → 原生 markdown 标记"的折算，**不是 DOM 解析**。

结论：约定好的 DOM→原生 UI 渲染路线从不存在。静态 HTML/CSS 角色卡（div 布局、`<style>` 块、`<details>`、内联样式）没有对应的原生渲染器，这就是"静态展示类内容零效果"的架构根因。

### 2.2 R2：HTML 全走 WebView + 既往两个致白 bug

- 分流：`buildMessageSegments`（ChatScreen.kt:4736）把命中 `carveWebElementRanges`（:4679）的 HTML 元素整体合成 `SegmentKind.WebHtml` 交给 `WebViewHtml`；完整网页文档整段走 Web（:4743-4745）。
- 既往致白 bug（记录于 HANDOFF.md §7.2，已修）：
  1. 完整 HTML 文档被切分器拆成多段（`<head>/<style>/<body>` 各自成段再各自套独立页面）→ 网页永远渲染不出来。修复：整文档整段走 WebView（ChatScreen.kt:4743）。
  2. `loadDataWithBaseURL` 曾用 base64 编码 → 不解码致空白。修复：改 UTF-8 原文直载。
- 现状：WebView 空白已解决，但**静态 HTML 无一例外靠 WebView**，没有路线 A。

### 2.3 R3：处理顺序核对（与官方一致）

官方顺序（`SillyTavern/public/script.js` `messageFormatting`）：宏替换（仅首条 AI 消息）→ prompt bias 剥离 → 正则脚本（REASONING/AI_OUTPUT 位点）→ fixMarkdown → encode_tags → reasoning 前后缀转义 → 引号转换 → `\begin{align*}`→`$$` → Showdown makeHtml → 代码块修复 → name2 前缀剥离 → DOMPurify 消毒。

我方：`ChatViewModel.displayTextOf`（ChatViewModel.kt:836）→ `MessageFormattingEngine.format`（引擎，宏→Note/name 归一→bias 剥离→显示正则→fixMarkdown→encode_tags→reasoning 转义→name2 剥离，`MessageFormattingDiffTest` 805 例差分）→ 渲染层（WebView 侧 `sanitizeHtmlForWebView`，原生侧 mikepenz + `preprocessOfficialHtml`）。

核对结论：**顺序与官方一致**，引擎文本子集 1:1。差异点在渲染层职责划分（官方把 Markdown 转换 + DOMPurify 消毒放进同一函数；我方拆成"引擎文本格式化 + 渲染层 Markdown/消毒"），属合理分层，不构成顺序错误。

### 2.4 R4：路线 B 网络收紧缺口

HANDOFF §7.2 明确记录当前策略"网络与外链放开"，且 WebView `allowFileAccessFromFileURLs`/`allowUniversalAccessFromFileURLs`/`MIXED_CONTENT_ALWAYS_ALLOW` 全开。任务书要求"网络访问要收紧，不能任意加载外部资源，角色卡不可信，按阶段一确认的官方媒体标签规则来定"。官方规则（`RENDER_AUTHORITY.md` §2.6）：`forbid_external_media` 默认 true（外部媒体默认禁），仅内嵌 data URI/本 origin 放行。→ 阶段三修复：`WebViewClient.shouldInterceptRequest` 按该开关拦截外部资源。

---

## 3. "零效果"根因的一句话总结

静态 HTML/CSS 消息之所以"看起来完全没渲染"：既没有路线 A 的原生 DOM 渲染器（R1），又被整体甩给当时有致白 bug 的 WebView（R2）；bug 修好后能显示，但走的是"整条消息进 WebView"的退路，并非任务书约定路线。阶段三按约定把静态内容接回原生 DOM 管线（路线 A），交互内容走收紧网络的 WebView（路线 B）。
