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
- `../engine/src/test/resources/diff/*.json`：官方输出快照（提交入库）。
- `engine/src/test/kotlin/com/emberinn/engine/prompt/InstructModeDiffTest.kt`：
  读快照，调 Kotlin 引擎，断言一致。
- `engine/src/test/kotlin/com/emberinn/engine/worldinfo/WorldInfoDiffTest.kt`：
  世界书对拍。
- `engine/src/test/kotlin/com/emberinn/engine/worldinfo/WorldInfoScanDiffTest.kt`：
  世界书整体扫描对拍。

## 用法

```sh
# 官方源码路径默认 ../sillytavern-ref，可用 OFFICIAL_REF 覆盖
node scripts/diff/instruct-official.mjs
node scripts/diff/worldinfo-official.mjs
node scripts/diff/worldinfo-scan-official.mjs
```

重新生成快照后，`./gradlew :engine:test` 跑全部对比测试。
官方发版时：重新生成快照 → 跑测试 → 红的就是需要移植/修正的差异。

## 规则

- fixture 只能由官方脚本生成，不要手改。
- 新功能先加 case 再实现，实现以通过差分为准。
- 官方桩环境只 stub 全局状态（name1/name2/selected_group/power_user 等），
  函数体保持逐字取自官方源码。
