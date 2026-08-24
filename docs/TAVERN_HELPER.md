# 酒馆助手（TavernHelper/JS-Slash-Runner v4.9.3）全量兼容 · 架构与数据映射

> 基线：`~/js-slash-runner-ref`（184 导出函数清单见 `scripts/th-golden/fixtures/th-api-surface.json`，
> 重跑 `node scripts/th-golden/gen-contract.mjs [仓库路径]` 刷新；契约测试 `contract.test.mjs` CI 常驻）。

## 一、架构铁律（用户拍板，不可违反）

1. **扩展自包含**：TH 的逻辑只存在于 `tavern-helper.js`（内核 JS）、`TavernHelperBridge.kt`（宿主桥）、
   `TavernHelperPrefs.kt` + `TavernHelperScreen.kt`（独立设置页）。禁止向 ChatStore/ChatViewModel/
   render.js/st-api-shim 添加任何 TH 专属成员。
2. **宿主能力经接口注入**：`TavernHelperBridge.Host` 由组合根（ChatScreen DisposableEffect）组装。
   新增能力 = 扩展 Host 接口默认方法 + 组合根补一行投影，Store/VM 不感知扩展业务。
3. **数据真值在既有仓**：下表是唯一权威映射。Bridge 只做「官方 JSON 原样搬运」；
   官方形状 ↔ TH 形状的转换一律放 **JS 层**（卡片只见 JS，转换在 JS 才能 1:1 对照 TH 源码）。
4. **1:1 验证三层**：API 契约（fixture 全量名册，硬断言已实现项）/ 纯函数差分（TH dist 同输入对拍）/ 
   端到端真实卡（表格记忆、MVU 变量卡）。

## 二、API 族 → 数据源映射（唯一权威）

| TH 函数族 (数量) | App 数据源（真值） | Bridge 方法面 | 状态 |
|---|---|---|---|
| event* (13) | 内核 eventSource（st-api-shim 官方 EventEmitter 1:1） | 无需桥 | ✅ |
| substitudeMacros/getLastMessageId/getMessageId (3) | MacroEngine（引擎差分锁定）经 macro.substitute | macro.substitute ✅ | ✅ |
| getVariables 族 (6) | chat=chat_metadata.variables / global=GlobalVariableStore | metadata.*+variables.* ✅ | ✅ |
| getChatMessages/set/delete (3) | ChatStore.messages/removeAt/editMessage | th.chat.get/th.message.* ✅ | ✅ |
| createChatMessages/rotateChatMessages (2) | **ChatStore.mutateMessages 通用原语组合**（splice 同构在 Bridge） | th.message.create/rotate ✅ | ✅ |
| worldbook 族 (16) | **WorldStore**(list/entries/save/create/delete/rename/export 原文 JSON) | th.worldbook.names/get/raw/save/create/delete/chatWorld/setChatWorld | 🔨 Batch A |
| lorebook/lorebook_entry 别名 (19) | 同上（TH 两套命名同源，JS 层别名互转） | 复用 th.worldbook.* | 🔨 Batch A |
| displayed_message (3) | render.js formatText 产物 + Kernel.refreshOneMessage（新增 JS API） | 无需新桥（DOM 即真值） | 🔨 Batch A |
| version (2-3) | 常量 '4.9.3' / 官方基线 '1.18.0' | 无需桥 | 🔨 Batch A |
| character/raw_character (15) | **CharacterStore**(list/get/save/delete)+CharacterRecord(官方 card 字段全量) | th.char.list/get/data | ⏳ Batch B |
| persona (9) | PersonaStore | th.persona.* | ⏳ Batch B |
| preset (15) | GenerationPrefs(采样预设)+PromptManagerPrefs | th.preset.*（读族先行） | ⏳ Batch B |
| tavern_regex (5) | 正则脚本存储（RegexScriptSettings 页数据源） | th.regex.get/replace | ⏳ Batch B |
| injectPrompts/uninjectPrompts (2) | 引擎 ExtensionPrompt 注入机制（vectors 已用 KNOWN_RELATIVE）——加通用注入注册表 | th.inject.set/unset | ⏳ Batch B |
| generate/generateRaw/getModelList/stopGeneration (5) | VM 生成管线（/genraw 已接 SlashEngine）→ Host.requestGeneration | th.generate.raw | ⏳ Batch C（跨进程异步，注意流式回传协议） |
| script_buttons (8) | TH 脚本库概念——App 侧新建 ScriptLibraryStore（filesDir/scripts）或先显式不支持 | th.script.* | ⏳ Batch C |
| audio (10) | WebView 自身可播 <audio>；TH AudioManager 面板 Phase D | th.audio.* 或直通 | ⏳ Phase D |
| import_raw (5) | 各 Store 已有 import 能力（WorldStore.importWorld 等）组合 | th.import.* | ⏳ Phase D |
| extension 管理 (9) | 桌面向（装/卸第三方扩展包）——移动端语义由「扩展运行时」替代，显式 unsupported 登记 | — | ⏳ 运行时阶段 |
| listener/builtin/global 初始化 | TH iframe 预热机制——我们的沙箱 iframe 直连 parent，无需等价物 | — | ✅ 免疫 |

## 三、世界书转换规范（Batch A 核心，逐字对照 src/function/worldbook.ts L176-356）

Bridge 只回 `WorldStore.export(name)` 原文（官方 `{"entries":{uid:{...}}}`）；
`tavern-helper.js` 内实现 `toWorldbookEntry/fromWorldbookEntry`：
- name=comment；enabled=!disable；strategy.type=constant|vectorized|selective；
- strategy.keys=key[]（parseRegexFromString：`/pattern/flags` → RegExp，其余原样字符串）；
- keys_secondary.logic 映射 {0:and_any,1:not_all,2:not_any,3:and_all}；scan_depth null↔'same_as_global'；
- position 数值映射 {0:before_character_definition,1:after_character_definition,5:before_example_messages,
  6:after_example_messages,2:before_author_note,3:after_author_note,4:at_depth,7:outlet}（反向同理）；
- role {0:system,1:user,2:assistant}（null 视 0）；probability=useProbability?probability:100；
- recursion{prevent_incoming=excludeRecursion, prevent_outgoing=preventRecursion,
  delay_until=delayUntilRecursion>0?:null}；effect{sticky/cooldown/delay >0?:null}；
- `_default_implicit_keys` 全表 merge（addMemo:true、group:''、characterFilter 等逐字抄源码）。

## 四、施工顺序与验收

- **Batch A（本轮起）**：worldbook 读族+写族、displayed_message、version、getAllVariables。
  验收：契约测试新增硬断言 ≥50 个；jsdom 下 getWorldbook 往返转换 golden 对拍 TH dist。
- **Batch B**：character/persona/preset 读族、regex、inject 注册表。验收：契约 ≥120；MVU 卡冷启动跑通。
- **Batch C**：generate 族（Host.requestGeneration 异步协议+stop）、脚本库。
- **Phase D**：扩展运行时（导入 zip→manifest→按 loading_order 注入内核；扩展官方面板跑在
  WebView 设置宿主里，extension_settings 经桥落盘）、audio 面板。
- 每批一个 commit，契约测试硬断言数只增不减（ratchet）。
