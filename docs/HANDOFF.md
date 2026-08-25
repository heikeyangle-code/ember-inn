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

- 基线 = SillyTavern release `8172dcd`（1.18.0），源码 `/root/sillytavern-ref`；全部“已对齐/已差分/已实现”结论与 fixture 均以此为准。
- 官方更新流程（不可跳过）：①更新 ref 到新 release 并记录 commit；②重跑全部 `scripts/diff/*.mjs` 重新生成 fixture，红/变的就是差异，逐个对照移植；③新功能按“先穷举 case → 差分 → 实现”流程；④`./gradlew :engine:test` 全量 + App 等 CI；⑤更新 0.2 基线、第 2 节组/例数、第 5 节完成度与相关模块状态。

## 1. 项目与常用命令

- 项目：EmberInn（余烬酒馆）——原生 Android SillyTavern 兼容客户端；本地 `/workspace`，远程 github.com/heikeyangle-code/ember-inn（main，公开）；官方参照 `/root/sillytavern-ref`（release 8172dcd / 1.18.0）。
- 引擎测试与 App Kotlin 编译本机均可跑（Java 17.0.2 + Gradle 9.7.0 + Android SDK 34，当前 **559 例全绿**：381 testcase + imagegen-services 57 fixture / tts-services 35 fixture / tts-local 38 fixture 各单 testcase 内 for-loop）；完整 APK 组装/签名走 CI。

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

**差分分组清单（97 行，表内合计 3040 例；历史 85/1969 为旧口径）见 [docs/DIFF_MATRIX.md](DIFF_MATRIX.md)，含打桩/未差分登记；新增 message-formatting-official.mjs → MessageFormattingDiffTest 805 例、cfg-prompt 25 例、logprobs 20 例、imagegen 10 例、imagegen-services-official.mjs → ImageGenServicesDiffTest 57 例（9 云端后端 + 5 LLM 后端 + replaceComfyWorkflow 纯函数 + getClosestSize 工具函数 4 例）、tts-services-official.mjs → TtsServicesDiffTest 35 例（11 云端后端）、tts-local-official.mjs → TtsLocalDiffTest 38 例（13 本地后端）、chat-template-official.mjs → ChatTemplateDiffTest 25 例、model-sort-official.mjs → ModelSortDiffTest 48 例。**

**打桩/未差分登记与“官方有而引擎/App 还没有”清单见 [docs/DIFF_MATRIX.md](DIFF_MATRIX.md)。**

**端到端审计（2026-08 真实浏览器行为比对，非手写期望）**：用真机 Puppeteer 启动官方 /workspace/SillyTavern（release），注入同输入分别喂官方与引擎逐字符 diff。5 个易藏隐藏细节已锁定：
- 世界书递归：最大深度、多词条触发环路截断、预算超限砍序、position 拼装顺序 → `.scratch/audit/probe4-wi.mjs` + `engine/.../worldinfo/WorldInfoRealDiffTest.kt`（7 场景全绿，官方 guesstimate token 口径）。
- 宏嵌套（宏内嵌宏展开顺序）→ `probe5-macro.mjs` + `MacroDiffTest`（官方 158 例全绿）。
- 正则执行时机（WORLD_INFO 正则=BUILDING_PROMPT 阶段、promptOnly 聊天理性先于世界书扫描、默认脚本 prompt 阶段不执行）→ `probe6-regex.mjs` + `engine/.../regex/RegexTimingDiffTest.kt`（3 场景全绿）。
- 群聊多角色 prompt 拼装顺序、token 截断优先级 → 引擎 Pipeline 总装链路已按官方 prepareOpenAIMessages 差分（见 3.5）。

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
- 2026-08-20 真实浏览器审计：`probe4-wi.mjs` 在官方浏览器注入同训练注入同输入，锁最大深度截断、多词条环路截断、预算超限砍序、position 拼装顺序；`WorldInfoRealDiffTest.kt` 7 场景全绿（官方 guesstimate token 口径，见 2）。

