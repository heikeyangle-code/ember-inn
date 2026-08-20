# 阶段四：重建结果验证报告

> 阶段一权威清单 → `docs/RENDER_AUTHORITY.md`（源码出处逐条标注）
> 阶段二诊断 → `docs/RENDER_DIAGNOSIS.md`（R1–R4 证据链）
> 本报告：实现覆盖范围 + 实测通过率（具体数字，不含"基本完成"式模糊表述）。

---

## 1. 权威清单（官方源码出处）

基线：`/workspace/SillyTavern` 官方 ST 仓库副本；依赖 `dompurify ^3.4.2`、`showdown ^2.1.0`、`@adobe/css-tools ^4.4.4`。完整出处见 `RENDER_AUTHORITY.md`，要点：

| 项 | 结论（以源码为准） | 出处 |
|---|---|---|
| 处理顺序 | 宏替换（仅首条 AI 消息）→ prompt bias 剥离 → 正则脚本 → fixMarkdown → encode_tags → reasoning 转义 → 引号转换 → `\begin{align*}`→`$$` → **Markdown(showdown makeHtml)** → 代码块修复 → name2 剥离 → **HTML 消毒(DOMPurify)** | `script.js:1753-1912` `messageFormatting` |
| HTML 消毒 | DOMPurify 默认白名单 + `ADD_TAGS:['custom-style']`；`details/summary/img/video/audio` 默认放行；`script/iframe` 不在白名单 | `script.js:1898-1906`、`purify.js html$1` |
| style 属性 | DOMPurify 3.4.2 **不对 style 属性值做 CSS 解析**，原样放行（安全靠浏览器）；我方原生解释器必须自实现保守 CSS 消毒 | `purify.js:569`、`_isValidAttribute` |
| 独立 `<style>` 块 | 消息内恒保留：选择器前缀 `.mes_text custom-`、删 `@import`、禁外部资源声明；`AllowGlobalStyles` 只影响 creator notes | `chats.js:564-606,644,687` |
| `<details>/<summary>` | 标准标签默认放行，浏览器原生折叠，无 JS 依赖 | `index.html:7429-7447` `mes_reasoning_details` |
| 媒体规则 | `isExternalMediaAllowed()` 默认 `!power_user.forbid_external_media`（**默认禁外部媒体**）；禁用时 `IMG/VIDEO/AUDIO/SOURCE/TRACK/EMBED/OBJECT` 的外部 `src/data/srcset` 整节点删除 | `chats.js:1974-2028,852-867` |
| Markdown 库 | showdown 2.1.0，`makeHtml`（openLinksInNewWindow、emoji、tables、underline 等选项） | `script.js:1880`、showdown 选项 |
| 宏系统 | 传统 `substituteParams` + 新宏引擎（宏调用/嵌套/条件 `{{if}}`/管道修饰符/变量读写/宏定义） | `public/scripts/macros.js`、`public/scripts/params.js` |
| 推理/思维链 | 独立展示：`message.extra.reasoning`，前缀/后缀高亮、流式追加、`<details>` 折叠 | `index.html` mes_reasoning_details、`script.js` REASONING 位点 |

## 2. 诊断根因（阶段二结论）

1. **R1（架构性缺口）**：路线 A（HTML DOM → 原生 UI 树）从未实现——`grep` 全量检索 `HtmlToCompose/Jsoup/RenderNode/StyleMap` 零命中；现有"原生"路径是 markdown 折算（`preprocessOfficialHtml`），不是 DOM 解析。
2. **R2**：所有真实 HTML 一律进 WebView，且 WebView 曾有致白 bug（整文档切分、base64 不解码）→ 静态卡"零效果"。
3. **R3（无问题）**：引擎处理顺序与官方一致，805 例差分锁定。
4. **R4**：路线 B 网络未收紧，与"角色卡不可信"矛盾。

## 3. 实现覆盖范围

### 路线 A（静态 HTML → 原生 UI，已落地）
- 引擎层（`engine/.../render/`，纯 JVM 无 UI 依赖）：
  - `HtmlSanitizerEngine.kt` — DOMPurify 白名单复刻 + 保守 CSS 消毒（剔 `url()`/`expression`/`behavior`/`-moz-binding`/`@import`/危险协议）+ 独立 `<style>` 块（前缀化/删 `@import`/禁外部资源）+ 媒体规则（外部 URL 默认删）。
  - `CssStyleParser.kt` / `RenderStyleResolver.kt` — 内联样式 + style 块选择器合并、样式继承、盒模型/颜色/文字/基础布局。
  - `RenderModels.kt` — `RenderNode` 树（含 `InteractiveKind`：Details/Summary/Link/Image/Video/Audio/InputCheckbox）。
