# 渲染逐项审计（HANDOFF 附录）

### 7.2 逐项对照表

| 官方项（源码位置） | 官方行为 | 我方实现 | 1:1 |
|---|---|---|---|
| 引号对 6 种（script.js） | `"…"`/“…”/«»/「」/『』/＂＂ → `<q>` 含引号字符，代码/style 先保护 | preprocessOfficialHtml 同 6 种 → \uE001..\uE002；保护 ```/~~~ /``/`/style | ✅ |
| 系统消息（script.js `if (!isSystem)`） | 引号转换/encode_tags 跳过，fixMarkdown 仍执行 | MessageFormattingEngine 按 systemUserName/Note 归一（差分锁死）；ChatMarkdown 同规则 | ✅ |
| 用户消息（script.js getMessageTextHTML） | 与 AI 消息同样走 messageFormatting | 用户气泡改走 ChatMarkdown（Markdown/HTML/WebView 同一管线） | ✅ |
| 普通换行（simpleLineBreaks） | 单个 `\n` 也会变 `<br>` | mikepenz `eolAsNewLine=true` | ✅ |
| ~text~ 下划线（Showdown underline） | → `<u>` 下划线色+Underline | \uE003..\uE004 → 下划线色+Underline | ✅ |
| `<em>/<i>`（style.css） | 斜体 + --SmartThemeEmColor | 原生 annotator EMPH → emColor；WebView CSS 同色 | ✅ |
| `<b>/<strong>` | bold | → `**` Markdown 加粗 | ✅ |
| `<s>/<strike>/<del>` | 删除线 | → `~~` | ✅ |
| `<font color="#hex">` | 指定色，内部 em/i/u/q 继承 | \uE005..#hex..\uE007 → 最后覆盖 em/u/q | ✅ |
| `<hr>`/`<br>` | 分隔线/换行 | `<hr>`→`\n\n---\n\n`；`<br>`→`  \n` | ✅ |
| sub/sup（UA） | smaller + vertical-align | 0.83× + BaselineShift.Sub/Super | ✅ |
| ins（UA） | underline | 原生 Underline | ✅ |
| small/big（UA） | smaller/larger | 0.83×/1.2× | ✅ |
| mark（UA） | Mark 黄底 + MarkText 黑字 | 黄底黑字，最后叠加不被继承色覆盖 | ✅ |
| kbd/samp/tt/code（UA） | monospace | FontFamily.Monospace | ✅ |
| var/dfn/cite（UA） | italic | Italic | ✅ |
| abbr[title]/acronym（UA） | dotted underline | 实线近似 | 🟡 视觉近似 |
| data/time/wbr | 无视觉 | 剥标签留内容 | ✅ |
| bdi/bdo/ruby/rt/rp | 方向/注音 | WebView 兜底 | ✅ 需 Web |
| font face/size | UA 字体族/1-7 号 | WebView 兜底（原生仅 color） | 🟡 需 Web |
| `<a href>` | 链接色+无下划线 | 原生 `[text](url)`（支持无引号 href；无 href 剥标签） | ✅ |
| `<img src>` | 内联图片 | 原生 `![alt](url)`（保留 alt；无 src 剥标签） | 🟡 width/height 不保留 |
| 无属性 `<div>`/`<p>` | 块级 | 原生剥标签 + `\n\n` 段落近似 | 🟡 视觉近似 |
| 无属性 `<span>` | 行内无视觉 | 剥标签 | ✅ |
| 带属性 `<div>`/`<p>` | 块级+样式 | 独立 WebView 元素（周围文字保持原生） | ✅ 需 Web |
| 正文色/链接/引用块/q 内斜体/u-em 层级/代码块/表格列表 | style.css 定值 | 原生/WebView CSS 对应（代码块改 softWrap 换行；表格列表原生 M3 近似） | ✅/🟡 见 6.3 |
| 全站文字阴影/阴影边框/BlurTint/气泡底/头像圆角/字体字号 | style.css :root | stShadow/stBorder #80000000；st_blur_tint 空=主题默认/官方 #171717；气泡 #4D000000/#4D3C3C3C；avatarShape square/rounded/circle（默认圆形，官方默认方形可改）；Noto Sans 4 面下载 / textSize=official 15px（默认 16px 可切） | ✅/🟡 见 6.3 |
| encode_tags | `<` 全转义；行首/换行+空白后 `>` 保留 | MessageFormattingEngine.encodeTags（等价字符扫描，非系统消息） | ✅ |
| 流式渲染 | 增量整段 messageFormatting | StreamingMarkdown 轻量着色，结束完整重渲染 | 🟡 中间态近似，最终一致 |
| DOMPurify | 剥 script/on*，白名单 | JS 全开、网络全开（用户要求），只拦 javascript: URL | ❌ 有意偏差 |
| `<style>` | 官方默认剥除（角色开关恢复+前缀） | 默认放行，且只影响该消息自己的 WebView | ❌ 有意偏差 |
| 外部媒体 | 官方 forbid_external_media 默认禁 | 默认放行 | ❌ 有意偏差 |
| Mermaid | 官方插件渲染 | WebView + 本地 asset JS | ✅ 功能级 |
| reasoning | 官方独立样式（em 色/左栏） | App 折叠卡（onSurfaceVariant），格式化走引擎 REASONING 位点 | 🟡 功能级非 1:1 |
| WebView 高度 | 官方 DOM 正常撑高 | ResizeObserver + EmberInnBridge 上报 + 图片兜底 + onPageFinished 轮询 ≤15s + 初始 160dp；CSS 像素 1:1 转 dp；实测全高展开 | ✅ 机制自研 |

