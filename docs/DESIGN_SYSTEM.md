# EmberInn 2.0 壳层设计系统（EmberDS）规格书

> 状态：定稿，与 REFACTOR_V2_PLAN.md 配套（对应 P3 设计令牌 / P6 壳层重写）
> 回答两个问题：① 官方有没有可抄的？② 参考谁？
> 用户决策：不喜欢现有 UI，整体重做；主题含 UI 组件层

---

## 一、先回答：官方有没有？

**没有可抄的。** SillyTavern 官方是桌面 Web UI（侧边栏+顶栏+密集面板的 PC 布局），直接搬到手机上就是现在各种"ST 安卓套壳"的糟糕体验。官方的价值在**内容区**（style.css/CSS 变量/主题 JSON——内核已原样接管），壳层必须自己设计。

这正是本方案的分工：
```
官方主题 JSON ──→ WebView 内容区（逐字兼容，像素级还原）
EmberDS 设计系统 ──→ Compose 壳层（首页/会话列表/设置/输入栏/弹层）
        两者通过 seed 取色桥接，互不污染
```

## 二、参考谁？（结论 + 学什么不学什么）

| 参考 | 定位 | 学什么 | 不学什么 |
|---|---|---|---|
| **Foreverse**（主参考） | 同品类原生客户端，已被市场验证，且有完整 smali 级逆向资料在手 | 语义色板全部角色、AI 专属三色、ThemeSkin 图片资产层、StChatTheme 聊天区独立主题、浅深双资源集、组件树信息架构 | 商业闭源细节（订阅/举报/时刻卡）；不做它的复刻 |
| **Character.AI** | 沉浸式角色聊天的标杆 | 暗色优先哲学、头像即身份的聊天页、极简 chrome（界面元素退后让内容站前排）、开场即对话 | 西方卡通化视觉；气泡圆角语言照搬 |
| **Material 3 Expressive** | Android 官方动效/形状语言 | 弹簧物理动效（damping 0.6/stiffness 500）、交互时形状形变、大圆角层级表达——作为实现底座 | M3 默认配色派生（正是现状被嫌弃的原因之一） |
| ChatterUI | 功能布局参考 | 设置分组方式、聊天样式微调入口的暴露方式 | 视觉（React Native 默认感） |

**现有 UI 为什么不好看（对照报告 15.5 的诊断）**：
1. 没有 AI 专属色——AI 气泡和普通卡片同色阶，AI 没有视觉身份
2. 文字只有三档层次，画面发闷
3. 主题只是换配色没有"皮"（无图片资产层），切换感知弱
4. 聊天输入栏跟随全局主题，无独立氛围
5. 视觉由 M3 自动派生决定，没有自己的形状/密度性格

## 二点五、补充调研结论（第二轮）

### 官方有什么？

| 资产 | 性质 | 对我们的意义 |
|---|---|---|
| 官方 34 套内置主题（Azure/Cappuccino/Celestial Macaron/Dark Lite…） | 内容区 CSS 变量配色 JSON | 内核直接逐字兼容；选人气高的作为内置内容主题 |
| **Moonlit Echoes 月下回聲**（RivelleDays，社区公认审美标杆） | 扩展+主题JSON 组合；现代/极简/优雅，桌面移动双优 | ① 其主题 JSON 直接可用（已抓取 Glimmer 夜晚模式实测：近黑中性底 rgba(30,30,30) + 蓝引号 #51A0DE + 低透明度气泡）；② 它的 8 种消息布局（Flat/Bubble/Document/Echo/Whisper/Hush/Ripple/Tide）是 CSS 层实现 → **WebView 内核可整包兼容其消息风格 CSS**——这是原生路线永远做不到的，WebView-first 的又一个红利 |
| Mobilyze 扩展 | 把 ST 改造成手机布局（隐藏顶栏、最大化聊天区、自动收起菜单） | 验证移动用户核心诉求=沉浸+大聊天区 → 我们的沉浸模式设计方向正确 |
| r/SillyTavernAI 痛点帖 | 「聊天文字颜色和 UI 颜色绑死」是长期抱怨 | 验证双层主题（内容区≠壳层）正是刚需，我们架构占位正确 |

