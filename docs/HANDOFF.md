# 交接清单（会话上下文耗尽时使用）

> 最后更新：2026-08-08。接手的 Agent 先读第 1、2 节，再读 3–5 节，最后看第 6 节工作日志。

## 1. 项目与常用命令

- 项目：EmberInn（余烬酒馆）——原生 Android SillyTavern 兼容客户端
- 本地：`/data/data/com.termux/files/home/ember-inn`
- 远程：github.com/heikeyangle-code/ember-inn（分支 main，公开）
- 官方源码参照：`/data/data/com.termux/files/home/sillytavern-ref`（release 分支）
- **官方基线版本**：release `8172dcd`（2026-07-07），SillyTavern **1.18.0**；以后酒馆更新时，用 `git -C ~/sillytavern-ref pull` 拉新 release，重跑 `node scripts/diff/*.mjs` + `node scripts/build-presets.mjs`，红的就是需要移植/修正的差异
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

**已覆盖（54 组，共 858 例官方基准，全部通过）**：

| 组 | 脚本 | 测试 | 例数 |
|---|---|---|---|
| instruct 提示词 | instruct-official.mjs | InstructModeDiffTest | 36 |
| 世界书纯逻辑 | worldinfo-official.mjs | WorldInfoDiffTest | 19 |
| 世界书整体扫描 | worldinfo-scan-official.mjs | WorldInfoScanDiffTest | 17 |
| 世界书文件 | worldinfo-file-official.mjs | WorldInfoFileDiffTest | 2 |
| 正则 | regex-official.mjs | RegexDiffTest | 20 |
| PNG 角色卡 | card-png-official.mjs | CardPngDiffTest | 6 |
| 宏 e2e | macros-official.mjs | MacroDiffTest | 158 |
| {{pick}} 确定性 | pick-official.mjs | PickDiffTest | 5 |
| 编辑器排序 | editor-sort-official.mjs | EditorSortDiffTest | 6 |
| 快捷回复自动执行选择 | auto-execute-official.mjs | AutoExecuteDiffTest | 4 |
| 向量工具函数 | vector-utils-official.mjs | VectorUtilsDiffTest | 14 |
| 角色卡 V2 归一 | char-v2-official.mjs | CharV2DiffTest | 5 |
| 世界书正则解析 | regex-parse-official.mjs | RegexParseDiffTest | 9 |
| 作用域宏内容裁剪 | macro-trim-official.mjs | MacroTrimDiffTest | 7 |
| Anthropic 请求体 | anthropic-body-official.mjs | AnthropicBodyDiffTest | 17 |
| Gemini 请求体 | gemini-body-official.mjs | GeminiBodyDiffTest | 16 |
| 聊天历史填充 | chat-history-pop-official.mjs | ChatHistoryPopDiffTest | 5 |
| 示例对话填充 | dialogue-examples-pop-official.mjs | DialogueExamplesPopDiffTest | 4 |
| YAML 角色卡导入 | yaml-import-official.mjs | YamlImportDiffTest | 5 |
| 提示词组装合并 | prepare-prompts-official.mjs | PreparePromptsDiffTest | 7 |
| CharX 角色卡导入 | charx-import-official.mjs | CharXImportDiffTest | 9 |
| BYAF 纯逻辑 | byaf-macros-official.mjs | ByafMacrosDiffTest | 14 |
| BYAF 聊天导入 | byaf-chat-official.mjs | ByafChatDiffTest | 5 |
| BYAF 角色卡组装 | byaf-card-official.mjs | ByafCardDiffTest | 4 |
| PromptManager 名字规则 | prompt-name-official.mjs | PromptNameDiffTest | 28 |
| 表情精灵引擎 | expression-engine-official.mjs | ExpressionEngineDiffTest | 19 |
| 表情分类文本预处理 | expression-classify-official.mjs | ExpressionClassifyDiffTest | 8 |
| 群聊成员激活 | group-activation-official.mjs | GroupActivationDiffTest | 15 |
| 群聊角色卡合并 | group-cards-official.mjs | GroupCardsDiffTest | 8 |
| 群聊深度提示 | group-depth-official.mjs | GroupDepthDiffTest | 7 |
| 精灵存储/Risu 导入 | sprites-storage-official.mjs | SpriteStorageDiffTest | 9 |
| 角色卡字段聚合 | character-fields-official.mjs | CharacterFieldsDiffTest | 8 |
| JSON 角色卡导入 | json-import-official.mjs | JsonImportDiffTest | 10 |
| BYAF 完整导入 | byaf-import-official.mjs | ByafImportDiffTest | 8 |
| 斜杠转义判定 | slash-escape-official.mjs | SlashEscapeDiffTest | 13 |
| 提示词工具 | prompt-utils-official.mjs | PromptUtilsDiffTest | 9 |
| JSON 角色卡导出 | json-export-official.mjs | JsonExportDiffTest | 6 |
| SSE 流解析 | sse-stream-official.mjs | SseStreamDiffTest | 15 |
| 正则整体管线 | regex-pipeline-official.mjs | RegexPipelineDiffTest | 10 |
| 导演备注 | authors-note-official.mjs | AuthorsNoteDiffTest | 7 |
| 人设引擎 | persona-engine-official.mjs | PersonaEngineDiffTest | 16 |
| 群聊完整循环 | group-loop-official.mjs | GroupLoopDiffTest | 11 |
| OpenAI 请求体（全厂商） | openai-params-official.mjs | OpenAiParamsDiffTest | 21 |
| 工具 token 预分配 | tool-budget-official.mjs | ToolBudgetDiffTest | 4 |
| ChatCompletionPipeline 计划 | chat-pipeline-official.mjs | ChatPipelineDiffTest | 5 |
| 媒体附件纯逻辑 | media-engine-official.mjs | MediaEngineDiffTest | 17 |
| 媒体内联（OpenAI） | media-inline-official.mjs | MediaInlineDiffTest | 7 |
| 媒体 token 成本 | media-cost-official.mjs | MediaCostDiffTest | 18 |
| 特殊协议请求体（Mistral/xAI/AI21/Cohere） | special-bodies-official.mjs | SpecialBodiesDiffTest | 23 |
| OpenAI 文本补全请求体 | text-completion-body-official.mjs | TextCompletionBodyDiffTest | 6 |
| BYAF 资源提取 | byaf-assets-official.mjs | ByafAssetsDiffTest | 6 |
| 提示词总装整链（prepareOpenAIMessages+populateChatCompletion，含工具/媒体/推理签名分支） | prepare-messages-official.mjs | PromptPipelineDiffTest | 19 |
| 媒体内容块转换（Claude/Gemini） | media-convert-official.mjs | MediaConvertDiffTest | 25 |
| 消息转换整链（Claude/Gemini） | prompt-converters-official.mjs | PromptConvertersDiffTest | 41 |
| 消息缓存深度（Claude/OpenRouter） | prompt-converters-official.mjs | PromptConvertersDiffTest | 4+3 |
| 其余提供商转换器+合并+预算+OpenRouter | prompt-converters-official.mjs | PromptConvertersDiffTest | 61 |

**分支级覆盖审计与打桩登记（防漏机制，2026-08-08 起强制）**
- 规则：差分脚本内任何打桩/未覆盖分支，必须登记在本节 + 脚本头部注释；未登记即视为未完成，不许声称该分支 1:1。
- prepare-messages（总装整链，19 例）：populationInjectionPrompts 已用官方真函数；getExtensionPrompt(IN_CHAT) 恒空串（官方内置扩展源，Kotlin 同步为空）；preparePromptsForChatCompletion 用 fixture 注入的同一提示集合（该函数自身 7 例差分）；**工具调用历史 / 推理链（active_chain/since_last_user）/ 推理签名 / 媒体内联（list/gallery/data URL）已补端到端 8 例**，打桩登记见脚本头部：registerFunctionToolsOpenAI 空对象 → 工具预算预分配恒 1 token；setToolCalls tokens = JSON.stringify 长度/4（官方 tokenHandler 对象整体计数，两端同一近似）；getChat content 归一 `?? ''`；媒体仅 data: URL 内联且只记账，content 数组表示由 MediaInliner/MediaConvert 差分单独覆盖；群聊 selected_group、names_behavior、send_if_empty、预算溢出、squash 开关均已覆盖。
- SSE：运行时只有官方对拍的 SseChunkParser 一条路（逐字符、事件级 catch 跳过 = 官方平滑流语义、[DONE]/message_stop 收尾、reasoning 独立通道）；旧 SseParser 已删除（曾把 content:null 拼成字面 "null"）。
- **仍绕过 fixture 的部分**：prepareOpenAIMessages 的 chat→messages 构造循环（names_behavior 内容前缀、isSameModel 签名/推理过滤、media/invocations 从 extra 提取）由 fixture 直接注入消息对象绕过；Kotlin 侧由 App 的 ChatPromptFactory（JSONL → PromptMessage）按官方同名逻辑实现，接线点见 4.7/4.9。
- 其它脚本的历史打桩（Message/PromptManager/tokenHandler 等）均为“与 Kotlin 移植同语义”的显式桩，fixture 生成即对拍，登记在各自脚本头部。

**尚未做差分的**：网络/路由层（Mistral/xAI/Cohere/AI21/OpenRouter 请求体与响应解析用 MockWebServer 单测锁行为，转换器本身已逐字差分）；斜杠完整 parser（SlashCommandParser 依赖数十个模块与 DOM，无法逐字提取；转义判定 testSymbol 已差分 10 例，其余手写单测 + 源码对照）。
聊天重排/文件向量化主体（官方函数与 DOM/服务端焊死，无法逐字提取；其中纯函数 splitRecursive/trim 系列已差分 14 例）。
作用域宏配对逻辑（官方 MacroCstWalker 依赖 chevrotain CST 与 MacroRegistry，无法逐字提取；其中 trimScopedContent 纯函数已差分 7 例）。

**预设体系**：官方 `default/content/presets` 已打包进 engine resources（context 34 / instruct 38 / openai 1 / textgen 6 / novel 24 / kobold 6 / sysprompt 13 / reasoning 5，共 127 个），PresetLibrary 可加载；quick-replies 也打包。官方发版后跑 `node scripts/build-presets.mjs`。

## 3. 引擎进度（对照官方 release）

### 3.1 角色卡 ✅
PNG V2/V3（tEXt/ccv3）与 JSON 导入导出（官方也只导出 PNG/JSON）、CharX/YAML/BYAF 导入；JSON 导入 5 例 + JSON 导出 4 例（getCharaCardV2+unsetPrivateFields）、YAML 3 例、CharX 5 例、BYAF 14+5+4+4 例；V2 归一（readFromV2，官方差分 5 例 + 多轮补真 bug）、私有字段清理、JSON 导出（CharacterCardExporter）；PNG 字节级差分 6 例。
✅ CharX 资源提取（引擎 CharXImporter.CharXAssets）；✅ BYAF 资源提取（getCharacterImages/getChatBackgrounds 官方差分 6 例：默认头像回退、字节去重、paths 合并、url-join 不折叠 ../）；✅ App 层资源入库（2026-08-09：CharX icon→头像 + seed 取色，background/voice 落盘 assets/ 并记入 CharacterRecord；URL 导入未做）。

### 3.2 世界书 ✅（含 RAG 向量扩展）
buffer/matchKeys/getScore/parseDecorators、checkWorldInfo 整体扫描（含两段扫描、sticky/cooldown/概率）、深度/递归、分组评分、角色过滤、时间效果、多世界合并、装饰器/哈希、世界书文件导入导出、世界书↔角色书互转；正则在 BUILD 阶段接入扫描器。
✅ 扩展字段已全接上（数据全量透传 + 行为）：
   - vectorized → RAG：WorldInfoVectorActivation（同步/检索/强制激活，对齐 vectors activateWorldInfo）+ VectorStore/EmbeddingProvider（OpenAI 兼容）；**FileVectorStore 磁盘持久化对齐官方 vectra.LocalIndex**（目录 root/source/collection/model + items.json，重启不丢；InMemoryVectorStore 仅测试/临时）；Scanner 通过 externalActivations 强制激活（跳过关键词/概率）
   - 向量扩展补齐：**VectorChatRearranger**（聊天历史重排，对齐 rearrangeChat：protect 保留最近 N 条、insert 条数、模板 Past events:{{text}}、position 映射 BEFORE_PROMPT→start/IN_PROMPT→end）+ **文件/Data Bank 向量化**（对齐 processFiles/ingestDataBankAttachments/injectDataBankChunks/retrieveFileChunks/vectorizeFile：分块 splitRecursive、overlap、chunk 检索注入）+ VectorTextUtils（splitRecursive/trimToEndSentence/trimToStartSentence/overlapChunks 官方 1:1）
   - automationId → 快捷回复自动执行：WorldInfoAutoExecute.resolve + AutoExecuteHandler（对齐 quick-reply AutoExecuteHandler，prevent 栈；选择逻辑 4 例官方差分）
   - displayIndex → 编辑器排序：WorldInfoEditorSort（对齐 sortWorldInfoEntries，6 例官方差分，抓出 length 方向 bug 已修）
   - addMemo → 官方核心从未读取，仅透传

