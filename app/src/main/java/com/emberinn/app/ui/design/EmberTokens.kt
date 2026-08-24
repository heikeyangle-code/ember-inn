package com.emberinn.app.ui.design

import com.emberinn.app.ui.design.EmberTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * EmberDS 设计令牌（docs/DESIGN_SYSTEM.md §四，定稿）。
 *
 * 分工铁律：官方主题 JSON → WebView 内容区（逐字兼容）；EmberTokens → Compose 壳层。
 * 两者经 seed 取色桥接（EmberTheme 的 accentOverride/stageTint），互不污染。
 *
 * 业务组件禁止直接引用 MaterialTheme.colorScheme（DESIGN_SYSTEM §4.3），
 * 一律经 [EmberTheme.colors] / [EmberTheme.shapes] 等取令牌。
 */

/**
 * 颜色令牌全集（Foreverse 角色命名 + 实测色值为内置默认）。
 * 底面五阶靠 1dp 亮度阶梯区分而非色相；墨阶四档代替 M3 三档；
 * AI 专属三色是核心差异化——AI 气泡/生成指示/Agent 面板贯穿使用。
 */
data class EmberColors(
    // 底面五阶
    val bg: Color,          // 页面底
    val bgTint: Color,      // 底色染色
    val surface: Color,     // 卡片表面
    val surface2: Color,    // 次级表面
    val surfaceSink: Color, // 凹陷(输入框/搜索)
    // 墨阶四档
    val ink: Color,         // 主文字
    val inkSoft: Color,     // 次要
    val inkMute: Color,     // 弱化
    val inkSoft2: Color,    // 极弱(时间戳)
    // 线两档
    val line: Color,        // 分隔线
    val lineStrong: Color,  // 强分隔/焦点边
    // 强调三态
    val accent: Color,      // 主按钮/选中/链接
    val accentSoft: Color,  // 半透明强调
    val accentBg: Color,    // 强调背景洗
    // AI 专属三色 ★核心差异化★
    val ai: Color,          // AI 标识/气泡描边
    val aiSoft: Color,      // AI 次级
    val aiBg: Color,        // AI 气泡底
    // 语义
    val success: Color,
    val warning: Color,
    val danger: Color,
)

/** 形状性格：每套皮肤可调维度（锐利紧凑 vs 圆润舒展）。 */
data class EmberShapes(
    val cornerCard: Dp,
    val cornerBubble: Dp,
    val cornerSheet: Dp,
    val cornerChip: Dp,
)

/** 间距节奏。 */
data class EmberSpacing(
    val unit: Dp,
    val screenPadding: Dp,
    val bubbleGap: Dp,
    val sectionGap: Dp,
)

/** 动效速度：M3 Expressive 弹簧物理为底座（damping 0.6 / stiffness 500）。 */
data class EmberMotion(
    val scale: Float = 1f,
    val springDamping: Float = 0.6f,
    val springStiffness: Float = 500f,
) {
    /** 页面转场 320ms × scale（§七）。 */
    val pageMs: Int get() = (320 * scale).toInt().coerceIn(120, 640)
    /** Sheet 弹出 240ms × scale（§七）。 */
    val sheetMs: Int get() = (240 * scale).toInt().coerceIn(100, 480)
    /** 减动画模式统一 80ms fade（§七 无障碍）。 */
    val reducedMs: Int get() = 80
}

/**
 * 聊天区独立配色（§五 chat.json 十字段）：输入区不跟随全局主题，
 * 有自己的氛围（Foreverse StChatTheme 语义）。
 */
data class ChatAreaTheme(
    val inputBg: Color?,
    val inputText: Color?,
    val inputPlaceholder: Color?,
    val inputBorder: Color?,
    val inputAccent: Color?,
    val buttonBg: Color?,
    val buttonIcon: Color?,
    val bottomScrim: Color?,
    val topScrim: Color?,
    val floatingInput: Boolean,
)