### 7.3 文本级 HTML 标签
- 官方管线 = messageFormatting → Showdown → DOMPurify → 浏览器 UA 默认；ST style.css **没有**为 sub/sup/ins/small/big/mark/kbd/samp/tt/code/var/dfn/cite/abbr 写规则。
- Chromium UA：sub/sup/small=smaller、big=larger、mark=Mark/MarkText（黄底黑字）、tt/code/kbd/samp=monospace、i/cite/em/var/address/dfn=italic、u/ins=underline、abbr[title]/acronym[title]=dotted underline。
- Android `Html.fromHtml`/`AnnotatedString.fromHtml` 不支持 mark/kbd/samp/var/ins/abbr/code，且接不了我方 q/u/font 着色层 → 不采用；Beeper matrix-messageformat-compose 的“HTML→AnnotatedString+延迟着色”与本项目同构（架构验证）。
- 实现：preprocessOfficialHtml 10 组文本级标签转换（私有标记 \uE020-\uE031）；mark 最后叠加（UA 声明 > 继承值）；var/dfn/cite 斜体先加、q/font 颜色后加。
- 保留 WebView：bdi/bdo/ruby/rt/rp/font face-size/nobr/marquee/blink；布局/交互/媒体/整页走 WebView。
- OFFICIAL_HTML_TAG 补齐（防漏成纯文本）：script/html/head/body/title/meta/link、caption/col/colgroup/tbody/thead/tfoot/tr/td/th、dl/dt/dd、datalist/optgroup/option、marquee/blink/nobr/xmp/shadow/menuitem/slot。
- 自定义/无属性完整标签（`<inner>`/`<UpdateVariable>`/`<StatusPlaceHolderImpl/>` 等）经 MessageHtml 判定走 WebView（官方 Showdown/DOMPurify/浏览器对未知标签保留文本；原生 IntelliJ markdown 会整块吞掉导致空白）；纯文字比较式（a<b、x<10、1 < 2）不误判。
- 对照源码文件：`~/sillytavern-ref/public/script.js`（messageFormatting）、`public/style.css`（:root/.mes_text）、Chromium UA `third_party/blink/.../html.css`、AOSP Html.java + Compose fromHtml、beeper matrix-messageformat-compose。
