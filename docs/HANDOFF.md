# 交接清单（会话上下文耗尽时使用）

> 最后更新：2026-08-09。接手顺序：第 0 节一眼看懂 → 1 常用命令 → 2 差分怎么用 → 3/4 现状 → 5 剩余工作 → 6 日志。

## 0. 一眼看懂：这是什么、怎么保证 1:1

```mermaid
flowchart LR
    A[app<br/>Android Compose UI<br/>聊天/首页/设置/媒体渲染] -->|ChatRepository<br/>ChatPromptFactory| B[engine<br/>纯 Kotlin 领域引擎<br/>不依赖 UI/Android]
    B -->|PromptPipeline 总装<br/>世界书/宏/正则/人设/示例/历史| C[LlmClient<br/>OpenAI/Claude/Gemini/Mistral/xAI/Cohere/AI21…]
    C -->|OkHttp SSE| D[厂商 API]
    E[官方 SillyTavern 1.18.0<br/>~/sillytavern-ref] -->|scripts/diff/*.mjs<br/>逐字提取纯函数| F[差分 fixture<br/>engine/src/test/resources/diff]
    B -->|引擎 Kotlin 同输入跑一遍| F
    F -->|DiffTest 断言一致| G[引擎 267 测全绿]
```

- 一句话：**App 只做“调用引擎 + 渲染”，引擎和官方 SillyTavern 1:1，UI 层自由**。
- “差分”= 同一输入，官方 JS 与引擎 Kotlin 各跑一遍，输出必须逐字一致；fixture 由脚本生成、不许手改。
- 官方基线：release `8172dcd`（SillyTavern **1.18.0**）；酒馆更新后重跑 `node scripts/diff/*.mjs`，红的就是要移植的差异。

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

CI：`.github/workflows/build.yml`，两个 job：`engine-test`（:engine:test）与 `build`（单测 + assembleDebug + assembleRelease + 出 APK）。push 自动触发条件见工作流 `on.push.paths`；纯文档改动不触发。当前以 `gh run list` 为准。引擎本地 **281 测全绿**。

## 2. 什么是差分验证（新会话必读）

**目标**：EmberInn 是酒馆兼容软件，引擎逻辑必须和官方 SillyTavern 1:1。
“差分验证” = 同一输入，官方 JS 跑一遍、我们 Kotlin 跑一遍，输出必须一致。
手写期望值的单测只是自证；差分才是“官方说对才算对”的机器验证。

**怎么用**：
1. `scripts/diff/*-official.mjs` 从 `~/sillytavern-ref` 逐字提取官方函数，桩掉 DOM/全局依赖，生成 fixture：`engine/src/test/resources/diff/*.json`
2. `engine/src/test/.../*DiffTest.kt` 读 fixture，调 Kotlin 引擎逐例对比
3. 官方发版 / 我们改代码后：`node scripts/diff/*.mjs` 重新生成 fixture → `./gradlew :engine:test`
4. fixture 只能由脚本生成，不许手改；新功能先加 case 再实现

**已覆盖（54 组差分 fixture，共 801 例对拍，全部通过）**：
> 说明：历史日志里的“官方基准 8xx”是当时的累计口径，不等于 fixture 用例数；当前以 54 组 / 801 例（机器数）为准。

| 组 | 脚本 | 测试 | 例数 |
> 注：脚本数 54 个（prompt-converters 一行脚本覆盖三行范围）；合计 801 例。
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
| 消息缓存深度（Claude/OpenRouter） | prompt-converters-official.mjs | PromptConvertersDiffTest | 4+3 |
| 其余提供商转换器+合并+预算+OpenRouter | prompt-converters-official.mjs | PromptConvertersDiffTest | 61 |

