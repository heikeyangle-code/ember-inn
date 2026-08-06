# EmberInn · 余烬酒馆

**全新原生 Android 酒馆客户端**——以 SillyTavern 兼容为核心，参考官方源码翻译重写，不做 WebView 壳，不搬运旧项目代码。

> ember（余烬/炉火）+ inn（酒馆）：每一个角色都是一炉火，故事在余烬里继续。

## 为什么重写（决策记录）

- **放弃 RikkaHub fork 路线**：酒馆格式属于“重实现”，与官方行为持续漂移，上游合并代价高，历史包袱重。
- **否决“官方服务端当引擎”**：源码实证——SillyTavern 的兼容内核（世界书扫描、宏展开、斜杠、提示词组装、群聊）全部位于**前端 JS**（`public/scripts/world-info.js`、`macros/engine/`、`slash-commands/`、`script.js`），与 DOM 焊死；Node 服务端只是存储 + API 转发层。只保留服务端并不能白拿兼容性。
- **否决 WebView 壳**：要求真原生客户端与全新 UI，不套官方网页。
- **最终路线**：新项目、Kotlin + Compose、参考官方源码**翻译 + 重写**酒馆逻辑层；官方 Node 服务端仅作为可选的存储/API 辅助进程（无浏览器界面）。

## 兼容目标（以官方行为为基准，回归测试锁定）

- **角色卡**：PNG V2/V3（tEXt/ccv3）、CharX、JSON 导入导出
- **世界书**：关键词扫描、注入位置（before/after char）、深度、递归、粘性/冷却、分组评分、向量化
- **宏系统**：`{{user}}`、`{{char}}`、`{{random}}`、`{{pick}}`、`{{roll}}`、`{{if}}`、变量宏等（对齐 Macros 2.0）
- **斜杠命令**：官方常用命令全量，行为一致
- **群聊**：多角色、多种响应模式
- **提示词组装**：角色字段 + 示例对话 + 世界书 + 作者注释 + 历史消息（与官方 `script.js` 行为一致）
- **预设 / 人设 / regex 脚本 / tokenizer / SSE 流式**

功能覆盖总清单见 [docs/FEATURES.md](docs/FEATURES.md)（对照官方 release 8172dcd，含优先级与 UI 映射）。本地官方源码：`~/sillytavern-ref`（release 分支，供翻译与回归对照）。

## 架构

```
app        Compose UI（角色列表、聊天、设置、主题）
engine     酒馆领域引擎（纯 Kotlin，不依赖 UI）
           ├─ CardParser（V2/V3/CharX/JSON）
           ├─ WorldBookScanner（关键词/深度/递归/分组）
           ├─ MacroEngine（lexer/parser/evaluator）
           ├─ SlashParser（解析 + 执行）
           └─ PromptAssembler（提示词组装）
data       Room / DataStore / 文件存储
provider   LlmProvider 接口 + 服务商注册表（数据驱动 JSON）
services   TTS / STT / 图像 / 向量 / 翻译 接口
theme      全局主题 + 角色卡驱动主题
```

### 关键接口（可插拔，不写死核心）

| 接口 | 说明 |
|---|---|
| `LlmProvider` | OpenAI-compatible / Anthropic / Gemini / 自定义，协议实现类很少变 |
| `CardParser` | V2 / V3 / CharX，为未来 V4 留口子 |
| `TtsProvider` / `ImageGenProvider` / `VectorStoreProvider` / `TranslatorProvider` | 后端可插拔 |
| `ThemeSource` | 全局预设 / 角色卡取色 |
| 扩展执行层 | 先做自己的插件 API；ST 前端扩展兼容为远期方案（QuickJS+API shim 或兼容模式），不承诺 |

### 服务商注册表（数据驱动）

每条记录为数据而非代码：`id / display_name / protocol / auth_type / base_url / region_variants / extra_headers / api_version / models_endpoint / default_models / requires_key`。

