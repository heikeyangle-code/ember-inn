# 组件清单（2026-08-08 调研落盘）

> 原则：**展示层/数据层用最强现成组件；酒馆逻辑层（角色卡/世界书/宏/斜杠/提示词组装）没有现成组件，按官方源码翻译进 engine。**
> App 接线时每个组件都标注“官方源码位置”——官方用什么，我们就在对应渲染点用什么能力。

## ✅ 有现成组件（可直接依赖，版本均为 2026-08 最新稳定）

| 类别 | 组件 | 版本 / 坐标 | 为什么选它 | App 接入点 | 官方源码位置 |
|---|---|---|---|---|---|
| UI 框架 | Jetpack Compose + Material3 | Compose BOM 2026.06.01；material3 **1.4.0 稳定**（1.5.0 是 Expressive alpha，不推荐进主线） | 官方 M3 组件全家桶，主题三件套现成 | 全部页面 | 官方是 Bootstrap/jQuery，UI 层我们自由（README 守则） |
| 导航 | Navigation Compose 2.9.x（备选：Navigation3 1.1.5，KMP 类型安全，纯 Android 暂不启用） | androidx.navigation:navigation-compose | 单 Activity + 返回栈 | 底部三 Tab、二级设置页、聊天页 | UI 层自由 |
| 返回手势 | activity-compose **1.13.0** + `PredictiveBackHandler`；Compose MPP 1.10 用 `NavigationBackHandler` | androidx.activity:activity-compose（项目已有） | Android 13+ 预测性返回动画，微信式侧滑 | 聊天页/设置子页逐级回退（已接 BackHandler，P0 换官方预测性 API 美化） | UI 层自由（README 守则 7：手势符合直觉） |
| 消息 Markdown | **multiplatform-markdown-renderer 0.43.0**（2026-07-27，Compose Multiplatform + Coil3） | com.mikepenz:multiplatform-markdown-renderer | 社区最强 KMP Markdown 渲染，表格/任务列表/代码块/图片全支持，和 Coil3 原生集成 | 聊天气泡、角色详情字段预览、世界书内容预览 | 官方 Showdown + highlight.js + DOMPurify（我们原生渲染替代） |
| 图片加载 | Coil3 **3.5.0**（项目已有）+ **coil-gif** | io.coil-kt.coil3:coil-compose / coil-gif | KMP 图片加载事实标准，支持 GIF/网络/本地/占位 | 角色头像、卡图、消息图片、GIF 表情、世界书条目图 | 官方 `<img>` + 头像路径逻辑（`public/scripts/characters.js`） |
| 音视频渲染 | Media3 ExoPlayer **1.10.0**（2026-03-25 stable；1.11 尚在 beta/alpha，不用） | androidx.media3:media3-exoplayer + media3-ui | 官方 Android 播放器标准，HLS/DASH/本地/网络全支持 | 消息里的 audio/video 附件（官方消息 extra.media → Media3），附件面板 | 官方消息媒体是 URL/data URI + `<video>`/`<audio>`（`public/scripts/openai.js` Message.addVideo/addAudio） |
| 动效 / 表情 | Lottie **6.7.1**（官方 Lottie Compose） | com.airbnb.android:lottie-compose | 官方动画库，角色表情精灵/品牌动效/生成微光 | 首启品牌动画、角色表情（ExpressionEngine 选图后播放）、空状态微光 | 官方表情精灵是 DOM 图片切换（`public/scripts/expressions/`），我们用 Lottie 增强 |
| 设置持久化 | DataStore Preferences **1.2.1** | androidx.datastore:datastore-preferences | 官方推荐的 KV 设置存储，协程安全，替代 SharedPreferences | 全局设置/主题模式/主题预设迁移（README 计划，现为 SharedPreferences，待迁移） | UI 层自由（官方 localStorage 键，迁移时映射） |
| 数据库 | Room 2.8.x | androidx.room:room-runtime + room-ktx | 官方 SQLite ORM，角色/会话/世界书/向量元数据 | 角色表（theme_seed/bg_uri/mode_override）、会话、聊天索引 | UI/数据层自由；引擎文件格式（characters/*.json、chats/*.jsonl）保持官方 |
| 依赖注入 | Koin 4.2.x | io.insert-koin:koin-android | 轻量，KMP 友好 | ViewModel/Repository 注册 | UI 层自由 |
| 序列化 | kotlinx.serialization 1.11.x | org.jetbrains.kotlinx | Kotlin 原生 JSON，引擎已在用 | 引擎出入参、设置、连接档案 | UI 层自由 |
| 代码高亮 | Highlights / KodeView | dev.snipme:highlights / KodeView | Kotlin 原生语法高亮 | Markdown 代码块 | 官方 highlight.js |
| LaTeX | huarangmeng/latex-renderer | io.github.huarangmeng:latex-renderer | 社区 KMP 公式渲染 | 消息公式（官方靠社区扩展，我们原生覆盖） | 官方社区扩展 |
| 模糊 / 玻璃 | **skydoves/Cloudy 0.7.1 稳定版**（KMP 模糊 + 液态玻璃，GPU + 旧设备 CPU 降级；1.0.0-alpha01 是 alpha 不用）；备选 Haze 2.0.0-alpha03 | com.github.skydoves:cloudy | 2026 液态玻璃方向，顶栏/输入栏/浮层用（正文区干净） | 毛玻璃顶栏、输入栏、BottomSheet、对话框（聊天页顶栏/输入栏已接） | UI 层自由（README 玻璃表面节） |
| 网格渐变 | 官方 MeshGradient | androidx.compose.ui.graphics.MeshGradient（已入 Compose UI，无需第三方） | 背景氛围渐变（2–4 色低饱和） | 聊天背景、卡片背景 | UI 层自由 |
| 取色 | androidx.palette | androidx.palette:palette | 卡图取色 seed | 角色卡 → theme_seed（已实现） | UI 层自由 |
| 种子色 → M3 配色 | MaterialKolor 4.1.x | com.jordond.materialkolor（5.0 是 alpha，不用） | 一套 seed 生成整套 M3 ColorScheme | 主题引擎 Theme.kt（已实现） | UI 层自由 |
| 图标 | Phosphor Icons（主推，Regular 字重）。现用内置 32 枚官方路径（scripts/gen-phosphor-icons.mjs 生成，APK 小、精确可控）；备用 Maven 包 com.adamglin:phosphor-icon:1.0.0（六字重全量 26.7MB，Kotlin 2.0.21 构建，想换可直接替换） / Material Symbols Rounded（备选）/ Lucide（内容级备选） | 内置 PhosphorIcons.kt（零第三方依赖） | 圆头现代，配“余烬/炉火”美学 | 全 App 图标（README 图标系统节） | UI 层自由 |
| 文件选择 | SAF / PhotoPicker | 系统 API | 导入卡（PNG/JSON/CharX）、附件、背景 | FAB 导入、附件面板 | 官方文件上传是 `<input type=file>`（`public/scripts/characters.js`） |
| 启动页 | SplashScreen API | androidx.core:core-splashscreen | Android 12+ 原生品牌启动 | MainActivity | UI 层自由 |
| QR | ZXing | com.google.zxing | 连接档案扫码导入导出 | 提供商设置页 | UI 层自由 |
| 拖拽排序 | Calvin-LL/Reorderable | sh.calvin.reorderable | 列表重排（世界书条目/快捷回复） | 世界书编辑页、快捷回复页 | 官方 jQuery UI sortable，我们原生替代 |
| HTTP | OkHttp 5.4.0（项目已有；备选：Ktor 3.5.1，KMP 跨端再考虑） | com.squareup.okhttp3:okhttp | 引擎已在用，自带代理支持、MockWebServer 测试 | LlmClient、模型列表、URL 导入、RAG 嵌入 | 官方 fetch / express（协议 1:1 在 engine） |
| OpenAI tokenizer | JTokkit 1.1.0 | com.knuddels:jtokkit | 精确 OpenAI cl100k/o200k | TokenCounterFactory（已接）；Claude/Gemini 回退待换官方 web tokenizer | 官方 `src/tokenizers.js` |
| 向量检索 | sqlite-vector（SQLite 扩展） | sqliteai/sqlite-vector | 本地 RAG 向量库，随 Android SQLite 走 | 世界书 RAG / 聊天重排 / 文件 Data Bank（引擎 FileVectorStore 对齐 vectra 目录，App 落盘） | 官方 vectra LocalIndex（`src/endpoints/vectors.js`） |
| STT / TTS | Android SpeechRecognizer / android.speech.tts | 系统 API + 各厂商 HTTP | 语音输入/朗读 | 输入区 🎤、消息朗读 | 官方 SillyTavern-Extras / 社区扩展，UI 层自由 |
| 后台任务 | WorkManager | androidx.work:work-runtime | 后台生成/备份/向量化不杀进程 | 长任务 | UI 层自由 |

## ❌ 没有现成组件（必须自己写 / 翻译官方源码，已全部进 engine）

| 模块 | 说明 | 官方参照（已翻译） |
|---|---|---|
| 角色卡解析 | PNG tEXt/ccv3、JSON V2/V3、CharX、YAML、BYAF | `src/character-card-parser.js` + `src/charx.js` + `src/byaf.js`（✅ engine CardParser，差分 48 例） |
| 世界书扫描/注入 | 关键词、深度、位置、递归、粘性、冷却、分组、向量化 | `public/scripts/world-info.js`（✅ engine WorldBookScanner + RAG） |
| 宏引擎 | lexer/parser/evaluator、{{if}}、变量宏、作用域宏 | `public/scripts/macros/engine/`（✅ engine MacroEngine，差分 158 例） |
| 斜杠命令 | 解析 + 执行（150+ 官方命令） | `public/scripts/slash-commands/`（🟡 engine SlashParser + 少数命令，多数需 App 状态） |
| 提示词组装 | 角色字段+示例+世界书+作者注释+历史 | `public/scripts/openai.js` + `script.js`（✅ engine PromptAssembler + ChatCompletionPipeline） |
| 群聊调度 | 多角色、模式、激活、深度提示 | `public/scripts/group-chats.js`（✅ engine 激活/合并/深度/循环） |
| 多模型 tokenizer | JTokkit 只覆盖 OpenAI；Claude/Gemini 需官方 web tokenizer | 官方 `src/tokenizers.js`（🟡 回退 cl100k，P2） |
| 表情分类 | Fuse 模糊匹配、本地 BERT/WebLLM 分类 | 官方 `public/scripts/expressions/` + 社区扩展（🟡 预处理已差分，分类 API 服务层） |
| 角色卡驱动主题引擎 | Palette+MaterialKolor+MeshGradient 拼装，逻辑自己写 | 我们的增强（UI 层自由） |
| 官方前端扩展兼容 | JS/DOM 扩展沙箱 | 远期（QuickJS+shim 或兼容模式） |

## 版本基线（2026-08-08 实测确认）

- Compose BOM 2026.06.01 · Kotlin 2.4.10 · AGP 9.3.1（项目 `gradle/libs.versions.toml`）
- material3 **1.4.0 稳定**（Expressive 1.5.0-alpha25 仅尝鲜分支）
- Coil3 **3.5.0** · Lottie **6.7.1** · Media3 **1.10.0** · DataStore **1.2.1** · activity-compose **1.13.0**
- OkHttp **5.4.0** · multiplatform-markdown-renderer **0.43.0**
- 升级策略：Renovate/Dependabot 每周自动 PR；major 版本人工看 changelog + 全量回归；小库失活直接 vendoring

## 结论

- **展示层/数据层几乎全有现成**（上面 ✅ 表），版本都已核实为 2026-08 最新稳定
- **酒馆逻辑层全部没有现成**——但每项都有官方源码可翻译，且多数已翻译进 engine（上表 ✅/🟡）
- **差异化层（命中灯、角色卡主题、上下文占比）** 没有现成，由现成库拼装（UI 层自由）
- App 接线时：渲染类功能查本表选组件；逻辑类功能**只调 engine，不重写**（对应官方源码位置见 HANDOFF 4.7）
