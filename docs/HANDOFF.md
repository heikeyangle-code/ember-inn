# 交接清单（会话上下文耗尽时使用）

> 接手顺序：0 一眼看懂/准则 → 1 命令 → 2 差分 → 3 引擎现状 → 4 App/UI 现状 → 5 完成度 → 6 不一致/边界登记 → 7 渲染/HTML 卡片 → 8 维护速记。
> 本文件只写现状与结论；过程日志、时间戳、“本轮修复/此前漏传”一律不写。官方基线见 0.2。

## 0. 一眼看懂：架构与 1:1 保证

```mermaid
flowchart LR
 A[app<br/>Android Compose UI<br/>聊天/首页/设置/媒体渲染] -->|ChatRepository<br/>ChatPromptFactory| B[engine<br/>纯 Kotlin 领域引擎<br/>不依赖 UI/Android]
 B -->|PromptPipeline 总装<br/>世界书/宏/正则/人设/示例/历史| C[LlmClient<br/>OpenAI/Claude/Gemini/Mistral/xAI/Cohere/AI21…]
 C -->|OkHttp SSE| D[厂商 API]
 E[官方 SillyTavern 1.18.0<br/>~/sillytavern-ref] -->|scripts/diff/*.mjs<br/>逐字提取纯函数| F[差分 fixture<br/>engine/src/test/resources/diff]
 B -->|引擎 Kotlin 同输入跑一遍| F
 F -->|DiffTest 断言一致| G[引擎 378 测全绿]
```

- 一句话：**引擎与官方 SillyTavern 1:1（必须差分）；App/UI 对照官方功能与设置实现官方语义，样式用 Ember 风格。**
- “差分”= 同一输入，官方 JS 与引擎 Kotlin 各跑一遍，输出逐字一致；fixture 由脚本生成、禁止手改。
- 官方基线：release `8172dcd`（SillyTavern **1.18.0**）；酒馆更新后重跑 `node scripts/diff/*.mjs`，红的就是要移植的差异。

### 0.1 工作准则

1. **引擎层改动 = 官方 1:1 + 差分，缺一不可**：精读官方源码 → 写 `scripts/diff/*-official.mjs`（函数体逐字摘自官方；打桩/未覆盖分支登记脚本头部）→ `node scripts/diff/*.mjs` 生成 fixture（禁止手改）→ Kotlin 移植 → `*DiffTest` 同输入对拍 → `./gradlew :engine:test` 全绿。穷举分支（空/单/多/极值/非法/布尔组合/厂商分支/嵌套/边界/Unicode）是硬性要求；**无差分不声称 1:1**；任何引擎改动（含改一行）必须重生成受影响 fixture + 全量测试。
2. **App/UI 层 = 官方字段/默认值/行为一致，样式 Ember**：先看官方对应位点（settings.html/index.js/power-user.js/script.js），数据模型与官方存储格式互导；**能引擎干的尽量引擎干，App 只做接线与渲染**，不得重复实现官方逻辑。
3. **交接文档只写现状**：完成即更新对应章节状态（1:1/部分/未做）与差分组/例数；不新增“补充/追加/更新记录”式日志。
4. **用户豁免项（仅两项）**：Claude/Gemini 官方 web tokenizer（cl100k 回退，只影响估算精度）；Custom CSS + Moving UI（延期，见 6.4）。
5. **自主工作不停止**：对照官方逐项审计“没做/写了没接/接了不对”，能做就做；引擎按 1、App 按 2。

### 0.2 版本基线

- 基线 = SillyTavern release `8172dcd`（1.18.0），源码 `/data/data/com.termux/files/home/sillytavern-ref`；全部“已对齐/已差分/已实现”结论与 fixture 均以此为准。
- 官方更新流程（不可跳过）：①更新 ref 到新 release 并记录 commit；②重跑全部 `scripts/diff/*.mjs` 重新生成 fixture，红/变的就是差异，逐个对照移植；③新功能按“先穷举 case → 差分 → 实现”流程；④`./gradlew :engine:test` 全量 + App 等 CI；⑤更新 0.2 基线、第 2 节组/例数、第 5 节完成度与相关模块状态。

## 1. 项目与常用命令

- 项目：EmberInn（余烬酒馆）——原生 Android SillyTavern 兼容客户端；本地 `~/ember-inn`，远程 github.com/heikeyangle-code/ember-inn（main，公开）；官方参照 `~/sillytavern-ref`（release 8172dcd / 1.18.0）。
- 引擎测试与 App Kotlin 编译本机均可跑（Java 21 + Gradle 9.7 + Android SDK，当前 **378 测全绿**）；完整 APK 组装/签名走 CI。

```sh
cd ~/ember-inn && ./gradlew :engine:test
node scripts/diff/*.mjs          # 改引擎/官方发版后重新生成 fixture
node scripts/build-presets.mjs   # 打包官方预设
git push origin main             # 需 GitHub 凭证；沙箱会话重置后 gh/token 可能丢失，届时 gh auth login 或临时 PAT
gh run list --limit 3            # 看 CI（改 app/engine/gradle/工作流才自动触发；纯文档不触发）
gh workflow run 328789880 --ref main   # 需要手工跑一次
```

- CI：`.github/workflows/build.yml`，job `engine-test`（:engine:test）+ `build`（单测 + assembleDebug/Release + 出 APK）；push 自动触发条件见 `on.push.paths`。

## 2. 差分验证（新会话必读）

**目标/机制**：EmberInn 引擎逻辑必须与官方 1:1；“差分”= 同输入官方 JS 与 Kotlin 各跑一遍、输出逐字一致。手写期望值的单测只是自证，差分才是机器验证。

**用法**：①`scripts/diff/*-official.mjs` 从 `~/sillytavern-ref` 逐字提取官方函数、桩掉 DOM/全局依赖 → 生成 `engine/src/test/resources/diff/*.json`；②`*DiffTest.kt` 读 fixture 调 Kotlin 引擎逐例对比；③官方发版/改代码后重生成 fixture → `:engine:test`；④fixture 只能脚本生成，不许手改，新功能先加 case 再实现。

**差分分组清单（96 行，表内合计 2984 例；历史 85/1969 为旧口径）见 [docs/DIFF_MATRIX.md](DIFF_MATRIX.md)，含打桩/未差分登记；新增 message-formatting-official.mjs → MessageFormattingDiffTest 805 例、cfg-prompt 25 例、logprobs 20 例、imagegen 10 例、chat-template-official.mjs → ChatTemplateDiffTest 25 例、model-sort-official.mjs → ModelSortDiffTest 48 例。**

**打桩/未差分登记与“官方有而引擎/App 还没有”清单见 [docs/DIFF_MATRIX.md](DIFF_MATRIX.md)。**

**官方有而引擎/App 还没有**：
- textgen/Novel/Kobold：✅ 全通（TextgenRequestBodyEngine 27 例 / NovelRequestBodyEngine 12 例 / KoboldRequestBodyEngine 12 例差分；LlmClient 三条路由 + 流式；providers.json 条目 + mancer/featherless/infermaticai 请求头 6 例差分；Kobold 非流式 MockWebServer）。
- ChromaDB 远程向量后端（官方 vectors 默认）：未做，用 FileVectorStore/InMemory + OpenAI 兼容嵌入替代。
- summarize 聊天摘要（vectors 扩展）：未做。
- 第三方扩展市场/插件体系：未做。
- Prompt Manager：引擎 1:1（29 例差分）+ App 完整（设置→提示词管理器 + dryRun 预览，见 5）。
- 官方部分 slash 命令（命令数少于官方=用户决策裁剪：仅移植高价值命令，UI 点击可完成的操作命令不移植，见 3.4）。
- connection-manager 扩展：App 用 ProviderScreen 多档案等价替代。
- Claude/Gemini 官方 web tokenizer：用户豁免（cl100k 回退）。
- 预设打包：官方 default/content/presets 已进 engine resources，并按用户确认裁剪表过滤（context 7 / instruct 6 / openai 1 / textgen 6 / novel 13 / kobold 6 / sysprompt 13 / reasoning 2，共 54；裁剪表在 scripts/build-presets.mjs trimPresets，官方发版重跑仍保持裁剪）；quick-replies 已打包；官方发版后跑 `node scripts/build-presets.mjs`。

## 3. 引擎进度（对照官方 release）

### 3.1 角色卡 ✅
PNG V2/V3（tEXt/ccv3）与 JSON 导入导出（官方也只导 PNG/JSON）、CharX/YAML/BYAF 导入；V2 归一（readFromV2 差分 5 例）、私有字段清理、PNG 字节级差分 6 例、JSON 导入 10 例/导出 6 例、YAML 5 例、CharX 9 例、BYAF 14+5+4 例。导入保留世界书回归锁（WorldBookImportTest）；CharX 资源提取（icon→头像+seed，background/voice 落盘 assets/）；URL 导入角色卡（PNG/CharX/JSON 按后缀/魔数探测，对齐 content-manager importURL）。

### 3.2 世界书 ✅（含 RAG 向量扩展）
buffer/matchKeys/getScore/parseDecorators、checkWorldInfo 整体扫描（两段扫描/sticky/cooldown/概率）、深度/递归、分组评分、角色过滤、时间效果、多世界合并、世界书文件导入导出、世界书↔角色书互转；正则在 BUILD 阶段接入扫描器；regexDepthOf 差分 40 例。
- vectorized → RAG（WorldInfoVectorActivation 同步/检索/强制激活，对齐 vectors activateWorldInfo）；FileVectorStore 磁盘持久化对齐 vectra 目录（root/source/collection/model + items.json，重启不丢）；Scanner 经 externalActivations 强制激活（跳过关键词/概率）。
- VectorChatRearranger（rearrangeChat：protect 最近 N、insert 条数、Past events:{{text}} 模板、BEFORE_PROMPT→start/IN_PROMPT→end）+ 文件/Data Bank 向量化（processFiles/ingestDataBankAttachments/injectDataBankChunks/retrieveFileChunks/vectorizeFile；splitRecursive/overlap）+ VectorTextUtils 官方 1:1。
- automationId → 快捷回复自动执行（WorldInfoAutoExecute + AutoExecuteHandler，4 例差分）；displayIndex → 编辑器排序（WorldInfoEditorSort 6 例差分）；addMemo 官方核心从不读取，仅透传。
- 2026-08-14 审查修复（现状）：世界书条目内容激活时先宏替换（官方 checkWorldInfo substituteParams），替换后文本进递归缓冲/预算/输出，总装再替换一次（官方两次替换语义）；世界书关键词也过宏替换；delayUntilRecursion 首级按官方 shift() 扫描前移出；角色卡 tags 接进 characterFilter 标签过滤；WorldInfoScannerMacroTest 锁宏替换。