### 结论：不需要换参考，组合已经最优

- **壳层设计**：Foreverse 语义令牌 + AI 三色 + ThemeSkin 换皮（同品类唯一有完整逆向资料的成熟产品）
- **内容区审美**：官方主题 + Moonlit Echoes 系直接兼容，用户零成本迁移已有审美
- **沉浸哲学**：Character.AI 式暗场舞台 + Mobilyze 验证的最大化聊天区
- **新增动作**：P4 扩展垫片清单加入「Moonlit Echoes 消息风格 CSS 兼容」验证项；内置皮肤与人气主题成对出货（Azure×冷灰蓝 / Glimmer×深夜黑）

### 结论：不需要换参考，组合已经最优

- **壳层设计**：Foreverse 语义令牌 + AI 三色 + ThemeSkin 换皮（同品类唯一有完整逆向资料的成熟产品）
- **内容区审美**：官方主题 + Moonlit Echoes 系直接兼容，用户零成本迁移已有审美
- **沉浸哲学**：Character.AI 式暗场舞台 + Mobilyze 验证的最大化聊天区
- **新增动作**：P4 扩展垫片清单加入「Moonlit Echoes 消息风格 CSS 兼容」验证项；内置皮肤与人气主题成对出货（Azure×冷灰蓝 / Glimmer×深夜黑）

## 二点六、社区审美标杆全景与完全兼容方案（用户决策：以 Moonlit Echoes 为首要标杆，尽量逐字对齐；其余标杆尽量全部兼容）

### 标杆清单

| 层级 | 名称 | 形态 | 兼容机制 | 兼容级别 |
|---|---|---|---|---|
| ★★★ 首要标杆 | **Moonlit Echoes 月下回聲**（RivelleDays，387★） | 扩展（3849 行 style.css + JS）+ 主题预设 JSON | 见下方三层机制 | **逐字级** |
| ★★☆ UI 大改扩展 | Guinevere UI（Bronya-Rand） | 主题包制 UI 大改（HTML/CSS/JS 包） | 扩展垫片 T3 加载其包 | 目标级 |
| ★★☆ UI 大改扩展 | Prome VN（Bronya-Rand） | 现代 VN 模式+世界/角色染色 | VN 模式内核化+垫片 | 目标级 |
| ★☆☆ UI 主题 | LA Theme（LenAnderson） | CSS/Less | CSS 资产包 | 可用级 |
| ★★☆ 纯主题集 | illuminaryidiot 九套（COFFEE/CYBER/GALLERY/Grape Fizz/Woodland/Abyssal Maid/Detective's Log/Jack of Diamonds/Pixel Players，含配套背景图） | 纯主题 JSON+背景图 | 主题直读（已实现） | **100%** |
| ★★☆ 纯主题集 | 官方内置 34 套 | 纯主题 JSON | 主题直读（已实现） | **100%** |

### 三层兼容机制（关键发现）

Moonlit Echoes 的实现已被源码级核实：它的消息风格 = 给 `body` 加类 + 对官方 DOM 选择器写 CSS（`.mes/.mes_text/.mesAvatarWrapper/#chat` 共 130+ 处）。而我们的内核 DOM 与官方同构，因此：

1. **第一层·主题 JSON 直读**（已实现）：所有纯主题文件零改动导入
2. **第二层·官方布局类**（内核 v0.2 新增任务）：官方 `chat_display` 本身就是 body 类——`bubblechat`（气泡）/`documentstyle`（文档）/默认平铺，另加 VN 模式。内核照搬 power-user.js applyChatDisplay 逻辑，成本≈零
3. **第三层·标杆样式资产包**：Moonlit 的 style.css 可近乎逐字作为内核可选样式包加载 + 一个 `setBodyClass()` 垫片（8 种风格：Echo/Whisper/Hush/Ripple/Tide/Bubble/Document/Flat + big-avatars 等）；其 slash 命令由 P4 垫片桥接

