# 引擎 vs 官方缺口审计（持续更新）

## 差分覆盖审计（2026-08-18）

**已差分：96 组 / 2984 例官方基准，全部通过；引擎 378 测全绿（明细与打桩登记见 [DIFF_MATRIX.md](DIFF_MATRIX.md)）。**

### 覆盖不足 / 只有手写测试或源码对照
- LlmClient 网络层（MockWebServer 锁行为）、ChatCompletionPipeline 整体、官方 tokenizer（Claude/Gemini 用户豁免回退 cl100k）、向量库持久化（对齐 vectra 目录语义）、斜杠完整 parser（解析核心已差分 43+27 例，执行链依赖 DOM 无法逐字提取）、chevrotain CST 依赖的宏配对

### 已知边界与潜在风险（后续优先补）
- 正则：JS 特有 flags 已对齐（x/X/A/J/U 非原生 flag → new RegExp 抛错 → 脚本跳过；u 原生 flag 保留、Java 近似忽略）；trimStrings characterOverride 已接（官方差分覆盖）；RegexPipeline 宏替换已透传且 App 已全位点接线
- SSE：choices.delta.content 数组 thinking、choices.message.content、not-primary 抛错已补；Cohere 完整分支与 choices.message 工具调用仍边界
- 群聊：shouldAutoContinue 未含 is_send_press/abortController 状态
- 角色卡：V2Normalizer 仍可能遇到更多缺失字段组合；BYAF/CharX 文件冲突未穷举
- 表情：本地 BERT/WebLLM 分类未移植（LLM 分类已接）

对照官方 release（~/sillytavern-ref），逐项核对引擎覆盖情况。
✅=已实现且有测试/差分　🟡=部分/边界　❌=未做

## 角色卡
- ✅ PNG/JSON 导入导出 + CharX/YAML/BYAF 导入全部对齐官方；JSON 导入 5 例 + 导出 4 例、YAML 3 例、CharX 5 例、BYAF 14+5+4+完整导入计划 4 例；V2 归一（官方差分 5 例 + 多轮补真 bug）、私有字段清理、PNG 字节差分（6 例）
- ✅ CharX 资源提取（引擎 CharXImporter.CharXAssets：icon/assets，官方差分覆盖 uri 映射/storageCategory/baseName）；✅ BYAF 资源提取（getCharacterImages/getChatBackgrounds 官方差分 6 例，url-join 不折叠 ../）；App 层资源入库已接（CharX icon→头像+seed、background/voice 落盘 assets/）
- ✅ URL 导入（App 层：HomeViewModel.importCardFromUrl + 首页弹层）

## 世界书
- ✅ 扫描/注入/递归/预算/分组/时间效果/过滤；整体扫描差分 17 例 + 纯逻辑 19 例
- ✅ 世界书文件导入导出（WorldInfoFileCodec）、世界书↔角色书互转（2 例差分）
- ✅ 正则在 BUILD 阶段接入扫描器（contentTransformer）
- ✅ vectorized/addMemo/displayIndex/automationId 已进强类型 + 扩展行为全接上（RAG 强制激活 / 快捷回复自动执行 / 编辑器排序）；编辑器排序与自动执行选择有官方差分（10 例）
- ✅ 向量扩展全量（引擎层）：世界书 RAG（同步/检索/强制激活）+ 聊天历史重排（rearrangeChat）+ 文件/DataBank 向量化（processFiles 系列）；FileVectorStore 磁盘持久化（对齐 vectra）；splitRecursive/overlap/trim 工具 1:1

