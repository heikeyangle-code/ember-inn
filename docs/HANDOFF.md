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

## 剩余工作（按顺序）
1. instruct 模式（格式指令/上下文模板，对照 script.js + instruct-mode.js）
2. PromptManager 用户自定义顺序持久化 + injection 位置/深度
3. **逐字节 1:1 验证**（跑官方 JS 同输入对比输出）+ 全量审计
4. UI 按 README 严谨收尾：聊天 Tab/设置页、真实模型对话（提供商三步配置）、角色详情世界书编辑、人设/预设、全局搜索、真毛玻璃/氛围渐变、CharX/BYAF 资源提取
5. CI：GitHub runner 卡死（两个仓库都 queued），恢复后跑 `./gradlew test assembleDebug`，红灯就修

## 注意
- 不要本地编译（用户明确要求）；靠 CI 验证
- 改动要先对照官方源码，能 1:1 就 1:1，近似项必须标注
- 删除类操作先确认
