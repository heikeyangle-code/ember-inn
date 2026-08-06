# 组件清单（2026-08 调研）

## ✅ 有现成组件（可直接依赖）

| 类别 | 组件 | 坐标 / 来源 | 说明 |
|---|---|---|---|
| UI 框架 | Jetpack Compose + Material3 | Compose BOM 2026.06.01；material3 1.4.0 稳定（Expressive 1.5.0-alpha25） | 基础 |
| 导航 | Navigation Compose 2.9.x / Navigation3 1.1.5 | androidx | 大屏自适应用 adaptive |
| 数据库 | Room 2.8.4 | androidx | 本地存储 |
| 设置存储 | DataStore 1.2.1 | androidx | 键值/偏好 |
| 依赖注入 | Koin 4.2.2 | io.insert-koin | 或 Hilt |
| 序列化 | kotlinx.serialization 1.11.0 | JetBrains | JSON |
| 图片加载 | Coil 3.5.0 | io.coil-kt.coil3 | 头像/卡图/消息图 |
| 动画 | Lottie | com.airbnb.android:lottie-compose | 品牌/加载动效 |
| 图标 | Phosphor / Material Symbols Rounded / Lucide | Compose 移植 | 规范见 README |
| 模糊/玻璃 | Cloudy（skydoves）/ Haze 2.0.0-alpha03 | dev.skydoves.cloudy / haze | GPU + CPU 降级 |
| 网格渐变 | 官方 MeshGradient | androidx.compose.ui.graphics | 氛围背景 |
| 取色 | androidx.palette | androidx | 卡图主色 |
| 种子色→配色 | MaterialKolor 4.1.x | com.jordond.materialkolor | M3 scheme |
| Markdown | mikepenz multiplatform-markdown-renderer | com.mikepenz | 消息渲染 |
| LaTeX | huarangmeng/latex | io.github.huarangmeng:latex-renderer | 公式 |
| 代码高亮 | Highlights / KodeView | dev.snipme | 代码块 |
| 文件选择 | SAF / PhotoPicker | 系统 API | 导入卡/附件 |
| 启动页 | SplashScreen API | androidx | 首启品牌动画 |
| QR | ZXing | com.google.zxing | 连接档案扫码 |
| 拖拽排序 | Calvin-LL/Reorderable | sh.calvin.reorderable | 列表重排 |
| HTTP | OkHttp 5.4.0 / Ktor 3.5.1 | — | API/代理（OkHttp 自带代理支持） |
| OpenAI tokenizer | JTokkit 1.1.0 | com.knuddels:jtokkit | token 计数 |
| 向量检索 | SQLite-vector | sqliteai/sqlite-vector | 本地 RAG |
| STT/TTS | Android SpeechRecognizer / android.speech.tts + 各家 API | 系统 + HTTP | 底层是接口调用 |
| 后台任务 | WorkManager | androidx | 后台生成/备份 |

## ❌ 没有现成组件（必须自己写 / 翻译官方源码）

| 模块 | 说明 | 官方参照（可翻译） |
|---|---|---|
| 角色卡解析 | PNG tEXt/ccv3、JSON V2/V3、CharX | `src/character-card-parser.js` + `src/charx.js`（纯 Node 逻辑，翻译容易） |
| 世界书扫描/注入 | 关键词、深度、位置、递归、粘性、冷却、分组、向量化 | `public/scripts/world-info.js` |
| 宏引擎 | lexer/parser/evaluator、{{if}}、变量宏 | `public/scripts/macros/engine/`（隔离良好） |
| 斜杠命令 | 解析 + 执行（150+ 官方命令） | `public/scripts/slash-commands/` |
| 提示词组装 | 角色字段+示例+世界书+作者注释+历史 | `public/script.js` generate 流程（需重构） |
| 群聊调度 | 多角色、模式 | `public/scripts/group-chats.js` |
| 多模型 tokenizer | JTokkit 只覆盖 OpenAI；Claude/Gemini 需自接 | 官方 `src/tokenizers.js` / web tokenizers |
| 世界书命中指示灯 UI | 竞品均无 | 我们的增强 |
| 角色卡驱动主题引擎 | 由 Palette+MaterialKolor+MeshGradient 拼装，逻辑自己写 | 我们的增强 |
| 官方前端扩展兼容 | JS/DOM 扩展沙箱 | 远期（QuickJS+shim 或兼容模式） |

## 结论

- **展示层/数据层几乎全有现成**（上面 ✅ 表）
- **酒馆逻辑层全部没有现成**（❌ 表）——但每项都有官方源码可翻译，且多数是隔离良好的纯逻辑（卡解析、宏引擎、斜杠、世界书扫描），提示词组装和群聊需要重构后再移植
- **差异化层（命中灯、角色卡主题）** 没有现成，但由现成库拼装
