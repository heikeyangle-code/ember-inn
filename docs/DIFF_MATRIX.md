# 差分矩阵（HANDOFF 附录；由 scripts/diff/*.mjs 生成 fixture，禁止手改）

> HANDOFF 第 2 节引用。表内 93 行、例数合计 2901；scripts/diff/ 共 95 个 *.mjs（部分脚本输出多组 fixture/决策类，见行内说明）；历史“85 组 / 1969 例”为旧口径，不再使用。

| 组 | 脚本 | 测试 | 例数 |
> 注：prompt-converters 一行脚本输出 claude-messages.json；chat-request-body 输出 requestBody；tool-loop/timed-effects/story-string/preset-apply 为决策类。
| instruct 提示词 | instruct-official.mjs | InstructModeDiffTest | 36 |
| 世界书纯逻辑 | worldinfo-official.mjs | WorldInfoDiffTest | 40 |
| 世界书整体扫描 | worldinfo-scan-official.mjs | WorldInfoScanDiffTest | 29 |
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
| 斜杠数学/布尔/len/sort | slash-math-official.mjs | SlashMathDiffTest | 444 |
| Prompt Manager 纯逻辑 | prompt-manager-official.mjs | PromptManagerDiffTest | 29 |
| NovelAI 请求体 | novel-body-official.mjs | NovelBodyDiffTest | 12 |
| 提示词工具 | prompt-utils-official.mjs | PromptUtilsDiffTest | 9 |
| JSON 角色卡导出 | json-export-official.mjs | JsonExportDiffTest | 6 |
| SSE 流解析 | sse-stream-official.mjs | SseStreamDiffTest | 16 |
| 正则整体管线 | regex-pipeline-official.mjs | RegexPipelineDiffTest | 10 |
| 导演备注 | authors-note-official.mjs | AuthorsNoteDiffTest | 20 |
| 人设引擎 | persona-engine-official.mjs | PersonaEngineDiffTest | 26 |
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
| 提示词总装整链（prepareOpenAIMessages+populateChatCompletion，含工具/媒体/推理签名/continue-nudge/空历史/预算裁剪/AN 位置/多 system 分支） | prepare-messages-official.mjs | PromptPipelineDiffTest | 29 |
| 媒体内容块转换（Claude/Gemini） | media-convert-official.mjs | MediaConvertDiffTest | 25 |
| 消息转换整链（Claude/Gemini） | prompt-converters-official.mjs | PromptConvertersDiffTest | 41 |
| 思考入提示词（PromptReasoning.addToMessage） | prompt-reasoning-official.mjs | PromptReasoningDiffTest | 7 |
| 消息缓存深度（Claude/OpenRouter） | prompt-converters-official.mjs | PromptConvertersDiffTest | 4+3 |
| 其余提供商转换器+合并+预算+OpenRouter | prompt-converters-official.mjs | PromptConvertersDiffTest | 61 |
| 消息清理（cleanUpMessage/cleanGroupMessage/fixMarkdown） | cleanup-official.mjs | CleanUpDiffTest | 49 |
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
| 作者注释注入判定（authors-note.shouldInject） | authors-note-inject-official.mjs | AuthorsNoteInjectDiffTest | 14 |
| 扩展提示 set/get + /inject 参数映射 | extension-prompt-official.mjs | ExtensionPromptDiffTest | 19 |
| 世界书 EM 示例（baseChatReplace+unshift/push） | em-examples-official.mjs | EmExamplesDiffTest | 9 |
| 深度提示注入规格（角色/群聊/世界书） | depth-inject-official.mjs | DepthPromptDiffTest | 6 |
| setOpenAIMessages 构造循环（names 各模式/isSameModel/narrator/工具过滤/forceAvatar/回车清理） | set-openai-messages-official.mjs | SetOpenAiMessagesDiffTest | 16 |
| 工具调用循环决策（canPerformToolCalls/shouldDeleteMessage/shouldStopGeneration/递归/空聊天无最后消息） | tool-loop-official.mjs | ToolLoopDiffTest | 17 |
| 世界书计时效果类（checkTimedEffects/setTimedEffects/setTimedEffect/isEffectActive/cleanUp） | worldinfo-timed-effects-official.mjs | WorldInfoTimedEffectsDiffTest | 14 |
| StoryString 模板渲染（renderStoryString，Handlebars trim/helperMissing 语义） | story-string-official.mjs | StoryStringDiffTest | 11 |
| 预设应用全链（类型识别/multi-section 校验/context/instruct/sysprompt/reasoning/chat-completion 应用与迁移/保存过滤/名字匹配/textgen·novel·kobold 采样器应用/生成参数/autoSelect/敏感字段） | preset-apply-official.mjs | PresetApplyDiffTest | 99 |
| YAML 合并/剔除（util.js mergeObjectWithYaml/excludeKeysByYaml，官方 'yaml' 包：锚点/别名解析、<< 保留字面键、多文档静默） | yaml-merge-official.mjs | YamlMergeDiffTest | 11 |
| Vertex AI 认证（google.js generateJWTToken/getProjectIdFromServiceAccount/getVertexAIAuth/getGoogleApiConfig，Date.now 冻结 + access_token 打桩） | vertex-auth-official.mjs | VertexAuthDiffTest | 6 |
| textgen 请求头（additional-headers.js getMancerHeaders/getInfermaticAIHeaders/getFeatherlessHeaders） | textgen-headers-official.mjs | TextgenHeadersDiffTest | 6 |
| 消息显示格式化纯文本子集（首条宏替换与 chat.mes 写回结果/Note-system 归一/bias 剥离/显示正则位点与 depth/fixMarkdown/encode_tags/reasoning 转义/非系统 trim/名字前缀剥离；打桩 substituteParams={{user}}→Alice、getRegexedString=可观测位点标记） | message-formatting-official.mjs | MessageFormattingDiffTest | 805 |
| CFG Scale 纯逻辑（getGuidanceScale 优先级/getCfgPrompt unshift 合并/getCustomSeparator JSON 回退/插入深度；打桩 substituteParams={{user}}→Alice；charaCfg 缺失+群聊覆盖官方抛 TypeError 登记不生成用例） | cfg-prompt-official.mjs | CfgPromptDiffTest | 25 |
| Token 概率解析（parseOpenAIChatLogprobs/parseOpenAITextLogprobs/parseChatCompletionLogprobs source 分支；text 解析 top_logprobs 整体缺失官方抛 TypeError 登记） | logprobs-official.mjs | LogprobsDiffTest | 20 |