## 宏
- ✅ 核心宏 + 官方 e2e 差分 158 例 + {{pick}} seedrandom 逐位一致（5 例）
- ✅ 变量简写全运算符、{{if}}、{{trim}} 作用域、legacy 标记/冒号/空格参数、嵌套参数
- ✅ 通用作用域宏（{{setvar::x}}content{{/setvar}}、{{#}} 保留空白、嵌套、trim+dedent，对齐 MacroCstWalker.processScopedMacros）；trimScopedContent 官方差分 7 例
- ✅ MacroRegistry 动态注册/注销/解析；🟡 宏 flags 的 !?~> 官方标 TBD 未实现（无需补）；配对逻辑依赖 chevrotain CST 无法逐字差分（源码对照+单测）；完整 MacroEnv（聊天/角色/系统状态）边界

## 斜杠
- 🟡 解析（命名/无名/引号/转义/list 值/rawQuotes）、管道/闭包/双管道、
  /pass /let /qr-arg、{{var}}/{{pipe}}/{{arg}} 状态宏、快捷回复执行器；
  偏差：官方惰性闭包（传给命令对象）与 () 即时执行被统一为即时求值；命令数远少于官方
- ✅ /parser-flag 命令已注册（引擎侧占位，参数保留）；✅ STRICT_ESCAPING 转义判定（SlashEscape，10 例差分）；🟡 REPLACE_GETVAR 完整语义、150+ 官方命令（多数要接 App 状态）

## 提示词组装
- ✅ PromptManager 核心、ChatCompletion 嵌套集合、组装管线、bias/override；populateChatHistory 5 例 + populateDialogueExamples 4 例官方差分
- ✅ CharacterCardFieldsEngine 角色卡字段聚合官方差分 6 例（群聊/chat_metadata/prefer 覆盖）
- ✅ PromptUtils（collapseNewlines/parseMesExamples）官方差分 9 例
  ✅ preparePromptsForChatCompletion 官方差分 7 例（wi_format/impersonation/role/injection/override/trigger 全核对）；✅ PromptPipeline 总装器整链官方差分 5 例（prepareOpenAIMessages+populateChatCompletion：示例解析/控制提示/continue/pin/squash）；✅ names_behavior COMPLETION 名字清理（PromptNameSanitizer 28 例差分 + ChatHistoryPopulator 接线）
- ✅ in-chat 深度注入（populationInjectionPrompts）、continue nudge、相对扩展注入 main
- ✅ 工具调用（tool_calls/tool 结果）、control prompts、pin_examples、squash
- 🟡 工具预分配 token、媒体内联（OpenAI 已接；Anthropic/Gemini 消息整链已差分并接入 builder）、推理签名（Gemini 已接）、多模态；✅ 预算计算/其余提供商转换器/OpenRouter 缓存媒体签名已差分
- ✅ continue prefill/nudge、人设 IN_CHAT 注入、作者注释组合（ANWithWI）
- ✅ AuthorsNoteEngine：默认值解析 + ANWithWI 官方差分 7 例（position 默认=1）
- ✅ 群聊调度全链（引擎 SWAP/APPEND/队列 + App 调度层 GroupStore/GroupScheduler/顺序生成/续写重生成按最后成员）；🟡 工具预分配 token/媒体内联/推理签名边界

## 正则
- ✅ 引擎 + substituteRegex/宏替换 + 20 例差分（g/首匹配/i/m/s/非法 flags）
- ✅ getRegexedString 整体管线官方差分 9 例（placement/markdown/prompt/编辑/深度/禁用扩展）
- ✅ global/preset/scoped 分桶、允许列表 character_allowed_regex、全局开关、命名预设集（App 层已全接，见 HANDOFF 3.6）；聊天消息正则已在扫描器接入（messageTransformer）

## 预设
- ✅ 官方 context 34 / instruct 38 / sampler openai1 textgen6 novel24 kobold6 / sysprompt13 / reasoning5 = 127（按用户决策裁剪至 54 入库，见 HANDOFF 3.7）
- ✅ quick-replies 打包+执行器；moving-ui（界面预设）未打包（官方 isMobile() 禁用，延期见 HANDOFF 6.4）

## 聊天
- ✅ jsonl 基础 + BYAF 聊天导入 + continue nudge
- ✅ 聊天元数据 ChatHeader（chat_metadata：system_prompt/scenario/mes_example/custom_background）+ 书签/存档 + 设置快照（App 已接，见 HANDOFF 3.8）（注：官方无 “chat v2”，此前审计有误已删）

## 表情精灵
- ✅ ExpressionEngine：标签提取/图片元数据/分组排序/chooseSprite（fallback/多立绘/reroll/override），官方差分 14 例
- ✅ sampleClassifyText 文本预处理（句尾/首尾裁剪/LLM trim），官方差分 8 例
- ✅ SpriteStorage：spritesPath/RisuAI 精灵导入（去重/删除字段），官方差分 7 例
- ✅ LLM 分类已接（llmPrompt/parseLlmResponse 对齐官方 + App 生成后异步分类切换）；🟡 本地 BERT/WebLLM 分类未移植；DOM 显示/动画属 App 渲染层（消息头像区已接精灵切换）

## 其它
- 🟡 TokenCounterFactory：OpenAI 精确（JTokkit），Claude/Gemini/Llama 用官方 web tokenizer 未实现
- ✅ 群聊成员激活策略官方差分 15 例；✅ 角色卡合并 8 例；✅ 深度提示 7 例；✅ 完整循环纯逻辑（自动续写/类型/队列）11 例；✅ App 调度层已接（多人回复拼接/组提示/nudge 链，见 HANDOFF 3.13）
- ✅ 数据驱动服务商注册表（22 家）+ 三协议路由 + SSE 流式解析（三种格式）+ 模型列表四种响应格式 + 多连接档案
- ✅ Anthropic/Gemini 请求体 1:1 + 官方差分（12+11 例）；边界：消息转换/预算计算/安全设置由调用方桩传参
- ✅ LLM 客户端（OpenAI 兼容：非流式 + SSE 流式，OkHttp + MockWebServer 验证）+ 连接档案存储
- ✅ SSE 流解析 parseStreamData 官方差分 11 例（OpenAI/Anthropic/Gemini/token/content/thinking/message/not-primary）
- ✅ MediaEngine 媒体纯逻辑官方差分 17 例（type/display/index，含边缘）
- ✅ MediaTokenCost 媒体 token 成本官方差分 18 例（图片 2048→768→512 方格计费、视频 263/秒、音频 32/秒、失败回退）
- ✅ 逐提供商审计已落盘（HANDOFF 3.9）：全部协议路由 + 预算 + 能力管道（tools/json_schema/web_search/图像/安全）已接；Vertex 服务账号认证已做（VertexAuth.kt）；App 接线源码对照见 HANDOFF 4.7，组件选型见 COMPONENTS.md
- ✅ PersonaEngine 状态/临时锁/连接/按聊天解析官方差分 16 例；✅ 人设持久化与管理 UI 已接（PersonaStore 官方全字段 + 顶栏/⋮ 菜单全功能）；🟡 多模型 tokenizer（豁免）
- ✅ 向量服务引擎已齐（RAG/聊天重排/文件向量化 + 持久化）+ App 配置/调用已接线（设置→服务→向量、数据银行 ⋮ 管理）；✅ TTS（系统 TTS + 官方字段）/图像（7 后端）/翻译（8 家）UI 已接；STT 官方 1.18 无

## App 层现状（2026-08-18）
App 层主体已完成并接入引擎（明细见 HANDOFF 第 4/5 节）：聊天页全链（流式/滑动变体/消息操作/冒充/继续/重生成/媒体/思考/上下文胶囊）、聊天 Tab 会话列表（置顶/导出/删除/群聊）、设置主页官方移动端 8 分区、提供商多档案 + 全协议、预设五类管理、正则/世界书/人设/快捷回复/翻译/图像/向量 UI、全局搜索、角色详情编辑、图标 = 官方同源 Font Awesome 6 Solid。剩余为登记的边界与延期项（见 HANDOFF 5/6 节）。

## 差分覆盖总数
现行口径：96 组 / 2984 例官方基准全部通过、引擎 378 测全绿——分组明细与打桩登记以 [DIFF_MATRIX.md](DIFF_MATRIX.md) 为准（本节历史 846 例算式已废）。
