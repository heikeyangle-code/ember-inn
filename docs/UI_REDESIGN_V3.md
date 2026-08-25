# UI/UX 重构 V3：Premium Editorial × AI Companion（执行总案）

> 本文件是第 1-4 阶段（完整阅读项目 → UI/功能地图 → 官方 IA 研究 → 完整性审计）的产出，
> 并作为后续实施阶段的唯一依据。方向：**Premium Editorial × AI Companion × Immersive Spatial UI**。
> 原则：Simple outside. Deep inside. —— 功能深度不减、信息架构不乱、视觉彻底升级。

---

## 一、现状地图（第 1-2 阶段结论）

### 1.1 架构分层（红线，重构不可触碰）

```
App Shell（Compose，本次重构对象）
 ↓
Chat Shell（ChatTopBar/输入区/弹层——仅外围，内核零接触）
 ↓
Chat Theme（官方主题 JSON → kernel WebView 逐字生效，禁止重设计）
 ↓
Message Renderer / Input（render.js 内核，禁止触碰）
```

- 引擎层与官方 SillyTavern 1.18.0 已 1:1（96 组 / 3040 例差分全绿）。
- 设置 IA 已对照官方移动端 8 分区，~20 个设置子屏全部在位。
- 本次重构 = 外围壳层视觉/交互/层级，引擎与内核 diff 必须为零。

### 1.2 Screen 清单（重构对象盘点）

| Screen | 行数 | 现状 | 处置 |
|---|---|---|---|
| MainScreen | 363 | FloatHub 四目的地（今夜/对话/角色/设置）+ 宽屏 HubRail 双栏 | 重构：升级为三空间自适应壳 |
| TonightScreen | 135 | 时钟问候+英雄卡+海报横排+时间线 | 升级：Editorial 排版/三层视觉权重 |
| CharactersScreen | 397 | 海报墙+搜索+长按菜单 | 升级：筛选/排序/收藏视图 |
| CharacterDetailScreen | 2044 | 官方字段全集编辑器（数据库形态） | **拆分**：角色主页（视觉）+ 编辑器（Power） |
| SessionsScreen | 569 | 会话列表（置顶/导出/删除/群聊） | 升级：Conversation 分离（收藏/归档/分支） |
| ChatScreen | 4579 | 内核+TopBar v4+Context 胶囊+动作表 | 仅外围：Context Sheet/快速档案 |
| SettingsScreen | 457 | 官方 8 分区+搜索+子页栈 | 升级：分组磁贴+值摘要+Progressive Disclosure |
| ~20 设置子屏 | — | 官方 IA 对齐完成 | 保留 IA，统一新组件语言 |

### 1.3 设计系统资产（ShellKit 复用基底）

已存在：ThemeSurface/RowLine/GroupLabel/SearchField/ShellInput/ShellChip/ShellSheet/
ShellActionButton/PosterTile/HeroCard/AvatarCircle/CandleDot/FloatHub/GlobalSearchPanel/
SurfaceCard/GlassBar/EmberChip/ChipRow/InkText 等 ~30 件。
EmberTheme 令牌（colors/shapes/spacing/motion/chat）经 CompositionLocal 下发，
颜色来源 = 官方主题字段推导（ShellTheme.derive）+ 对比度守卫。**该管线保留，仅扩展。**

### 1.4 官方 IA 研究结论（第 3 阶段，源码核实）

官方 index.html 左侧抽屉序（移动端同序）：
1. AI 响应配置（#left-nav-panel L72）→ 内含 API 连接（L2275）/采样预设
2. AI 响应格式化（#AdvancedFormatting L4082）→ context/instruct/系统提示
3. 世界书（#WorldInfo L4662）
4. 用户设置（#user-settings-block L4869）
5. 背景（#Backgrounds L5644）
6. 扩展（#rm_extensions_block L5741）
7. 人设管理（#PersonaManagement L5824）
右侧：角色管理（#right-nav-panel L5993）；wand 菜单 = 扩展快捷入口集合。

**App 现行 SettingsHome 8 分区与此一致（AI 响应/API 连接/高级格式化/世界书/用户设置/背景/扩展/人设）——IA 骨架正确，保留；仅做视觉升级与值摘要。**

