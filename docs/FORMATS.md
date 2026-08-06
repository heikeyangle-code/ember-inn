# 官方数据格式总表（1:1，源码核实：release 8172dcd）

## 角色卡导入（5 种，一个都不能少）

| 格式 | 说明 | 官方实现 |
|---|---|---|
| PNG | V2（`chara` tEXt）/ V3（`ccv3` tEXt），读取优先 ccv3 | importFromPng |
| JSON | V2 / V3 原始 JSON | importFromJson |
| CharX | ZIP：`card.json` + assets（icon/background/voice） | importFromCharX |
| YAML / YML | 旧版 TavernAI YAML（`context`→description、`greeting`→first_mes 等映射） | importFromYaml |
| BYAF | Backyard Archive ZIP（ST 自家格式；宏 `#{user}`→`{{user}}` 等转换） | importFromByaf |

## 角色卡导出（按官方现状 1:1）

| 格式 | 说明 |
|---|---|
| PNG | 同时写 `chara` + `ccv3` 双 chunk；私有字段清理：`fav=false`、`data.extensions.fav=false`、删除 `chat` |
| JSON | 统一转 V2 结构（getCharaCardV2，`spec=chara_card_v2` / `spec_version=2.0`） |
| CharX | **官方当前仅导入不导出**（服务端 /export 只有 png/json）——对齐现状；官方后续若加导出再跟进 |

## 规格版本（读取时的归一逻辑）

- **无 spec / V1（legacy TavernAI）** → convertToV2：`creatorcomment`→creator_notes、`talkativeness`、`depth_prompt_*` 等映射
- **V2**：`spec=chara_card_v2` / `spec_version=2.0`
- **V3**：`spec=chara_card_v3` / `spec_version=3.0`；`data` 嵌套，读取时字段映射回 V2 结构

## 其它数据格式（同样 1:1）

- 聊天：`jsonl`（每行一条消息 JSON）
- 世界书：JSON
- 群聊：JSON
- 预设（AI response formatting / prompt presets）：JSON
- 人设：JSON
- 背景 / 表情精灵 / 快捷回复 / regex / 向量 / 主题：JSON

## 守则

1. 导入全支持：PNG / JSON / CharX / YAML / BYAF，一个都不能少
2. 导出对齐官方现状：PNG（双 chunk）+ JSON（V2 结构）
3. 所有格式过“官方互读”回归测试：官方导入的我们能导，我们导出的官方能导，字段一致
