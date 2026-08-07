# 交接清单（会话上下文耗尽时使用）

> 最后更新：2026-08-07。接手的 Agent 先读第 1、2 节，再读 3–5 节，最后看第 6 节工作日志。

## 1. 项目与常用命令

- 项目：EmberInn（余烬酒馆）——原生 Android SillyTavern 兼容客户端
- 本地：`/data/data/com.termux/files/home/ember-inn`
- 远程：github.com/heikeyangle-code/ember-inn（分支 main，公开）
- 官方源码参照：`/data/data/com.termux/files/home/sillytavern-ref`（release 分支）
- 架构：`app`（Compose UI）→ `engine`（纯 Kotlin 领域引擎，不依赖 UI）；引擎与官方 1:1，UI 层自由

常用命令：

```sh
# 引擎测试（本机可跑：Java 21 + Gradle 9.7；App 编译只能靠 CI）
cd ~/ember-inn && ./gradlew :engine:test

# 重新生成差分 fixture（官方发版 / 我们改引擎后）
node scripts/diff/*.mjs
node scripts/build-presets.mjs

# 推送（网络不稳，失败就重试；push 不会自动触发 CI，见下）
git push "https://x-access-token:${GITHUB_TOKEN}@github.com/heikeyangle-code/ember-inn.git" main

# 手动触发 CI（x-access-token 推送时 GitHub 不触发 Actions，必须手动 dispatch）
curl -s -X POST -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/heikeyangle-code/ember-inn/actions/workflows/328789880/dispatches" \
  -d '{"ref":"main"}'
# 然后轮询 runs（event=workflow_dispatch&per_page=1），等 completed
```

CI：`.github/workflows/build.yml`，两个 job：`engine-test`（:engine:test）与 `build`（test + assembleDebug + assembleRelease + 出 APK）。当前状态：全绿。

## 2. 什么是差分验证（新会话必读）

**目标**：EmberInn 是酒馆兼容软件，引擎逻辑必须和官方 SillyTavern 1:1。
“差分验证” = 同一输入，官方 JS 跑一遍、我们 Kotlin 跑一遍，输出必须一致。
手写期望值的单测只是自证；差分才是“官方说对才算对”的机器验证。

**怎么用**：
1. `scripts/diff/*-official.mjs` 从 `~/sillytavern-ref` 逐字提取官方函数，桩掉 DOM/全局依赖，生成 fixture：`engine/src/test/resources/diff/*.json`
2. `engine/src/test/.../*DiffTest.kt` 读 fixture，调 Kotlin 引擎逐例对比
3. 官方发版 / 我们改代码后：`node scripts/diff/*.mjs` 重新生成 fixture → `./gradlew :engine:test`
4. fixture 只能由脚本生成，不许手改；新功能先加 case 再实现

**已覆盖（8 组，共 256 例官方基准，全部通过）**：

| 组 | 脚本 | 测试 | 例数 |
|---|---|---|---|
| instruct 提示词 | instruct-official.mjs | InstructModeDiffTest | 36 |
| 世界书纯逻辑 | worldinfo-official.mjs | WorldInfoDiffTest | 19 |
| 世界书整体扫描 | worldinfo-scan-official.mjs | WorldInfoScanDiffTest | 17 |
| 世界书文件 | worldinfo-file-official.mjs | WorldInfoFileDiffTest | 2 |
| 正则 | regex-official.mjs | RegexDiffTest | 13 |
| PNG 角色卡 | card-png-official.mjs | CardPngDiffTest | 6 |
| 宏 e2e | macros-official.mjs | MacroDiffTest | 158 |
| {{pick}} 确定性 | pick-official.mjs | PickDiffTest | 5 |

**尚未做差分的**：斜杠解析器（目前是手写单测）、JSON/CharX/YAML/BYAF 导入导出（除 PNG 外是手写单测）、PromptAssembler 各 populator（单测 + 官方源码对照，暂无 fixture）。

**预设体系**：官方 `default/content/presets` 已打包进 engine resources（context 34 / instruct 38 / openai 1 / textgen 6 / novel 24 / kobold 6 / sysprompt 13 / reasoning 5，共 127 个），PresetLibrary 可加载；quick-replies 也打包。官方发版后跑 `node scripts/build-presets.mjs`。

## 3. 引擎进度（对照官方 release）