### 内置出货策略（换皮商店初始货架）

**用户决策：Moonlit Echoes 整包内置、完全模仿。** 已下载入 `app/src/main/assets/themes/moonlit-echoes/`：

| 文件 | 内容 | 用法 |
|---|---|---|
| Glimmer.json / MoonlitEchoes.json | 2 个官方格式主题 JSON | 主题直读，出厂默认 = Glimmer |
| Glimmer-preset.json | 扩展预设格式（moonlitEchoesPreset） | 壳层配色提取源：customThemeColor #51A0DE、顶栏 rgba(30,30,30)、sheld 底色透明度等 |
| style.css（101KB） | 8 种消息布局 + 全套美化 | 内核样式资产包，body 类切换：echostyle/whisperstyle/hushstyle/tidestyle/ripplestyle/bubblechat/documentstyle/flatchat |
| extension.css（24KB） | 扩展自身 UI 样式 | 随包备用 |
| LICENSE | **AGPL-3.0** | 合规：保留许可文件 + 关于页署名来源仓库；CSS 资产按 AGPL 分发义务处理 |

其余货架组合：

| 组合 | 内容区 | 壳层皮肤 |
|---|---|---|
| 默认推荐 | **[Moonlit] Glimmer**（实测色值：近黑中性底 rgba(30,30,30) + 蓝引号 #51A0DE + 低透明气泡） | 深夜黑 EmberSkin（向 Glimmer 审美 DNA 对齐） |
| 清爽蓝 | Azure（官方） | 冷灰蓝 EmberSkin |
| 其余货架 | Celestial Macaron / Cappuccino / illuminaryidiot 精选 3 套 | 各配对应 EmberSkin |

### 壳层审美向标杆对齐的具体决定（用户要求“壳层也弄好”）

EmberDS 默认深色皮肤的视觉 DNA 直接取自 Glimmer/Moonlit 的成功要素：
- 低饱和近黑中性底（非深蓝紫调），表面靠 1dp 亮度阶梯区分而非色相
- 极细边框 `rgba(255,255,255,0.06~0.1)` 代替重阴影；阴影仅用于浮层
- 小圆角（气泡 14dp/卡 16dp），克制的玻璃模糊（blur_strength 8 档感）
- 强调色少而冷（引号蓝 #51A0DE 级），AI 身份暖金三色作唯一暖色点睛
- 字号克制（font_scale ≈ 0.98–1.0）、行距宽松、留白优先

## 三、EmberDS 设计原则

1. **沉浸优先**：聊天页 = 舞台。chrome（导航/按钮/栏）用玻璃和渐变退到幕后，角色立绘与文字内容站前排
2. **AI 有身份**：`ai` 三色专属角色贯穿 AI 气泡、生成指示、Agent 面板——用户一眼认出"这是它在说话"
3. **暗色优先**：默认深色（#10151C 级别的近黑蓝底），浅色模式为一等公民但非默认
4. **四档墨阶**：ink/inkSoft/inkMute/inkSoft2 的细腻层次代替 M3 三档
5. **皮比色重要**：主题=配色+背景图+卡片框+图标包+启动图+特效，切换主题=换一层皮
6. **有性格**：形状/间距/动效速度是每套主题的可调维度（保留现 ThemePreset 的这个优点），不是全局常量

## 四、EmberTokens 定义

### 4.1 颜色令牌（完整采纳 Foreverse 角色 + 实测色值为内置深色默认）

