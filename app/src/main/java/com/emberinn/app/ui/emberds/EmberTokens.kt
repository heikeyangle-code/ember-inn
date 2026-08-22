package com.emberinn.app.ui.emberds

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * EmberDS 设计令牌 — 默认深色皮肤的视觉 DNA 取自 Moonlit Glimmer（实测色值）。
 * 业务组件禁止直接引用 MaterialTheme.colorScheme，一律经 [EmberTheme.current] 取令牌。
 */
data class EmberColors(
    // 舞台与表面（1dp 亮度阶梯，非色相区分）
    val stage: Color = Color(0xFF121212),          // 最底舞台（Glimmer blur_tint 近黑）
    val surface: Color = Color(0xFF1E1E1E),        // 卡面（rgba(30,30,30)）
    val surfaceHigh: Color = Color(0xFF272727),    // 浮起卡面
    val surfaceOverlay: Color = Color(0xFF2D2D2D), // 弹层（user_mes_blur_tint）

    // 墨阶四档（Glimmer main_text rgba(204,204,204) 系）
    val ink: Color = Color(0xFFCCCCCC),
    val inkSecondary: Color = Color(0xFF969696),
    val inkTertiary: Color = Color(0xFF6C6C6C),
    val inkDisabled: Color = Color(0xFF4A4A4A),

    // 强调与身份
    val accent: Color = Color(0xFF51A0DE),         // Glimmer 引号蓝——冷强调
    val aiGold: Color = Color(0xFFE9C46A),         // AI 身份暖金三色的主金（Foreverse）
    val aiGoldSoft: Color = Color(0x33E9C46A),
    val danger: Color = Color(0xFFE57373),

    // 描边与遮罩（极细边框代替重阴影）
    val hairline: Color = Color(0x14FFFFFF),       // rgba(255,255,255,0.08)
    val hairlineStrong: Color = Color(0x1FFFFFFF),
    val scrim: Color = Color(0x99000000),

    // 消息气泡底（Moonlit bot/user mes tint 直译）
    val bubbleBot: Color = Color(0xA6272727),      // rgba(39,39,39,0.65)
    val bubbleUser: Color = Color(0x802D2D2D),     // rgba(45,45,45,0.5)
)

data class EmberShapes(
    val bubble: Dp = 14.dp,
    val card: Dp = 16.dp,
    val bar: Dp = 20.dp,
    val chip: Dp = 8.dp,
)

data class EmberSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
)

data class EmberType(
    /** 克制字号：font_scale ≈ 1.0，正文 15sp 起步 */
    val bodySp: Float = 15f,
    val titleSp: Float = 17f,
    val labelSp: Float = 12f,
)

data class EmberMotion(
    val fastMs: Int = 150,
    val normalMs: Int = 240,
    val slowMs: Int = 360,
)

object EmberDefaults {
    val colors = EmberColors()
    val shapes = EmberShapes()
    val spacing = EmberSpacing()
    val type = EmberType()
    val motion = EmberMotion()
}
