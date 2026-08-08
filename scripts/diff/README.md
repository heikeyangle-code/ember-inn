# 官方行为差分验证

目标：同一输入下，官方 SillyTavern JS 的引擎函数与 EmberInn 的 Kotlin 引擎输出必须一致。
这是“兼容酒馆 1:1”的唯一可机器验证方式——手写期望值的单测只是自证。

## 目录

- `instruct-official.mjs`：从 `~/sillytavern-ref`（release 分支）读取官方源码，
  用桩环境跑纯函数，生成标准答案 fixture。
- `worldinfo-official.mjs`：世界书纯逻辑（WorldInfoBuffer.matchKeys/getScore、
  parseDecorators、parseRegexFromString/escapeRegex）生成 fixture。
- `worldinfo-scan-official.mjs`：checkWorldInfo 全流程（关键词/常驻/递归/预算/
  min activations/分组/角色标签过滤/sticky/cooldown/delay/概率）生成 fixture。
- `card-png-official.mjs`：PNG 角色卡 write/read（chara/ccv3 双写、旧块清理、
  ccv3 优先、往返）生成 fixture。
- `macros-official.mjs`：从官方 MacroEngine.e2e.js 提取字面用例（基础/参数/嵌套/
  注释/trim/legacy 标记/{{if}}/变量简写运算符/括号边界）生成 fixture。
- `pick-official.mjs`：用官方 seedrandom@3.0.5（vendor）生成 {{pick}} 确定性基准。
- `worldinfo-file-official.mjs`：世界书↔角色书互转（convertWorldInfoToCharacterBook /
  convertCharacterBook）fixture。
- `yaml-import-official.mjs`：逐字提取官方 characters.js importFromYaml + convertToV2 + charaFormatData（yaml/sanitize-filename 用官方同版本 npm 包，已声明在 vendor/package.json）。
- `prepare-prompts-official.mjs`：逐字提取官方 public/scripts/openai.js preparePromptsForChatCompletion（oai_settings/substituteParams/promptManager 按官方语义打桩）。
- `charx-import-official.mjs`：逐字提取官方 src/charx.js CharXParser + characters.js importFromCharX（JSZip v3.10.1 vendor 等价打桩 yauzl）。
- `byaf-macros-official.mjs`：逐字提取官方 src/byaf.js 纯逻辑（replaceMacros/示例/备选开场/角色书转换）。
- `byaf-chat-official.mjs`：逐字提取官方 src/byaf.js getChatFromScenario（Date/encodeURI 打桩）。
- `byaf-card-official.mjs`：逐字提取官方 src/byaf.js getCharacterCard（含 isNSFW 原始真值语义）。
- `prompt-name-official.mjs`：官方 PromptManager isValidName/sanitizeName（COMPLETION 名字清理）。
- `expression-engine-official.mjs`：官方表情精灵纯逻辑（标签/分组/选图）。
- `expression-classify-official.mjs`：官方表情分类文本预处理 sampleClassifyText。
- `group-activation-official.mjs`：官方群聊成员激活策略（自然/列表/池化/滑动/冒充）。
- `group-cards-official.mjs`：官方 APPEND 群聊角色卡合并。
- `group-depth-official.mjs`：官方群聊成员深度提示收集。
- `sprites-storage-official.mjs`：官方精灵存储路径 + RisuAI 精灵导入。
- `character-fields-official.mjs`：官方角色卡字段聚合。
- `json-import-official.mjs`：官方 JSON 角色卡导入（V2/V3/V1/Gradio）。
- `byaf-import-official.mjs`：官方 BYAF 完整导入流程（计划输出）。
- `slash-escape-official.mjs`：官方斜杠转义判定 testSymbol（STRICT_ESCAPING）。
- `prompt-utils-official.mjs`：官方提示词工具 collapseNewlines/parseMesExamples。
- `json-export-official.mjs`：官方 JSON 角色卡导出。
- `sse-stream-official.mjs`：官方 SSE 流解析 parseStreamData。
- `regex-pipeline-official.mjs`：官方正则整体管线 getRegexedString。
- `authors-note-official.mjs`：官方导演备注默认值 + ANWithWI。
- `persona-engine-official.mjs`：官方人设纯逻辑（状态/临时锁/连接/解析）。
- `group-loop-official.mjs`：官方群聊完整循环纯逻辑（自动续写/生成计划）。
- `openai-params-official.mjs`：官方 OpenAI 请求体核心。
- `tool-budget-official.mjs`：官方工具 token 预分配。
- `build-presets.mjs`：把官方 default/content/presets 打包进引擎 resources。
- `../engine/src/test/resources/diff/*.json`：官方输出快照（提交入库）。
- `engine/src/test/kotlin/com/emberinn/engine/prompt/InstructModeDiffTest.kt`：
  读快照，调 Kotlin 引擎，断言一致。