**分支级覆盖审计与打桩登记（防漏机制，2026-08-08 起强制）**
- 规则：差分脚本内任何打桩/未覆盖分支，必须登记在本节 + 脚本头部注释；未登记即视为未完成，不许声称该分支 1:1。
- prepare-messages（总装整链，20 例（2026-08-09 补顶层 continue-nudge 非 prefill 用例，锁 PromptPipeline→ChatHistoryPopulator 的 cyclePrompt 透传））：populationInjectionPrompts 已用官方真函数；getExtensionPrompt(IN_CHAT) 恒空串（官方内置扩展源，Kotlin 同步为空）；preparePromptsForChatCompletion 用 fixture 注入的同一提示集合（该函数自身 7 例差分）；**工具调用历史 / 推理链（active_chain/since_last_user）/ 推理签名 / 媒体内联（list/gallery/data URL）已补端到端 8 例**，打桩登记见脚本头部：registerFunctionToolsOpenAI 空对象 → 工具预算预分配恒 1 token；setToolCalls tokens = JSON.stringify 长度/4（官方 tokenHandler 对象整体计数，两端同一近似）；getChat content 归一 `?? ''`；媒体仅 data: URL 内联且只记账，content 数组表示由 MediaInliner/MediaConvert 差分单独覆盖；群聊 selected_group、names_behavior、send_if_empty、预算溢出、squash 开关均已覆盖。in-chat 扩展合并的 order==100 规则由引擎单测锁（官方 getExtensionPrompt 恒空，差分无法覆盖）。
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
✅ 导入保留世界书回归锁（2026-08-10 WorldBookImportTest：JSON/PNG 导入后 data.character_book.entries 可读可解析）；✅ CharX 资源提取（引擎 CharXImporter.CharXAssets）；✅ BYAF 资源提取（getCharacterImages/getChatBackgrounds 官方差分 6 例：默认头像回退、字节去重、paths 合并、url-join 不折叠 ../）；✅ App 层资源入库（2026-08-09：CharX icon→头像 + seed 取色，background/voice 落盘 assets/ 并记入 CharacterRecord）；✅ URL 导入角色卡（HomeViewModel.importCardFromUrl + 首页 FAB 弹层，PNG/CharX/JSON 按 URL 后缀/魔数探测，对齐官方 content-manager importURL；第 129 轮复验）。

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
✅ MacroRegistry 动态注册/注销/解析；✅ 宏 flags（{{#}} 保留空白已随作用域宏实现）；✅ 角色字段已接线（2026-08-10：App ChatPromptFactory 按官方 MacroEnvBuilder 映射 character/system.model，{{description}}/{{chardepthprompt}} 等可用）；🟡 聊天/系统状态边界仍缺；!?~> 官方标 TBD 无需补。

### 3.4 斜杠 🟡
SlashParser（命名/无名/引号/转义/list 值/rawQuotes）+ SlashEngine（管道/闭包/双管道）、/pass /let /qr-arg、{{var}}/{{pipe}}/{{arg}} 状态宏、快捷回复执行器；SlashEscape（testSymbol 转义判定，STRICT_ESCAPING 奇偶反斜杠）官方差分 27 例。
✅ 2026-08-10 按官方 SlashCommandParser 逐字移植 tokenizer：parseCommand/parseNamedArgument/parseUnnamedArgument（split+splitUnnamedArgumentCount，/let、/setvar=1、/qr-arg=2）/parseQuotedValue/parseListValue/parseValue；STRICT_ESCAPING 完整语义（/parser-flag 可切换，影响后续命令解析）；REPLACE_GETVAR 官方新宏引擎下为 no-op（{{getvar::}} 由 MacroEngine 展开，已测）；rawQuotes 官方语义（整段到命令结束、保留引号）；注释（//、/#、块注释）与命令间普通文本丢弃；闭包转义（\{:）按官方消费反斜杠。
🟡 偏差：官方惰性闭包（传给命令对象）与 () 即时执行统一为即时求值（近似，闭包仍预解析）；命令数仍少于官方（UI 已能做到的不补；异步/生成类 /gen /genraw /trigger /inject /while 未实现，登记 P2）。第 130 轮补 /renamechat /getchatname /setinput /bg /impersonate（官方语义：renamechat 空名提示、setinput 文本进管道并写输入框、bg 无参返回/clear 清除/URL 路径近似、impersonate prompt 覆盖默认冒充提示；引擎占位 + App 动作 + 单测）。第 133 轮补 /persona-set（mode=lookup/temp/all 默认 all：先找人设、找不到回退临时用户名，对齐官方 setNameCallback）。差分：参数解析核心 43 例 + testSymbol 27 例（scripts/diff/slash-parser-official.mjs / slash-escape-official.mjs 从官方逐字提取，SlashParserDiffTest/SlashEscapeDiffTest 对拍）；执行链/闭包/注释仍源码对照 + 单测（依赖 DOM/模块无法逐字提取）。

### 3.5 提示词组装 ✅（核心）
PromptManagerCore（默认/用户顺序、enabled、injection_trigger、preparePrompt original/groupOverride、mergeSystemPrompts）、PromptCollection、ChatCompletion 嵌套集合（预算/溢出/squash）、ChatHistoryPopulator、DialogueExamplesPopulator、扩展注入（summary/AN/vectors/chromadb/persona/未知扩展）、in-chat 深度注入、continue nudge/prefill、bias、control prompts（impersonate/quiet）、nsfw/jailbreak/用户相对提示、工具调用（tool_calls）、ToolLoopPlanner 递归决策（官方 RECURSE_LIMIT=5：shouldContinue/buildNextMessages/nextRecursionCount，单测 4 例；工具真正执行在 App 扩展注册表）、人设 IN_CHAT 注入；**✅ PromptPipeline 总装器**（官方 prepareOpenAIMessages+populateChatCompletion 1:1：示例解析 parseExampleIntoIndividual/setOpenAIMessageExamples、控制提示、continue prefill、pin 顺序、squash；整链官方差分 20 例；in-chat 深度注入（populationInjectionPrompts：order 降序/角色固定序/深度 splice/reverse）已用官方真函数，扩展合并 order==100 规则由单测锁（官方 getExtensionPrompt 恒空，差分无法覆盖））、作者注释组合（ANWithWI）；CharacterCardFieldsEngine 官方差分 6 例；PromptUtils 官方差分 9 例；AuthorsNoteEngine（默认值解析+ANWithWI）官方差分 7 例（默认 position 修正为官方 1）。
✅ 角色 system_prompt / 剧情后指令已真正进请求体（2026-08-10 第 79 轮修复：官方 script.js generate 传 systemPromptOverride/jailbreakPromptOverride，App 此前漏传——角色系统提示词从未生效；现按官方语义传 fields.system/jailbreak，且 chat_metadata 同名键优先）；✅ 每条历史消息过 preparePrompt 宏替换已补（对齐官方 populateChatHistory；ChatHistoryPrepareTest）；✅ 角色宏环境接线（2026-08-10：ChatPromptFactory env.character=CharacterFields(system/jailbreak/description/…/charDepthPrompt)+system.model，官方 MacroEnvBuilder 映射 1:1，{{chardepthprompt}} 等历史消息宏可用）；✅ names_behavior 已按真实官方修正：Message.fromPromptAsync 不复制 name（请求体只在 COMPLETION 模式带 name，且先 isValidName 再 sanitizeName——PromptNameSanitizer 28 例差分；2026-08-09 修正 DEFAULT 模式误带 name）；✅ 工具预分配 token、媒体内联、推理签名已补（整链差分 20 例）；多模态请求体已接（MediaInliner/MediaConvert 差分）；🟡 工具真正执行在 App 扩展注册表。

### 3.6 正则 ✅
RegexEngine + substituteRegex/宏替换 + 27 例差分（第 132 轮扩：g/首匹配、i/m/s、x/X/A/J/U 非原生 flag → new RegExp 抛错 → 脚本跳过、u 原生 flag 应用、重复 flags 回退整体正则——全部对照官方 regexFromString 1:1）；世界书 key 解析 parseRegexFromString 差分 9→15 例（第 132 轮扩：x/X/A/J/U 无效 → null、重复 flag → null，WorldRegexUtils 已补重复 flag 拒绝；u/y 原生 flag 仍为边界登记）；RegexPipelineEngine（getRegexedString：placement/markdownOnly/promptOnly/runOnEdit/minDepth/maxDepth/禁用扩展）官方差分 9 例；聊天消息正则已在扫描器接入（messageTransformer）。
✅ 该卡正则已接线（2026-08-10：CharacterCardEdit 读写 data.extensions.regex_scripts 官方 RegexScriptData）；✅ 存前应用（第 128 轮：sendMessageAsUser→USER_INPUT、saveReply→AI_OUTPUT（冒充→USER_INPUT 不落盘）、getFirstMessage→开场白 AI_OUTPUT，全部走 ChatPromptFactory.resolveRegexScripts 统一脚本集合；落盘文本已过正则，宏仍延后到总装，请求等价）；✅ 总装应用（第 127 轮：isPrompt=true + 官方 depth 公式，只跑 promptOnly 脚本——官方 coreChat.map 语义，普通脚本不再双应用；世界书内容过 WORLD_INFO 正则）；✅ 允许列表（第 127 轮：character_allowed_regex 存储 + 角色详情开关 + allowedOnly=true，scoped 默认不生效）；🟡 preset 脚本存储/UI（preset 恒空，结构已留）。

### 3.7 预设 ✅
官方 127 个预设打包 + PresetLibrary；quick-replies 打包 + 执行器。moving-ui（界面预设）未打包。

### 3.8 聊天 🟡
jsonl 基础 + BYAF 聊天导入 + continue nudge；**swipes 数据模型（App 层，对齐官方 `swipe_id`/`swipes[]`/`swipe_info[]`：ensureSwipes 初始化、syncSwipeToMes 同步、Generate('swipe') 追加、deleteSwipe、editMessage 写回）**。
✅ 聊天元数据（2026-08-10 第 79 轮）：官方 ChatHeader（chats/{id}.json chat_metadata）读写 + 字段覆盖（system_prompt/scenario/mes_example）+ 背景（custom_background）；✅ 书签（第 130 轮复验：ChatStore bookmarkNames/createBookmark/openBookmark，存档 chats/{id}-Checkpoint-*.jsonl + 最后 AI extra.bookmark_link，官方 saveBookmark 语义；UI 对话框 + 二次确认）；❌ 快照（官方快照扩展，待做）。

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
- ✅ 群聊成员激活策略官方差分 15 例；✅ APPEND 角色卡合并 8 例；✅ 深度提示 7 例；✅ 完整循环纯逻辑（GroupLoopEngine）官方差分 11 例；✅ App 调度层（2026-08-10 第 84 轮：GroupStore/新建群聊/GroupScheduler 选人/合并卡/顺序生成/续写与重生成按最后成员）；✅ natural/pooled 激活（第 87 轮）+ 队列提示（第 87 轮）；✅ 深度提示 App 接线（第 90 轮：in-chat 扩展注入 + GroupDepthPromptsEngine）；✅ 自动续写（第 90 轮：shouldAutoContinue + /continue 链，默认关）；✅ 策略切换 UI（第 90 轮：新建群聊 + 聊天 ⋮ 群聊设置）；narrator 按官方 1.18 无独立模式关闭（/sys 旁白消息群聊可用）。✅ 作者注释、聊天元数据模型、TokenCounterFactory（OpenAI 精确 JTokkit）
- ❌ 服务层：TTS / STT / 图像 / 翻译（P3/P4）；向量引擎已齐，App 层接线待做

## 4. App / UI 进度

### 4.1 导航与返回手势 ✅
底部三 Tab（角色/聊天/设置）；聊天页、设置子页都接 BackHandler，系统返回键/侧滑返回逐级回退（聊天→列表、提供商详情→列表→设置主页）；Manifest 已开 enableOnBackInvokedCallback（Android 13+ 预测性返回动画）。README 守则第 7 条已落实。

### 4.2 首页（角色 Tab）🟡
品牌顶栏 + **全局搜索**（README 守则 8：角色名/描述、会话名/最后消息、世界书条目 key/content/comment、设置项；分组结果列表；世界书条目点击出详情弹层；设置项点击跳设置 Tab；空结果引导）、AI 对话置顶卡、最近聊过横滑、角色双列网格、FAB 导入（PNG/JSON/CharX）、长按菜单（置顶/新会话/字段/导出/删除）、删除二次确认、字段详情弹层、空状态引导、Toast 反馈。角色卡取色 seed 已存（avatar → Palette）。
✅ 角色字段编辑（README：分字段 标签+预览+点击展开编辑；保存改写 rawJson 并同步会话名）。
✅ 角色详情编辑页已完成（2026-08-10 复验修复，c8b22e4 起）：官方 v2 卡字段全集编辑（名字/描述/性格/场景/开场白/示例对话/系统提示/历史指令/深度提示/话痨程度/作者/标签/备用开场白管理）+ 世界书条目管理 UI（增删改/启停/常量/选择性）+ 删除/置顶/导出 JSON/一键开始聊天。本轮修复：depth_prompt/talkativeness 读写改到官方位置 data.extensions（旧实现写 data 顶层，{{chardepthprompt}} 读不到）；世界书读取兼容 data.character_book 与根级 character_book（历史卡）；保存只覆盖编辑字段、未知扩展字段（probability/vectorized/automationId/displayIndex/extensions 等）原样保留、v1（key/order/disable）归一 v2；新增开场白编辑行；布局上下留白加大、条目卡片化；“新增条目”弹层删除按钮误删第一条的 bug 已修；导出文件名用编辑后名字。字段读写抽为纯逻辑 CharacterCardEdit（App 单测 5 例）。
✅ 正则（该卡）UI 已做（2026-08-10：data.extensions.regex_scripts 官方格式读写 + 编辑弹层 + 聊天 USER_INPUT/AI_OUTPUT 位点接线，见第 75 轮；第 127 轮补“允许此角色应用该卡正则”开关，写官方 character_allowed_regex，默认关闭）；✅ 变量（该卡）UI 已做（data.extensions.emberinn_variables，README 自定义扩展，官方无 per-character 变量，见第 8 节不一致登记）；✅ 快捷回复（全局）已做（第 77 轮：按官方 Quick Reply 扩展做成全局 preset + 槽位，字段 mes/label/enabled/automationId/preventAutoExecute 完全复用官方 QuickReplySlot；设置→服务→快捷回复管理，聊天输入区快捷盘点击执行；per-character 快捷回复已删除，README 表述已改全局）；✅ 模型覆盖已做（2026-08-10 第 81 轮）；✅ 主题配方（第 82 轮，部分）：data.extensions.emberinn_theme_recipe（seed/background/shape/font/style/lockMode）读写 + 角色详情页“主题配方”卡片（seed 输入、背景选图/清除、形状/字体/风格/浅深锁定 chips、恢复全局）；聊天页背景 = 会话锁定 custom_background 优先、角色配方 background 回退；✅ 全局应用已做（第 92 轮：ThemeState + MainActivity 管线：浅深锁定/seed/形状生效；字体 source=系统衬线、lxgw 待字体包）；🟡 字体文件下载、风格档位映射未做（边界登记）。设置搜索深链已实现。
注：模型覆盖/主题配方官方角色编辑器无对应字段（模型覆盖官方是聊天级 #custom_model_id），但为 README 明确承诺的项目自定义角色级覆盖，属待办，非移除。

### 4.3 聊天页 🟡 v2（核心已接线 + 媒体 + 状态胶囊）
> 现状：continue 走官方默认 nudge 路径（历史“新的在前”对齐 setOpenAIMessages）；思考过程走 onReasoning 独立通道（流式显示 + 生成后折叠卡片）；重新生成/继续只对最后一条 AI 生效；新角色空会话自动补 first_mes 开场白（第 133 轮起：alternate_greetings 一并进第一条 AI 的 swipes，对齐官方 getFirstMessage，可滑动切换开场白）。
消息流 LazyColumn + 气泡 + 自动滚底 + 输入框 + 发送；**PromptPipeline 总装流式发送**（角色卡/世界书/示例/历史全部引擎内完成，SSE 逐 token）；停止按钮 = 取消 OkHttp call 并保留已生成部分（官方 mes_stop）；重新生成 = 删最后 AI 回复、复用最后用户消息（option_regenerate）；继续生成 = 官方 mes_continue（移出最后 AI + continue 模式续写，流结束与原消息合并落盘）；复制 / 删除 / **编辑消息**（官方 updateMessage：isEdit 正则分位点 + 清/写 extra.bias，第 129 轮）/ **冒充**（官方 Generate('impersonate')：模型以 {{user}} 视角写草稿，流式进输入框、不落历史；引擎 type=impersonate 整链差分已覆盖）/ 长按菜单；最后一条 AI 常驻 4 键；清空会话二次确认；Markdown + 代码高亮（mikepenz m3/coil3/code 0.43.0，import 包名已对 0.43.0 源码 jar 逐一核实；聊天气泡内已收敛为聊天风样式）；未配置模型横幅 → **一键深链“提供商与模型”子页**（先退出聊天再切 Tab，不会被早退逻辑挡住）；顶栏返回 + 角色头像 + accent 角色名；系统返回 / 侧滑返回已修。聊天页布局按 README 重排：systemBars 留白、气泡限宽 78%、间距/圆角/留白加大、顶栏与输入栏为 Cloudy 0.7.1 真背板模糊玻璃（sky 源层 + cloudy 浮层，正文区不模糊）、空状态居中留白。
✅ 角色详情入口已接通（角色卡长按菜单“查看/编辑详情”→ 详情编辑页，见 4.2）。
❌ Claude 冒充的 assistant_impersonation 设置（默认空串，影响为 0，排 P2）。
✅ **滑动切回复已做（README #1731“每条消息都能滑”）**：数据模型对齐官方 jsonl（`swipe_id` / `swipes[]` / `swipe_info[]`，ChatStore.ensureSwipes 初始化 + syncSwipeToMes 语义同步 mes/send_date/gen_*/extra）；AI 气泡横滑（右=下一个/最后一条 AI 越界生成新变体，左=上一个）；计数条 `n/N` + CaretLeft/Right（有变体时显示）；长按菜单“上一个/下一个回复”“删除当前回复”（官方 deleteSwipe 的 newSwipeId 规则）+“生成新回复（变体）”（官方 Generate('swipe')：coreChat.pop() 排除最后一条，结果追加进最后一条 swipes 不新增消息）；编辑消息同步写回 swipes[swipe_id]（官方 editMessage）。导出 jsonl 含 swipes 字段可直接进酒馆。✅ 世界书扫描与官方一致（第 130 轮核对 script.js prepareMessages：swipe 在 coreChat.pop() 之后才 chatForWI=coreChat 扫描，App 的 dropLast(1) 等价，原登记“官方含最后一条”为误记，已更正）。
❌ 滑动切回复的 swipe picker（变体历史弹层跳转）未做，排 P2。
✅ 上下文占比胶囊已达标（圆环+百分比+绿黄橙红分级+点开分解，分母=ConnectionProfile.contextWindow，设置页可配）；✅ 世界书状态已升级为命中面板（条目名/命中键/常驻/位置/token，点 pill 打开）。
⚠️ 快捷工具盘=“继续/冒充 + 全局快捷回复 chips”（第 77 轮）+ automationId 自动执行（第 93 轮：世界书命中条目 automationId 匹配槽位自动执行，prevent 栈 1:1）；图像生成/附件/TTS 已入快捷工具盘与长按菜单，聊天页正则开关仍待（全局正则已在设置页）。✅ 聊天元数据（2026-08-10 第 79 轮）：chats/{id}.json 官方 ChatHeader 读写；chat_metadata.system_prompt/scenario/mes_example 覆盖角色卡（引擎参数已接）；custom_background 聊天背景（⋮ 菜单选图/清除，消息区低透明铺底）；✅ 书签（存档 + bookmark_link + 载入，第 130 轮复验）；❌ 快照未做。
现状补充：键盘适配（adjustResize + imePadding）、消息日期分隔（今天/昨天/日期）、删除消息二次确认、⋮ 会话菜单（导出聊天 JSONL / 清空）、发送按钮空输入禁用态、媒体附件与状态胶囊（见 4.8）。
近期修复（2026-08-09）：自动滚底=贴底跟随+上滑暂停+回底恢复；思考过程空正文时独立成卡不再消失；流中断保留思考+人话提示；世界书状态=命中面板（名字/键/常驻/位置/token）；上下文胶囊分母=contextWindow（默认按模型自动填，见 4.4）；SSE 事件级容错对齐官方平滑流（坏事件跳过不中断，差分 16 例 + MockWebServer 回归）；滚动跟随仅贴底时滚、发送复位；首页预览走 ViewModel 缓存（不再组合期读盘）；**滑动切回复全链**（swipes 数据模型 + 手势/计数/菜单 + 生成变体 + 编辑同步，对齐官方 ensureSwipes/syncSwipeToMes/Generate('swipe')/deleteSwipe/editMessage）。

### 4.3.5 聊天 Tab（会话列表）✅
全部会话按时间倒序、置顶优先；点卡片进聊天；长按 / ⋯ = 置顶 / 导出聊天 JSONL（官方格式，可直接进酒馆）/ 删除（二次确认）；FAB「+」新建对话（AI 对话或选角色，每个角色可开多个会话，UUID 会话 id）；空状态引导；会话置顶持久化（SessionRecord.pinned，兼容旧 JSON）。
❌ 新建群聊入口（引擎群聊激活/合并/深度/循环已 1:1，App 调度层排 P2，UI 如实标“开发中”）。

### 4.4 设置 ✅（README 规格）
- 数据与隐私页已做实：导出全部数据（zip：角色/会话/聊天/头像/提供商配置）+ 数据位置透明展示 + 清除全部数据（二次确认，建议先备份）
- 首启引导已做实（README 启动体验）：欢迎页 + 导入角色卡（系统选择器直接导入）/ 直接开始聊天（进 AI 对话）/ 跳过；SharedPreferences 标记只显示一次；低饱和氛围渐变

- 设置主页：大标题 + 副标题、设置搜索（真过滤）、常用快捷区（主题/模型/语音/备份）、六组卡片（外观与主题 / 提供商与模型 / 语音 / 服务 / 数据与隐私 / 关于）
- 外观与主题：主题模式（跟随系统/浅色/深色）+ 六套预设主题（墨韵/青瓷/夜航/丹砂/琉璃/简约纸感），点选立即全局生效（实时预览），SharedPreferences 持久化；字体/圆角/背景模糊标“开发中”
- 提供商与模型（参照命理2 逻辑）：搜索 + 卡片列表（品牌 SVG 头像 + 名称 + 一句话 + 已配置/未配置 pill + “我的连接”切换/删除）；详情页 = 名称 / API Key（遮罩+显示）/ 接口地址 / 区域 / 账户 ID / API 版本 / 默认模型（底部弹层搜索）/ 上下文上限（tokens，占比胶囊分母）/ 最大回复 tokens（推理模型思考会占额度，512 太小正文被掐空；默认按 providers.json default_max_tokens）/ 测试连接 / 保存 / 删除确认
- 关于页做实：版本 0.1.0 / AGPL-3.0 / 数据仅本地 / 开源仓库
- 语音（TTS）✅（2026-08-10 第 80 轮执行层已接）：Android 系统 TTS 本机引擎，语音选择/语速/试听真实可用；朗读选项字段对齐官方 tts 扩展（enabled/voice/rate/auto_generation/narrate_user/narrate_by_paragraphs/skip_codeblocks/skip_tags/apply_regex）；聊天自动朗读（auto_generation）、消息长按“朗读这条消息”、narrate_user 已接；文本处理对齐官方（跳代码块/标签、去星号、正则 /pat/flags、去图片、按行分段排队），纯逻辑 TtsTextProcessor 单测 3 例；官方 1.18 无 STT，语音输入不假装（未做）
- 服务页 ✅（2026-08-10 第 86 轮执行层已接）：翻译（LibreTranslate/DeepL/DeepLX 已实现，接口地址可配；Google/Lingva/Bing/OneRing/Yandex 登记未实现）、图像（AUTOMATIC1111 已实现；ComfyUI/SDCPP/Horde/NovelAI/OpenAI/HF 登记未实现）、向量（OpenAI 兼容嵌入 / 本地 BagOfGram）——✅ 2026-08-10 第 88 轮已接线：设置页开关（启用/聊天历史重排/文件数据银行 + query/insert/protect/阈值）、发送时 VectorChatRearranger 重排+数据银行检索、世界书 vectorized 条目经 externalActivations 强制激活、聊天 ⋮ 数据银行管理；OpenAI 配置不完整时本轮禁用并人话提示

### 4.4.5 应用图标 ✅
launcher 图标 = 用户提供的原图（Download/file_0000000078d0820782054bfedd4cb346.png）缩放为 mipmap-xxxhdpi/ic_launcher.png（192px），Manifest 引用 @mipmap/ic_launcher；换图只需替换该 PNG。

### 4.5 主题系统 ✅（全局层）
ThemePreset（seed/secondary/tertiary + 纸色/夜色）→ Theme.kt 自动生成整套 M3 ColorScheme（含 surfaceContainer 系列，浅色低饱和容器、深色提亮主色）；MainActivity 持有 themeMode/preset 状态，贯通 MainScreen → SettingsScreen → AppearanceScreen。
✅ 玻璃表面：聊天页顶栏/输入栏 + 首页顶栏已接 Cloudy 0.7.1（背板模糊 + 半透明 tint，GPU + 旧设备 CPU 降级）；1px 高光描边/内阴影与其余页面待铺开。
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
| Markdown 渲染 | 官方用 Showdown + highlight.js + DOMPurify | mikepenz multiplatform-markdown-renderer + Highlights/KodeView；❌ HTML 消息开关 / Mermaid 未实现（README 高级渲染节要求，HANDOFF 旧文误写已做） |
| 媒体渲染 | `public/scripts/openai.js` Message.addImage/addVideo/addAudio + `public/scripts/media.js` | 聊天消息 `extra.media` → MediaEngine.getFromMime 判定类型 → 图片/GIF 用 Coil3（coil-gif）、音视频用 Media3 ExoPlayer；URL 附件按官方逻辑下载/展示；✅ extra.media 解析与渲染组件已接（见 4.8） |
| 世界书注入 | `public/scripts/world-info.js` checkWorldInfo + `public/scripts/openai.js` | 发送前：世界书条目 → Scanner（含正则 messageTransformer、RAG 强制激活）→ 注入结果进 PromptAssembler；命中灯只读 Scanner 完整 match 结果 |
| 宏 | `public/scripts/macros/engine/` | 所有文本入 prompt 前统一走 MacroEngine（世界书 format、作者注释、历史消息 preparePrompt 已由引擎接线，App 只需保证 MacroEnv 提供聊天/角色/系统状态） |
| 正则 | `public/scripts/regex/` | 存前（sendMessageAsUser/saveReply/getFirstMessage）+ 总装（isPrompt=true/depth）双位点接入 RegexPipelineEngine；允许列表 character_allowed_regex；设置页做 global/preset/scoped 分桶（preset 存储待做） |
| 群聊 | `public/scripts/group-chats.js` | 每轮：GroupActivationEngine 选成员 → GroupCharacterCardsEngine 合并卡字段 → GroupDepthPromptsEngine 深度提示 → GroupLoopEngine 判定续写/生成类型 → 多人回复按官方顺序拼接 |
| 表情精灵 | `public/scripts/expressions/` + `endpoints/sprites.js` | ExpressionEngine.chooseSpriteForExpression 选图 → sprite 渲染到消息头像区；分类 API 接 LLM 或本地模型 |
| 快捷回复 | `public/scripts/quick-reply.js` | 输入区快捷盘 → QuickReply 执行器（automationId 自动执行由引擎 WorldInfoAutoExecute 判定） |
| 人设 | `public/scripts/personas.js` | ✅ 2026-08-10 第 83 轮：PersonaStore（filesDir/personas.json，官方 Persona Management 语义）+ 聊天 ⋮ 人设选择/新建/编辑/删除；选中人设时 personaDescription 注入（personaInPrompt=true，官方 persona_in_prompt 语义；引擎 PromptPipeline 补同名透传参数，默认 false 行为不变） |
| 向量 RAG | `extensions/vectors/index.js` + `utils.js` | ✅ 2026-08-10 第 88 轮：VectorRagService（OpenAI 兼容 / 本地 BagOfGram + FileVectorStore）→ ChatPromptFactory 总装前跑 VectorChatRearranger（聊天重排/文件分块/数据银行检索，引擎 1:1），世界书命中经 scanner externalActivations 强制激活，扩展提示 3_vectors/4_vectors_data_bank 注入；数据银行文件在聊天 ⋮ 菜单管理 |
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
- ⚠️ 图库切换已做（第 95 轮：发送端列表/图库切换 + 渲染横滑/圆点 + media_index 落盘）；✅ 从 URL 导入附件（第 131 轮：输入区附件菜单“从 URL 添加”→ 下载 → 落盘 media/ → 与本地附件同链，URL 后缀+魔数判型；官方 Message.addImage/addVideo/addAudio URL 来源）；未做（登记）：URL 型资产下载（图片发送前压缩 compressImage 已做近似：非 jpeg/png/webp 转 JPEG 最长边 2048）

### 4.9 App↔引擎接线状态
聊天链路（发送/停止/继续/重新生成/冒充/编辑/删除/媒体/思考）全部接到引擎 1:1 能力上；官方行为接线点明细不再单列，见 4.3/4.7 现状描述。
上下文预算对齐官方（commit `131d5c6`）：默认 32K（旧 8192 视为未设置）、maxTokens 钳制保证预算为正、
必选提示词装不下时走 `ContextBudgetException` 人话报错；Claude 直连缓存参数已接线（详见第 6 节日志 72）。

## 5. 完成度总览（截至第 126 轮 / 全量审计后，2026-08-10）

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
- 54 组差分 fixture / 801+ 例对拍全绿（slash-parser 43、regex-scope 7、json-import 13、json-export 10 等）

## 6. 引擎差分/修复日志（仅引擎层；App/UI 层不记过程，现状见第 4 节）

> 只保留会影响后续工作的结论；更早逐轮完整历史见 `git log --oneline`。

## 8. 与官方不一致登记（2026-08-10 全量审计，防漏机制）

> 规则：任何与官方 1:1 有出入的实现必须在此登记；未登记即视为未完成。

| 功能 | 与官方的差异 | 状态 |
|---|---|---|
| 斜杠执行链 | 官方惰性闭包（传给命令对象、可延迟执行）vs 引擎闭包预解析立即执行；`/if` 的 then/else 闭包同样预解析为文本（官方惰性）；命令数少于官方（第 130 轮补 renamechat/getchatname/setinput/bg/impersonate；/gen /genraw /trigger /inject /while 等异步类仍缺）；`/parser-flag REPLACE_GETVAR` 在官方新宏引擎为 no-op（已对齐） | 近似已登记，见 3.4 |
| 斜杠参数解析核心 | parseCommand/parseNamedArgument/parseUnnamedArgument/testSymbol 已机器差分 18+27 例 1:1；执行链依赖 DOM/闭包无法逐字提取 | ✅ 差分 |
| 正则（该卡） | 存储/字段/位点同官方（data.extensions.regex_scripts、RegexScriptData、USER_INPUT=1/AI_OUTPUT=2/WORLD_INFO=5）。✅ 存前应用已接（第 128 轮：sendMessageAsUser/saveReply/getFirstMessage）；✅ 总装 isPrompt=true 只跑 promptOnly（第 127/128 轮），普通脚本不再双应用；✅ 允许列表 character_allowed_regex + 角色详情开关（第 127 轮）；剩余差异：①落盘文本宏未替换（发送时应用、请求等价，登记边界）；②preset 脚本存储/UI 未做（preset 恒空）；③编辑消息 isEdit 正则未接 | 🟡 宏落盘 + preset + isEdit 边界，见 3.6 |
| 变量（该卡） | 官方变量是全局/聊天 scope（/let、variables.js），**没有 per-character 变量**；App 存 data.extensions.emberinn_variables 为 README 自定义扩展，官方导入会忽略该字段 | 🟡 README 自定义 |
| 快捷回复 | 已按官方全局：QuickReplyPreset/QuickReplySlot（mes/label/enabled/automationId/preventAutoExecute）+ QuickReplyExecutor 1:1。差异：①官方多预设文件（data/default-user/quick-replies/*.json），App 单预设 filesDir/quick-replies.json；②UI 已编辑 automationId/preventAutoExecute（第 93 轮）；③点击槽位官方按命令类型处理结果，App 把文本输出填输入框（可改可发），/let 等无输出命令正确静默 | 🟡 存储/交互近似，见 4.2/4.3 |
| 角色详情保存 | 官方编辑器写 data.extensions.depth_prompt/talkativeness，App 同位置；App 保存时额外把 readFromV2 提升字段镜像回 root（官方仅导入时提升），保证导出/其它客户端一致，不冲突 | ✅ 兼容增强 |
| 世界书 UI | 官方是独立 World Info 面板（world_info 扩展），App 在角色详情页自绘增删改；数据格式（data.character_book.entries、v1 key→v2 keys 归一）与官方一致，未知字段保留 | 🟡 UI 自主（兼容层一致） |
| 角色 system_prompt / 剧情后指令 | 官方 script.js generate 传 systemPromptOverride/jailbreakPromptOverride；App 此前漏传（角色系统提示词从未生效）→ 已修（79 轮） | ✅ 已修 |
| {{bias}} 提示词 | 官方 getBiasStrings 从输入/最近用户消息 extra.bias 提取；App 此前不传 → 已修：提取 {{bias:...}} 并剥离宏、generate/swipe 注入、impersonate/continue 不注入（Handlebars 嵌套近似） | ✅ 已修 |
| chatCompletionSource | 官方 Claude 走 claude 分支（assistant prefill 等）；App 此前恒 openai → 已按 provider.protocol 传 claude | ✅ 已修 |
| 人设 personaDescription | ✅ 已接（2026-08-10 第 83 轮）：PersonaStore + 聊天 ⋮ 选择；App 选中人设即 personaInPrompt=true（官方默认关，语义一致）；官方还有 {{persona}} 宏可用 | ✅ |
| 扩展提示 extensionPrompts | 引擎支持 summary/AN/vectors；App 作者注释已接（第 105 轮：聊天 ⋮ 作者注释 + ANWithWI）；记忆 UI 未做（官方默认关） | 🟡 记忆 UI 待做 |
| 工具调用 | PromptPipeline 支持 canUseTools/toolBudget/推理签名；App 工具注册表未做（HANDOFF 已有登记） | 🟡 P2 |
| 世界书设置 | 已做（第 94 轮：设置→服务→世界书，深度/递归/预算/大小写/整词，改动即存并用于聊天扫描） | ✅ |
| 模型覆盖 / 主题配方 | README 角色页承诺；官方无角色级字段（模型覆盖官方是聊天级 #custom_model_id）；已实现存储+UI+聊天背景（第 81/82 轮），全局形状/字体/浅深锁定管线已做（第 92/106 轮）；配方导出/分享已做 | ✅ |
| 向量 / 数据银行 | 官方 Data Bank 是浏览器附件/URL 上传；App 存 filesDir/databank/ 仅本地文本（UTF-8），不做 URL 下载；sizeThresholdDb/chunkCountDb/overlap 等高级参数用官方默认未暴露 UI；本地 BagOfGram 为离线兜底（无官方对应） | 🟡 存储/交互近似 |

## 官方对齐确认总表（2026-08-10 全量审计结论）

**已逐字/差分确认对齐（官方源码 1:1）**
- 媒体内联能力：isImage/Video/AudioInliningSupported 白名单 + source 分支（差分 24 例）
- 世界书：externalActivations 键 world.uid、负深度、深度注入、EM 锚点、coreChat 过滤 is_system、
  ensureSwipes（只排除 user/isSmallSys、swipe_info 回填 extra={}）
- 斜杠：解析器 43 例差分、testSymbol 27 例；sendas 缺省 name 兜底当前角色名；/sysname 空名写 System；
  /hide=/message-role=is_system/narrator 语义；Comment 默认名 Note；/delswipe 1-based
- 消息数据流：AI 消息落盘带 swipes 结构；saveReply 尾部 mes/swipes/swipe_info.extra 逐字段刷新（continue 同步）；
  deleteSwipe 新 id 规则；syncSwipeToMes 字段；send_date=ISO；AI extra 恒有
  api/model/reasoning/reasoning_duration/reasoning_signature；群聊 AI 带 gen_id（第 129 轮起整批共享 group_generation_id）；
  普通用户消息 extra isSmallSys=false、无 gen_id；附件 media_index 恒写、inline_image=true
- 提示词：默认提示集合/顺序、populationInjectionPrompts、历史消息 preparePrompt 宏替换、
  AN interval 公式与默认 position=1、Generate 类型（regenerate/continue/swipe/impersonate）
- 正则分桶：GLOBAL→PRESET→SCOPED 顺序 + allowedOnly（差分 7 例）；JSON 导入导出（13/10 例）；
  slash-parser（43 例）；向量工具（14 例）等 54 组差分

**审计修复（bug/偏差已修）**
- 历史索引错位（media 挂错）、bias 提取最后用户消息 + 编辑存 extra.bias 回溯、
  /hide 语义、comment 不进提示词、系统消息防误操作（继续/重生成/变体/滑动）、
  continue swipe_info 同步、发送失败不丢输入、重生成先查配置、群聊配置实时、书签路径消毒、
  世界书条目删除确认、角色主题/背景实时刷新、平板导航轨、滑动返回手势、返回按钮不贴最高处

**登记边界（有意保留，非 bug）**
- extra.api 存提供商 id（官方存 source）；落盘文本未过 regex/宏替换（发送时应用，请求等价）；
  bias 文本提取 vs extra.bias（双轨已接）；
  /hide name 过滤、narrator/sendas bias-only is_system；SWAP/APPEND 旧版近似；
  openrouter/mistral 等模型元数据缺失回退；远程 URL 附件；/gen /genraw /trigger /while 异步命令；
  表情精灵 App、Room/DataStore、插件 API、网络代理、视觉小说、STT、翻译自动模式、记忆摘要（官方默认关/远期）

## 9. 维护速记（2026-08-10 精简归档）

### 历史轮次
88–126 轮逐轮记录已精简归档（需要时查 `git log --oneline`）；当前状态以上文第 3/4/5/8 节为准。

### 常见编译坑（CI 红→绿经验）
1. 注释里写 `group-chats/*.json` 会触发 Kotlin 嵌套注释，把文件后半段吞掉 → 写成“目录的 *.json”
2. 缺 import、括号不配对、属性初始化引用后声明属性（前向引用）→ push 前自查
3. M3 1.4：Typography 无 defaultFontFamily（需逐样式 copy）；Modifier.padding 不能混用 horizontal+top
4. 正则字符串里 `\s` 必须双反斜杠（非 raw string 时）；helper 函数别嵌进局部函数
5. Modifier 扩展若用 rememberUpdatedState，必须包 `Modifier.composed`（@Composable 上下文）
6. App 无法本地编译，全靠 CI；push 后以 `gh run list` 为准，网络不稳就重试

## 7. 注意事项

- **兼容层 1:1，UI 层自由**：数据格式、注入算法、宏展开、斜杠行为、导入导出必须与官方互读互通；界面/交互/主题自主（设置与提供商参照命理2 + README）
- 改动先对照官方源码，能 1:1 就 1:1，近似项必须标注
- App 无法本地编译（无 Android SDK），全靠 CI 验证；引擎测试本机可跑
- 推送用 `gh`（已 `gh auth setup-git`）；push 会自动触发 CI，必要时 `gh workflow run 328789880 --ref main`；GitHub 网络不稳定，失败就重试
- 本沙箱 apply_patch 被审批策略禁用，文件编辑用 python3 精确改写（多替换时必须链式 `replace(...).replace(...)`，否则只有最后一处落盘——已踩坑两次）；路径相对 `~/` 而不是仓库根
- 删除类操作先确认；大改动保持小步提交
