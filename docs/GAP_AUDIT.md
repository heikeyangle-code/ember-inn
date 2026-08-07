# 引擎 vs 官方缺口审计（持续更新）

对照官方 release（~/sillytavern-ref），逐项核对引擎覆盖情况。
✅=已实现且有测试/差分　🟡=部分/边界　❌=未做

## 角色卡
- ✅ PNG/JSON/CharX/YAML/BYAF 导入导出、V2 归一、私有字段清理、PNG 字节差分（6 例）
- 🟡 CharX/BYAF 资源提取（头像/背景/语音）未做（App 层）
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
- 🟡 动态宏注册 API、宏 flags（{{#}}）、完整 MacroEnv（聊天/角色/系统状态）未做

## 斜杠
- ✅ 解析（命名/无名/引号/转义/list 值/rawQuotes）、管道/闭包/双管道、
  /pass /let /qr-arg、{{var}}/{{pipe}}/{{arg}} 状态宏、快捷回复执行器
- 🟡 parser flags（REPLACE_GETVAR 等）、150+ 官方命令（多数要接 App 状态）

## 提示词组装
- ✅ PromptManager 核心、ChatCompletion 嵌套集合、组装管线、bias/override
- ✅ in-chat 深度注入（populationInjectionPrompts）、continue nudge、相对扩展注入 main
- ✅ 工具调用（tool_calls/tool 结果）、control prompts、pin_examples、squash
- 🟡 工具预分配 token、媒体内联、推理签名、多模态
- ✅ continue prefill/nudge、人设 IN_CHAT 注入、作者注释组合（ANWithWI）
- ❌ 群聊完整调度（队列 UI）、工具预分配 token/媒体/推理

## 正则
- ✅ 引擎 + substituteRegex/宏替换 + 13 例差分
- 🟡 global/preset/scoped 分桶与允许列表（App 层）；聊天消息正则已在扫描器接入（messageTransformer）

## 预设
- ✅ 官方 context 34 / instruct 38 / sampler openai1 textgen6 novel24 kobold6 / sysprompt13 / reasoning5 = 127
- ✅ quick-replies 打包+执行器；moving-ui（界面预设）未打包

## 聊天
- ✅ jsonl 基础 + BYAF 聊天导入 + continue nudge
- 🟡 聊天元数据（背景/书签/快照）、chat v2 迁移

## 其它
- 🟡 TokenCounterFactory：OpenAI 精确（JTokkit），Claude/Gemini/Llama 用官方 web tokenizer 未实现
- ✅ 群聊调度核心（SWAP/APPEND/队列）、人设模型+注入、作者注释、快捷回复、聊天元数据模型
- ✅ 数据驱动服务商注册表（22 家，2026-08 联网核实最新模型）+ OpenAI/Anthropic/Google 三协议请求体 + SSE 流式解析（三种格式）+ 模型列表四种响应格式 + 多连接档案
- ✅ LLM 客户端（OpenAI 兼容：非流式 + SSE 流式，OkHttp + MockWebServer 验证）+ 连接档案存储
- ❌ 人设管理（选择/持久化，App 层）、多模型 tokenizer、服务层
- 🟡 向量服务引擎已齐（RAG/聊天重排/文件向量化 + 持久化）；App 层配置/调用未接线；TTS/STT/图像/翻译 仍 P3/P4

## 下一步（README App 层）
- 已完成：提供商管理 UI（列表+详情，参照命理2）、设置主页六分组+搜索、预设主题实时预览、真实对话接线（非流式）
- 待做：聊天 Tab 会话列表、聊天页流式+消息操作+Markdown、世界书编辑 UI、全局搜索、角色详情编辑、角色卡驱动主题、服务层
- 角色卡资源入库（BYAF/CharX 提取已就绪）

## 差分覆盖总数
instruct 36 + 世界书 19 + 世界书扫描 17 + 世界书文件 2 + 正则 13 + PNG 6 +
宏 158 + pick 5 + 编辑器排序 6 + 自动执行选择 4 = 266 例官方基准（全部通过）。