```kotlin
data class EmberColors(
    // 底面五阶
    val bg: Color,          // 页面底          #FF10151C
    val bgTint: Color,      // 底色染色        #FF171717
    val surface: Color,     // 卡片表面        #FF1A2028
    val surface2: Color,    // 次级表面        #FF222A34
    val surfaceSink: Color, // 凹陷(输入框/搜索)#FF0E131A
    // 墨阶四档
    val ink: Color,         // 主文字          #FFD7DAE0
    val inkSoft: Color,     // 次要            #FF96999E
    val inkMute: Color,     // 弱化            #FF686C73
    val inkSoft2: Color,    // 极弱(时间戳)     #FF4A4F58
    // 线两档
    val line: Color,        // 分隔线          #FF2A3038
    val lineStrong: Color,  // 强分隔/焦点边    #FF3A4048
    // 强调三态
    val accent: Color,      // 主按钮/选中/链接 #FF8FA8BE
    val accentSoft: Color,  // 半透明强调       #5C8FA8BE
    val accentBg: Color,    // 强调背景洗       #1A8FA8BE
    // AI 专属三色 ★核心差异化★
    val ai: Color,          // AI 标识/气泡描边 #FFE9C46A 暖金
    val aiSoft: Color,      // AI 次级          #5CE9C46A
    val aiBg: Color,        // AI 气泡底        #1AE9C46A
    // 语义
    val success: Color,     // #3D8F5A
    val warning: Color,
    val danger: Color,      // #B34A4A
)
```
浅色默认（报告实测）：bg #FCFCFA / surface #E9EAED / ink #171717 / accent #4E6B68 / ai #D9A441。

### 4.2 形状 / 间距 / 动效（主题性格维度）

```kotlin
data class EmberShapes(val cornerCard: Dp, val cornerBubble: Dp, val cornerSheet: Dp, val cornerChip: Dp)
data class EmberSpacing(val unit: Dp, val screenPadding: Dp, val bubbleGap: Dp, val sectionGap: Dp)
data class EmberMotion(val scale: Float, val springDamping: Float = 0.6f, val springStiffness: Float = 500f)
// 每套主题携带这三个维度 → 同一套令牌不同性格（锐利紧凑 vs 圆润舒展）
```

### 4.3 与 Material3 的关系

M3 只是实现底座：EmberColors 映射进 MaterialTheme.scheme（保证 M3 组件自动协调），但**业务组件只允许引用 EmberTokens**，禁止直接 `MaterialTheme.colorScheme.X`。lint 规则强制。

## 五、主题皮肤系统 v2（ThemeSkin）

```
EmberSkin（壳层皮肤包）
├── palette.json      ← 上面的颜色令牌全集（浅/深两份独立）
├── personality.json  ← 形状/间距/动效参数
├── assets/
│   ├── background.(png|jpg|webp) × light/dark   ← 应用背景图 + 透明度 + 适应方式
│   ├── card_frame.9.png × light/dark            ← 九宫格卡片框
│   ├── navbar_icons/ 6tab × normal/selected     ← 底栏图标包
│   └── splash.png                               ← 冷启动图(SplashScreen API)
├── effect.json      ← 粒子特效（可选；低电/减动画自动停用）
└── chat.json        ← ChatAreaTheme 10 字段（输入区独立配色）
                      inputBg/inputText/inputPlaceholder/inputBorder/inputAccent
                      buttonBg/buttonIcon/bottomScrim/topScrim/floatingInput
```

- 格式对齐 `.fvtheme.json` 思路，未来可直接互导 Foreverse 社区主题
- 导入安全校验沿用 Foreverse 经验：压缩比/大小上限/路径遍历检测
- **桥接规则**：导入官方 ST 主题时，从其主色提取 seed 自动生成配套 EmberSkin（壳层自动协调）；反向亦可把 EmberSkin 导出为 ST 主题
- 现有 24 套 ThemePreset 不迁移（用户决策），改为内置 6–8 套全新 EmberSkin（含一套以官方 Azure 为内容主题联动的示范组合）

## 六、组件清单与屏幕信息架构

### 6.1 基础组件库（ui/design/components/）

