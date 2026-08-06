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

- **设计规范**：Material 3 Expressive + 动态取色 + 移动优先分层导航
- **信息架构**：底部导航（角色 / 聊天 / 设置）+ 独立二级设置页；不照搬 ST 桌面多面板
- **角色卡驱动主题（核心卖点）**：卡图 → Palette 取色 → MaterialKolor 生成整套 M3 配色 → 每张卡拥有独立配色、背景、气泡色、名字色；切换卡时背景 crossfade + 颜色过渡动画
- **视觉语言**：深色沉浸优先、毛玻璃（顶部栏/输入栏）、模糊背景 + 暗色 scrim、克制的排版与动效、多套有格调的全局预设主题
- **大屏自适应**：手机单栏底部导航；平板/折叠屏双栏（列表 + 聊天）
- **渲染**：Markdown（mikepenz renderer）、LaTeX（huarangmeng/latex）、代码高亮（Highlights）、Mermaid/复杂 HTML 用局部 WebView 兜底、流式消息增量渲染

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
