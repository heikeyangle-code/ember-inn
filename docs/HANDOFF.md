# 交接清单（会话上下文耗尽时使用）

## 什么是差分验证（新会话先读这段，别让用户再解释一遍）

**目标**：EmberInn 是酒馆兼容软件，引擎逻辑必须和官方 SillyTavern 1:1。
“差分验证” = 同一输入，官方 JS 跑一遍、我们 Kotlin 跑一遍，输出必须一致。
手写期望值的单测只是自证；差分才是“官方说对才算对”的机器验证。

**怎么用**：
1. `scripts/diff/*-official.mjs` 从 `~/sillytavern-ref`（release 分支）逐字提取官方函数，
   桩掉 DOM/全局依赖，生成 fixture：`engine/src/test/resources/diff/*.json`
2. `engine/src/test/.../*DiffTest.kt` 读 fixture，调 Kotlin 引擎逐例对比
3. 官方发版/我们改代码后：`node scripts/diff/*.mjs` 重新生成 fixture → `./gradlew :engine:test`
4. fixture 只能由脚本生成，不许手改；新功能先加 case 再实现

**已覆盖**：instruct（36 例）、world-info matchKeys/getScore/parseDecorators（19 例）、
regex runRegexScript（13 例）、world-info checkWorldInfo 整体扫描（17 例，含两段扫描
sticky/cooldown/概率）、PNG 角色卡读写（6 例）、官方宏 e2e（158 例，含变量简写
全部运算符与括号边界）、{{pick}} 确定性（5 例，seedrandom@3.0.5 逐位一致）、
世界书↔角色书互转（2 例）。

**预设体系**：官方 default/content/presets 已打包进 engine resources
（context 34 / instruct 38 / openai 1 / textgen 6 / novel 24 / kobold 6 /
sysprompt 13 / reasoning 5，共 127 个），PresetLibrary 可加载；官方发版后
`node scripts/build-presets.mjs` 重新生成。
**待覆盖**：宏引擎、slash 解析器、卡片导入导出。

**关键规则**：本地不要编译（用户明确要求），靠 CI `:engine:test` 验证；
runner 恢复后第一件事就是跑 `./gradlew :engine:test :app:assembleDebug`。

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
- **ChatCompletion 重构为官方嵌套集合模型**：稀疏根集合 + MessageCollection、insert/insertAtStart/End、reserve/freeBudget、removeLastFrom、getChat 展平、squashSystemMessages 排除 newChat 等
- **组装管线 ChatCompletionPipeline**：固定顺序提示 + control prompts(impersonate/quiet) + nsfw/jailbreak/用户相对提示 + bias + 相对扩展注入 main + 历史/示例（逆序插入保证时间正序、newChat 最前、群聊 nudge 最后）
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
2. **App 接线（README 下一步）**：ChatViewModel 接 LlmClient（引擎已就绪）、
   提供商三步配置 UI、聊天 Tab/设置页、世界书编辑 UI、全局搜索
3. **引擎边界**：parser flags 完整语义、Claude/Gemini 官方 tokenizer、
   150+ 斜杠命令（需 App 状态）、人设/群聊持久化
4. **差分**：官方发版时重跑 scripts/diff/*.mjs + build-presets.mjs 再全量测试
5. 推送：等用户说推再推

## 注意
- 不要本地编译（用户明确要求）；靠 CI 验证
- 改动要先对照官方源码，能 1:1 就 1:1，近似项必须标注
- 删除类操作先确认
