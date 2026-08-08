# 引擎 vs 官方缺口审计（持续更新）

## 差分覆盖审计（2026-08-08）

**已差分：54 组 / 846 例官方基准，全部通过；引擎 259 测全绿。**

### 覆盖充分的模块
- 宏（158）、世界书（19+17+2）、正则（20+10）、PNG（6）、角色卡导入导出（YAML 5 / JSON 15 / CharX 9 / BYAF 35）、提示词组装（preparePrompts 7 + 历史/示例 9 + 角色卡字段 8 + 提示词工具 9）、Anthropic/Gemini（23）、OpenAI 请求体全厂商（21）、SSE（11）、表情（19+8+9）、群聊（15+8+7+11）、名字规则（28）、斜杠转义（13）、导演备注（7）、人设引擎（16）

### 覆盖不足 / 只有手写测试或源码对照
- LlmClient 网络层、ChatCompletionPipeline 整体、官方 tokenizer、向量库持久化、斜杠完整 parser、快捷回复/预设库、作用域宏配对（chevrotain CST）

### 已知边界与潜在风险（后续优先补）
- 正则：JS 特有 flags x/X/u/U/A/J 未实现；trimStrings characterOverride 已接（官方差分覆盖）；RegexPipeline 宏替换已透传，App 层需接 MacroEngine
- SSE：choices.delta.content 数组 thinking、choices.message.content、not-primary 抛错已补；Cohere 完整分支与 choices.message 工具调用仍边界
- 人设：resolve 的 autoLock 已按 persona_auto_lock 计算（官方差分覆盖）
- 群聊：shouldAutoContinue 未含 is_send_press/abortController 状态；多人回复拼接/nudge 链属 App 调度
- 角色卡：V2Normalizer 仍可能遇到更多缺失字段组合；BYAF/CharX 文件冲突未穷举
- 表情：Fuse 模糊匹配、本地 BERT/WebLLM 分类未移植
- 向量：emoji 码点近似、FileVectorStore 持久化未官方差分

对照官方 release（~/sillytavern-ref），逐项核对引擎覆盖情况。
✅=已实现且有测试/差分　🟡=部分/边界　❌=未做

## 角色卡
- ✅ PNG/JSON 导入导出 + CharX/YAML/BYAF 导入全部对齐官方；JSON 导入 5 例 + 导出 4 例、YAML 3 例、CharX 5 例、BYAF 14+5+4+完整导入计划 4 例；V2 归一（官方差分 5 例 + 多轮补真 bug）、私有字段清理、PNG 字节差分（6 例）
- ✅ CharX 资源提取（引擎 CharXImporter.CharXAssets：icon/assets，官方差分覆盖 uri 映射/storageCategory/baseName）；✅ BYAF 资源提取（getCharacterImages/getChatBackgrounds 官方差分 6 例，url-join 不折叠 ../）；App 层资源入库未做
- ❌ URL 导入（App 层）

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
- 🟡 群聊调度核心已实现（SWAP/APPEND/队列），完整调度（App 联动）待做；工具预分配 token/媒体内联/推理签名边界

## 正则
- ✅ 引擎 + substituteRegex/宏替换 + 20 例差分（g/首匹配/i/m/s/非法 flags）
- ✅ getRegexedString 整体管线官方差分 9 例（placement/markdown/prompt/编辑/深度/禁用扩展）
- 🟡 global/preset/scoped 分桶与允许列表（App 层）；聊天消息正则已在扫描器接入（messageTransformer）

## 预设
- ✅ 官方 context 34 / instruct 38 / sampler openai1 textgen6 novel24 kobold6 / sysprompt13 / reasoning5 = 127
- ✅ quick-replies 打包+执行器；moving-ui（界面预设）未打包

## 聊天
- ✅ jsonl 基础 + BYAF 聊天导入 + continue nudge
- 🟡 聊天元数据（背景/书签/快照）（注：官方无 “chat v2”，此前审计有误已删）

## 表情精灵
- ✅ ExpressionEngine：标签提取/图片元数据/分组排序/chooseSprite（fallback/多立绘/reroll/override），官方差分 14 例
- ✅ sampleClassifyText 文本预处理（句尾/首尾裁剪/LLM trim），官方差分 8 例
- ✅ SpriteStorage：spritesPath/RisuAI 精灵导入（去重/删除字段），官方差分 7 例
- 🟡 分类（本地 BERT/LLM/WebLLM 的请求与 Fuse 模糊匹配）、DOM 显示/动画/上传 UI 属 App/服务层