已按官方酒馆源码（2026 版）核实：OpenAI、Anthropic、Gemini（AI Studio/Vertex）、DeepSeek、OpenRouter、Groq、Ollama（本地）、Mistral、xAI、Moonshot、SiliconFlow（国内/国际）、Z.AI（通用/编码）、MiniMax（国内/国际 + GroupId）、Fireworks、Perplexity（无 /v1 前缀）、Cloudflare Workers AI、Azure OpenAI（deployment + api-version）。

## UI 与主题

### 设计规范

- **Material 3 Expressive** + 动态取色 + 移动优先分层导航
- **信息架构**：底部导航（角色 / 聊天 / 设置）+ 独立二级设置页；不照搬 ST 桌面多面板
- **大屏自适应**：手机单栏底部导航；平板 / 折叠屏双栏（列表 + 聊天）
- **渲染**：Markdown（mikepenz renderer）、LaTeX（huarangmeng/latex）、代码高亮（Highlights）、Mermaid / 复杂 HTML 用局部 WebView 兜底、流式消息增量渲染

### 主题系统三层结构

```
第一层 全局主题（用户设置）：浅色 / 深色 / 跟随系统 + 预设主题 + 字体 / 圆角 / 密度 / 气泡样式 / 背景模糊开关
第二层 角色卡驱动（核心卖点）：每张卡一套观感 —— 配色 / 背景 / 气泡色 / 名字色 / 形状 / 字体
第三层 状态微调：流式生成微光、深水区/夜间可单独锁深色
优先级：角色卡 > 全局预设 > 系统默认
```

### 配色流程（角色卡接管主题）

1. 卡图 → **Palette** 取色（浅色取 `Muted` 低饱和色，深色取 `Vibrant` 高饱和色）
2. 取到的颜色作为 seed → **MaterialKolor** 生成整套 M3 配色（light / dark 两套）
3. 无头像卡 → 用卡名哈希生成稳定 seed 色（可复现、每卡不同）
4. 结果缓存到 Room（角色表 `theme_seed` / `bg_uri` / `mode_override`），只算一次
5. 切换角色：配色 `animateColorAsState` 过渡 + 背景 crossfade（200–300ms）

### 背景系统（敲定）

- **默认 = 氛围渐变**：从卡图取 2–4 个颜色 → 低饱和 Mesh Gradient + 光晕（**不是主图**，是主图的光），浅色/深色下都干净
  - 实现：官方 `androidx.compose.ui.graphics.MeshGradient`（已入 Compose UI，无需第三方库）；动画版参考 ComposeMeshGradient
- **可选 = 卡图玻璃背景**：主图 + 模糊 + 遮罩（深色叠 60–75% 暗色，浅色叠 25–35% 白/纸色），保证文字可读
- 每张卡独立记忆，可随时切换 / 关闭

### 玻璃表面（2026 液态玻璃方向）

- 顶栏 / 输入栏 / 浮层 / 对话框：`blur` + 半透明 + 1px 高光描边 + 轻微内阴影
  - 实现：**skydoves/Cloudy**（KMP 模糊 + 液态玻璃，GPU 加速 + 旧设备 CPU 降级）；备选 Haze（可调降采样）、miuix-blur（自适应降采样）
- **正文区保持干净，不全屏玻璃**（可读性优先）
- 依据：iOS 26 Liquid Glass 引发全行业跟进，国产安卓 2026 年集体上新玻璃 UI；安卓官方暂不跟进 → 第三方 App 的差异化机会

### 四维主题配方（颜色 + 形状 + 动效 + 排版）

- **形状**：每张卡可带 圆润 16dp / 方正 4dp / 浑圆 24dp
- **动效**：M3 Expressive spring 弹性动效
- **字体**：中文氛围字体可下载（霞鹜文楷、思源宋体等），默认系统字体
- **表面染色**：背景表面带角色主色 tint（Android 16 / Expressive 方向），纯黑仅作可选

### 预设主题（按中国人审美）

