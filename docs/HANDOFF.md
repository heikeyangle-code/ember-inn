# 交接清单（会话上下文耗尽时使用）

## 仓库
- 项目：`ember-inn`（已公开，github.com/heikeyangle-code/ember-inn）
- 本地：`/data/data/com.termux/files/home/ember-inn`
- 官方源码参照：`/data/data/com.termux/files/home/sillytavern-ref`（release）
- 分支：main；**本地有 5 个待推提交**（用户先不让推，说推再推）：
  1. offset 编译修复（MacroEngine 空格参数 pass）
  2. ChatHistoryPopulator（预留 newChat 预算 + 逆序插入 + 预算停止）
  3. DialogueExamplesPopulator（newChat + system 组 + 整组预算 + marker 后插入）
  4. 扩展提示注入（summary/AN/vectors/chromadb/persona/未知扩展顺序）
  5. docs/HANDOFF.md

## 已完成（引擎，均有官方源码对照 + 测试）
- PNG/JSON/CharX/YAML/BYAF 导入、V2 归一、私有字段清理、JSON 导出
- 世界书全套：buffer/matchKeys/扫描核心/时间效果/分组/角色过滤/装饰器/哈希/多世界合并
- 聊天 jsonl + 官方消息字段；正则引擎；斜杠解析/管道/闭包
- 宏引擎：核心宏 + {{if}} + 变量宏 + 字段宏 + 聊天/状态宏 + pick 的 seedrandom 逐位移植
- 提示词组装核心：StoryString 渲染（默认模板）、formatWorldInfo、systemPrompts 顺序、setOpenAIMessages、PromptCollection（默认集合/顺序/合并 marker）、ChatCompletion（预算/溢出/squash）、ChatHistoryPopulator、DialogueExamplesPopulator、扩展注入
- UI：首页角色列表（搜索/AI对话/网格/长按菜单/字段/导出/删除）、聊天页（消息流/气泡/占位回复）、角色卡取色 seed

## 已补做（本轮，均未推送）
- **CI 修复**：build.yml 监听 engine/** 并新增 :engine:test job（此前引擎提交从不触发构建）
- **差分验证工具**：scripts/diff/instruct-official.mjs 从官方源码逐字提取函数生成 fixture（36 用例），InstructModeDiffTest 逐用例对比 Kotlin 输出
- **instruct 模式**：InstructModels（@Serializable + 官方字段名）+ InstructMode（formatChat/StoryString/Examples/Prompt/stoppingSequences/createRawPrompt/formatHistoryItem/ExampleParser）
- **PromptManager 核心**：PromptManagerCore（默认/用户顺序、enabled、injection_trigger、preparePrompt original/groupOverride、mergeSystemPrompts role/injection 覆盖）+ PromptItems 集合 + PromptOrderList 持久化模型
- **修复的确定 bug**：
  - MacroEngine 引用未定义 monthDayYear → 补常量 + 固定 en-US 格式
  - WorldInfoScannerTest 提前闭合类 → 后面 5 个测试从未运行
  - app 未依赖 :engine 模块 → 全项目从未编译过
  - contentOrNull 扩展缺导入（HomeViewModel/CharacterCardExporter）
  - V2 归一：chat 被 toString 加引号、data 被丢弃 → 对齐 readFromV2；UI 导出改走 CharacterCardExporter
  - 世界书：概率检查缺 sticky 跳过；delayUntilRecursion:true 被解析成 0
  - ChatCompletion：超预算应抛 TokenBudgetExceededError（原实现置标志继续）
  - 扩展注入标识符对齐官方（summary/authorsNote/vectorsMemory/vectorsDataBank/smartContext）
  - 宏引擎：{{trim}} 遗留语义（删除前后换行）、instruct/context/system 宏注册

## 剩余工作（按顺序）
1. **CI 恢复后跑** `./gradlew :engine:test :app:assembleDebug`（runner 此前 queued），红灯就修
2. PromptManager 接入 app：用户顺序编辑/持久化 UI + 角色级 prompt_order
3. **差分验证扩展到其它模块**：✅ 已覆盖 world-info 的 matchKeys/getScore/parseDecorators（19 例）、regex runRegexScript（13 例，含 substituteRegex/宏替换/trim）；剩余 checkWorldInfo 整体流程/getSortedEntries/timed effects、宏引擎、slash、卡片导入导出；官方发版时重生成 fixture
4. 全量 1:1 审计：PromptAssembler（bias/systemPromptOverride/jailbreakOverride）、ChatHistory/DialogueExamples 逐行对照、世界书 filterByInclusionGroups 细节
5. UI 按 README 严谨收尾：聊天 Tab/设置页、真实模型对话（提供商三步配置）、角色详情世界书编辑、人设/预设、全局搜索、真毛玻璃/氛围渐变、CharX/BYAF 资源提取
6. 推送：等用户说推再推

## 注意
- 不要本地编译（用户明确要求）；靠 CI 验证
- 改动要先对照官方源码，能 1:1 就 1:1，近似项必须标注
- 删除类操作先确认