### 3.3 宏 ✅（含作用域宏）
通用作用域宏（{{setvar}}/{{#}} 保留空白/嵌套/trim+dedent，对齐 MacroCstWalker.processScopedMacros）；trimScopedContent 差分 7 例；!?~> flags 官方标 TBD（无需补）；配对逻辑依赖 chevrotain CST 无法逐字差分（源码对照+单测）。核心宏 + 官方 e2e 158 例；变量简写全运算符、{{if}}、{{trim}} 作用域、legacy 标记、嵌套参数、字段宏、聊天/状态宏；{{pick}} 用 seedrandom@3.0.5 逐位一致（5 例）。{{outlet::key}} 差分 5 例（官方 core-macros.js 逐字提取；空 key 未判空已修）；MacroRegistry 动态注册/注销/解析；角色字段已接线（{{description}}/{{chardepthprompt}} 等可用）；聊天/系统状态宏已补齐（{{lastmessage}}/{{lastmessageid}}/{{lastusermessage}}/{{lastcharmessage}}/{{lastswipeid}}/{{lastgenerationtype}}/{{time}}/{{date}}/{{weekday}}/{{random}}/{{roll}}/{{pick}}/{{if}} 等；MacroEnv 注入 chat/lastGenerationType/firstIncludedMessageId，App ChatPromptFactory.prepare 按官方 MacroEnvBuilder 接线）。

### 3.4 斜杠 ✅
SlashParser（命名/无名/引号/转义/list/rawQuotes）+ SlashEngine（管道/闭包/双管道）、/pass /let /qr-arg、{{var}}/{{pipe}}/{{arg}} 状态宏、快捷回复执行器；testSymbol 差分 27 例；参数解析核心 43 例差分；斜杠数学/布尔/len/sort 1:1（SlashMathEngine 差分 444 例——注意最新 SlashMathDiffTest 已对齐 fixture 444 例，见 DIFF_MATRIX；历史 288 为旧 fixture）；输入框斜杠补全 UI（/ 前缀过滤、最多 12 条、220dp 可滚动）。
已接命令：/renamechat /getchatname /setinput /bg /impersonate /persona-set /trigger /inject /gen /genraw + 异步执行器；消息类命令（/sendas /send /impersonate return=）已对齐（sendas 缺省当前角色、SLASH_COMMAND 正则 characterOverride、{{bias}} 只偏置→is_system、avatar/compact 落 extra/force_avatar/isSmallSys、swipes 初始化；return= 官方 slashCommandReturnHelper：pipe/object/toast-html/toast-text/console/none）；按角色头像渲染已接（extra.force_avatar/original_avatar → avatars/{id}.png）。
- /inject filter 闭包 ✅：引擎 closureArgs 机制（对齐官方 ARGUMENT_TYPE.CLOSURE）——命令声明的闭包参数保留原文（trimEnd，宏不提前替换），ScriptInject.filter 持久化，生成时注入聊天变量求值（isTrueBoolean：true/1/yes/on/y 才注入，空/解析失败=始终注入）。
- /genraw instruct/as ✅：text completion 路径走 InstructMode.createRawPrompt（instruct 开关 + 协议分支 + quietToLoud），对齐官方 generateRawCallback。
- 扩展斜杠 20+ 条 ✅：AppSlashExecutor 注册完整——/db /db-list /db-get /db-add /db-update /db-disable /db-enable /db-delete /db-show /db-hide /db-apply /db-list-inline /db-parse-inline（13 条 db 家族，三源 global/character/chat 目录隔离 attachmentsContext）；/listGallery /installAsset /deleteAsset（assets 3 条）；/vectorize /index /vectorize-faiss（向量 3 条）；/imagine（ImageGenClient.generate）；/caption（Caption 扩展命令入口）；/qr（Quick Reply 切换预设/列全部）；/expression（表情精灵 set=标签/列当前角色精灵）；/world /world-list /world-get（世界书 3 条，WorldStore 列表/导出）；/member（群聊成员 add/remove/list）。对照官方 slash-commands.js + extensions/*/*.js 条目齐全；vectorize/index/caption/member/world/expression/imagine 各自回调真接对应 Service/动作（非桩）。
裁剪决策（用户确认）：斜杠命令只移植高价值命令（生成链/注入/变量/消息类/扩展家族），UI 点击可完成的操作命令不移植；命令数少于官方属有意为之。
剩余偏差：未声明 closureArgs 的闭包（如 /if then/else）仍预解析立即求值（官方惰性，非核心路径）；官方 1.18 无 /while；/tokens 用 cl100k 近似（用户豁免项）。

### 3.5 提示词组装 ✅（核心）
PromptManagerCore、PromptCollection/ChatCompletion 嵌套集合（预算/溢出/squash）、ChatHistoryPopulator、DialogueExamplesPopulator、扩展注入（summary/AN/vectors/chromadb/persona）、in-chat 深度注入、continue nudge/prefill、bias、control prompts、工具调用、ToolLoopPlanner（RECURSE_LIMIT=5，差分 17 例）、人设 IN_CHAT 注入。
- PromptPipeline 总装器 1:1（prepareOpenAIMessages+populateChatCompletion；整链差分 29 例；populationInjectionPrompts 官方真函数；getExtensionPrompt 差分 19 例）；CharacterCardFieldsEngine 差分 6 例；PromptUtils 差分 9 例；AuthorsNoteEngine 差分 7 例（默认 position=1 修正）。
- 历史 reasoning 注入（PromptReasoningEngine.addToMessage 差分 7 例；add_to_prompts 默认关，continue 最后一条 prefix 不受开关限制）；角色 system_prompt/剧情后指令已真正进请求体（fields.system/jailbreak，chat_metadata 同名键优先）；每条历史消息过 preparePrompt 宏替换；names_behavior 修正（COMPLETION 才带 name，PromptNameSanitizer 28 例）；工具预分配/媒体内联/推理签名端到端 20 例。

### 3.6 正则 ✅
RegexEngine + substituteRegex/宏替换（27 例差分：g/首匹配/i/m/s/x/X/A/J/U 非原生 flag、u 原生、重复 flag 回退）；世界书 key 解析 parseRegexFromString（15 例，u/y 原生 flag 边界登记）；RegexPipelineEngine（getRegexedString：placement/markdownOnly/promptOnly/runOnEdit/minDepth/maxDepth/禁用扩展，9 例差分）；聊天消息正则已接入扫描器。
- 2026-08-20 正则执行时机审计：`probe6-regex.mjs` 真机锁官方执行阶段——WORLD_INFO 正则（placement 5）在 BUILDING_PROMPT 阶段改条目内容、promptOnly 聊天正则先于世界书扫描作用、默认（非 promptOnly）脚本 prompt 阶段不执行；`RegexTimingDiffTest.kt` 3 场景全绿（见 2）。
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
| Reasoning 解析 | ReasoningEngine.kt（parse/remove/formatReasoning；13 例） | ChatPromptFactory 488-526 行：历史 AI 消息 extra.reasoning 先 REGEX 正则（isPrompt=true+depth）再 PromptReasoningEngine.addToMessage 注入（含 addToPrompts/maxAdditions/prefix/suffix/separator；continue 最后一条 prefix 不受开关限制）；removeReasoningFromString 官方只在发送前输入框（reasoning.js 1604）、summary/memory/表情分类/翻译扩展结果处调用，发送链路"预算计算前对历史 AI mes 去标签"官方无此逻辑（已对照源码确认）；token 预算进总装时已含 reasoning 文本（与官方 getTokenCountAsync(mes+reasoning) 语义一致） |
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

### 3.12 图像生成（stable-diffusion） ✅（17 后端差分全量：9 云端 + 5 LLM + replaceComfyWorkflow）
官方扩展 generateAutoImage/generateSdcppImage 请求体 1:1 差分移植（imagegen-official.mjs 10 例）：prompt/negative/sampler/scheduler/steps/cfg_scale/width/height/seed/restore_faces/clip_skip/vae/HR 全字段，JSON.stringify undefined 省略语义一致。App 设置页补齐核心参数（前缀/负向/采样器/调度器/CFG/尺寸/种子/恢复人脸/CLIP skip/VAE/HR），消息级生成挂 extra.media（官方 sd_message_gen）。角色提示词前缀按角色 id 存储并在聊天生成时合并。
- **✅ 9 云端后端（imagegen-services-official.mjs 39 例 + getClosestSize 4 例）**：togetherai / pollinations / chutes / stability / aimlapi / electronhub / nanogpt / bfl / xai；引擎层 [ImageGenRequestEngine.kt](file:///workspace/engine/src/main/kotlin/com/emberinn/engine/prompt/ImageGenRequestEngine.kt) 新增 `togetherAiPayload`/`pollinationsUrl`/`chutesPayload`/`stabilityPayload`/`aimlapiBody`/`electronhubBody`/`getClosestSize`/`nanogptBody`/`bflBody`/`xaiBody`/`getClosestAspectRatio`；App 层 [ImageGenBackendsCloud.kt](file:///workspace/app/src/main/java/com/emberinn/app/data/ImageGenBackendsCloud.kt) 全部 9 云端后端改为只做 HTTP 接线（准则 2）；差分修复 Pollinations path `URLEncoder.encode`（space=+）→`encodeURIComponent`（space=%20）不匹配。
- **✅ 5 LLM 后端 + replaceComfyWorkflow 纯函数（第三批扩 18 例，差分 3→39→57）**：falai-server（服务端加工后 requestBody，4 例）/ google-client（客户端 body，2 例）/ zai（客户端 body，2 例）/ openrouter（客户端 body，3 例）/ workersai-client（客户端 body，4 例）/ replaceComfyWorkflow（ComfyUI 占位符替换纯函数，3 例）；引擎层新增 `falaiServerBody`/`googleClientBody`/`zaiClientBody`/`openRouterBody`/`workersAiClientBody`/`replaceComfyWorkflow`（含 `bflBody` 共用的 `numStr` 数字语义 helper）。
- **✅ App 层 LLM 全部改接线（准则 2）**：[ImageGenBackendsLlm.kt](file:///workspace/app/src/main/java/com/emberinn/app/data/ImageGenBackendsLlm.kt) 8 LLM 后端——Google/ZAI/OpenRouter/WorkersAI/FalAI/Extras/ComfyRunPod/DrawThings（macOS only 登记）——均只做 HTTP 接线：①从 ServicesPrefs 取参数构造纯值；②调引擎层对应方法得 JsonObject body；③拼 URL + Header + 发请求 + 响应解析（落盘 saveBase64/saveFromUrl/saveFromRunPodOutput）。Google Vertex 客户端翻译为 `{instances,parameters}` Vertex predict 契约、WorkersAI Cloudflare form 契约、ComfyRunPod `/run`→`/status/{id}` 轮询均属接线（不在差分范围，差分覆盖的纯函数 1:1 已保）；DrawThings 登记不实现。
- **✅ Comfy workflow 管理（App 层）**：[ComfyWorkflowStore.kt](file:///workspace/app/src/main/java/com/emberinn/app/data/ComfyWorkflowStore.kt) 多 workflow 文件存储（filesDir/comfy-workflows/*.json，对齐官方 data/default-user/content 文件语义）+ 内嵌官方默认 Default_Comfy_Workflow.json + 旧单字符串 sd_comfy_workflow 迁移；设置页 workflow 选择/新建（以默认模板初始化）/重命名/删除/JSON 编辑器（占位符提示）；标准 ComfyUI 与 RunPod 共用活动 workflow（官方同一 comfy_workflow 设置）。
- **✅ ADetailer（引擎 + App + 差分）**：[ImageGenRequestEngine.kt](file:///workspace/engine/src/main/kotlin/com/emberinn/engine/prompt/ImageGenRequestEngine.kt) 在 A1111 请求体注入 `alwayson_scripts.ADetailer.args`（face fix），[imagegen-official.mjs](file:///workspace/scripts/diff/imagegen-official.mjs) 差分用例；App 设置页“恢复人脸”开关经 [ImageGenClient.kt](file:///workspace/app/src/main/java/com/emberinn/app/data/ImageGenClient.kt) 透传 adetailerFace。
- **✅ 样式库（App 层）**：[StyleStore.kt](file:///workspace/app/src/main/java/com/emberinn/app/data/StyleStore.kt) 存储 {name,prefix,negative} 样式集合，设置页选择/保存/重命名/删除，生成时合并前缀与负向。
- **✅ prompt 纯逻辑引擎 1:1（imagegen-prompt-official.mjs → ImageGenPromptDiffTest 59 例）**：[ImageGenPromptEngine.kt](file:///workspace/engine/src/main/kotlin/com/emberinn/engine/prompt/ImageGenPromptEngine.kt) 移植 generationMode/modeLabels/triggerWords/messageTrigger、13 个 promptTemplates 逐字、stringFormat、getGenerationType/getQuietPrompt、parseInteractiveTrigger、processReply（minimal 与常规清洗两分支）；App 层 [PromptTemplateStore.kt](file:///workspace/app/src/main/java/com/emberinn/app/data/PromptTemplateStore.kt) 模板编辑/恢复默认 + 设置页“图像提示词模板”区块。
- **✅ refine / interactive / multimodal 模式（App 接线）**：设置页开关（sd_refine_mode / sd_interactive_mode / sd_multimodal_captioning / sd_free_extend）；[ChatViewModel.kt](file:///workspace/app/src/main/java/com/emberinn/app/ui/chat/ChatViewModel.kt) `generateImageSmart` 统一管线（模式解析 → quietPrompt → RAW_LAST/FREE/multimodal/LLM 提示词 → processReply → refine 确认 → 图像接口）：refine 生成前弹窗编辑（FREE 除外）；interactive 发送时命中触发正则则中止正常回复改自动生图；multimodal 用角色/用户头像经 LLM 视觉描述生成提示词；FREE_EXTENDED 走 LLM 扩写。

### 3.13 群聊 / 其它 ✅
群聊成员激活策略（15 例）、APPEND 角色卡合并（8 例）、深度提示（7 例）、完整循环纯逻辑 GroupLoopEngine（11 例）；App 调度层（GroupStore/新建群聊/GroupScheduler/顺序生成/续写重生成按最后成员）；natural/pooled 激活+队列提示；自动续写（shouldAutoContinue + /continue 链，默认关）；narrator 按官方 1.18 无独立模式关闭（/sys 旁白群聊可用）；TokenCounterFactory（OpenAI 精确 JTokkit）。

### 3.14 向量扩展（RAG 全量）✅（引擎层）
世界书 RAG（vectorized 同步/检索/强制激活）；聊天历史向量重排（enabled_chats/rearrangeChat）；文件/Data Bank 向量化（enabled_files：分块/overlap/检索注入）；FileVectorStore（磁盘持久化对齐 vectra 目录）+ InMemoryVectorStore；EmbeddingProvider（OpenAI 兼容 + BagOfGramsEmbedding）；查询语义对齐官方（multiQueryCollection 全局 topK/queryCollection 单集合，hashes 不过滤阈值）；扩展提示经 ExtensionPrompt（3_vectors/4_vectors_data_bank）注入组装管线（ChatCompletionPipeline KNOWN_RELATIVE）。未做：summarize（P3，官方默认关）、本地 transformers 嵌入（Android 用 Ollama 替代，接口已留）、translate_files（P3）。

### 3.15 表情精灵 ✅（引擎层纯逻辑）
ExpressionEngine（文件名→标签、图片元数据、分组排序、chooseSpriteForExpression fallback/多立绘随机/rerollIfSame/overrideSpriteFile）；sampleClassifyText（去宏/引号/星号、短文本裁句尾、长文本首尾各 250 拼接、LLM 模式仅 trim；8 例差分）；官方差分 14+8+7 例（expressions/index.js + endpoints/sprites.js + utils.js 逐字对拍）；SpriteStorage（spritesPath 子目录/sanitize + importRisuSprites）；LLM 分类 ✅（llmPrompt=官方 getLlmPrompt {{labels}} 模板 + parseLlmResponse=JSON {emotion} → removeReasoning 清理后模糊匹配 → null 走 fallback；App ExpressionScreen 开关/自定义提示词，ChatViewModel 生成后异步分类切换表情）；App 层 ExpressionStore 精灵目录 LRU 缓存（24 角色，save/delete/import 即时失效——对齐官方 spriteCache 语义，聊天列表滚动每条 AI 消息组合不再列目录）；DOM 显示/动画属 App 层；差分顺带修 VectorTextUtils.trimToStartSentence（Kotlin 需 coerceAtMost）。

## 4. 渲染内核（V2 核心，已完成）

### 4.1 架构定位
WebView 是唯一权威渲染器（否决原生 Compose 渲染路线）：内核 DOM 与官方同构，官方 style.css/主题/社区主题 CSS 原样生效。引擎层（差分锁定）负责宏/正则/reasoning 前处理；内核 render.js 负责 fixMarkdown 之后到 DOMPurify 为止的显示管线；壳层（EmberDS）负责聊天以外的全部界面。

### 4.2 内核资产（app/src/main/assets/kernel/）
- kernel.html：CSP 全放行（用户决策）；官方 webfonts/fontawesome/style.css/mobile-styles.css/toggle-dependent.css/**popup.css**（lightbox 走官方 .popup dialog 体系）；`<style id="custom-style">` 注入点；body.light-theme + 官方整页壳骨架 **#sheld > #chat + #form_sheld**（C3 输入区，同构 index.html L8069-8113，文案中文化）+ **#bg1**（C4 官方背景层）；app-host-actions-style 仅藏 reasoning 编辑按钮等宿主未接管项（官方消息按钮/swipe/删除框全部保留，交互经点击桥回宿主）；**官方媒体模板四件原样**（#message_image_template/#message_video_template/#message_gallery_controls/#message_audio_template，index.html L7670-7727）
- js/render.js（约 1780 行）：官方管线移植——fixMarkdown、encodeStyleTags/decodeStyleTags（css-tools）、引号包裹 6 种、\ufffe 标签内引号保护、DOMPurify 三 hooks（class 加 custom- 前缀豁免 fa-*、target=_blank+noopener、未知元素换行转 br）；滑动语义对齐 refreshSwipeButtons/isMessageSwipeable/getOverswipeBehavior（isOverswipeable=`(isLastSwipe && regenerate) || edit_generate` 官方 &&优先于|| L9232-9235；swipes_visible=hasSwipes||pristineGreeting；extra.swipeable===false 严格闸门 L9152）、画廊 onImageSwiped 回绕（chats.js L2061-2102）、lightbox expandMessageMedia 三层结构 img_enlarged_container>holder>img（chats.js L900-967）、行内编辑 messageEdit（L8157-8250 curEditTextarea 填 trimSpaces(rawMes||mes)、save/cancel/copy/move/reasoning 经 uiAction 桥）、formatGenerationTimer（L2681-2706）、setInContextMessages 单标记 lastInContext（L6022-6041）、show more 分批 prepend（printMessages L12495/showMoreMessages L12517）、appendImage/Video/AudioAttachment 媒体挂载（L2196-2412 模板克隆+error 类+AudioPlayer 实例化）、extraMesButtonsHint 展开/点外收起（script.js L11806/L11835）、头像失败兜底 missing-avatar（L2646-2650）
- js/audio-player.js：官方 scripts/audio-player.js 605 行逐字节移植（import formatTime 内联自 utils.js L975-983；`export class`→普通 class + window.AudioPlayer）。注意官方 file-form.css 的播放器样式**并未被 index.html 引用**——官方消息内音频就是无专属样式 + AudioPlayer 行为，照抄不补
- 同版本库：showdown 2.1.0 / dompurify 3.4.2 / highlight 11.11.1 / css-tools 4.4.4
- official/message-template.html：官方模板原样；**克隆内部 .mes 而非根节点**（template_element，对齐 script.js L447）

### 4.3 window.Kernel API 与桥协议
| API | 说明 |
|---|---|
| formatText(mes, opts) | 返回 HTML 字符串（DOM 黄金对比入口） |
| renderMessage(payload) | 生产入口；payload={mesid,mes,chName,isUser,isSystem,avatarUrl,timestamp,tokenCount,reasoning,swipeCount,currentSwipe,lastMessage,smallSysMes,type,bookmarkLink,forceAvatar,title,toolCall,media,timerValue,apiModelTitle…}。全 DOM 行：整条官方 .mes 模板由内核渲染 |
| renderChat(payloads, showMore=false) | 整页壳 C1/C2：清空重建全量同步官方 #chat，payload 顺序即聊天顺序；showMore=true 且首条未到顶时挂 div#show_more_messages（"Show more messages"，官方 printMessages L12495/showMoreMessages L12517） |
| scrollToBottom(smooth) | 官方 scrollChatToBottom 接管（C1） |
| setDeleteMode(enabled) / selectDeleteFrom(mesid) | 官方 openMessageDelete 的 DOM 状态部分 + 删除模式点击截断选择 |
| prependMessages(payloads) | 官方 showMoreMessages 增量插入：button.after(frag)+视口相关 scrollTop 补偿（宿主点 Show more 时调，避免全量重渲） |
| beginEditMessage(mesid) | 边界3 行内编辑模式：.editing 类 + curEditTextarea 填 trimSpaces(rawMes||mes)；保存/取消/复制/移动/删除/思考编辑经 uiAction mes_edit_* 桥回宿主 |
| setInputText(text) | C3：宿主 → #send_textarea（草稿下发/冒充流式/发送后清空），派发合成 input 事件回镜像 |
| setInputState({generating,swiping}) | C3：body[data-generating]/[data-swiping] + mes_stop 显隐 + hideAllSwipeButtons 合成 |
| setBackground(url, fitting) | C4：#bg1 background-image + cover/contain/stretch/center 类；url=null 清除 |
| updateStreaming(mesid,text,reasoning?,timerValue?,timerTitle?) | C5+边界4：官方 onProgressStreaming 原地换 .mes_text innerHTML（+ .mes_reasoning），不重建其余节点；timerValue/timerTitle 直写 .mes_timer（formatGenerationTimer 形态） |
| applyTheme(theme) | 官方主题全字段 → CSS 变量 + custom_css + body 类开关（compact_input_area → #send_form.compact，power-user.js L529-532 官方语义） |
| applyStylePack(cfg) | 第三方样式包整包：{enabled,href,extensionHref,vars}——style.css 走 `<link id="style-pack-style">`、extension.css 兼容层独立 link、vars 逐键写入 documentElement CSS 变量（缺 `--` 自动补）；href 变更复用节点；enabled=false 零污染 |
| clear() | 清空 #chat |

applyTheme 的开关字段→body 类与 power-user.js applyPowerUserSettings 逐项同构：no-blur/noShadows/waifuMode/reduced-motion/no-timestamps/no-timer/no-tokenCount/no-mesIDDisplay/no-modelIcons/no-hotswap/hideChatAvatars/expandMessageActions/swipeAllMessages/enableZenSliders/enableLabMode/big-avatars/square-avatars/rounded-avatars/bubblechat/documentstyle/flatchat/echostyle/whisperstyle/hushstyle/tidestyle/ripplestyle；每次应用先移除后添加（全量同步语义）。avatar_style 枚举对齐 power-user.js L95-100：ROUND=0/RECTANGULAR=1(big-avatars)/SQUARE=2(square-avatars)/ROUNDED=3(rounded-avatars)。chat_display 全枚举：0 flatchat/1 bubblechat/2 documentstyle/3 echostyle/4 whisperstyle/5 hushstyle/6 ripplestyle/7 tidestyle（3-7 经 Moonlit 上游扩展 index.js initChatDisplaySwitcher 核实）；≥8 安全落空不加类。
长按手势：500ms 阈值、touchmove 超 10px 取消，bridgeSend {type:'longPress'}。
桥事件：kernelReady/height/heightChanged/click/longPress/chatScroll/hostRequest/inputChanged/inputHeight → window.AndroidKernel.postMessage(JSON)。hostRequest 承载输入区 8 控件动作（chat_send/chat_interrupt/chat_options/chat_attach/chat_impersonate/chat_continue/chat_delete_confirm/chat_delete_cancel）。宿主能力白名单（openLink/copy/share/toast/saveMedia/saveDataUrl/vibrate）经 hostAction 回传 ChatScreen.handleHostAction。

### 4.4 Kotlin 侧（app/.../renderer/）
- KernelModels.kt：载荷与事件模型（tokenCount 为官方 tokenCounterDisplay 形态原样 String 透传）；ChatDisplayMode 枚举与内核布局 body 类一一对应；StTheme 仅作类型化视图，**主题必须以原始 JSON 字符串透传**（RenderKernel.applyThemeRaw），防有损转换丢字段
- KernelBridge.kt：window.AndroidKernel；能力面=高度/点击/长按/shimRequest/hostAction 回传；无任意 Android API/文件系统通道
- KernelWebViewFactory.kt：WebViewAssetLoader 统一 https origin appassets.androidplatform.net——assets/=内核页、/avatars/=角色头像（filesDir/avatars）、/pavatars/=人设头像（filesDir/persona-avatars）、/media/=消息附件（filesDir/media）、/backgrounds/=官方背景（C4，filesDir/backgrounds）、/themefiles/=导入主题 CSS（filesDir/themes）；JS/DOM storage 开、媒体自动播放、textZoom 固定 100、外链交系统浏览器
- KernelWebViewPool.kt：预热 2、软上限 8、kernelReady 挂起等待（主线程轮询避免跨线程续体竞争）。池持有页面五态（themeJson/bodyClasses/stylePack/inputState/background），新建实例 ready 即套用，updateTheme/updateStylePack/updateInputState/updateBackground 广播全部存活实例；层叠顺序=applyThemeRaw→setBodyClasses→applyStylePack，与官方「power-user 设置→扩展主题 CSS」一致。body 类默认含 fullchat（整页壳：#sheld fixed 全屏、#chat 唯一滚动容器）+ app-host-actions（仅藏 reasoning 编辑按钮等宿主未接管项）；监听器面=height/click/messageAction/longPress/uiAction/chatScroll/crash/input/inputHeight
- RenderKernel.kt：门面——renderMessage/renderChat(showMore)/prependMessages/beginEditMessage/scrollToBottom/setDeleteMode/selectDeleteFrom/pushInputText/setInputState/setBackground/updateStreaming(text,reasoning,timerValue,timerTitle，官方 onProgressStreaming 原地换 .mes_text)/applyThemeRaw/setBodyClasses(全量同步)/applyStylePack/emitEvent(官方 event_types 下发)/setChatDisplayMode/clear

### 4.5 官方主题导入与管理（data/OfficialThemeManager.kt）
- 内置：assets/themes/moonlit-echoes/（Glimmer/MoonlitEchoes 两套官方格式 + *-preset.json 扩展预设 + style.css 101KB + extension.css + AGPL-3.0 LICENSE）；默认主题=Glimmer
- 导入：任意官方格式主题 JSON 无损保存（filesDir/themes/）；导出/删除/切换
- currentThemeJson：原始 JSON StateFlow 直接喂内核；shellSettings() 派生壳层设置（chatDisplay/avatarStyle/compactInputArea/chatTruncation=chat_truncation coerceIn(0,1000) 等 22 项供原生 UI 读）
- currentStylePack：StylePack(enabled/href/extensionHref/varsJson) StateFlow。detectStylePack() 探测主题目录的 style.css/extension.css 与 *-preset.json 的 settings 对象（逐键转 CSS 变量）；内置主题 href=/assets/themes/<dir>/…，导入包 href=/themefiles/…。零按名特判，任何含同构文件的目录同样生效
- 官方 36 字段处置表见 docs/DESIGN_SYSTEM.md §二点六

### 4.6 Moonlit Echoes 兼容（首要审美标杆）
其消息风格=body 类+官方 DOM 选择器 CSS（源码级核实）；内核 DOM 同构→style.css/extension.css 经 applyStylePack 整包装载（extension.css 含 .mes/#sheld 等聊天区选择器，故一并加载）。通用承诺：任何含 style.css(+extension.css)+*-preset.json 的官方/社区主题包走同一探测/装载路径。

### 4.7 黄金测试（scripts/kernel-golden/，本地 jsdom 25+155+74+55=309 例全绿 + CI puppeteer-dom）
- kernel-format.test.mjs 25 例：markdown/引号包裹/DOMPurify hooks/style 前缀化/表格/官方扩展/name2/fixMarkdown/LaTeX 链
- theme-moonlit.test.mjs 155 例：全字段落位、换主题全量同步、chat_display 0..7 全枚举+未知值安全、样式包 link/扩展层/变量/href 复用/禁用零污染、avatar_style 0..3、enableLabMode、Moonlit 选择器与内核 DOM 同构命中、compact_input_area→#send_form.compact 官方语义、setInputState/setBackground、头像失败兜底 missing-avatar、滑动 swipes_visible/last_swipe/ZWSP 计数+overswipe 三态（regenerate/edit_generate/pristine_greeting）+swipeable=false 闸门、按钮排展开/点外收起/expandMessageActions 常显豁免、**媒体三模板挂载/gallery 计数与回绕桥/lightbox 三层结构与放大链/行内编辑 save·ESC·按钮显隐/show more 挂载与 prepend 桥/lastInContext 单标记/formatGenerationTimer 文本与流式 tick**（九边界 ~48 例）
- shim-api.test.mjs 74 例 / variables-shim.test.mjs 55 例：st-api-shim 协议与 TavernHelper 变量族
- puppeteer-dom.test.mjs：CI kernel-golden job `test:dom` 步骤（headless Chromium 对 golden 逐字对比）
- 运行：cd scripts/kernel-golden && npm install && npm test

### 4.8 九项渲染边界对齐与宿主接线（commit 812b094c/8afe8d8b）
- 载荷新字段（KernelModels.kt KernelMessagePayload）：rawMes/reasoningRaw/mediaIndex/inlineImage/overswipe/swipeable/lastInContext/timerValue/timerTitle/extraTitle
- 宿主单一出口（ChatScreen.kt）：messagePayloadOf(index,el) 统一构造载荷（index<chatFromIndex 返 null）；chatFromIndex 用 remember(truncation){derivedStateOf}（chat_truncation+historyWindowExtra 窗口）、licTarget 用 derivedStateOf——**必须用 derivedStateOf 而非普通 val**：DisposableEffect(kernelPool) 里的监听器捕获的是初组合实例，普通 val 会冻结旧值（SHOW_MORE 批次被滤空的教训）；licTarget 对齐 setInContextMessages：单标记 + toolCall 豁免（Array.isArray(tool_invocations)，空数组也算）+ 回落首条可见消息
- ChatViewModel：markTainted（editMessage/deleteMessage/startStream，官方六污染点子集 script.js:1659/4288/5846/8131/9323/11669）；overswipeOf 严格 `swipeable === false`（字符串 "false" 不触发，L9152）；liveStreamTimer 对齐 formatGenerationTimer（%.1fs US locale、title 五行英文、负差值空 value 保 title）
- 行内编辑接线：原生编辑对话框退役——菜单「编辑」与 onTextClick(click_to_edit) 一律 kernelPool.acquireSingle{beginEditMessage("m-$index")}；uiAction 分支 mes_edit_save/delete/move/copy、mes_reasoning_add/save、mes_img_swipe(refresh=false)、mes_media_delete 全部落 vm
- Show more 接线：uiAction show_more_messages → 按 truncation 切批 messagePayloadOf → prependMessages(batch) + vm.extendHistoryWindow(batch 大小)

## 5. App/UI 进度（V2 重构中 + V3 壳层重构阶段 1-16 已落）

### 5.1 已完成（新架构）
- renderer/ 五类（§4.4）+ OfficialThemeManager（§4.5）
- **消息渲染单轨化（内核为唯一管线，全 DOM 行）**：每条消息整条官方 .mes 模板进内核渲染（头像/名字/时间/tokenCount/正文全部官方 DOM）；原生只承担交互面——reasoning 卡、操作条（swipe 箭头/计数器/⋯/flag/pencil）、媒体、手势。无任何回退开关。要点：
  - 用户消息与 AI 一视同仁进内核（对齐官方 messageFormatting 全量语义）；impersonation 保持原生
  - 流式行走内核：StreamingThrottler 120ms 节流 updateStreamingText 轻量更新 .mes_text；流结束 payload 变化触发 renderMessage 权威重渲。mesid 连续性："m-${items.lastIndex}" == "m-${item.index}" 同槽位跨过渡复用不闪换
  - 主题随页：池持有 themeJson/bodyClasses/stylePack 三态（§4.4），新建实例 ready 即套用；ChatScreen 收集 OfficialThemeManager 的 currentThemeJson 与 currentStylePack 双流驱动广播
  - 文本前处理分工：引擎 MessageFormattingEngine 管正则/宏/bias/name2 等（kernelDisplayTextOf 跳过 fixMarkdown/encode_tags，由内核 render.js 接管后半段，独立 kernelDisplayCache 防缓存串写）
  - 高度契约：内核 ResizeObserver 回报 CSS px（≈dp），MessageKernelRow 按 mesid 过滤监听撑高，未回报前 64dp 兜底；挂载竞态用 disposed 哨兵判定（**禁止用 slot.parent 判定**——AndroidView factory 阶段 parent 恒为 null，会误判首挂载为已销毁而归还池 → 正文空白）；池 release 端摘除旧父容器
  - 长按路由：内核 touch 桥 → pool.longPressListeners → 原生 ActionSheet
- ui/emberds/：EmberTokens（Glimmer DNA：近黑中性底/亮度阶梯表面/四档墨阶/引号蓝 #51A0DE 强调/AI 暖金身份/极细描边/小圆角/克制模糊）+ InkText/SurfaceCard/GlassBar/AiBubble/UserBubble；业务组件禁直接引用 MaterialTheme.colorScheme（lint 门禁待接）
- ui/chat/surface/MessageKernelRow.kt：消息内核宿主（槽位式挂载 + 高度感知 + 归还池）+ StreamingThrottler（120ms 节流，流式在用）
- **P4 扩展桥已完成（21a9888）**：assets/kernel/js/st-api-shim.js = 官方 EventEmitter 1:1 移植（7 原型方法）+ 全量 event_types + SillyTavern.getContext()/triggerSlash/executeSlashCommands/substituteParams（同步本地 {{user}}/{{char}} 回退 + macro.substitute 桥全量宏）；桥协议 shimRequest{reqId,method,params} → StApiShimInstaller 分发 VM 差分锁定资产：ctx.snapshot / metadata.get / metadata.set（即时落盘+bump displayRevision）/ slash.run→AppSlashExecutor / macro.substitute→MacroEngine；generate 族显式拒绝并登记边界；回传走 URLEncoder + window.__shimRespond 免转义陷阱。金测试 shim-api.test.mjs 18 例并入 npm test。扩展兼容边界登记见 §6.3
- **壳层换装=官方主题字段直供（ShellTheme.derive）**：官方主题 JSON 字段单向纯函数推导出整套令牌——blur_tint_color→bg、bot/user_mes_blur_tint→surface/surface2、shadow_color→surfaceSink、main_text_color→墨阶、italics_text_color→inkMute、quote_text_color→accent 三态、border_color→line；AI 身份金与语义三色是壳层品牌常量。无主题 JSON 时回落 Moonlit 推导常量。暗色基线由推导 bg 亮度判定，mapToM3Scheme 映射进 M3 保证存量组件协调
- **结构推倒已完成（25d1109c，CI 绿后继续修编）**：MainScreen 三域底部导航（聊天/世界/设置，玻璃底栏胶囊指示）+ 平板 ≥840dp 双栏 NavigationRail；首页/书架/世界/设置/外观五大屏按 DESIGN_SYSTEM §六 IA 全部重排；onboarding 重做
- **ui/design 令牌层（新架构核心）**：
  - EmberTokens.kt：EmberColors（bg/bgTint/surface/surface2/surfaceSink 五阶底面 + ink 四档墨阶 + line/lineStrong + accent 三态 + ai 身份三态 + success/warning/danger）/ EmberShapes / EmberSpacing / EmberMotion（弹簧底座 damping 0.6 / stiffness 500，reducedMotion 全降 80ms）/ ChatAreaTheme（10 个可空 Color? 字段 + floatingInput，null=回落令牌）
  - EmberTheme.kt：全部访问器是 @Composable getter（CompositionLocal）——**禁止在 remember/LaunchedEffect lambda 里直接读，必须先在组合上下文读出局部变量**（CI 两轮红的根因）
  - 壳层无独立皮肤体系：EmberSkins/SkinStore/SkinImageAssets/SkinBackgroundLayer 与 OfficialThemeManager.skinColors() 桥已删，换装唯一来源=ShellTheme.derive（上条）；AppearancePrefs.radius 四档经 shapesForRadius 进形状令牌
  - 组件库 components/：InkText(墨阶排版)/SurfaceCard/GlassBar/Bubbles/Buttons/Chips/EmptyState/Overlays/Motion（rememberEmberSpring/Light、breathingGlow 1.6s 呼吸、EnterFadeSlide 入场）
- **P5 删旧码已执行部分**：RenderNodeCompose.kt（615 行 RenderNode 原生 HTML 渲染生态）整删；isStaticHtml 双轨分流删（WebHtml/Interactive 段统一 WebView 路线 B，htmlFenceInner 死码同删）；旧主题体系 24 套 ThemePreset/BackdropSpec/ArtBackdrop/VibePreset 随 25d1109c 退役
- **思考块迁内核（官方 .mes_reasoning DOM）**：payload 增 reasoning 字段；render.js 按官方 reasoning.js updateDom 语义填充（.mes reasoning 类 + data-reasoning-state/details data-state=done + 内容 formatText）；kernel.html 解除 .mes_reasoning_details 隐藏（details/summary 原生折叠，主题 CSS 接管样式），死按钮容器 .mes_reasoning_actions 仍隐藏；全 DOM 行撤原生 ReasoningCard。金测试 +8 例（类/状态/内容/折叠）
- **本地外观偏好收敛（官方字段全删）**：AppearancePrefs 删除 st_ 九色（main/em/underline/quote/user·bot bubble/border/shadow/blur_tint）、排版 11 字段（textSize/lineHeight/headingStyle/bodyWeight/headingH1/H2/quoteItalic/codeSize/inlineCodeSize/blockSpacing/listIndent）、avatarShape、文字阴影开关与强度——全部由官方主题接管。消费方改读真值：气泡/流式着色取 ShellTheme 令牌，正文样式 chatTextStyle() 单一 font_scale 缩放，头像形状读 shellSettings().avatarStyle（0圆/1大矩形/2方2px/3圆角10px）。保留项均为壳层自有或官方 power_user 行为旗标（radius/font/immersiveActions/bubbleStyle/density/backgroundBlur/openLastChat/encodeTags/fixMarkdown/chatBg*/blurStrength）。MessageRenderScreen 颜色编辑页删除，只留行为与兼容

### 5.2 待办（当前优先级）

**V3 壳层重构（Premium Editorial × AI Companion）剩余未完项**（阶段 1-17 已全部落地，
总案见 [docs/UI_REDESIGN_V3.md](UI_REDESIGN_V3.md)；编译绿 + App 单测 53/53 绿）：
1. **第 16 阶段 Polish ✅ 全部完成**：Typography 审计=12 档类型比例表
   （EmberTokens EmberType/EmberTypography，fontSize 全仓收编，无离表字面量；
   InkText 的 sizeSp 为组件原语参数属合理保留）；Reduced Motion 跟随系统=
   MainActivity 读 ANIMATOR_DURATION_SCALE==0 并入 reducedMotion 三根判定线；
   动效一致性=EmberMotion 四档时长令牌（pageMs 页面转场 / sheetMs 弹层 /
   controlMs 微交互 / reducedMs 减动画），DestContent 目的地切换 AnimatedContent
   fade+上滑走 pageMs（减动画降级纯 fade），ShellKit 开关滑块/FloatHub 展开、
   Editorial AccordionGroup、Onboarding 分段入场全部归档；保留值均有据：
   toastr 250ms=官方对齐、呼吸光 1600ms=规格、主题 lerp 400/80ms=V3 规格。
2. **第 17 阶段回归 ✅ 机检全过，剩真机人工项**：内核金测试 jsdom 四套件
   25+155+74+55=309 例全绿（2026-08-25 本机）；CI compileDebugKotlin +
   assembleDebug + 单测随本次 push 验证。真机人工验收（用户执行）：三空间
   视觉特性成立、宽屏双栏/中屏限宽/角色库 List-Detail、设置深链、全域搜索、
   导入导出回归。
3. V3 已落要点（速查）：三空间壳（FloatHub 四目的地）；书架排序 11 档/标签筛选/网格⇄列表（char_list_grid）；群聊生成队列条（show_group_chat_queue）；角色主页 CharacterHomeScreen + 编辑器分离；聊天 Context 胶囊；设置 IA 官方 8 分区 + 全域搜索目录；BehaviorPrefs 35 字段全链路核销（见 V3 文档 §2.2.2 表）；自适应三档（<600 单栏 / 600-840 限宽 600dp / ≥840 导航轨 + List-Detail，windowWidthClass() 自实现）；壳层个性化三键（shell_density/motion_level/home_style）；主题切换 lerp 400ms（EmberColors.lerpTo）。

**V2 渲染链既有待办（仍有效）**：
1. **聊天页整页壳（用户已拍板的方向终点）**：聊天屏整体交一个 WebView 承载官方 #top-bar/#sheld/#chat/#form_sheld 全套层级——主题包对顶栏/输入栏/背景的规则直接生效，接缝类 bug（薄空隙/裁剪/度量漂移）连根消失；池化与高度契约机器退役（单实例）。总纲（用户指令）：**官方渲染一律直拷官方源文件，内核 JS 只做薄胶水；桥接以稳定+兼容为最高准则；架构留扩展缝。**
   分期：
   - ✅ C1 已落：render.js 拆 mountMessage（模板就绪后同步挂载）+ renderChat 全量同步（清空重建保序，幂等）+ scrollToBottom + watchChatScroll 节流回报 {type:'chatScroll',atBottom}（距底 40px 容差）；Kernel.clear 委托共享 clearMessages；kernel.html 增 body.fullchat 模式块（恢复官方 #sheld fixed/#chat overflow-y:auto 滚动语义，声明序压过嵌入态覆盖）；桥协议 KernelEvent.atBottom + CHAT_SCROLL，池侧 chatScrollListeners 扇出。金测试 239 绿
   - C2 ChatScreen 单 AndroidView 化（LazyColumn/pool/高度契约退役，长按+swipe 手势桥；fullchat 类切换；跳底浮标吃 chatScroll 事件）
   - C3 #form_sheld 输入区进 DOM（textarea/#send_but/#mes_stop 结构直拷官方 index.html，发送/中断/附件经桥），主题包输入栏样式自动生效
   - C4 背景=官方 #bg1 语义（background+blur_strength+chat_tint_color），本机 chatBg* 背景系统删除
   - C5 流式 per-tick 进 .mes_text/.mes_reasoning（单页后 StreamingThrottler 直写）
   - C6 官方面直拷对齐（审计驱动）：官方 messageFormatting/utils、reasoning.js、power-user applyTheme 等能整文件拷的不再手写移植——以审计清单为准逐项替换并跑金测试
   原生保留：ActionSheet/对话框浮层、导航壳、设置/世界书各屏（Compose+ShellTheme 推导）。
   嵌入态覆盖（app-host-shell-style）为过渡期产物，整页壳落地后删除
2. **主题整包导入通道**：zip/目录导入含 style.css/extension.css/*-preset.json 的主题包（安全校验：压缩比/路径穿越/重复表项/条目上限），落 filesDir/themes/ 即被 detectStylePack 探测生效
3. **渲染边界欠账**：mes_ghost eye-slash 指示未随内核行携带（payload 缺口）；背景图进内核页（原生 backdrop-filter 才能对 Compose 内容取样真玻璃）；highlight.js 语言包经 AppBridge 按需装载
4. **扩展桥验收欠账**：2 张 MVU 卡 + 2 个酒馆助手脚本免改真机运行。event_types 两期接线已落（v1 生成生命周期 + v2 消息级七事件，注释标官方 script.js 行号，金测试断言参数形态）——待真卡验收

### 5.3 旧渲染链清理（已完成）
RenderNodeCompose(615 行)、isStaticHtml 双轨、24 套旧 ThemePreset 体系、mikepenz 全家（NativeMarkdown/SegmentedMarkdown/OfficialMarkdownNode/官方 HTML 转译与 WebView 嵌页链）、MarkdownCache、旧 ui.chat.WebViewPool、TextTypographyScreen、双轨开关 RenderPrefs.kernelRender/userKernelRender 全部删除；gradle markdown-renderer 四件套移除。消息渲染唯一管线=渲染内核（§4）。

### 5.4 App 接线总表（官方行为怎么接，引擎能力部分仍有效）
> 原则：App 只做“调用引擎 + 渲染结果”，不再重写逻辑；每项注明官方源码位置。

| 引擎能力 | 官方源码位置 | App 接线点 |
|---|---|---|
| 流式渲染 | sse-stream.js + openai.js eventSource | LlmClient.streamChatCompletions → SseChunkParser → ViewModel 增量状态 → 逐 token 追加；停止=取消 OkHttp call；流结束必须走 onDone |
| 提示词组装 | openai.js prepareOpenAIMessages + populateChatCompletion + script.js generate | PromptPipeline.prepare 一个入口出最终消息；App 发送前调它 + 按协议走 ChatRequestBuilder/Anthropic/Google |
| 消息转换 | src/prompt-converters.js | Claude/Gemini 在各自 builder；Mistral/xAI/Cohere/AI21 在 LlmClient 协议分支；OpenRouter 在 openai-compatible 先签名/媒体再序列化 |
| 工具/能力选项 | chat-completions.js + openai.js oai_settings | ProviderRequestOptions 承载 tools/tool_choice/json_schema/web_search/request_images/safety；LlmClient 按厂商官方形态写入请求体 |
| 预算计算 | chat-completions.js calculateClaudeBudgetTokens/calculateGoogleBudgetTokens | LlmClient 按模型/effort 调两个预算函数，结果进 builder reasoningBudget（adaptive→effort/auto→不加/数字→budget_tokens/thinkingBudget） |
| Markdown 渲染 | Showdown + highlight.js + DOMPurify | ✅ 内核 render.js 原版管线（同版本 showdown/dompurify/highlight），见 §4.2；引擎 MessageFormattingEngine 差分 805 例为前处理权威 |
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



## 6. 完成度与边界登记

### 6.0 完成度总览

- 引擎测试 **全绿**（engine 全量 testcase + imagegen-services 57 + tts-services 35 + tts-local 38 + quickreply 16 + caption 17 + translate 19 + gallery-assets 5 + imagegen-prompt 59 → fixture 内 for-loop 累计）；App `:app:compileDebugKotlin` 全过；App 单测 `GalleryAssetsDiffTest` 5 例全绿；差分分组表 104 组，明细见 [docs/DIFF_MATRIX.md](DIFF_MATRIX.md)。
- 完成项对齐（“写了未差分”全部补齐）：
  - ①QuickReply v2 纯函数：migrateSetV1ToV2 9 字段迁移 + visibleSetNames + shouldAutoExecute → quickreply-official.mjs 16 例 + `QuickReplyDiffTest` 全绿；App `QuickReplyStore` 接入 V2 Settings + 多预设 v2 set 读写 + 旧版预设迁移。
  - ②Caption：PROMPT_DEFAULT 链 + wrapCaptionTemplate(含 {{User}}/{{Char}} 宏 + poka-yoke 自动补 {{caption}}) + multimodalRequest(无 system) + isVideo 视频扩展名拦截 → caption-official.mjs 17 例 + `CaptionDiffTest` 全绿；App `ChatViewModel` startCaptionFlow prompt_ask(!external) 触发 + 视频拦截 + captionImageAndDraft/captionExistingMessage 两处入口用 `CaptionEngine`；`ChatRepository.captionImage` 移除硬塞 system 消息。
  - ③Translate：substituteParams(name2Override=message.name→{{char}}/{{Char}} 替换) + 译文写 extra.display_text / reasoning_display_text + 8 provider body（libre/google/lingva/deepl/deeplx/onering）→ translate-official.mjs 19 例 + `TranslateDiffTest` 全绿；App `TranslateClient` 8 家 provider 全部改走引擎 body/URL 构造；ChatViewModel.translateIncoming/Outgoing 传 message.name / user.name 作为 nameOverride。
  - ④Attachments 斜杠补 5 子命令：/db show / hide / apply / list-inline / parse-inline（AppSlashExecutor）；dbDispatch 接收 `attachmentsContext()`(characterAvatar, chatFile, charName)，三源（global/character/chat）目录隔离。
  - ⑤Gallery 4 排序字面值（nameAsc/nameDesc/dateDesc/dateAsc）+ Assets 5 类型集（extension/character/ambient/bgm/blip）→ gallery-assets-official.mjs 5 例 + App 侧 `GalleryAssetsDiffTest` 全绿。
  - ⑥图像生成 prompt 纯逻辑：generationMode/modeLabels/triggerWords/messageTrigger/13 模板逐字/stringFormat/getGenerationType/getQuietPrompt/parseInteractiveTrigger/processReply → imagegen-prompt-official.mjs 59 例 + `ImageGenPromptDiffTest` 全绿（见 3.12）。
- 剩余未做：未声明 closureArgs 的闭包仍即时求值（SlashEngine，见 3.4）；斜杠命令按用户决策裁剪（仅高价值命令，见 3.4）；发送链路登记项（见 6.2）；自定义预设“设为默认”（官方无此概念）。
- 已实现但登记不差分（不再属“未做”）：TTS 3 本地后端 kokoro/kokoro-worker/openai-compatible（不同源见 4.4/6.1，App 层 HTTP 接线已实现）；图像生成 interactivity 的 LLM 提示词生成复用记忆扩展 quiet 管线、refine 弹窗/multimodal 头像描述为 App 接线（纯逻辑均已差分）。
- Prompt Itemization 分节明细面板已做（聊天消息菜单；布局对齐官方 itemizationText.html；官方 itemized-prompts.js 语义：ItemizationStore 按会话持久化 rawPrompt + TokenHandler 八分桶 + 分节消息；五分类百分比图（Character Definitions=总 token−世界书−聊天历史−扩展−bias；World Info；Chat History；Extensions；{{}} Bias）+ 总 Token/Max Context/Padding/Actual Max Context；diff 词级 LCS，超大输入回退行级）。
- Prompt Manager 面板已做（设置→提示词管理器：identifier 自动 uuid 只读/name/role/injection_trigger 六选多选/position 0=Relative 1=In-chat/depth/order/forbid_overrides/content（marker 只读）/main·nsfw·jailbreak·enhanceDefinitions 官方 Reset/新提示项 system_prompt=false/删除二次确认/编辑底部弹层/长按拖动排序/官方 Append 下拉/“查”检查弹窗（PromptAssemblyCache 最近一次总装，官方 PromptManager.messages/handleInspect））+ dryRun 提示词预览（聊天会话菜单，全文+token）。
- 用户决策延期：Custom CSS + Moving UI（6.4）；Claude/Gemini 官方 web tokenizer。
- 官方发版流程：`node scripts/diff/*.mjs` + `node scripts/build-presets.mjs` → `./gradlew :engine:test` → 按 0.2 更新基线。


### 6.1 与官方差异表（引擎层，仍有效）

| 功能 | 与官方的差异 | 状态 |
|---|---|---|
| 斜杠执行链 | 声明 closureArgs 的闭包参数（/inject filter）已原文保留+生成时求值（对齐官方 ARGUMENT_TYPE.CLOSURE）；未声明闭包（/if then/else）仍预解析立即执行（非核心路径，非阻塞）；命令数少于官方=用户决策裁剪（仅高价值命令，UI 可点击操作不移植）；官方无 /while；/parser-flag REPLACE_GETVAR 官方为 no-op（已对齐）；扩展家族 20+ 条命令（/db 13 条 /listGallery /installAsset /deleteAsset /vectorize /index /vectorize-faiss /imagine /caption /qr /expression /world* /member）回调全接真实 Service/动作（非桩） | ✅ 核心 1:1 / 非闭包惰性登记 |
| 斜杠参数解析核心 | parseCommand/parseNamedArgument/parseUnnamedArgument/testSymbol 机器差分 18+27 例 1:1；执行链依赖 DOM/闭包无法逐字提取 | ✅ 差分 |
| 正则（该卡） | 存储/字段/位点同官方；存前/总装 isPrompt/编辑 isEdit/允许列表/全局开关全接；差异：①落盘文本宏未替换（**已修**：ChatViewModel 1885 行 sendMessageAsUser 先 regex USER_INPUT 再 substituteParams，与官方 script.js 5815 一致；AI saveReply 官方本身不落盘前宏替换，宏只在请求前总装时再替换一次，与官方两次替换语义等价）；②preset 脚本命名预设集（结构等价官方 preset 扩展字段） | ✅ 全链路对齐 |
| 人设搜索 | 官方 FilterHelper 用 Fuse.js 模糊搜索（name 权重 20 + description 权重 3 相关度排序）；App 为名称/描述子串过滤，无相关度排序 | 🟡 UI 近似（功能等价：结果全集只少了相关度排序权重） |
| 人设同步 force_avatar | 官方写 getThumbnailUrl('persona', user_avatar) 缩略图 URL；App 写本地头像路径（导出 jsonl 官方无法解析） | 🟡 App 边界（功能可用：仅跨平台导入导出互读，本地导入导出自洽） |
| 人设备份头像 | 官方备份只含 avatar key（缺失时上传默认头像）；App 同 key，恢复时本地无文件回退默认头像 | 🟡 等价边界（已对齐 key 语义） |
| /preset fuzzy | 官方精确匹配后回退 Fuse.js；App exact + Fuse.js 7.1 移植（27 例差分） | ✅ |
| 预设导入 | 官方 openai 采样预设导入不校验字段（敏感字段 11 项剥离 + 同名覆盖已接）；textgen preset 进 textgenerationwebui 管理器；App 单导入入口，textgen preset 暂存 sampler 且不应用（后端未接） | 🟡 等价边界（OpenAI 采样预设全通；textgen 预设暂存） |
| textgen 采样器应用 | 官方 setSettingByName 有 DOM checkbox/text/parseFloat 归约；引擎纯赋值，归约登记剥除（纯逻辑=同值） | ✅ 打桩登记，结果等价 |
| 变量（该卡） | 官方变量是全局/聊天 scope，没有 per-character 变量；App 存 data.extensions.emberinn_variables（README 自定义，官方导入忽略） | 🟡 README 自定义（非 1:1 偏差，属 App 扩展） |
| 世界书 UI | 官方独立 World Info 面板；App 在角色详情页自绘增删改；数据格式与官方一致（v1→v2 归一，未知字段保留） | ✅ UI 自主 / 数据 1:1 |
| 角色 system_prompt/剧情后指令 | 曾漏传（角色系统提示词不生效）→ 已修：按官方传 fields.system/jailbreak，chat_metadata 同名优先 | ✅ |
| {{bias}} 提示词 | 曾不传 → 已修：提取 {{bias:...}} 并剥离宏、generate/swipe 注入、impersonate/continue 不注入（Handlebars 嵌套近似） | ✅ |
| chatCompletionSource | 曾恒 openai → 已按 provider.protocol 传 claude | ✅ |
| 人设 personaDescription | PersonaStore + 选中即 personaInPrompt=true（官方默认关，语义一致）；{{persona}} 宏可用 | ✅ |
| 扩展提示 extensionPrompts | 引擎支持 1_memory/AN/3_vectors/4_vectors_data_bank；官方 setExtensionPrompt 约定键仅此 4 项（对照 public/script.js grep 确认），extras/webllm 非官方核心约定键（属第三方扩展）；App 1_memory/AN/3_vectors/4_vectors 全部接线 + script_injects 注入完整 | ✅ 官方核心 4 键全接 |
| 工具调用 | PromptPipeline canUseTools/toolBudget/推理签名；ToolCallParser + ToolLoopPlanner 差分；App ToolRegistry 执行/历史重构/递归重装已接 | ✅ |
| 世界书设置 | 设置→服务→世界书（深度/递归/预算/大小写/整词），改动即存并用于扫描 | ✅ |
| 模型覆盖/主题配方 | README 角色页承诺；官方无角色级字段；已实现存储+UI+聊天背景+全局管线；配方导出/分享已做 | ✅ App 扩展 |
| 向量/数据银行 | 官方 Data Bank 是浏览器附件/URL 上传；App 存 filesDir/databank/ 本地文本（UTF-8）+ URL 下载（对齐官方语义）；本地 BagOfGram 为离线兜底（无官方对应） | ✅ 核心功能（上传/分块/检索/注入）全接 / BagOfGram 离线兜底登记 |
| Prompt Manager 顺序 | 官方 1.18 global 策略固定 character_id=100000；App 已统一存/读该键，preset prompt_order 按官方格式互导 | ✅ |
| Prompt Manager 应用范围 | 官方仅 chat-completion 源（main_api==='openai'）；App textgen/novel/kobold 路径已移除 PM 注入 | ✅ |
| 模型排序 pricing/context | sortModelsBy/groupModelsByVendor 1:1 差分移植（48 例）；模型列表带元数据，五源按官方字段排序/分组 | ✅ |
| context_size_derived / chat_template_hash | 官方按 llama.cpp/koboldcpp 模型信息派生；App 对 kobold 连接已接 GET /props 全流程（hash/派生/n_ctx）；llamacpp 官方独立类型无 App 提供商条目 | ✅ Kobold 全接 / llamacpp 登记不实现 |
| 代理预设 | 官方 settings.proxies 全局列表；App 已改全局存储（旧档案侧数据自动迁移） | ✅ |
| 预设默认选中 | 官方加载设置即应用当前采样预设；App 冷启动应用一次（applySelectedSamplerPresetOnLoad） | ✅ |
| 内置预设删除 | 官方删除默认预设会从列表移除且可恢复；App 内置预设来自只读资源，删除仅切到下一个（资源不可删） | 🟡 资源只读边界（效果等价：删除后自动切换至剩余有效预设） |
| Prompt Manager overridden 标记 | 官方行内显示"来自角色卡"覆盖标记；PrepareResult/缓存已带 overriddenPrompts，行内 🪪 图标显示 | ✅ |
| 内置预设裁剪 | 用户确认：老模型专用/用处不大的内置预设从 127 裁剪到 54（build-presets.mjs trimPresets 登记，重打包保持） | ✅ 用户决策 |
| 图生 17 新后端请求体 | app/data/ImageGenBackendsCloud.kt（9 云端）+ ImageGenBackendsLlm.kt（8 LLM）已写完请求体对照官方 `stable-diffusion.js` 路由 + `index.js generateXxxImage`；**9 云端已差分**（togetherai/pollinations/chutes/stability/aimlapi/electronhub/nanogpt/bfl/xai，imagegen-services-official.mjs 39 例 + getClosestSize 4 例 + 引擎层 `ImageGenRequestEngine.{togetherAiPayload,pollinationsUrl,chutesPayload,stabilityPayload,aimlapiBody,electronhubBody,getClosestSize,nanogptBody,bflBody,xaiBody,getClosestAspectRatio}` + `ImageGenServicesDiffTest`；差分已修复 Pollinations path `URLEncoder.encode`（space=+）→`encodeURIComponent`（space=%20）不匹配）；**5 LLM 后端 + replaceComfyWorkflow 纯函数（第三批扩 18 例）**：falai-server/google-client/zai/openrouter/workersai-client + Comfy 占位符替换，imagegen-services-official.mjs 3→39→57 例 + 引擎层 `falaiServerBody`/`googleClientBody`/`zaiClientBody`/`openRouterBody`/`workersAiClientBody`/`replaceComfyWorkflow` + ImageGenServicesDiffTest 57 例全绿；App 全部 8 LLM 后端改接线（准则 2，ImageGenBackendsLlm.kt） | ✅ 9 云端 / ✅ 5 LLM + Comfy / 🟡 DrawThings macOS only 登记 |
| TTS 27 外部后端 | app/data/TtsBackend.kt 接口 + TtsBackendsCloud.kt 11 云端 + TtsBackendsLocal1/2.kt 16 本地已写完对照官方 `tts/*.js fetchTtsGeneration`；**11 云端已差分**（elevenlabs/openai/edge/azure/novel/minimax/volcengine/chutes/pollinations/google-native/google-translate，tts-services-official.mjs 35 例 + 引擎层 `TtsRequestEngine` + `TtsServicesDiffTest`；差分修复 ElevenLabs `shouldInvolveExtendedSettings` 分支、OpenAI `instructions` 条件添加、Chutes `||` 短路 0→默认 1.0）；**13 本地已差分**（alltalk/chatterbox/coqui/cosyvoice/gpt-sovits-adapter/gpt-sovits-v2/gsvi/sbvits2/silerotts/speecht5/tts-webui/vits/xtts，tts-local-official.mjs 38 例 + 引擎层 `TtsRequestEngine` 扩展 + `TtsLocalDiffTest`；差分修复 URLSearchParams form 编码 space=+、JSON 数字语义整数不带小数点、chatterbox 13 字段 params 过滤、vits model_type 分支 W2V2/BERT-VITS2 条件字段）；**3 本地登记不差分**（kokoro/kokoro-worker/openai-compatible，不同源：kokoro.js/kokoro-worker.js 为浏览器 WebWorker postMessage 无 HTTP 请求体，App POST {endpoint}/tts {text,voice}；openai-compatible.js 走 ST 代理 /api/openai/custom/generate-voice body 含 provider_endpoint 字段，App 直连厂商 /v1/audio/speech body 不含）；TtsReader 改造为 MediaPlayer 播放字节 + Android 系统 TTS 回退；VoicePrefs 扩展 `tts_provider`/`tts_endpoint`/`tts_api_key`/`tts_model` | ✅ 11 云端 / ✅ 13 本地 / 🟡 3 本地登记不差分 |
| attachments / gallery / assets / quick-reply App 服务 | app/data/AttachmentsService.kt（三源 CRUD）/ GalleryService.kt（四排序+视频缩略图）/ AssetsService.kt（5 类型资产）/ QuickReplyStore.kt（多预设迁移）已写完对照官方 `extensions/{attachments,gallery,assets,quick-reply}/index.js`；/db 斜杠补齐 13 子命令 list/get/add/update/disable/enable/delete/**show/hide/apply/list-inline/parse-inline**（show=enable/hide=disable），dbDispatch 按 characterAvatar/chatFile 三源隔离；Gallery 4 排序字面值（nameAsc/nameDesc/dateDesc/dateAsc）+ Assets 5 类型集（extension/character/ambient/bgm/blip）= GalleryAssetsDiffTest 5 例全绿；Quick Reply v2 迁移/可见集/自动执行判定 QuickReplyDiffTest 16 例全绿；App QuickReplyStore settings（isEnabled/isCombined/config.setList）读写 + v2 set 文件读写 + 旧版预设迁移 | ✅ Gallery/Assets 5 例差分 / ✅ Attachments 13 子命令补齐 / ✅ QuickReply v2 16 例差分 |
| translate reasoning 自动翻译 | 已引擎化差分（TranslateDiffTest 19 例）：substituteParams(name2Override=message.name→{{char}}/{{Char}} 替换) + 写 `extra.display_text`（正文）/ `extra.reasoning_display_text`（推理）+ 8 provider body（libre/google/lingva/deepl/deeplx/onering/bing/yandex）；App TranslateClient 8 家 provider 全部改走 TranslateEngine 构造请求体/URL；ChatViewModel.translateIncoming（AI 回复译文写 extra.display_text，推理写 reasoning_display_text）/ translateOutgoing（用户消息 mes 换译文，原文进 extra.display_text）按 translateAutoMode=none/responses/inputs/both 自动触发；编辑消息 translateMessageEdit 按 auto_mode 重译或清 display_text；translateClient 全链路 nameOverride 透传（对齐 substituteParams name2Override 宏替换） | ✅ 19 例差分 / ✅ 自动模式全接 |
| caption 4 来源路由 | 已引擎化差分（CaptionDiffTest 17 例）：PROMPT_DEFAULT 链（external→settings→PROMPT_DEFAULT）+ wrapCaptionTemplate(含 {{User}}/{{Char}} 宏 + poka-yoke 自动补 {{caption}}) + multimodalRequest(无 system 消息构造) + isVideo 视频扩展名拦截（mp4/webm/mov/avi/mkv/flv/m4v）；App ChatViewModel：startCaptionFlow 触发 prompt_ask(!external && settings.prompt_ask) + 视频拦截报错 + captionImageAndDraft(生成后 refine_mode 草稿确认）/ captionExistingMessage(已有消息图片补字幕 mes 空→写 mes 否则 media.title + append_title) 两处入口用 CaptionEngine.resolvePrompt/wrapCaptionTemplate/isVideo；ChatRepository.captionImage 移除硬塞 system 消息，用 CaptionEngine.multimodalRequest 构造无 system 的纯 multimodal 请求 | ✅ 17 例差分 / ✅ App 两处入口全接 |
| 斜杠命令扩展补全 | SlashRegistry 内置 stub 注册 `/summarize`/`/db*`(13)/`/listGallery`/`/installAsset`/`/deleteAsset`/`/vectorize`/`/index`/`/vectorize-faiss`/`/imagine`/`/caption`/`/qr`/`/expression`/`/world`/`/member`；AppSlashExecutor 扩展命令覆盖同名校（callback 真接 actions/Service 非 stub）：/summarize 调 actions.summarize→MemoryService；/db* 13 条全接 AttachmentsService + attachmentsContext 三源；/listGallery GalleryService.getGalleryFolders/ItemsJson；/installAsset AssetsService.installAsset 真实下载（URL→filename→type 落盘）；/deleteAsset AssetsService.deleteAsset；/vectorize VectorRagService 向量化；/imagine ImageGenClient.generate；/caption Caption 入口；/qr QuickReplyStore.setActive 切换预设；/expression 设精灵；/world WorldStore list/export；/member 群聊成员（接口预留）。对照官方 extensions/*/*.js 注册条目完整 | ✅ 所有扩展命令回调接真实动作（非桩）

### 6.2 已确认 1:1 / 审计修复

已逐字/差分确认对齐：媒体内联能力白名单 + source 分支（24 例）；世界书 externalActivations/负深度/深度注入/EM 锚点/coreChat 过滤 is_system/ensureSwipes；斜杠解析器 43 例 + testSymbol 27 例（sendas 缺省名/sysname 空名 System/hide·message-role 语义/Comment 默认 Note/delswipe 1-based）；消息数据流（AI 落盘 swipes 结构、saveReply 尾部逐字段刷新、deleteSwipe 新 id、syncSwipeToMes、send_date=ISO、AI extra 恒有 api/model/reasoning/reasoning_duration/reasoning_signature、群聊 AI gen_id 整批共享 group_generation_id、普通用户消息 extra isSmallSys=false 无 gen_id、附件 media_index 恒写 inline_image=true）；提示词默认集合/顺序/populationInjectionPrompts/历史 preparePrompt 宏替换/AN interval 与默认 position=1/Generate 类型；正则 GLOBAL→PRESET→SCOPED + allowedOnly（7 例）。

审计修复（已修）：聊天流式卡顿——流式文本/思考状态只在流式行内订阅（每 token 不再重组合法整棵消息列表）+ 文本/思考 120ms 节流（思考卡顿主因是 ReasoningCard 每 token 全量渲染，现 8fps 上限）；show_thoughts 增加会话菜单快捷开关（官方默认 true，与官方一致，可即时关停并清空当前思考显示）；模型页按官方面板结构重组（连接/采样参数/预设联动与提示词：Logit Bias·消息角色与续写·工具与媒体·提示词模板含 main/nsfw/jailbreak 快捷编辑/连接高级/上下文与连接测试）；预设页按官方 preset-manager 重组（下拉选择+对选中项 更新/另存/重命名/删除/导出/恢复）；Prompt Manager 补 Token 列/总 Token/官方行图标（marker/global/important/user/injection/角色）；kobold 官方 GUI KoboldAI Settings 特殊预设（默认/不可更新/重命名/导出/恢复）；context story_string_position 与 instruct names_behavior 改官方下拉选项；start_reply_with/show_user_prompt_bias 移回 Advanced Formatting 位点；模型排序/分组按官方元数据差分（sortModelsBy/groupModelsByVendor/filterModelsBySource，48 例）；kobold /props 全流程（chat_template_hash sha256、context/instruct 派生自动选中、context_size_derived n_ctx 自动改上下文）；代理预设改全局存储+旧数据迁移；冷启动应用当前采样预设；Prompt Manager 全局顺序 key=100000（原 null/UUID 三键不互通）+ prompt_order 导出带 character_id；导入采样预设后即应用；删除预设二次确认+自动切换首个剩余；Unicode 预设名保存；textgen legacy 导入用文件名；bind_to_context 双向联动；auto-select 与 /preset 按活动协议+群聊名；sort_models 官方四项并限 5 源显示；request_images 组/impersonation_prompt UI；补 6 家官方提供商（electronhub/chutes/nanogpt/aimlapi/pollinations/cometapi）；reverse proxy 预设列表；删除 contextAuto/defaultMaxTokens 假“按厂商自动填”；reasoning auto_parse/add_to_prompts/auto_expand/show_hidden/max_additions 字段+UI；textgen/novel/kobold 路径移除 PM 注入；用户消息保存顺序（regex→substituteParams→removeMacros，token_count 落盘）；AI 消息补 time_to_first_token；AI_OUTPUT 正则改在 cleanUpMessage 停用词裁剪后注入；开场白数据格式（extra={}、无 title/gen_*、空首条 swipes.shift()）；continue 合并刷新 send_date/gen_started（时长守恒）/token_count；滑动变体 gen_id 仅群聊 + reasoning_duration/signature；历史索引错位（media 挂错）；bias 提取最后用户消息 + 编辑存 extra.bias 回溯；/hide 语义；comment 不进提示词；系统消息防误操作；continue swipe_info 同步；发送失败不丢输入；重生成先查配置；群聊配置实时；书签路径消毒；世界书条目删除确认；角色主题/背景实时刷新；平板导航轨；滑动返回手势；返回按钮不贴最高处；设置主页重构官方移动端 8 分区（AI 响应配置/API 连接/高级格式化/世界书/用户设置/背景/扩展/人设管理 + 数据隐私/关于）；设置默认值字段级对照官方（auto_continue.target_length=400·allow_chat_completions=false、textgen temperature_last=true·top_p=0.5·top_k=40·top_a=0、NovelAI 采样默认、Kobold 空配置回退官方 kai_settings 默认）；表情 LLM 分类（llmPrompt/parseLlmResponse 对齐官方 getLlmPrompt/parseLlmResponse + 生成后异步分类切换）；/inject filter 闭包（closureArgs 原文保留 + isTrueBoolean 生成时门控）；/genraw instruct/as（InstructMode.createRawPrompt 消费 instruct 开关/协议分支）；NovelAI 差分 default_order 修正为官方数字索引数组。

**App 接线层审计（2026-08-20，见 4.7/4.3）**：17 分区设置项逐项验证 UI 改值 → SharedPreferences → ChatPromptFactory/PromptPipeline → 引擎参数 → 实际输出差异全链路；世界书（WorldInfoPrefs.depth/budget/recursive/case/whole-word/插入策略/include_names）、正则（RegexPrefs 全局开关）、Prompt Manager（prompt_order 读 key=100000 注入）、预设采样器、人设位置等均已确认下游真实读取并参与计算，非仅存 DataStore。**合并上下文胶囊**（世界书指示灯+上下文占比合成一囊）：worldHits 来自引擎 onPrepared→wiResult.activatedWorldInfo（非 UI 模拟）；_contextUsage 来自引擎 counts/maxContextTokens（+3 reserveBudget）；命中灯常驻但命中才高亮主色。删除 StatusPill 死代码（见 4.3）。

登记边界（有意保留）：extra.api 存提供商 id（官方存 source）；bias 文本提取 vs extra.bias 双轨；/hide name 过滤；narrator/sendas bias-only is_system；SWAP/APPEND 旧版近似；openrouter/mistral 模型元数据缺失回退；远程 URL 附件；Room/DataStore、插件 API、网络代理、视觉小说、STT、翻译自动模式触发逻辑（实际 translateIncoming/translateOutgoing 已按 auto_mode 触发；登记指官方"翻译服务自动下拉列表"的 UI 交互，非核心 1:1）、记忆摘要 summarize（官方默认关/远期，/summarize 斜杠命令已接 MemoryService 但向量 RAG 路径默认关=用户决策）。


### 6.3 渲染边界（V2 内核架构下重新登记）
- 旧原生渲染路线的边界登记全部作废（该路线已由 WebView 内核取代，旧文归档于 git 历史）
- 内核已知边界：流式中间态为轻量近似（120ms 节流跳过全量消毒），流结束权威全量；jsdom 无法验证 CSS 视觉级联，Moonlit style.css 视觉效果待 CI Puppeteer/真机确认；WebView 池软上限 8，池满兜底新建不设限（内存压力由系统回收）
- **Custom CSS 结论更新（V2 后）**：
- Custom CSS 已随内核落地：官方 user.css 语义 = 内核 custom-style 注入点 + 主题 custom_css 字段，逐字支持；Moving UI 维持延期（官方 isMobile() 直接禁用）

### 6.4 扩展兼容状态登记（不独立成文，随本表维护）

> 原则：不做没测过的"100% 兼容"宣称；状态升级必须附验证方式（jsdom 金测试 / 真机跑通记录）。

**垫片能力面（st-api-shim v1）**：

| 能力 | 我方实现 | 状态 |
|---|---|---|
| eventSource（7 原型方法）/ event_types 全量事件名 | 1:1 移植进 shim；触发点位已接：chat_id_changed + first_message + 生成三事件（v1；generation_ended 对齐官方 hideStopButton 闩——每轮恰一次、用户停止 STOPPED+ENDED 双发、参数=落盘后 chat.length）+ 消息级七事件全落点（v2，参数逐点对官方 script.js 行号），Native→Web 走 RenderKernel.emitEvent → `__emitKernelEvent` → eventSource.emit | ✅ |
| SillyTavern.getContext() | 快照字段对齐官方 st-context.js 同名直引：chat（含 extra）/ chatMetadata / name1 / name2 / characterId / groupId（单聊 null，官方 selected_group 语义）/ chatId / eventTypes 别名；桥为异步故 getter 返回 Promise（await 取值）——同步直读差异登记为内核桥面边界；生成族方法显式拒绝 | ✅ 只读 |
| chat_metadata 读/写 | metadata.get/set，写即时落盘+bump displayRevision（非 debounce 语义） | ✅ |
| triggerSlash / executeSlashCommands | slash.run → AppSlashExecutor（命令集按用户决策裁剪） | ✅ |
| substituteParams | macro.substitute → MacroEngine 全量宏 | ✅ |
| generate()/generateQuietPrompt() 生成族 | **显式拒绝**——生成链路由 App 侧统一调度 | 🚫 登记边界 |
| saveSettingsDebounced | no-op（设置由 App 侧持久化） | 🟡 |
| TavernHelper 变量族（getVariables/replaceVariables/insertOrAssign/insertVariables/deleteVariable/updateVariablesWith） | **双作用域**：chat = chat_metadata.variables（metadata.get/set 桥）；global = extension_settings.variables.global 等价物（variables.get/set 桥 → GlobalVariableStore，SharedPreferences 单键 JSON）——lodash mergeWith 数组替换语义、insertVariables 多源旧值优先、`__proto__` 污染防护（CVE-2020-8203 对齐）——金测试 variables-shim.test.mjs 44 例（node:vm 行为级）；character/preset/message/script 作用域显式抛错待宿主态 | ✅ chat+global / 🚫 其余 |
| AppBridge 白名单（openLink/copyText/share/toast/saveMedia/saveDataUrl/haptic/vibrate/readClipboard） | hostRequest fire-and-forget + host.clipboard request-response；官方 toastr 全局兼容映射原生 Toast | ✅ |
| WebView 崩溃自愈 | onRenderProcessGone→池剔除+crashListeners 广播→MessageKernelRow mountEpoch 重挂；raw 恒在 Kotlin 侧零丢失 | ✅ |
| 内核严格模式 | RenderPrefs.strictMode 禁 JS 排障开关（默认关），MessageRenderScreen 可切 | ✅ |

**逐扩展**：卡内交互 HTML/Moonlit Echoes/官方 34 套主题/快捷回复/表情精灵/vectors 本地/stable-diffusion/TTS/translate/attachments ✅；MVU 与酒馆助手脚本 🟡（缺 TavernHelper globals）；ChromaDB 远程与 summarize 🔴 SERVER_REQUIRED（/summarize 斜杠已接 MemoryService）；connection-manager ⚪ 由 ProviderScreen 多档案等价替代。
**验收欠账**：2 MVU 卡+2 酒馆助手脚本免改真机运行。

## 7. 维护速记与注意事项

### 7.1 常见编译坑（CI 红→绿经验）
1. 注释里写 `group-chats/*.json` 会触发 Kotlin 嵌套注释吞文件 → 写成“目录的 *.json”。
2. 缺 import、括号不配对、前向引用属性 → push 前自查。
3. M3 1.4：Typography 无 defaultFontFamily；Modifier.padding 不能混用 horizontal+top。
4. 正则字符串里 `\s` 必须双反斜杠（非 raw string）；helper 别嵌局部函数。
5. 全局替换函数名时 `return@旧名` 标签必须同步改名。
6. Modifier 扩展用 rememberUpdatedState 必须包 `Modifier.composed`。
7. **EmberTheme 访问器全是 @Composable getter**：在 remember{}/LaunchedEffect{} lambda 里直接读会报 "@Composable invocations"——先在组合上下文读出局部变量再进 lambda（Motion.kt EnterFadeSlide 教训）。
8. ChatAreaTheme 字段全部 Color? 可空：直接当 Color 用报类型不匹配，用 `?: EmberTheme.colors.xxx` 回落令牌；EmberTextFieldDefaults.colors() 无 focused/unfocusedPlaceholderColor 参数（placeholder 色由 placeholder composable 自己给）。
9. 本机（Termux）无 gradle/Android SDK，任何编译验证只能 push 走 CI（`gh run list` / `gh run view <id> --log-failed`，网络不稳重试）；push 前用括号平衡自检 + grep 悬空引用兜底。
10. 局部函数表达式体里禁止非局部 return——`fun parse(raw: String) = runCatching{...}.getOrNull() ?: return null` 报 "Returns are prohibited in functions with expression body"（CI ChatScreen.kt:4194 教训）：局部 helper 一律块体 + 显式可空返回类型，`?: return null` 只写在调用点。

### 7.2 注意事项
- 兼容层 1:1，UI 层自由：数据格式、注入算法、宏展开、斜杠行为、导入导出必须与官方互读互通；界面/交互/主题自主。
- 改动先对照官方源码，能 1:1 就 1:1，近似项必须标注（登记 6.1/6.3/6.4）。
- 本机（Termux）无 gradle/SDK：编译与 APK 全走 CI；引擎 jsdom 金测试本机可跑（scripts/kernel-golden）。
- push 自动触发 CI，必要时 `gh workflow run 328789880 --ref main`；GitHub 网络不稳定失败重试。
- 沙箱会话重置会丢 GitHub 凭证（gh auth/token）：push 失败先查 `gh auth status`，缺凭证就 `gh auth login` 或临时 PAT，不要反复盲推。
- 删除类操作先确认；大改动保持小步提交。

## 9. 内核稳定化与性能（在办，2026-08-24）

### 9.1 已根治（勿回退）
- 白屏三根因：①#sheld 高度塌缩→fullchat 覆盖块长手属性显式+flex 镜像官方；②WebView 视口
  0×0→AndroidView 重组重建容器后旧槽失联，ChatKernelShell 挂载前校验 parent===target 重挂；
  ③宽度锁死 var(--sheldWidth)=50vw 只剩左半屏→fullchat 改 width:auto+left/right 双锚定。
- 渲染进程崩溃循环（渲染 1s 即白+失败提示刷屏）：Warmer(建即毁)与双实例并发预热引发，
  已回退删除。**教训：低端机禁止并发建 WebView、禁止建即毁预热。**
- kernelReady 超时 15s 销毁误杀慢加载实例→30s 且只对调用方报错，实例留池继续加载。
- TH 脚本卡不转 iframe：管线类名为 custom-language-js（encodeStyleTags 前缀），逐 token 匹配。

### 9.2 内核稳定化（2026-08-25 已根治，勿回退）
- 冷启动空白 8~10s：池建在 ChatScreen remember 里=每次进聊天重载整页+旧池泄漏挤爆渲染进程。
  已改 KernelPoolHolder 进程级单例（MainActivity 入口预热，首帧后 600ms，单实例串行红线）。
- 开场白 1~2s 消失只剩背景：渲染签名守卫跨实例复用（重挂后新页空 DOM 被旧签名跳过）+
  双实例互踩（acquire 异步期效果重跑）。已修：签名按 host 重置 remember(host) + AtomicBoolean
  挂载守卫（20s 安全阀）。体检事件史实证闭环。
- 卡 17 开场白不渲染：角色正则放行开关不触发 DisplayCacheVersion.bump（聊天页持旧空脚本表
  直到重启）。已修：放行/收回即 bump。体检 firstTextLen=11 实证。
- 消息按钮全装死：describeAction 按"第一个 mes_ 前缀"取动作名，而按钮首类恒为通用样式
  mes_button → 全部上报成 mes_button。已改官方模板 34 动作白名单精确匹配；mes_bookmark
  宿主新接（读 extra.bookmark_link 复用打开确认弹窗）。冒充/继续语义已对官方核实一致
  （冒充结果进输入框可改再发=L5465；继续=引擎差分锁定）。
- 滚动顿挫：贴底状态每帧过桥挤占主线程，已限流 50ms。上下文/世界书胶囊已按用户要求删除。
- 体检=三层报告：引擎生效全景（正则链/世界书/上下文/人设/提供商/预设/开关真值）+
  内核 X 光 + 诊断事件史（创建/就绪/崩溃/渲染/清空/console 错误，80 条环形）。

### 9.3 原生组件现状
- ChatTopBar 重做 v1（49cd747b）：去实底 Surface（主题 bg 72% 实铺是发黑根因），
  近透明 0.38+调用点细模糊，高度砍半。智能显隐未做（用户未确认要）。
- 「选择模型」常驻提示条已删：发送时无模型才 toast+跳设置（用户拍板，勿恢复常驻 UI）。

### 9.4 酒馆助手（任务 #5）
- 31/184：事件/宏/消息族/变量族/世界书族 13 函数/版本常量/沙箱 iframe 运行时/独立设置页。
- 架构定稿见 docs/TAVERN_HELPER.md（引擎仓为真值、转换在 JS、Host 接口注入，零耦合红线）。
- 关键未解：TH 卡多在消息里写裸 <script>，官方净化器会剥掉——现 DOM 扫描抓不到；
  需改为从载荷 rawMes **原文**提取脚本块（TH 官方即读原始消息自建 iframe）。

### 9.5 用户红线（多次强调）
- 主题视觉与酒馆官方 1:1，任何影响美观的折中一律不接受（is-scrolling 模糊挂起已被否决撤销）；
  架构改动需可扩展、扩展模块不得与既有文件耦合（Host 接口注入模式为准）。