| 主题 | 风格 | 浅色底 | 深色底 | 点缀色 |
|---|---|---|---|---|
| 墨韵 | 水墨风 | 宣纸白 | 墨黑 | 朱砂红 |
| 青瓷 | 雅致 | 米白 | 青墨 | 青绿 |
| 夜航 | 沉稳 | 雾白 | 深蓝黑 | 琥珀 |
| 丹砂 | 热烈 | 纸白 | 暖黑 | 丹红 |
| 琉璃 | 现代玻璃 | 冰白 | 玻璃黑 | 渐变紫蓝 |
| 简约纸感 | 极简 | 象牙白 | 石墨 | 中性灰 |

### 默认偏好（中文用户操作习惯）

- **默认浅色**（暖纸色底，不用纯白 `#FFFFFF`），深色“跟随系统”可选
- 消息布局：**AI 左侧、用户右侧**（微信式），头像 / 气泡 / 时间戳对齐
- 底部导航 + 返回手势 + 下拉刷新 + 长按菜单（复制 / 编辑 / 删除 / 重新生成）
- 设置项**中文为主**，字号调节、夜间模式自动、免打扰
- 分享 / 导出路径用中文提示（“已导出到…”）

### 动态色基线

- 未导入卡 / 未取色时：全局默认跟随系统壁纸动态色（Material You）
- 角色卡 seed **永远优先覆盖**

### 主题配方可分享

- 每张卡的完整配方（seed + 背景 + 形状 + 字体）可导出 / 导入 / 分享

### 主题组件清单（最强现成件）

| 用途 | 组件 | 坐标 |
|---|---|---|
| 主题框架 | Material3 基线 1.4.0 稳定版（Expressive 组件在 1.5.0-alpha，等稳定后再启用） | `androidx.compose.material3:material3` |
| 种子色 → M3 配色 | MaterialKolor **4.1.x 稳定版**（勿用 5.0 alpha） | `com.jordond.materialkolor:*` |
| 氛围渐变 | 官方 MeshGradient（动画版参考 ComposeMeshGradient） | `androidx.compose.ui.graphics.MeshGradient` |
| 玻璃 / 模糊 | **skydoves/Cloudy**（GPU + CPU 降级）；备选 Haze、miuix-blur | `dev.skydoves.cloudy:*` |
| 卡图取色 | Palette / landscapist-palette | `androidx.palette:palette` |
| 图片加载 | Coil 3 | `io.coil-kt.coil3:coil-compose` |
| 动效 | Lottie | `com.airbnb.android:lottie-compose` |
| 图标 | Material Symbols | `androidx.compose.material:material-icons-extended` |
| 中文字体（可下载） | 霞鹜文楷 Screen/Lite、霞鹜新晰黑（OFL 开源） | 可下载字体包 / Google Fonts Provider |
| 主题切换动画 | `animateColorAsState` + Crossfade | 内置 |
| 持久化 | Room + DataStore | androidx |

### 实现要点与坑

1. 取色必须在 IO 线程跑一次并缓存，禁止 UI 线程执行
2. 背景模糊用 1/4 尺寸位图，禁用原图，避免滚动掉帧
3. 所有背景上的文字必须叠遮罩（浅色卡图 + 白字 = 灾难）
4. 主题数据（seed / 背景 / 形状 / 字体）跟随角色卡导入导出
5. 设置页做实时预览（选主题直接看到效果）
6. MaterialKolor 已移除 Expressive 支持（4.0+）：配色走基线 M3，Expressive 只做组件/动效层，两者不冲突
7. 生产依赖 M3 1.4.0 稳定版；Expressive 组件（1.5.0-alpha）仅在尝鲜分支启用，不进入主线
8. 中文字体用屏幕版/轻便版（霞鹜文楷 Screen/Lite），完整版体积过大，作可下载项

### 格调守则（好看、有格调、不花哨）

1. **配色系统 = 1 个主色 seed**（生成整套 M3 色调，约 20+ 色值，和谐由源头统一）；**背景氛围 = 取 2–4 个低饱和色**（负责丰富与辨识度）——界面克制、背景出彩
   - 可选“副 seed”：取卡图第二主色降饱和作 tertiary（第三色），默认关闭，防花哨
