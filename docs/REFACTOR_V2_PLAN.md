# EmberInn 2.0 重构方案（定稿）

> 状态：定稿，即日实施
> 决策输入：REFACTOR_V2_PLAN 初版 + Foreverse 解包分析 + 外部重构指令文档（已批判性吸收，纠错 4 处）+ 官方源码核实（release 8172dcd）
> 用户决策：**不保留任何旧 App 层实现**；引擎不动

---

## 〇、宪法（优先级铁律，冲突时从上到下裁决）

1. Tavern / SillyTavern 兼容性
2. 现有 Engine 的 1:1 行为（559 例差分测试 = 不变量）
3. 内容渲染正确性（内容不丢 > 视觉漂亮）
4. 扩展生态兼容性
5. 主题 / CSS / UI 内容兼容性
6. Android 性能与稳定性
7. EmberInn 自己的视觉设计

禁止为 Compose 纯度、架构洁癖、Material 规范牺牲以上任何一条。

---

## 一、区域裁决表

| 区域 | 裁决 | 说明 |
|---|---|---|
| engine/ 全部 | **[PROTECTED]** | 差分资产，一行不改 |
| 官方格式数据文件（角色/世界书/预设/正则/QR/jsonl 聊天） | **[PROTECTED]** | 正典存储，格式即兼容 |
| 旧渲染器（RenderNodeCompose/CssStyleParser 显示链/isStaticHtml 分流） | **[DELETE]** | WebView 内核替代 |
| 旧主题体系（ThemePreset/BackdropSpec/ArtBackdrop/VibePreset 24 套） | **[DELETE]** | 官方主题机制替代，不迁移不转换 |
| 旧 ChatScreen/ChatViewModel/设置页/首页 | **[REWRITE]** | 从零重写 |
| WebViewPool 旧实现 / interactiveCards 特殊分支 / MarkdownCache / mikepenz 定制 | **[DELETE]** | 新内核统一接管 |
| 引擎 render 包（HtmlSanitizerEngine 等） | **[KEEP-UNUSED]** | 文件保留（差分资产），显示路径不再消费 |
| SlashEngine / 变量宏 / STscript | **[PROTECTED]** | 它就是 1:1 STscript 引擎，禁止重写 |

---

## 二、目标架构

```
┌─────────────────────────────────────────────────────────┐
│ feature/（Compose 全新壳层）                              │
│   home / chat / character / worldbook / preset /         │
│   persona / settings / extensions / thememanager         │
│   视觉 = EmberDesignSystem 语义令牌                       │
├─────────────────────────────────────────────────────────┤
│ ui/design/  EmberDesignSystem                            │
│   语义令牌：bg/surface/surface2/surfaceSink ·             │
│   ink/inkSoft/inkMute · line/lineStrong ·                │
│   accent/accentSoft/accentBg · ai/aiSoft/aiBg ·          │
│   success/warning/danger + shape/spacing/motion           │
│   Material3 仅为底层实现，不决定视觉                       │
├─────────────────────────────────────────────────────────┤
│ renderer/（重写核心）                                     │
│   RenderKernel：assets 内置官方渲染资产（见 §三）           │
│   WebViewPool（上限 8，视口回收，进程崩溃自愈）             │
│   WebBridge：白名单 API，双向协议严格定义                   │
│   ThemeInjector：官方主题 JSON → CSS 变量 + custom_css     │
├─────────────────────────────────────────────────────────┤
│ extension/（一级架构，第一天就建）                          │
│   loader（manifest 解析，对齐官方字段）/ registry /        │
│   sandbox（WebView 宿主 + 权限门）/ compat-api（st-shim）  │
├─────────────────────────────────────────────────────────┤
│ runtime/                                                 │
│   ChatRuntime · GenerationRuntime · StreamingRuntime ·   │
│   VariableRuntime（薄壳，语义在引擎）· MediaRuntime        │
├─────────────────────────────────────────────────────────┤
│ core/                                                    │
│   TavernEngineAdapter（薄适配：类型转换/生命周期/错误映射）  │
│   UI/Renderer/Extension 一律经 Adapter，禁直接依赖引擎内部  │
├─────────────────────────────────────────────────────────┤
│ data/                                                    │
│   files/：官方格式文件 = 正典存储（不搬 Room）              │
│   datastore/：仅偏好设置/功能开关                           │
│   index/（可选 Room）：仅搜索索引、请求日志等派生数据        │
│   importexport/：官方格式互导（引擎能力 + 薄封装）           │
├─────────────────────────────────────────────────────────┤
│ platform/  android / webview / filesystem / media         │
├─────────────────────────────────────────────────────────┤
│ engine/  [PROTECTED] 不动                                 │
└─────────────────────────────────────────────────────────┘
```

---

## 三、渲染内核（最高优先级）

### 3.1 构成