| 组件 | 说明 | 令牌用法 |
|---|---|---|
| SurfaceCard | 万能卡片（支持九宫格皮框） | surface + cardFrame 皮 |
| GlassBar | 顶/底毛玻璃栏 | bg 半透明 + blurStrength 联动 |
| InkText | 文字四档封装 | ink 四档 |
| AiBubble / UserBubble | 气泡（AI 金描边身份） | aiBg/aiSoft/accent |
| ReasoningPanel | 思考折叠面板 | surfaceSink + inkMute |
| ChipRow / EmberChip | 选择器 chips | accentSoft/accentBg |
| Sheet / Dialog | 底部弹层/对话框 | surface2 + cornerSheet |
| EmptyState | 空态插画位 | inkMute |
| SwipeBack | 手势返回（保留现有实现精华） | motion |

### 6.2 聊天页（对标 Foreverse st_ 组件树，testTag 全套对齐）

```
ChatScreen
├── GlassTopBar：back · 头像+名字+模型 chip · 会话菜单
│   └─ 沉浸模式：上滑淡出，只留渐变遮罩（st_chat_immersive_gradient 同款）
├── MessageList（WebView 池渲染消息本体）
│   ├── 气泡变体：user/glass/expand/image/checkpoint/hidden/empty
│   ├── swipe 左右热区 + 计数指示
│   ├── 生成中：aiSoft 呼吸光 + stop 按钮（st_awaiting_reply 同款）
│   └── 长按 ActionSheet：复制/编辑/重roll/分叉/截断/隐藏/书签/swipe选择器
├── InputArea（ChatAreaTheme 独立配色 + floatingInput 支持）
│   ├── "+"功能面板（发送图片/继续/TTS/…）
│   └── jump-to-bottom 浮标 + 未读跳转
└── Sheets：会话列表(置顶/搜索/归档/检查点/时间线) · 聊天菜单 · 导出
```

### 6.3 首页/书架

- 双 tab 或左右分屏：「聊天」居左、「世界」（角色+世界书）居右（Foreverse 信息架构）
- 角色卡网格：封面图 + 名字 + 最近一句预览，九宫格卡皮
- 搜索/标签筛选常驻顶部 GlassBar

### 6.4 设置

- 分组列表（连接/生成/外观/扩展/数据），每行右侧当前值摘要
- 外观页 = 皮肤商店体验：EmberSkin 卡片预览（缩略图+主色条）一键切换
- 扩展页按 EXTENSION_COMPATIBILITY.md 状态徽章展示

## 七、动效规范

| 场景 | 规格 |
|---|---|
| 页面转场 | 共享轴 X，320ms × motion.scale |
| 气泡入场 | fade+slide-up 8dp，spring(stiffness 500, damping 0.6) |
| 生成中 | aiSoft 色 alpha 呼吸 1.6s 循环 |
| Sheet 弹出 | 底部滑入 + 背景压暗，240ms |
| 主题切换 | 颜色 lerp 400ms；背景图交叉淡化 |
| 长按菜单 | scale 0.92→1 + haptic light |
| 减动画模式 | 全部降为 80ms fade（无障碍） |

## 八、实施落点

| 阶段 | 内容 |
|---|---|
| P3 | EmberTokens + EmberDS 基础组件库 + ThemeSkin 加载器（JSON 层先行） |
| P6 | 五大屏重写（聊天/首页/角色/会话管理/设置）套用 EmberDS；图片资产层与粒子特效 |
| 内置资产 | 新绘 6 套 EmberSkin（2 暗 2 明 2 特色）+ splash |

## 九、验收标准

1. 截图对比：新 UI 与 Foreverse 同场景截图并排评审（布局密度/层次感不落下风）
2. AI 气泡在任意皮肤下都有可辨识的 ai 色身份
3. 切换 EmberSkin = 背景+卡框+图标+启动图+输入区配色整体变化（"换皮"感知）
4. 导入官方 ST 主题后：内容区逐字还原 + 壳层自动协调不违和
5. 全部业务组件零直接 M3 colorScheme 引用（lint 门禁）
