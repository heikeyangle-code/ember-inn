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
- 🟡 vectorized/addMemo/displayIndex/automationId 保留在 raw JSON，未进强类型；RAG 未消费

## 宏
- ✅ 核心宏 + 官方 e2e 差分 158 例 + {{pick}} seedrandom 逐位一致（5 例）
- ✅ 变量简写全运算符、{{if}}、{{trim}} 作用域、legacy 标记/冒号/空格参数、嵌套参数
- 🟡 动态宏注册 API、宏 flags（{{#}}）、完整 MacroEnv（聊天/角色/系统状态）未做

## 斜杠
- ✅ 解析（命名/无名/引号/转义/list 值）、管道/闭包/双管道、/pass
- 🟡 rawQuotes、命名参数 list 值（存为展开字符串）、parser flags（REPLACE_GETVAR 等）未做
- ❌ 150+ 官方命令（多数要接 App 状态）、/let + {{var}}/{{pipe}}/{{arg}} 联动宏

## 提示词组装
- ✅ PromptManager 核心、ChatCompletion 嵌套集合、组装管线、bias/override
- ✅ in-chat 深度注入（populationInjectionPrompts）、continue nudge、相对扩展注入 main
- ✅ 工具调用（tool_calls/tool 结果）、control prompts、pin_examples、squash
- 🟡 工具预分配 token、媒体内联、推理签名、多模态
- ❌ 群聊完整调度、人设 IN_CHAT 注入（管线待接）

## 正则
- ✅ 引擎 + substituteRegex/宏替换 + 13 例差分
- 🟡 global/preset/scoped 分桶与允许列表（App 层）；聊天消息正则已在扫描器接入（messageTransformer）

## 预设
- ✅ 官方 context 34 / instruct 38 / sampler openai1 textgen6 novel24 kobold6 / sysprompt13 / reasoning5 = 127
- 🟡 quick-replies（UI 脚本）、moving-ui（界面预设）未打包

## 聊天
- ✅ jsonl 基础 + BYAF 聊天导入 + continue nudge
- 🟡 聊天元数据（背景/书签/快照）、chat v2 迁移

## 其它
- 🟡 TokenCounterFactory：OpenAI 精确（JTokkit），Claude/Gemini/Llama 用官方 web tokenizer 未实现
- ❌ 人设管理（数据模型/选择/注入位置深度）、作者注释完整逻辑、快捷回复、群聊调度
- ❌ 服务层：TTS/STT/图像/翻译/向量（路线图 P3/P4）

## 差分覆盖总数
instruct 36 + 世界书 19 + 世界书扫描 17 + 世界书文件 2 + 正则 13 + PNG 6 +
宏 158 + pick 5 = 256 例官方基准（全部通过）。