| 资产 | 来源 | 说明 |
|---|---|---|
| kernel.html | 自研薄模板 | CSP 元数据 + 挂载点 + 资产引用 |
| style.css + fontawesome.min.css | **官方逐字** | 6456 行 + 161 CSS 变量原样生效 |
| showdown / DOMPurify / highlight.js | **官方同版本** | 显示管线最后两步用官方原码 |
| webfonts（FA + NotoSans，~9.4MB） | 官方 | 按需裁剪子集 |
| message DOM 模板 | 官方 #message_template 复刻 | `.mes > .mesAvatarWrapper + .mes_block > .mes_text` 层级一致 |
| render.js | 自研薄胶水 | payload→官方管线→DOM→测高上报→事件转发 |
| st-api-shim.js | 自研 | 插件兼容垫片（§五） |
| themes/*.json | 官方内置若干 | Azure 等，直读 |

### 3.2 管线分工（与官方逐段对齐）

```
raw mes（jsonl 原文，永不覆盖）
  ↓ 宏替换 / 正则位点 / fixMarkdown 决策 / encode_tags / reasoning 转义
  ← 引擎 MessageFormattingEngine（805 例差分锁死）[PROTECTED]
  ↓ converter.makeHtml + DOMPurify.sanitize + decodeStyleTags('.mes_text ')
  ← 内核内官方原版 JS [零移植]
  ↓ 官方 style.css + 主题 CSS 变量 + custom_css
  ← WebView Blink 渲染 [像素级同源]
```

### 3.3 关键机制

- **资源服务**：WebViewAssetLoader 挂 `https://appassets.androidplatform.net/`（assets=内核，data=头像/媒体/主题包），统一 https origin
- **流式**：流式中轻量增量更新（节流 120ms，跳过全量消毒）；流结束权威全量管线。长消息流畅度反超官方
- **消息粒度池化**：每条可见消息一个池化 WebView 实例（上限 8，视口进出回收，模板一次加载 postMessage 换内容）；渲染进程崩溃 onRenderProcessGone 自动重建，**不丢 raw 内容**
- **原始内容永久保留**：渲染模型只是 raw 的投影，Renderer 升级可随时重渲染

---

## 四、主题运行时（双层，互不污染）

```
官方主题 JSON（34 字段）           EmberInn 主题（自家格式）
      ↓ ThemeInjector                   ↓ TokenMapper
CSS 变量注入 + custom_css         EmberDesignSystem 令牌
      ↓                                 ↓
WebView 内容区（酒馆语义）          Compose 壳层（自家视觉）
      └──────── 互不污染 ────────────────┘
```

- 导入/导出官方 theme.json 逐字支持（含 custom_css、blur_strength、chat_width 等）
- 壳层配色从活动主题提取 seed 派生，用户可覆盖
- 旧 24 套主题直接删除（用户决策：不保留）

---

## 五、扩展运行时

### 5.1 JS Runtime 决策（记录选型理由）

**选定：WebView 内核即 JS 宿主（V8 + 真 DOM）。否决 QuickJS 作为主方案。**

理由：酒馆助手生态的卡片脚本运行于 iframe、依赖 DOM/CSSOM/jQuery（JS-Slash-Runner 机制核实）。QuickJS 无 DOM，模拟假 DOM 的成本高于收益且兼容性必然劣于真 DOM。本决策满足"没有真 JS Runtime 无法实现扩展兼容 → 必须内置"的要求——WebView 的 V8 就是真 Runtime。QuickJS-ng 留作未来"无 DOM 后台脚本"（定时任务类）的备选，本期不做。

### 5.2 兼容四层

| 层 | 内容 | 方案 |
|---|---|---|
| T1 数据互操作 | 卡/世界书/正则/QR/预设/人设/jsonl | 引擎已有 [PROTECTED] |
| T2 卡内交互 HTML | ``` 围栏卡/状态栏/表单/动态立绘 | 消息即在 WebView，围栏代码直接执行 |
| T3 酒馆助手式脚本 | 全局/角色脚本、MVU、EJS、事件钩子 | st-api-shim.js：eventSource（对齐官方 event_types 事件名）+ triggerSlash→引擎 SlashEngine 桥 + get/setVariables→ChatStore + getContext() 只读快照（chat/characters/chat_metadata/name1/name2…） |
| T4 服务端依赖扩展 | ChromaDB vectors 等 | 标注 SERVER_REQUIRED；按需内嵌 nano HTTP 复刻端点（远期） |

### 5.3 安全模型（放开优先——用户决策：默认全开，交互媒体不设限）

- **JS 默认开启**：卡片脚本、酒馆助手式脚本直接跑，无需确认；设置里保留一个「严格模式」开关供少数用户自选收紧，但默认路径零打扰
- **CSP 默认 FULL**：外链图片/音视频/字体/网络请求全放行，媒体自动加载自动播放策略从宽；不做逐次联网确认
- **iframe 放开**：allow-scripts + allow-same-origin 按需给足，保证酒馆助手类卡片的 iframe 内脚本能正常工作
- **localStorage 正常持久化**（WebView origin 稳定，天然可用），不用内存垫片
- WebBridge 保留白名单（这是技术边界而非安全洁癖）：openLink / copyText / saveMedia / hapticFeedback 等实用能力主动提供，让卡片交互能触达系统能力；Android API / 文件系统任意访问仍不经由桥暴露（WebView 架构上也不存在此通道）
- 崩溃隔离保留：单卡渲染崩 → 降级显示源码，Chat/Engine 不受影响（这是稳定性不是限制）
- raw 内容永久保留不变
- Server Plugin 明示 REQUIRES_SERVER（信息透明，非限制）

### 5.4 扩展管理

Extensions 页：已装/可用/更新/停用/权限面板；来源 = 官方 content repository + Git URL；安装前展示 source/author/version/permissions/risk。
**EXTENSION_COMPATIBILITY.md** 维护逐扩展状态：SUPPORTED / PARTIAL / WEB_ONLY / SERVER_REQUIRED / UNSUPPORTED——不做没测过的"100% 兼容"宣称。

---

## 六、验证体系

1. **引擎 559 例全绿** = 每次提交的硬门禁（不动区）
2. **DOM 黄金对比**：扩展现有 Puppeteer probe 基建——同输入官方浏览器 vs 我方内核，断言 messageFormatting 输出逐字一致 + 关键节点 computedStyle 一致 + 主题变量逐值一致
3. **渲染语料库**：MD/HTML/CSS/table/details/spoiler/嵌套 HTML/畸形 HTML/正则宏/复杂开场白/世界书注入/扩展生成内容/LaTeX/交互卡 ≥20 类真实样本，进 CI 快照
4. **视觉回归**：官方 vs EmberInn 同输入对比（内容完整性一票否决，像素差异阈值告警）
5. **真卡回归集**：10 张社区典型卡（纯 MD/状态栏/iframe 游戏/MVU/EJS 各 2）

---

## 七、实施阶段（8 期，每期独立可交付）

| 期 | 内容 | 出口标准 |
|---|---|---|
| **P0** | 审计文档定稿（本文件 + ENGINE_BOUNDARY + COMPATIBILITY_MATRIX 骨架）；内核资产从官方源码提取打包 | 资产清单冻结 |
| **P1** | RenderKernel PoC：kernel.html + AssetLoader + render.js + 主题注入 + DOM 对比 harness | 单消息 DOM/computedStyle 对比全绿 |
| **P2** | ChatSurface：池化列表 + 流式通道 + WebBridge 手势桥 + 媒体/头像接入 | 真机全链路；10 张回归卡正确 |
| **P3** | 主题运行时 + EmberDesignSystem 令牌 | 官方 5 套主题导入变量逐值一致；custom_css 生效 |
| **P4** | 扩展运行时 v1：loader/registry/权限/safe mode + st-api-shim（事件/slash 桥/变量/getContext） | 2 张 MVU 卡 + 2 个酒馆助手脚本免改运行 |
| **P5** | 数据与运行时重构：TavernEngineAdapter 落位、runtime 包重组、删除旧渲染器/旧主题/旧 ChatViewModel | 区域裁决表全部达成；引擎测试仍全绿 |
| **P6** | 壳层全重写：首页/角色/会话/设置/扩展管理新 UX | 功能覆盖 HANDOFF 4/5 章清单 |
| **P7** | 语料库+视觉回归 CI、性能打磨（池调优/预热/首帧<800ms）、EXTENSION_COMPATIBILITY.md | 全部门禁绿 |

每期结束报告：修改/新增/删除清单、引擎是否变化（必须为否）、四类兼容性变化、测试结果、未解决项。

---

## 八、错误隔离与生命周期铁律

- Extension 崩 ≠ Chat 崩；WebView 崩 ≠ Engine 崩；Renderer 错 ≠ 丢 raw；Provider 错 ≠ 坏历史
- 旋转/进程死亡/后台回归不依赖 Activity 存核心状态；会话状态可从正典文件完整恢复
- 长文本聊天：池化+虚拟化+渲染缓存，掉帧 <5%

---

## 九、验收清单（35 条外部指令验收全盘采纳，关键摘录）

引擎不破坏 / 1:1 不破坏 / 卡·书·正则·变量高兼容 / prompt 不受 UI 影响 / MD·HTML·CSS·Theme 高兼容 / QR·STscript 高兼容（引擎实现）/ JS 扩展有沙箱兼容层 / WEB_ONLY·SERVER_REQUIRED 明确标注 / raw 永不丢 / Renderer·Extension·Engine 可独立测试 / UI 不依赖引擎内部 / M3 不决定视觉 / Chat 不再巨型 ViewModel / Theme 不再常量堆 / 扩展不触 Android / WebView 不是补丁 / 复杂卡不丢内容 / Swipe·Branch·Checkpoint 正常（视图层，存储扁平 1:1）/ 导入导出酒馆互通

**产品定位**：不是"套漂亮 UI 的酒馆客户端"，也不是"酒馆 Android 复刻"，而是——**内部真正理解酒馆生态、拥有自主 Android UX 的 AI Roleplay Platform**。

Tavern compatibility first. Rendering compatibility first. Extension compatibility first.
Android-native where it is safe. EmberInn design everywhere it does not conflict with compatibility.