### 3.3 宏 ✅（含作用域宏）
通用作用域宏（{{setvar}}/{{#}} 保留空白/嵌套/trim+dedent，对齐 MacroCstWalker.processScopedMacros）；trimScopedContent 差分 7 例；!?~> flags 官方标 TBD（无需补）；配对逻辑依赖 chevrotain CST 无法逐字差分（源码对照+单测）。核心宏 + 官方 e2e 158 例；变量简写全运算符、{{if}}、{{trim}} 作用域、legacy 标记、嵌套参数、字段宏、聊天/状态宏；{{pick}} 用 seedrandom@3.0.5 逐位一致（5 例）。{{outlet::key}} 差分 5 例（官方 core-macros.js 逐字提取；空 key 未判空已修）；MacroRegistry 动态注册/注销/解析；角色字段已接线（{{description}}/{{chardepthprompt}} 等可用）；🟡 聊天/系统状态边界仍缺。

### 3.4 斜杠 🟡
SlashParser（命名/无名/引号/转义/list/rawQuotes）+ SlashEngine（管道/闭包/双管道）、/pass /let /qr-arg、{{var}}/{{pipe}}/{{arg}} 状态宏、快捷回复执行器；testSymbol 差分 27 例；参数解析核心 43 例差分；斜杠数学/布尔/len/sort 1:1（SlashMathEngine 差分 288 例）；输入框斜杠补全 UI（/ 前缀过滤、最多 12 条、220dp 可滚动）。
已接命令：/renamechat /getchatname /setinput /bg /impersonate /persona-set /trigger /inject /gen /genraw + 异步执行器；消息类命令（/sendas /send /impersonate return=）已对齐（sendas 缺省当前角色、SLASH_COMMAND 正则 characterOverride、{{bias}} 只偏置→is_system、avatar/compact 落 extra/force_avatar/isSmallSys、swipes 初始化；return= 官方 slashCommandReturnHelper：pipe/object/toast-html/toast-text/console/none）；按角色头像渲染已接（extra.force_avatar/original_avatar → avatars/{id}.png）。
- /inject filter 闭包 ✅：引擎 closureArgs 机制（对齐官方 ARGUMENT_TYPE.CLOSURE）——命令声明的闭包参数保留原文（trimEnd，宏不提前替换），ScriptInject.filter 持久化，生成时注入聊天变量求值（isTrueBoolean：true/1/yes/on/y 才注入，空/解析失败=始终注入）。
- /genraw instruct/as ✅：text completion 路径走 InstructMode.createRawPrompt（instruct 开关 + 协议分支 + quietToLoud），对齐官方 generateRawCallback。
- 裁剪决策（用户确认）：斜杠命令只移植高价值命令（生成链/注入/变量/消息类），UI 点击可完成的操作命令不移植；命令数少于官方属有意为之。
剩余偏差：未声明 closureArgs 的闭包（如 /if then/else）仍预解析立即求值（官方惰性）；/trigger await 已等待生成结束；官方 1.18 无 /while；/tokens 用 cl100k 近似。

### 3.5 提示词组装 ✅（核心）
PromptManagerCore、PromptCollection/ChatCompletion 嵌套集合（预算/溢出/squash）、ChatHistoryPopulator、DialogueExamplesPopulator、扩展注入（summary/AN/vectors/chromadb/persona）、in-chat 深度注入、continue nudge/prefill、bias、control prompts、工具调用、ToolLoopPlanner（RECURSE_LIMIT=5，差分 17 例）、人设 IN_CHAT 注入。
- PromptPipeline 总装器 1:1（prepareOpenAIMessages+populateChatCompletion；整链差分 29 例；populationInjectionPrompts 官方真函数；getExtensionPrompt 差分 19 例）；CharacterCardFieldsEngine 差分 6 例；PromptUtils 差分 9 例；AuthorsNoteEngine 差分 7 例（默认 position=1 修正）。
- 历史 reasoning 注入（PromptReasoningEngine.addToMessage 差分 7 例；add_to_prompts 默认关，continue 最后一条 prefix 不受开关限制）；角色 system_prompt/剧情后指令已真正进请求体（fields.system/jailbreak，chat_metadata 同名键优先）；每条历史消息过 preparePrompt 宏替换；names_behavior 修正（COMPLETION 才带 name，PromptNameSanitizer 28 例）；工具预分配/媒体内联/推理签名端到端 20 例。

### 3.6 正则 ✅
RegexEngine + substituteRegex/宏替换（27 例差分：g/首匹配/i/m/s/x/X/A/J/U 非原生 flag、u 原生、重复 flag 回退）；世界书 key 解析 parseRegexFromString（15 例，u/y 原生 flag 边界登记）；RegexPipelineEngine（getRegexedString：placement/markdownOnly/promptOnly/runOnEdit/minDepth/maxDepth/禁用扩展，9 例差分）；聊天消息正则已接入扫描器。
- 该卡正则接线：CharacterCardEdit 读写 data.extensions.regex_scripts（RegexScriptData）；存前（sendMessageAsUser→USER_INPUT、saveReply→AI_OUTPUT（冒充→USER_INPUT 不落盘）、getFirstMessage→开场白 AI_OUTPUT）；总装 isPrompt=true + 官方 depth（只跑 promptOnly）；允许列表 character_allowed_regex（角色详情开关 + allowedOnly=true，scoped 默认不生效）；全局开关 disabledExtensions.regex；preset 脚本命名预设集（结构等价官方 preset 扩展字段）。替换串宏替换（官方 runRegexScript 收尾 substituteParams）已全位点接线。
- 登记：落盘文本宏未替换（发送时应用，请求等价）。

### 3.7 预设 ✅（应用引擎差分 99 例 / App 全接）
官方定位（index.html 核实）：预设全链在 Power User 的 Advanced Formatting 抽屉 + 各 API 连接面板采样预设管理器；与 Prompt Manager 无关。
- 引擎 PresetApplyEngine：类型识别（isPossibly*Data + performMasterImport legacy 顺序）、五类应用（context/instruct/sysprompt/reasoning/chat-completion + migrate）、保存过滤（getChatCompletionPresetBody/getContextSettingsCompiled/filterPresetSettings）、名字匹配（matchPresetNameExact/findMatchingTemplateName）、采样器应用（textgen setting_names 全量/novel loadNovelPreset/kobold loadKoboldSettingsFromPreset/applyGenerationParamsFromPreset：MAX_CONTEXT_DEFAULT=8192、MAX_RESPONSE_DEFAULT=2048）、autoSelectPresetDecision、detectSensitivePresetFields（openai 11 字段）。打桩登记：textgen/novel/kobold DOM 归约剥除、order 默认数组由参数注入。
- App：预设页五类选择即应用 + 保存当前为预设 + 删除 + 单文件导入（legacy 顺序）+ 多区段 master 导入/导出；/preset exact + Fuse.js 7.1 模糊（27 例差分）；bind_preset_to_connection（默认 true）；autoSelectPreset（进聊天角色名=采样预设名自动应用）；用户预设同名覆盖官方打包预设；存储 = 官方打包 engine resources + 用户 filesDir/presets/{type}/{name}.json。
- OpenAI 采样预设全字段：settingsToUpdate 102 键已按官方默认全量入档案/采样器；prompts/prompt_order 写 PromptManagerPrefs；其余生成字段（names_behavior/send_if_empty/new_chat/.../continue_postfix/function_calling/show_thoughts/media_inlining/.../max_context_unlocked）已接总装/请求体/UI；reverse_proxy/proxy_password 按 proxySupportedSources 替换；custom_include_body/exclude_body/include_headers 用 YamlMerge 标量子集（嵌套 YAML 边界登记）；custom_url 已接。
- 分词器：getTokenizerModel 映射 1:1 差分 37 例；web 族 Claude（HfBpeTokenizer，官方 claude.json 打包，HF BPE：ByteLevel/merges/added tokens/NFKC/字节编解码）；sentencepiece 族（SentencePieceTokenizer：proto + BPE 按 score 合并 + <0xXX> fallback + dummy prefix），打包保留 Google Gemini（gemma.model）；按用户要求仅保留 OpenAI(JTokkit)/Google/Claude 三族，llama3/mistral/llama1/yi/jamba/nerdstash/command-r/qwen2/nemo/deepseek 未打包（回退 cl100k，bias 按官方返回 {}，登记）；tiktoken JTokkit（O200K/CL100K）按官方 /bias 真算；原始 id 数组透传、后写覆盖一致。登记：无官方库无法差分（sanity 值锁定）；command-r/command-a/qwen2/nemo/deepseek 官方无模型文件不可实现；precompiled_charsmap 非空模型不支持（现 9 个模型全空）。
- bias_presets 官方弹窗已做；YamlMerge 用 SnakeYAML 对齐 js-yaml（锚点/别名/<< 原生解析/多文档静默/时间戳 ISO）；vertexai 服务账号认证已做（VertexAuth.kt + LlmClient vertexai express/full/proxy + UI）；bypass_status_check 已接；show_external_models 已接（extensions 键已持久化，官方 core 也只存不消费）。
- 登记：context/instruct/sysprompt 官方消费点是非 OpenAI 路径（script.js:4663 renderStoryString/formatInstructModeStoryString/applyStoryStringInject=main_api!=='openai'），OpenAI 主提示不走 story string——App 对 OpenAI/Anthropic/Google 不消费与官方一致；textgen/novel/kobold 路径已把 context/instruct/sysprompt 传进引擎（InstructMode.createRawPrompt 消费；sysprompt 作 systemPrompt；post_history 按官方作为 user 消息注入）；master 导入的 textgen preset 暂存 sampler 用户预设（不应用）；moving-ui 延期见 6.4。
- 模型模板派生/绑定：ChatTemplateEngine（chat-template-official.mjs 25 例差分）——deriveTemplatesFromChatTemplate（哈希表+子串启发式派生 context/instruct 模板名）+ bindModelTemplates（模型 id / chat template hash 映射）；App Advanced Formatting 已补 context_derived / instruct_derived / bind_model_templates 开关，模型切换时按官方绑定/激活。kobold 连接测试时按官方 textgen-settings.js 流程 GET {base}/props 读 chat_template/chat_template_hash（sha256）与 default_generation_settings.n_ctx：context_size_derived 自动改上下文、context/instruct 派生自动选中。登记：llamacpp 为官方独立类型（App 无 llamacpp 提供商条目）。
- 模型排序/分组：ModelSortEngine（model-sort-official.mjs 48 例差分）——sortModelsBy 五源（openrouter/chutes/electronhub/nanogpt/aimlapi 各按 context_length/pricing.prompt|input/pricing.completion|output/name|id 排序，default 原序）+ groupModelsByVendor（vendor 分组）+ filterModelsBySource（electronhub endpoints/chutes affine/aimlapi type）；LlmClient.models 返回带元数据（id/name/context_length/pricing/tokens/info）的模型对象，App 模型选择面板按官方各源 option text（name ?? info.name ?? id）显示。
- 预设缺口清单（用户确认）：①采样预设逐字段勾选 settings_checked——官方 1.18 源码无此字段，不实现；②textgen/Novel/Kobold 全通（见 2）；③/preset fuzzy 已完成。

### 3.8 聊天/消息 ✅（核心）
jsonl 基础 + BYAF 聊天导入 + continue nudge；swipes 数据模型（App 层，对齐 swipe_id/swipes[]/swipe_info[]：ensureSwipes/syncSwipeToMes/Generate('swipe')/deleteSwipe/editMessage）；聊天元数据 ChatHeader（chats/{id}.json chat_metadata：system_prompt/scenario/mes_example/custom_background）；书签（bookmarkNames/createBookmark/openBookmark，存档 chats/{id}-Checkpoint-*.jsonl + 最后 AI extra.bookmark_link）；设置快照（SettingsSnapshotStore 命名 zip 保存/恢复 SharedPreferences+提供商档案，恢复需重启，登记）。

| 子模块 | 引擎/差分 | App 接线 |
|---|---|---|
| 消息清理 | CleanUpMessage.kt（cleanUpMessage/cleanGroupMessage/fixMarkdown；CleanUpDiffTest 49 例） | CleanUpConfig 注入 promptBias/regexTransform/stoppingStrings；finalizeStream 保存前走全链 |
| 响应数据提取 | ResponseDataExtractor.kt（extractMessageFromData/extractJsonFromData；31 例；textgen content 数组/openai \n\n 拼接/tool_plan/非 openai {}） | LlmClient 非流式最终响应 |
| 自动续写 | AutoContinue.kt（shouldAutoContinue 11 例；tokenCount 注入） | 单聊/群聊最大 5 轮 |
| 停用词 | StoppingStrings.kt（14 例；openai 仅自定义；非 openai 名字/群成员/Instruct/自定义/单行 \n） | ChatViewModel + 协议分支 |
| 偏置 | BiasEngine.kt（getBiasStrings/extractMessageBias/removeMacros；Handlebars ^4.7.9 vendor；17 例） | {{bias}} 提取/剥离/编辑回溯/impersonate-continue 不注入 |
| 流式响应/错误 | StreamingResponse.kt（getStreamingReply/tryParseStreamingError；20 例；全厂商 delta 分支/reasoning/images/signature/错误分类） | SseChunkParser 运行时唯一路径 |
| Reasoning 解析 | ReasoningEngine.kt（parse/remove/formatReasoning；13 例） | removeReasoning/token 预算未接（发送链路未接项见 6.2） |
| Token 预算 | TokenBudgetEngine.kt（17 例；kobold/textgen/novel/未知默认 1487；override 校验） | ChatPromptFactory |
| 滑动/自动过滤 | SwipeEngine.kt（29 例；isSwipingAllowed/isMessageSwipeable/getOverswipeBehavior/ensureSwipes/generatedTextFiltered/extractMultiSwipes） | 滑动/变体/auto_swipe |
| 工具调用增量解析 | ToolCallParser.kt（ToolManager.parseToolCalls/#applyToolCallDelta；8 例；OpenAI/Cohere/Anthropic/Gemini） | 流式 tool_calls 回调 |
| 记忆扩展纯逻辑 | MemoryEngine.kt（14 例；最新摘要/间隔/force/raw 提示词构建） | MemoryPrefs/MemoryService/{{summary}}/1_memory 注入/自动触发 |
| append_title | PromptAssembler.appendMessageTitles（5 例） | App 从 JSONL extra 提取 titles |
| 作者注释注入判定 | AuthorsNoteEngine.shouldInjectNote（8 例；按用户消息数，interval=1 恒注入） | AN 三层（全局/角色/聊天） |
| 扩展提示/EM/深度/工具循环 | ExtensionPromptEngine（19 例）+ ExampleAssembler（9 例）+ DepthPromptEngine（6 例）+ ToolLoopPlanner（17 例） | /inject 数字枚举持久化/scan；EM before unshift/after push；深度提示 IN_CHAT 注入；ToolRegistry 执行+历史重构+递归重装 |
| setOpenAIMessages | PromptAssembler.toOpenAiMessages（16 例；narrator→system/names 各模式/isSameModel 过滤/输出新的在前） | ChatPromptFactory 从 JSONL extra 解析 |
| 边界补齐 | Captions refine/prompt_ask 弹层；extra.sprite 落盘；/genraw stop/trim；power-user 行为设置全接；auto_swipe；世界书编辑器全字段；strip_examples/message_token_count；外置世界书（WorldStore/WorldLoreMerger/globalSelect/插入策略） | 见 4.x/5 剩余清单 |

### 3.9 提供商 / LLM 客户端 ✅
OpenAI 兼容全家、Anthropic、Gemini（含预算自动推导）、Mistral、xAI、Cohere、AI21 路由全部接完（转换器均已差分移植，网络层 MockWebServer 锁行为）；OpenRouter 已接媒体嵌入/推理签名/reasoning exclude；Vertex 服务账号认证已做（VertexAuth.kt：官方 google.js getVertexAIAuth/generateJWTToken/getAccessToken/getProjectIdFromServiceAccount 移植 + LlmClient vertexai express/full/proxy + ProviderScreen 服务账号 JSON 校验/保存）。

| 提供商 | 路由 | 请求体 | 转换/媒体 | 预算/缓存/签名 | 模型列表 | 状态 |
|---|---|---|---|---|---|---|
| OpenAI/Azure/DeepSeek/Groq/Moonshot/MiniMax/智谱/通义/硅基流动/Z.AI/Fireworks/Perplexity/Custom/NanoGPT/Chutes/ElectronHub/Ollama | openai-compatible（Azure deployments+api-version 2024-12-01；DeepSeek 默认 /v1） | 全厂商参数 27 例 + 实际请求体 28 例差分（o1/gpt-5/空 stop/温度 clamp/seed 边界） | MediaInliner 7 例 | — | data[].id / value[].id / 最小对话探测 | ✅ |
| Workers AI | {account}/ai/v1/chat/completions | ✅ | ✅ | — | result[].name | ✅ |
| Anthropic | /v1/messages + x-api-key + anthropic-version | 17 例差分（thinking/tools/web_search/json_schema/beta/采样/verbosity/no-prefill） | convertClaudeMessages 整链 41 例 + convertClaudePart 25 例 | calculateClaudeBudgetTokens 已接（adaptive→effort 字符串/auto→不加 thinking） | 官方不发模型列表，用默认 | ✅ 差 tokenizer |
| Gemini AI Studio | v1beta/models/{model}:generateContent?key= | 16 例差分（generationConfig/thinkingConfig/tools/toolConfig/google_search/图像模态） | convertGooglePrompt 41 例 + convertGooglePart 25 例 | calculateGoogleBudgetTokens 已接（gemini-3 flash/pro→thinkingLevel，2.5→数字预算） | models[].name（过滤 generateContent） | ✅ 差 tokenizer |
| OpenRouter | openai-compatible | openrouter 分支 + transforms/plugins/reasoning.exclude/effort | embedOpenRouterMedia（audio+video） | addOpenRouterSignatures + cachingAtDepthForOpenRouterClaude + cachingSystemPromptForOpenRouter（SamplerParams 缓存开关/深度/TTL）+ DeepSeek addReasoningContentToToolCalls | ✅ | ✅ |
| Mistral / xAI / AI21 | 专用路由 /chat/completions | body 差分 23 例含内（sendMistralAIRequest/sendXAIRequest/sendAI21Request） | convertMistral/convertXAI/convertAI21 | — | Mistral/xAI ✅；AI21 无端点用默认 jamba-large | ✅ |
| Cohere | /v2/chat | sendCohereRequest（documents/tools/p/frequency/presence） | convertCohere | — | 无端点默认 command-r-plus | ✅ |
| Vertex AI | LlmClient express/full/proxy | vertex 参数分支已差分 | 复用 Gemini 转换 | — | — | ✅ 认证已做 |

其余：providers.json 数据驱动 **36 家**（含 Together/Cerebras/SambaNova/NVIDIA NIM/GitHub Models/Hugging Face/腾讯混元/阶跃星辰/零一万物/百度千帆/讯飞星火/LM Studio；Cohere 官方地址 api.cohere.com/v2；DeepSeek 默认 /v1），端点按官方 chat-completions.js 核对 + 联网核实最新模型；LlmClient 七协议路由（openai-compatible/Anthropic/Gemini/Mistral/xAI/AI21/Cohere）+ SSE 四格式（OpenAI delta 覆盖 Mistral/xAI/AI21、Anthropic content_block_delta、Gemini candidates.parts、Cohere content-delta），流结束兜底 onDone；能力管道全通（tools/tool_choice/json_schema/Anthropic·Gemini web_search/Gemini requestImages+safety）；响应解析按协议取纯文本；ProviderStore 多档案（profiles.json + activeId，旧 connection.json 自动迁移）。边界：GEMINI_SAFETY/VERTEX_SAFETY 由调用方桩/传参；convertClaudePrompt 遗留旧函数（仅 token 计数用）未移植；Claude/Gemini tokenizer 回退 cl100k。

### 3.10 CFG Scale ✅
官方 scripts/cfg-scale.js 纯逻辑 1:1 差分移植（getGuidanceScale chat>chara>global 优先级、getCfgPrompt prompt_combine unshift 合并、getCustomSeparator JSON.parse 回退、插入深度；25 例差分）。App 三层接线：全局（CfgPrefs）/角色（按角色 id）/会话（chat_metadata.cfg_*），聊天菜单 → CFG Scale 弹层编辑；发送时 maxContext 扣减 max(neg,pos) token，正向提示按深度注入（textgen/novel 非 openai 路径），textgen 请求体 cfgValues.guidanceScale+negativePrompt、novel 只 guidanceScale（官方 switch 分支）。
登记（非 1:1 边界）：depth==0 官方直接追加末条（空格规则），App 用 injectionDepth=0 的 in-chat PromptItem 近似；charaCfg 缺失+群聊覆盖官方抛 TypeError，Kotlin 空安全返回 null 档。

### 3.11 Token 概率（logprobs） ✅
官方 openai.js parseOpenAIChatLogprobs/parseOpenAITextLogprobs/parseChatCompletionLogprobs 1:1 差分移植（20 例）；流式 OpenAI chat 每块 choices[0].logprobs 经 LogprobsEngine 解析 → ChatViewModel 内存保留最近一条 → 会话菜单 “Token 概率（logprobs）” 底部面板点击 token 查看备选。
登记（非 1:1 边界）：官方 viewer 的“从备选重roll/从词前缀重roll”未移植（需 swipe 预填链路）；textgen/novel 非流式 logprobs 未解析（官方 parseAndSaveLogprobs 路径）；text 解析 top_logprobs 整体缺失官方抛 TypeError（响应契约恒带）。

### 3.12 图像生成（stable-diffusion） ✅（核心子集）
官方扩展 generateAutoImage/generateSdcppImage 请求体 1:1 差分移植（imagegen-official.mjs 10 例）：prompt/negative/sampler/scheduler/steps/cfg_scale/width/height/seed/restore_faces/clip_skip/vae/HR 全字段，JSON.stringify undefined 省略语义一致。App 设置页补齐核心参数（前缀/负向/采样器/调度器/CFG/尺寸/种子/恢复人脸/CLIP skip/VAE/HR），消息级生成挂 extra.media（官方 sd_message_gen）。角色提示词前缀按角色 id 存储并在聊天生成时合并。
登记（未移植）：ADetailer、vlad/drawthings/openai/horde/hf/comfy 等其余 23 个后端的请求体、样式库/prompt templates/refine/interactive/multimodal 等交互模式、Comfy workflow 管理。

### 3.13 群聊 / 其它 ✅
群聊成员激活策略（15 例）、APPEND 角色卡合并（8 例）、深度提示（7 例）、完整循环纯逻辑 GroupLoopEngine（11 例）；App 调度层（GroupStore/新建群聊/GroupScheduler/顺序生成/续写重生成按最后成员）；natural/pooled 激活+队列提示；自动续写（shouldAutoContinue + /continue 链，默认关）；narrator 按官方 1.18 无独立模式关闭（/sys 旁白群聊可用）；TokenCounterFactory（OpenAI 精确 JTokkit）。

### 3.14 向量扩展（RAG 全量）✅（引擎层）
世界书 RAG（vectorized 同步/检索/强制激活）；聊天历史向量重排（enabled_chats/rearrangeChat）；文件/Data Bank 向量化（enabled_files：分块/overlap/检索注入）；FileVectorStore（磁盘持久化对齐 vectra 目录）+ InMemoryVectorStore；EmbeddingProvider（OpenAI 兼容 + BagOfGramsEmbedding）；查询语义对齐官方（multiQueryCollection 全局 topK/queryCollection 单集合，hashes 不过滤阈值）；扩展提示经 ExtensionPrompt（3_vectors/4_vectors_data_bank）注入组装管线（ChatCompletionPipeline KNOWN_RELATIVE）。未做：summarize（P3，官方默认关）、本地 transformers 嵌入（Android 用 Ollama 替代，接口已留）、translate_files（P3）。

### 3.15 表情精灵 ✅（引擎层纯逻辑）
ExpressionEngine（文件名→标签、图片元数据、分组排序、chooseSpriteForExpression fallback/多立绘随机/rerollIfSame/overrideSpriteFile）；sampleClassifyText（去宏/引号/星号、短文本裁句尾、长文本首尾各 250 拼接、LLM 模式仅 trim；8 例差分）；官方差分 14+8+7 例（expressions/index.js + endpoints/sprites.js + utils.js 逐字对拍）；SpriteStorage（spritesPath 子目录/sanitize + importRisuSprites）；LLM 分类 ✅（llmPrompt=官方 getLlmPrompt {{labels}} 模板 + parseLlmResponse=JSON {emotion} → removeReasoning 清理后模糊匹配 → null 走 fallback；App ExpressionScreen 开关/自定义提示词，ChatViewModel 生成后异步分类切换表情）；App 层 ExpressionStore 精灵目录 LRU 缓存（24 角色，save/delete/import 即时失效——对齐官方 spriteCache 语义，聊天列表滚动每条 AI 消息组合不再列目录）；DOM 显示/动画属 App 层；差分顺带修 VectorTextUtils.trimToStartSentence（Kotlin 需 coerceAtMost）。

## 4. App / UI 进度

### 4.1 导航与返回手势 ✅
底部三 Tab（角色/聊天/设置）；聊天页、设置子页 BackHandler 逐级回退；设置页内导航栈（页面切换压栈，子页返回回真实上一级而非全跳 HOME；主页列表位置与搜索词常驻层保留，进出子页不重置）；Manifest enableOnBackInvokedCallback（Android 13+ 预测性返回）。

### 4.2 首页（角色 Tab）与角色详情 🟡
- 首页：品牌顶栏 + 全局搜索（角色名/描述、会话名/最后消息、世界书条目 key/content/comment、设置项；条目详情弹层；设置项跳转）、AI 对话置顶、最近聊过横滑、角色双列网格、FAB 导入（PNG/JSON/CharX）、长按菜单（置顶/新会话/字段/导出/删除）、删除二次确认、角色卡取色 seed 已存。
- 角色详情编辑页：官方 v2 卡字段全集（name/description/personality/scenario/first_mes/mes_example/system_prompt/post_history_instructions/creator_notes/creator/character_version/tags/alternate_greetings）+ 世界书条目管理 UI（官方全字段，v1 key→v2 keys 归一，未知扩展字段原样保留）+ 删除/置顶/导出/一键开始聊天。depth_prompt/talkativeness 读写官方位置 data.extensions；字段读写抽为纯逻辑 CharacterCardEdit（App 单测 5 例）。
- 正则（该卡）UI：官方格式读写 + 编辑弹层 + USER_INPUT/AI_OUTPUT 位点接线 + “允许此角色应用该卡正则”开关（UI 默认开=用户要求；实际生效以 character_allowed_regex 列表为准，未进过详情页/未切换过开关时不写入）。
- 变量（该卡）：data.extensions.emberinn_variables（README 自定义扩展，官方无 per-character 变量，见 6.1）。
- 快捷回复（全局）：Quick Reply 官方字段（mes/label/enabled/automationId/preventAutoExecute），设置→服务→快捷回复管理；per-character 快捷回复已删。
- 模型覆盖 / 主题配方：README 承诺的角色级自定义（官方无对应字段，模型覆盖官方是聊天级 #custom_model_id）；存储+UI+聊天背景已做；🟡 字体文件下载、风格档位映射未做。

### 4.3 聊天页 🟡（核心已接线 + 媒体 + 状态胶囊）
- 发送：PromptPipeline 总装流式发送（世界书/宏/人设/AN/示例/历史/控制提示/工具/媒体/推理签名全引擎内完成，SSE 逐 token）；停止=取消 OkHttp call 保留已生成（mes_stop）；重新生成=删最后 AI 回复复用最后用户消息（option_regenerate）；继续=mes_continue（移出最后 AI，流结束合并落盘）；send_if_empty 已接；冒充=Generate('impersonate')（流式进输入框不落历史）。
- 交互：复制/删除/编辑（updateMessage：isEdit 正则分位点 + 清/写 extra.bias）/长按菜单/最后一条 AI 常驻 4 键/清空二次确认/未配置模型横幅一键深链；Markdown+代码高亮（mikepenz m3/coil3/code 0.43.0）；用户消息气泡上限 320dp（AI 全文宽）；顶栏/输入栏 Cloudy 0.7.1 真背板模糊玻璃（sky 源层静态）。
- 滑动切回复全链：数据模型对齐官方 jsonl（swipe_id/swipes[]/swipe_info[]；ensureSwipes/syncSwipeToMes/Generate('swipe')/deleteSwipe/editMessage）；AI 气泡横滑（右=下一个/越界生成新变体，左=上一个）；计数条 n/N + 箭头；长按菜单变体列表 ModalBottomSheet；导出 jsonl 可直接进酒馆；世界书扫描按官方 prepareMessages（swipe 在 coreChat.pop 之后扫描，App dropLast(1) 等价）。
- 上下文占比胶囊（圆环+百分比+绿黄橙红分级+点开分解，分母=contextWindow）；世界书命中面板（条目名/命中键/常驻/位置/token）；快捷工具盘=“继续/冒充 + 全局快捷回复 chips”+ automationId 自动执行；图像生成/附件/TTS 已入快捷工具盘与长按菜单；全局正则开关在设置→正则。
- 滚动/键盘：reverseLayout=true（第 0 项=最新消息贴底，删掉三条 scrollToItem 强制滚动与 layoutInfo 手写贴底）；自动滚底=贴底跟随 + 上滑暂停 + 回底恢复；imePadding 只作用于“消息列表+输入栏”列；animateItem 已移除（Google Issue 395536917）；毛玻璃 sky 源静态化（消息列表不再参与模糊重绘）；逐条滚动零磁盘 IO——displayTextOf 组合期的全局设置/宏环境/正则脚本收敛 ensureDisplayCtx 缓存（随 DisplayCacheVersion/会话身份失效），usable 下标随消息表实例缓存，ExpressionPrefs 进程级缓存（旧实现每条消息进视口都读盘，逐条卡顿根因）。
- ❌ Claude 冒充的 assistant_impersonation 设置（默认空串，影响为 0，P2）——注：assistant_impersonation 已接 Claude 冒充预填（见 3.9/4.4），此 ❌ 作废。

### 4.3.5 聊天 Tab（会话列表）✅
会话按时间倒序、置顶优先；点卡片进聊天；长按/⋯ = 置顶/导出聊天 JSONL/删除（二次确认）；FAB 新建对话（AI 或选角色，UUID 会话 id，每角色可多会话，会话名=角色名）；空状态引导；置顶持久化（SessionRecord.pinned，兼容旧 JSON）；新建群聊入口（FAB → 勾选角色 → GroupRecord + 群聊设置 UI）。

### 4.4 设置 ✅（README 规格）
- 数据与隐私：导出全部数据（zip：角色/会话/聊天/头像/提供商配置）+ 数据位置透明 + 清除全部数据（二次确认）；首启引导（欢迎页 + 导入角色卡/直接开始/跳过）。
- 设置主页：对照官方移动端 8 分区抽屉重构（AI 响应配置 / API 连接 / 高级格式化 / 世界书 / 用户设置 / 背景 / 扩展 / 人设管理 + 数据与隐私/关于）；搜索（真过滤）；聊天页未配置模型一键深链进 API 连接；分区子屏：AiResponseScreen（参数预设/采样器/快速提示词/Prompt Manager 入口）、UserSettingsScreen（UI 主题/个性化/聊天与消息处理/自动滑动/续写）、MessageRenderScreen+TextTypographyScreen（渲染与排版）、BackgroundsScreen、PersonaSettingsScreen；外观（AppearanceScreen）：主题模式（浅/深/跟随系统）+ 24 套预设主题卡（预览色板 = 三圆点 + 宝石菱形 + 金属环，选中描边用主题 metal，架构见 4.5）+ 视觉氛围滑杆（VibePreset），实时预览。
- 提供商与模型：搜索 + 卡片列表（品牌 SVG/已配置 pill/我的连接）；详情页 = API Key（遮罩）/接口地址（未配置自动预填 providers.json 默认）/区域/账户 ID/API 版本/默认模型（底部弹层搜索）/上下文上限/最大回复/测试连接/保存/删除确认；模型页已补 top_k/min_p/top_a/repetition_penalty/seed/n/流式/logprobs/use_sysprompt + OpenRouter use_fallback/allow_fallbacks/middleout/providers/quantizations。
- 关于页：版本 0.1.0 / AGPL-3.0 / 数据仅本地 / 开源仓库。
- 语音（TTS）：Android 系统 TTS，语音/语速/试听；字段对齐官方 tts 扩展（enabled/voice/rate/auto_generation/narrate_user/narrate_by_paragraphs/skip_codeblocks/skip_tags/apply_regex）；朗读前 substituteParams；文本处理纯逻辑 TtsTextProcessor 单测 3 例；官方 1.18 无 STT，语音输入不假装。
- 服务页：翻译 8 家全实现（Libre/Google/Yandex/Lingva/DeepL/OneRing/DeepLX/Bing，协议对齐 translate.js；自动模式 responses/both/inputs/both；编辑后 translateMessageEdit 自动重译/清除）；图像（A1111/SDCPP/NovelAI/OpenAI gpt-image/HuggingFace/Stable Horde 异步轮询（官方 horde.js）/ComfyUI workflow+轮询；DrawThings 仅 macOS 已移除；官方默认 Default_Comfy_Workflow.json 不在仓库，由用户粘贴，登记）；向量（OpenAI 兼容嵌入/本地 BagOfGram；聊天历史重排+数据银行+强制激活+聊天 ⋮ 管理）；翻译/图像/向量未完成项见 5。

### 4.4.5 应用图标 ✅
launcher 图标 = 用户原图（Download/file_0000000078d0820782054bfedd4cb346.png）缩放为 mipmap-xxxhdpi/ic_launcher.png（192px），Manifest 引用 @mipmap/ic_launcher；换图替换该 PNG。

### 4.5 主题系统 ✅（全局层）
**生成管线**：ThemePreset → Theme.kt 生成 M3 ColorScheme（浅色主色自适应加深至 WCAG 4.5:1 + 氛围底色，深色 schemePrimary ?? lighten(seed,0.30) + 夜色底）→ MainActivity 贯通 MainScreen → SettingsScreen → AppearanceScreen；**容器色阶整面染主题色相**：浅色容器阶梯直接向 seed 染色（5.5%–18% × contrast，主题感从"点点缀"变"整面画布"——卡片/顶栏/输入栏/气泡全走这条阶梯，画布本身染 10%）、深色容器向"主色调白"提亮 45%（卡片像被主题光照射——火光反射在铠甲上，阶梯比例 7.5%–20.5%，官方主题豁免仍用纯灰阶）；浅色不再是白纸——24 套 lightBg 全部重做为氛围日间版（天青釉/旧画布/羊皮纸/海雾/暮霭紫），深浅同性格；VibePreset 视觉氛围（降饱和/冷暖/光效，外观页滑杆）对整盘 scheme 后处理（standard 档与官方主题豁免）；玻璃表面 5 处（聊天顶栏/输入栏 + 首页顶栏/搜索顶栏 + 玻璃 FAB）接 Cloudy 0.7.1（静态 sky + 边缘高光）；角色卡驱动主题管线（seed/形状/字体/浅深锁定，角色配方优先，全局兜底，艺术字段全默认=不启用，角色主题沿用全局预设的 texture/aura）；聊天背景三层（显式 > 头像玻璃（模糊五档 0/12/24/36/48 + 遮罩色/强度）> 氛围渐变）。

**ThemePreset 字段架构**（ThemePreset.kt）：
- 基础：seed/secondary/tertiary 三强调色 + lightBg/darkBg 氛围底/夜色 + shape（square 4dp / default 12dp / rounded 16dp / circle 24dp）+ spacing 间距节奏倍数 + motionScale 动效速度倍数
- 官方对齐：st*（消息渲染正文/强调/下划线/引用/气泡/阴影，对齐官方 SmartTheme 变量）+ scheme*（M3 角色覆盖；酒馆官方主题填绝对真值，无浅色模式恒按官方深色渲染且豁免一切滤镜）
- 艺术扩展六字段（默认全关；官方主题恒关；实现全在 ArtBackdrop.kt）：
  - `contrast` 明暗对照（chiaroscuro）：缩放 surfaceContainer 色阶梯，>1 夜更沉卡片浮更亮、<1 雾感压平（clamp 0.6–1.6）
  - `gem` 宝石色：聊天页左下光晕（深色 alpha 0.13 / 浅色 0.09）+ 主题卡菱形预览
  - `metal` 金属色：聊天页右上角第二氛围光（对角呼应宝石光）+ 选中主题卡描边 + 圆环预览
  - `backdrop` 画布底材 = **BackdropSpec 三层画板级自由配方**（不再是 3 选 1，也不是只有纹理）：
    1. **washes 色域泼彩**：N 个径向色斑（ColorWash：颜色/位置/半径/浓度），软边渐变落下去像水彩湿画/光晕/撞色泼彩；
    2. **gradient 定向渐变**：CanvasGradient（任意多色 + 角度 0–360° + 浓度），日落/夜幕/极光的整面底色；
    3. **texture 六图元纹理**：weave 织纹（主线+错位辅线两级、逐段抖动）/ stipple 布点 / hatch 定向排线（角度可调）/ crossHatch 交叉排线 / fiber 微弯长纤维 / grain 细颗粒 + 缩放/强度/着色。
    绘制顺序 = 色域 → 渐变 → 纹理 → 内容（先铺底色再上肌理的作画顺序）；色域/渐变在浅色自动收敛（×0.8/×0.85）同一配方深浅都成立；drawWithCache 预生成一次（固定随机种子不闪烁），滚动/重组零开销；
    设置→外观→画布底材可全局自定义：跟随主题/自定义双模式 + **效果库 10 套一键配方**（落日熔金/霓虹雨夜/水彩粉彩/星穹夜幕/大理石云纹/火山余烬/极光垂帘/晨雾海面/羊皮古卷/素白画布）+ 渐变起终色色板/强度/角度 + 泼彩色板多选/浓度/**重掷布局**（randomWashes 换随机位置）+ 纹理 9 滑杆 + 实时预览；LocalBackdropOverride 贯通，BackdropPrefs JSON 持久化（色值 ARGB Long 往返）
  - `auraTop` 深色天空氛围：页面顶部渐变（红月/烟雾战场等，混合比 0.76）
  - `auraTopLight` 浅色天空氛围：象牙晨光/绯薄暮等——浅色模式同样有画面而非惨白纸面（混合比 0.80）；聊天页渐变 + emberBackdrop 四屏背景（角色/会话/设置/角色详情）均深浅双模式生效

**24 套主题清单**（性格 = 形状/间距/动效/艺术字段的组合，彼此不重复；全部非官方主题均配深浅双天空）：

| 组 | 主题 | 画种 · 关键性格 |
|---|---|---|
| 基础 11 | 墨韵 ink | 水墨：墨青色域浮于右上 + 宣纸草筋纤维+微尘，朱砂宝石 + 冷银金属 + 墨青天/青雾晨光，rounded 慢 0.85x |
| | 青瓷 celadon | 瓷器：无纹理（釉面光滑即身份），青釉在底部积出窑变釉泪（色域），天青夜/暖沙晨光 aura |
| | 夜航 night | 透纳海景：深青色域=海在底 + 琥珀色域=灯塔光在右上 + 画布织纹+海盐颗粒，黄铜金属，深海夜天/灯塔晨光 |
| | 丹砂 cinnabar | 篆刻金石：印泥朱色域沉在左下 + 蚀刻排线 60°+交叉（刀痕）+ 印泥朱宝石 + 刻石白金属，square 快 1.15x |
| | 琉璃 glaze | 玻璃器：无纹高反差 1.3，紫/青两色琉璃色域对撞 + 紫宝石/青金属，circle，紫夜/淡紫玻璃光 |
| | 石墨 paper | 石墨素描：全场唯一无宝石无金属（极简即身份），铅笔排线 30°+交叉+炭粉颗粒，灰纸光 |
| | 竹青 bamboo | 竹园晨雾：竹绿色域在右下 + 竹叶长纤维+雾尘 + 竹黄宝石/苔色金属，黛绿夜天/竹雾晨光 |
| | 暮紫 dusk | 暮霭水彩：紫→橙黄昏渐变铺天 + 暮尘布点+水彩颗粒 + 落日橙宝石/紫银金属，紫暮天/暮光橙 |
| | 晨雾 mist | 全场唯一低反差 0.9（雾压平明暗），两片雾色域 + 雾滴布点+湿颗粒（intensity 0.8 收敛）+ 晨光金宝石 |
| | 樱粉 sakura | 樱吹雪：樱粉天空色域在顶 + 落瓣大布点（scale 1.5）+花瓣纤维 + 樱绯宝石/粉银金属，暮樱紫天/樱霞光 |
| | 酒馆官方 st | 官方 SmartTheme 逐值还原，无滤镜/画布/氛围（恒定豁免） |
| 艺术向 7 | 熔金 molten | 电影 teal&orange：teal→orange 对角撞色渐变铺满 + 粗织纹（scale 1.35）+胶片颗粒 + 熔金金属/余烬宝石，金夜天/金色黄昏光 |
| | 靛夜 indigo | 梦境星夜：靛蓝夜幕渐变从天垂下 + 星尘布点（scale 1.3）+微颗粒 + 星尘青宝石/月银金属，靛蓝天/淡靛光 |
| | 琥珀 amber | 古典油画：琥珀底光从画布底透上（渐变）+ 老画布粗织纹（scale 1.5）+龟裂颗粒 + 烛光琥珀/威尼斯红宝石，画布夜天/暖琥珀光 |
| | 黛山 inkwash | 水墨留白：两片雾色域浮于上下（山岚）+ 宣纸纤维最重（草筋横陈）+雾尘，枯金宝石，最慢 0.8x 最松 1.18x，青灰山雾光 |
| | 深海 abyss | 万米幽光：海水随深度压暗（渐变从底）+ 一柱浮游青光（色域）+ 浮游光斑大布点（scale 1.8）+悬浮微粒 + 浮游青绿宝石，深海 aura/浅海青光 |
| | 胶片 film | 柯达褪色：暖橙/冷青两片偏色色域对角分置 + 银盐颗粒全场最重+相纸微尘 + 褪棕宝石/灰青金属，褪色暖调光 |
| | 霓虹 neon | 雨夜霓虹：品红/青两片霓虹色域对角互撞（湿路面倒影），无纹方正（湿玻璃）+ 品红宝石/青金属，最快 1.1x，雨后粉紫光 |
| 画廊 6 | 猩红王座 crown | 巴洛克骷髅王：顶部金色天光 + 底部暗青烟雾（王座厅空气透视）+ 宫廷粗织挂毯（scale 1.6）+金粉颗粒 + 古金主色 + 红宝石光晕，黑曜石王座厅 |
| | 白鹰圣殿 knight | 白马白鹰圣殿：一柱神圣白光从穹顶洒下（色域）+ 大理石云纹纤维+石粉颗粒 + 象牙白主色 + 暗红宝石，蓝灰夜空/象牙晨光，square 庄重 |
| | 赤月森林 vermilion | 红月孤影：血月本体挂在画布顶部（红月色域）+ 地平线余烬（底部暖红域）+ 血月浮尘（scale 1.4）+ 红黑有限色调，circle，血月天/绯薄暮光 |
| | 亡者圣堂 requiem | 白袍玫瑰：底部玫瑰墓园寒光（白域）+ 立剑下血泊（极淡红域）+ 蚀刻全工艺（排线+交叉+布点三件套）+ 全场最高反差 1.45（白袍对极黑虚空），黑白红三色 |
| | 古海神殿 cobalt | 碧海神阶：钴蓝海从底部涨上来（渐变）+ 海面天光（色域）+ 版画组合（布点+15°海风排线+交叉）+ 钴蓝海/大理石/赤月宝石，钴蓝夜天/海雾蓝光 |
| | 禁忌档案馆 archive | 原创·五画元素融为一馆：一盏烛灯悬在画布上方（烛光色域）+ 羊皮纸（草筋纤维+颗粒+尘点）+ 烛光古金主色 + 红宝石微光 + 蓝宝石点缀 + 旧铜金属，烛光暖天/羊皮纸暖光，最松 1.2x 最静 0.85x |

🟡 MeshGradient 氛围背景未做（README 可选）。

### 4.5.5 图标系统 ✅
全 App 图标 = **Font Awesome 6 Solid**（512 viewport viewBox，与酒馆官方前端同库同形），内置 88 枚 `app/src/main/java/com/emberinn/app/ui/icons/FaIcons.kt`（scripts/gen-fa-icons.mjs 从 Font Awesome 官方 SVG 生成，增图先加清单再重跑脚本；SVG 缓存 .fa-cache/）。material-icons-core/extended 与旧 Phosphor 已移除。规范：默认 onSurfaceVariant、激活 primary、警示 error。

### 4.6 数据存储 🟡
角色卡 characters/*.json + avatars/*.png；会话 sessions/*.json（含 pinned）+ chats/*.jsonl；提供商 profiles.json；主题 SharedPreferences（README 计划 DataStore 未迁移）；ProviderState 进程内共享（设置保存/切换/删除后刷新，聊天页订阅，仅进聊天页读一次盘兜底）；Room 未引入。

### 4.7 App 接线总表（官方行为怎么接）
> 原则：App 只做“调用引擎 + 渲染结果”，不再重写逻辑；每项注明官方源码位置。

| 引擎能力 | 官方源码位置 | App 接线点 |
|---|---|---|
| 流式渲染 | sse-stream.js + openai.js eventSource | LlmClient.streamChatCompletions → SseChunkParser → ViewModel 增量状态 → 逐 token 追加；停止=取消 OkHttp call；流结束必须走 onDone |
| 提示词组装 | openai.js prepareOpenAIMessages + populateChatCompletion + script.js generate | PromptPipeline.prepare 一个入口出最终消息；App 发送前调它 + 按协议走 ChatRequestBuilder/Anthropic/Google |
| 消息转换 | src/prompt-converters.js | Claude/Gemini 在各自 builder；Mistral/xAI/Cohere/AI21 在 LlmClient 协议分支；OpenRouter 在 openai-compatible 先签名/媒体再序列化 |
| 工具/能力选项 | chat-completions.js + openai.js oai_settings | ProviderRequestOptions 承载 tools/tool_choice/json_schema/web_search/request_images/safety；LlmClient 按厂商官方形态写入请求体 |
| 预算计算 | chat-completions.js calculateClaudeBudgetTokens/calculateGoogleBudgetTokens | LlmClient 按模型/effort 调两个预算函数，结果进 builder reasoningBudget（adaptive→effort/auto→不加/数字→budget_tokens/thinkingBudget） |
| Markdown 渲染 | Showdown + highlight.js + DOMPurify | ✅ 显示格式化序列进引擎差分（MessageFormattingEngine + message-formatting 805 例）；渲染层 mikepenz + Highlights/KodeView；encode_tags 开关（默认关=渲染）；Mermaid WebView 兜底（mermaid.min.js 本地 asset，网络/外链放开，JS 全开只拦 javascript: URL——官方 DOMPurify 禁脚本，有意偏差） |
| 媒体渲染 | openai.js Message.addImage/addVideo/addAudio + media.js | extra.media → MediaEngine.getFromMime → Coil3（图片/GIF）/ Media3 ExoPlayer（音视频）；URL 附件按官方逻辑下载/展示 |
| 世界书注入 | world-info.js checkWorldInfo + openai.js | 发送前：条目 → Scanner（正则 messageTransformer、RAG 强制激活）→ PromptAssembler；命中灯只读 Scanner 结果 |
| 宏 | macros/engine/ | 所有文本入 prompt 前统一走 MacroEngine（世界书 format、AN、历史 preparePrompt 已引擎接线）；App 保证 MacroEnv 提供聊天/角色/系统状态 |
| 正则 | regex/ | 存前（sendMessageAsUser/saveReply/getFirstMessage）+ 总装（isPrompt=true/depth）双位点接入 RegexPipelineEngine；替换串宏替换已接线；允许列表 character_allowed_regex；global/preset/scoped 分桶；命名预设集已做 |
| 群聊 | group-chats.js | GroupActivationEngine 选成员 → GroupCharacterCardsEngine 合并卡 → GroupDepthPromptsEngine → GroupLoopEngine → 按官方顺序拼接 |
| 表情精灵 | expressions/ + endpoints/sprites.js | ExpressionEngine.chooseSpriteForExpression → 消息头像区；LLM 分类已接（llmPrompt/parseLlmResponse → generateRaw/quietPrompt 异步分类切换） |
| 快捷回复 | quick-reply.js | 输入区快捷盘 → QuickReply 执行器（automationId 由 WorldInfoAutoExecute 判定） |
| 人设 | personas.js | PersonaStore 官方全字段 + 顶栏按钮 + ⋮ 菜单（搜索/选择/新建/编辑/删除/复制/备份/恢复/默认/锁定/同步名称/头像/世界书）；备份按官方 personas_YYYYMMDD.json 合并语义；effectivePersona = PersonaEngine.resolve（聊天锁 > 连接 > 默认 > 当前）；注入按 persona_description_positions 0/2/3/4/9；lorebook/matchPersonaDescription 已接 |
| 向量 RAG | vectors/index.js + utils.js | VectorRagService → VectorChatRearranger → scanner externalActivations → 3_vectors/4_vectors_data_bank 注入；数据银行 ⋮ 管理 |
| 预设 | preset-manager.js + power-user/instruct-mode/sysprompt/reasoning/openai.js | PresetApplyEngine 差分 99 例；PresetsScreen 五类选择即应用/保存/删除/单文件/master 导入导出；/preset exact+Fuse 7.1；sampler 应用到活动连接；reasoning 进 addToMessage |
| 作者注释 | authors-note.js | 三层：全局默认 + 角色备注（useChara/before/after/replace，applyCharaNote 差分 6 例）+ 聊天级 note_*；弹层 token 计数与下次插入计数；AuthorsNoteEngine.resolve 按用户消息数注入，ANWithWI 合并世界书 |
| tokenizer | src/tokenizers.js | TokenCounterFactory：OpenAI JTokkit；Claude/Gemini 回退 cl100k（豁免） |
| 提供商设置 | script.js / chat-completions.js | ProviderStore（profiles.json）多档案；协议/URL/认证/模型全在 LlmClient，UI 只读写 ProviderSpec + ConnectionProfile |

### 4.8 媒体盘点
- 引擎已做（差分全过）：MediaEngine 17 例（type/display/index 越界 NaN null）；MediaInliner 7 例（OpenAI content 文本→数组 + image_url/video_url/audio_url + detail）；MediaConverter 25 例（Claude/Gemini 内容块转换）；消息转换整链 41 例；MediaTokenCost 18 例（image low→85/auto≤512→85/2048 缩放→768 短边→512 方格 170/格+85；视频 263 tokens/秒（回退 263×40）；音频 32 tokens/秒（回退 32×300））。
- App 已接：extra.media 解析（mediaDisplay/mediaIndex）；Coil3 图片/GIF + Media3 ExoPlayer；系统文件选择器 → filesDir/media/，extra.media 只存路径+source:"upload"（官方 saveBase64AsFile 语义）；发送时读文件转 data URL → 引擎链内联 + token 预算；上下文胶囊 + 世界书命中面板；图库切换（LIST↔GALLERY + media_index 落盘）；从 URL 导入附件（后缀+魔数判型）；删除/清空/删会话清理附件文件。
- “只思考无正文/继续不出内容”已修：gpt-5 分支已移植（max_tokens→max_completion_tokens、删不支持采样参数，差分 27 例）+ 老档案默认值迁移（旧 maxTokens=512/contextWindow=8192 自动升厂商默认）+ providers.json 24 家补 default_context_window + model_contexts。
- 未做（登记）：URL 型资产下载（compressImage 近似：非 jpeg/png/webp 转 JPEG 最长边 2048）。

### 4.9 接线状态
聊天链路（发送/停止/继续/重新生成/冒充/编辑/删除/媒体/思考）全部接到引擎 1:1 能力上；上下文预算对齐官方（默认 4095/300，getMaxPromptTokens=context-response，必选提示词超限 ContextBudgetException，历史超限静默丢最老）；Claude 直连缓存参数已接线。

## 5. 完成度总览

- 引擎测试 **378 例全绿**；差分分组表 96 行（表内例数合计 2984；历史“85 组/1969 例”为旧口径，不再使用），明细见 [docs/DIFF_MATRIX.md](DIFF_MATRIX.md)。
- 剩余未做：图像生成其余 23 个后端请求体 / ADetailer / 样式库 / prompt templates / refine·interactive·multimodal 模式 / Comfy workflow 管理（见 3.12 登记）；Captions extras/local/horde 来源；未声明 closureArgs 的闭包仍即时求值（SlashEngine，见 3.4）；斜杠命令按用户决策裁剪（仅高价值命令，见 3.4）；发送链路未接项（见 6.2 登记）；自定义预设“设为默认”（官方无此概念）。
- Prompt Itemization 分节明细面板已做（聊天消息菜单；布局对齐官方 itemizationText.html；官方 itemized-prompts.js 语义：ItemizationStore 按会话持久化 rawPrompt + TokenHandler 八分桶 + 分节消息；五分类百分比图（Character Definitions=总 token−世界书−聊天历史−扩展−bias；World Info；Chat History；Extensions；{{}} Bias）+ 总 Token/Max Context/Padding/Actual Max Context；diff 词级 LCS，超大输入回退行级）。
- Prompt Manager 面板已做（设置→提示词管理器：identifier 自动 uuid 只读/name/role/injection_trigger 六选多选/position 0=Relative 1=In-chat/depth/order/forbid_overrides/content（marker 只读）/main·nsfw·jailbreak·enhanceDefinitions 官方 Reset/新提示项 system_prompt=false/删除二次确认/编辑底部弹层/长按拖动排序/官方 Append 下拉/“查”检查弹窗（PromptAssemblyCache 最近一次总装，官方 PromptManager.messages/handleInspect））+ dryRun 提示词预览（聊天会话菜单，全文+token）。
- 用户决策延期：Custom CSS + Moving UI（6.4）；Claude/Gemini 官方 web tokenizer。
- 官方发版流程：`node scripts/diff/*.mjs` + `node scripts/build-presets.mjs` → `./gradlew :engine:test` → 按 0.2 更新基线。

## 6. 不一致与边界登记（防漏机制）

> 规则：任何与官方 1:1 有出入的实现必须在此登记；未登记即视为未完成。

### 6.1 与官方差异表

| 功能 | 与官方的差异 | 状态 |
|---|---|---|
| 斜杠执行链 | 声明 closureArgs 的闭包参数（/inject filter）已原文保留+生成时求值（对齐官方 ARGUMENT_TYPE.CLOSURE）；未声明闭包（/if then/else）仍预解析立即执行；命令数少于官方=用户决策裁剪（仅高价值命令，UI 可点击操作不移植）；官方无 /while；/parser-flag REPLACE_GETVAR 官方为 no-op（已对齐） | 🟡 见 3.4 |
| 斜杠参数解析核心 | parseCommand/parseNamedArgument/parseUnnamedArgument/testSymbol 机器差分 18+27 例 1:1；执行链依赖 DOM/闭包无法逐字提取 | ✅ 差分 |
| 正则（该卡） | 存储/字段/位点同官方；存前/总装 isPrompt/编辑 isEdit/允许列表/全局开关全接；差异：①落盘文本宏未替换（发送时应用，请求等价）；②preset 脚本命名预设集（结构等价官方 preset 扩展字段） | 🟡 见 3.6 |
| 人设搜索 | 官方 FilterHelper 用 Fuse.js 模糊搜索（name 权重 20 + description 权重 3 相关度排序）；App 为名称/描述子串过滤，无相关度排序 | 🟡 UI 近似 |
| 人设同步 force_avatar | 官方写 getThumbnailUrl('persona', user_avatar) 缩略图 URL；App 写本地头像路径（导出 jsonl 官方无法解析） | 🟡 App 边界 |
| 人设备份头像 | 官方备份只含 avatar key（缺失时上传默认头像）；App 同 key，恢复时本地无文件回退默认头像 | 🟡 等价边界 |
| /preset fuzzy | 官方精确匹配后回退 Fuse.js；App exact + Fuse.js 7.1 移植（27 例差分） | ✅ |
| 预设导入 | 官方 openai 采样预设导入不校验字段（敏感字段 11 项剥离 + 同名覆盖已接）；textgen preset 进 textgenerationwebui 管理器；App 单导入入口，textgen preset 暂存 sampler 且不应用（后端未接） | 🟡 等价边界 |
| textgen 采样器应用 | 官方 setSettingByName 有 DOM checkbox/text/parseFloat 归约；引擎纯赋值，归约登记剥除 | 🟡 打桩登记 |
| 变量（该卡） | 官方变量是全局/聊天 scope，没有 per-character 变量；App 存 data.extensions.emberinn_variables（README 自定义，官方导入忽略） | 🟡 README 自定义 |
| 快捷回复 | 已按官方全局（QuickReplyPreset/Slot 字段 1:1 + QuickReplyExecutor）；差异：①官方多预设文件，App 单预设 filesDir/quick-replies.json；②点击槽位官方按命令类型处理，App 文本输出填输入框（/let 等无输出正确静默） | 🟡 存储/交互近似 |
| 角色详情保存 | 官方编辑器写 data.extensions.depth_prompt/talkativeness；App 同位置 + 额外把 readFromV2 提升字段镜像回 root（官方仅导入时提升），保证导出一致，不冲突 | ✅ 兼容增强 |
| 世界书 UI | 官方独立 World Info 面板；App 在角色详情页自绘增删改；数据格式与官方一致（v1→v2 归一，未知字段保留） | 🟡 UI 自主 |
| 角色 system_prompt/剧情后指令 | 曾漏传（角色系统提示词不生效）→ 已修：按官方传 fields.system/jailbreak，chat_metadata 同名优先 | ✅ |
| {{bias}} 提示词 | 曾不传 → 已修：提取 {{bias:...}} 并剥离宏、generate/swipe 注入、impersonate/continue 不注入（Handlebars 嵌套近似） | ✅ |
| chatCompletionSource | 曾恒 openai → 已按 provider.protocol 传 claude | ✅ |
| 人设 personaDescription | PersonaStore + 选中即 personaInPrompt=true（官方默认关，语义一致）；{{persona}} 宏可用 | ✅ |
| 扩展提示 extensionPrompts | 引擎支持 summary/AN/vectors + MemoryEngine 差分；App AN/记忆已接（source=main；extras/webllm 未接） | 🟡 |
| 工具调用 | PromptPipeline canUseTools/toolBudget/推理签名；ToolCallParser + ToolLoopPlanner 差分；App ToolRegistry 执行/历史重构/递归重装已接 | ✅ |
| 世界书设置 | 设置→服务→世界书（深度/递归/预算/大小写/整词），改动即存并用于扫描 | ✅ |
| 模型覆盖/主题配方 | README 角色页承诺；官方无角色级字段；已实现存储+UI+聊天背景+全局管线；配方导出/分享已做 | ✅ |
| 向量/数据银行 | 官方 Data Bank 是浏览器附件/URL 上传；App 存 filesDir/databank/ 本地文本（UTF-8）+ URL 下载（对齐官方语义）；本地 BagOfGram 为离线兜底（无官方对应） | 🟡 存储/交互近似 |
| Prompt Manager 顺序 | 官方 1.18 global 策略固定 character_id=100000；App 已统一存/读该键，preset prompt_order 按官方格式互导 | ✅ |
| Prompt Manager 应用范围 | 官方仅 chat-completion 源（main_api==='openai'）；App textgen/novel/kobold 路径已移除 PM 注入 | ✅ |
| 模型排序 pricing/context | sortModelsBy/groupModelsByVendor 1:1 差分移植（48 例）；模型列表带元数据，五源按官方字段排序/分组 | ✅ |
| context_size_derived / chat_template_hash | 官方按 llama.cpp/koboldcpp 模型信息派生；App 对 kobold 连接已接 GET /props 全流程（hash/派生/n_ctx）；llamacpp 官方独立类型无 App 提供商条目 | 🟡 登记 |
| 代理预设 | 官方 settings.proxies 全局列表；App 已改全局存储（旧档案侧数据自动迁移） | ✅ |
| 预设默认选中 | 官方加载设置即应用当前采样预设；App 冷启动应用一次（applySelectedSamplerPresetOnLoad） | ✅ |
| 内置预设删除 | 官方删除默认预设会从列表移除且可恢复；App 内置预设来自只读资源，删除仅切到下一个（资源不可删） | 🟡 登记 |
| Prompt Manager overridden 标记 | 官方行内显示“来自角色卡”覆盖标记；PrepareResult/缓存已带 overriddenPrompts，行内 🪪 图标显示 | ✅ |
| 内置预设裁剪 | 用户确认：老模型专用/用处不大的内置预设从 127 裁剪到 54（build-presets.mjs trimPresets 登记，重打包保持） | ✅ 用户决策 |

### 6.2 已确认 1:1 / 审计修复

已逐字/差分确认对齐：媒体内联能力白名单 + source 分支（24 例）；世界书 externalActivations/负深度/深度注入/EM 锚点/coreChat 过滤 is_system/ensureSwipes；斜杠解析器 43 例 + testSymbol 27 例（sendas 缺省名/sysname 空名 System/hide·message-role 语义/Comment 默认 Note/delswipe 1-based）；消息数据流（AI 落盘 swipes 结构、saveReply 尾部逐字段刷新、deleteSwipe 新 id、syncSwipeToMes、send_date=ISO、AI extra 恒有 api/model/reasoning/reasoning_duration/reasoning_signature、群聊 AI gen_id 整批共享 group_generation_id、普通用户消息 extra isSmallSys=false 无 gen_id、附件 media_index 恒写 inline_image=true）；提示词默认集合/顺序/populationInjectionPrompts/历史 preparePrompt 宏替换/AN interval 与默认 position=1/Generate 类型；正则 GLOBAL→PRESET→SCOPED + allowedOnly（7 例）。

审计修复（已修）：聊天流式卡顿——流式文本/思考状态只在流式行内订阅（每 token 不再重组合法整棵消息列表）+ 文本/思考 120ms 节流（思考卡顿主因是 ReasoningCard 每 token 全量渲染，现 8fps 上限）；show_thoughts 增加会话菜单快捷开关（官方默认 true，与官方一致，可即时关停并清空当前思考显示）；模型页按官方面板结构重组（连接/采样参数/预设联动与提示词：Logit Bias·消息角色与续写·工具与媒体·提示词模板含 main/nsfw/jailbreak 快捷编辑/连接高级/上下文与连接测试）；预设页按官方 preset-manager 重组（下拉选择+对选中项 更新/另存/重命名/删除/导出/恢复）；Prompt Manager 补 Token 列/总 Token/官方行图标（marker/global/important/user/injection/角色）；kobold 官方 GUI KoboldAI Settings 特殊预设（默认/不可更新/重命名/导出/恢复）；context story_string_position 与 instruct names_behavior 改官方下拉选项；start_reply_with/show_user_prompt_bias 移回 Advanced Formatting 位点；模型排序/分组按官方元数据差分（sortModelsBy/groupModelsByVendor/filterModelsBySource，48 例）；kobold /props 全流程（chat_template_hash sha256、context/instruct 派生自动选中、context_size_derived n_ctx 自动改上下文）；代理预设改全局存储+旧数据迁移；冷启动应用当前采样预设；Prompt Manager 全局顺序 key=100000（原 null/UUID 三键不互通）+ prompt_order 导出带 character_id；导入采样预设后即应用；删除预设二次确认+自动切换首个剩余；Unicode 预设名保存；textgen legacy 导入用文件名；bind_to_context 双向联动；auto-select 与 /preset 按活动协议+群聊名；sort_models 官方四项并限 5 源显示；request_images 组/impersonation_prompt UI；补 6 家官方提供商（electronhub/chutes/nanogpt/aimlapi/pollinations/cometapi）；reverse proxy 预设列表；删除 contextAuto/defaultMaxTokens 假“按厂商自动填”；reasoning auto_parse/add_to_prompts/auto_expand/show_hidden/max_additions 字段+UI；textgen/novel/kobold 路径移除 PM 注入；用户消息保存顺序（regex→substituteParams→removeMacros，token_count 落盘）；AI 消息补 time_to_first_token；AI_OUTPUT 正则改在 cleanUpMessage 停用词裁剪后注入；开场白数据格式（extra={}、无 title/gen_*、空首条 swipes.shift()）；continue 合并刷新 send_date/gen_started（时长守恒）/token_count；滑动变体 gen_id 仅群聊 + reasoning_duration/signature；历史索引错位（media 挂错）；bias 提取最后用户消息 + 编辑存 extra.bias 回溯；/hide 语义；comment 不进提示词；系统消息防误操作；continue swipe_info 同步；发送失败不丢输入；重生成先查配置；群聊配置实时；书签路径消毒；世界书条目删除确认；角色主题/背景实时刷新；平板导航轨；滑动返回手势；返回按钮不贴最高处；设置主页重构官方移动端 8 分区（AI 响应配置/API 连接/高级格式化/世界书/用户设置/背景/扩展/人设管理 + 数据隐私/关于）；设置默认值字段级对照官方（auto_continue.target_length=400·allow_chat_completions=false、textgen temperature_last=true·top_p=0.5·top_k=40·top_a=0、NovelAI 采样默认、Kobold 空配置回退官方 kai_settings 默认）；表情 LLM 分类（llmPrompt/parseLlmResponse 对齐官方 getLlmPrompt/parseLlmResponse + 生成后异步分类切换）；/inject filter 闭包（closureArgs 原文保留 + isTrueBoolean 生成时门控）；/genraw instruct/as（InstructMode.createRawPrompt 消费 instruct 开关/协议分支）；NovelAI 差分 default_order 修正为官方数字索引数组。

登记边界（有意保留）：extra.api 存提供商 id（官方存 source）；落盘文本未过 regex/宏替换（发送时应用，请求等价）；bias 文本提取 vs extra.bias 双轨；/hide name 过滤；narrator/sendas bias-only is_system；SWAP/APPEND 旧版近似；openrouter/mistral 模型元数据缺失回退；远程 URL 附件；Room/DataStore、插件 API、网络代理、视觉小说、STT、翻译自动模式、记忆摘要（官方默认关/远期）。

### 6.3 渲染已知限制（App/UI）
- 原生 mikepenz 列表/表格样式与官方 CSS 非逐像素一致（视觉近似）。
- 全站文字阴影覆盖聊天内全部文字；按钮/输入栏等 UI 未加（官方 `*` 全站）。
- 气泡为平涂半透明色，官方是毛玻璃 tint（色值一致，质感差一层）。
- Markdown 表格单元格/任务列表 checkbox 文本走库内直绘，官方字段可能残留占位符（低频）。
- 流式中间态为轻量近似（官方每 tick 全量 messageFormatting）；最终一致。
- abbr/acronym 官方虚线下划线，Compose 无虚线用实线近似；嵌套 sub/sup/small 按单层 0.83×（官方逐层累乘），极低频偏差。
- 官方页面级交互（click-to-edit/消息按钮/角色自定义样式开关）未实现；消息内脚本官方禁、我方放行（有意偏差，见 7.4 安全）。
- 行内 Web 标签（button/input/select/.../span[属性]/font face-size/ruby/bdi/bdo 等）整段走 Web（Compose 无法原生文字+行内控件混排）。
- 无属性 `<div>`/`<p>` 用 `\n\n` 段落近似；img width/height 不保留；残缺元素延伸到末尾、跨围栏按片段处理（低频）。
- WebViewPool 上限 6；HTML 开关关闭时围栏外一律原生且 < > 已转义；WebView 链接 text-decoration:none；高度允许回缩、按实测全高展开。

### 6.4 用户决策延期：Custom CSS + Moving UI（暂不做）
- Custom CSS：官方写 data/_css/user.css 套整个 Web UI；EmberInn 是原生 Compose 无 DOM，无法 1:1。
- Moving UI：官方设置→移动界面（top/left/.../margin + 命名预设 default/content/presets/moving-ui/*.json，1.18 自带只有空 Default.json）；**官方 isMobile() 直接禁用**。
- 结论：1:1 不可行（依赖 DOM/CSS，且官方移动端禁用）。等价方案待选：A 自定义 CSS 限定 WebView 交互卡（推荐）；B 主题 JSON 编辑器；C 布局预设。用户答复：先记录以后再做，未选 A/B/C。本项不参与差分。

## 7. 渲染与 HTML 卡片

### 7.1 官方管线 vs 我方管线
官方：script.js `messageFormatting` → Showdown(makeHtml) → DOMPurify → style.css 渲染。
我方：`displayTextOf`/`displayReasoningText`（引擎 MessageFormattingEngine 纯文本子集，差分 805 例；含首条宏替换写回 chat.mes 与非系统 trim）→ `preprocessOfficialHtml`（代码保护 + 官方标记化 \uE001-\uE007）→ 原生 mikepenz Markdown + `OfficialMarkdownNode`（buildMarkdownAnnotatedString + applyOfficialMarkers）→ 或 WebView 兜底（officialStyledHtml + 自动测高）。渲染层全部 App/UI，引擎只负责格式化序列。

**逐项对照表与文本级 HTML 标签细节见 [docs/RENDER_AUDIT.md](RENDER_AUDIT.md)。**

### 7.2 交互 HTML 卡片 / iframe 渲染器（App 层，第三方机制）
- 定位：App/UI 层，官方本体没有（官方 DOMPurify 禁消息脚本）。机制参照 Tavern Helper 渲染器与阡濯《ST酒馆 html 代码注入器》（userscript，CC BY-NC 4.0——只参考机制未搬运代码；若日后搬运注意非商用）。
- 开关：设置→扩展插件→交互 HTML 卡片（`ExtensionPrefs.interactiveCards`，默认开）。渲染与交互分离：``` 内 HTML 代码块无论开关都渲染成 iframe 卡片；关闭时 `sandbox="allow-same-origin"`（静态渲染、脚本/表单禁用）。
- 实现：ChatMarkdown 先按 ``` / ~~~ 分段（buildMessageSegments）；交互卡段（``` 内以 `<` 开头以 `>` 结尾或含 `<body>`）与 Mermaid/富 HTML 段各自进独立 WebView，围栏外文本走原生；embedInteractiveBlocks 做 `<iframe srcdoc>`（实体转义 + onload/ResizeObserver/MutationObserver 持续同步）；WebViewHtml JS 恒开、网络与外链放开、实例来自 WebViewPool 复用；加载方式=原文 UTF-8 + file base（曾因 base64 不解码导致空白，已修）；整页文档（<!DOCTYPE html>）整段走 WebView，兜底 CSS 注入原文档 `<head>`（不再 html 套 html）；测高/样式注入点跳过 `<script>/<style>` 文本内的伪 `</body>`；`allowFileAccessFromFileURLs`/`allowUniversalAccessFromFileURLs`/`MIXED_CONTENT_ALWAYS_ALLOW` 全开。
- 能力对照：```→iframe 脚本可交互 ✅；非 HTML 代码块保留显示 ✅（pre/code）；自动测高 ✅；围栏外文本保留换行 ✅；头像类 `.char-avatar`/`.char_avatar` + `{{charAvatarPath}}` ✅（`{{userAvatarPath}}` 暂空登记）；min-height vh 换算 ➖；原代码折叠 ✅；后台脚本库/表情 VN STT EJS 变量/插件市场 ➖（App 等价物 = Kotlin 引擎 + 快捷回复/斜杠）。
- 手工回归清单：①单个 ``` 包 HTML+onclick 按钮可点、高度自适应不撑爆；②交互块+普通文字/代码块混排正常；③纯 HTML 消息（无围栏）正常；④远程图片/字体可加载（离线占位）；⑤长网页 ≤90% 屏高全高展开、超上限 WebView 内滚动，`height:100%;overflow:hidden` 页面被注入 `height:auto!important;overflow:visible!important` 还原。
- 安全：交互代码块（开关开）= 执行任意脚本（可发网络请求、可读该消息 WebView 内一切）；唯一 JS 桥 EmberInnBridge 只收“高度/未加载图片数”两个整数，不暴露 Android API/本地文件（除 asset）。与 JS 全开同风险等级，官方默认禁止，属有意偏差；收紧时先关 `settings.javaScriptEnabled` 或恢复 sanitize 剥 script。

### 7.3 分段渲染 / WebView 池 / 测高（App/UI 层）
- 分段：carveWebElementRanges 切块级 Web 元素（table/ul/ol/li/blockquote/pre/h1-6/.../iframe/style/script/form 及带属性 div/p、face/size font），周围文字保持原生 Markdown；围栏（含未闭合围栏，行首 ```/~~~ 无闭合时延伸到文本末尾、对齐官方 marked 按代码块渲染）内不切 Web 元素；再按 ``` / ~~~ 切交互卡/Mermaid/普通代码块；围栏外文本命中 OFFICIAL_HTML_TAG 或 MessageHtml 且 htmlEnabled → WebView，否则原生。
- WebViewPool：ArrayDeque 闲置池（上限 6）；release 不 about:blank，保留已渲染页面 + WebViewSession（loaded/loadToken/heightPx）；token 每次进入换新，复用同页不重载、记忆高度直接恢复；同页滚动出屏不再销毁重建；池化实例整页重载期间 INVISIBLE、onPageFinished（token 校验后）恢复显示，复用不闪上一条消息的旧页面。
- 测高：ResizeObserver(html+body) + load + fonts.ready + 图片未就绪 800ms 低频轮询（20s 上限）+ onPageFinished 纯字符串轮询 ≤15s + 初始 160dp 兜底；公式 = html/body scrollHeight 与 getBoundingClientRect 最大值 + ≤8000 元素 max(bottom) 扫描 + ceil+2px；CSS 像素 1:1 转 dp（旧代码按物理像素/density 导致高密度屏压扁）；上限 maxOf(90% 屏高, 280dp)，超上限 WebView 内滚动；iframe 150/500/1500/3000ms 复测 + 父页观察同源 srcdoc 持续同步。
- 性能：animateItem 已移除（Google Issue 395536917）；毛玻璃 sky 源静态化（依据 Cloudy 源码 Sky.kt/SkyFrameDriver.kt：滚动活动触发每帧重捕；同屏玻璃 ≤2-3 处）；热路径缓存（chatTypography/chatTextShadow/NativeMarkdown 组件 remember；Markdown 解析 LRU 缓存 MarkdownCache.kt 上限 32）；行级参数稳定化（immersiveActions/bubbleStyle/density 层读一次、List<MediaAttachment> 包 @Immutable ChatMedia）；发送链路缓存（角色卡解析 LRU 8、外置世界书 mtime、连接档案 mtime、聊天元数据；JSONL/元数据落盘单线程后台队列；命中面板/上下文胶囊移出请求关键路径）。
- 代码块：mikepenz 默认 MarkdownCode 挂 horizontalScroll（源码 MarkdownCode.kt）导致长 JSON 框死；WrappingHighlightedCode（snipme 高亮 + softWrap 换行）替换 codeFence/codeBlock（官方 overflow-x:auto，我方有意改换行，内容完整可见）。
- 第三方扩展卡说明（MVU/酒馆助手/EJS，如“苍玄界”）：完整渲染依赖三层——①角色卡自带正则（官方核心，须角色详情开启“允许此角色应用该卡正则”→ character_allowed_regex；开启后 `【GameStart】`→HTML 启动页、`<inner>`→心声卡片、`<StatusPlaceHolderImpl/>`→状态栏）；②MVU 变量系统（`<UpdateVariable>/<initvar>/JSONPatch` + `{{format_message_variable::stat_data}}`，第三方扩展，官方核心不含）；③EJS 模板（`<% getvar(...) %>`，ST-Prompt-Template/酒馆助手，官方核心不含）。本 App 与官方核心一致：只做①接线与②③“未知标签可见”兜底，不实现 MVU/EJS 扩展本身。

## 8. 维护速记与注意事项

### 8.1 常见编译坑（CI 红→绿经验）
1. 注释里写 `group-chats/*.json` 会触发 Kotlin 嵌套注释吞文件 → 写成“目录的 *.json”。
2. 缺 import、括号不配对、前向引用属性 → push 前自查。
3. M3 1.4：Typography 无 defaultFontFamily；Modifier.padding 不能混用 horizontal+top。
4. 正则字符串里 `\s` 必须双反斜杠（非 raw string）；helper 别嵌局部函数。
5. 全局替换函数名时 `return@旧名` 标签必须同步改名。
6. Modifier 扩展用 rememberUpdatedState 必须包 `Modifier.composed`。
7. App Kotlin 可本地编译（`:app:compileDebugKotlin`）；APK 组装/签名靠 CI，push 后以 `gh run list` 为准，网络不稳重试。

### 8.2 注意事项
- 兼容层 1:1，UI 层自由：数据格式、注入算法、宏展开、斜杠行为、导入导出必须与官方互读互通；界面/交互/主题自主。
- 改动先对照官方源码，能 1:1 就 1:1，近似项必须标注（登记 6.1/6.3）。
- App Kotlin 编译本机可跑（Android SDK 已装）；APK 组装/签名走 CI；引擎测试本机可跑。
- push 自动触发 CI，必要时 `gh workflow run 328789880 --ref main`；GitHub 网络不稳定失败重试。
- 沙箱会话重置会丢 GitHub 凭证（gh auth/token）：push 失败先查 `gh auth status`，缺凭证就 `gh auth login` 或临时 PAT，不要反复盲推。
- 删除类操作先确认；大改动保持小步提交。
