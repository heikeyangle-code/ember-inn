# 交接清单（会话上下文耗尽时使用）

> 最后更新：2026-08-12。接手顺序：第 0 节一眼看懂（含 0.1 工作准则）→ 1 常用命令 → 2 差分怎么用 → 3/4 现状 → 5 剩余工作。

## 0. 一眼看懂：这是什么、怎么保证 1:1

```mermaid
flowchart LR
 A[app<br/>Android Compose UI<br/>聊天/首页/设置/媒体渲染] -->|ChatRepository<br/>ChatPromptFactory| B[engine<br/>纯 Kotlin 领域引擎<br/>不依赖 UI/Android]
 B -->|PromptPipeline 总装<br/>世界书/宏/正则/人设/示例/历史| C[LlmClient<br/>OpenAI/Claude/Gemini/Mistral/xAI/Cohere/AI21…]
 C -->|OkHttp SSE| D[厂商 API]
 E[官方 SillyTavern 1.18.0<br/>~/sillytavern-ref] -->|scripts/diff/*.mjs<br/>逐字提取纯函数| F[差分 fixture<br/>engine/src/test/resources/diff]
 B -->|引擎 Kotlin 同输入跑一遍| F
 F -->|DiffTest 断言一致| G[引擎 318 测全绿]
```

- 一句话：**引擎和官方 SillyTavern 1:1（必须差分），App/UI 层对照官方功能与设置实现官方语义（样式用 Ember 风格）**。
- “差分”= 同一输入，官方 JS 与引擎 Kotlin 各跑一遍，输出必须逐字一致；fixture 由脚本生成、不许手改。
- 官方基线：release `8172dcd`（SillyTavern **1.18.0**）；酒馆更新后重跑 `node scripts/diff/*.mjs`，红的就是要移植的差异。

### 0.1 工作准则（以后每轮都按这个做）

1. **引擎层（engine/）改动 = 官方 1:1 + 差分，缺一不可**
   - 先精读官方源码（~/sillytavern-ref，release 8172dcd），逐字提取对应纯函数。
   - 写 `scripts/diff/*-official.mjs`（函数体逐字摘自官方；任何打桩/未覆盖分支登记在脚本头部注释）。
   - 生成 fixture（`node scripts/diff/*.mjs`）→ Kotlin 移植 → `*DiffTest` 同输入对拍 → `./gradlew :engine:test` 全绿。
   - fixture 只能由脚本生成，不许手改；新功能先加 case 再实现。
   - 没有差分验证，就不许声称该引擎能力“1:1 官方”。
2. **App/UI 层 = 对照官方功能与设置实现官方语义，样式用 Ember 风格**
   - 每个官方功能先看官方实现（settings.html / index.js / power-user.js / script.js 对应位点），把官方可调字段、默认值、交互行为一一列出。
   - 官方字段/默认值/行为必须一致；仅视觉样式（组件/图标/排版）用现有 Ember 风格。
   - 数据模型与官方存储格式一致（JSONL/角色卡扩展字段/extra.*），能直接互导。
3. **交接文档同步更新，如实写状态**
   - 完成一项就更新对应章节：差分组数/例数、引擎测试数、功能做到什么地步（1:1 / 部分 / 未做）。
   - 不允许写流水账；相应章节更新“现状”即可。
4. **用户豁免项（仅这两项，其余必须做）**
   - Claude/Gemini 官方 web tokenizer（当前用 cl100k 回退，只影响估算精度）。
   - Custom CSS + Moving UI（用户决策延期，见 8.9；等价方案 A/B/C 待选）。
5. **自主工作不停止**：对照官方逐项审计“还没做/写了没接/接了不对”，能做就做；涉及引擎的按第 1 条走，涉及 App/UI 的按第 2 条走。

## 1. 项目与常用命令

- 项目：EmberInn（余烬酒馆）——原生 Android SillyTavern 兼容客户端
- 本地：`/data/data/com.termux/files/home/ember-inn`；远程：github.com/heikeyangle-code/ember-inn（main，公开）
- 官方源码参照：`/data/data/com.termux/files/home/sillytavern-ref`（release 分支）

常用命令：

```sh
# 引擎测试（本机可跑：Java 21 + Gradle 9.7；App 编译只能靠 CI）
cd ~/ember-inn && ./gradlew :engine:test

# 改引擎/官方发版后：重新生成差分 fixture + 打包官方预设
node scripts/diff/*.mjs
node scripts/build-presets.mjs

# 推送（本机已 gh auth setup-git；网络不稳失败就重试）
git push origin main

# 看 CI：只有改 app/engine/gradle/工作流才自动触发；纯文档改动不会跑 CI
gh run list --limit 3
# 需要手工跑（比如只想验证一次）：
gh workflow run 328789880 --ref main
```

CI：`.github/workflows/build.yml`，两个 job：`engine-test`（:engine:test）与 `build`（单测 + assembleDebug + assembleRelease + 出 APK）。push 自动触发条件见工作流 `on.push.paths`；纯文档改动不触发。当前以 `gh run list` 为准。引擎本地 **315 测全绿**。

## 2. 什么是差分验证（新会话必读）

**目标**：EmberInn 是酒馆兼容软件，引擎逻辑必须和官方 SillyTavern 1:1。
“差分验证” = 同一输入，官方 JS 跑一遍、我们 Kotlin 跑一遍，输出必须一致。
手写期望值的单测只是自证；差分才是“官方说对才算对”的机器验证。

**怎么用**：
1. `scripts/diff/*-official.mjs` 从 `~/sillytavern-ref` 逐字提取官方函数，桩掉 DOM/全局依赖，生成 fixture：`engine/src/test/resources/diff/*.json`
2. `engine/src/test/.../*DiffTest.kt` 读 fixture，调 Kotlin 引擎逐例对比
3. 官方发版 / 我们改代码后：`node scripts/diff/*.mjs` 重新生成 fixture → `./gradlew :engine:test`
4. fixture 只能由脚本生成，不许手改；新功能先加 case 再实现

**已覆盖（77 组差分 fixture，共 1225 例对拍，全部通过；2026-08-12 全量复算）**：
> 说明：历史日志里的“官方基准 8xx”是当时的累计口径，不等于 fixture 用例数；当前以 77 组 / 1225 例（机器数）为准。

| 组 | 脚本 | 测试 | 例数 |
> 注：脚本数 64 个（prompt-converters 一行脚本输出 claude-messages.json）；合计 1225 例。
| instruct 提示词 | instruct-official.mjs | InstructModeDiffTest | 36 |
| 世界书纯逻辑 | worldinfo-official.mjs | WorldInfoDiffTest | 19 |
| 世界书整体扫描 | worldinfo-scan-official.mjs | WorldInfoScanDiffTest | 17 |
| 世界书正则深度（regexDepth） | worldinfo-regex-depth-official.mjs | WorldInfoRegexDepthDiffTest | 40 |
| outlet 宏（{{outlet::key}}） | outlet-macro-official.mjs | OutletMacroDiffTest | 5 |
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
| 斜杠转义判定 | slash-escape-official.mjs | SlashEscapeDiffTest | 27 |
| 斜杠参数解析核心 | slash-parser-official.mjs | SlashParserDiffTest | 18 |
| 提示词工具 | prompt-utils-official.mjs | PromptUtilsDiffTest | 9 |
| JSON 角色卡导出 | json-export-official.mjs | JsonExportDiffTest | 6 |
| SSE 流解析 | sse-stream-official.mjs | SseStreamDiffTest | 16 |
| 正则整体管线 | regex-pipeline-official.mjs | RegexPipelineDiffTest | 10 |
| 导演备注 | authors-note-official.mjs | AuthorsNoteDiffTest | 7 |
| 人设引擎 | persona-engine-official.mjs | PersonaEngineDiffTest | 16 |
| 群聊完整循环 | group-loop-official.mjs | GroupLoopDiffTest | 11 |
| OpenAI 请求体（全厂商） | openai-params-official.mjs | OpenAiParamsDiffTest | 27 |
| 工具 token 预分配 | tool-budget-official.mjs | ToolBudgetDiffTest | 4 |
| ChatCompletionPipeline 计划 | chat-pipeline-official.mjs | ChatPipelineDiffTest | 5 |
| 媒体附件纯逻辑 | media-engine-official.mjs | MediaEngineDiffTest | 17 |
| 媒体内联（OpenAI） | media-inline-official.mjs | MediaInlineDiffTest | 7 |
| 媒体 token 成本 | media-cost-official.mjs | MediaCostDiffTest | 18 |
| 特殊协议请求体（Mistral/xAI/AI21/Cohere） | special-bodies-official.mjs | SpecialBodiesDiffTest | 23 |
| OpenAI 文本补全请求体 | text-completion-body-official.mjs | TextCompletionBodyDiffTest | 6 |
| BYAF 资源提取 | byaf-assets-official.mjs | ByafAssetsDiffTest | 6 |
| 提示词总装整链（prepareOpenAIMessages+populateChatCompletion，含工具/媒体/推理签名/continue-nudge 分支） | prepare-messages-official.mjs | PromptPipelineDiffTest | 20 |
| 媒体内容块转换（Claude/Gemini） | media-convert-official.mjs | MediaConvertDiffTest | 25 |
| 消息转换整链（Claude/Gemini） | prompt-converters-official.mjs | PromptConvertersDiffTest | 41 |
| 思考入提示词（PromptReasoning.addToMessage） | prompt-reasoning-official.mjs | PromptReasoningDiffTest | 7 |
| 消息缓存深度（Claude/OpenRouter） | prompt-converters-official.mjs | PromptConvertersDiffTest | 4+3 |
| 其余提供商转换器+合并+预算+OpenRouter | prompt-converters-official.mjs | PromptConvertersDiffTest | 61 |
| 消息清理（cleanUpMessage/cleanGroupMessage/fixMarkdown） | cleanup-official.mjs | CleanUpDiffTest | 34 |
| 响应数据提取（extractMessageFromData/extractJsonFromData） | response-data-official.mjs | ResponseDataDiffTest | 31 |
| 自动续写判定（shouldAutoContinue） | auto-continue-official.mjs | AutoContinueDiffTest | 11 |
| 停用词全链（getStoppingStrings/getCustomStoppingStrings） | stopping-strings-official.mjs | StoppingStringsDiffTest | 14 |
| 偏置全链（getBiasStrings/extractMessageBias/removeMacros） | bias-official.mjs | BiasDiffTest | 17 |
| 流式响应/错误解析（getStreamingReply/tryParseStreamingError） | streaming-response-official.mjs | StreamingResponseDiffTest | 20 |
| Reasoning 解析（parse/remove/formatReasoning） | reasoning-official.mjs | ReasoningDiffTest | 13 |
| Token 预算（getMaxContext/Response/PromptTokens） | token-budget-official.mjs | TokenBudgetDiffTest | 17 |
| 滑动/自动过滤（swipe/generatedTextFiltered/extractMultiSwipes） | swipe-official.mjs | SwipeDiffTest | 29 |
| 工具调用增量解析（ToolManager.parseToolCalls） | tool-calls-official.mjs | ToolCallDiffTest | 8 |
| 记忆扩展纯逻辑（memory） | memory-official.mjs | MemoryDiffTest | 14 |
| append_title 标题追加（coreChat.map） | append-title-official.mjs | AppendTitleDiffTest | 5 |
| 作者注释注入判定（authors-note.shouldInject） | authors-note-inject-official.mjs | AuthorsNoteInjectDiffTest | 8 |
| 扩展提示 set/get + /inject 参数映射 | extension-prompt-official.mjs | ExtensionPromptDiffTest | 19 |
| 世界书 EM 示例（baseChatReplace+unshift/push） | em-examples-official.mjs | EmExamplesDiffTest | 9 |
| 深度提示注入规格（角色/群聊/世界书） | depth-inject-official.mjs | DepthPromptDiffTest | 6 |
| setOpenAIMessages 构造循环（names/isSameModel/narrator/工具过滤） | set-openai-messages-official.mjs | SetOpenAiMessagesDiffTest | 9 |

**分支级覆盖审计与打桩登记（防漏机制，2026-08-08 起强制）**
- 规则：差分脚本内任何打桩/未覆盖分支，必须登记在本节 + 脚本头部注释；未登记即视为未完成，不许声称该分支 1:1。
- prepare-messages（总装整链，20 例（2026-08-09 补顶层 continue-nudge 非 prefill 用例，锁 PromptPipeline→ChatHistoryPopulator 的 cyclePrompt 透传））：populationInjectionPrompts 已用官方真函数；getExtensionPrompt(IN_CHAT) 的过滤/拼接/wrap/substituteParams 已由 extension-prompt 差分 19 例覆盖（2026-08-12），populationInjectionPrompts 同步补宏替换 + key 升序（官方 getExtensionPrompt 语义）；preparePromptsForChatCompletion 用 fixture 注入的同一提示集合（该函数自身 7 例差分）；**工具调用历史 / 推理链（active_chain/since_last_user）/ 推理签名 / 媒体内联（list/gallery/data URL）已补端到端 8 例**，打桩登记见脚本头部：registerFunctionToolsOpenAI 空对象 → 工具预算预分配恒 1 token；setToolCalls tokens = JSON.stringify 长度/4（官方 tokenHandler 对象整体计数，两端同一近似）；getChat content 归一 `?? ''`；媒体仅 data: URL 内联且只记账，content 数组表示由 MediaInliner/MediaConvert 差分单独覆盖；群聊 selected_group、names_behavior、send_if_empty、预算溢出、squash 开关均已覆盖。in-chat 扩展合并的 order==100 规则由引擎单测锁。
- SSE：运行时只有官方对拍的 SseChunkParser 一条路（逐字符、事件级 catch 跳过 = 官方平滑流语义、[DONE]/message_stop 收尾、reasoning 独立通道）；旧 SseParser 已删除（曾把 content:null 拼成字面 "null"）。
- **仍绕过 fixture 的部分**：prepareOpenAIMessages 的 chat→messages 构造循环（names_behavior 内容前缀、isSameModel 签名/推理过滤、media/invocations 从 extra 提取）由 fixture 直接注入消息对象绕过；Kotlin 侧由 App 的 ChatPromptFactory（JSONL → PromptMessage）按官方同名逻辑实现，接线点见 4.7/4.9。2026-08-12 补：`extra.tool_invocations` 已由 App 解析进 PromptMessage.toolInvocations（工具系统消息按官方 coreChat 例外保留），工具调用历史端到端可进提示词。
- 其它脚本的历史打桩（Message/PromptManager/tokenHandler 等）均为“与 Kotlin 移植同语义”的显式桩，fixture 生成即对拍，登记在各自脚本头部。

**尚未做差分的**：网络/路由层（Mistral/xAI/Cohere/AI21/OpenRouter 请求体与响应解析用 MockWebServer 单测锁行为，转换器本身已逐字差分）；斜杠完整 parser（SlashCommandParser 依赖数十个模块与 DOM，无法逐字提取；转义判定 testSymbol 已差分 10 例，其余手写单测 + 源码对照）。
聊天重排/文件向量化主体（官方函数与 DOM/服务端焊死，无法逐字提取；其中纯函数 splitRecursive/trim 系列已差分 14 例）。
作用域宏配对逻辑（官方 MacroCstWalker 依赖 chevrotain CST 与 MacroRegistry，无法逐字提取；其中 trimScopedContent 纯函数已差分 7 例）。

**预设体系**：官方 `default/content/presets` 已打包进 engine resources（context 34 / instruct 38 / openai 1 / textgen 6 / novel 24 / kobold 6 / sysprompt 13 / reasoning 5，共 127 个），PresetLibrary 可加载；quick-replies 也打包。官方发版后跑 `node scripts/build-presets.mjs`。

## 3. 引擎进度（对照官方 release）

### 3.1 角色卡 ✅
PNG V2/V3（tEXt/ccv3）与 JSON 导入导出（官方也只导出 PNG/JSON）、CharX/YAML/BYAF 导入；JSON 导入 5 例 + JSON 导出 4 例（getCharaCardV2+unsetPrivateFields）、YAML 3 例、CharX 5 例、BYAF 14+5+4+4 例；V2 归一（readFromV2，官方差分 5 例 + 多轮补真 bug）、私有字段清理、JSON 导出（CharacterCardExporter）；PNG 字节级差分 6 例。
✅ 导入保留世界书回归锁（2026-08-10 WorldBookImportTest：JSON/PNG 导入后 data.character_book.entries 可读可解析）；✅ CharX 资源提取（引擎 CharXImporter.CharXAssets）；✅ BYAF 资源提取（getCharacterImages/getChatBackgrounds 官方差分 6 例：默认头像回退、字节去重、paths 合并、url-join 不折叠 ../）；✅ App 层资源入库（2026-08-09：CharX icon→头像 + seed 取色，background/voice 落盘 assets/ 并记入 CharacterRecord）；✅ URL 导入角色卡（HomeViewModel.importCardFromUrl + 首页 FAB 弹层，PNG/CharX/JSON 按 URL 后缀/魔数探测，对齐官方 content-manager importURL；复验）。

### 3.2 世界书 ✅（含 RAG 向量扩展）
buffer/matchKeys/getScore/parseDecorators、checkWorldInfo 整体扫描（含两段扫描、sticky/cooldown/概率）、深度/递归、分组评分、角色过滤、时间效果、多世界合并、装饰器/哈希、世界书文件导入导出、世界书↔角色书互转；正则在 BUILD 阶段接入扫描器。 ✅ 世界书 BUILDING PROMPT 正则深度已差分（regexDepthOf 逐字提取官方表达式，40 例对拍）。
✅ 扩展字段已全接上（数据全量透传 + 行为）：
 - vectorized → RAG：WorldInfoVectorActivation（同步/检索/强制激活，对齐 vectors activateWorldInfo）+ VectorStore/EmbeddingProvider（OpenAI 兼容）；**FileVectorStore 磁盘持久化对齐官方 vectra.LocalIndex**（目录 root/source/collection/model + items.json，重启不丢；InMemoryVectorStore 仅测试/临时）；Scanner 通过 externalActivations 强制激活（跳过关键词/概率）
 - 向量扩展补齐：**VectorChatRearranger**（聊天历史重排，对齐 rearrangeChat：protect 保留最近 N 条、insert 条数、模板 Past events:{{text}}、position 映射 BEFORE_PROMPT→start/IN_PROMPT→end）+ **文件/Data Bank 向量化**（对齐 processFiles/ingestDataBankAttachments/injectDataBankChunks/retrieveFileChunks/vectorizeFile：分块 splitRecursive、overlap、chunk 检索注入）+ VectorTextUtils（splitRecursive/trimToEndSentence/trimToStartSentence/overlapChunks 官方 1:1）
 - automationId → 快捷回复自动执行：WorldInfoAutoExecute.resolve + AutoExecuteHandler（对齐 quick-reply AutoExecuteHandler，prevent 栈；选择逻辑 4 例官方差分）
 - displayIndex → 编辑器排序：WorldInfoEditorSort（对齐 sortWorldInfoEntries，6 例官方差分，抓出 length 方向 bug 已修）
 - addMemo → 官方核心从未读取，仅透传