### 3.1 角色卡 ✅
PNG V2/V3（tEXt/ccv3）、CharX、JSON、YAML、BYAF 导入；V2 归一（readFromV2）、私有字段清理（charaFormatData）、JSON 导出（CharacterCardExporter）；PNG 字节级差分 6 例。
🟡 CharX/BYAF 资源（头像/背景/语音）提取到 App 层未做；URL 导入未做（App 层）。

### 3.2 世界书 ✅
buffer/matchKeys/getScore/parseDecorators、checkWorldInfo 整体扫描（含两段扫描、sticky/cooldown/概率）、深度/递归、分组评分、角色过滤、时间效果、多世界合并、装饰器/哈希、世界书文件导入导出、世界书↔角色书互转；正则在 BUILD 阶段接入扫描器。
✅ vectorized/addMemo/displayIndex/automationId 数据全量透传（rawEntries/extensions + 转换器写回）；官方核心扫描不消费这些字段（vectorized=RAG 扩展、automationId=快捷回复自动执行、displayIndex=编辑器排序、addMemo=官方核心未读取），不属于核心 1:1 缺口，等扩展层做行为。

### 3.3 宏 ✅
核心宏 + 官方 e2e 差分 158 例；变量简写全运算符、{{if}}、{{trim}} 作用域、legacy 标记/冒号/空格参数、嵌套参数、字段宏、聊天/状态宏；{{pick}} 用 seedrandom@3.0.5 逐位一致（5 例）。
🟡 动态宏注册 API、宏 flags（{{#}}）、完整 MacroEnv（聊天/角色/系统状态）未做。

### 3.4 斜杠 🟡
SlashParser（命名/无名/引号/转义/list 值/rawQuotes）、管道/闭包/双管道、/pass /let /qr-arg、{{var}}/{{pipe}}/{{arg}} 状态宏、快捷回复执行器。
❌ parser flags（REPLACE_GETVAR 等）完整语义；150+ 官方命令多数未实现（多数依赖 App 状态）；slash 无差分 fixture。

### 3.5 提示词组装 ✅（核心）
PromptManagerCore（默认/用户顺序、enabled、injection_trigger、preparePrompt original/groupOverride、mergeSystemPrompts）、PromptCollection、ChatCompletion 嵌套集合（预算/溢出/squash）、ChatHistoryPopulator、DialogueExamplesPopulator、扩展注入（summary/AN/vectors/chromadb/persona/未知扩展）、in-chat 深度注入、continue nudge/prefill、bias、control prompts（impersonate/quiet）、nsfw/jailbreak/用户相对提示、工具调用（tool_calls）、人设 IN_CHAT 注入、作者注释组合（ANWithWI）。
🟡 工具预分配 token、媒体内联、推理签名、多模态；群聊完整调度（队列核心有，UI 无）。

### 3.6 正则 ✅
RegexEngine + substituteRegex/宏替换 + 13 例差分；聊天消息正则已在扫描器接入（messageTransformer）。
🟡 global/preset/scoped 分桶与允许列表（App 层）。

### 3.7 预设 ✅
官方 127 个预设打包 + PresetLibrary；quick-replies 打包 + 执行器。moving-ui（界面预设）未打包。

### 3.8 聊天 🟡
jsonl 基础 + BYAF 聊天导入 + continue nudge。
❌ 聊天元数据（背景/书签/快照）、chat v2 迁移。

### 3.9 提供商 / LLM 客户端 ✅
- providers.json 数据驱动 **22 家**（含智谱/通义/火山方舟），端点按官方 `src/endpoints/backends/chat-completions.js` 核对 + 2026-08 联网核实最新模型（OpenAI gpt-5.5/5.4、Claude opus-5/sonnet-5/haiku-4-5、Gemini 3.6/3.5-flash/3-pro、DeepSeek v4、Grok 4.3、Kimi k3、GLM-5.2、Qwen3.7、豆包 Seed 2.1、MiniMax M3 等）
- LlmClient 三协议路由：openai-compatible（/chat/completions）、Anthropic（/v1/messages + x-api-key + anthropic-version）、Gemini（v1beta/models/{model}:generateContent?key=）
- 响应解析按协议取纯文本；SSE 三种格式（OpenAI delta / Anthropic content_block_delta / Gemini candidates.parts）都支持，流结束兜底 onDone
- Azure（deployments + api-version 2024-12-01 + api-key 头）、Workers AI（账户 ID + /ai/v1）专用 URL
- 模型列表拉取四种格式：openai data[].id / google models[].name（过滤 generateContent）/ workers result[].name / azure value[].id；无模型端点的提供商（Perplexity/自定义）用最小对话探测
- ProviderStore 多连接档案（profiles.json + activeId，旧 connection.json 自动迁移）
🟡 Vertex AI 服务账号认证未做（UI 上明确标注）；Claude/Gemini tokenizer 仍是回退 cl100k（官方 web tokenizer 未实现）。

### 3.10 其它
- ✅ 群聊调度核心（SWAP/APPEND/队列）、人设模型 + 注入、作者注释、聊天元数据模型、TokenCounterFactory（OpenAI 精确 JTokkit）
- ❌ 服务层：TTS / STT / 图像 / 翻译 / 向量（路线图 P3/P4）

## 4. App / UI 进度

### 4.1 导航与返回手势 ✅
底部三 Tab（角色/聊天/设置）；聊天页、设置子页都接 BackHandler，系统返回键/侧滑返回逐级回退（聊天→列表、提供商详情→列表→设置主页）；Manifest 已开 enableOnBackInvokedCallback（Android 13+ 预测性返回动画）。README 守则第 7 条已落实。

### 4.2 首页（角色 Tab）🟡
品牌顶栏 + 搜索框（目前只过滤角色名/描述，非全局）、AI 对话置顶卡、最近聊过横滑、角色双列网格、FAB 导入（PNG/JSON）、长按菜单（置顶/新会话/字段/导出/删除）、删除二次确认、字段详情弹层、空状态引导、Toast 反馈。角色卡取色 seed 已存（avatar → Palette）。
❌ 角色详情编辑页、世界书/正则/变量/快捷回复/模型覆盖 UI、角色卡驱动完整主题。

### 4.3 聊天页 🟡 v1
消息流 LazyColumn + 气泡 + 自动滚底 + 输入框 + 发送；配置提供商后走 LlmClient 真实请求（非流式）；返回手势已修。
❌ 流式渲染/停止、重新生成/继续/冒充/编辑/删除、滑动切回复、长按菜单、Markdown/代码高亮、上下文占比胶囊、世界书命中灯、快捷工具盘、PromptAssembler 拼好的提示词还没接进发送（现在直接发历史消息）。

### 4.4 设置 ✅（README 规格）
- 设置主页：大标题 + 副标题、设置搜索（真过滤）、常用快捷区（主题/模型/语音/备份）、六组卡片（外观与主题 / 提供商与模型 / 语音 / 服务 / 数据与隐私 / 关于）
- 外观与主题：主题模式（跟随系统/浅色/深色）+ 六套预设主题（墨韵/青瓷/夜航/丹砂/琉璃/简约纸感），点选立即全局生效（实时预览），SharedPreferences 持久化；字体/圆角/背景模糊标“开发中”
- 提供商与模型（参照命理2 逻辑）：搜索 + 卡片列表（品牌 SVG 头像 + 名称 + 一句话 + 已配置/未配置 pill + “我的连接”切换/删除）；详情页 = 名称 / API Key（遮罩+显示）/ 接口地址 / 区域 / 账户 ID / API 版本 / 默认模型（底部弹层搜索）/ 测试连接 / 保存 / 删除确认
- 关于页做实：版本 0.1.0 / AGPL-3.0 / 数据仅本地 / 开源仓库
- 语音 / 服务 / 数据与隐私：标“开发中”（不假装做完）

### 4.5 主题系统 ✅（全局层）
ThemePreset（seed/secondary/tertiary + 纸色/夜色）→ Theme.kt 自动生成整套 M3 ColorScheme（含 surfaceContainer 系列，浅色低饱和容器、深色提亮主色）；MainActivity 持有 themeMode/preset 状态，贯通 MainScreen → SettingsScreen → AppearanceScreen。
❌ 角色卡驱动主题（seed 已存，未生成角色配色）、MeshGradient 氛围背景、玻璃表面（Cloudy/Haze）、预设主题完整落盘（目前只有模式+六套 preset 的基础）。

### 4.6 数据存储 🟡
角色卡 characters/*.json + avatars/*.png、会话 sessions/*.json + chats/*.jsonl、提供商 profiles.json、主题 SharedPreferences（README 计划是 DataStore，未迁移）。
❌ Room 未引入。

## 5. 剩余工作（按优先级）

**P0（“打开即聊”体验短板）**
1. 聊天 Tab：会话列表 / 新建对话 / 群聊入口（现在是占位页）
2. 聊天页：流式渲染 + 停止按钮；消息操作（重新生成/继续/复制/删除/编辑）；PromptAssembler 提示词真正接进发送
3. 全局搜索：首页搜索框接会话/世界书/设置

**P1（功能完整）**
4. 角色详情编辑页：卡字段编辑、世界书管理 UI、正则/变量/快捷回复、模型覆盖、主题配方
5. 聊天页 Markdown/LaTeX/代码高亮渲染；滑动切回复；上下文占比胶囊 + 世界书命中灯
6. 设置剩余组：语音（TTS/STT）、服务（翻译/图像/向量）、数据与隐私（备份/导出）、首启引导

**P2（引擎边界）**
7. SlashParser flags 完整语义 + 常用斜杠命令（需 App 状态）+ slash 差分 fixture
8. Claude/Gemini 官方 web tokenizer（当前回退 cl100k）
9. 群聊完整调度 + 人设管理 UI；chat v2 迁移、聊天元数据
10. Vertex AI 服务账号认证；工具预分配 token / 媒体内联 / 推理签名

**P3/P4（服务与扩展）**
11. TTS/STT/图像生成/翻译/向量库（services 接口已规划）
12. 自有插件 API、无障碍贯穿、平板双栏

**差分跟进**
- 官方发版：重跑 `node scripts/diff/*.mjs` + `node scripts/build-presets.mjs`，再全量 `:engine:test`
- 补 slash / JSON / CharX 导入导出的差分 fixture

## 6. 最近工作日志

### 轮 5（11e33e4，2026-08-07）：返回手势按 README 守则修复
- 聊天页 BackHandler：系统返回键/侧滑返回 = 回角色列表（此前直接退出 App）
- 设置子页逐级返回（提供商详情 → 列表 → 设置主页）
- Manifest 开 enableOnBackInvokedCallback（预测性返回动画）

### 轮 4（6a4e2a8 + b70c0a3，2026-08-07）：设置视觉按 README 升级 + 全局预设主题
- 设置主页：大标题/搜索过滤/常用快捷区/六组大圆角卡片
- 外观与主题子页：主题模式 + 六套预设主题（点选全局实时生效 + 持久化）
- 主题引擎：seed 自动生成整套 M3 ColorScheme（含 surfaceContainer）
- 关于页做实；提供商卡片状态 pill + 圆角
- 修：byId 扩展接收者（列表 vs 单元素）

### 轮 3（9c76bd9 + aaa4986，2026-08-07）：设置页按 README 分组 + 提供商按命理2重构
- 设置主页分组、设置搜索；提供商列表+详情（品牌 SVG 头像 / Key 遮罩 / 模型弹层 / 测试 / 保存 / 删除）
- 25 个 lobehub 品牌 SVG 入 app/assets/icons + coil-svg；providers.json icon 改文件名
- 删除旧三步向导；文案精简

### 轮 2（ed3b1e8 等，2026-08-07）：模型/接口联网核实 + 三协议路由
- providers.json 22 家（修正 Moonshot .ai / MiniMax minimax.io+minimaxi.com / DeepSeek /beta / Azure api-version 2024-12-01）
- LlmClient 三协议路由 + 按协议响应解析 + SSE 三格式 + 模型列表四格式 + 多档案存储
- 最新模型：GPT-5.5、Claude opus-5、Gemini 3.6、DeepSeek V4、Grok 4.3、Kimi K3、GLM-5.2、Qwen3.7、豆包 Seed 2.1、MiniMax M3

### 轮 1（更早，已合入 main）
- 引擎：PNG/JSON/CharX/YAML/BYAF 导入、世界书全套、宏 e2e 差分 158、正则 13 差分、提示词组装（ChatCompletion 嵌套集合 + populators + 扩展注入）、instruct 36 差分、预设 127 打包、CI 修复（keystore 目录、KDoc 未闭合注释、ChatScreen 导入等）
- 差分工具 8 个脚本 + 256 例 fixture

## 7. 注意事项

- **兼容层 1:1，UI 层自由**：数据格式、注入算法、宏展开、斜杠行为、导入导出必须与官方互读互通；界面/交互/主题自主（设置与提供商参照命理2 + README）
- 改动先对照官方源码，能 1:1 就 1:1，近似项必须标注
- App 无法本地编译（无 Android SDK），全靠 CI 验证；引擎测试本机可跑
- 推送用 x-access-token；GitHub 网络不稳定，失败就重试；push 不触发 CI，必须手动 dispatch
- apply_patch 在本沙箱被审批策略禁用，文件编辑用 python3 精确改写（注意路径相对 `~/`）
- 删除类操作先确认；大改动保持小步提交