## 其它
- 🟡 TokenCounterFactory：OpenAI 精确（JTokkit），Claude/Gemini/Llama 用官方 web tokenizer 未实现
- ✅ 群聊成员激活策略官方差分 15 例；✅ 角色卡合并 8 例；✅ 深度提示 7 例；✅ 完整循环纯逻辑（自动续写/类型/队列）11 例；🟡 App 调度（多人回复拼接/组提示/nudge 链）待做
- ✅ 数据驱动服务商注册表（22 家）+ 三协议路由 + SSE 流式解析（三种格式）+ 模型列表四种响应格式 + 多连接档案
- ✅ Anthropic/Gemini 请求体 1:1 + 官方差分（12+11 例）；边界：消息转换/预算计算/安全设置由调用方桩传参
- ✅ LLM 客户端（OpenAI 兼容：非流式 + SSE 流式，OkHttp + MockWebServer 验证）+ 连接档案存储
- ✅ SSE 流解析 parseStreamData 官方差分 11 例（OpenAI/Anthropic/Gemini/token/content/thinking/message/not-primary）
- ✅ MediaEngine 媒体纯逻辑官方差分 17 例（type/display/index，含边缘）
- ✅ MediaTokenCost 媒体 token 成本官方差分 18 例（图片 2048→768→512 方格计费、视频 263/秒、音频 32/秒、失败回退）
- ✅ 逐提供商审计已落盘（HANDOFF 3.9）：全部协议路由 + 预算 + 能力管道（tools/json_schema/web_search/图像/安全）已接；只剩 Vertex 认证（搁置）；App 接线源码对照见 HANDOFF 4.7，组件选型见 COMPONENTS.md
- ✅ PersonaEngine 状态/临时锁/连接/按聊天解析官方差分 16 例；🟡 人设持久化与管理 UI（App 层）、多模型 tokenizer、服务层
- 🟡 向量服务引擎已齐（RAG/聊天重排/文件向量化 + 持久化）；App 层配置/调用未接线；TTS/STT/图像/翻译 仍 P3/P4

## 下一步（README App 层）
- 已完成：提供商管理 UI（列表+详情，参照命理2）、设置主页六分组+搜索、预设主题实时预览、真实对话接线（非流式）
- 待做：聊天 Tab 会话列表、聊天页流式+消息操作+Markdown+媒体渲染（Coil3/Media3，组件见 COMPONENTS.md）、世界书编辑 UI、全局搜索、角色详情编辑、角色卡驱动主题、服务层；接线时按 HANDOFF 4.7 的官方源码对照逐项接
- 角色卡资源入库（BYAF/CharX 提取已就绪）

## 差分覆盖总数
instruct 36 + 世界书 19 + 世界书扫描 17 + 世界书文件 2 + 正则 13 + PNG 6 +
宏 158 + pick 5 + 编辑器排序 6 + 自动执行选择 4 + 向量工具 14 + 角色卡 V2 归一 5 + 正则解析 9 + 正则 20 + 作用域宏裁剪 7 + Anthropic 请求体 12 + Gemini 请求体 11 + 聊天历史填充 5 + 示例对话填充 4 + YAML 导入 5 + 提示词组装合并 7 + CharX 导入 9 + BYAF 纯逻辑 14 + BYAF 聊天导入 5 + BYAF 角色卡组装 4 + 名字规则 28 + 表情精灵 19 + 表情分类预处理 8 + 群聊激活 15 + 群聊角色卡 8 + 群聊深度提示 7 + 群聊完整循环 11 + 精灵存储 9 + 角色卡字段 8 + JSON 导入 10 + BYAF 完整导入 8 + 斜杠转义 13 + 提示词工具 9 + JSON 导出 6 + SSE 流解析 11 + 正则管线 10 + 导演备注 7 + 人设引擎 16 + OpenAI 请求体 21 + 工具预算 4 + 管线计划 5 + 媒体附件 17 + 媒体内联 7 + 媒体内容块转换 25 + 消息转换整链 41 + 缓存深度 7 + 其余提供商转换器 61 + 媒体 token 成本 18 + 特殊协议请求体 23 + OpenAI 文本补全 6 + BYAF 资源 6 + 提示词总装整链 11 = **846 例官方基准（全部通过）**。