- 应用层（`app/.../ui/chat/RenderNodeCompose.kt`）：
  - `RenderNodeTree`（:84）— 节点树 → Compose 递归映射，含 `DetailsExpandable`（:222）原生折叠、图片/音视频、链接、降级。
  - `parseStaticHtml`（:577）+ `HtmlRenderCache`（:555）+ `StaticHtmlContent`（:589，后台解析 + 非零占位防假空白）。

### 内容分流（路线 A / B，已接入）
- `isStaticHtml`（RenderNodeCompose.kt:543）：含 `<script>` / `on\w+=` 事件 / `javascript:` → 动态走路线 B；否则走路线 A。
- `SegmentedMarkdown`（ChatScreen.kt:5039）：`WebHtml`（:5057）/ `Interactive`（:5076）段按 `isStaticHtml` 分流；`Mermaid` 段恒走 WebView（依赖 JS）。
- `<details>/<summary>` 明确划入路线 A（`InteractiveKind.Details`），不丢给路线 B。

### 路线 B（WebView，网络收紧）
- `shouldInterceptRequest`（ChatScreen.kt:5379）：按官方 `forbid_external_media` 语义拦截外部 http(s) 媒体资源（`isExternalMediaUrl` :5179，媒体扩展名 + Accept:image 请求头）；data:/本地 file:/android_asset 放行（字体/头像/mermaid.min.js）。JS/交互保持可用。

### 明确不支持项（及理由）
| 不支持项 | 理由 |
|---|---|
| 冷门 CSS 特性（grid 复杂布局、flex 高级属性、动画/过渡/伪元素/媒体查询） | 阶段三既定方案：只覆盖颜色/盒模型/文字/基础布局 |
| style 属性里的 `position/float/transform/z-index` 等 | 同上，忽略不致命 |
| 外部媒体默认加载 | 官方默认禁外部媒体，角色卡不可信；仅 data:/本 origin 放行 |
| 无扩展名签名 CDN 的 video/audio 外部 URL 拦截 | WebView 请求头不含元素类型，扩展名/Content-Type 无法识别（已知限制） |
| 任意第三方脚本调用官方全局对象（SillyTavern 运行时 API、事件系统） | 阶段三既定方案：范围收紧为"传状态、展示正确"，不做通用 JS 沙盒 |

## 4. 实测通过率（具体数字）

| 项 | 结果 |
|---|---|
| 引擎渲染管线 `RenderPipelineTest`（消毒/样式/选择器/details/降级/媒体） | **22/22 通过（100%）** |
| 引擎全量测试套件 | **408 测试，0 失败，0 错误，0 跳过（100%）**（155 个测试类） |
| 应用层隔离复验 `RenderRoutingTest`（分流判定 + 路线 A 解析 6 用例） | **6/6 通过（100%）** |
| 应用层全量单元测试 | **51 测试，0 失败，0 错误（100%）**（7 个测试类，含新增 6 例） |
| 完整 APK 构建 `:app:assembleDebug` | **成功**（b1/b2/b3 全部编译链接通过） |
| 隔离证据 | JVM 级：静态/动态样例分类正确、代表卡 HTML → RenderNode 树（style 块命中、details 原生、危险内容删除、外部媒体删除、未知标签降级）均有断言证据；应用 UI 层 Compose 映射编译通过（Robolectric 截图未建——app 模块无该测试基建，Compose 组件在 JVM 不可运行，见下） |

### 未做项与诚实说明
- **Robolectric/截图证据**：app 模块仅有 `testImplementation(junit)`，无 Robolectric/Compose UI 测试基建，本阶段未搭建（属测试基建改造，非渲染管线本身）。UI 层验证以"编译通过 + 引擎/路由层 JVM 断言"为据。
- **真实社区角色卡抽样**：沙箱无网络与社区卡样本库，未能下载真实卡实测。改用"代表性卡内容模式"（style 块 + details + 媒体 + 危险内容 + 未知标签）跑真实管线，通过率见上表。数据层管道对真实卡为同一路径。
- **分流后静态 WebHtml 是否真正"有视觉效果"**：需真机/模拟器运行确认（本环境无设备）。引擎数据层与映射层均已验证。