### 3.3 宏 ✅（含作用域宏）
通用作用域宏（{{setvar::x}}content{{/setvar}}、{{#}} 保留空白、嵌套、trim+dedent，对齐 MacroCstWalker.processScopedMacros）；trimScopedContent 官方差分 7 例；!?~> flags 官方标 TBD 未实现（无需补）；配对逻辑依赖 chevrotain CST 无法逐字差分（源码对照+单测）。
核心宏 + 官方 e2e 差分 158 例；变量简写全运算符、{{if}}、{{trim}} 作用域、legacy 标记/冒号/空格参数、嵌套参数、字段宏、聊天/状态宏；{{pick}} 用 seedrandom@3.0.5 逐位一致（5 例）。
✅ MacroRegistry 动态注册/注销/解析；✅ 宏 flags（{{#}} 保留空白已随作用域宏实现）；🟡 完整 MacroEnv（聊天/角色/系统状态）边界；!?~> 官方标 TBD 无需补。

### 3.4 斜杠 🟡
SlashParser（命名/无名/引号/转义/list 值/rawQuotes）+ SlashEngine（管道/闭包/双管道）、/pass /let /qr-arg、{{var}}/{{pipe}}/{{arg}} 状态宏、快捷回复执行器；SlashEscape（testSymbol 转义判定，STRICT_ESCAPING 奇偶反斜杠）官方差分 10 例。
🟡 偏差：官方惰性闭包（传给命令对象）与 () 即时执行统一为即时求值（近似）；命令数远少于官方。
✅ /parser-flag 命令已注册（引擎侧占位，参数保留）；❌ REPLACE_GETVAR/STRICT_ESCAPING 完整语义；150+ 官方命令多数未实现（多数依赖 App 状态）；无差分（SlashCommandParser 依赖数十模块与浏览器，无法逐字提取，源码对照+单测）。

### 3.5 提示词组装 ✅（核心）
PromptManagerCore（默认/用户顺序、enabled、injection_trigger、preparePrompt original/groupOverride、mergeSystemPrompts）、PromptCollection、ChatCompletion 嵌套集合（预算/溢出/squash）、ChatHistoryPopulator、DialogueExamplesPopulator、扩展注入（summary/AN/vectors/chromadb/persona/未知扩展）、in-chat 深度注入、continue nudge/prefill、bias、control prompts（impersonate/quiet）、nsfw/jailbreak/用户相对提示、工具调用（tool_calls）、ToolLoopPlanner 递归决策（官方 RECURSE_LIMIT=5：shouldContinue/buildNextMessages/nextRecursionCount，单测 4 例；工具真正执行在 App 扩展注册表）、人设 IN_CHAT 注入；**✅ PromptPipeline 总装器**（官方 prepareOpenAIMessages+populateChatCompletion 1:1：示例解析 parseExampleIntoIndividual/setOpenAIMessageExamples、控制提示、continue prefill、pin 顺序、squash；整链官方差分 5 例；边界：absolute 提示 in-chat 深度注入暂原样透传）、作者注释组合（ANWithWI）；CharacterCardFieldsEngine 官方差分 6 例；PromptUtils 官方差分 9 例；AuthorsNoteEngine（默认值解析+ANWithWI）官方差分 7 例（默认 position 修正为官方 1）。
✅ 每条历史消息过 preparePrompt 宏替换已补（对齐官方 populateChatHistory；ChatHistoryPrepareTest）；✅ names_behavior（COMPLETION 名字清理）已接：PromptNameSanitizer 对齐 isValidName/sanitizeName（28 例差分），ChatHistoryPopulator 在 COMPLETION 模式清理 name，常量改为官方 NONE=-1/DEFAULT=0/COMPLETION=1/CONTENT=2；✅ 工具预分配 token、媒体内联、推理签名已补（整链差分 19 例，ChatHistoryPopulator 官方分支）；多模态请求体已接（MediaInliner/MediaConvert 差分）；🟡 工具真正执行在 App 扩展注册表。

### 3.6 正则 ✅
RegexEngine + substituteRegex/宏替换 + 20 例差分（含 g/首匹配、i/m/s、非法 flags）；RegexPipelineEngine（getRegexedString：placement/markdownOnly/promptOnly/runOnEdit/minDepth/maxDepth/禁用扩展）官方差分 9 例；聊天消息正则已在扫描器接入（messageTransformer）。
🟡 global/preset/scoped 分桶与允许列表（App 层）。

### 3.7 预设 ✅
官方 127 个预设打包 + PresetLibrary；quick-replies 打包 + 执行器。moving-ui（界面预设）未打包。

### 3.8 聊天 🟡
jsonl 基础 + BYAF 聊天导入 + continue nudge。
❌ 聊天元数据（背景/书签/快照）（注：官方无 chat v2，此前审计有误已删）。

### 3.9 提供商 / LLM 客户端（引擎 1:1 审计）

**一句话结论**：OpenAI 兼容全家、Anthropic、Gemini（含预算自动推导）、Mistral、xAI、Cohere、AI21 路由全部接完（转换器均已差分移植，网络层用 MockWebServer 单测锁行为）；OpenRouter 已接媒体嵌入/推理签名/reasoning exclude，缓存标记待设置项；只剩 Vertex 服务账号认证未做。

| 提供商 | 协议路由 | 请求体 | 消息转换 | 媒体 | 预算/缓存/签名 | 模型列表 | 状态 |
|---|---|---|---|---|---|---|---|
| OpenAI | ✅ `/chat/completions` | ✅ 全厂商参数 21 例差分 | ✅ | ✅ MediaInliner 7 例差分 | — | ✅ `data[].id` | ✅ |
| Azure OpenAI | ✅ `deployments/{model}/chat/completions?api-version=2024-12-01` + api-key 头 | ✅ 同全厂商参数 | ✅ | ✅ | — | ✅ `value[].id` | ✅ |
| DeepSeek | ✅ `/beta/chat/completions` | ✅ | ✅（官方 sendDeepSeekRequest：postProcessPrompt semi_tools + addAssistantPrefix + addReasoningContentToToolCalls + reasoning_effort） | ✅ | — | ✅ | ✅ |
| Groq / Moonshot / MiniMax / 智谱 / 通义 / 硅基流动 / Z.AI / Fireworks / Perplexity / Custom / NanoGPT / Chutes / ElectronHub / SiliconFlow / o1 / Ollama | ✅ openai-compatible | ✅（各自厂商参数分支） | ✅ | ✅（OpenAI 媒体数组） | — | ✅ `data[].id` / 无端点时最小对话探测 | ✅ |
| Workers AI | ✅ `{account}/ai/v1/chat/completions` | ✅ | ✅ | ✅ | — | ✅ `result[].name` | ✅ |
| Anthropic | ✅ `/v1/messages` + x-api-key + anthropic-version | ✅ 17 例差分（thinking/tools/web_search/json_schema/beta/采样/verbosity/no-prefill） | ✅ `convertClaudeMessages` 整链 41 例差分，已接入 builder | ✅ `convertClaudePart` 25 例差分（image/text/video/audio → 块） | ✅ `calculateClaudeBudgetTokens` 已接入 LlmClient（SamplerParams.reasoningEffort 默认 auto；adaptive→effort 字符串/auto→不加 thinking） | 🟡 官方不发模型列表请求，用默认模型 | ✅ 差 tokenizer |
| Gemini AI Studio | ✅ `v1beta/models/{model}:generateContent?key=` | ✅ 16 例差分（generationConfig/thinkingConfig/tools/toolConfig/google_search/图像模态） | ✅ `convertGooglePrompt` 整链 41 例差分，已接入 builder | ✅ `convertGooglePart` 25 例差分（inlineData/分辨率） | ✅ `calculateGoogleBudgetTokens` 已接入 LlmClient（gemini-3 flash/pro→thinkingLevel，2.5→数字预算） | ✅ `models[].name`（过滤 generateContent） | ✅ 差 tokenizer |
| OpenRouter | ✅ openai-compatible | ✅（openrouter 参数分支 + transforms/plugins/reasoning.exclude/effort） | ✅ | ✅ `embedOpenRouterMedia`（audio+video）已接线 | ✅ `addOpenRouterSignatures` + `cachingAtDepthForOpenRouterClaude` + `cachingSystemPromptForOpenRouter`（SamplerParams 缓存开关/深度/TTL）+ DeepSeek `addReasoningContentToToolCalls` 全部接线 | ✅ | ✅ |
| Mistral | ✅ 专用路由 `/chat/completions` | ✅ body 差分 23 例含内（sendMistralAIRequest 逐字提取） | ✅ `convertMistral` 已接线 | — | — | ✅ | ✅ |
| xAI | ✅ 专用路由 `/chat/completions` | ✅（官方 sendXAIRequest 字段，reasoning_effort high→high/其它→low） | ✅ `convertXAI` 已接线 | — | — | ✅ | ✅ |
| Cohere | ✅ 专用路由 `/v2/chat`（官方 API_COHERE_V2） | ✅（官方 sendCohereRequest 字段：documents/tools/p/frequency/presence） | ✅ `convertCohere` 已接线 | — | — | ❌（无 models 端点，默认模型 command-r-plus） | ✅ |
| AI21 | ✅ 专用路由 `studio/v1/chat/completions` | ✅（官方 sendAI21Request 字段） | ✅ `convertAI21` 已接线 | — | — | ❌（无 models 端点，默认模型 jamba-large） | ✅ |
| Vertex AI | ❌ LlmClient 明确拒绝（需服务账号/项目配置） | 🟡 vertex 参数分支已差分 | Gemini 转换可复用 | ✅ | — | ❌ | ❌ 服务账号认证未做 |

其余要点：
- providers.json 数据驱动 **24 家**（新增 Cohere/AI21 专用协议条目；含智谱/通义/火山方舟），端点按官方 `src/endpoints/backends/chat-completions.js` 核对 + 2026-08 联网核实最新模型（OpenAI gpt-5.5/5.4、Claude opus-5/sonnet-5/haiku-4-5、Gemini 3.6/3.5-flash/3-pro、DeepSeek v4、Grok 4.3、Kimi k3、GLM-5.2、Qwen3.7、豆包 Seed 2.1、MiniMax M3 等）
- LlmClient 七协议路由：openai-compatible（/chat/completions）、Anthropic（/v1/messages）、Gemini（generateContent）、Mistral / xAI / AI21（/chat/completions）、Cohere（/v2/chat）；SSE 四格式（OpenAI delta 也覆盖 Mistral/xAI/AI21、Anthropic content_block_delta、Gemini candidates.parts、Cohere content-delta），流结束兜底 onDone
- **能力管道已全通**（ProviderRequestOptions）：tools/tool_choice（OpenAI 兼容全家 + Anthropic + Gemini + Mistral/xAI/AI21/Cohere/DeepSeek 各自官方形态）、json_schema 结构化输出（openai/mistral/xai response_format、ai21/deepseek json_object+hack 消息、cohere response_format.schema、Anthropic 强制 tool、Gemini responseMimeType/responseSchema）、Anthropic/Gemini web_search、Gemini requestImages/aspectRatio/imageSize/safetySettings
- 响应解析按协议取纯文本；Azure（deployments + api-version 2024-12-01 + api-key 头）、Workers AI（账户 ID + /ai/v1）专用 URL
- 模型列表拉取四种格式：openai data[].id / google models[].name（过滤 generateContent）/ workers result[].name / azure value[].id；无模型端点的提供商（Perplexity/自定义）用最小对话探测
- ProviderStore 多连接档案（profiles.json + activeId，旧 connection.json 自动迁移）
- 边界：GEMINI_SAFETY/VERTEX_SAFETY 由调用方桩/传参（差分 fixture 同样打桩）；`convertClaudePrompt` 遗留旧函数（仅 token 计数用）未移植；Claude/Gemini tokenizer 仍是回退 cl100k

### 3.12 表情精灵 ✅（引擎层纯逻辑）
- ExpressionEngine：文件名→标签（joy/joy-1/joy.expressive→joy）、图片元数据（fileName/title/imageSrc/isCustom）、分组排序（主文件优先、附加标记 additional）、chooseSpriteForExpression（fallback、多立绘随机、rerollIfSame、overrideSpriteFile）
- sampleClassifyText：去宏/引号/星号、短文本裁句尾、长文本首尾各 250 拼接、LLM 模式仅 trim（8 例差分）
- 官方差分 14+8+7 例（expressions/index.js + endpoints/sprites.js + utils.js 逐字对拍）；SpriteStorage 覆盖 spritesPath（含子目录/sanitize）与 importRisuSprites（提取/去重/删除字段）；DOM 显示/动画/LLM 分类 API 属 App/服务层
- 差分顺带修 VectorTextUtils.trimToStartSentence：JS substring 自动钳制长度，Kotlin 需 coerceAtMost（原实现会越界）

## 3.11 向量扩展（RAG 全量）✅（引擎层）
- 世界书 RAG（vectorized 同步/检索/强制激活）
- 聊天历史向量重排（enabled_chats / rearrangeChat）
- 文件 / Data Bank 向量化（enabled_files：分块、overlap、检索注入）
- 向量库：FileVectorStore（磁盘持久化，对齐 vectra 目录）+ InMemoryVectorStore（测试）；EmbeddingProvider：OpenAI 兼容 + BagOfGramsEmbedding（本地离线）
- 查询语义对齐官方：multiQueryCollection 全局 topK / queryCollection 单集合（hashes 不过滤阈值）
- ❌ 聊天摘要 summarize（P3，官方默认关）；本地 transformers 嵌入（Android 用 Ollama 替代，接口已留）；translate_files（P3）
- 扩展提示通过 ExtensionPrompt（3_vectors→vectorsMemory / 4_vectors_data_bank→vectorsDataBank）注入组装管线（ChatCompletionPipeline KNOWN_RELATIVE）
- 引擎测试 228 全绿（含重排/文件/分块/工具函数/作用域宏/YAML/JSON 导入导出/提示词组装合并/CharX/BYAF 完整导入/名字规则/表情精灵/分类预处理/群聊完整循环/精灵存储/角色卡字段/斜杠转义/提示词工具/SSE 流解析/正则管线/导演备注/人设引擎/OpenAI 请求体全厂商/工具预算/管线计划/媒体附件/媒体内联/媒体成本）

### 3.10 其它
- ✅ 群聊成员激活策略官方差分 15 例；✅ APPEND 角色卡合并 8 例；✅ 深度提示 7 例；✅ 完整循环纯逻辑（GroupLoopEngine：自动续写判定 + 每人生成类型 + 队列号）官方差分 11 例；🟡 多人回复拼接/组提示/nudge 链的 App 调度仍待做。✅ 作者注释、聊天元数据模型、TokenCounterFactory（OpenAI 精确 JTokkit）
- ❌ 服务层：TTS / STT / 图像 / 翻译（P3/P4）；向量引擎已齐，App 层接线待做

## 4. App / UI 进度

### 4.1 导航与返回手势 ✅
底部三 Tab（角色/聊天/设置）；聊天页、设置子页都接 BackHandler，系统返回键/侧滑返回逐级回退（聊天→列表、提供商详情→列表→设置主页）；Manifest 已开 enableOnBackInvokedCallback（Android 13+ 预测性返回动画）。README 守则第 7 条已落实。

### 4.2 首页（角色 Tab）🟡
品牌顶栏 + **全局搜索**（README 守则 8：角色名/描述、会话名/最后消息、世界书条目 key/content/comment、设置项；分组结果列表；世界书条目点击出详情弹层；设置项点击跳设置 Tab；空结果引导）、AI 对话置顶卡、最近聊过横滑、角色双列网格、FAB 导入（PNG/JSON）、长按菜单（置顶/新会话/字段/导出/删除）、删除二次确认、字段详情弹层、空状态引导、Toast 反馈。角色卡取色 seed 已存（avatar → Palette）。
❌ 角色详情编辑页、世界书/正则/变量/快捷回复/模型覆盖 UI、角色卡驱动完整主题；设置搜索结果目前只跳到设置 Tab（深链到具体子页未做）。

### 4.3 聊天页 🟡 v2（核心已接线 + 媒体 + 状态胶囊）
> 2026-08-09 修正：continue 用官方默认 nudge 路径（此前误用 continue_prefill=true 且 App 传“旧在前”，导致引擎把最老消息当续写对象=“重新生成”）；ChatPromptFactory 已按官方 setOpenAIMessages 翻成“新的在前”。思考过程走 LlmClient.onReasoning 独立通道，流式气泡上方显示“思考过程”块。
消息流 LazyColumn + 气泡 + 自动滚底 + 输入框 + 发送；**PromptPipeline 总装流式发送**（角色卡/世界书/示例/历史全部引擎内完成，SSE 逐 token）；停止按钮 = 取消 OkHttp call 并保留已生成部分（官方 mes_stop）；重新生成 = 删最后 AI 回复、复用最后用户消息（option_regenerate）；继续生成 = 官方 mes_continue（移出最后 AI + continue 模式续写，流结束与原消息合并落盘）；复制 / 删除 / **编辑消息**（官方 updateMessage：更新文本 + 清 extra.bias；regex/isEdit 待正则 UI）/ **冒充**（官方 Generate('impersonate')：模型以 {{user}} 视角写草稿，流式进输入框、不落历史；引擎 type=impersonate 整链差分已覆盖）/ 长按菜单；最后一条 AI 常驻 4 键；清空会话二次确认；Markdown + 代码高亮（mikepenz m3/coil3/code 0.43.0，import 包名已对 0.43.0 源码 jar 逐一核实；聊天气泡内已收敛为聊天风样式）；未配置模型横幅 → **一键深链“提供商与模型”子页**（先退出聊天再切 Tab，不会被早退逻辑挡住）；顶栏返回 + 角色头像 + accent 角色名；系统返回 / 侧滑返回已修。聊天页布局按 README 重排：systemBars 留白、气泡限宽 78%、间距/圆角/留白加大、顶栏与输入栏玻璃感（surface 半透明 + 细阴影）、空状态居中留白。
❌ 滑动切回复、上下文占比胶囊、世界书命中灯、快捷工具盘、媒体附件渲染（见 4.8）；Claude 冒充的 assistant_impersonation 设置未接（默认空串，影响为 0，排 P2）。

### 4.3.5 聊天 Tab（会话列表）✅
全部会话按时间倒序、置顶优先；点卡片进聊天；长按 / ⋯ = 置顶 / 导出聊天 JSONL（官方格式，可直接进酒馆）/ 删除（二次确认）；FAB「+」新建对话（AI 对话或选角色，每个角色可开多个会话，UUID 会话 id）；空状态引导；会话置顶持久化（SessionRecord.pinned，兼容旧 JSON）。
❌ 新建群聊入口（引擎群聊激活/合并/深度/循环已 1:1，App 调度层排 P2，UI 如实标“开发中”）。

### 4.4 设置 ✅（README 规格）
- 数据与隐私页已做实：导出全部数据（zip：角色/会话/聊天/头像/提供商配置）+ 数据位置透明展示 + 清除全部数据（二次确认，建议先备份）
- 首启引导已做实（README 启动体验）：欢迎页 + 导入角色卡（系统选择器直接导入）/ 直接开始聊天（进 AI 对话）/ 跳过；SharedPreferences 标记只显示一次；低饱和氛围渐变

- 设置主页：大标题 + 副标题、设置搜索（真过滤）、常用快捷区（主题/模型/语音/备份）、六组卡片（外观与主题 / 提供商与模型 / 语音 / 服务 / 数据与隐私 / 关于）
- 外观与主题：主题模式（跟随系统/浅色/深色）+ 六套预设主题（墨韵/青瓷/夜航/丹砂/琉璃/简约纸感），点选立即全局生效（实时预览），SharedPreferences 持久化；字体/圆角/背景模糊标“开发中”
- 提供商与模型（参照命理2 逻辑）：搜索 + 卡片列表（品牌 SVG 头像 + 名称 + 一句话 + 已配置/未配置 pill + “我的连接”切换/删除）；详情页 = 名称 / API Key（遮罩+显示）/ 接口地址 / 区域 / 账户 ID / API 版本 / 默认模型（底部弹层搜索）/ 测试连接 / 保存 / 删除确认
- 关于页做实：版本 0.1.0 / AGPL-3.0 / 数据仅本地 / 开源仓库
- 语音 / 服务 / 数据与隐私：标“开发中”（不假装做完）

### 4.4.5 应用图标 ✅
launcher 图标 = 用户提供的原图（Download/file_0000000078d0820782054bfedd4cb346.png）缩放为 mipmap-xxxhdpi/ic_launcher.png（192px），Manifest 引用 @mipmap/ic_launcher；换图只需替换该 PNG。

### 4.5 主题系统 ✅（全局层）
ThemePreset（seed/secondary/tertiary + 纸色/夜色）→ Theme.kt 自动生成整套 M3 ColorScheme（含 surfaceContainer 系列，浅色低饱和容器、深色提亮主色）；MainActivity 持有 themeMode/preset 状态，贯通 MainScreen → SettingsScreen → AppearanceScreen。
❌ 角色卡驱动主题（seed 已存，未生成角色配色）、MeshGradient 氛围背景、玻璃表面（Cloudy/Haze）、预设主题完整落盘（目前只有模式+六套 preset 的基础）。

### 4.6 数据存储 🟡
角色卡 characters/*.json + avatars/*.png、会话 sessions/*.json（含 pinned 置顶字段）+ chats/*.jsonl、提供商 profiles.json、主题 SharedPreferences（README 计划是 DataStore，未迁移）。
✅ 提供商“已配置”状态用进程内 ProviderState 共享（设置页保存/切换/删除后刷新，聊天页订阅，不再每发必读盘）；仅进入聊天页时读一次盘兜底。
❌ Room 未引入。

### 4.7 App 接线时官方行为怎么接（源码对照，新会话先读这里）

> 原则：App 接线只做“调用引擎 + 渲染结果”，不再重写一遍逻辑。每项都注明官方源码位置，接 UI 时照官方行为实现交互，引擎函数已经 1:1。

| 引擎能力 | 官方源码位置 | App 接线点 |
|---|---|---|
| 流式渲染 | `public/scripts/sse-stream.js` + `public/scripts/openai.js` eventSource | LlmClient.streamChatCompletions → SseChunkParser → ViewModel 增量状态 → 消息流逐 token 追加；停止 = 取消 OkHttp call（官方 abortController）；流结束必须走 onDone 收尾（引擎已兜底） |
| 提示词组装 | `public/scripts/openai.js` prepareOpenAIMessages + populateChatCompletion + `public/scripts/script.js` generate | ✅ 引擎已接：PromptPipeline.prepare 一个入口出最终消息（世界书/宏/人设/AN/示例/历史/控制提示/工具调用/媒体/推理签名/squash，整链差分 19 例）；App 发送前调它 + 按协议走 ChatRequestBuilder / Anthropic / Google |
| 消息转换 | `src/prompt-converters.js` convertClaudeMessages / convertGooglePrompt / 其余厂商 | ✅ 已全接：Claude/Gemini 在各自 builder 内部；Mistral/xAI/Cohere/AI21 在 LlmClient 对应协议分支调用；OpenRouter 在 openai-compatible 分支先签名/媒体再序列化 |
| 工具/能力选项 | `src/endpoints/backends/chat-completions.js` 各厂商分支 + `public/scripts/openai.js` oai_settings | ✅ 已接：ProviderRequestOptions 承载 tools/tool_choice/json_schema/web_search/request_images/safety，LlmClient 按各厂商官方形态写入请求体；App 层把设置/工具注册表填进 options 即可 |
| 预算计算 | `src/endpoints/backends/chat-completions.js` sendClaudeRequest / getGeminiBody（调用 calculateClaudeBudgetTokens / calculateGoogleBudgetTokens） | ✅ 已接：LlmClient 按模型/effort 调两个预算函数，结果传进 builder 的 reasoningBudget（adaptive→effort 字符串、auto→不加 thinking、数字→budget_tokens/thinkingBudget） |
| Markdown 渲染 | 官方用 Showdown + highlight.js + DOMPurify | mikepenz multiplatform-markdown-renderer + Highlights/KodeView；HTML 消息开关默认关，开启走本地 WebView + 消毒（对齐 power-user HTML 设置） |
| 媒体渲染 | `public/scripts/openai.js` Message.addImage/addVideo/addAudio + `public/scripts/media.js` | 聊天消息 `extra.media` → MediaEngine.getFromMime 判定类型 → 图片/GIF 用 Coil3（coil-gif）、音视频用 Media3 ExoPlayer；URL 附件按官方逻辑下载/展示；**extra.media 解析与渲染组件还没接** |
| 世界书注入 | `public/scripts/world-info.js` checkWorldInfo + `public/scripts/openai.js` | 发送前：世界书条目 → Scanner（含正则 messageTransformer、RAG 强制激活）→ 注入结果进 PromptAssembler；命中灯只读 Scanner 完整 match 结果 |
| 宏 | `public/scripts/macros/engine/` | 所有文本入 prompt 前统一走 MacroEngine（世界书 format、作者注释、历史消息 preparePrompt 已由引擎接线，App 只需保证 MacroEnv 提供聊天/角色/系统状态） |
| 正则 | `public/scripts/regex/` | 消息编辑/发送扫描接入 RegexPipelineEngine（placement/markdownOnly/promptOnly/runOnEdit/minDepth/maxDepth）；设置页做 global/preset/scoped 分桶 |
| 群聊 | `public/scripts/group-chats.js` | 每轮：GroupActivationEngine 选成员 → GroupCharacterCardsEngine 合并卡字段 → GroupDepthPromptsEngine 深度提示 → GroupLoopEngine 判定续写/生成类型 → 多人回复按官方顺序拼接 |
| 表情精灵 | `public/scripts/expressions/` + `endpoints/sprites.js` | ExpressionEngine.chooseSpriteForExpression 选图 → Lottie/sprite 动画渲染到消息头像区；分类 API 接 LLM 或本地模型 |
| 快捷回复 | `public/scripts/quick-reply.js` | 输入区快捷盘 → QuickReply 执行器（automationId 自动执行由引擎 WorldInfoAutoExecute 判定） |
| 人设 | `public/scripts/personas.js` | 进聊天前 PersonaEngine.resolve 出当前人设 → 描述符注入提示词组装 |
| 作者注释 | `public/scripts/authors-note.js` | AuthorsNoteEngine.resolve 每 N 条消息刷新，ANWithWI 合并世界书结果后注入 |
| tokenizer | `src/tokenizers.js` | TokenCounterFactory：OpenAI 用 JTokkit；Claude/Gemini 目前回退 cl100k，P2 换官方 web tokenizer |
| 提供商设置 | `public/script.js` / `src/endpoints/backends/chat-completions.js` | ProviderStore（profiles.json）多档案；协议/URL/认证/模型列表全在 LlmClient，UI 只读写 ProviderSpec + ConnectionProfile |

### 4.8 媒体这轮覆盖盘点（引擎已做 / App 待做）

**引擎已做（差分全过）**：
- MediaEngine 17 例：media type / display / index 纯逻辑（含越界、NaN、null 回退）
- MediaInliner 7 例：OpenAI 消息 content 文本→数组、image_url/video_url/audio_url + detail 质量
- MediaConverter 25 例：Claude/Gemini 内容块转换（image/text/video/audio → Claude image/text、Gemini inlineData，media_resolution_low/high、JS split 边缘）
- 消息转换整链 41 例：convertClaudeMessages / convertGooglePrompt（媒体随消息走）
- 已接入请求体：OpenAI（ChatRequestBuilder）、Anthropic/Gemini（builder 内 toChatMLJson → 转换器）
- MediaTokenCost 18 例：getImageTokenCost（low→85、auto≤512→85、2048 缩放→768 短边→512 方格 170/格+85）、视频 263 tokens/秒（回退 263×40）、音频 32 tokens/秒（回退 32×300）

**App 层（2026-08-09 已接）**：
- ✅ 聊天消息 `extra.media` 解析（ChatPromptFactory → PromptMessage.media/mediaDisplay/mediaIndex）
- ✅ 媒体渲染组件：图片/GIF（Coil3 + coil-gif）、音视频（Media3 ExoPlayer 1.10.0 PlayerView）、发送前附件缩略图/移除
- ✅ 系统文件选择器（image/video/audio 多选）→ 落盘 filesDir/media/，聊天 extra.media 只存路径 + source:"upload"（官方 saveBase64AsFile 语义，chats.js 逐行核实：{url,type,title,source} / media_display / media_index）→ 发送时 ChatPromptFactory 读文件转 data URL（官方 fetch→base64 语义）→ 引擎链内联 + token 预算；渲染时路径/URL 都支持
- ✅ 上下文占比胶囊 + 世界书命中灯（onPrepared 回调 → counts/激活条目 → 聊天页状态胶囊，点击世界书看命中条目）
- ✅ 官方对齐项：附件落盘 filesDir/media/（非 base64）、extra.media {url,type,title,source:"upload"} + inline_image:true、删除/清空/删会话时清理附件文件
- ⚠️ 未做（登记）：图库切换（LIST/GALLERY + media_index 左右滑）、从 URL 导入附件、图片发送前压缩（compressImage）、角色卡 extensions.assets（icon/background/voice）App 层入库/展示、LaTeX 渲染

### 4.9 App↔引擎接线核对表（本轮聊天链路，确认“结没结上”用这张表）

| 官方行为 | 官方源码位置 | 引擎函数 | App 调用点 | 验证 |
|---|---|---|---|---|
| 流式发送（角色卡/世界书/示例/历史总装） | `public/scripts/openai.js` prepareOpenAIMessages + `public/scripts/script.js` generate | PromptPipeline.prepare（整链差分 19 例）+ LlmClient.streamChatCompletionsAsync | ChatPromptFactory.prepare → ChatRepository.streamPrepared → ChatViewModel.startStream → ChatScreen StreamingRow | 差分✅ / CI 编译✅ / 真机待测 |
| SSE 逐 token 解析 + 思考过程 | `public/scripts/sse-stream.js` | SseChunkParser（差分 15 例） | LlmClient.executeStream → onDelta/onReasoning 独立通道 → ViewModel 正文 + 思考过程 | 差分✅ / CI 编译✅ |
| 停止生成（保留已生成部分） | 官方 abortController + mes_stop | StreamSession.cancel | ChatViewModel.stop → 部分落盘 | 差分✅ / CI 编译✅ / 真机待测 |
| 重新生成 | option_regenerate | —（复用最后用户消息重新请求） | ChatViewModel.regenerate | CI 编译✅ / 真机待测 |
| 继续生成 | mes_continue（默认 continue_prefill=false → nudge 路径） | PromptPipeline type=continue + cyclePrompt（nudge，差分已覆盖） | ChatViewModel.continueGeneration：传完整历史（ChatPromptFactory 翻成官方“新的在前”），续写追加回最后一条 AI | 差分✅ / CI 编译✅ / 真机待测 |
| 删除单条消息 | 官方删除消息后重写 jsonl | ChatJsonl.import/export（文件差分） | ChatStore.removeAt → ChatViewModel.deleteMessage | CI 编译✅ / 真机待测 |
| 清空会话 | 官方清空聊天 | — | ChatStore.replace(emptyList) | CI 编译✅ / 真机待测 |
| 会话列表/置顶/导出 | 官方 session 管理 + 聊天文件 | ChatJsonl 格式 | SessionRecord.pinned + ChatStore.list/lastMessage/exportJsonl → SessionsScreen | CI 编译✅ / 真机待测 |
| 未配置模型 | 官方报错提示不生成占位 | — | ChatViewModel notice → ChatScreen 横幅 → 设置页 | CI 编译✅ / 真机待测 |

> App 层（Compose UI）官方没有对应物，不做差分；上表“差分✅”指其调用的引擎能力已 1:1 差分验证。“CI 编译✅”指引用关系编译期对上；“真机待测”指安装 APK 后人工点一遍的最终确认项。

### 4.10 接线测试策略（新会话必读：以后“怎么测接线”按这个来）

接线盲区 = “运行时接对函数”与“App→引擎输入契约”，差分 fixture 覆盖不到，必须补自动化测试：

| 层 | 测什么 | 怎么测 | 例子 |
|---|---|---|---|
| 引擎函数 | 输出与官方一致 | 差分 fixture（官方 JS 逐字提取对拍） | 858 例基准 |
| 引擎内部运行时接线 | 实际走的函数/通道正确 | JVM 单测（MockWebServer 模拟协议） | LlmClientTest 流式：content:null 跳过、reasoning 独立通道、[DONE] 收尾 |
| App→引擎契约 | 输入形状/顺序/回调 | app 模块 JVM 单测（纯 Kotlin，不碰 Android） | ChatPromptFactoryTest：generate 正序、continue nudge 选最后一条 AI |
| 网络协议 | 请求体/响应解析 | MockWebServer + 转换器差分 | 各厂商请求体差分 |
| UI 交互 | 清空时机/滚动/折叠等 | Compose UI 测试（androidTest，待引入）+ 真机清单 | 输入框点发送立即清空 |

原则：测试代码都在 `src/test`（引擎/app），只跑 CI，**不进 APK**；HANDOFF 4.9 核对表每行按“差分✅ / CI✅ / 真机待测”标注，缺哪个补哪个。

### 4.11 审计结论（2026-08-09，App 层 vs 官方引擎逐项）

**已确认并修复**
- 重新生成/继续生成：官方只对最后一条 AI 生效（Generate 先删最后一条再生成）；长按菜单同样限制 → 已修
- 新会话开场白：官方新聊天第一条 = first_mes；空会话补上（含 {{user}} 宏由总装展开）→ 已修；alternate_greetings 未做
- 首页/会话列表进入不刷新（导入/清除后过期）→ 已修
- 发送主线程卡顿、流式滚动跳动、消息 420dp 截断、思考过程消失 → 已修（前几轮）

**已确认的兼容缺口（登记，未修）**
- 导出的 JSONL 消息缺官方字段（gen_started/gen_finished/extra.api/extra.model/extra.reasoning/token_count）：官方可导入（缺省兼容），但不是逐字段 1:1
- ChatPromptFactory.toOpenAiMessages 跳过 is_system 消息，官方把 narrator（extra.type=narrator）当 system 消息保留：导入含 system/narrator 的官方聊天时会错位
- 上下文占比用 TokenHandler.counts 之和，不含 start_chat 预留 3 token：轻微低估
- 编辑消息未跑官方 regex(isEdit)+bias 提取（正则 UI 未接）
- 附件：图库切换/URL 导入/图片压缩未做；角色卡 URL 型资产未下载
- swipes 模型缺失 → 无滑动切回复；聊天元数据（书签/快照/背景）未做
- 顶栏/输入栏玻璃为半透明模拟（Cloudy 未引入）；平板双栏未做

## 5. 剩余工作（按优先级）

**P0（“打开即聊”体验短板）**
1. ~~聊天 Tab 占位~~ → 会话列表 / 新建对话已做；剩群聊 App 调度层（引擎已 1:1，排 P2）
2. ~~流式/停止/重新生成/继续/复制/删除/编辑/冒充/提示词总装~~ → 已做；剩滑动切回复（需要 swipes 数据模型）
3. ~~全局搜索~~ → 首页搜索已接角色/会话/世界书/设置；设置结果深链到具体子页未做（目前跳设置 Tab）

**P1（功能完整）**
4. 角色详情编辑页：卡字段编辑、世界书管理 UI、正则/变量/快捷回复、模型覆盖、主题配方
5. 聊天页 LaTeX 渲染；滑动切回复；上下文占比胶囊 + 世界书命中灯；媒体附件渲染（4.8 的 App 待做项）
6. 设置剩余组：~~数据与隐私（备份/导出）、首启引导~~ → 已做；剩语音（TTS/STT）、服务（翻译/图像/向量）

**P2（引擎边界）**
7. SlashParser flags 完整语义 + 常用斜杠命令（需 App 状态）+ slash 差分 fixture
8. Claude/Gemini 官方 web tokenizer（当前回退 cl100k）
9. 群聊完整调度 + 人设管理 UI；聊天元数据（背景/书签/快照）
10. Vertex AI 服务账号认证（无法纯引擎实现，需服务账号/项目配置）；Claude/Gemini 官方 web tokenizer（当前回退 cl100k）；斜杠完整 parser 与命令；聊天元数据（书签/快照/背景）；群聊多人回复拼接；BYAF 资源提取；App 层：聊天 extra.media 解析 + 媒体渲染组件（图片 Coil3 / 音视频 Media3）

**备注（不能纯引擎做 / 需 App 或外部）**
- Vertex AI 服务账号认证（需服务账号 JSON + 项目配置，引擎无法实现）
- Claude/Gemini 官方 tokenizer（可做但用户同意暂时搁置；只影响 token 估算精度，不影响聊天）
- 150+ 斜杠命令（多数依赖 App 状态/生成流程）；斜杠 REPLACE_GETVAR 等解析语义（部分可做，排 P2）
- 群聊多人回复拼接 / 组提示 / nudge 链（App 调度层，引擎激活/合并/深度/循环已 1:1）
- 聊天书签 / 快照 / 背景（引擎逻辑待做，且需 App 数据模型）
- TTS / STT / 图像 / 翻译 / 聊天摘要 summarize（服务层 P3/P4，官方默认关）

**P3/P4（服务与扩展）**
11. TTS/STT/图像生成/翻译/向量库（services 接口已规划）
12. 自有插件 API、无障碍贯穿、平板双栏

**差分跟进**
- 官方发版：重跑 `node scripts/diff/*.mjs` + `node scripts/build-presets.mjs`，再全量 `:engine:test`
- 补 slash / JSON / CharX 导入导出的差分 fixture

## 6. 最近工作日志

## 最近一轮 77（2026-08-09：审计——新会话开场白补齐 + 兼容缺口登记）

- ChatViewModel init：角色空会话自动补 first_mes（官方 newChat 语义，宏由总装展开）
- HANDOFF 4.11 新增审计结论：已修复清单 + 兼容缺口（JSONL 官方字段/ narrator-system 错位/上下文占比低估/编辑 regex/图库/URL/压缩/swipes/元数据/玻璃/双栏）
- 无引擎改动；App 编译待 CI

## 最近一轮 76（2026-08-09：审计修复——regenerate 官方语义 / 设置回调 / 首页刷新）

- 官方核实（script.js Generate）：regenerate 只对最后一条 AI 生效——先删最后一条再生成。修复：ChatViewModel.regenerate 改为 lastOrNull 判定 + removeAt；continueGeneration 同样只对最后一条；长按菜单“重新生成/继续”只在最后一条 AI 显示
- 编译修复：ChatStore 缺 jsonArray import；SettingsHome 数据与隐私行误用外层 page（改为 onOpenData 回调）
- 首页不刷新 bug：清除全部数据/导入后回角色 Tab 列表过期 → CharactersScreen 进入时 LaunchedEffect refresh
- 无引擎改动；App 编译待 CI

## 最近一轮 75（2026-08-09：CharX 角色卡资产 App 入库）

- HomeViewModel.importCard：CHARX 格式用引擎 extractAssets 取 icon→头像（+Palette seed）、background/voice 落盘 filesDir/assets/<id>-<type>.<ext>，路径记入 CharacterRecord.backgroundPath/voicePath（旧 JSON 兼容）
- 官方参照：src/endpoints/characters.js persist auxiliary assets；引擎 CharXAssets 差分已覆盖提取
- 无引擎改动；App 编译待 CI

## 最近一轮 74（2026-08-09：媒体官方行为补齐 + 审计修正）

- 补官方行为：extra.inline_image=true（chats.js populateFileAttachment）；删除单条/整体替换/清空/删会话时清理附件文件（官方删除消息/附件会删文件）
- 审计修正：HANDOFF 4.8 原“大附件 base64 进 jsonl”已过时（已改路径存储），改为官方对齐清单 + 未做登记（图库切换/URL 导入/压缩/角色卡 assets/LaTeX）
- 无引擎改动；App 编译待 CI

## 最近一轮 73（2026-08-09：媒体附件对齐官方存储格式）

- 官方源码核实（script.js getMediaDisplay/getMediaIndex + chats.js populateFileAttachment）：extra.media 条目 {url,type,title,source}，source=upload，存储路径而非 base64；media_display/media_index 键名与我实现一致
- 调整：附件落盘 filesDir/media/，extra.media 只存路径 + source:"upload"；发送时 ChatPromptFactory 读文件 → data URL（mime 按扩展名推断）→ 链内联；渲染按 data:/路径 分别处理（Coil3 File / ExoPlayer Uri.fromFile）
- 备份 zip 与清除数据均包含 media 目录；app 测试补“文件路径→data URL”断言
- 无引擎改动；App 编译待 CI

## 最近一轮 72（2026-08-09：首启引导）

- OnboardingScreen：欢迎页（✦ 品牌 + 低饱和氛围渐变 + 导入角色卡 / 直接开始聊天 / 跳过 + “数据仅保存在本地”信任信号）
- MainScreen：首次启动展示（OnboardingPrefs SharedPreferences 标记）；导入走 HomeViewModel.importCard（PNG/JSON/CharX/YAML/BYAF）；直接开始聊天 → openChat(null, "AI 对话")
- 无引擎改动；App 编译待 CI

## 最近一轮 71（2026-08-09：设置·数据与隐私页做实）

- DataPrivacyScreen：导出全部数据 zip（characters/sessions/chats/avatars/provider，相对路径）+ 数据位置展示 + 清除全部数据（二次确认、不可撤销提示）
- SettingsScreen：SettingsPage.DATA + 深链 "data" + 设置组入口（备份与导出 / 数据仅保存在本地）
- 无引擎改动；App 编译待 CI

## 最近一轮 70（2026-08-09：媒体附件全链路 + 上下文占比胶囊 + 世界书命中灯）

- 发送附件：系统多选（image/video/audio）→ IO 读字节 → data URL → extra.media 落 jsonl → ChatPromptFactory 解析成 PromptMessage.media → 引擎链内联（data URL + token 记账）→ 各协议请求体（MediaInliner/MediaConvert 已有差分）
- 渲染：图片/GIF Coil3（coil-gif）、音视频 Media3 ExoPlayer（PlayerView，AndroidView 包装）；输入区附件缩略图可移除；消息气泡内媒体随消息显示（AI/用户均支持）
- 上下文占比胶囊 + 世界书命中灯：ChatPromptFactory.Prepared 增加 counts/maxContextTokens；ChatRepository.onPrepared 回调；ChatViewModel worldHits/contextUsage；聊天页输入区上方状态胶囊（世界书点击 Toast 展示命中条目）
- app 测试补：extra.media → 出站消息 media 内联断言；media3-exoplayer/ui 依赖加入；app JUnit 依赖补上（修 CI）
- 无引擎改动；App 编译待 CI

## 最近一轮 69（2026-08-09：锁死两处接线盲区为自动化测试）

- 引擎：LlmClientTest 新增流式运行时测试（MockWebServer 喂 role/null/reasoning_content/正文/[DONE]），断言正文只含“你好”、思考走 onReasoning、永不出现字面 null——锁住“运行时必须走官方对拍 SseChunkParser”
- App：ChatPromptFactoryTest（纯 Kotlin JVM 单测）锁住“generate 输出时间正序”与“continue nudge 选中最后一条 AI 并收尾”——锁住“新的在前”接线契约（曾致 continue 错当最老消息）
- 引擎 262 测全绿；App 单测由 CI 编译验证

## 最近一轮 68（2026-08-09：发送卡顿修复——总装挪后台线程）

- 根因：send() 在主线程同步做 PromptPipeline 总装（世界书/宏/历史/JTokkit token 计数）→ UI 顿 ~1s 才开始发
- 修复：startStream 用 viewModelScope.launch(Dispatchers.Default) 调 streamPrepared，先置“生成中”再异步组装+建请求；组装期间点停止会取消刚建好的请求；streamSession/streamActive 加 @Volatile
- 无引擎改动；App 编译待 CI

## 最近一轮 67（2026-08-09：聊天页滚动/消息完整显示/思考过程折叠）

- 流式滚动：只在用户贴底时跟随，且流式用 scrollToItem（逐 token animate 会造成上下跳）
- 消息显示不全：ChatMarkdown 去掉 heightIn(420.dp) 上限，最终消息完整渲染，无需展开
- 思考过程：生成完不再清空——lastReasoning 保留（finalize/stop 均保存），最后一条 AI 消息下方 ReasoningCard 默认折叠、点开查看；finalize 分支顺序修正为 冒充→continue→思考→追加
- 无引擎改动；App 编译待 CI

## 最近一轮 66（2026-08-09：Markdown 收敛成聊天风）

- ChatMarkdown：AI 气泡 Markdown 定制 colors/typography/padding——正文 bodyMedium、标题降级 titleSmall/Medium、代码低饱和容器 + 等宽小字、列表/引用间距克制（对齐 README：正文 16sp、低饱和、克制）
- 旧 SseParser 删除后运行时只有 SseChunkParser 一条路（见轮 65）
- 无引擎改动；App 编译待 CI

## 最近一轮 65（2026-08-09：删除旧 SseParser，运行时只留官方对拍解析器）

- 删除 engine/provider/SseParser.kt 与 SseParserTest.kt（旧实现曾把 content:null 拼成 "null"，且与官方对拍 SseChunkParser 重复）
- LlmClientTest 里两个直接测 SseParser 的用例改写为 SseChunkParser（anthropic/google data 载荷），覆盖不丢失
- HANDOFF 2.1 更新：SSE 只有一条运行时路径

## 最近一轮 64（2026-08-09：continue 官方 nudge 路径修正 + 思考过程显示 + ProviderState 共享）

- 根因：App 把历史“旧在前”传给总装，而官方 setOpenAIMessages 是“新的在前”；continue_prefill 的 shift() 于是把最老消息当续写对象 → 表现成“重新生成”。修复：ChatPromptFactory 历史消息 asReversed()（官方顺序）；continueGeneration 用官方默认 nudge 路径（type=continue + cyclePrompt=最后 AI 文本，continue_prefill=false），续写追加回最后一条 AI（stop 保留 partial 同样合并）
- 思考过程：LlmClient.streamChatCompletionsAsync/executeStream 增加 onReasoning 独立通道（官方 reasoning 不进正文）；ChatViewModel.streamingReasoning；ChatScreen 流式气泡上方“思考过程”块（secondaryContainer 低饱和）
- ProviderState 进程内共享：ProviderViewModel 保存/切换/删除后刷新；ChatViewModel 订阅，不再每次发送读 profiles.json；仅进聊天页读盘兜底（更彻底方案 Room/DataStore Flow 列 P1）
- 引擎 261 测全绿（本轮无引擎差分新增；LlmClient 改动由现有 SSE 差分 + 单测覆盖，App 接线待 CI）

## 最近一轮 63（2026-08-09：SSE null 修复 + LlmClient 切官方对拍解析器 + 聊天页 UI 重排 + 模型页深链）

- 修复“回复全是 NULL NULL”：SseParser.asText() 对非字符串原语（JsonNull/数字/布尔）不再 toString，null content 直接跳过；LlmClient.executeStream 改用官方对拍的 SseChunkParser（逐字符增量、事件级 catch 跳过 = 官方平滑流语义、[DONE]/anthropic message_stop 收尾、reasoning 不进正文）
- SseChunkParser 补齐官方边界：无匹配分支抛 Unknown event data format；delta:null 时按 JS typeof null==='object' 抛 reading 'text'；cohere 要求 type 匹配；.jsonArray/.jsonObject 全部改安全转型防 JsonNull 抛错
- sse-stream 差分 11→15 例（null content / 空 content / delta null / choices 空）；官方基准 854→858；引擎 261 测全绿
- 聊天页 UI 按 README 重排（systemBars 留白、气泡 78% 限宽、间距圆角留白、顶栏输入栏玻璃感、空状态居中）；未配置横幅一键深链“提供商与模型”子页（SettingsScreen deepLink + MainScreen 先退出聊天再切 Tab）
- HANDOFF：2 节表、2.1、4.3、日志63

## 最近一轮 62b（2026-08-09：修“配置了模型聊天还说没配置”）

- 根因：ChatViewModel 只在创建时读一次 profiles.json 并缓存 providerConfigured，之后不刷新；且 MainScreen 在聊天打开时早退 return，聊天页“先选一个模型”跳设置被挡住
- 修复：refreshProviderConfigured() 每次进入聊天页/发送前实时读盘（设置页与聊天页本就共用 ProviderStore/profiles.json）；MainScreen 跳设置时先退出聊天再切 Tab 2
- 无引擎改动 → 无新差分；CI 验证中

## 最近一轮 62（2026-08-09：聊天页补编辑消息 + 冒充）

- ChatStore.updateMessage：编辑文本 + extra.bias=null（官方 updateMessage AI_OUTPUT 分支；regex/isEdit 待正则 UI 接线）
- ChatViewModel：editMessage（流式中禁编辑）、impersonate（type=impersonate，草稿进 _impersonated 不落历史，停止保留草稿）、consumeImpersonation
- ChatPromptFactory/ChatRepository：impersonationPrompt 透传（默认官方 default_impersonation_prompt）
- ChatScreen：长按菜单加“编辑”（弹窗改文本保存）与“冒充”（AI 消息）；冒充流式气泡标“冒充草稿 · 我”，完成后草稿自动进输入框可改可发
- 无引擎改动 → 无新差分；App 编译待 CI 验证；Claude assistant_impersonation（默认空）排 P2

## 最近一轮 61（2026-08-09：首页全局搜索——角色/会话/世界书/设置）

- HomeViewModel.search()：角色名/描述、会话名 + 最后消息预览、角色卡内嵌世界书条目（key/keys/content/comment）、设置目录；WorldInfoHit / SettingsHit / SearchResults 数据类
- CharactersScreen：输入非空时切搜索结果列表（分组：角色/会话/世界书/设置），角色点击开聊、会话点击打开、世界书点击出详情弹层、设置点击跳设置 Tab；空结果引导；保留顶部搜索框
- MainScreen：CharactersScreen 接 onOpenSettings（切 Tab 2）
- 无引擎改动 → 无新差分；App 编译待 CI 验证；设置结果深链排后续

## 最近一轮 60（2026-08-09：总装整链补工具/媒体/推理签名分支差分 8 例 + App 聊天链路 CI 全绿）

- prepare-messages-official.mjs 补 8 例：tool-history-disabled、tool-active-chain-fallback、tool-since-last-user、tool-budget-tight、signature-history、media-list-inline、media-gallery-index、media-disabled-skip
- 引擎补齐官方分支：ChatHistoryPopulator 工具推理链（active_chain / since_last_user 官方循环 + 上一助手思考 fallback + includeSignature + setToolCalls 的 JSON token 近似）、媒体内联（list/gallery、data URL、开关、token 记账）、per-message signature 透传；ToolInvocation/PromptMessage/CompletionMessage/ToolCall 增加 reasoning/signature/media 字段；ChatRequestBuilder 透传 message.signature/reasoning 与 tool_calls.signature（对齐官方 serializeMessages）
- 打桩登记（脚本头部）：工具预算预分配恒 1 token、setToolCalls tokens=JSON.stringify 长度/4、getChat content 归一 ''、媒体仅 data: URL 且只记账（content 数组由 MediaInliner/MediaConvert 差分覆盖）
- 官方基准 846 → 854；引擎 259 测全绿
- App 层：聊天页总装流式 + 停止/重新生成/继续/删除 + Markdown；聊天 Tab 会话列表/新建对话/置顶/导出；修复 ChatPromptFactory contentOrNull() 误调用（CI 编译错误）；CI 全绿（engine-test + assembleDebug/Release APK）

## 最近一轮 59（2026-08-09：聊天页总装流式接线 + 聊天 Tab 会话列表）

- ChatRepository.streamPrepared：PromptPipeline 总装 → LlmClient.streamChatCompletionsAsync（SSE 可取消，官方 sse-stream.js 语义）；新增 type / continuePrefill 透传
- ChatViewModel：流式发送（未配置模型只提示不写历史）、stop()（取消保留已生成部分）、regenerate()、continueGeneration()（mes_continue 语义，续写合并落盘）、deleteMessage / clearSession / notice / providerConfigured
- ChatScreen：顶栏返回 + 角色头像 + accent 角色名；AI 左 / 用户右气泡（surfaceContainer / primaryContainer，用户 4dp 角）；流式气泡 + 闪烁光标；最后一条 AI 常驻 4 键（复制/重新生成/继续/删除）+ 长按菜单；清空二次确认；Markdown + 代码高亮；未配置模型横幅 → 设置页
- 新会话列表页（聊天 Tab）：按时间倒序 + 置顶优先、长按 / ⋯ = 置顶 / 导出 JSONL / 删除（二次确认）、FAB 新建对话（AI 对话或选角色，UUID 多会话）、空状态引导；群聊入口如实标开发中
- 依赖：multiplatform-markdown-renderer-m3 / coil3 / code 0.43.0 + material-icons-extended（BOM 1.7.8）；**markdownComponents 实际在 com.mikepenz.markdown.compose.components、highlightedCodeBlock/Fence 在 com.mikepenz.markdown.compose.elements**（对 0.43.0 源码 jar 核实，非 m3/code 包）
- 数据层：SessionRecord.pinned 置顶字段（兼容旧 JSON）、ChatStore.lastMessage / exportJsonl / delete
- 本轮无新引擎差分（引擎未改动）；引擎 259 测全绿；App 编译待 CI 验证

## 最近一轮 58（2026-08-08：总装整链差分 5→11 例 + 防漏机制）

- prepare-messages-official.mjs：populationInjectionPrompts 从 identity 打桩换成官方真函数（absolute 深度注入）；ChatCompletion 打桩的 checkTokenBudget 从 no-op 改为真抛（对齐官方 TokenBudgetExceededError）；canAfford/canAffordAll 用真实预算
- PromptPipeline 移植 populationInjectionPrompts（order 降序/角色固定序/深度 splice/整体 reverse）并接入 populate；预算溢出按官方“捕获后返回部分消息”
- 新增 6 例：absolute-in-chat（深度 2/3 注入）、群聊 groupNudge（含 "undefined" new_group_chat_prompt 官方怪癖）、names_behavior=COMPLETION、send_if_empty、squash 关闭、预算溢出（官方返回 0）
- 差分抓出并修 3 处：
  1. 官方主链 populationInjectionPrompts 无条件 reverse → 历史最终为“最新在前”（1.18 真行为，旧 fixture 因打桩没暴露）
  2. 测试 harness 的 token 计数用整数除法（floor），官方 stub 是 ceil —— 预算行为不一致，改 ceil
  3. 测试映射漏了 promptCollection 的 injection_depth 字段 → absolute 注入不生效
- 官方基准 840 → 846；引擎 259 测全绿
- **防漏机制**：HANDOFF 新增 2.1 分支级覆盖审计与打桩登记——所有差分打桩必须登记，未登记不得声称 1:1

## 最近一轮 57（2026-08-08：PromptPipeline 总装器官方整链差分 5 例）

- prepare-messages-official.mjs：逐字提取官方 prepareOpenAIMessages + populateChatCompletion + setOpenAIMessageExamples + parseExampleIntoIndividual + parseMesExamples + populateChatHistory + populateDialogueExamples；Message/ChatCompletion/promptManager/tokenHandler/ToolManager 打桩（与 Kotlin 移植同语义），dryRun=false + squash
- PromptPipeline.prepareWithPrompts：官方端到端输出（最终消息数组）与 Kotlin 总装器整链对拍，5 例全过（basic / 人设+AN 扩展 / impersonate+quiet / pin_examples / continue+prefill）
- 差分抓出并修 1 个真差异：continuePrefill 消息官方仅 names_behavior=COMPLETION 才带 name，原实现无条件带
- 调试过程还发现 fixture 层坑：JsonNull 会被 kotlinx 当字符串 "null"（测试映射已处理）
- 边界（已在脚本与类注释标注）：preparePromptsForChatCompletion 由它自己的 7 例差分覆盖，整链 fixture 用同一提示集合注入；absolute 提示 in-chat 深度注入（populationInjectionPrompts）暂原样透传
- 官方基准 835 → 840；引擎 255 → 259 测全绿

## 最近一轮 56（2026-08-08：ToolLoopPlanner 工具递归决策 + 剩余缺口备注）

- ToolLoopPlanner：官方 ToolManager.RECURSE_LIMIT=5 语义 —— shouldContinue（有 tool_calls 且 recursion < limit）、buildNextMessages（assistant + 每条 tool 结果消息）、nextRecursionCount；单测 4 例
- 工具真正执行（扩展注册/调用/流式累计）在 App 层，引擎决策层已 1:1
- HANDOFF 5 新增“备注（不能纯引擎做）”：Vertex 认证 / tokenizer（搁置）/ 150+ 斜杠命令 / 群聊拼接 / 书签快照 / 服务层
- 引擎 254 测全绿；官方基准 835 例不变

## 最近一轮 55（2026-08-08：BYAF 资源提取官方差分 6 例）

- byaf-assets-official.mjs：逐字提取官方 getCharacterImages/getChatBackgrounds 方法体（extractFileFromZipBuffer/fsPromises/path/urlJoin 打桩），6 例 fixture 全过
- ByafImporter 新增 extractCharacterImages/extractChatBackgrounds（1:1）：无图回退默认头像、label 透传、字节去重+paths 合并、名称 "Name bg N"
- 差分抓出 1 个真差异：官方 url-join 不折叠 ../（zip 路径原样查），原实现折叠 .. 会错误命中
- ByafAssetsDiffTest：图片文件名/字节/label、背景名/字节/paths 逐项对比
- 官方基准 829 → 835；引擎 249 → 250 测全绿
- 文档 3.1 更正：BYAF 资源提取从“未实现”改为 ✅（此前代码已有但未差分）

## 最近一轮 54（2026-08-08：OpenAI 文本补全路由 + 官方差分 6 例）

- TEXT_COMPLETION_MODELS（官方 src/endpoints/tokenizers.js 29 个模型）+ TextCompletionRequestBuilder
- LlmClient：模型命中列表且非 OpenRouter 时走 /completions + prompt（convertTextCompletionPrompt），响应解析兼容 choices[].text
- text-completion-body-official.mjs：逐字提取官方 isTextCompletion requestBody 构造段 + 真 convertTextCompletionPrompt，6 例 fixture 全过
- 差分抓出 1 个真差异：convertTextCompletionPrompt 官方 JS 拼接缺失 content 输出 "undefined"（null→"null"），原移植用空串——已按 JS 语义修正
- 官方基准 823 → 829；引擎 248 → 249 测全绿

## 最近一轮 53（2026-08-08：特殊协议请求体官方差分 23 例）

- special-bodies-official.mjs：逐字提取官方 sendMistralAIRequest/sendXaiRequest/sendAI21Request/sendCohereRequest 的 requestBody 构造段 + 真 convert*Messages（getPromptNames/getConfigValue/crypto/request.socket 打桩），23 例 fixture 全过
- 请求体从 LlmClient 内联抽出为 MistralRequestBuilder/XaiRequestBuilder/Ai21RequestBuilder/CohereRequestBuilder（与 Anthropic/Gemini 同架构），builder 增加 names 参数
- 差分抓出并修 2 个真差异：
  1. xAI reasoning_effort：官方“非空即写（auto 也写 low）”，原实现 auto 时省略
  2. AI21/DeepSeek JSON schema 消息：官方 JSON.stringify(value, null, 4) 4 空格美化，原实现紧凑拼接
  3. json_schema.strict 官方 `?? true`（false 保 false），原实现写死 true
  4. Cohere safety_mode：模型以 08-2024 结尾时加 OFF（原实现漏了）
- 官方基准 800 → 823；引擎 247 → 248 测全绿

## 最近一轮 52（2026-08-08：能力管道全通 —— 工具/结构化输出/联网搜索/图像模态/安全设置）

- 新增 ToolDefinition + ProviderRequestOptions（tools/toolChoice/jsonSchema/enableWebSearch/requestImages/aspectRatio/imageSize/safetySettings）
- ChatRequestBuilder：buildOpenAiCompatible(FromChatML) 支持 tools/tool_choice/response_format(json_schema)
- LlmClient 全厂商按官方形态接线：
  - OpenAI 兼容全家：tools + tool_choice + response_format.json_schema
  - Anthropic：tools→input_schema、tool_choice、jsonSchema→强制 tool、enableWebSearch→web_search 工具
  - Gemini：tools→function_declarations（$schema 移除、空 properties→null）、toolChoice→toolConfig、web_search→google_search、requestImages→responseModalities/imageConfig、safetySettings
  - Mistral/xAI：tools + response_format.json_schema（strict 默认 true）
  - AI21/DeepSeek：官方 json_object hack（response_format=json_object + “JSON schema for the response:” 追加 user 消息）+ tools（DeepSeek 清空空 required 数组）
  - Cohere：tools（参数 $schema 移除）+ response_format.schema
- LlmClientTest +6（openai/anthropic/gemini/mistral/ai21/cohere 能力断言）
- 引擎 247 测全绿；官方基准 800 例不变

## 最近一轮 51（2026-08-08：OpenRouter 缓存 + DeepSeek reasoner 处理接线）

- SamplerParams 新增 enableSystemPromptCache（默认关）/cachingAtDepth（默认 -1）/cacheTTL（默认 5m）
- OpenRouter：isClaude 模型按官方顺序 cachingSystemPromptForOpenRouter（开缓存时）→ cachingAtDepthForOpenRouterClaude（深度 != -1）；reasoning.exclude 补 effort
- DeepSeek：官方 sendDeepSeekRequest 全链 —— postProcessPrompt(semi_tools) → addAssistantPrefix(prefix) → addReasoningContentToToolCalls → includeReasoning+effort 时加 reasoning_effort
- LlmClientTest +2（OpenRouter cache_control/5m、DeepSeek reasoning_content/reasoning_effort）
- 引擎 241 测全绿；官方基准 800 例不变
- 提供商引擎侧仅剩：Vertex 认证（搁置）、Claude/Gemini 官方 tokenizer（P2）

## 最近一轮 50（2026-08-08：Mistral/xAI/Cohere/AI21 协议路由 + OpenRouter 专项接线）

- providers.json +2：cohere（protocol=cohere，api.cohere.ai/v2，默认 command-r-plus）/ ai21（protocol=ai21，api.ai21.com/studio/v1，默认 jamba-large）；mistral/xai 协议从 openai-compatible 改为专用
- LlmClient 七协议路由：Mistral（convertMistral + 官方字段）、xAI（convertXAI + reasoning_effort high/low）、AI21（convertAI21）、Cohere（convertCohere + /v2/chat + 独立响应解析 message.content 块/tool_plan）
- OpenRouter：openai-compatible 分支先 addOpenRouterSignatures + embedOpenRouterMedia(audio+video)，body 补 transforms/plugins/reasoning.exclude；Referer/X-Title 由 providers.json extra_headers 提供（项目身份）
- SseParser +cohere：content-delta/message.content.text/tool_plan、message-end/stream-end 结束
- LlmClientTest +11（mistral/xai/ai21/cohere 请求、cohere 响应块、openrouter 头与 body、cohere SSE）；ProviderRegistryTest 协议集合更新
- 引擎 239 测全绿；官方基准 800 例不变（转换器差分已覆盖，路由/网络层属 MockWebServer 单测）

## 最近一轮 49（2026-08-08：Claude/Gemini 预算自动推导接进 LlmClient）

- SamplerParams 新增 reasoningEffort（默认 auto）/includeReasoning/enableAdaptiveThinking（官方默认值）
- LlmClient anthropic 分支：isAdaptiveModel 按官方正则（opus-4-7 恒 adaptive；opus-4-6/sonnet-4-6 看开关）→ calculateClaudeBudgetTokens → builder.reasoningBudget（允许 null：auto+adaptive 不加 thinking）
- LlmClient google 分支：calculateGoogleBudgetTokens → builder（gemini-3 flash/pro 返回 thinkingLevel 字符串，2.5 flash/pro 返回数字预算）
- Anthropic/Google builder 的 reasoningBudget 参数改为可空（null = 官方“不加 thinking”语义）
- LlmClientTest +5：数字预算（1024+max_tokens 补到 1536）、adaptive effort→output_config、adaptive auto 无 thinking、gemini-3 thinkingLevel、google auto 不设预算
- 引擎 228 测全绿；官方基准 800 例不变

## 最近一轮 48（2026-08-08：媒体 token 成本估算移植 + 官方差分 18 例）

- media-cost-official.mjs：逐字提取官方 openai.js getImageTokenCost + addVideo/addAudio 的 token 规则（getImageSize/getDuration 打桩），18 例 fixture 全过
- MediaTokenCost：图片 low→85、auto≤512→85、2048 缩放→768 短边→512 方格（170/格+85）；视频 263 tokens/秒（回退 263×40）；音频 32 tokens/秒（回退 32×300）
- 官方基准 782 → 800；引擎 227 → 228 测全绿；HANDOFF 4.8 缺口已销（App 渲染时调用 MediaTokenCost 供上下文占比显示）

## 最近一轮 47（2026-08-08：提供商审计 + App 接线源码对照 + 组件选型文档）

- CI 确认全绿（3 条最新 workflow runs completed success；HEAD 86ae555 已推送）
- HANDOFF 3.9 重写为**逐提供商审计表**：OpenAI/Azure/DeepSeek/其余 openai-compatible/Workers AI 全绿；Anthropic/Gemini 缺预算自动推导；OpenRouter/Mistral/xAI/Cohere/AI21 转换器已差分未接线；Vertex 认证未做
- 新增 HANDOFF 4.7：**App 接线时官方行为怎么接**（每个引擎能力 → 官方源码位置 → App 接线点：流式/提示词组装/消息转换/预算/渲染/世界书/宏/正则/群聊/表情/快捷回复/人设/作者注释/tokenizer/提供商）
- 新增 HANDOFF 4.8：媒体这轮覆盖盘点（引擎 17+7+25+41 例差分全过并接入三协议请求体；App 待做 extra.media 解析 + Coil3/Media3 渲染 + 附件上传）
- docs/COMPONENTS.md 补齐最强现成件与版本（mikepenz 0.43.0 / Coil 3.5.0 / Media3 1.10.0 / Lottie 6.7.1 / DataStore 1.2.1 / PredictiveBack / M3 1.4.0）+ 每个组件的 App 接入点 + 官方源码位置
- README 技术栈与版本基线更新（Lottie 6.7.1、media3 1.10.0、markdown 0.43.0、datastore 1.2.1、activity-compose 1.13.0）
- 官方基准 782 例 / 引擎 227 测全绿（本轮只改文档，未动引擎）

## 最近一轮 46（2026-08-08：媒体内联官方差分 + OpenAI 请求体接入）

- media-inline-official.mjs：openai.js Message.addImage/addVideo/addAudio 内容部分，7 例 fixture 全过
- MediaInliner.inlineOpenAi：text→数组、image_url/video_url/audio_url、detail 质量、无媒体时原样返回
- CompletionMessage.media + ChatRequestBuilder 接入（OpenAI 请求体 content 支持媒体数组）
- Anthropic/Gemini 媒体内联仍标 App/服务层待做
- 官方基准 634 → 641；引擎 223 测全绿

## 最近一轮 45（2026-08-08：媒体附件纯逻辑官方差分）

- media-engine-official.mjs：getMediaDisplay/getMediaIndex/getFromMime，17 例 fixture 全过
- MediaEngine + MediaAttachment/MediaDisplay：list/gallery、media_index 原样类型（数字/字符串/null）、越界/NaN/负值回退、MIME 大小写敏感
- 媒体内联到请求体与 App 渲染标注为 App/服务层
- 官方基准 617 → 634；引擎 222 测全绿

## 最近一轮 44（2026-08-08：ChatCompletionPipeline 整链计划官方差分）

- chat-pipeline-official.mjs：逐字提取 openai.js populateChatCompletion，prompts/Message/TokenHandler/populate* 打桩，5 例 fixture 全过
- ChatCompletionPipelinePlan：固定顺序/控制提示/系统+用户相对/增强/bias/相对注入/工具预留/continue prefill/pin_examples/free+control 全操作序列
- 官方基准 612 → 617；引擎 221 测全绿

## 最近一轮 43（2026-08-08：工具 token 预分配官方差分）

- tool-budget-official.mjs：populateChatCompletion 的 ToolManager 预分配片段，4 例 fixture 全过
- ToolBudgetEngine + ChatCompletionPipeline toolBudget 接线（预留 token）
- 官方基准 608 → 612；引擎 220 测全绿

## 最近一轮 42（2026-08-08：OpenAI 请求体全厂商官方差分）

- openai-params-official.mjs 扩到 21 例：OpenRouter/Groq/XAI/Cohere/DeepSeek/Workers AI/Moonshot/Custom/Perplexity/Mistral/Chutes/ZAI/MiniMax/NanoGPT/Vertex/ElectronHub/SiliconFlow/o1
- OpenAiParamsBuilder 实现全部厂商分支；Number.EPSILON 用 Math.ulp(1.0)；JS 科学计数法 e 与 Kotlin E 归一比较
- 官方基准 593 → 608；引擎 219 测全绿

## 最近一轮 41（2026-08-08：OpenAI 请求体核心官方差分）

- openai-params-official.mjs：createGenerationParameters 核心（OpenAI/Azure）6 例 fixture 全过
- OpenAiParamsBuilder：stream/n/seed/logprobs/vision 清理/o1 max_completion_tokens 迁移；JS 整数序列化差异用规范化比较
- 厂商专用分支（OpenRouter/Groq/XAI/Cohere 等）仍标注边界
- 官方基准 587 → 593；引擎 219 测全绿

## 最近一轮 40（2026-08-08：审计修复）

- 正则管线 +1：trimStrings 的 characterOverride 透传（官方 substituteParams name2Override）
- 人设 +2：resolve 的 autoLock 按 persona_auto_lock 计算（切换/同人设两分支）
- SSE +3：choices.delta.content 数组 thinking、choices.message.content、not-primary 抛错（官方行为）
- 官方基准 581 → 587；引擎 218 测全绿

## 最近一轮 39（2026-08-08：群聊完整循环纯逻辑官方差分）

- group-loop-official.mjs：shouldAutoContinue（script.js）+ generateGroupWrapper 计划（类型/队列号），11 例 fixture 全过
- GroupLoopEngine：自动续写（enabled/target/chat-completions/impersonate/用户输入）、每人生成类型、showQueue 队列
- 官方基准 570 → 581；引擎 218 测全绿

## 最近一轮 38（2026-08-08：人设引擎官方差分）

- persona-engine-official.mjs：personas.js 纯逻辑 14 例 fixture 全过
- PersonaEngine：状态（default/chat lock/character lock）、临时锁、连接查询、当前连接对象、描述符创建、按聊天解析（chat/character/default 优先级、无效解锁、多连接）
- 官方基准 556 → 570；引擎 217 测全绿

## 最近一轮 37（2026-08-08：导演备注官方差分）

- authors-note-official.mjs：默认值解析（prompt/interval/position/depth/role）+ ANWithWI 合并，7 例 fixture 全过
- AuthorsNoteEngine.resolve/composeWithWorldInfo；修正 AuthorsNote 默认 position 为官方 1（原为 0）
- 官方基准 549 → 556；引擎 216 测全绿

## 最近一轮 36（2026-08-08：正则完整系统补差分）

- RegexDiffTest 13 → 20：新增 /foo/g 全局替换、无 g 仅首匹配、i/m/s、非法 flags 回退整体正则
- RegexPipelineDiffTest 9 例：getRegexedString 整体管线（placement、markdownOnly、promptOnly、runOnEdit、minDepth/maxDepth、禁用扩展）
- RegexEngine.parseRegex 重写为 regexFromString 语义：无 g 用 replaceFirst（原实现总是全替换，是真实 bug）
- RegexPipelineEngine 新增；官方基准 533 → 549；引擎 215 测全绿

## 最近一轮 35（2026-08-08：SSE 流解析官方差分）

- sse-stream-official.mjs：逐字提取 sse-stream.js parseStreamData，8 例 fixture 全过
- SseChunkParser：OpenAI delta/text/reasoning_content/reasoning、Anthropic text/thinking、Gemini parts/工具调用、token、content 逐字符增量，数据克隆与 reasoning 标记 1:1
- 官方基准 525 → 533；引擎 214 测全绿

## 最近一轮 34（2026-08-08：边缘 case 扩充三）

- 斜杠转义 +3：未命中、offset 未命中、多反斜杠
- 角色卡字段 +2：仅人设、群聊空卡覆盖
- 群聊深度 +2：空 groupId、空成员
- 官方基准 518 → 525（组数不变）；引擎 213 测全绿

## 最近一轮 33（2026-08-08：边缘 case 扩充二）

- YAML +2：尾部点空格清理、多行 context
- JSON 导出 +2：v3 无 data、v1 tags 数组
- 群聊角色卡 +2：APPEND 禁用其它成员、全空字段
- 精灵存储 +2：空名称路径、Risu 重复 label
- 官方基准 510 → 518（组数不变）；引擎 213 测全绿

## 最近一轮 32（2026-08-08：边缘 case 扩充）

- JSON 导入 +5：v3 无 data / v2 data 覆盖 / tags 数组 / gradio 带 name / 空备注
- BYAF 导入 +4：重复背景去重 / 背景文件名冲突 / 空场景标题 / 重复备用图标
- CharX +4：扩展名从 zipPath 推导 / URI 大小写 / 非 main 图标 / ./ 路径归一
- 表情引擎 +5：大写文件名 / 空列表 / 无缓存 / fallback 空 / reroll 全排除
- 群聊激活 +5：空成员 / 自然空 / 池化空 / 全 system 且允许 / 随机 0
- 差分顺带修 2 个真 bug：BYAF uniqueName 要按完整路径判重；空 title 回退 card.name
- 官方基准 487 → 510（组数不变，case 增加）；引擎 213 测全绿

## 最近一轮 31（2026-08-08：JSON 角色卡导出官方差分）

- json-export-official.mjs：逐字提取 getCharaCardV2/convertToV2/readFromV2/unsetPrivateFields，4 例 fixture 全过
- CharacterCardExporter：旧版 mes_example/根 creator_notes/create_date 语义补对齐（buildV2FromLegacy 增加 rootCreatorNotes/mesExample 透传）
- 官方基准 483 → 487；引擎 213 测全绿

## 最近一轮 30（2026-08-08：提示词工具官方差分）

- prompt-utils-official.mjs：官方 collapseNewlines/parseMesExamples，9 例 fixture 全过
- PromptUtils：连续换行压缩；示例块解析（<START> 归一、openai/instruct 表头、自定义 separator）
- 官方基准 474 → 483；引擎 212 测全绿

## 最近一轮 29（2026-08-08：斜杠转义判定官方差分）

- slash-escape-official.mjs：照官方 SlashCommandParser.testSymbol/testSymbolLooseyGoosey 生成 10 例 fixture
- SlashEscape：STRICT_ESCAPING 反斜杠奇偶判定、loose 单反斜杠、jumpedEscapeSequence 状态迁移、offset
- 官方基准 464 → 474；引擎 211 测全绿

## 最近一轮 28（2026-08-08：BYAF 完整导入官方差分）

- byaf-import-official.mjs：逐字提取 characters.js importFromByaf + readFromV2，ByafParser/fs/write 打桩，4 例 fixture 全过
- ByafImporter.importPlan：角色卡/头像/聊天/背景/备用图标导入计划（fileName=sanitize replacement、聊天文件名、背景唯一名、备用图标、card.chat 指向首个聊天）
- App 层按计划落盘即可；官方基准 460 → 464；引擎 210 测全绿

## 最近一轮 27（2026-08-08：JSON 角色卡导入官方差分）

- json-import-official.mjs：逐字提取 characters.js importFromJson（V2/V3、V1、Gradio 三分支）+ charaFormatData/convertToV2/readFromV2/unsetPrivateFields，5 例 fixture 全过
- JsonImporter 实现三分支；CardImporter.JSON 从“原样返回”改为官方完整流程（Risu 字段清理/私有字段/sanitize/归一/创建时间/旧版转换）
- 差分抓出并修 3 个真 bug：
  1. V2Normalizer 聊天回填只读 data.name，官方读根 name——V2 卡回填成裸时间戳
  2. readFromV2 缺失 talkativeness/fav 时官方会删除根字段，原实现只透传不删
  3. V1/Gradio 的 mes_example/根 creator 未进 buildV2FromLegacy——补 mesExample 参数/includeRootCreator
- 官方基准 455 → 460；引擎 209 测全绿

## 最近一轮 26（2026-08-08：角色卡字段聚合官方差分）

- character-fields-official.mjs：照官方 script.js getCharacterCardFieldsLazy 生成 6 例 fixture
- CharacterCardFieldsEngine：persona/system/jailbreak/version/depth/creatorNotes/description/personality/scenario/mesExamples/firstMessage/alternateGreetings；群聊卡片覆盖、chat_metadata 覆盖、prefer_character_prompt/jailbreak 开关
- 官方基准 449 → 455；引擎 208 测全绿

## 最近一轮 25（2026-08-08：精灵存储/RisuAI 导入官方差分）

- sprites-storage-official.mjs：照官方 sprites.js getSpritesPath/importRisuSprites 生成 7 例 fixture
- SpriteStorage：spritesPath（sanitize/子目录）、extractRisuSprites（additionalAssets+emotions 合并、label 去重、删除导入字段、空/无名称时原卡不变）
- 官方基准 442 → 449；引擎 207 测全绿

## 最近一轮 24（2026-08-08：群聊深度提示官方差分）

- group-depth-official.mjs：照官方 getGroupDepthPrompts 生成 5 例 fixture
- GroupDepthPromptsEngine：APPEND/APPEND_DISABLED 收集成员 depth_prompt（text/depth/role），disabled 且非当前角色跳过，缺省 depth=4/role=system
- 官方基准 437 → 442；引擎 206 测全绿

## 最近一轮 23（2026-08-08：群聊角色卡合并官方差分）

- group-cards-official.mjs：照官方 getGroupCharacterCardsLazy（customTransform/replaceAndPrepareForJoin/collectField）生成 6 例 fixture
- GroupCharacterCardsEngine：APPEND/APPEND_DISABLED 合并多人描述/性格/场景/示例；prefix/suffix/<FIELDNAME>；disabled_members 与当前角色豁免；scenario/mes_example override 优先
- 官方基准 431 → 437；引擎 205 测全绿

## 最近一轮 22（2026-08-08：群聊成员激活策略官方差分）

- group-activation-official.mjs：照官方 group-chats.js activateListOrder/activateImpersonate/activateSwipe/activatePooledOrder/activateNaturalOrder + utils.js shuffle/extractAllWords 生成 10 例 fixture
- GroupActivationEngine 实现全部激活策略（NATURAL/LIST/POOLED/MANUAL/SWIPE/IMPERSONATE），输出激活成员 avatar
- 覆盖：名单去重、随机冒充、用户/系统/旁白过滤、未发言优先、自然轮转（mention/talkativeness/禁自答/兜底随机）
- 官方基准 421 → 431；引擎 204 测全绿

## 最近一轮 21（2026-08-08：表情分类文本预处理官方差分）

- expression-classify-official.mjs：官方 sampleClassifyText + utils.js trimToEndSentence/trimToStartSentence，8 例 fixture 全过
- ExpressionEngine.sampleClassifyText 实现：去宏/引号/星号；<500 字符裁到句尾；>=500 取首尾各 250 再拼接；LLM 模式只 trim
- 差分抓出 1 个真 bug：VectorTextUtils.trimToStartSentence 用 Kotlin substring 会越界，官方 JS substring 自动钳制——已 coerceAtMost 修复
- 官方基准 413 → 421；引擎 203 测全绿

## 最近一轮 20（2026-08-08：表情精灵引擎官方差分）

- expression-engine-official.mjs：逐字提取 expressions/index.js getExpressionImageData/chooseSpriteForExpression + sprites.js 标签提取 + getSpritesList 分组，14 例 fixture 全过
- 新增 engine/expression/ExpressionEngine：SpriteEntry/ExpressionImage/ExpressionGroup/ExpressionSettings
- 覆盖：标签提取、图片元数据、分组排序、fallback、多立绘随机、rerollIfSame、overrideSpriteFile、#reset
- 官方基准 399 → 413；引擎 202 测全绿

## 最近一轮 19（2026-08-08：PromptManager 名字规则官方差分 + COMPLETION 名字清理）

- prompt-name-official.mjs：官方 isValidName（^[a-zA-Z0-9_]{1,64}$）与 sanitizeName（非法→_，截断 64）28 例 fixture 全过
- 新增 PromptNameSanitizer；ChatHistoryPopulator 接 namesBehavior：COMPLETION 模式先清理 name 再进历史消息
- 修正 character_names_behavior 常量：官方 NONE=-1 / DEFAULT=0 / COMPLETION=1 / CONTENT=2（原来 0..3 与官方不一致）
- 官方基准 371 → 399；引擎 201 测全绿

## 最近一轮 18（2026-08-08：BYAF getCharacterCard 官方差分）

- byaf-card-official.mjs：逐字提取 src/byaf.js getCharacterCard + 依赖的 4 个纯逻辑方法，Date 打桩，4 例 fixture 全过
- 差分抓出并修 1 处真差异：isNSFW 官方按原始真值判断（字符串 "false" 也是 true）——原实现解析成布尔
- ByafImporter 抽出 buildCard(manifest/character/scenarios/now) 供差分直测，import 复用同一实现
- 官方基准 367 → 371；引擎 200 测全绿

## 最近一轮 17（2026-08-08：BYAF getChatFromScenario 官方差分）

- byaf-chat-official.mjs：逐字提取 src/byaf.js getChatFromScenario + replaceMacros/formatExampleMessages，Date/encodeURI/console 打桩，5 例 fixture 全过
- 差分抓出并修 4 处真差异：
  1. chat_metadata.scenario 官方不做宏替换——原实现错误 replaceMacros
  2. 模型设置/布尔字段官方保留原始类型（字符串不进数字转换）——改 raw 透传
  3. messages 为 null 时开场白不写 send_date（仅空数组才写当前时间）——chatStartDate 语义对齐
  4. 聊天背景来自 chatBackgrounds 参数（name/paths 匹配 + encodeURI custom_background）——新增 ByafChatBackground 参数
- 官方基准 362 → 367；引擎 199 测全绿

## 最近一轮 16（2026-08-08：BYAF 纯逻辑官方差分）

- byaf-macros-official.mjs：逐字提取 src/byaf.js 的 replaceMacros / formatExampleMessages / formatAlternateGreetings / convertCharacterBook，14 例 fixture 全过
- 差分抓出 1 处真差异：formatExampleMessages 官方只跳过 falsy（空串），空白字符串仍输出 <START>——原实现用 isBlank 错误跳过，已改为 isEmpty
- 官方基准 348 → 362；引擎 198 测全绿

## 最近一轮 15（2026-08-08：CharX 角色卡导入官方差分）

- charx-import-official.mjs：逐字提取 src/charx.js CharXParser + characters.js importFromCharX（findZipStart/normalizeZipEntryPath/readFromV2/unsetPrivateFields 也逐字提取），yauzl 用官方同版本 JSZip v3.10.1 等价打桩，5 例 fixture 全过
- 5 例：V3 基础/V2 无 data/带资源（uri 前缀、图标、sprite/background/misc 映射）/嵌套 card.json/SFX 自解压前缀
- 差分抓出并修 5 处真差异：
  1. 官方 importFromCharX 先 sanitize data.name/name（删除非法字符）再 readFromV2——补 CardSanitize
  2. 官方 CharXParser.findZipStart 支持 SFX 自解压前缀——补 PK\x03\x04 定位
  3. 官方 extractFileFromZipBuffer 用 endsWith('card.json')（任意层级）——补任意路径匹配
  4. 资源 zipPath 来自 uri 前缀（embeded:///embedded:///__asset:）+ normalizeZipEntryPath，不是 card 里的 zipPath 字段——extractAssets 重写为官方映射
  5. 官方 mapped assets 包含 missing 文件、icon 单独 pick、baseName 用 sanitize-filename 规则——CharXAsset 增加 name/storageCategory/baseName，data 可空
- 官方基准 343 → 348；引擎 197 测全绿

## 最近一轮 14（2026-08-08：preparePromptsForChatCompletion 官方差分）

- prepare-prompts-official.mjs：逐字提取官方 public/scripts/openai.js preparePromptsForChatCompletion，oai_settings/substituteParams/promptManager 按官方 PromptManager.js 语义打桩，7 例 fixture 全过（默认集合/已知扩展/未知扩展/role-depth 覆盖/override/forbid+disabled/trigger 过滤）
- 差分抓出并修 5 处真差异：
  1. wi_format（`[{0}]` 等）没接入——buildSystemPrompts/preparePromptsForChatCompletion 增加 wiFormat 参数并透传
  2. impersonation_prompt 官方先 substituteParams 再进集合——补宏替换
  3. 官方 new Prompt() 不复制 marker/enabled、injection_order 缺省 100——prepare 返回 marker=false/enabled=true/injectionOrder=100
  4. 官方 preparePrompt 对 marker 也做宏替换——去掉 marker 跳过分支
  5. 系统提示合并后只继承系统提示自身字段（role/injection 覆盖除外），system_prompt 官方 undefined 语义等同 system——mergeSystemPrompts 不再保留集合项的 marker/system_prompt/position/extension 等
- 官方基准 336 → 343；引擎 196 测全绿

## 最近一轮 13（2026-08-08：YAML 角色卡导入官方差分 + sanitize-filename 对齐）

- yaml-import-official.mjs：逐字提取官方 characters.js importFromYaml + convertToV2 + charaFormatData，yaml/sanitize-filename 用官方同版本 npm 包，时间/写盘/文件系统打桩，3 例 fixture 全过
- 差分抓出并修 3 处：
  1. create_date 被函数内 humanizedDateTime 遮蔽，输出本地时间而非官方 ISO——改为透传 now 参数
  2. 官方根字段保留 creator（convertToV2 传入对象原有根 creator）——buildV2FromLegacy 增加 includeRootCreator
  3. 官方 sanitize-filename 默认是删除非法字符（/ ? < > \\ : * | " 与控制字符），不是替换成 _——CardSanitize 重写为 1.6.3 行为（含保留名/去尾部点空格/255 字节截断）
- 官方基准 333 → 336；引擎 195 测全绿

## 最近一轮 12（2026-08-08：populateDialogueExamples 官方差分）

- dialogue-examples-pop-official.mjs：逐字提取官方 populateDialogueExamples，4 例 fixture 全过（basic/空组/空示例/预算截断）
- 验证现有实现与官方一致（identifier `dialogueExamples i-j`、整组预算不足即停、newChat 每组前、role 固定 system、name 字段），无 bug 无需改代码
- 官方基准 329 → 333；引擎 194 测全绿

## 最近一轮 11（2026-08-08：populateChatHistory 官方差分 + 两处真 bug 修复）

- chat-history-pop-official.mjs：逐字提取官方 populateChatHistory，Message/PromptManager/预算/ToolManager 打桩，输出重建后的最终集合结构（集合名 + 消息序列），5 例 fixture 全过
- 差分抓出并修 2 个真 bug：
  1. 历史消息 identifier 官方为动态编号 chatHistory-N（正序）——原来固定 "chatHistory"
  2. continue 模式移出的最后一条消息也要过 preparePrompt 宏替换——原来原样插入
- 官方基准 324 → 329；引擎 197 测全绿

## 最近一轮 10（2026-08-08：Anthropic/Gemini 请求体 1:1 差分 + 历史消息 preparePrompt + 应用图标）

- AnthropicRequestBuilder 1:1：thinking（adaptive/enabled+预算）、tools/tool_choice、json_schema、web_search、beta headers（tools/cache/effort）、采样限制、verbosity、no-prefill assistant→user；官方差分 12 例（逐字提取 sendClaudeRequest 构造段，convertClaudeMessages/预算打桩）
- GoogleRequestBuilder 1:1：generationConfig 全字段（stopSequences/candidateCount/topK/responseMimeType/responseSchema/seed）、thinkingConfig、tools+toolConfig、google_search、图像模态 responseModalities/imageConfig；官方差分 11 例（getGeminiBody 构造段）
- 差分抓出并修：JS 数字 1.0→1 序列化、tool_choice 是对象非字符串、官方 .test 是部分匹配（Kotlin 用 containsMatchIn）、systemInstruction.parts 结构
- ChatHistoryPopulator：每条历史消息过 preparePrompt 宏替换（对齐官方）；ChatHistoryPrepareTest
- 应用图标：用户原图 → mipmap-xxxhdpi/ic_launcher.png，Manifest 引用；删掉手绘矢量
- 官方基准 301 → 324；引擎测试数待全量确认

## 最近一轮 9（2026-08-08：作用域宏补齐 + trimScopedContent 差分）

- 通用作用域宏：{{setvar::x}}content{{/setvar}} → {{setvar::x::content}}（content 默认先求值嵌套宏再 trim+dedent；{{#}} 保留空白；嵌套最外层先处理；未配对 closing 原样），对齐官方 MacroCstWalker.processScopedMacros
- replaceInline 支持宏 flags 前缀剥离（!?~#>），{{#setvar}} 等可正常执行
- trimScopedContent 对齐官方（trimIndent 参数、一致缩进去缩进），官方差分 7 例
- 修复过程抓出 3 个实现 bug（scoped 正则要求 {{ 开头、closing 偏移转全局、宏前文本重复 append）
- 官方基准 294 → 301；引擎 189 测全绿
- 配对逻辑（processScopedMacros）依赖 chevrotain CST 无法逐字差分，源码对照 + 单测

## 最近一轮 8（2026-08-08：差分补课——readFromV2 + parseRegexFromString）

- char-v2-official.mjs：逐字提取官方 characters.js readFromV2（lodash/humanizedDateTime 打桩），5 例 fixture，CharV2DiffTest 全过
- regex-parse-official.mjs：逐字提取官方 world-info.js parseRegexFromString，9 例 fixture，RegexParseDiffTest 全过
- 差分抓出 3 处真差异并修复：
  1. talkativeness/fav 缺失时官方最终不写入（默认值被后续 undefined 赋值覆盖）——原实现错误回填 0.5/false，已改为透传缺失即不写
  2. humanizedDateTime 官方格式为 YYYY-MM-DD@HHhMMmSSsMSms（毫秒3位）——原实现是 "yyyy-MM-dd HH:mm"，已对齐
  3. JS RegExp.source 将 / 序列化为 \/ —— 测试端做 JS 风格转义（语义一致）
- V2Normalizer 旧单测期望（回填 0.5/false）被差分推翻，已按官方行为修正
- 官方基准 280 → 294；引擎 182 测全绿

## 最近一轮 7（2026-08-08：向量扩展补齐——聊天历史重排 + 文件/DataBank 向量化）

- VectorChatExtensions.kt：rearrange（对齐 rearrangeChat）、processFiles/ingestDataBank/injectDataBankChunks/retrieveFileChunks/vectorizeFile（对齐 vectors/index.js）、splitRecursive/trimToStartSentence/trimToEndSentence/overlapChunks（对齐 utils.js）
- VectorStore 新增 querySingle（对齐官方 queryCollection：hashes=topK 不过滤阈值，metadata 按 score>=threshold）
- 扩展提示接入：3_vectors→vectorsMemory、4_vectors_data_bank→vectorsDataBank，position 映射 BEFORE_PROMPT(2)→start / IN_PROMPT(0)→end / IN_CHAT(1)→in_chat
- 边界标注：substituteParams 由宏替换器注入（App 层接 MacroEngine）；summarize/translate_files 未做（P3，官方默认关）；emoji 判定码点近似
- 引擎 179 测全绿；App 接线未动（用户要求引擎先完美）
- 差分补课：vector-utils-official.mjs 提取官方 utils.js 三个纯函数（splitRecursive/trimToEndSentence/trimToStartSentence）生成 14 例 fixture，VectorUtilsDiffTest 全过（含 emoji 用例）；官方基准 266 → 280；引擎 180 测全绿

## 最近一轮 6（2026-08-08：世界书扩展行为全接上 + 差分补课）

- vectorized/RAG：WorldInfoVector.kt（VectorSettings/VectorItem/VectorStore/EmbeddingProvider/InMemoryVectorStore/OpenAiCompatibleEmbeddingProvider/WorldInfoVectorActivation），对齐官方 vectors activateWorldInfo（按 world 分组同步、hash 去重、最近 query 条消息查询、threshold 0.25、max_entries 5、强制激活）
- Scanner 接入 externalActivations：WorldInfoBuffer.getExternallyActivated（对齐官方 buffer），强制激活跳过关键词/概率，扫描结束 reset
- automationId：QuickReplySlot 补 automationId/preventAutoExecute；WorldInfoAutoExecute.resolve + AutoExecuteHandler（prevent 栈）
- displayIndex：WorldInfoEditorSort（custom/priority/default/length，secondary order 降序 + tertiary uid 升序）
- WorldInfoEntry 强类型补 4 字段 + 解析（顶层/extensions 双格式）
- 新增官方差分 2 组 10 例：editor-sort（sortWorldInfoEntries 逐字提取，6 例）、auto-execute（handleWIActivation 选择逻辑，4 例）；差分抓出 length 方向 bug 已修
- 官方持久化核实：vectra LocalIndex 落盘 data/{user}/vectors/{source}/{collectionId}/{model}；已实现 FileVectorStore 对齐（items.json + upsert + 全局 topK 查询），内存库降级测试用
- 修正多集合查询语义：官方 multiQueryCollection 为全局 topK（合并→降序→阈值→topK→分组），InMemory/File 两实现都已对齐
- RAG 全链路无法做纯函数差分（官方依赖 vectra/嵌入服务/浏览器 API），采用逐行对照源码 + 单测锁行为，已在文档标注
- 引擎 172 测全绿

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
- 差分工具 47 个脚本 + 641 例 fixture

## 7. 注意事项

- **兼容层 1:1，UI 层自由**：数据格式、注入算法、宏展开、斜杠行为、导入导出必须与官方互读互通；界面/交互/主题自主（设置与提供商参照命理2 + README）
- 改动先对照官方源码，能 1:1 就 1:1，近似项必须标注
- App 无法本地编译（无 Android SDK），全靠 CI 验证；引擎测试本机可跑
- 推送用 x-access-token；GitHub 网络不稳定，失败就重试；push 不触发 CI，必须手动 dispatch
- apply_patch 在本沙箱被审批策略禁用，文件编辑用 python3 精确改写（注意路径相对 `~/`）
- 删除类操作先确认；大改动保持小步提交
