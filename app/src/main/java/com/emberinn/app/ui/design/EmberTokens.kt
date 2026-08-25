package com.emberinn.app.ui.design

import com.emberinn.app.ui.design.EmberTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
) {
    /** 主题切换 lerp（第 16 阶段）：全套令牌向目标调色板线性插值（400ms 转场用）。 */
    fun lerpTo(other: EmberColors, t: Float): EmberColors = EmberColors(
        bg = lerp(bg, other.bg, t),
        bgTint = lerp(bgTint, other.bgTint, t),
        surface = lerp(surface, other.surface, t),
        surface2 = lerp(surface2, other.surface2, t),
        surfaceSink = lerp(surfaceSink, other.surfaceSink, t),
        ink = lerp(ink, other.ink, t),
        inkSoft = lerp(inkSoft, other.inkSoft, t),
        inkMute = lerp(inkMute, other.inkMute, t),
        inkSoft2 = lerp(inkSoft2, other.inkSoft2, t),
        line = lerp(line, other.line, t),
        lineStrong = lerp(lineStrong, other.lineStrong, t),
        accent = lerp(accent, other.accent, t),
        accentSoft = lerp(accentSoft, other.accentSoft, t),
        accentBg = lerp(accentBg, other.accentBg, t),
        ai = lerp(ai, other.ai, t),
        aiSoft = lerp(aiSoft, other.aiSoft, t),
        aiBg = lerp(aiBg, other.aiBg, t),
        success = lerp(success, other.success, t),
        warning = lerp(warning, other.warning, t),
        danger = lerp(danger, other.danger, t),
    )
}

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

/** 单档类型：字号 + 行高 + 字重 + 字距（第 16 阶段 Typography 审计单一真相源）。 */
data class EmberType(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val letterSpacing: TextUnit = 0.sp,
)

/**
 * 壳层类型比例（第 16 阶段，V3 §四排版律）：12 档（含组件库实存值归档）。
 * display 32/40 Light（页面问候）· displaySmall 24/30 SemiBold（页面题/品牌/标语/紧凑问候）·
 * heroBig 30/38 Light（英雄卡大题）· hero 26/32 Light（海报名）·
 * title 18/24 SemiBold（区块题）· head 16/22 SemiBold（空态题/弹层题/编辑器字段题）·
 * subhead 15/20 Medium（卡片题/行题）· body 14/20（正文）·
 * bodySmall 13/19（副正文/预览）· caption 12/16（辅助说明）·
 * meta 11/14 + ls 0.8（时间戳/极弱）· micro 10/13 + ls 0.6（徽标/组别标）。
 * 业务组件禁止再散写 fontSize/sp；确需新档先在本表登记。
 */
data class EmberTypography(
    val display: EmberType,
    val displaySmall: EmberType,
    val heroBig: EmberType,
    val hero: EmberType,
    val title: EmberType,
    val head: EmberType,
    val subhead: EmberType,
    val body: EmberType,
    val bodySmall: EmberType,
    val caption: EmberType,
    val meta: EmberType,
    val micro: EmberType,
)

val EmberTypographyDefault = EmberTypography(
    display = EmberType(32.sp, 40.sp, FontWeight.Light, 0.4.sp),
    displaySmall = EmberType(24.sp, 30.sp, FontWeight.SemiBold),
    heroBig = EmberType(30.sp, 38.sp, FontWeight.Light, 0.4.sp),
    hero = EmberType(26.sp, 32.sp, FontWeight.Light, 0.4.sp),
    title = EmberType(18.sp, 24.sp, FontWeight.SemiBold),
    head = EmberType(16.sp, 22.sp, FontWeight.SemiBold),
    subhead = EmberType(15.sp, 20.sp, FontWeight.Medium),
    body = EmberType(14.sp, 20.sp, FontWeight.Normal),
    bodySmall = EmberType(13.sp, 19.sp, FontWeight.Normal),
    caption = EmberType(12.sp, 16.sp, FontWeight.Normal),
    meta = EmberType(11.sp, 14.sp, FontWeight.Normal, 0.8.sp),
    micro = EmberType(10.sp, 13.sp, FontWeight.Normal, 0.6.sp),
)

/** 类型比例经 CompositionLocal 下发（EmberTheme.typo 访问器统一取用）。 */
val LocalEmberTypography = androidx.compose.runtime.staticCompositionLocalOf { EmberTypographyDefault }

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

/**
 * 窗口宽度档位（第 14 阶段自适应布局）：M3 WindowWidthSizeClass 同口径阈值
 * （compact <600 / medium 600-840 / expanded ≥840），以 screenWidthDp 自实现——
 * 行为与官方 material3-adaptive 等价，不引入新依赖、不破坏现有工程。
 * 配置变更（旋转/折叠）时 LocalConfiguration 重组自动重算。
 */
enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

@androidx.compose.runtime.Composable
fun windowWidthClass(): WindowWidth {
    val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> WindowWidth.EXPANDED
        w >= 600 -> WindowWidth.MEDIUM
        else -> WindowWidth.COMPACT
    }
}