## 二、功能完整性审计（第 4 阶段）

依据 docs/SETTINGS_PARITY.md + docs/UI_REBUILD_AUDIT.md + 本次源码复核：

### 2.1 有 UI 且生效（验证通过，禁止回退）
- 全部采样参数编辑器（ProtocolSamplerEditors → SettingsStore → ChatRepository.rawGenerate 消费链完整）
- AppearancePrefs（radius/font/open_last_chat → AppearanceBus → 全局重组）
- 主题/扩展管理（OfficialThemeManager/ExtensionManager，金测试 155+30 例全绿）
- 提供商多档案、预设五类、世界书、正则、人设、快捷回复、TTS/图像/翻译/向量

### 2.2 待修断链/缺口（第 5 阶段修复清单）
| 项 | 状态 | 修法 |
|---|---|---|
| stream_fade_in | 无 UI | 消息渲染设置补开关 |
| send_on_enter | 无 UI | 行为设置补开关 |
| play_message_sound / play_sound_unfocused | 无 UI | 行为设置补开关（SoundPool 两档） |
| spoiler_free_mode | 无 UI | 消息渲染设置补开关 |
| auto_save_msg_edits | 无 UI | 行为设置补开关 |
| quick_continue / quick_impersonate | 无 UI | 行为设置补开关 |
| 主题切换 lerp 400ms | 未做 | 动效阶段补 |
| CharacterDetail「角色主页」形态 | 未做 | 第 9 阶段重构 |
| bogus_folders 书架集合 | 未接 | 第 9 阶段接标签筛选 |
| 宽屏 List-Detail | 手写 Row | 第 14 阶段规范自适应 |

### 2.2.1 第 12 阶段已补齐（第 5 阶段清单收口）
| 项 | 落点 |
|---|---|
| stream_fade_in / send_on_enter / play_message_sound 等 6 项行为开关 | BehaviorPrefs + UserSettingsScreen（第 5 阶段已接线） |
| CharacterDetail「角色主页」形态 | CharacterHomeScreen（Companion）+ CharacterDetailScreen（Power）（第 9 阶段） |
| bogus_folders 书架集合 | CharactersScreen 标签轨道筛选（第 9 阶段） |
| persona_sort_order | PersonaSettingsScreen 排序 Chip（A-Z ⇄ Z-A） |
| fuzzy_search | HomeViewModel.search 加权模糊匹配 + 得分排序 |
| confirm_message_delete | ChatScreen 删除确认门控 |
| restore_user_input | BehaviorPrefs 草稿单键全局存取 + ChatScreen 防抖保存/冷启动恢复 |
| custom_stopping_strings + _macro | BehaviorPrefs → StoppingStringsEngine 接线 + 用户设置 UI |
| show_tag_filters | 书架标签轨道开关（本 App 默认开） |
| aux_field | PosterTile 副标题（character_version/creator） |
| prefer_character_prompt / _jailbreak | ChatPromptFactory 覆盖门控 + 用户设置开关 |
| auto_load_chat | 已有 openLastChat，已生效 |
| sort_field + sort_order（书架 11 档排序） | BehaviorPrefs + CharactersScreen 排序弹层 |
| show_group_chat_queue | ChatViewModel.groupQueue 状态流 + ChatComposerOverlays 队列条 + 用户设置开关 |
| char_list_grid（书架网格/列表视图） | BehaviorPrefs + CharactersScreen 视图切换 Chip + CharacterListRow 列表形态 + 用户设置开关 |

### 2.2.2 第 13 阶段全链路验证（UI → State → Storage → Business 逐字段核销）