**打桩/分支登记（防漏机制）**：差分脚本内任何打桩/未覆盖分支必须登记在本节 + 脚本头部；未登记即视为未完成。
- prepare-messages：populationInjectionPrompts 用官方真函数；getExtensionPrompt(IN_CHAT) 已由 extension-prompt 19 例覆盖；工具调用历史/推理链/推理签名/媒体内联端到端 8 例；打桩见脚本头（registerFunctionToolsOpenAI 空对象→工具预算恒 1 token；setToolCalls tokens=JSON 长度/4；getChat content ?? ''；媒体仅 data: URL 内联）。in-chat order==100 规则由引擎单测锁。
- SSE：运行时只有官方对拍的 SseChunkParser 一条路（逐字符、事件级 catch、[DONE]/message_stop 收尾、reasoning 独立通道）；旧 SseParser 已删（曾把 content:null 拼成 "null"）。
- 仍绕过 fixture：prepareOpenAIMessages 的 chat→messages 构造循环由 fixture 直接注入消息对象绕过，Kotlin 侧由 App ChatPromptFactory 按官方同名逻辑实现（接线见 4.7）；extra.tool_invocations 已由 App 解析进 PromptMessage.toolInvocations。
- 尚未差分（登记）：网络/路由层（Mistral/xAI/Cohere/AI21/OpenRouter 用 MockWebServer 锁行为，转换器已逐字差分）；斜杠完整 parser（依赖 DOM，testSymbol 已差分 27 例，其余源码对照+单测）；聊天重排/文件向量化主体（纯函数 splitRecursive/trim 已差分 14 例）；作用域宏配对（依赖 chevrotain CST，trimScopedContent 已差分 7 例）；WorldLoreMerger（App 多书合并为自有封装）。

| 实际请求体（openai/azure/openrouter/custom/perplexity/groq/deepseek/moonshot/zai/siliconflow/minimax/workers_ai/o1/gpt-5，空 stop/温度 clamp/seed 边界） | chat-request-body-official.mjs | ChatRequestBodyDiffTest | 28 |
| /preset Fuse.js 7.1 模糊回退 | preset-fuzzy-official.mjs | FusePresetDiffTest | 27 |
| getTokenizerModel 映射 | tokenizer-model-official.mjs | TokenizerModelDiffTest | 37 |
