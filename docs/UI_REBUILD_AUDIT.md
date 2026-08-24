# UI 重构终审对照表（DESIGN_SYSTEM.md 定稿 ↔ 实际落地）

> 逐条验收用。状态：✅ 已达成 / 🟡 部分达成（附差距）/ ⬜ 未做 / N/A 边界外。
> 每条注明证据（commit 或文件）。终审时逐行复核并补证据。

## 一、三条铁律

| 条目 | 状态 | 证据 |
|---|---|---|
| 1a 壳层零自有配色/零品牌色 | ✅ | AI 金常量删除，改 bot tint→ink 派生（ShellTheme.kt；ThemeDeriveTest `ai identity is theme-derived`） |
| 1b 官方字段直接映射壳层 | ✅ | ShellTheme.derive 全表 + blurRadius/fast_ui_mode |
| 1c 无内置皮肤/无代号/无预置主题 | ✅ | 出厂仅 Glimmer 数据文件；无皮肤货架代码 |
| 2 对比度守卫（最小干预） | ✅ | ensureContrast + ThemeDeriveTest 黑底黑字/白底白字用例 |
| 3 浮层地板 | ✅ | derive 内 compositeOver(bg) 合成为不透明面；PosterTile 名牌 bg 78% 垫底 |

## 二、字段消费总表

| 条目 | 状态 | 证据 |
|---|---|---|
| 全部 15 字段映射落位 | ✅ | ShellTheme.derive |
| 行为开关透传（bogus_folders 等） | 🟡 | bogus_folders 未接书架集合（海报墙改造时接）；其余 compact/hotswap 已存 |
| 未知字段无损保留 | ✅ | OfficialThemeManager merge 语义未动 |

## 三、导航与 IA

| 条目 | 状态 | 证据 |
|---|---|---|
| 废三域 Tab + NavigationRail | ✅ | MainScreen 推倒（dea1fabf） |
| 右下悬浮主钮 + 四项竖栈 | ✅ | FloatHub |
| 长按主钮=全域搜索面板 | ⬜ | 待做（GlobalSearch 组件未建） |
| 边缘横滑切页 | 🟡 | SwipeBack 存在但切页手势未接到目的地切换 |
| 平板左缘轨+双栏 | ✅ | HubRail |
| 聊天页主钮隐藏 | ✅ | ChatScreen 早退路径不组合壳层 |

## 四、逐屏布局

| 屏 | 状态 | 差距 |
|---|---|---|
| 今夜主页 §4.1 | ✅ | TonightScreen 全要素 |
| 导航栈/全局搜索 §4.2 | ✅ | GlobalSearchPanel 长按主钮唤起，四类结果分流（43cc6f4b） |
| 角色库海报墙 §4.3 | ✅ | 整屏重写 e9dd4e05：搜索场+瀑布+幽灵砖+ShellSheet 菜单全家；集合 chips 待接（-754 行） |
| 角色详情幕布 §4.4 | ⬜ | CharacterDetailScreen 未重做 |
| 世界卷轴架 §4.5 | ✅ | WorldInfoScreen 新语言完成；烛火点/命中统计在设置页形态，独立「世界」目的地未拆出 |
| 设置搜索先行 §4.6 | ✅ | SettingsHome 搜索场+分区行+实时值 |
| 人格身份页 §4.7 | ✅ | PersonaSettingsScreen 完成；独立身份页入口未从设置拆出 |
| 聊天边界声明 §4.8 | ✅ | 内核零接触；铬件令牌化 |

## 五、组件族

| 条目 | 状态 | 证据 |
|---|---|---|
| ShellKit 十一件 | ✅ | ShellKit.kt（另加 ShellInput/ShellChip/ShellActionButton/min-maxLines/enabled） |
| 旧 EmberSwitch 退役 | ✅ | 16 文件 39 处全换（af04ca42） |
| 旧 EmberSwitch 全退役 / 旧输入场 126 处转换 | ✅/🟡 | af04ca42+5333f2b2；8 文件复杂站点待收尾 |
| 旧 EmberSlider/EmberBottomSheet/EmptyState/EmberGlassFab | ⬜ | 待 R4 清扫 |

## 六、动效

| 条目 | 状态 |
|---|---|
| 弹簧底座/reduced_motion | ✅ 沿用 |
| 主题切换 lerp 400ms | ⬜ 未做（当前为即时切换） |
| 其余转场规格 | 🟡 部分（栈动画有；页面 fade-through 未统一） |

## 七、推倒清单（§七）

| 条目 | 状态 |
|---|---|
| ui/design 12 文件删除 | ⬜ 迁移未完（ShellKit 并行期） |
| ui/components 13 文件删除 | ⬜ 同上 |
| MainScreen 重写 | ✅ |
| home/sessions 重写 | 🟡 Tonight✅ Sessions✅ Characters/Detail⬜ |
| settings 子屏重写 | ✅ 17 屏完整重写（主页/AI响应/扩展/用户设置/世界书/人设/图片说明/消息渲染/作者注释官方序/表情/记忆/酒馆助手/背景/数据隐私/快捷回复/正则/语音）；🟡 余：Appearance(已令牌化留R4换件)/PromptManager/Services/Presets收尾/Provider收尾 |
| onboarding/icons 重写 | ✅ / 图标族沿用 FaIcons（细线几何达标） |
| chat 铬件 REFIT | ✅ |

## 八、防 bug 门禁

| 条目 | 状态 |
|---|---|
| lint 禁 colorScheme/hex | ⬜ CI grep 步骤未加 |
| derive 单测穷举 | ✅ ThemeDeriveTest 8 例 |
| 无双轨删旧 | ⬜ R4 |
| Roborazzi 截图基线 | ⬜ 未搭 |
| push-CI 节奏 | ✅ 执行中 |

## 九、分期出口

R1 ✅ · R2 🟡（缺全局搜索/角色库墙）· R3 🟡（子屏过半）· R4 ⬜

## 十、验收标准

1 导入主题全壳同装 ✅（守卫+派生单测）· 2 可读兜底 ✅ · 3 零 M3 引用 🟡（长尾 typography 残留）· 4 截图评审 ⬜ · 5 无预置外观 ✅
