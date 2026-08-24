# 设置项对照官方审计（power_user 域 · 持续更新）

> 方法：抽取官方 `public/script.js` + `scripts/**` 全部 `power_user.*` 引用键（143 个），
> 与 App 存储层自动比对，未命中的 61 个人工 triage 成三类。
> 口径：**等价**=语义已被其他实现覆盖；**缺口**=官方有而 App 无，列入待办；**登记**=有意不实现（附原因）。

## 一、真缺口（按影响排序；✅=已落地）

| 官方键 | 说明 | 状态 / 补法 |
|---|---|---|
| `smooth_streaming` + `_speed` + `_no_think` | 流式打字机平滑输出 | ✅ 逐字揭示移植 sse-stream.js L93-105（延迟公式+标点加权），设置入用户设置「流式输出」组；no_think 登记边界：reasoning 独立通道本就不平滑 |
| `auto_scroll_chat_to_bottom` | 新消息自动滚底开关 | ✅ 官方同键名入 BehaviorSettings，生成开始不再强制贴底（script.js:2715 同语义） |
| `world_import_dialog` | 导入世界书确认对话框 | ✅ 导入暂存+AlertDialog 确认（同名覆盖提示） |
| `stream_fade_in` | 流式文本渐显 | 待做：内核流式行 CSS 过渡 |
| `send_on_enter` | 回车发送开关 | 待做：输入区已进内核 DOM，需经桥下发 power_user 标志 |
| `play_message_sound` / `play_sound_unfocused` | 消息音效 | 待做：SoundPool 两档 |
| `allow_name1_display` | 用户名前缀剥离开关 | **改判引擎差分级**：官方消费点在 Generate 响应清理（L6437/6512 wrongName），对应引擎 CleanUpMessage——PROTECTED 区须走差分流程，不可 UI 直补 |
| `spoiler_free_mode` | 防剧透模式 | 待做：消息折叠态 |
| `auto_save_msg_edits` | 编辑自动保存 | 待做 |
| `quick_continue` / `quick_impersonate` | 快捷继续/冒充钮 | 待做：输入区动作位 |
| 标签域五键 | 标签管理器 | 待做（书架筛选数据源） |
| `sort_order` | 角色列表排序 | 待做：书架排序选择器 |
| `disable_group_trimming` | 群聊不裁剪成员卡 | 待做：GroupLoop 开关透传 |
| `wi_key_input_plaintext` | 世界书 key 纯文本输入 | 待做 |
| `persona_sort_order` | 人设排序 | 待做 |
| `fuzzy_search` | 模糊搜索权重 | 待做：全域搜索面板接 Fuse 口径 |

## 二、等价覆盖（键名不同或由其他机制承担）

| 官方键 | 我方等价物 |
|---|---|
| `auto_load_chat` | AppearancePrefs.openLastChat（启动进入上次聊天） |
| `custom_stopping_strings(_macro)` | StoppingStrings 引擎自定义停止串（差分锁定） |
| `prefer_character_prompt/jailbreak` | CharacterCardFieldsEngine fields.system/jailbreak（chat_metadata 同名优先） |
| `confirm_message_delete` | 删除二次确认已做（消息/预设/世界书条目） |
| `token_padding` | TokenBudgetEngine reserveBudget |
| `console_log_prompts` | dryRun 预览 + Itemization 分节面板（移动端无 console 语义） |
| `persona_description_depth/role` | 人设注入位置 0/2/3/4/9 全接 |
| `servers` | ProviderStore 多档案（connection-manager 等价替代，登记） |
| `auto_connect` | 冷启动应用当前采样预设 + 活动档案即连语义 |
| `custom_stopping_strings_macro` | 停止串宏替换随总装链路 |

## 三、登记不实现（附原因）

| 官方键 | 原因 |
|---|---|
| `movingUI` / `movingUIPreset` / `movingUIState` | 用户豁免项（HANDOFF §6.4 延期） |
| `experimental_macro_engine` | 官方实验旗标，宏引擎已自研 1:1 |
| `forbid_external_media` + overrides ×2 | CSP 全放行为用户决策（V2 §5.3 安全模型） |
| `fastui_bg_color` | fast_ui_mode 已接；背景色随主题 blur_tint（无自有配色铁律） |
| `charListGrid` / `pin_styles` / `never_resize_avatars` / `image_overswipe` / `show_card_avatar_urls` | 书架已是海报墙范式（本重构 IA 决策）；其余为桌面布局细节 |
| `enable_md_hotkeys` / `enable_auto_select_input` / `relaxed_api_urls` | 桌面键盘/URL 放宽细节，移动端无对应场景 |
| `removeXML` / `markdown_escape_strings` / `restore_user_input` / `aux_field` | 官方遗留兼容键或内部字段（core 不暴露 UI），随引擎差分口径处理 |
| `stscript` | SlashEngine 即 1:1 STscript 本体（此键为官方调试开关） |
| `show_group_chat_queue` | natural/pooled 激活队列提示已在群聊调度内呈现 |
| `persona_allow_multi_connections` / `persona_show_notifications` | 连接绑定为人设详情单选（ProviderScreen 多档案语义），通知属桌面冗余 |

## 四、后续

1. 「真缺口」表逐项按官方位点补齐（每项：先 grep 官方消费点 → 实现 → 登记）
2. 其余设置域（context/instruct/sysprompt/reasoning/openai 采样等）同法抽取比对，追加成节
3. 本文件与 GAP_AUDIT.md 分工：GAP 管**引擎 vs 官方**，本文件管**App 设置面 vs 官方**