- `engine/src/test/kotlin/com/emberinn/engine/worldinfo/WorldInfoDiffTest.kt`：
  世界书对拍。
- `engine/src/test/kotlin/com/emberinn/engine/worldinfo/WorldInfoScanDiffTest.kt`：
  世界书整体扫描对拍。
- `engine/src/test/kotlin/com/emberinn/engine/card/CardPngDiffTest.kt`：
  PNG 角色卡读写对拍。
- `engine/src/test/kotlin/com/emberinn/engine/macros/MacroDiffTest.kt`：
  宏引擎对拍（环境 name1=User/name2=Character + 变量预置）。
- `engine/src/test/kotlin/com/emberinn/engine/macros/PickDiffTest.kt`：
  {{pick}} 种子公式/随机数逐位对拍。
- `engine/src/test/kotlin/com/emberinn/engine/worldinfo/WorldInfoFileDiffTest.kt`：
  世界书文件/角色书互转对拍。

## 用法

```sh
# yaml-import 首次使用先装 vendor 依赖（yaml/sanitize-filename 官方同版本）
cd scripts/diff/vendor && npm ci && cd ../..

# 官方源码路径默认 ../sillytavern-ref，可用 OFFICIAL_REF 覆盖
node scripts/diff/instruct-official.mjs
node scripts/diff/worldinfo-official.mjs
node scripts/diff/worldinfo-scan-official.mjs
node scripts/diff/card-png-official.mjs
node scripts/diff/macros-official.mjs
node scripts/diff/pick-official.mjs
node scripts/diff/worldinfo-file-official.mjs
node scripts/diff/yaml-import-official.mjs
node scripts/diff/prepare-prompts-official.mjs
node scripts/diff/charx-import-official.mjs
node scripts/diff/byaf-macros-official.mjs
node scripts/diff/byaf-chat-official.mjs
node scripts/diff/byaf-card-official.mjs
node scripts/diff/prompt-name-official.mjs
node scripts/diff/expression-engine-official.mjs
node scripts/diff/expression-classify-official.mjs
node scripts/diff/group-activation-official.mjs
node scripts/diff/group-cards-official.mjs
node scripts/diff/group-depth-official.mjs
node scripts/diff/sprites-storage-official.mjs
node scripts/diff/character-fields-official.mjs
node scripts/diff/json-import-official.mjs
node scripts/diff/byaf-import-official.mjs
node scripts/diff/slash-escape-official.mjs
node scripts/diff/prompt-utils-official.mjs
node scripts/diff/json-export-official.mjs
node scripts/diff/sse-stream-official.mjs
node scripts/diff/regex-pipeline-official.mjs
node scripts/diff/authors-note-official.mjs
node scripts/diff/persona-engine-official.mjs
node scripts/diff/group-loop-official.mjs
node scripts/diff/openai-params-official.mjs
node scripts/diff/tool-budget-official.mjs
node scripts/build-presets.mjs
```

重新生成快照后，`./gradlew :engine:test` 跑全部对比测试。
官方发版时：重新生成快照 → 跑测试 → 红的就是需要移植/修正的差异。

## 规则

- fixture 只能由官方脚本生成，不要手改。
- 新功能先加 case 再实现，实现以通过差分为准。
- 官方桩环境只 stub 全局状态（name1/name2/selected_group/power_user 等），
  函数体保持逐字取自官方源码。