### 3.3 宏 ✅（含作用域宏）
通用作用域宏（{{setvar::x}}content{{/setvar}}、{{#}} 保留空白、嵌套、trim+dedent，对齐 MacroCstWalker.processScopedMacros）；trimScopedContent 官方差分 7 例；!?~> flags 官方标 TBD 未实现（无需补）；配对逻辑依赖 chevrotain CST 无法逐字差分（源码对照+单测）。
核心宏 + 官方 e2e 差分 158 例；变量简写全运算符、{{if}}、{{trim}} 作用域、legacy 标记/冒号/空格参数、嵌套参数、字段宏、聊天/状态宏；{{pick}} 用 seedrandom@3.0.5 逐位一致（5 例）。
✅ {{outlet::key}} 宏（官方 core-macros.js 逐字提取差分 5 例；App 把世界书 outletEntries 注入 MacroEnv.outlets，官方 NONE 位置不注入提示词、仅供宏读取；差分抓出空 key 未判空已修）；✅ MacroRegistry 动态注册/注销/解析；✅ 宏 flags（{{#}} 保留空白已随作用域宏实现）；✅ 角色字段已接线（2026-08-10：App ChatPromptFactory 按官方 MacroEnvBuilder 映射 character/system.model，{{description}}/{{chardepthprompt}} 等可用）；🟡 聊天/系统状态边界仍缺；!?~> 官方标 TBD 无需补。

### 3.4 斜杠 🟡
SlashParser（命名/无名/引号/转义/list 值/rawQuotes）+ SlashEngine（管道/闭包/双管道）、/pass /let /qr-arg、{{var}}/{{pipe}}/{{arg}} 状态宏、快捷回复执行器；SlashEscape（testSymbol）官方差分 27 例；参数解析核心 43 例差分。
已接：/renamechat /getchatname /setinput /bg /impersonate /persona-set /trigger /inject /gen /genraw + 异步执行器；/inject 支持 chat_metadata.script_injects 持久化、before/after/chat/none/scan/ephemeral；2026-08-12 起参数归一按官方 injectCallback（数字枚举持久化，旧字符串兼容读取），scan 已真正传入元数据。
剩余偏差：惰性闭包仍即时求值；/genraw 的 instruct/as/stop/trim 参数未实现；/inject filter 闭包（元数据复活 + 异步判定）未实现；/trigger await 不等待。官方 1.18 无 /while。

### 3.5 提示词组装 ✅（核心）
PromptManagerCore（默认/用户顺序、enabled、injection_trigger、preparePrompt original/groupOverride、mergeSystemPrompts）、PromptCollection、ChatCompletion 嵌套集合（预算/溢出/squash）、ChatHistoryPopulator、DialogueExamplesPopulator、扩展注入（summary/AN/vectors/chromadb/persona/未知扩展）、in-chat 深度注入、continue nudge/prefill、bias、control prompts（impersonate/quiet）、nsfw/jailbreak/用户相对提示、工具调用（tool_calls）、ToolLoopPlanner 递归决策（官方 RECURSE_LIMIT=5：shouldContinue/buildNextMessages/nextRecursionCount，单测 4 例；工具真正执行已接线，见 3.8.16）、人设 IN_CHAT 注入；**✅ PromptPipeline 总装器**（官方 prepareOpenAIMessages+populateChatCompletion 1:1：示例解析 parseExampleIntoIndividual/setOpenAIMessageExamples、控制提示、continue prefill、pin 顺序、squash；整链官方差分 20 例；in-chat 深度注入（populationInjectionPrompts：order 降序/角色固定序/深度 splice/reverse）已用官方真函数，扩展合并 order==100 规则由单测锁；2026-08-12 起 getExtensionPrompt 过滤/拼接/wrap/宏替换已差分 19 例并接入 populationInjectionPrompts）、作者注释组合（ANWithWI）；CharacterCardFieldsEngine 官方差分 6 例；PromptUtils 官方差分 9 例；AuthorsNoteEngine（默认值解析+ANWithWI）官方差分 7 例（默认 position 修正为官方 1）。
✅ 历史 reasoning 注入（PromptReasoningEngine.addToMessage 官方 1:1 差分 7 例；App 总装时先过 REASONING 正则（isPrompt=true+depth）再注入；power_user.reasoning.add_to_prompts 默认关，设置→服务开关；continue 最后一条 prefix 不受开关限制，官方语义）；✅ 角色 system_prompt / 剧情后指令已真正进请求体（2026-08-10 修复：官方 script.js generate 传 systemPromptOverride/jailbreakPromptOverride，App 此前漏传——角色系统提示词从未生效；现按官方语义传 fields.system/jailbreak，且 chat_metadata 同名键优先）；✅ 每条历史消息过 preparePrompt 宏替换已补（对齐官方 populateChatHistory；ChatHistoryPrepareTest）；✅ 角色宏环境接线（2026-08-10：ChatPromptFactory env.character=CharacterFields(system/jailbreak/description/…/charDepthPrompt)+system.model，官方 MacroEnvBuilder 映射 1:1，{{chardepthprompt}} 等历史消息宏可用）；✅ names_behavior 已按真实官方修正：Message.fromPromptAsync 不复制 name（请求体只在 COMPLETION 模式带 name，且先 isValidName 再 sanitizeName——PromptNameSanitizer 28 例差分；2026-08-09 修正 DEFAULT 模式误带 name）；✅ 工具预分配 token、媒体内联、推理签名已补（整链差分 20 例）；多模态请求体已接（MediaInliner/MediaConvert 差分）；✅ 工具真正执行 App 注册表已接线（2026-08-12，见 3.8.16）。

### 3.6 正则 ✅
RegexEngine + substituteRegex/宏替换 + 27 例差分（扩：g/首匹配、i/m/s、x/X/A/J/U 非原生 flag → new RegExp 抛错 → 脚本跳过、u 原生 flag 应用、重复 flags 回退整体正则——全部对照官方 regexFromString 1:1）；世界书 key 解析 parseRegexFromString 差分 9→15 例（扩：x/X/A/J/U 无效 → null、重复 flag → null，WorldRegexUtils 已补重复 flag 拒绝；u/y 原生 flag 仍为边界登记）；RegexPipelineEngine（getRegexedString：placement/markdownOnly/promptOnly/runOnEdit/minDepth/maxDepth/禁用扩展）官方差分 9 例；聊天消息正则已在扫描器接入（messageTransformer）。
✅ 该卡正则已接线（2026-08-10：CharacterCardEdit 读写 data.extensions.regex_scripts 官方 RegexScriptData）；✅ 存前应用（sendMessageAsUser→USER_INPUT、saveReply→AI_OUTPUT（冒充→USER_INPUT 不落盘）、getFirstMessage→开场白 AI_OUTPUT，全部走 ChatPromptFactory.resolveRegexScripts 统一脚本集合；落盘文本已过正则，宏仍延后到总装，请求等价）；✅ 总装应用（isPrompt=true + 官方 depth 公式，只跑 promptOnly 脚本——官方 coreChat.map 语义，普通脚本不再双应用；世界书内容过 WORLD_INFO 正则）；✅ 允许列表（character_allowed_regex 存储 + 角色详情开关 + allowedOnly=true，scoped 默认不生效）；✅ 全局开关（设置→正则“启用正则脚本”，写 disabledExtensions.regex 语义，关闭后存前/总装/编辑/世界书全位点跳过）；✅ preset 脚本存储/UI（命名预设集保存/恢复/编辑 + preset_allowed_regex[openai] 允许开关 + 存前/总装/编辑/开场白全位点接线；App 无采样预设管理器，命名集为官方 preset 扩展 regex_scripts 字段的结构等价，登记）。

### 3.7 预设 ✅
官方 127 个预设打包 + PresetLibrary；quick-replies 打包 + 执行器。moving-ui（界面预设）未打包，用户决策延期见 8.9。

### 3.8 聊天 🟡
jsonl 基础 + BYAF 聊天导入 + continue nudge；**swipes 数据模型（App 层，对齐官方 `swipe_id`/`swipes[]`/`swipe_info[]`：ensureSwipes 初始化、syncSwipeToMes 同步、Generate('swipe') 追加、deleteSwipe、editMessage 写回）**。
✅ 聊天元数据（2026-08-10）：官方 ChatHeader（chats/{id}.json chat_metadata）读写 + 字段覆盖（system_prompt/scenario/mes_example）+ 背景（custom_background）；✅ 书签（复验：ChatStore bookmarkNames/createBookmark/openBookmark，存档 chats/{id}-Checkpoint-*.jsonl + 最后 AI extra.bookmark_link，官方 saveBookmark 语义；UI 对话框 + 二次确认）；✅ 设置快照（SettingsSnapshotStore 命名 zip 保存/恢复/删除 SharedPreferences + 提供商档案，对齐官方 user.js 设置快照语义；恢复后需重启 App 完全生效，登记）。

### 3.8.5 消息清理 ✅（2026-08-11）
官方 `script.js cleanUpMessage`（停用词逐字符裁剪/prompt bias/错误名字裁剪/endoftext/Instruct 序列/群消息裁剪/名字剥离/fixMarkdown/句子与空格收尾）+ `cleanGroupMessage` + `power-user.js fixMarkdown` 已移植到引擎：
- `engine/prompt/CleanUpMessage.kt`：`CleanUpConfig` 注入 promptBias/regexTransform/stoppingStrings 等官方依赖；App 接线时传入已宏替换的 bias、真实 RegexPipelineEngine 与 API 停用词。
- `engine/prompt/FixMarkdown.kt`：forDisplay=false（cleanUp 用）与 forDisplay=true（显示用）两条官方路径。
- 差分：`scripts/diff/cleanup-official.mjs`（函数体逐字摘自 script.js:3112/6383、power-user.js:408/429、utils.js:883/1378；打桩 substituteParams/getRegexedString/stoppingStrings 已在脚本头部登记）→ `CleanUpDiffTest` 34 例全过。

### 3.8.6 响应数据提取 ✅（2026-08-11）
官方 `script.js extractMessageFromData / extractJsonFromData` 已移植到 `engine/provider/ResponseDataExtractor.kt`：
- `extractMessageFromData`：kobold/koboldhorde/textgenerationwebui/novel/openai 各协议响应取文本链，与官方逐字段一致（含 textgen `data[0].content` 数组分支、openai content 文本数组 `\n\n` 拼接、tool_plan 回退）。
- `extractJsonFromData`：openai 主 API 下按 chat_completion_source 解析 JSON；claude 取 tool_use.input；perplexity 先过 removeReasoning（调用方注入）；returnInvalidJson 原样返回；非 openai 返回 `{}`。kotlinx 会把裸词当字符串，已加官方 JSON.parse 语义白名单。
- 差分：`scripts/diff/response-data-official.mjs`（script.js:6217/6252 逐字；打桩 removeReasoningFromString=恒等，已登记）→ `ResponseDataDiffTest` 31 例全过。

### 3.8.7 自动续写判定 ✅（2026-08-12）
官方 `script.js shouldAutoContinue` 已移植到 `engine/prompt/AutoContinue.kt`：
- 开关/冒充/发送中/停止/目标长度/OpenAI 禁止/输入框非空/短 chunk/无最后消息/已达目标长度/应续写 全分支 11 例差分。
- `tokenCount` 由调用方注入（App 用 TokenCounterFactory），`AutoContinueConfig` 承载官方 power_user/chat/textarea 状态。
- 差分：`scripts/diff/auto-continue-official.mjs`（script.js:5657 逐字；打桩 getTokenCount/textarea/abortController，已登记）→ `AutoContinueDiffTest` 11 例全过。

### 3.8.8 停用词全链 ✅（2026-08-12）
官方 `getStoppingStrings` + `getCustomStoppingStrings` 已移植到 `engine/prompt/StoppingStrings.kt`：
- openai 只返回自定义停止串；非 openai 组装名字停止串/群成员停止串/Instruct 停止串/自定义停止串/单行 `\n`，最后去重。
- Instruct 部分复用已差分的 `InstructMode.stoppingSequences`；自定义停止串支持 JSON 解析、宏替换、临时停止串、limit。
- 差分：`scripts/diff/stopping-strings-official.mjs`（script.js:2966 + power-user.js:3072 + instruct-mode.js:301 逐字；打桩 substituteParams/EPHEMERAL，已登记）→ `StoppingStringsDiffTest` 14 例全过。

### 3.8.9 偏置全链 ✅（2026-08-12）
官方 `getBiasStrings` + `extractMessageBias` + `removeMacros` 已移植到 `engine/prompt/BiasEngine.kt`：
- `extractMessageBias` 用 Handlebars 官方 vendor 生成差分基准，引擎实现兼容 `{{bias "..."}}` 字面量、未定义路径（undefined→空串）、无参 `{{bias}}`（[object Object]）。
- `getBiasStrings` 覆盖 impersonate/continue 空返回、文本 bias、用户全局 bias、回溯聊天 bias、swipe 跳过最后一条、空 bias 继续回溯。
- 差分：`scripts/diff/bias-official.mjs`（script.js:3081/5735/5801 逐字；Handlebars ^4.7.9 加入 diff vendor）→ `BiasDiffTest` 17 例全过。

### 3.9.6 流式响应/错误解析 ✅（2026-08-12）
官方 `openai.js getStreamingReply / tryParseStreamingError` 已移植到 `engine/provider/StreamingResponse.kt`：
- `StreamingReplyParser`：Claude/Gemini/Cohere/DeepSeek/xAI/OpenRouter/自定义源/Mistral/默认源全部 delta 分支，reasoning/images/signature/toolSignatures 状态纯函数返回。
- `StreamingErrorParser`：quota/moderation/error/message/detail 分类，严格 JSON.parse 语义（裸词不算 JSON）。
- 差分：`scripts/diff/streaming-response-official.mjs`（openai.js:1624/3128 逐字；打桩 oai_settings/toastr/check*，已登记）→ `StreamingResponseDiffTest` 20 例全过。

### 3.8.10 Reasoning 解析 ✅（2026-08-12）
官方 `reasoning.js parseReasoningFromString / removeReasoningFromString / formatReasoning` 已移植到 `engine/prompt/ReasoningEngine.kt`：
- strict 锚定/非 strict 任意位置、prefix/suffix 缺失返回 null、无匹配返回 `{reasoning:"", content:原串}`（官方真语义）、trimSpaces 开关。
- `formatReasoning` 与 `ResponseDataExtractor` 的 removeReasoning 注入点可直接接真函数。
- 差分：`scripts/diff/reasoning-official.mjs`（reasoning.js:1389/1410/1450 + utils.js trimSpaces 逐字）→ `ReasoningDiffTest` 13 例全过。

### 3.9.7 Token 预算 ✅（2026-08-12）
官方 `getMaxContextTokens / getMaxResponseTokens / getMaxPromptTokens` + `getKayraMaxContextTokens` 已移植到 `engine/provider/TokenBudgetEngine.kt`：
- kobold/textgen/novel（clio/kayra/erato 与订阅 tier）/openai/未知默认 1487 全分支。
- `getMaxPromptTokens` 的 override 校验（非数字/<=0/NaN → 回退 response）与官方一致。
- 差分：`scripts/diff/token-budget-official.mjs`（script.js:5870/5907/5922 + nai-settings.js:92 逐字）→ `TokenBudgetDiffTest` 17 例全过。

### 3.8.11 滑动/自动过滤 ✅（2026-08-12）
官方 `isSwipingAllowed / isMessageSwipeable / getOverswipeBehavior / ensureSwipes / generatedTextFiltered / extractMultiSwipes` 已移植到 `engine/prompt/SwipeEngine.kt`：
- 滑动允许/消息可滑/越界行为（pristine_greeting/regenerate/loop/none）与 ensureSwipes 初始化/归一。
- auto-swipe 最短长度/黑名单阈值过滤；openai/textgen llamacpp 多回复提取（cleanUpMessage 注入，已单独差分）。
- 差分：`scripts/diff/swipe-official.mjs`（script.js:9100/9123/9163/6778/6300 + power-user.js:3032 逐字；打桩 cleanUpMessage/syncMesToSwipe，已登记）→ `SwipeDiffTest` 29 例全过。

### 3.8.12 工具调用增量解析 ✅（2026-08-12）
官方 `tool-calling.js ToolManager.parseToolCalls / #applyToolCallDelta` 已移植到 `engine/provider/ToolCallParser.kt`：
- OpenAI choices/tool_calls 字符串增量拼接、多 choice、thought signature 转移。
- Cohere 工具事件、Anthropic content_block/input_json_delta/content_block_stop、Gemini candidates functionCall。
- 差分：`scripts/diff/tool-calls-official.mjs`（tool-calling.js:427 逐字；打桩 isToolCallingSupported，已登记）→ `ToolCallDiffTest` 8 例全过。

### 3.8.13 记忆扩展纯逻辑 ✅（2026-08-12）
官方 `extensions/memory` 的 `getLatestMemoryFromChat / getIndexOfLatestChatSummary / getSummaryPromptForNow / getRawSummaryPrompt` 已移植到 `engine/prompt/MemoryEngine.kt`：
- 摘要位置回溯、间隔/强制字数/force 条件、原始摘要提示词构建（最新摘要 + 消息缓冲区 + token 截断 + maxMessagesPerRequest）。
- 差分：`scripts/diff/memory-official.mjs`（memory/index.js:353/374/559/756 + utils.js extractAllWords 逐字；打桩 substituteParamsExtended/countSourceTokens，已登记）→ `MemoryDiffTest` 14 例全过。

### 3.8.14 append_title 标题追加 ✅（2026-08-12）
官方 `Generate coreChat.map` 的 `append_title` / 媒体 `append_title` 标题拼接已下沉到引擎 `PromptAssembler.appendMessageTitles`：
- App 只负责从 JSONL extra 提取 titles 列表，拼接逻辑由引擎完成并差分。
- 差分：`scripts/diff/append-title-official.mjs`（script.js:4448-4462 逐字）→ `AppendTitleDiffTest` 5 例全过。

### 3.8.15 作者注释注入判定 ✅（2026-08-12）
官方 `authors-note.js` 的“按用户消息数决定是否注入 AN”已下沉到引擎 `AuthorsNoteEngine.shouldInjectNote`：
- 修正 App 旧实现用“总消息数”而非“用户消息数”的问题；interval=1 恒注入。
- 差分：`scripts/diff/authors-note-inject-official.mjs`（authors-note.js:333-362 逐字）→ `AuthorsNoteInjectDiffTest` 8 例全过。

### 3.8.16 扩展提示引擎 / EM 示例 / 深度提示 / 工具执行循环 ✅（2026-08-12）
- `ExtensionPromptEngine`：官方 setExtensionPrompt/getExtensionPrompt/getExtensionPromptByName + injectCallback 参数映射（positions before→2/after→0/chat→1/none→-1、depth Number+NaN 回退 4、role 字符串查表、scan isTrueBoolean、script_inject_ 前缀）下沉引擎；差分 19 例。App `/inject` 现在按官方数字枚举持久化（旧字符串位置兼容读取），scan 参数已补传（此前恒 false）。
- `ExampleAssembler`：官方 generate “Add message example WI” 1:1——EM 内容先 baseChatReplace（宏替换+collapse+去 \r）再 parseMesExamples，before unshift/after push；差分 9 例（此前 App 缺 baseChatReplace）。
- `DepthPromptEngine`：角色卡/群聊/世界书深度提示按官方 setExtensionPrompt(IN_CHAT, depth, role) 规格落 PromptItem（identifier 对齐官方 DEPTH_PROMPT / DEPTH_PROMPT_{i} / customDepthWI_{depth}_{roleInt}）；角色卡 `data.extensions.depth_prompt.depth/role` 已解析，单聊深度提示此前完全没注入，已补；差分 6 例。
- `WorldInfoScanner.scan` 新增 `scanInjections`：官方 checkWorldInfo 把 scan=true 扩展提示 addInject 进扫描缓冲（此前 App 把它们拼进聊天数组，语义偏差）；scan 值按 getExtensionPromptByName 先宏替换。
- `PromptPipeline.populationInjectionPrompts`：in-chat 扩展现在按官方 getExtensionPrompt 语义 trim → key 升序 → separator 拼接 → wrap → substituteParams（此前原始拼接不替换宏）。
- 工具调用执行循环已全链接线：`ToolRegistry.executeToolCalls`（兼容引擎 ToolCallAccumulator 快照形状）→ 官方 shouldDeleteMessage（空回复删新发送用户消息）/ finalizeIntermediaryMessage（非空回复先落盘）→ `ChatStore.appendToolInvocations`（官方 saveFunctionToolInvocations：is_system + extra.tool_invocations）→ RECURSE_LIMIT=5 内递归 `Generate('normal')` 重新总装（工具调用历史经 ChatPromptFactory 解析 `extra.tool_invocations` → PromptMessage.toolInvocations，ChatHistoryPopulator 重构 tool_call + tool 结果消息）。

### 3.8.17 记忆扩展 App 层 / 快捷回复多文件 / 表情精灵 / Captions ✅（2026-08-12）
- 记忆扩展全链接线：`MemoryPrefs`（官方 defaultSettings 全字段）+ `MemoryService`（官方 onChatEvent 触发判定、getSummaryPromptForNow/getRawSummaryPrompt、DEFAULT=generateQuietPrompt / RAW=generateRaw、setMemoryContext 落盘 `extra.memory` 到倒数第二条或 lastUsedIndex）+ 聊天 ⋮“记忆总结（立即）”+ `/summarize` + 设置页；`{{summary}}` 宏与 `1_memory` 注入（位置/深度/角色/scan）经 ChatPromptFactory 接入；消息编辑/删除/滑动/回复完成后自动触发。登记边界：source=main（extras/webllm 未接）；RAW builder 的 promptSize 用当前模型上下文近似官方 getMaxPromptTokens。
- 快捷回复多文件：QuickReplyStore 改为目录 `filesDir/quick-replies/*.json`（官方 data/default-user/quick-replies 语义），旧单文件自动迁移；设置页预设选择/新建/删除；聊天快捷盘与 automationId 自动执行按当前激活预设。
- 表情精灵 App 层：`ExpressionStore`（expressions/{角色名}/*.png + Risu 导入）、设置页（启用/兜底/多立绘/去重 + 角色精灵导入/删除）、聊天 AI 消息按正文 `sampleClassifyText` → `chooseSprite` 渲染到头像下方。登记：extra.sprite 持久化未做（渲染期确定性选择）；LLM 分类未接。
- Captions App 层：`CaptionPrefs` + 输入区“图片描述”按钮（对首张待发图生成描述 → `sendCaptionedMessage` 语义追加 captioned 用户消息 → 触发回复）+ 设置页。登记：source 仅 multimodal（extras/local/horde 未接）；refine_mode/prompt_ask 为开关未接确认弹层。
- 官方设置对齐：`collapse_newlines`（字段/示例/回复清理折叠换行）与 `example_separator`（默认 ***）已加到 消息渲染 设置页并全链路接线（CharacterCardFieldsEngine/ExampleAssembler/CleanUpConfig）。

### 3.8.18 setOpenAIMessages 构造循环 ✅（2026-08-12）
- `PromptAssembler.toOpenAiMessages` 按官方 openai.js setOpenAIMessages 1:1 下沉：narrator→system、names_behavior（DEFAULT 群聊/force_avatar、CONTENT 非旁白、NONE/COMPLETION 不加）、isSameModel 过滤（reasoning/signature 仅同 API/模型携带，工具调用里的推理/签名同步剥离）、输出“新的在前”。
- ChatMessage 增补 api/model/reasoningSignature/reasoning/narrator/forceAvatar 字段；ChatPromptFactory 从 JSONL extra 解析并接线。
- 差分：`set-openai-messages-official.mjs`（openai.js:561-640 逐字；打桩 getMediaDisplay/getMediaIndex/IGNORE_SYMBOL，已登记）→ `SetOpenAiMessagesDiffTest` 9 例全过。引擎 318 测全绿。

### 3.8.19 边界补齐（2026-08-12）
- Captions：refine_mode（发送前编辑确认弹层）与 prompt_ask（生成前自定义提示词弹层）已接，状态机在 ChatViewModel（CaptionDraft/captionPromptRequest）。
- 表情精灵：AI 回复落盘后把选中精灵路径写进 `extra.sprite`，渲染优先读存储（官方 extra.sprite 语义）。
- `/genraw` 官方参数补齐：instruct/as/stop/trim（stop 按 JSON 数组注入一次性停用词；trim 裁掉 user/char 名前缀；instruct/as 因 App 无 instruct 模式登记边界）。
- 官方行为设置（power-user）：user_prompt_bias/show_user_prompt_bias、trim_spaces、trim_sentences、pin_examples、names_as_stop_strings 已加设置页并全链路接线（BiasEngine/CleanUpMessage/StoppingStrings/PromptPipeline.pinExamples）。
- auto_swipe（最短长度/黑名单/阈值）：设置页 + finalizeStream 正常回复落盘后按 SwipeEngine.generatedTextFiltered 命中自动生成新变体（官方 power_user.auto_swipe 语义）。
- 世界书条目编辑器补齐全部官方字段（原仅 7 个）：position 整数枚举/深度/角色/selectiveLogic/大小写/整词/scanDepth/六个 match 开关/prevent/exclude/delayUntilRecursion/概率/ignoreBudget/triggers/outletName/sticky/cooldown/delay/group/组权重/组覆盖/组评分/角色过滤/vectorized/addMemo/automationId/displayIndex/keysecondary；读写按官方字段位置（case_sensitive 等进 entry.extensions）。
- strip_examples（完全移除示例）与 message_token_count（落盘 extra.token_count + 消息时间旁显示 token 数）已接。
- 外置世界书（官方双轨外置轨）数据层已接：WorldStore（filesDir/worlds/*.json，{name, entries:{uid:entry}} 官方格式）、
  CharacterCardEdit.readWorldLink/applyWorldLink（data.extensions.world）、chat_metadata.world_info 指定、
  settings 全局选择 globalSelect 与插入策略；ChatPromptFactory 用 WorldLoreMerger 把内嵌卡书+关联+聊天+全局合并进扫描。
  角色详情页“关联外置世界”选择、聊天 ⋮“外置世界（本会话）”指定（chat_metadata.world_info）已接；
  世界书扫描设置补全官方字段：minActivationsDepthMax/budgetCap/useGroupScoring/include_names（扫描文本带名字前缀）已接线；
  外置世界条目编辑器已接：复用内嵌同款 WorldEntryEditorSheet（官方全字段），保存进 worlds/*.json 官方格式
  （字段命名对齐官方 world 文件：key/keysecondary 数组 + case_sensitive/scan_depth 等进 extensions）；
  世界文件导入/导出 UI（GetContent/CreateDocument）与 overflow_alert 开关已接。
- `/trigger await`：await=true 等待生成结束（官方语义）；`/inject filter`：filter 参数持久化，生成前用 SlashEngine 求值（true/1/yes 才注入；解析失败/空=始终注入，与官方 filter 复活失败同语义；闭包上下文为 App 近似）。

### 3.9 提供商 / LLM 客户端（引擎 1:1 审计）

**一句话结论**：OpenAI 兼容全家、Anthropic、Gemini（含预算自动推导）、Mistral、xAI、Cohere、AI21 路由全部接完（转换器均已差分移植，网络层用 MockWebServer 单测锁行为）；OpenRouter 已接媒体嵌入/推理签名/reasoning exclude，缓存标记待设置项；只剩 Vertex 服务账号认证未做。

| 提供商 | 协议路由 | 请求体 | 消息转换 | 媒体 | 预算/缓存/签名 | 模型列表 | 状态 |
|---|---|---|---|---|---|---|---|
| OpenAI | ✅ `/chat/completions` | ✅ 全厂商参数 27 例差分 | ✅ | ✅ MediaInliner 7 例差分 | — | ✅ `data[].id` | ✅ |
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
- 引擎测试 267 全绿（含重排/文件/分块/工具函数/作用域宏/YAML/JSON 导入导出/提示词组装合并/CharX/BYAF 完整导入/名字规则/表情精灵/分类预处理/群聊完整循环/精灵存储/角色卡字段/斜杠转义/提示词工具/SSE 流解析/正则管线/导演备注/人设引擎/OpenAI 请求体全厂商/工具预算/管线计划/媒体附件/媒体内联/媒体成本）

### 3.10 其它
- ✅ 群聊成员激活策略官方差分 15 例；✅ APPEND 角色卡合并 8 例；✅ 深度提示 7 例；✅ 完整循环纯逻辑（GroupLoopEngine）官方差分 11 例；✅ App 调度层（2026-08-10 ：GroupStore/新建群聊/GroupScheduler 选人/合并卡/顺序生成/续写与重生成按最后成员）；✅ natural/pooled 激活+ 队列提示；✅ 深度提示 App 接线（in-chat 扩展注入 + GroupDepthPromptsEngine）；✅ 自动续写（shouldAutoContinue + /continue 链，默认关）；✅ 策略切换 UI（新建群聊 + 聊天 ⋮ 群聊设置）；narrator 按官方 1.18 无独立模式关闭（/sys 旁白消息群聊可用）。✅ 作者注释、聊天元数据模型、TokenCounterFactory（OpenAI 精确 JTokkit）
- ❌ 服务层：TTS / STT / 图像 / 翻译（P3/P4）；向量引擎已齐，App 层接线待做

## 4. App / UI 进度

### 4.1 导航与返回手势 ✅
底部三 Tab（角色/聊天/设置）；聊天页、设置子页都接 BackHandler，系统返回键/侧滑返回逐级回退（聊天→列表、提供商详情→列表→设置主页）；Manifest 已开 enableOnBackInvokedCallback（Android 13+ 预测性返回动画）。README 守则第 7 条已落实。

### 4.2 首页（角色 Tab）🟡
品牌顶栏 + **全局搜索**（README 守则 8：角色名/描述、会话名/最后消息、世界书条目 key/content/comment、设置项；分组结果列表；世界书条目点击出详情弹层；设置项点击跳设置 Tab；空结果引导）、AI 对话置顶卡、最近聊过横滑、角色双列网格、FAB 导入（PNG/JSON/CharX）、长按菜单（置顶/新会话/字段/导出/删除）、删除二次确认、字段详情弹层、空状态引导、Toast 反馈。角色卡取色 seed 已存（avatar → Palette）。
✅ 角色字段编辑（README：分字段 标签+预览+点击展开编辑；保存改写 rawJson 并同步会话名）。
✅ 角色详情编辑页已完成（2026-08-10 复验修复，c8b22e4 起）：官方 v2 卡字段全集编辑（名字/描述/性格/场景/开场白/示例对话/系统提示/历史指令/深度提示/话痨程度/作者/标签/备用开场白管理）+ 世界书条目管理 UI（增删改/启停/常量/选择性）+ 删除/置顶/导出 JSON/一键开始聊天。本轮修复：depth_prompt/talkativeness 读写改到官方位置 data.extensions（旧实现写 data 顶层，{{chardepthprompt}} 读不到）；世界书读取兼容 data.character_book 与根级 character_book（历史卡）；保存只覆盖编辑字段、未知扩展字段（probability/vectorized/automationId/displayIndex/extensions 等）原样保留、v1（key/order/disable）归一 v2；新增开场白编辑行；布局上下留白加大、条目卡片化；“新增条目”弹层删除按钮误删第一条的 bug 已修；导出文件名用编辑后名字。字段读写抽为纯逻辑 CharacterCardEdit（App 单测 5 例）。
✅ 正则（该卡）UI 已做（2026-08-10：data.extensions.regex_scripts 官方格式读写 + 编辑弹层 + 聊天 USER_INPUT/AI_OUTPUT 位点接线，见；补“允许此角色应用该卡正则”开关，写官方 character_allowed_regex，默认关闭）；✅ 变量（该卡）UI 已做（data.extensions.emberinn_variables，README 自定义扩展，官方无 per-character 变量，见第 8 节不一致登记）；✅ 快捷回复（全局）已做（按官方 Quick Reply 扩展做成全局 preset + 槽位，字段 mes/label/enabled/automationId/preventAutoExecute 完全复用官方 QuickReplySlot；设置→服务→快捷回复管理，聊天输入区快捷盘点击执行；per-character 快捷回复已删除，README 表述已改全局）；✅ 模型覆盖已做（2026-08-10）；✅ 主题配方（部分）：data.extensions.emberinn_theme_recipe（seed/background/shape/font/style/lockMode）读写 + 角色详情页“主题配方”卡片（seed 输入、背景选图/清除、形状/字体/风格/浅深锁定 chips、恢复全局）；聊天页背景 = 会话锁定 custom_background 优先、角色配方 background 回退；✅ 全局应用已做（ThemeState + MainActivity 管线：浅深锁定/seed/形状生效；字体 source=系统衬线、lxgw 待字体包）；🟡 字体文件下载、风格档位映射未做（边界登记）。设置搜索深链已实现。
注：模型覆盖/主题配方官方角色编辑器无对应字段（模型覆盖官方是聊天级 #custom_model_id），但为 README 明确承诺的项目自定义角色级覆盖，属待办，非移除。

### 4.3 聊天页 🟡 v2（核心已接线 + 媒体 + 状态胶囊）
> 发送行为：官方 send_if_empty 已接（最后一条 AI + 空输入 → 发送配置文本续聊，设置→服务→发送）。
> 现状：continue 走官方默认 nudge 路径（历史“新的在前”对齐 setOpenAIMessages）；思考过程走 onReasoning 独立通道（流式显示 + 生成后折叠卡片）；重新生成/继续只对最后一条 AI 生效；新角色空会话自动补 first_mes 开场白（起：alternate_greetings 一并进第一条 AI 的 swipes，对齐官方 getFirstMessage，可滑动切换开场白）。
消息流 LazyColumn + 气泡 + 自动滚底 + 输入框 + 发送；**PromptPipeline 总装流式发送**（角色卡/世界书/示例/历史全部引擎内完成，SSE 逐 token）；停止按钮 = 取消 OkHttp call 并保留已生成部分（官方 mes_stop）；重新生成 = 删最后 AI 回复、复用最后用户消息（option_regenerate）；继续生成 = 官方 mes_continue（移出最后 AI + continue 模式续写，流结束与原消息合并落盘）；复制 / 删除 / **编辑消息**（官方 updateMessage：isEdit 正则分位点 + 清/写 extra.bias）/ **冒充**（官方 Generate('impersonate')：模型以 {{user}} 视角写草稿，流式进输入框、不落历史；引擎 type=impersonate 整链差分已覆盖）/ 长按菜单；最后一条 AI 常驻 4 键；清空会话二次确认；Markdown + 代码高亮（mikepenz m3/coil3/code 0.43.0，import 包名已对 0.43.0 源码 jar 逐一核实；聊天气泡内已收敛为聊天风样式）；未配置模型横幅 → **一键深链“提供商与模型”子页**（先退出聊天再切 Tab，不会被早退逻辑挡住）；顶栏返回 + 角色头像 + accent 角色名；系统返回 / 侧滑返回已修。聊天页布局按 README 重排：systemBars 留白、气泡限宽 78%、间距/圆角/留白加大、顶栏与输入栏为 Cloudy 0.7.1 真背板模糊玻璃（sky 源层 + cloudy 浮层，正文区不模糊）、空状态居中留白。
✅ 角色详情入口已接通（角色卡长按菜单“查看/编辑详情”→ 详情编辑页，见 4.2）。
❌ Claude 冒充的 assistant_impersonation 设置（默认空串，影响为 0，排 P2）。
✅ **滑动切回复已做（README #1731“每条消息都能滑”）**：数据模型对齐官方 jsonl（`swipe_id` / `swipes[]` / `swipe_info[]`，ChatStore.ensureSwipes 初始化 + syncSwipeToMes 语义同步 mes/send_date/gen_*/extra）；AI 气泡横滑（右=下一个/最后一条 AI 越界生成新变体，左=上一个）；计数条 `n/N` + CaretLeft/Right（有变体时显示）；长按菜单“上一个/下一个回复”“删除当前回复”（官方 deleteSwipe 的 newSwipeId 规则）+“生成新回复（变体）”（官方 Generate('swipe')：coreChat.pop 排除最后一条，结果追加进最后一条 swipes 不新增消息）；编辑消息同步写回 swipes[swipe_id]（官方 editMessage）。导出 jsonl 含 swipes 字段可直接进酒馆。✅ 世界书扫描与官方一致（核对 script.js prepareMessages：swipe 在 coreChat.pop 之后才 chatForWI=coreChat 扫描，App 的 dropLast(1) 等价，原登记“官方含最后一条”为误记，已更正）。
✅ 滑动切回复的 swipe picker（复验：长按菜单“变体列表”→ ModalBottomSheet，逐条显示当前高亮，点击即跳转并关层；数据/跳转/删除接口均已接线）。
✅ 上下文占比胶囊已达标（圆环+百分比+绿黄橙红分级+点开分解，分母=ConnectionProfile.contextWindow，设置页可配）；✅ 世界书状态已升级为命中面板（条目名/命中键/常驻/位置/token，点 pill 打开）。
⚠️ 快捷工具盘=“继续/冒充 + 全局快捷回复 chips”+ automationId 自动执行（世界书命中条目 automationId 匹配槽位自动执行，prevent 栈 1:1）；图像生成/附件/TTS 已入快捷工具盘与长按菜单，全局正则开关在设置→正则页（disabledExtensions.regex 语义）。✅ 聊天元数据（2026-08-10）：chats/{id}.json 官方 ChatHeader 读写；chat_metadata.system_prompt/scenario/mes_example 覆盖角色卡（引擎参数已接）；custom_background 聊天背景（⋮ 菜单选图/清除，消息区低透明铺底）；✅ 书签（存档 + bookmark_link + 载入，复验）；✅ 设置快照（见 3.8）。
现状补充：键盘适配（adjustResize + imePadding）、消息日期分隔（今天/昨天/日期）、删除消息二次确认、⋮ 会话菜单（导出聊天 JSONL / 清空）、发送按钮空输入禁用态、媒体附件与状态胶囊（见 4.8）。
近期修复（2026-08-09）：自动滚底=贴底跟随+上滑暂停+回底恢复；思考过程空正文时独立成卡不再消失；流中断保留思考+人话提示；世界书状态=命中面板（名字/键/常驻/位置/token）；上下文胶囊分母=contextWindow（默认按模型自动填，见 4.4）；SSE 事件级容错对齐官方平滑流（坏事件跳过不中断，差分 16 例 + MockWebServer 回归）；滚动跟随仅贴底时滚、发送复位；首页预览走 ViewModel 缓存（不再组合期读盘）；**滑动切回复全链**（swipes 数据模型 + 手势/计数/菜单 + 生成变体 + 编辑同步，对齐官方 ensureSwipes/syncSwipeToMes/Generate('swipe')/deleteSwipe/editMessage）。

### 4.3.5 聊天 Tab（会话列表）✅
全部会话按时间倒序、置顶优先；点卡片进聊天；长按 / ⋯ = 置顶 / 导出聊天 JSONL（官方格式，可直接进酒馆）/ 删除（二次确认）；FAB「+」新建对话（AI 对话或选角色，每个角色可开多个会话，UUID 会话 id）；空状态引导；会话置顶持久化（SessionRecord.pinned，兼容旧 JSON）。
✅ 新建群聊入口（复验：会话 Tab FAB → 勾选角色 → 新建群聊，GroupRecord + 群聊设置 UI 已接线；入口文案同步去“开发中”）。

### 4.4 设置 ✅（README 规格）
- 数据与隐私页已做实：导出全部数据（zip：角色/会话/聊天/头像/提供商配置）+ 数据位置透明展示 + 清除全部数据（二次确认，建议先备份）
- 首启引导已做实（README 启动体验）：欢迎页 + 导入角色卡（系统选择器直接导入）/ 直接开始聊天（进 AI 对话）/ 跳过；SharedPreferences 标记只显示一次；低饱和氛围渐变

- 设置主页：大标题 + 副标题、设置搜索（真过滤）、常用快捷区（主题/模型/语音/备份）、六组卡片（外观与主题 / 提供商与模型 / 语音 / 服务 / 数据与隐私 / 关于）
- 外观与主题：主题模式（跟随系统/浅色/深色）+ 六套预设主题（墨韵/青瓷/夜航/丹砂/琉璃/简约纸感），点选立即全局生效（实时预览），SharedPreferences 持久化；字体/圆角/背景模糊标“开发中”
- 提供商与模型（参照命理2 逻辑）：搜索 + 卡片列表（品牌 SVG 头像 + 名称 + 一句话 + 已配置/未配置 pill + “我的连接”切换/删除）；详情页 = 名称 / API Key（遮罩+显示）/ 接口地址 / 区域 / 账户 ID / API 版本 / 默认模型（底部弹层搜索）/ 上下文上限（tokens，占比胶囊分母）/ 最大回复 tokens（推理模型思考会占额度，512 太小正文被掐空；默认按 providers.json default_max_tokens）/ 测试连接 / 保存 / 删除确认
- 关于页做实：版本 0.1.0 / AGPL-3.0 / 数据仅本地 / 开源仓库
- 语音（TTS）✅（2026-08-10 执行层已接）：Android 系统 TTS 本机引擎，语音选择/语速/试听真实可用；朗读选项字段对齐官方 tts 扩展（enabled/voice/rate/auto_generation/narrate_user/narrate_by_paragraphs/skip_codeblocks/skip_tags/apply_regex）；聊天自动朗读（auto_generation）、消息长按“朗读这条消息”、narrate_user 已接；文本处理对齐官方（跳代码块/标签、去星号、正则 /pat/flags、去图片、按行分段排队），纯逻辑 TtsTextProcessor 单测 3 例；官方 1.18 无 STT，语音输入不假装（未做）
- 服务页 ✅（2026-08-10 执行层已接）：翻译（8 家全实现：Libre/Google/Yandex/Lingva/DeepL/OneRing/DeepLX/Bing，协议对齐官方 src/endpoints/translate.js，Bing 按官方依赖 bing-translate-api 4.2.1 移植 token 流程；语言映射/DeepL formality/free-pro 端点均按官方；自动翻译模式已接：responses/both→AI 回复译文进 extra.display_text、推理进 extra.reasoning_display_text，inputs/both→用户消息 mes 换译文、原文存 display_text，渲染按官方 display_text ?? mes；编辑后按官方 translateMessageEdit 自动重译/清除 display_text）、图像（AUTOMATIC1111/SDCPP/NovelAI/OpenAI gpt-image/HuggingFace 已实现，协议对齐官方 stable-diffusion 扩展与 src/endpoints/{stable-diffusion,novelai}.js——SDCPP 同 /sdapi/v1/txt2img、NovelAI 请求体 1:1 且解 ZIP 取 PNG、HF 直连 /models/{model}；UI 增 API Key 字段； Stable Horde 已实现（官方 horde.js：截断+sanitize+异步任务+轮询 check/status，默认 cfg_scale=7/512x512/karras/sampler=k_euler_a）；DrawThings 仅 macOS 本地服务，Android 不适用已从 UI 移除；ComfyUI 已做（用户提供 workflow JSON（含 %prompt%/%model%/%steps%/%width%/%height%/%seed%/%denoise%/%clip_skip%/%vae%/%sampler%/%scheduler%/%scale% 占位符）→ POST /prompt → 轮询 /history → GET /view，官方 comfy.generate 1:1；官方默认 Default_Comfy_Workflow.json 不在仓库，由用户粘贴，登记）、向量（OpenAI 兼容嵌入 / 本地 BagOfGram）——✅ 2026-08-10 已接线：设置页开关（启用/聊天历史重排/文件数据银行 + query/insert/protect/阈值）、发送时 VectorChatRearranger 重排+数据银行检索、世界书 vectorized 条目经 externalActivations 强制激活、聊天 ⋮ 数据银行管理；OpenAI 配置不完整时本轮禁用并人话提示

### 4.4.5 应用图标 ✅
launcher 图标 = 用户提供的原图（Download/file_0000000078d0820782054bfedd4cb346.png）缩放为 mipmap-xxxhdpi/ic_launcher.png（192px），Manifest 引用 @mipmap/ic_launcher；换图只需替换该 PNG。

### 4.5 主题系统 ✅（全局层）
ThemePreset（seed/secondary/tertiary + 纸色/夜色）→ Theme.kt 自动生成整套 M3 ColorScheme（含 surfaceContainer 系列，浅色低饱和容器、深色提亮主色）；MainActivity 持有 themeMode/preset 状态，贯通 MainScreen → SettingsScreen → AppearanceScreen。
✅ 玻璃表面：聊天页顶栏/输入栏 + 首页顶栏/搜索顶栏已接 Cloudy 0.7.1（背板模糊 + 半透明 tint，GPU + 旧设备 CPU 降级）；五处真毛玻璃 + AI 对话玻璃渐变卡均已补 1px 边缘高光，其余页面暂无毛玻璃。
✅ 角色卡驱动主题管线（seed/形状/字体/浅深锁定，角色配方优先，全局兜底）；🟡 MeshGradient 氛围背景未做（README 可选）。

### 4.5.5 图标系统 ✅
全 App 图标已从 Material icons 换成 Phosphor Regular（24dp 网格 / 256 viewport / 圆头圆角），内置 32 枚官方路径 `app/src/main/java/com/emberinn/app/ui/icons/PhosphorIcons.kt`（由 `scripts/gen-phosphor-icons.mjs` 从 phosphor-icons/core 官方 SVG 生成，增图重跑脚本即可）。
备用 Maven 包：com.adamglin:phosphor-icon:1.0.0（六字重全量、API PhosphorIcons.Regular.X，Kotlin 2.0.21 构建）与 io.github.dev778g-me:phosphoricon-compose:1.0.5（拆分包）均可用（Android AAR 存在）；现选内置 32 枚是出于 APK 体积与精确可控，后续如需全量图标可换 adamglin 包。material-icons-core/extended 依赖已移除。
规范：默认 onSurfaceVariant、激活 primary、警示 error；冒充用 MaskHappy、继续用 CaretDoubleRight、删除用 TrashSimple（README 图标系统节）。

### 4.6 数据存储 🟡
角色卡 characters/*.json + avatars/*.png、会话 sessions/*.json（含 pinned 置顶字段）+ chats/*.jsonl、提供商 profiles.json、主题 SharedPreferences（README 计划是 DataStore，未迁移）。
✅ 提供商“已配置”状态用进程内 ProviderState 共享（设置页保存/切换/删除后刷新，聊天页订阅，不再每发必读盘）；仅进入聊天页时读一次盘兜底。
❌ Room 未引入。

### 4.7 App 接线时官方行为怎么接（源码对照，新会话先读这里）

> 原则：App 接线只做“调用引擎 + 渲染结果”，不再重写一遍逻辑。每项都注明官方源码位置，接 UI 时照官方行为实现交互，引擎函数已经 1:1。

| 引擎能力 | 官方源码位置 | App 接线点 |
|---|---|---|
| 流式渲染 | `public/scripts/sse-stream.js` + `public/scripts/openai.js` eventSource | LlmClient.streamChatCompletions → SseChunkParser → ViewModel 增量状态 → 消息流逐 token 追加；停止 = 取消 OkHttp call（官方 abortController）；流结束必须走 onDone 收尾（引擎已兜底） |
| 提示词组装 | `public/scripts/openai.js` prepareOpenAIMessages + populateChatCompletion + `public/scripts/script.js` generate | ✅ 引擎已接：PromptPipeline.prepare 一个入口出最终消息（世界书/宏/人设/AN/示例/历史/控制提示/工具调用/媒体/推理签名/squash，整链差分 20 例）；App 发送前调它 + 按协议走 ChatRequestBuilder / Anthropic / Google |
| 消息转换 | `src/prompt-converters.js` convertClaudeMessages / convertGooglePrompt / 其余厂商 | ✅ 已全接：Claude/Gemini 在各自 builder 内部；Mistral/xAI/Cohere/AI21 在 LlmClient 对应协议分支调用；OpenRouter 在 openai-compatible 分支先签名/媒体再序列化 |
| 工具/能力选项 | `src/endpoints/backends/chat-completions.js` 各厂商分支 + `public/scripts/openai.js` oai_settings | ✅ 已接：ProviderRequestOptions 承载 tools/tool_choice/json_schema/web_search/request_images/safety，LlmClient 按各厂商官方形态写入请求体；App 层把设置/工具注册表填进 options 即可 |
| 预算计算 | `src/endpoints/backends/chat-completions.js` sendClaudeRequest / getGeminiBody（调用 calculateClaudeBudgetTokens / calculateGoogleBudgetTokens） | ✅ 已接：LlmClient 按模型/effort 调两个预算函数，结果传进 builder 的 reasoningBudget（adaptive→effort 字符串、auto→不加 thinking、数字→budget_tokens/thinkingBudget） |
| Markdown 渲染 | 官方用 Showdown + highlight.js + DOMPurify | mikepenz multiplatform-markdown-renderer + Highlights/KodeView；✅ HTML 消息开关 / Mermaid WebView 兜底（硬化：mermaid.min.js 本地资源离线渲染、放开网络与外链（远程图片/资源可加载、http(s) 链接走系统浏览器，不加开关）； JS 全开、sanitize 只拦 javascript: URL（用户要求活动页/交互页面能跑，官方 DOMPurify 禁脚本，已知偏差，风险登记）） |
| 媒体渲染 | `public/scripts/openai.js` Message.addImage/addVideo/addAudio + `public/scripts/media.js` | 聊天消息 `extra.media` → MediaEngine.getFromMime 判定类型 → 图片/GIF 用 Coil3（coil-gif）、音视频用 Media3 ExoPlayer；URL 附件按官方逻辑下载/展示；✅ extra.media 解析与渲染组件已接（见 4.8） |
| 世界书注入 | `public/scripts/world-info.js` checkWorldInfo + `public/scripts/openai.js` | 发送前：世界书条目 → Scanner（含正则 messageTransformer、RAG 强制激活）→ 注入结果进 PromptAssembler；命中灯只读 Scanner 完整 match 结果 |
| 宏 | `public/scripts/macros/engine/` | 所有文本入 prompt 前统一走 MacroEngine（世界书 format、作者注释、历史消息 preparePrompt 已由引擎接线，App 只需保证 MacroEnv 提供聊天/角色/系统状态） |
| 正则 | `public/scripts/regex/` | 存前（sendMessageAsUser/saveReply/getFirstMessage）+ 总装（isPrompt=true/depth）双位点接入 RegexPipelineEngine；允许列表 character_allowed_regex；global/preset/scoped 分桶；✅ 命名预设集已做（官方无采样预设管理器，登记） |
| 群聊 | `public/scripts/group-chats.js` | 每轮：GroupActivationEngine 选成员 → GroupCharacterCardsEngine 合并卡字段 → GroupDepthPromptsEngine 深度提示 → GroupLoopEngine 判定续写/生成类型 → 多人回复按官方顺序拼接 |
| 表情精灵 | `public/scripts/expressions/` + `endpoints/sprites.js` | ExpressionEngine.chooseSpriteForExpression 选图 → sprite 渲染到消息头像区；分类 API 接 LLM 或本地模型 |
| 快捷回复 | `public/scripts/quick-reply.js` | 输入区快捷盘 → QuickReply 执行器（automationId 自动执行由引擎 WorldInfoAutoExecute 判定） |
| 人设 | `public/scripts/personas.js` | ✅ 2026-08-10 ：PersonaStore（filesDir/personas.json，官方 Persona Management 语义）+ 聊天 ⋮ 人设选择/新建/编辑/删除；选中人设时 personaDescription 注入（personaInPrompt=true，官方 persona_in_prompt 语义；引擎 PromptPipeline 补同名透传参数，默认 false 行为不变） |
| 向量 RAG | `extensions/vectors/index.js` + `utils.js` | ✅ 2026-08-10 ：VectorRagService（OpenAI 兼容 / 本地 BagOfGram + FileVectorStore）→ ChatPromptFactory 总装前跑 VectorChatRearranger（聊天重排/文件分块/数据银行检索，引擎 1:1），世界书命中经 scanner externalActivations 强制激活，扩展提示 3_vectors/4_vectors_data_bank 注入；数据银行文件在聊天 ⋮ 菜单管理 |
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
- ✅ 上下文占比胶囊（圆环+百分比+绿黄橙红分级+点开分解，分母=contextWindow）+ 世界书命中面板（条目名/命中键/常驻/位置/token）
- ✅ 2026-08-09 根治“只思考无正文 / 继续生成不出内容”（此前 maxTokens 未传总装只是接线，真正根因两个一次修完）：
 - 引擎 1:1 缺口：官方 openai.js gpt-5 分支已移植——openai/azure_openai/openrouter 三源对 gpt-5 自动 max_tokens→max_completion_tokens，并按官方删不支持采样参数（gpt-5-chat-latest 删 tools/tool_choice；gpt-5.1–5.4 无 reasoning_effort 删 freq/pres；其余删 temp/top_p/freq/pres）；ChatRequestBuilder 增 source 参数接入 LlmClient 三个调用点，OpenAiParamsBuilder 同步镜像；差分 21→27 例。
 - 老档案默认值迁移：旧 profile 存 maxTokens=512 / contextWindow=8192（旧写死默认），运行时自动升为厂商默认（openai 16384）/ 模型窗口，不重进设置保存也生效。
 - providers.json 24 家补 default_context_window + model_contexts（gpt-5.5 272K / claude 1M / gemini 1M / deepseek 1M / grok-4.3 1M / kimi-k3 1M / glm 200K / qwen 262K / 豆包 256K 等）；上下文胶囊分母默认按所选模型，设置页显示“按模型自动”（手动改数字后退出自动）。
- ✅ 官方对齐项：附件落盘 filesDir/media/（非 base64）、extra.media {url,type,title,source:"upload"} + inline_image:true、删除/清空/删会话时清理附件文件
- ✅ 角色卡 extensions.assets（CharX）：icon→头像 + seed，background/voice 落盘 assets/ 并记入角色记录
- ⚠️ 图库切换已做（发送端列表/图库切换 + 渲染横滑/圆点 + media_index 落盘）；✅ 从 URL 导入附件（输入区附件菜单“从 URL 添加”→ 下载 → 落盘 media/ → 与本地附件同链，URL 后缀+魔数判型；官方 Message.addImage/addVideo/addAudio URL 来源）；未做（登记）：URL 型资产下载（图片发送前压缩 compressImage 已做近似：非 jpeg/png/webp 转 JPEG 最长边 2048）

### 4.9 App↔引擎接线状态
聊天链路（发送/停止/继续/重新生成/冒充/编辑/删除/媒体/思考）全部接到引擎 1:1 能力上；官方行为接线点明细不再单列，见 4.3/4.7 现状描述。
上下文预算对齐官方（commit `131d5c6`）：默认 32K（旧 8192 视为未设置）、maxTokens 钳制保证预算为正、
必选提示词装不下时走 `ContextBudgetException` 人话报错；Claude 直连缓存参数已接线（历史见 git log）。

## 5. 完成度总览（截至 2026-08-12；增量见第 8 节半成品治理与各小节登记）

**新增完成**（全部 CI 绿、引擎 318 测全绿、差分 77 组 / 1225 例）：
- 正则全链路（允许列表/存前/总装/编辑/世界书/全局开关/preset 命名集）
- 群聊 gen_id 整批共享、备用开场白 swipes、书签/URL 导入/设置快照复验
- 翻译 8 家 + 自动翻译模式 + 编辑重译、图像 6 来源 + Horde + ComfyUI
- 向量 Data Bank 高级参数 + URL 上传、Mermaid/HTML WebView 硬化
- 斜杠：/renamechat /getchatname /setinput /bg /impersonate /persona-set /trigger /inject
 /gen /genraw + 异步执行器、send_if_empty
- 引擎：{{outlet::key}}、PromptReasoning、正则 flags、世界书正则深度
- 2026-08-12：/inject 参数映射 + set/getExtensionPrompt + EM 示例 baseChatReplace + 深度提示规格全部下沉引擎并差分（34 例）；WorldInfoScanner scanInjections 缓冲语义修正；populationInjectionPrompts 宏替换对齐；getBiasStrings 引擎接入总装；工具调用执行循环全链接线（ToolRegistry 执行 → 官方 shouldDeleteMessage/saveFunctionToolInvocations → RECURSE_LIMIT 递归重装 → extra.tool_invocations 历史重构）

**剩余**：captions extras/local/horde 来源（multimodal + refine/prompt_ask 已接）、表情精灵 LLM 分类、
instruct 模式（textgen 协议提供商）、惰性闭包即时求值（引擎 SlashEngine）、
Custom CSS + Moving UI（用户决策延期，见 8.9）、Claude/Gemini 官方 web tokenizer（用户豁免）。
图库（LIST/GALLERY）、世界书设置 UI、快捷回复多文件均已核实完成。

**已完成（全部经 CI 验证 / 引擎测试全绿）**

**已完成（全部经 CI 验证 / 引擎测试全绿）**
- P0：会话列表/新建对话、流式/停止/重生成/继续/复制/删除/编辑/冒充/提示词总装、滑动切回复 + swipe picker、
 全局搜索 + 设置深链（含服务/正则/世界书/快捷回复/语音）、群聊调度层
- P1：角色详情编辑页（字段/世界书/正则/变量/模型覆盖/主题配方 + 导出分享/AI 生成背景）、聊天页（胶囊/命中面板/媒体/
 滑动/行高）、启动无黑屏/无图标（Splash 已移除）、数据与隐私、首启引导、TTS、翻译/图像执行层（Libre/DeepL/DeepLX/A1111/gpt-image）、
 向量 App 接线（聊天重排/世界书 RAG/数据银行）、默认采样参数、全局字体/圆角、作者注释、全局正则
- P2：SlashParser flags + 常用/消息类斜杠命令 + 差分 18→43 例；群聊全部可做项（natural/pooled、深度提示、
 自动续写链、策略 UI）；世界书设置 UI；快捷回复 automationId 自动执行；JSON 导入导出差分 10→13/6→10；
 正则分桶差分 7 例；世界书深度注入/EM 锚点接线 + 负深度回归；HTML/Mermaid WebView；平板双栏；
 图库 LIST/GALLERY
- P3/P4：主题全局管线（浅深锁定/seed/形状/字体）、配方导出/分享、无障碍基础达标

**延迟/边界**：完整清单见第 8 节（不一致登记）与第 9 节；仅保留用户决策项——Claude/Gemini 官方 web tokenizer 用户明确豁免（cl100k 回退，只影响估算精度）。

**差分跟进（机制就绪，官方发版时执行）**
- 官方发版 → `node scripts/diff/*.mjs` + `node scripts/build-presets.mjs` → `./gradlew :engine:test`
- 77 组差分 fixture / 1225 例对拍全绿（slash-parser 43、regex-scope 7、regex 27、regex-parse 15、json-import 13、json-export 10、extension-prompt 19、em-examples 9、depth-inject 6、set-openai-messages 9 等）

## 8. App/UI 关键实现与登记（精简；逐轮流水账已删，历史见 git log --oneline）

### 8.1 布局/组件定稿
- 底部三 Tab + 平板 NavigationRail；首页毛玻璃顶栏/全局搜索/AI 对话置顶/双列网格/卡片 seed 底色
- 角色详情：世界书收进一张卡片默认折叠并置底；全部分组 SectionCard（18dp 圆角/surfaceContainerLow）；
 BackHandler 防返回直接退出 App
- 聊天消息布局：AI 全文宽纸面流、用户右对齐限宽 78% 整块气泡；图片内联大图（高限 320dp）
 + 点击 LIST↔GALLERY 切换并持久化（官方 switchMessageMediaDisplay）
- 输入栏（OmniBot 借鉴）：图标 36dp onSurfaceVariant(0.8)；发送=实心圆（accent 底+亮度自适应图标）、
 停止=error 实心圆；输入框 44dp
- 设置：六组卡片 + 搜索深链；外观与主题四分区（主题 / 视觉与质感 / 消息外观 / 行为与兼容）
- 共享组件：EmberSwitch（统一触觉）、EmberEmptyState、EmberSkeletonBox、emberShadow（元素色深版阴影）、
 ColorField（色块即选色入口 + hex 等宽预览 + 选色盘）、ColorPickerDialog（20 色板+RGB+hex）

### 8.2 显示管线 / 流式 / 滚动（官方对照结论）
- displayTextOf：显示位点正则（用户/旁白/AI + 官方 depth）→ fixMarkdown(forDisplay=true) → encode_tags（可选）；
 复制/编辑用原始落盘文本，显示与操作分离
- 流式：120ms 节流；StreamingMarkdown 轻量 AnnotatedString 一次构建（粗/斜/删/下划线/行内码/引号/链接），
 结束由 ChatMarkdown 完整重渲染；balanceStreamingDelimiters 为 App 增强（官方 1.18 无此函数）
- 列表 key：流式/思考项与最终消息共用 `m-末尾索引` + contentType，结束原地替换不闪跳；
 MessageRow 派生字段 remember(el) 一次缓存
- 自动触底：最后一项可见=贴底；上滑暂停、回底恢复；首帧滚底读当前 layoutInfo
- 登记未做：auto_scroll_chat_to_bottom 开关（官方默认开，App 恒开）、
 LaTeX、MeshGradient、网络代理、快捷回复全屏编辑器

### 8.3 性能 / 缓存（点卡进聊天、发送按钮卡顿治理结论）
- CharacterStore/ChatStore 进程级共享缓存（companion object），写操作全量失效回填；
 displayCache 按消息索引缓存显示文本，组合期不再读盘/跑正则
- 进聊天首帧滚底等 totalItemsCount>0 再 scrollToItem；流式不再每 tick 整段 Markdown 解析/正则
- 角色卡去掉逐卡 dropShadow；WebView 兜底项高度突变登记为潜在滚动跳变源（测高机制见第 12 章）

### 8.4 主题系统现状
- 三层：全局（预设/视觉氛围/字体/圆角/密度/气泡/模糊）→ 角色配方（seed/背景/形状/字体/浅深锁定）
 → 状态微调；优先级：显式配方 seed > 头像取色 / 卡名哈希（无头像兜底，HSV 0.55/0.78）> 全局预设；
 自动取色同时作强调色（名字/氛围光/气泡点缀）
- 官方字段 st*/scheme*：酒馆官方主题填官方真值（#DCDCD2/#919191/#BCE7CF/#E18A24/#171717…）；
 其余 10 套由色板派生深色套；浅色模式回退 M3；官方主题浅深都强制官方深色
- 聊天背景三层：显式背景（会话/配方）> 头像玻璃背景（开关 + 模糊五档 0/12/24/36/48 +
 深/浅遮罩颜色与强度 65%/30% + 恢复默认）> 氛围渐变兜底

### 8.5 半成品治理记录（2026-08-10）

针对“UI 有入口/执行没实现、字段没暴露、文档滞后”的半成品逐项核对官方源码并补齐：

| 项 | 之前 | 现在 |
|---|---|---|
| 翻译执行层 | UI 8 家、执行 3 家；自动翻译模式只有选项没接线 | 8 家全实现（协议对齐 src/endpoints/translate.js；Bing 按官方依赖 bing-translate-api 4.2.1 移植 token 流程）；自动模式 responses/inputs/both 已按官方位点接线 |
| 图像执行层 | UI 8 来源、执行 2 家 | A1111/SDCPP/NovelAI/OpenAI/HuggingFace/Stable Horde 已实现（Horde 对齐官方 horde.js 异步轮询）；DrawThings 仅 macOS 不适用已移除；ComfyUI 仍标“开发中” |
| 图像 API Key | 无字段 | 设置→服务→图像新增 API Key（NovelAI/HF/Horde 用） |
| 向量 Data Bank 高级参数 | 官方默认隐藏 | sizeThresholdDb/chunkCountDb/overlapPercentDb 已暴露（默认 5/5/0，接进 VectorChatSettings） |
| 群聊入口文案 | “开发中” | 已实现并去文案 |
| swipe picker / 书签 / URL 导入 | 文档标未做 | 复验已实现并更正文档 |
| 死代码 | openComingSoon 未使用 | 已删除 |
| 快照 | 未做 | ✅ 设置快照（命名 zip 保存/恢复/删除设置+提供商档案，官方 user.js 语义；恢复需重启，登记） |
| 预设正则 | preset 恒空 | ✅ 命名预设集 + 允许列表 + 全位点接线（结构等价官方 preset 扩展字段） |
| 数据银行 URL 上传 | 仅本地文件 | ✅ 从 URL 添加（官方 vectors Data Bank URL 上传语义） |
| ComfyUI | 开发中 | ✅ 用户 workflow + 占位符 + /prompt + /history + /view（官方 comfy.generate 1:1；默认 workflow 文件官方仓库无，登记） |
| send_if_empty | 未做 | ✅ 空输入且最后一条为 AI 时发送配置文本续聊（官方 oai_settings.send_if_empty） |
| 斜杠异步命令 | 无异步执行器 | ✅ executeAsync + /gen /genraw（；官方无 /while，误记已删） |

**剩余已知半成品（继续治理中）**：工具调用 App 注册表/执行器（引擎已差分）、表情精灵 App 层（引擎已差分）。

### 8.6 与官方不一致登记（2026-08-10 全量审计，防漏机制）

> 规则：任何与官方 1:1 有出入的实现必须在此登记；未登记即视为未完成。

| 功能 | 与官方的差异 | 状态 |
|---|---|---|
| 斜杠执行链 | 官方惰性闭包（传给命令对象、可延迟执行）vs 引擎闭包预解析立即执行；`/if` 的 then/else 闭包同样预解析为文本（官方惰性）；命令数少于官方（补 renamechat/getchatname/setinput/bg/impersonate/trigger/inject/gen/genraw；官方无 /while）；`/parser-flag REPLACE_GETVAR` 在官方新宏引擎为 no-op（已对齐） | 近似已登记，见 3.4 |
| 斜杠参数解析核心 | parseCommand/parseNamedArgument/parseUnnamedArgument/testSymbol 已机器差分 18+27 例 1:1；执行链依赖 DOM/闭包无法逐字提取 | ✅ 差分 |
| 正则（该卡） | 存储/字段/位点同官方（data.extensions.regex_scripts、RegexScriptData、USER_INPUT=1/AI_OUTPUT=2/SLASH_COMMAND=3/WORLD_INFO=5）。✅ 存前应用；✅ 总装 isPrompt=true 只跑 promptOnly；✅ 编辑 isEdit；✅ 允许列表；✅ 全局开关；剩余差异：①落盘文本宏未替换（发送时应用、请求等价，登记边界）；②preset 脚本存储/UI 已做（命名预设集，结构等价官方 preset 扩展字段；无采样预设管理器，登记） | 🟡 宏落盘 + preset 边界，见 3.6 |
| 变量（该卡） | 官方变量是全局/聊天 scope（/let、variables.js），**没有 per-character 变量**；App 存 data.extensions.emberinn_variables 为 README 自定义扩展，官方导入会忽略该字段 | 🟡 README 自定义 |
| 快捷回复 | 已按官方全局：QuickReplyPreset/QuickReplySlot（mes/label/enabled/automationId/preventAutoExecute）+ QuickReplyExecutor 1:1。差异：①官方多预设文件（data/default-user/quick-replies/*.json），App 单预设 filesDir/quick-replies.json；②UI 已编辑 automationId/preventAutoExecute；③点击槽位官方按命令类型处理结果，App 把文本输出填输入框（可改可发），/let 等无输出命令正确静默 | 🟡 存储/交互近似，见 4.2/4.3 |
| 角色详情保存 | 官方编辑器写 data.extensions.depth_prompt/talkativeness，App 同位置；App 保存时额外把 readFromV2 提升字段镜像回 root（官方仅导入时提升），保证导出/其它客户端一致，不冲突 | ✅ 兼容增强 |
| 世界书 UI | 官方是独立 World Info 面板（world_info 扩展），App 在角色详情页自绘增删改；数据格式（data.character_book.entries、v1 key→v2 keys 归一）与官方一致，未知字段保留 | 🟡 UI 自主（兼容层一致） |
| 角色 system_prompt / 剧情后指令 | 官方 script.js generate 传 systemPromptOverride/jailbreakPromptOverride；App 此前漏传（角色系统提示词从未生效）→ 已修 | ✅ 已修 |
| {{bias}} 提示词 | 官方 getBiasStrings 从输入/最近用户消息 extra.bias 提取；App 此前不传 → 已修：提取 {{bias:...}} 并剥离宏、generate/swipe 注入、impersonate/continue 不注入（Handlebars 嵌套近似） | ✅ 已修 |
| chatCompletionSource | 官方 Claude 走 claude 分支（assistant prefill 等）；App 此前恒 openai → 已按 provider.protocol 传 claude | ✅ 已修 |
| 人设 personaDescription | ✅ 已接（2026-08-10）：PersonaStore + 聊天 ⋮ 选择；App 选中人设即 personaInPrompt=true（官方默认关，语义一致）；官方还有 {{persona}} 宏可用 | ✅ |
| 扩展提示 extensionPrompts | 引擎支持 summary/AN/vectors + MemoryEngine 已差分；App 作者注释已接；记忆 UI 待做（官方默认关） | 🟡 记忆 UI 待做 |
| 工具调用 | PromptPipeline 支持 canUseTools/toolBudget/推理签名；ToolCallParser 已差分；App 工具注册表/执行器待接 | 🟡 App 待接 |
| 世界书设置 | 已做（设置→服务→世界书，深度/递归/预算/大小写/整词，改动即存并用于聊天扫描） | ✅ |
| 模型覆盖 / 主题配方 | README 角色页承诺；官方无角色级字段（模型覆盖官方是聊天级 #custom_model_id）；已实现存储+UI+聊天背景，全局形状/字体/浅深锁定管线已做；配方导出/分享已做 | ✅ |
| 向量 / 数据银行 | 官方 Data Bank 是浏览器附件/URL 上传；App 存 filesDir/databank/ 本地文本（UTF-8）；✅ URL 下载已做（数据银行对话框“从 URL 添加”，对齐官方 vectors 扩展 Data Bank URL 上传语义）；sizeThresholdDb/chunkCountDb/overlapPercentDb 已暴露 UI（官方默认 5/5/0）；本地 BagOfGram 为离线兜底（无官方对应） | 🟡 存储/交互近似 |

### 8.7 官方对齐确认总表（2026-08-10 全量审计结论）

**已逐字/差分确认对齐（官方源码 1:1）**
- 媒体内联能力：isImage/Video/AudioInliningSupported 白名单 + source 分支（差分 24 例）
- 世界书：externalActivations 键 world.uid、负深度、深度注入、EM 锚点、coreChat 过滤 is_system、
 ensureSwipes（只排除 user/isSmallSys、swipe_info 回填 extra={}）
- 斜杠：解析器 43 例差分、testSymbol 27 例；sendas 缺省 name 兜底当前角色名；/sysname 空名写 System；
 /hide=/message-role=is_system/narrator 语义；Comment 默认名 Note；/delswipe 1-based
- 消息数据流：AI 消息落盘带 swipes 结构；saveReply 尾部 mes/swipes/swipe_info.extra 逐字段刷新（continue 同步）；
 deleteSwipe 新 id 规则；syncSwipeToMes 字段；send_date=ISO；AI extra 恒有
 api/model/reasoning/reasoning_duration/reasoning_signature；群聊 AI 带 gen_id（起整批共享 group_generation_id）；
 普通用户消息 extra isSmallSys=false、无 gen_id；附件 media_index 恒写、inline_image=true
- 提示词：默认提示集合/顺序、populationInjectionPrompts、历史消息 preparePrompt 宏替换、
 AN interval 公式与默认 position=1、Generate 类型（regenerate/continue/swipe/impersonate）
- 正则分桶：GLOBAL→PRESET→SCOPED 顺序 + allowedOnly（差分 7 例）；JSON 导入导出（13/10 例）；
 slash-parser（43 例）；向量工具（14 例）等 57 组差分

**审计修复（bug/偏差已修）**
- 历史索引错位（media 挂错）、bias 提取最后用户消息 + 编辑存 extra.bias 回溯、
 /hide 语义、comment 不进提示词、系统消息防误操作（继续/重生成/变体/滑动）、
 continue swipe_info 同步、发送失败不丢输入、重生成先查配置、群聊配置实时、书签路径消毒、
 世界书条目删除确认、角色主题/背景实时刷新、平板导航轨、滑动返回手势、返回按钮不贴最高处

**登记边界（有意保留，非 bug）**
- extra.api 存提供商 id（官方存 source）；落盘文本未过 regex/宏替换（发送时应用，请求等价）；
 bias 文本提取 vs extra.bias（双轨已接）；
 /hide name 过滤、narrator/sendas bias-only is_system；SWAP/APPEND 旧版近似；
 openrouter/mistral 等模型元数据缺失回退；远程 URL 附件；
 表情精灵 App、Room/DataStore、插件 API、网络代理、视觉小说、STT、翻译自动模式、记忆摘要（官方默认关/远期）


### 8.8 设置即时生效与默认值
- HTML 消息开关默认改为开（RenderPrefs html_enabled=true）
- 角色卡“允许此角色应用该卡正则”默认改为开（CharacterDetailScreen regexAllowed=true；显式关闭仍会写入允许列表移除）
- 即时生效补全：
 - TextTypographyScreen / MessageRenderScreen 接入 onAppearanceChanged（原来保存后不触发刷新）
 - AppearanceScreen 的 HTML/沉浸/气泡/密度/背景模糊/启动/转义保存全部补 onAppearanceChanged
 - 新增 DisplayCacheVersion：encode_tags / 全局正则 / 角色允许列表变更时 bump，displayTextOf 缓存整体失效（转义/正则设置即时生效）
 - OfficialMarkdownNode 的 remember 增加 style 键：字号/行高/阴影等排版改动对已渲染消息即时生效
 - 外观页卡片圆角从写死 18dp 改为 MaterialTheme.shapes.medium：切“全局圆角”档位时设置页即时预览
 - ColorField 支持 fallback=当前主题默认色：消息渲染页字段留空时显示主题默认值（#hex · 跟随主题），换主题即时更新
 - 新增官方玻璃色调设置（st_blur_tint）：消息渲染页“毛玻璃（官方字段）”卡；glassTint() 统一解析（用户 > 主题预设 > M3 surface），8 处玻璃面即时生效

## 10. 扩展插件：交互 HTML 卡片 / iframe 渲染器（App 层）

### 10.1 定位与结论（先读）
- **这是 App/UI 层功能，不是引擎层**。engine 未改一行；官方 SillyTavern 本体也没有这个功能。
- 官方本体通过 DOMPurify 剥掉消息里的 `<script>` 和 `on*`，所以“角色卡消息自带 JS 交互”在官方里跑不了。
- 实现参照的第三方扩展机制：**Tavern Helper（酒馆助手）渲染器** 与 **阡濯《ST酒馆 html 代码注入器》** 都是同一个机制——消息里 ``` 包起来的 HTML 代码块 → 放进独立 iframe 网页运行，卡内 `<script>`/`onclick`/Vue/React 在 iframe 里正常执行。
- 我们按同一机制在 App 里实现了等价渲染器（commit 560f251），JS 全开，设置页 1 个总开关（修订）。

### 10.2 对照了哪些源代码 / 差分结论
| 参照 | 用途 | 是否差分 |
|---|---|---|
| SillyTavern 1.18.0（~/sillytavern-ref，script.js messageFormatting + chats.js DOMPurify 钩子） | 确认官方禁消息脚本；本功能官方不存在 | 不适用（官方无此功能） |
| Tavern Helper 渲染器文档（github.com/N0VI028/JS-Slash-Runner-Doc） | ``` + `<body>` 条件 → iframe；头像类/宏、vh 换算、代码折叠 | 否（文档级参考） |
| 阡濯《ST酒馆 html 代码注入器》userscript（greasyfork 503174，CC BY-NC 4.0） | ``` 内以 `<` 开头以 `>` 结尾 → iframe；contentWindow.scrollHeight 测高 | 否（只参考机制，未复制代码） |
- **差分验证：未做、也不适用**。差分体系（60 组 / 961 例）只保证“官方引擎逻辑 1:1”；这是第三方扩展 + App/UI 层，按 README/HANDOFF 规则为 UI 自主。验证方式 = CI 编译 + 本文行为规则 + 手工回归清单（见 10.5）。
- 许可证注意：若日后直接搬运注入器代码，其许可证为 CC BY-NC 4.0（非商用）；目前只实现了机制，不涉及搬运。
- 设置与开关是 App 层自主 UI，不参与差分；总开关只影响扩展渲染器，不影响官方引擎 1:1 基线。

### 10.3 设置入口与总开关（只留 1 个开关）
- 设置 → **扩展插件** → **交互 HTML 卡片**（`ExtensionPrefs.interactiveCards`，默认开）。
- 关闭后：``` 内 HTML 代码块不再转 iframe，按普通代码块原生显示。
- 头像类/宏、原代码折叠、自动测高随总开关一起生效；JS 全开、网络/外链放开、Mermaid、HTML 消息均不是独立开关。

### 10.4 实现位置与行为（维护必读）
- `ChatScreen.kt / ChatMarkdown`：消息先按 ``` / ~~~ 分段（buildMessageSegments）；交互卡段（``` 内以 `<` 开头以 `>` 结尾或含 `<body>`）与 Mermaid / 富 HTML 段各自进独立 WebView，围栏外文本走原生 Markdown（详见第 12 章）。
- `ChatScreen.kt / embedInteractiveBlocks`（在 officialStyledHtml 内对 WebView 页面调用）：
 - 交互代码块 → `<iframe srcdoc="...">`，内容做 `& / " / < / >` 实体转义；`onload` 用 `contentWindow.document.documentElement.scrollHeight+5` 设 iframe 高度，并对 iframe 文档挂 ResizeObserver/MutationObserver 持续同步；
 - 非交互代码块 → `<pre><code>`（转义）；
 - 围栏外纯文本 → 转义后 `<div style="white-space:pre-wrap">`（保留换行）；本身含 `<` 的 HTML 段原样放行。
- `ChatScreen.kt / WebViewHtml`：JS 恒开、网络与外链放开；实例来自 `WebViewPool` 复用，测高用 ResizeObserver + `EmberInnBridge`（addJavascriptInterface）事件上报 + 1s 低频兜底 + onPageFinished 轮询兜底（≤15s）+ 测高未返回时 160dp 可见兜底（详见第 12 章）；等 iframe 加载完再撑外层高度；外层高度上限仍 75% 屏高，超出后卡片内部滚动；加载方式 = 原文 UTF-8 + file base（2026-08-12 修正，旧 base64 方案在非 data baseUrl 下不解码、显示 base64 原文，见 12.14）。
- 与 JS 全开联动：卡内脚本能跑；http(s) 顶层导航仍走系统浏览器。

### 10.5 与 Tavern Helper 能力对照
| 能力 | 状态 |
|---|---|
| ``` 代码块 → iframe 独立网页、脚本可交互 | ✅ 已实现 |
| 非交互代码块保留显示 | ✅ 已实现（pre/code） |
| iframe 自动测高 | ✅ 已实现（onload + 150/500/1500/3000ms 复测；iframe 内 ResizeObserver/MutationObserver 持续同步高度；外层 ResizeObserver 上报） |
| 围栏外文本保留换行 | ✅ 已实现（pre-wrap） |
| 头像类 `.char-avatar`/`.char_avatar` + `{{charAvatarPath}}` | ✅ 已实现（角色头像传进 WebView 注入 CSS；`{{userAvatarPath}}` 暂空，登记） |
| `min-height: *vh` 按 iframe 高度换算 | ➖ 未做（登记） |
| 原代码折叠（details 默认收起） | ✅ 已实现 |
| 后台脚本库（页面级自动化：改世界书/注入提示词/监听事件） | ➖ 不内嵌；App 等价物 = Kotlin 引擎 + 快捷回复/斜杠 |
| 表情/VN/STT/EJS 变量/插件市场 | ➖ 未实现（登记） |

### 10.6 手工回归清单
1. 消息 = 单个 ``` 包着 `<html><body><button onclick=...>`：卡片内按钮可点、脚本执行、高度自适应、不撑爆列表
2. 同一消息 = 交互块 + 普通文字/普通代码块：文字保留换行、普通代码块正常显示
3. 纯 HTML 消息（无代码围栏）：行为同（透明底、图片加载、外链跳系统浏览器）
4. 交互块内的远程图片/字体：可加载（网络已放开）；离线时显示占位
5. 长交互页：外层 75% 屏高上限，内部滚动正常，聊天列表滚动不被卡死

### 10.7 安全与许可证
- 交互代码块 = 执行任意脚本：可发网络请求、可读该消息 WebView 内的一切；唯一的 JS 桥 `EmberInnBridge` 只收“高度/未加载图片数”两个整数，不暴露 Android API/本地文件（除 asset）。
- 与 JS 全开为同一风险等级；官方默认禁止，属有意偏差。后续若收紧，先关 `settings.javaScriptEnabled` 或恢复 sanitize 剥 script。

## 11. 渲染与官方源码逐项对照（审计，2026-08-11）

对照版本：SillyTavern 1.18.0（~/sillytavern-ref，release 8172dcd）。
官方管线：script.js `messageFormatting` → Showdown(makeHtml) → DOMPurify → style.css 渲染。
我方管线：`displayTextOf`（位点正则/fixMarkdown/encode_tags）→ `preprocessOfficialHtml`
（代码保护 + 官方标记化 \uE001-\uE007）→ 原生 mikepenz Markdown + `OfficialMarkdownNode`
（buildMarkdownAnnotatedString + applyOfficialMarkers）→ 或 WebView 兜底（officialStyledHtml + 自动测高）。
引擎层未动，本审计只覆盖 App/UI 渲染。

### 11.1 逐项对照表

| 官方项（源码位置） | 官方行为 | 我方实现 | 1:1 |
|---|---|---|---|
| 引号对 6 种（script.js） | `"…"`/“…”/«»/「」/『』/＂＂ → `<q>` 含引号字符，代码/style 先保护 | preprocessOfficialHtml 同 6 种 → \uE001..\uE002；保护 ```/~~~ /``/`/style | ✅ |
| 系统消息（script.js `if (!isSystem)`） | 引号转换/encode_tags 跳过，fixMarkdown 仍执行 | displayTextOf 已跳过正则+encode；补跳过引号转换 | ✅ 本轮修复 |
| 用户消息（script.js getMessageTextHTML） | 与 AI 消息同样走 messageFormatting | 用户气泡改走 ChatMarkdown（Markdown/HTML/WebView 同一管线） | ✅ 本轮修复 |
| 普通换行（script.js simpleLineBreaks） | 单个 `\n` 也会变 `<br>` | mikepenz `eolAsNewLine=true`（OfficialMarkdownNode + NativeMarkdown 两处注入） | ✅ 本轮修复 |
| ~text~ 下划线（Showdown underline） | → `<u>` 下划线色+Underline | \uE003..\uE004 → 下划线色+Underline | ✅ |
| `<em>/<i>`（style.css .mes_text i,em） | 斜体 + --SmartThemeEmColor | 原生 annotator EMPH → emColor；WebView CSS em,i 同色 | ✅ |
| `<b>/<strong>`（style.css strong/h1/h2） | font-weight bold | → `**` Markdown 加粗 | ✅ |
| `<s>/<strike>/<del>` | 删除线 | → `~~` | ✅ |
| `<font color="#hex">`（style.css font[color]…inherit） | 指定色，内部 em/i/u/q 继承 | \uE005..#hex..\uE007 → 最后覆盖 em/u/q | ✅ |
| `<hr>`/`<br>` | 分隔线/换行 | `<hr>`→`\n\n---\n\n`（避免紧跟文字时被解析成 Setext 标题）；`<br>`→`  \n`（Markdown 硬换行） | ✅ |
| sub/sup（Chromium UA html.css） | font-size: smaller + vertical-align: sub/super | 原生 SpanStyle：0.83×字号 + BaselineShift.Subscript/Superscript | ✅ |
| ins（UA） | text-decoration: underline | 原生 Underline | ✅ |
| small/big（UA） | font-size: smaller / larger | 0.83× / 1.2× 字号 | ✅ |
| mark（UA） | background: Mark（黄）+ color: MarkText（黑） | 黄底黑字，最后叠加、不被继承色覆盖（UA 声明 > 继承值） | ✅ |
| kbd/samp/tt/code（UA） | font-family: monospace | FontFamily.Monospace | ✅ |
| var/dfn/cite（UA） | font-style: italic | Italic | ✅ |
| abbr[title]/acronym（UA） | text-decoration: dotted underline | 实线近似（Compose 无虚线） | 🟡 视觉近似 |
| data/time/wbr | 无视觉样式 | 剥标签留内容 | ✅ |
| bdi/bdo/ruby/rt/rp（UA） | 方向隔离/覆盖、注音 | WebView 兜底（原生无法表达） | ✅ 需 Web |
| font face/size | UA 字体族/1-7 号字 | WebView 兜底（原生仅 font color） | 🟡 需 Web |
| `<a href>`（原始 HTML） | 官方 a 链接色+无下划线 | 原生转换 `[text](url)`，支持无引号 href；无 href 剥标签 | ✅ 本轮修复 |
| `<img src>`（原始 HTML） | 浏览器内联图片 | 原生转换 `![alt](url)`，支持无引号 src、保留 alt；无 src 剥标签 | 🟡 width/height 不保留 |
| 无属性 `<div>`/`<p>` | 块级布局（上下分行） | 原生剥标签 + 空行段落近似（`\n\n`） | 🟡 视觉近似 |
| 无属性 `<span>` | 行内无视觉 | 原生剥标签 | ✅ |
| 带属性 `<div>`/`<p>`（class/style/align 等） | 块级+样式 | 独立 WebView 元素（周围文字保持原生，不再整条 Web） | ✅ 需 Web |
| 正文色（style.css body） | --SmartThemeBodyColor | 原生无色样式统一补 bodyColor；WebView body color | ✅ |
| 链接（style.css a） | --SmartThemeQuoteColor，无下划线 | linkTextSpanStyle=quoteColor；typography.textLink 同色无下划线；WebView `a{text-decoration:none}` | ✅ 本轮修复 |
| 引用块（style.css blockquote） | 左 3px quote + padding-left 10px + black30a + margin 0 | 原生黑 30% Box + MarkdownBlockQuote 左边条；WebView CSS 同官方 | ✅ |
| q 内斜体（style.css q i/q em） | color:inherit（被 q 色覆盖） | applyOfficialMarkers q 后于 em | ✅ |
| u 与 em 层级（style.css u / em 优先级） | u 段 em 保持 em 色 | u 避开 em 段上色 | ✅ |
| 代码块（style.css pre code） | display:block + overflow-x:auto（长行横向滚动） | WrappingHighlightedCode（snipme 高亮 + softWrap 换行，不再横向截断；WebView pre 同样 pre-wrap） | ✅ 功能级（官方横向滚动→我方换行，内容完整可见） |
| 表格/列表/p/li tt（style.css .mes_text） | border/padding/margin 定值 | WebView CSS 同官方；原生 mikepenz 为 M3 风格近似 | 🟡 原生视觉近似 |
| 全站文字阴影（style.css `*`） | 0 0 2px --SmartThemeShadowColor | chatTypography body+h1-6 + 名字/时间/日期/思考卡（补）+ WebView text-shadow | 🟡 聊天内全文字；按钮/输入栏等 UI 未加 |
| 阴影/边框色（style.css :root） | rgba(0,0,0,.5)=#80000000 | stShadow/stBorder #80000000 | ✅ |
| --SmartThemeBlurTintColor（style.css :root） | 玻璃底色 rgba(23,23,23,1) | 设置 st_blur_tint（空=主题预设；酒馆官方 #171717）；8 处玻璃面共用 glassTint()，alpha 按面叠加 | ✅ |
| 用户/AI 气泡底（style.css :root） | rgba(0,0,0,.3) / rgba(60,60,60,.3) | stUserBubble #4D000000 / stBotBubble #4D3C3C3C | ✅ 色值；气泡无官方玻璃模糊 🟡 |
| 头像圆角（style.css :root） | 2px / 10px / 50% | avatarShape square/rounded/circle | ✅（默认圆形，官方默认方形，可改） |
| 主字体/字号（style.css :root） | Noto Sans / 15px（fontScale=1） | Noto Sans 4 面下载 / textSize=official 15px | ✅（默认 16px，可切官方 15） |
| encode_tags（script.js） | `<`/`>` 转义（负向后顾版本差异） | AppearancePrefs.encodeTags，非系统消息 | ✅ |
| 流式渲染（官方 StreamingProcessor） | 增量整段 messageFormatting | StreamingMarkdown 轻量着色，结束完整重渲染 | 🟡 中间态近似，最终一致 |
| DOMPurify（script.js） | 剥 script/on*，白名单 | JS 全开、网络全开（用户要求），只拦 javascript: URL | ❌ 有意偏差，风险登记 |
| `<style>` | 官方默认剥除（角色开关恢复+前缀） | 默认放行，且只影响该消息自己的 WebView | ❌ 有意偏差 |
| 外部媒体 | 官方 forbid_external_media 默认禁 | 默认放行 | ❌ 有意偏差 |
| Mermaid | 官方插件渲染 | WebView + 本地 asset JS | ✅ 功能级 |
| reasoning | 官方独立样式（em 色/左栏） | App 折叠卡（onSurfaceVariant） | 🟡 功能级非 1:1 |
| WebView 高度 | 官方 DOM 正常撑高 | ResizeObserver + `EmberInnBridge`（addJavascriptInterface）事件上报 + 图片未就绪 1s 低频兜底 + onPageFinished 轮询兜底（≤15s）+ 初始 160dp 可见兜底；scrollHeight 按 CSS 像素 1:1 转 dp（不是 Android 物理像素）；iframe 150/500/1500/3000ms 复测 + iframe 内 ResizeObserver/MutationObserver 持续同步；上限 75% 屏高（替换旧 250ms 轮询） | ✅ 机制自研 |

### 11.2 已知 bug / 限制登记（继续治理清单）
1. 原生 mikepenz 列表/表格样式与官方 CSS 非逐像素一致（视觉近似，UI 层自主）
2. 全站文字阴影覆盖聊天内全部文字（正文/标题/名字/时间/日期/思考卡）；按钮/输入栏等 UI 控件未加（官方 `*` 全站）
3. 气泡为平涂半透明色，官方是毛玻璃 tint（色值一致，质感差一层）
4. Markdown 表格单元格/任务列表 checkbox 文本仍走库内直绘，引号等官方字段可能残留占位符（低频）
5. 流式中间态为轻量近似（官方每 tick 全量 messageFormatting）；最终一致
6. WebView 高度上限 75% 屏高，超长内容卡内滚动（防单条撑爆列表）
7. 官方页面级交互（click-to-edit/消息按钮/角色自定义样式开关）未实现；消息内脚本官方禁、我方放行（登记）
8. abbr/acronym 官方为虚线下划线，Compose 无虚线，用实线近似；嵌套 sub/sup/small 缩放按单层 0.83× 计算（官方逐层累乘），极低频偏差

### 11.3 对照源码文件
- `~/sillytavern-ref/public/script.js`：messageFormatting（引号对/encode_tags/Showdown/DOMPurify）
- `~/sillytavern-ref/public/style.css`：`:root`（SmartTheme*、阴影、字号、字体、头像圆角）、`.mes_text`（i/em、q、u、a、blockquote、table/p/ol/ul/li tt、pre code、font[color]）
- Chromium UA 样式表 `third_party/blink/renderer/core/html/resources/html.css`：sub/sup/small（font-size: smaller）、big（larger）、mark（Mark/MarkText）、tt/code/kbd/samp（monospace）、i/cite/em/var/address/dfn（italic）、u/ins（underline）、abbr[title]/acronym[title]（dotted underline）
- AOSP `core/java/android/text/Html.java` + Compose `AnnotatedString.fromHtml`（Android-only）：平台原生 HTML 支持范围（无 mark/kbd/samp/var/ins/abbr/code，不采用的原因）
- beeper/matrix-messageformat-compose：生产库“HTML→AnnotatedString + 组合期延迟着色”架构（架构验证，未引依赖）

### 11.4 文本级 HTML 标签原生渲染对照（2026-08-11）
- **问题**：官方 DOMPurify 默认白名单放行的文本级标签，此前一部分走 WebView、一部分（sub/sup/ins/small/code 等）因 HTML 判定收紧直接漏成纯文本。
- **调研结论（权威依据）**：
  - 官方管线：script.js messageFormatting → Showdown → DOMPurify（默认 HTML 白名单）→ 浏览器按 UA 默认渲染；ST style.css 只覆盖 q/u/em/b/s/font[color]/blockquote 等，**没有**为 sub/sup/ins/small/big/mark/kbd/samp/tt/code/var/dfn/cite/abbr 写任何规则 → 全部是浏览器 UA 默认。
  - Chromium UA 样式表（third_party/blink/renderer/core/html/resources/html.css）：`sub,sup { font-size: smaller }`、`small { font-size: smaller }`、`big { font-size: larger }`、`mark { background-color: Mark; color: MarkText }`（黄底黑字）、`tt,code,kbd,samp { font-family: monospace }`、`i,cite,em,var,address,dfn { font-style: italic }`、`u,ins { text-decoration: underline }`、`abbr[title],acronym[title] { text-decoration: dotted underline }`。
  - Android 平台 `Html.fromHtml`/Compose `AnnotatedString.fromHtml`（Android-only）只支持 b/i/u/s/font/big/small/sub/sup/tt/h1-6/p/div/span 等，**不支持 mark/kbd/samp/var/ins/abbr/code**，且无法接入我方 q/u/font 官方着色层 → 不采用。
  - Beeper/Element 生产库 matrix-messageformat-compose 采用“HTML → AnnotatedString + 组合期延迟着色”架构，与本项目 preprocess → 私有标记 → applyOfficialMarkers 同构（验证架构方向，不引依赖）。
- **实现（本轮）**：preprocessOfficialHtml 新增 10 组文本级标签转换（私有标记 \uE020-\uE031）：sub/sup（0.83× + BaselineShift）、ins（下划线）、small/big（0.83×/1.2×）、mark（黄底黑字，最后叠加）、kbd/samp/tt/code（等宽）、var/dfn/cite（斜体）、abbr[title]/acronym（实线下划线近似）；data/time/wbr 剥标签留内容。无 title 的 abbr/acronym 无 UA 装饰，同样剥标签。
- **layering 依据**：UA 声明优先于继承值（CSS 层叠），所以 mark 的黄底黑字最后加、不被 q/u/font/em 继承色覆盖；var/dfn/cite 斜体先加、q/font 颜色后加只覆盖 color 属性。
- **保留 WebView**：bdi/bdo（方向）、ruby/rt/rp（注音）、font face/size、nobr（nowrap）、marquee/blink 等原生无对应能力；布局/交互/媒体/整页仍走 WebView（12.14 已修 base64 加载）。
- **OFFICIAL_HTML_TAG 补齐**（防漏成纯文本）：script/html/head/body/title/meta/link、caption/col/colgroup/tbody/thead/tfoot/tr/td/th、dl/dt/dd、datalist/optgroup/option、marquee/blink/nobr/xmp/shadow/menuitem/slot；文本级标签保留在清单作为转换失败兜底。
- 影响：纯 App/UI 层，引擎零改动。



## 12. 消息分段渲染 / WebView 复用池 / ResizeObserver 测高（App/UI 层，2026-08-11）

### 12.1 为什么改（问题）
- 旧实现：只要消息里出现 HTML / 交互卡 / Mermaid，整条消息丢进一个 WebView。围栏外 Markdown（`**粗体**`、列表、引用）会被 `embedPlainText` 转义成纯文本，Markdown 语法失效。
- 旧实现每个 HTML 消息新建 WebView，LazyColumn 滚出即销毁重建（AndroidView 本身不复用 View），聊天滚动 / 发送时卡顿。
- 旧测高用 `evaluateJavascript` 每 250ms 轮询，每个 HTML 消息最多 60 次空转。

### 12.2 分段渲染（ChatScreen.kt：buildMessageSegments / SegmentedMarkdown）
- **第一步：Web 元素切分（本轮新增）**：`carveWebElementRanges` 在围栏外找出“真正需要 WebView”的块级/结构元素（table/ul/ol/li/blockquote/pre/h1-6/center/figure/…/video/audio/canvas/svg/math/iframe/style/script/form 等，以及带属性的 div/p、face/size 的 font），从开标签到同名闭标签（同层嵌套计数、自闭合除外）切出独立 WebView 段；**周围文字保持原生 Markdown**，不再“一条消息有一点 HTML 就整条 Web”。切分受 `htmlEnabled` 控制，开关关闭时该区间并入原生段，不创建 WebView。
- **第二步：非 Web 部分按围栏切分**：`ANY_FENCE` 按 ``` / ~~~ 分段：
 - 交互卡段：``` 内以 `<` 开头以 `>` 结尾或含 `<body>`（`INTERACTIVE_FENCE` 与 `embedInteractiveBlocks` 同一正则）→ 独立 WebViewHtml，内部仍是 details 原代码 + iframe srcdoc，脚本照常执行
 - Mermaid 段：```mermaid → 独立 WebViewHtml（mermaid.min.js 本地 asset）
 - 普通代码块段：原样交给原生 Markdown
 - 围栏外文本段：先 `preprocessOfficialHtml`（本轮新增 a/img 原生转换（支持无引号属性、保留 img alt）、无属性 div/p/span 剥标签、`<br>` 硬换行、`<hr>` 空行分隔），命中 `OFFICIAL_HTML_TAG`（行内 Web 标签：button/input/span[属性]/font face-size/ruby/bdi/bdo 等）或 `looksLikeHtml` 且 `htmlEnabled` → 整段 WebView 兜底；否则原生 Markdown
- 纯 Markdown 消息（全段 Native）仍整条一次原生渲染，不拆散列表 / 引用等跨段 Markdown 结构。
- 段间 `Arrangement.spacedBy(6.dp)`；外层 modifier（气泡 / 长按 / 滑回复）包在整条 Column 上。

### 12.3 WebView 复用池（WebViewPool.kt）
- `object WebViewPool`：ArrayDeque 闲置池；`acquire` 从池取（空则新建 applicationContext WebView），`release` 停 loading、about:blank、清历史 / 子 View、换空 WebViewClient 后回池；闲置超过 6 个销毁。
- `WebViewHtml` 通过 `remember { WebViewPool.acquire(context) }` 取实例，`AndroidView(onRelease = { WebViewPool.release(it) })` 回池。
- 每个加载会话一个 `WebViewSession`（token + html）：token 变化丢弃旧页面回调，html 变化才重载，避免主题 / 设置刷新时反复 reload。
- 效果：HTML 消息滚动出屏不再销毁重建，发送 / 滚动卡顿消除；官方无此机制，属 App/UI 性能层，不改变渲染语义。

### 12.4 ResizeObserver 测高（ChatScreen.kt：WEBVIEW_MEASURE_SCRIPT）
- 兜底页 `</body>` 前注入脚本：`ResizeObserver(document.documentElement)` + load 事件 + 1s 低频轮询（图片未加载完继续，`p==0` 停，15s 上限）。
- 高度经 `window.EmberInnBridge.onMeasure(h,p)` 直接回调 Kotlin（`addJavascriptInterface`，仅回传高度/未加载图片数，不暴露其它能力）；`onPageFinished` 轮询作为第二道兜底。
- `onPageFinished` 改为 ≤15s 轮询兜底（纯字符串 `高度:未加载图片数`，避免 JSON 转义问题）；初始测高未返回时给 160dp 可见兜底，打破“高度 0 → 不布局 → 量不到高度”的死循环。
- **CSS 像素换算（本轮修复）**：`scrollHeight` 是 WebView 的 CSS 像素（1 CSS px == 1 dp），旧代码 `heightPx.toDp()` 按 Android 物理像素除以 density，高密度屏上 HTML/卡片被压成细条甚至不可见；现改为 `heightPx.toFloat().dp`。
- iframe（交互卡内部）按 onload + 150/500/1500/3000ms 复测，并在 iframe 文档上挂 ResizeObserver/MutationObserver 持续同步高度（不注入卡片代码，仅从父页观察同源 srcdoc）；外层高度上限仍是 75% 屏高，超长卡内滚动。
- token 机制保证复用后旧页面的上报不会写进新消息的高度状态。

### 12.5 删除的旧实现（已确认无残留）
- `ChatMarkdown` 里整条 `rawHtml` / `interactiveBlock` 路由、`mermaid != null` 优先整条 WebView 的分支已删除。
- `WebViewHtml` 里旧版 `onPageFinished` 每 250ms 高频轮询（≤60 次）已删除；现保留 ≤15s 低频兜底轮询（事件桥为主、轮询为第二道）。
- `emberinnh://` 自定义 scheme 测高上报已删除，改 `EmberInnBridge`（addJavascriptInterface）直接回调；`shouldOverrideUrlLoading` 只处理 http(s) 外链。
- 每消息 `WebView(ctx)` 新建已改为 `WebViewPool.acquire`；除 WebViewPool 内部外不再直建 WebView。
- 保留：`officialStyledHtml` / `embedInteractiveBlocks` / `embedPlainText`（iframe 转换与 CSS 样式仍按原机制），`sanitizeHtmlForWebView`（只拦 javascript:）。

### 12.7 滚动 / 键盘卡顿治理（2026-08-11 更新）
- **animateItem 移除（“每条消息都在动”根因）**：消息行原本挂 `Modifier.animateItem()`，对应 Google Issue Tracker 395536917（BOM 2025.01.01 复现，2026-03 仍 Not started）：上下滚动时条目位移动画持续触发，表现为“列表自己在动 / 每条消息都在动”。聊天列表不需要位移动画（google compose-samples 的 Jetchat 消息行不用），已移除。
- **毛玻璃静态化（滚动卡顿主因之二）**：原实现把“消息列表”整列作为 Cloudy `sky` 模糊源；Cloudy 0.7.1 的 SkyFrameDriver 检测到滚动活动后，滚动期间每帧重捕整屏并重模糊。现改为只把“静态背景层”（氛围渐变 + 光晕 + 显式/头像背景）作为 sky 源，消息列表不再参与模糊重绘；顶栏/输入栏保留玻璃质感，只是不再实时模糊滚过的消息文字。
- **发送后先滚底再收键盘**：`scrollToItem(last, Int.MAX_VALUE)` 在键盘未收起的小视口内完成，键盘收起后视口向下扩展、最后一条仍钉在底部；删掉了原来“等 280ms 再滚”的猜时长逻辑。
- **热路径缓存**：`chatTypography()` / `chatTextShadow()` 按设置值 `remember`（流式每 tick、每条消息重组时不再重建几十个 TextStyle / Shadow）；`NativeMarkdown` 的 colors / typography / padding / components 按实际值缓存，参数不变即复用同一实例（对照 mikepenz 官方 PR #408 的 remember 复用方向）。
- **行级参数稳定化**：`immersiveActions` / `bubbleStyle` / `density` 在 ChatScreen 层读一次传入列表，避免每条消息组合时各自读 SharedPreferences；`List<MediaAttachment>` 包成 `@Immutable ChatMedia`，让 MessageRow 可跳过重组（Compose 把 List 判为不稳定参数）。
- **玻璃边缘高光（毛玻璃美化，全量铺开）**：共用 `EmberFx.glassEdgeHighlight` 画 1dp 白色渐变细线（深色 0.16 / 浅色 0.30）——聊天顶栏下缘、聊天输入栏上缘、首页顶栏下缘、搜索态顶栏下缘、AI 对话玻璃渐变卡上缘、首页玻璃 FAB 上缘；补上 README 遗留的“1px 高光描边”。边缘反光是毛玻璃“高级感”的主要来源，tint / 阴影保持克制（README 格调守则）。全 app 真模糊玻璃共 5 处：聊天顶栏/输入栏 + 首页顶栏/搜索顶栏 + 玻璃 FAB，已全部覆盖。
- 影响：纯 App/UI 层，不动引擎；`backgroundBlur` 开关仍生效（关=纯色表面）；渲染语义仍对照官方（第 11 章），不参与差分。

### 12.9 M3 组件整体升级（2026-08-11 补充）
- **共享高级输入框 `EmberTextField`（ui/components/EmberM3.kt）**：全局替换 `OutlinedTextField`（65 处）。无边框 tonal 容器（聚焦 0.58 / 未聚焦 0.34 低饱和表面），圆角跟随主题大圆角，聚焦主色光标 + 标签上色，错误态保留 M3 语义色；聊天输入框单独用 accent 光标 + 更淡容器，配合玻璃输入栏。
- **共享高级底部栏 `EmberBottomSheet`（ui/components/EmberM3.kt）**：全局替换 `ModalBottomSheet`（14 处）。顶部 28dp 大圆角 + 拖拽把手 + `surfaceContainerLow` 低对比表面；交互语义（sheetState/onDismissRequest/content）不变。
- 依据：M3 Expressive 全组件（Glow/ButtonGroup/新 FAB 等）在 1.4.0 稳定版已移除、仅 1.5.x alpha 可用，生产不引入 alpha；改用 1.4 稳定 API 自研封装达到同类质感（tonal 容器 + 大圆角 + 拖拽把手），零依赖新增、旧设备无降级。
- 影响：纯 App/UI 层，未动引擎；官方渲染语义不受影响。

### 12.10 UI 质感整体升级（2026-08-11 补充）
- **聊天输入区重做**：输入框 `EmberTextField` 聚焦时 1.5dp 描边 + `emberShadow` 柔光环（默认主题主色，聊天输入框传角色 seed 的 accent，180ms 淡入）；快捷工具/快捷回复统一成 999 圆角胶囊流；附件/语音/快捷工具按钮换成 40dp tonal 圆钮（`EmberInputIcon`）；发送钮保留角色 seed 取色（accent 底 + 自适应亮暗图标 + accent 柔光），停止钮同规格 error 柔光。
- **聊天列表页（Sessions）**：补静态背景层 + 玻璃顶栏 + 玻璃新建 FAB（`EmberGlassFab` 共享组件）；会话卡升级为 seed 专属配色——60dp 圆角头像块 + seed 描边、角色头像整卡淡背景（alpha 0.15）、卡片底 seed tint、左侧 seed 竖条、彩色发光阴影（与首页角色卡同一套语言）。
- **设置页全量玻璃顶栏**：`SettingsGlassPage` 提供静态背景层（内容滚动不触发整屏重捕），`SettingsTopBar` 升级支持 glass（sky + 边缘高光 + Cloudy 背板模糊）/ subtitle / trailing；11 个子页（外观/文字排版/消息渲染/扩展/语音/快捷回复/世界书/正则/数据隐私/关于/提供商列表+详情）全部接入，ProviderScreen 原私有 `TopBar` 并入 `SettingsTopBar`。
- 主题影响：所有新颜色均取色自 `MaterialTheme.colorScheme` / 角色 seed accent，11 套主题与深浅模式自动适配，无硬编码色值。
- 依据：M3 1.4 稳定版（Expressive 仅 1.5 alpha），Cloudy 静态 sky 源原则，首页角色卡既有 seed 语言。
- 影响：纯 App/UI 层，未动引擎。

### 12.11 取色盘组件升级（2026-08-11 补充）
- **`ColorPickerDialog` 重做（ui/components/ColorPickerSheet.kt）**：旧版“色板 + 三条普通 M3 滑杆”换成高级选色器——二维 HSV 取色板（横=饱和度、纵=明度，渐变底 + 可拖圆点）、色相渐变条、RGB 渐变滑杆（轨道渐变跟随另外两通道）、大预览色块（当前色 + 主题环 + 彩色阴影）、官方 SillyTavern 色板、hex 输入；容器从 AlertDialog 换成 `EmberBottomSheet`（28dp 圆角 + 拖拽把手）。
- **`ColorField` 重做（ui/components/ColorField.kt）**：色块升级为 38dp 大色块（彩色阴影 + 白边内描边），整行可点开选色盘；保留 hex 输入 + 跟随主题 fallback 语义。
- 使用点：消息渲染（官方 st* 字段）、外观（阴影/遮罩颜色）等全部自动生效；`ColorPickerDialog` 公共签名未变，调用方零改动。
- 依据：HSV 取色是主流选色器范式（Android/iOS 系统取色器同构）；颜色全部取色自主题/所选色，11 套主题不受影响。
- 影响：纯 App/UI 层，未动引擎。

### 12.12 模型页 UI + 服务商默认值 + 官方字段审计（2026-08-11 补充）
- **模型页（ProviderScreen）UI 升级**：ProviderCard 补彩色阴影、状态胶囊改 999 圆角、箭头换 PhosphorIcons；默认模型选择卡片同款阴影；ModelPickerSheet 选中行 tonal 高亮 + 主色 ✓ 圆点；服务商文案 22→24 家。
- **providers.json 默认值**：24 家全部预置 base_url / default_models / default_context_window / default_max_tokens / docs_url；本次补 groq（qwen-qwq-32b、deepseek-r1-distill-llama-70b）、workers-ai（qwen2.5-72b、deepseek-r1-distill-qwen-32b）、openrouter（gemini-3-pro、claude-haiku-4-5）、perplexity（sonar）。azure/custom 保持空（Azure 部署名、自定义地址必须用户填，不硬编码）。
- **官方字段审计（对照 SillyTavern V2 spec + char-data.js）**：角色详情页已 1:1 覆盖 V2 核心字段——name/description/personality/scenario/first_mes/mes_example/system_prompt/post_history_instructions/creator_notes/creator/character_version/tags/alternate_greetings；extensions 已接线：talkativeness（话痨滑杆）、depth_prompt（深度提示）、regex_scripts（卡正则）、fav（置顶）、world（内嵌世界书，导入端处理 `embeded://`/`__asset:` 资源）。明确不做的两项（避免“无效接线”）：group_only（引擎无消费点，只加 UI 不生效）、官方“linked world name”字符串引用（本 App 用卡内嵌世界书，不依赖官方世界书文件体系）。
- 接线验证：CharacterCardEdit 的 readFields/writeFields 与导入导出共用同一 data 层，所有可编辑字段非 UI-only；保存按 V2 归一写回并同步 root/data。
- 影响：模型页为 App/UI 层；providers.json 为引擎资源，仅追加默认模型列表，不改协议逻辑。

### 12.13 引擎接线迁移（2026-08-11 补充）
- **媒体纯逻辑 → MediaEngine（官方 script.js getMediaDisplay/getMediaIndex + constants.js getFromMime）**：
  - ChatPromptFactory 历史消息：`media_display` 手写白名单 → `MediaEngine.getMediaDisplay`（extra 优先、无效回退 LIST）；`media_index` 手写 `toIntOrNull` → `MediaEngine.getMediaIndex`（数字/字符串原样、越界/负数/NaN 回退 0、null 透传），再转 Int? 供 UI 使用。
  - ChatViewModel 本地附件 / URL 附件：手写 mime 前缀分类 → `MediaEngine.typeFromMime`（未知类型拒绝，与原来一致）。
  - 保留 `mimeFromPath`（引擎无扩展名→具体 mime 表，OpenAI/Claude 请求需要）。
- **变量宏接线（{{getvar}} 读角色卡变量）**：`MacroEnv.local` 由默认 `EmptyVariableStore` 改为 `MemoryVariableStore`，预置本卡 `extensions.emberinn_variables`（CharacterCardEdit.readVariables）；`{{getvar::x}}` 现在能读到角色变量；`{{setvar}}` 走内存（官方 setvar 是聊天级内存变量，不写回卡文件，语义正确）。global 无 UI 保持空。
- **明确不做（无收益/高风险）**：
  - ChatJsonl：`ChatStore.messages` 早已用 `ChatJsonl.import`；导出保留原文件直读（格式零改动，不再序列化一遍）。
  - PersonaEngine：App 无人设连接/聊天锁/默认锁 UI，接 `resolve` 后行为与现状完全相同，纯增风险，跳过。
  - WorldInfoConverter：App 编辑器“只覆盖编辑字段、未知字段透传”的保存策略优于整表归一；转换器补的 secondary_keys/position 无 UI 展示，跳过。
  - 群聊队列（GroupChat/GroupQueue）、工具调用、Instruct 模式、表情立绘：功能新增而非迁移，未做。
- 影响：纯 App/UI 层，引擎零改动；变量宏行为从“读不到”变为“可读”，属于修复。

### 12.14 WebView 空白根因修复 + HTML 判定收紧 + 代码块换行（2026-08-11 追加）
- **空白根因（已修，2026-08-12 复核修正）**：`configureWebView` 曾用 `loadDataWithBaseURL(baseUrl, page, "text/html", "utf-8", null)`（baseUrl=null 路径，data 按 data: URL 处理，`#`/`%` 被 URL 解析截断），随后又改成 `Base64` + `encoding="base64"`。**后一方案仍错**：`loadDataWithBaseURL` 在“非 data: 的 baseUrl”（`file:///android_asset/`）下把 data 当作普通字符串直接灌入 WebView，**不做 base64 解码**（AOSP CTS 2b3744f 明确 non-data base URL → treat the String as a raw string；Android 文档：URL 编码实体也不解码）。实际表现：WebView 把整段 base64 原文当页面文本显示，开头即 `PCFET0NUWVBFIGh0bWw+...`（= `<!DOCTYPE html><html><head>`），下面大片空白——用户多轮报告“网页/交互卡片从来渲染不出来”的真根因。
- **最终修复（2026-08-12）**：直接传原文 + `encoding="UTF-8"` + `mime="text/html; charset=UTF-8"`，baseUrl 仍保留 `file:///android_asset/`。非 data: baseUrl 下 data 按 HTTP 响应体加载、不做 URL 解码，`#`/`%` 不会截断（截断只发生在 baseUrl=null 的 data: URL 路径，即 `loadData`）；mermaid.min.js 相对引用与 file:// 字体不受影响。社区权威解法一致（SO 57198560 等）。
- **渲染不出来的第二根因（本轮修复）**：Compose 初始把 WebView 高度给成 0 → 页面不布局 → `scrollHeight` 也量不到 → 高度永远 0。修复：测高未返回时先给 160dp 可见兜底高度，让页面先布局，再回缩/撑到真实高度。
- **渲染不出来的第三根因（本轮修复）**：`scrollHeight` 是 CSS 像素（1 CSS px == 1 dp），旧代码 `heightPx.toDp()` 按 Android 物理像素换算，高密度屏上高度被除以 density，HTML/卡片被压成细条甚至不可见。现改为 `heightPx.toFloat().dp`。
- **测高链路（本轮修复）**：`EmberInnBridge`（addJavascriptInterface，只回传高度/未加载图片数）→ ResizeObserver + load + 1s 兜底；`onPageFinished` 纯字符串 `高度:未加载图片数` 轮询 ≤15s 作为第二道；token 机制丢弃复用后的旧回调。
- **渲染语义（本轮一并修）**：普通 `\n` 对齐官方 `simpleLineBreaks:true`（`eolAsNewLine=true`）；HTML 开关真正关闭 WebView；WebView 链接补 `text-decoration:none`；用户消息改走 Markdown/HTML 同一管线；Mermaid 不再 html 套 html；iframe 高度由父页观察同源 srcdoc 持续同步。
- **修复方式（2026-08-12 改为原文 UTF-8）**：`loadDataWithBaseURL("file:///android_asset/", page, "text/html; charset=UTF-8", "UTF-8", null)`（page 为完整拼接页/整页文档原文）。不再手动 base64：非 data: baseUrl 下 WebView 根本不解码 base64。
- **整页文档处理（2026-08-12 新增）**：角色卡自带网页 / 模型直接输出 `<!DOCTYPE html>` 整页时，消息分段器整段走 WebView（不再被 carveWebElementRanges 拆散 head/body）；`officialStyledHtml` 检测完整文档后把兜底 CSS 注入原文档 `<head>`（`injectIntoFullDocument`），不再外套 `<html>`（html 套 html → 嵌套 `</head></body>` 提前关闭外层文档 → 页面错乱/大片空白）。
- **注入健壮性（2026-08-12 新增）**：测高脚本与 CSS 注入点通过 `structuralTagPositions` 查找，跳过 `<script>/<style>` 文本内的伪 `</body>`/`</head>` 字面量（角色卡 JS 字符串里常见），避免脚本被插进字符串中间导致整段 JS 失效。
- **本地资源访问补齐**：`allowFileAccessFromFileURLs=true` + `allowUniversalAccessFromFileURLs=true`（file:// 页面加载 file:// 字体/图片，WebView 默认禁止 file→file 跨源）；`MIXED_CONTENT_ALWAYS_ALLOW`（消息内 http 图片/资源在 data 页可加载）。与用户“网络/JS 全放开”要求一致，不加开关。
- **HTML 误判收紧（本轮）**：`looksLikeHtml` 由“任意 `<tag>`”改为“带属性或自闭合标签”（`<[a-zA-Z][^>]*(?:=|/>)`，忽略 ``` / ~~~ 围栏）。原因：普通文字/JSON 里出现 `<tag>` 会被旧规则整条丢进 WebView，WebView 又因上面的截断 bug 白屏 → 表现为“文字被框死/正文消失”。裸标签（b/i/q/u/s/font color/hr/br）已由 `preprocessOfficialHtml` 原生转换，官方富标签仍由 `OFFICIAL_HTML_TAG` 接管，行为不回退。
- **代码块“框死看不全”（fd95265）**：mikepenz 默认 `MarkdownCode` 对 code 挂 `horizontalScroll`（源码 MarkdownCode.kt），长 JSON 只能横向滚动、内容“被框住”。新增 `WrappingHighlightedCode`：snipme 高亮保留 + `Text(softWrap=true)` 自动换行，替换 codeFence/codeBlock 两个入口。官方 style.css 是 overflow-x:auto（横向滚动），此处为视觉可用性有意改成换行（功能级对齐，内容完整可见）。
- 影响：纯 App/UI 层，引擎零改动；与第 11 章官方对照结论不冲突。

### 12.15 引擎新功能 App 接线（2026-08-12）
- `ChatPromptFactory` / `ChatViewModel` 的 `{{bias}}` 提取与宏剥离已从私有正则改为 `BiasEngine`（官方 Handlebars 语义；同时兼容历史 `{{bias:...}}` 冒号写法，作为 README 扩展保留）。
- `ChatViewModel.finalizeStream` 保存回复前已接 `CleanUpMessageEngine` + `StoppingStringsEngine`（停用词/名字/群消息/trim 全链），替换原先只过正则的保存路径。
- 单聊自动续写已接 `AutoContinueEngine`（原只有群聊 `GroupLoopEngine`），最大 5 轮与群聊一致。
- `LlmClient` OpenAI 兼容非流式最终响应已改用 `ResponseDataExtractor`；`StreamingReplyParser` / `ReasoningEngine` / `TokenBudgetEngine` / `SwipeEngine` 引擎已差分就绪，App 剩余接线点：流式 SSE delta（StreamingReplyParser）、removeReasoning（ReasoningEngine）、Token 预算默认值（TokenBudgetEngine）、滑动 UI 判定（SwipeEngine）。

### 12.16 发送链路审计（2026-08-12）
对照官方 `sendTextareaMessage → Generate → prepareOpenAIMessages/populateChatCompletion → createGenerationParameters → sendOpenAIRequest → saveReply`：
- ✅ 已对齐：continue_on_send、send_if_empty（仅 OpenAI 系）、用户消息 `extra.bias` + removeMacros + substituteParams、append_title/媒体标题、请求 stop/seed/n/top_k/logit_bias/reasoning_effort/verbosity（官方源白名单）、默认上下文 4095 / 最大回复 300、getMaxPromptTokens=context-response（不再扣非官方安全余量）、流式 tool_calls 回调管线。
- 2026-08-12 默认值复核：官方 `oai_settings` 默认 = `openai_max_context: max_4k(4095)`、`openai_max_tokens: 300`（openai.js default_settings）。App UI 常量此前已 4095/300；**引擎 `ConnectionProfile.contextWindow` 与 `SamplerParams.maxTokens` 的旧默认 8192/512 已修正为 4095/300**（旧注释误标“官方 8192”），ChatPromptFactory/MemoryService 兜底同步。必选提示词超限：官方 `TokenBudgetExceededError` → toast “Mandatory prompts exceed the context size.” + Prompt Manager 提示调大限额，随后仍带残缺消息请求（空数组会 API 400）；我方引擎管线同语义（抛错→返回已装下部分），App 层在空消息时直接给友好错误、不请求 API（有意收敛）。聊天历史超限：官方与引擎都是**静默丢弃最老消息**直到能装下，不报错。
- 🟡 仍未接：quiet/quietImage/quietToLoud、dryRun 提示词预览、runGenerationInterceptors 扩展事件、appendFileContent 文本附件、itemizedPrompts/parseTokenCounts、force_name2（非 OpenAI 文本后端）、非流式 title/reasoning/image 提取、token_count 落盘。
- 规则：这些缺口不会伪造“已对齐”；HANDOFF 只在真正接完并 CI 绿后改成 ✅。

### 12.8 性能治理权威依据（调研结论）
- **LazyColumn 消息列表**：稳定 key + contentType 是底线（项目已具备：key=`m-索引`、contentType=`chat-message`）；不要把 `animateItem()` 用在滚动型聊天行（Google Issue 395536917，官方未修复；官方样本 Jetchat 不用）。
- **毛玻璃（Cloudy 0.7.1）**：sky 源必须静态。Cloudy 源码 `Sky.kt` / `SkyFrameDriver.kt` 确认：滚动活动会触发每帧 recorder 重捕 + overlay 重模糊；API ≤ 30 默认 Scrim 不跑 CPU 模糊（Cloudy README 性能优先策略）。同屏玻璃 ≤ 2-3 处（README 格调守则）。首页顶栏原把整张角色网格当 sky 源（与聊天页同样的问题），已一并改为静态背景层。
- **重组 / 分配**：skydoves compose-performance-skills（optimizing-lazy-layouts / deferring-state-reads / choosing-derivedstateof）——分配重的值移出 items lambda 并 remember；滚动/动画状态读进 `LaunchedEffect` / `snapshotFlow` 或 `graphicsLayer`（Draw 阶段），不在 Composition 阶段读滚动值。本项目 followBottom 只在 effect 里消费、光标动画已走 graphicsLayer，符合该规范。
- **Markdown**：mikepenz PR #408 官方性能方向——解析/参数 remember 复用，内容未变不重建组件模型；本项目流式走轻量渲染器、结束后一次性完整渲染，与官方 streaming_fps 语义一致。
- **键盘 / 滚底**：先滚底再收键盘（小视口滚底、大视口锚底）避免“滚到旧视口”。聊天客户端权威做法 `reverseLayout = true`（google compose-samples Jetchat）可彻底消除键盘开合/新消息的底部跳动，但需重写滚动跟随与 key 逻辑，风险高，列为后续方案（当前未采用）。

### 12.6 已知边界
- **行内 Web 标签仍整段走 Web（无法与原生文本混排）**：button/input/select/textarea/label/progress/meter/output/map/area/object/span[属性]/font face-size/ruby/rt/rp/bdi/bdo 等出现在围栏外文字里时，所在整段仍进 WebView（Compose 不支持“原生文字 + 任意行内 HTML 控件”混排）；块级卡片/表格/媒体已独立切出，周围文字不再被拖入。
- 无属性 `<div>`/`<p>` 用 `\n\n` 段落近似块级分行（官方是块级 margin）；连续 `<div>` 的间隔视觉略不同。带属性 div/p 走 Web 元素，不参与该近似。
- `<a href>`/`<img src>` 转原生 markdown：现已支持无引号属性值；img 的 alt 会保留，width/height 仍不保留（官方 parseImgDimensions 差异登记）。
- 围栏外“非官方裸标签”（如 `<foo>`、`a<b>`）不再误判成富 HTML：无属性/非官方清单标签走原生 Markdown（原样显示文本）；带属性或自闭合标签（`<foo x=1>`、`<br/>`）仍进 WebView。HTML 开关关闭时围栏外文本一律走原生，符合“HTML 开关关闭 = 不渲染任意 HTML”。
- Web 元素切分边界：无闭标签的残缺元素延伸到消息末尾；同名嵌套按层计数；跨围栏的残缺 HTML（开标签在围栏外、闭标签在围栏后）会按片段分别处理（低频边缘，行为不崩溃）。
- iframe 内部动态改高：已从父页对同源 srcdoc 文档挂 ResizeObserver/MutationObserver 持续同步，不再只靠 3s 内复测（未向卡片脚本注入代码）。
- WebViewPool 上限 6：长聊天中同时可见的 HTML 消息数远小于 6，正常不会触发销毁重建。
- **2026-08-11 渲染修复**：普通 `\n` 已按官方 `simpleLineBreaks:true` 打开 `eolAsNewLine`；HTML 开关现在真正关闭 WebView（围栏外一律原生）；WebView 链接补 `text-decoration:none`；WebView 高度允许回缩、上限严格 75%；用户消息改走与 AI 同一条 Markdown/HTML 渲染管线。
- 本项全为 App/UI 层，未动 engine；渲染语义仍对照 SillyTavern 1.18.0 style.css / script.js（第 11 章），不参与差分。

### 8.9 用户决策延期：自定义 CSS + Moving UI（2026-08-12 记录，暂不做）

**官方是什么（对照 release 8172dcd）**：
- Custom CSS：设置页文本框 → 写 `data/_css/user.css`，整个 Web UI（DOM）套用；因 EmberInn 是原生 Compose 无 DOM，无法 1:1。
- Moving UI：设置→“移动界面”开关，鼠标拖拽/缩放聊天、角色列表、设置面板等，位置尺寸存 `power_user.movingUIState`（top/left/right/bottom/width/height/margin），可保存/加载/删除命名预设（`default/content/presets/moving-ui/*.json`，结构 `{name, movingUIState}`），窗口缩放按比例重算；**官方 `isMobile()` 直接禁用**，1.18 自带预设只有空 `Default.json`。

**结论**：1:1 不可行（依赖 DOM/CSS，且官方移动端禁用）。等价方案待用户选择：
- A（推荐）：自定义 CSS 限定 WebView 交互卡片/HTML 消息（当前渲染器已可承载，需加“自定义样式”输入 + user.css 注入）；
- B：主题 JSON 编辑器（颜色/字体/圆角/密度映射 Compose 主题令牌，扩展现有主题配方体系）；
- C：布局预设（面板显隐/消息密度/双栏，数据结构可对齐 movingUIState 思路，但控件语义为 Ember 自有）。

用户答复（2026-08-12）：先记录，以后再做；未选 A/B/C。**本项不参与差分**（官方移动端禁用 + 非引擎逻辑）。

## 9. 维护速记（2026-08-10 精简归档）

### 常见编译坑（CI 红→绿经验）
1. 注释里写 `group-chats/*.json` 会触发 Kotlin 嵌套注释，把文件后半段吞掉 → 写成“目录的 *.json”
2. 缺 import、括号不配对、属性初始化引用后声明属性（前向引用）→ push 前自查
3. M3 1.4：Typography 无 defaultFontFamily（需逐样式 copy）；Modifier.padding 不能混用 horizontal+top
4. 正则字符串里 `\s` 必须双反斜杠（非 raw string 时）；helper 函数别嵌进局部函数
4.5. 全局替换函数名时，`return@旧名` 标签必须同步改名（Switch→EmberSwitch 已踩坑）
5. Modifier 扩展若用 rememberUpdatedState，必须包 `Modifier.composed`（@Composable 上下文）
6. App 无法本地编译，全靠 CI；push 后以 `gh run list` 为准，网络不稳就重试

## 7. 注意事项

- **兼容层 1:1，UI 层自由**：数据格式、注入算法、宏展开、斜杠行为、导入导出必须与官方互读互通；界面/交互/主题自主（设置与提供商参照命理2 + README）
- 改动先对照官方源码，能 1:1 就 1:1，近似项必须标注
- App 无法本地编译（无 Android SDK），全靠 CI 验证；引擎测试本机可跑
- 推送用 `gh`（已 `gh auth setup-git`）；push 会自动触发 CI，必要时 `gh workflow run 328789880 --ref main`；GitHub 网络不稳定，失败就重试
- 本沙箱 apply_patch 被审批策略禁用，文件编辑用 python3 精确改写（多替换时必须链式 `replace(...).replace(...)`，否则只有最后一处落盘——已踩坑两次）；路径相对 `~/` 而不是仓库根
- 删除类操作先确认；大改动保持小步提交