2. 背景氛围渐变一律**低饱和**（取色后降饱和 30–40%），光晕克制；禁止高饱和霓虹渐变球（2026 已俗）
3. 玻璃效果只用于浮层 / 输入栏 / 顶栏，**正文区永远干净**；同屏玻璃元素不超过 2–3 处
4. 每屏只有一个视觉焦点：角色图或聊天内容，不叠加抢眼
5. 动效 200–300ms，spring 只用于交互反馈；常驻动画仅限生成微光
6. 深色默认 **tinted 灰**（Android 16 方向），纯黑仅作可选
7. 排版 2–3 档字号、行高 1.5–1.6、对比度达标
8. 默认配方保持克制；用户自定义“花哨”是用户自由，但不进默认主题
9. 默认系统字体；氛围字体（霞鹜文楷等）作可下载可选

### 美学设计原则（2026 调研落盘）

**高级感 = 克制（Quiet Luxury，2026 主流方向）**
- 奢侈品设计的信号不是装饰，是留白、清晰层级、有限色彩强调、克制对比——安静界面没有地方藏弱层级
- “极简做对”的标准：**3 色上限**、排版当主角、留白是主动设计（不是空白）
- 每屏**一个强调方向**（one accent direction at a time），一次只突出一个东西

**色彩**
- 界面主色 3 色以内：1 个 seed 生成的主色 + 中性底色 + 背景氛围的低饱和色
- 低饱和 = 高级；高饱和只做点缀（按钮/链接/名字色），面积越小越好
- 预设主题对应情绪：墨韵=水墨留白 · 青瓷=雅 · 夜航=沉稳 · 丹砂=热烈但克制 · 琉璃=现代玻璃 · 简约纸感=quiet luxury

**中文美学基因（留白/水墨/雅）**
- 中国 UI 设计的传统是“计白当黑、以无胜有”：视觉焦点靠**留白引导**，不靠装饰堆砌
- 水墨的用法：黑白灰墨韵打底 + 少量朱砂级点缀，不堆具象元素
- 聊天页应是大面积留白 + 低饱和氛围光 + 少量角色色强调——这就是“雅”

**排版**
- 全 App ≤ 2 种字体（默认 1 种系统黑体 + 可选氛围字体）；每屏 ≤ 3 种字重/字号组合
- 字号、字重、颜色是建立层级的三个杠杆，优先于一切装饰
- 中文行高 1.5–1.6，正文 16sp，标题 20–24sp，说明 12–13sp

**动效与材质**
- 柔和动效（soft motion）：200–300ms，一次只动一个东西；spring 仅用于交互反馈
- 材质诚实：玻璃只做表面（浮层/输入栏/顶栏），正文区永远干净；阴影/光晕克制
- 生成微光是唯一允许的常驻动画，亮度低、节奏慢

### 版本策略（尽量用最新版）

- 生产依赖一律使用**当前最新稳定版**；仅有 alpha/beta 才支持的功能放“尝鲜分支”或功能开关，不进主线
- 2026-08 实测基线：Compose BOM 最新稳定版 · material3 1.4.0（Expressive 1.5.0-alpha25 仅尝鲜）· MaterialKolor 4.1.x（5.0 为 alpha，不用）· Coil 3.5.0 · Lottie 6.6.x · Palette / Room / DataStore / Navigation 跟最新稳定版
- 升级流程：Renovate / Dependabot 每周自动 PR → CI 通过自动合 patch/minor → major 人工看 changelog + 全量回归
- 小库失活预案：直接把开源源码搬进项目（vendoring），不守死库

## 启动与首启体验

### 首次打开（Onboarding）