**BehaviorPrefs（35 字段全部通过）**：
| 链路组 | 字段 → 消费点 |
|---|---|
| 内核桥接组 | stream_fade_in / gestures / send_on_enter / quick_continue / quick_impersonate / auto_save_msg_edits / markdown_escape_strings / trim_spaces → KernelRuntimeConfig.fromBehavior → KernelWebViewPool.updateRuntimeConfig（revision 总线驱动重发） |
| 生成链组 | user_prompt_bias / show_user_prompt_bias / pin_examples / strip_examples / names_as_stop_strings / prefer_character_prompt / prefer_character_jailbreak / custom_stopping_strings(+macro) → ChatViewModel:3832-3839 → ChatRepository → ChatPromptFactory |
| 清洗链组 | allow_name1/2_display / trim_sentences / disable_group_trimming → ChatViewModel:312/979/4100-4108（cleanUpMessage 管线） |
| 渲染组 | chat_truncation（ChatScreen:698/1135）/ streaming_fps + smooth_streaming 三件套（ChatScreen:937-963 流式 tick）/ spoiler_free_mode（CharacterHomeScreen:82 + CharacterDetailScreen:140）/ message_token_count（ChatViewModel:2219/4199/4285/4363） |
| 交互组 | auto_scroll_chat_to_bottom（ChatScreen:1197）/ confirm_message_delete（删除确认门控）/ restore_user_input（草稿防抖保存+冷启动恢复）/ show_group_chat_queue（ChatScreen:1397 队列条门控） |
| 书架组 | sort_field + sort_order（11 档排序）/ show_tag_filters（标签轨道门控）/ aux_field（副标题字段）/ char_list_grid（海报墙⇄列表）/ fuzzy_search（HomeViewModel.search） |
| 行为组 | play_message_sound + play_sound_unfocused（生命周期门控）/ auto_swipe 四件套（ChatViewModel:4228 → SwipeEngine.generatedTextFiltered） |
| 人设组 | persona_sort_order（PersonaSettingsScreen 排序 Chip） |

**RenderPrefs**：collapse_newlines / example_separator → ChatViewModel 内核桥接；strict_mode → KernelWebViewFactory/Pool JS 开关。
**重进读取**：各设置屏均为 `remember { Prefs.load() }`——重组即重读存储，无陈旧态；跨页生效经 revision 总线（书架/聊天页 collectAsState 重读）。
**实证**：compileDebugKotlin ✅ · testDebugUnitTest 53/53 ✅（AppSlashExecutor/CharacterCardEdit/ChatPromptFactory/DisplayPipeline/GalleryAssetsDiff/TtsTextProcessor/ThemeDerive）。

### 2.3 官方 power_user 字段 N/A 裁定（移动端无对应语义，不设死开关）
| 字段 | 裁定 | 依据 |
|---|---|---|
| auto_connect | N/A | 网页连接档案重建概念；App 按需请求无持久连接态 |
| pin_styles | N/A | style-pins 为聊天渲染内核行为（kernel render.js 冻结区） |
| forbid_external_media + 双 override | N/A | messageFormatting 渲染期过滤（内核冻结区） |
| image_overswipe | N/A | 图片变体越界滑动生成；App 滑动=文本变体 |
| experimental_macro_engine | N/A | App 单一宏引擎，无新旧切换 |
| tag_import_setting | N/A | 无独立全局标签库；标签恒随卡（tagsOf 现算） |
| never_resize_avatars | N/A | 导入原图直存，无裁剪缩放流程 |
| stscript.autocomplete/parser | N/A | 无脚本编辑器 UI |
| sort_rule | N/A | 自定义排序规则构建器（标签/文件夹 Web 专属）；App 用官方 11 档固定排序 |
| console_log_prompts | N/A | 浏览器控制台调试输出；移动端无控制台 |
| relaxed_api_urls | N/A | Web 同源 URL 校验放宽；App 原生 HTTP 客户端无此约束 |
| tag_sort_mode | N/A | 标签管理面板手动排序；App 标签随卡（tagsOf 现算），无独立标签库 |
| show_card_avatar_urls | N/A | 书架显示卡内头像直链（作者排障用）；App 头像为本地文件 |
| servers | N/A | stscript 服务器端点；无脚本运行时 |

### 2.4 禁止事项（对照用户最高约束）
- 不删除任何设置/扩展/服务/预设/世界书/人设/Prompt/Generation 能力
- 不合并设置、不改参数语义
- Chat Renderer/Input/Chat Theme 零接触
- 设置 IA 以官方为准，不主观重新发明

## 三、三体验空间（信息架构总纲）