- **品牌开场**：炉火余烬微光动画（Lottie，低饱和、1.5–2 秒）→ 淡入欢迎页；仅首次，之后不再播放
- **欢迎页**：「欢迎来到余烬酒馆」+ 副标题“每个角色都是一炉火，故事在余烬里继续”
- **两个主选项**（玻璃卡片，大按钮，低饱和氛围渐变背景）：
  - 「导入角色卡」→ 直接打开文件选择器（PNG / JSON / CharX）
  - 「直接开始聊天」→ 进入「AI 对话」并带一句默认开场（“我是余烬，想聊点什么？”）
- 底部一行小字：**数据仅保存在本地**（信任信号）
- 允许「跳过」→ 进入角色列表空状态

### 日常打开

- Android 12+ 原生 Splash（品牌图标 + 品牌色），**不播自定义长动画**（每次开 App 播动画是烦人）
- 直接进角色列表首页：AI 对话（置顶）→ 最近聊过 → 全部角色
- 设置可选：「启动时直接进入上次聊天」（默认关）

### 新建空白聊天

- 角色列表置顶「AI 对话」：点开即新会话
- 聊天 Tab：「+」新建对话（默认 AI 对话，可改选角色）
- 角色内：顶栏菜单「新会话」，每个角色可开多个空白会话
- 空白聊天 = 无历史的新会话；主题沿用当前角色配方或全局主题

## 设置架构（两级：主设置 + 角色设置）

```
主设置（底部导航「设置」Tab）＝所有人的公共环境
├─ 外观与主题：浅/深/跟随、全局预设主题、字体、圆角密度、背景模糊、启动行为
├─ 提供商与模型：连接档案（地址/Key）、全局默认模型、默认采样参数、代理
├─ 语音：TTS 朗读、STT 语音输入
├─ 服务：翻译、图像生成、向量库、搜索
├─ 数据与隐私：存储位置、备份/导出、清缓存
└─ 关于：版本、许可、开源仓库

角色设置（每张卡单独，从角色详情页进）＝只管这一个角色
├─ 角色信息：名字/头像/卡字段（描述、性格、场景、示例对话、系统提示、PHI…）
├─ 世界书：内嵌（导入随卡带入）+ 可挂外置
├─ 正则 / 变量 / 快捷回复：本角色专用
├─ 模型覆盖（可选，默认收起）：连接档案、采样、上下文长度
├─ 主题配方：seed、背景、形状、字体、风格档位、浅/深锁定
└─ 会话：聊天列表、新会话、导出

「AI 对话」＝无卡角色，同样有自己的角色设置页（名字/头像/系统提示词/世界书/模型覆盖/主题）
```

**重叠项规则**：模型、正则、世界书、预设 = 默认“跟随全局”，角色页可“本角色覆盖”；主题 = 默认跟随全局，角色卡自动生成配方覆盖，可一键恢复；卡字段 = 只属于角色，主设置不出现。

## 技术栈

Kotlin · Jetpack Compose · Material3（含 Expressive）· Navigation Compose · Room · DataStore · Coil3 · Lottie · Koin · kotlinx.serialization

渲染与主题：multiplatform-markdown-renderer · latex-renderer · Highlights/KodeView · androidx.palette · MaterialKolor

## 路线图

- **P0 骨架**：工程、主题系统、导航、角色列表/详情、V2/V3/JSON 导入
- **P1 聊天**：消息流、流式渲染、气泡、滑动/长按交互、聊天存储
- **P2 引擎**：世界书扫描注入、宏引擎、提示词组装、tokenizer
- **P3 功能**：斜杠、群聊、预设、人设、作者注释、regex
- **P4 主题**：角色卡取色驱动、模糊背景、毛玻璃、预设主题完成
- **P5 服务**：TTS/STT/图像/翻译/向量、服务商注册表完善
- **P6 扩展**：自有插件 API + 官方行为回归测试体系

## 兼容性守则

1. 每个引擎模块配“官方行为回归测试”：同一输入，官方输出 vs 本项目输出。
2. 核心引擎不依赖 UI 层。
3. 服务商注册表只改数据，不改协议代码。
4. 保持小步提交，CI 全量验证后再合入。

## 许可

AGPL-3.0（参考/翻译 SillyTavern 源码，派生义务；分发必须开源）。