```
Companion Space（视觉优先·Editorial）     Chat Space（沉浸·内核统治）      Power Space（专业·高密度）
├─ 今夜 Home                            ├─ ChatTopBar v4（保留）          ├─ 设置（8 分区+搜索+值摘要）
├─ 角色库（海报墙+筛选）                  ├─ Context Capsule（保留）        ├─ AI 响应/格式化/预设
├─ 角色主页（Hero+故事轨道）              ├─ Conversation Context Sheet 新   ├─ 提供商/服务/扩展
└─ 对话（故事时间线）                      ├─ 动作表（保留，收敛入口）         ├─ 世界书/人设/正则/QR
                                          └─ 输入区（零接触）                └─ 数据/关于
```

一级导航：**今夜 / 角色 / 对话 / 设置**（FloatHub 保留——隐藏式导航符合 Companion 沉浸定位，
低频能力全部收进设置 Power Space；聊天为独立沉浸空间不占导航位）。

## 四、Design System 扩展规范（第 6 阶段）

保留：EmberTheme 令牌管线、ShellKit 组件族、主题即皮肤三铁律。
新增（editorial 件，全部走令牌，零硬编码色）：

| 组件 | 用途 |
|---|---|
| EditorialHeader | 大标题区（Display 字阶 32-40sp Light + 副文本） |
| SectionRail | 横向内容轨道（横向滚动+轨道头「查看全部」） |
| StatBadges / MetaRow | 角色主页元信息（条目数/最近活动） |
| CharacterHeroBlock | 全幅头图+渐隐入底+头像压缝+主操作 |
| StoryCard | 对话=故事卡片（角色+进度+时间） |
| SettingsTile | 设置分区磁贴（2×2，值摘要副文本） |
| AccordionGroup | 折叠组（Progressive Disclosure 载体） |
| ContextSheet | 聊天上下文面板（角色/人设/世界/模型/预算） |

排版律：Display 32/40sp Light（留白呼吸）· Title 18sp SemiBold · Body 14/13sp ·
Meta 11sp + letterSpacing 0.8sp。间距律：页缘 20dp · 组间 28dp · 组内 8-12dp。
动效律：沿用弹簧底座；reduced_motion 降级；无发光/弹跳/无限动画。

## 五、自适应布局（第 14 阶段，已实施）

- 布局令牌：`WindowWidth{COMPACT,MEDIUM,EXPANDED}` + `windowWidthClass()`（EmberTokens.kt，
  M3 同口径阈值 600/840dp，screenWidthDp 自实现零新依赖，配置变更自动重组）。
- 紧凑（<600dp）：现状 FloatHub 单栏（不变）。
- 中（600-840dp）：内容最大宽 600dp 居中（四主目的地 + 角色主页正文），导航同紧凑。
- 展开（≥840dp）：
  - 导航轨常驻（无聊天时不再只有 FloatHub；轨顶放大镜=全域搜索入口）；
  - **角色库 List-Detail**：导航轨 + 书架列表 | 角色主页同屏（轨高亮恒为角色库空间）；
  - 聊天双栏：导航轨 + 目的地 | 聊天（现状保留）；
  - 全域搜索浮层（searchOverlay 共用 lambda）在三个展开分支均可达。
- 海报墙 `GridCells.Fixed(2)` → `Adaptive(160dp)`：手机仍双列瀑布，宽屏自动加密列数
  ——海报尺寸不随窗口拉宽（"不把手机 UI 拉宽"红线）。

## 六、个性化（第 15 阶段，只影响 App Shell）

AppearancePrefs 扩展（Chat Theme 独立不受影响）：
- 已有：radius 四档 / font 三选
- 新增：shell_density（舒适/紧凑，影响组间距）、motion_level（完整/减弱）、
  home_style（编辑/极简，首页两种密度形态）
- 原则：Preset → Custom → Advanced 三层暴露，不做几十个开关。

## 七、验收（第 17 阶段）

1. `:app:compileDebugKotlin` 绿 + 内核金测试 5 套件全绿（339 例）
2. 功能清单核对：2.2 表逐项落地
3. 视觉验收：三空间各自特性成立（Companion 大图/Chat 沉浸/Power 密度）
4. 回归：设置深链、全局搜索、导入导出、宽屏双栏全部可用
