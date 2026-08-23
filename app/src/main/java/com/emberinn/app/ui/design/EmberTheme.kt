package com.emberinn.app.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * EmberTheme（docs/DESIGN_SYSTEM.md §四）：
 * 令牌经 CompositionLocal 下发；M3 只是实现底座——EmberColors 映射进 MaterialTheme.scheme
 * 保证存量 M3 组件自动协调，但业务组件一律读 [EmberTheme]，禁止直接 colorScheme。
 *
 * 暗色优先（§三 3）：全局强制暗基底是用户决策——不再跟随系统浅色，
 * 浅色皮肤为一等公民但在外观页显式选择。
 */
object EmberTheme {
    val colors: EmberColors @Composable get() = LocalEmberColors.current
    val shapes: EmberShapes @Composable get() = LocalEmberShapes.current
    val spacing: EmberSpacing @Composable get() = LocalEmberSpacing.current
    val motion: EmberMotion @Composable get() = LocalEmberMotion.current
    val chat: ChatAreaTheme @Composable get() = LocalEmberChatTheme.current

    /** 是否暗基底（聊天页渐变遮罩等按此取深浅）。 */
    val isDark: Boolean @Composable get() = LocalEmberDark.current

    /** 官方内容主题的舞台染色桥（blur_tint），聊天页遮罩可用。null = 未桥接。 */
    val stageTint: Color? @Composable get() = LocalEmberStageTint.current

    /** 减动画模式（官方主题 reduced_motion 桥）：全部动效降为 80ms fade（§七）。 */
    val reducedMotion: Boolean @Composable get() = LocalEmberReduced.current
}

val LocalEmberColors = staticCompositionLocalOf { EmberSkins.DEFAULT.dark }
val LocalEmberShapes = staticCompositionLocalOf { EmberShapes(16.dp, 14.dp, 28.dp, 8.dp) }
val LocalEmberSpacing = staticCompositionLocalOf { EmberSpacing(4.dp, 20.dp, 8.dp, 24.dp) }
val LocalEmberMotion = staticCompositionLocalOf { EmberMotion() }
val LocalEmberChatTheme = staticCompositionLocalOf { ChatAreaTheme(null, null, null, null, null, null, null, null, null, false) }
val LocalEmberDark = staticCompositionLocalOf { true }
val LocalEmberStageTint = staticCompositionLocalOf<Color?> { null }
val LocalEmberReduced = staticCompositionLocalOf { false }
// SkinImageAssets / LocalEmberImageAssets 定义于 SkinImageAssets.kt（§五资产层）

/**
 * 应用根主题。
 * @param darkTheme 强制暗基底默认 true（用户决策：不再跟随系统浅色）；
 *   外观页将来提供浅色皮肤时显式传 false。
 * @param accentOverride 官方内容主题强调色桥（quote_text_color 系）：壳层自动配套不违和（验收标准 4）。
 * @param stageTint 官方内容主题舞台染色桥（blur_tint/chat_tint）。
 */
@Composable
fun EmberTheme(
    skin: EmberSkin,
    darkTheme: Boolean = true,
    accentOverride: Color? = null,
    stageTint: Color? = null,
    reducedMotion: Boolean = false,
    fontFamily: FontFamily = FontFamily.Default,
    imageAssets: SkinImageAssets = SkinImageAssets.EMPTY,
    content: @Composable () -> Unit,
) {
    var palette = if (darkTheme) skin.dark else skin.light
    // 官方主题桥：只动强调三态，底面五阶与墨阶保持皮肤性格（互不污染原则）
    if (accentOverride != null) {
        palette = palette.copy(
            accent = accentOverride,
            accentSoft = accentOverride.copy(alpha = 0x5C / 255f),
            accentBg = accentOverride.copy(alpha = 0x1A / 255f),
        )
    }
    val chatSkin = skin.chat.let { ct ->
        if (accentOverride != null) ct.copy(inputAccent = accentOverride) else ct
    }
    val m3Scheme = mapToM3Scheme(palette, darkTheme)

    CompositionLocalProvider(
        LocalEmberColors provides palette,
        LocalEmberShapes provides skin.shapes,
        LocalEmberSpacing provides skin.spacing,
        LocalEmberMotion provides skin.motion,
        LocalEmberChatTheme provides chatSkin,
        LocalEmberDark provides darkTheme,
        LocalEmberStageTint provides stageTint,
        LocalEmberReduced provides reducedMotion,
        LocalEmberImageAssets provides imageAssets,
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = emberTypography(fontFamily),
            content = content,
        )
    }
}

// ---------------------------------------------------------------- M3 映射

private fun readableOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF17171A) else Color(0xFFF2F3F5)

private fun mapToM3Scheme(p: EmberColors, dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = readableOn(p.accent),
            primaryContainer = p.accentBg.compositeOver(p.surface),
            onPrimaryContainer = p.ink,
            secondary = p.inkSoft,
            onSecondary = readableOn(p.inkSoft),
            secondaryContainer = p.surface2,
            onSecondaryContainer = p.ink,
            tertiary = p.ai,
            onTertiary = readableOn(p.ai),
            tertiaryContainer = p.aiBg.compositeOver(p.surface),
            onTertiaryContainer = p.ink,
            background = p.bg,
            onBackground = p.ink,
            surface = p.surface,
            onSurface = p.ink,
            surfaceVariant = p.surface2,
            onSurfaceVariant = p.inkSoft,
            outline = p.lineStrong,
            outlineVariant = p.line,
            error = p.danger,
            onError = readableOn(p.danger),
            errorContainer = p.danger.copy(alpha = 0.18f),
            onErrorContainer = p.ink,
            inverseSurface = p.ink,
            inverseOnSurface = p.bg,
            inversePrimary = p.accent,
            surfaceDim = p.surfaceSink,
            surfaceBright = p.surface2,
            surfaceContainerLowest = p.surfaceSink,
            surfaceContainerLow = p.surface,
            surfaceContainer = p.surface2,
            surfaceContainerHigh = lerp(p.surface2, p.inkSoft2, 0.22f),
            surfaceContainerHighest = lerp(p.surface2, p.inkSoft2, 0.38f),
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = readableOn(p.accent),
            primaryContainer = p.accentBg.compositeOver(p.surface),
            onPrimaryContainer = p.ink,
            secondary = p.inkSoft,
            onSecondary = readableOn(p.inkSoft),
            secondaryContainer = p.surface2,
            onSecondaryContainer = p.ink,
            tertiary = p.ai,
            onTertiary = readableOn(p.ai),
            tertiaryContainer = p.aiBg.compositeOver(p.surface),
            onTertiaryContainer = p.ink,
            background = p.bg,
            onBackground = p.ink,
            surface = p.surface,
            onSurface = p.ink,
            surfaceVariant = p.surface2,
            onSurfaceVariant = p.inkSoft,
            outline = p.lineStrong,
            outlineVariant = p.line,
            error = p.danger,
            onError = readableOn(p.danger),
            errorContainer = p.danger.copy(alpha = 0.14f),
            onErrorContainer = p.ink,
            inverseSurface = p.ink,
            inverseOnSurface = p.bg,
            inversePrimary = p.accent,
            surfaceDim = p.surfaceSink,
            surfaceBright = Color.White,
            surfaceContainerLowest = p.surfaceSink,
            surfaceContainerLow = p.surface,
            surfaceContainer = p.surface2,
            surfaceContainerHigh = lerp(p.surface2, p.lineStrong, 0.25f),
            surfaceContainerHighest = lerp(p.surface2, p.lineStrong, 0.45f),
        )
    }
}

// ---------------------------------------------------------------- 排版

/** M3 1.4 无 defaultFontFamily：整体换字体族时逐样式 copy。标题 Bold/SemiBold 级差鲜明。 */
private fun emberTypography(fontFamily: FontFamily): Typography {
    val base = Typography()
    fun hierarchy(style: androidx.compose.ui.text.TextStyle, weight: FontWeight): androidx.compose.ui.text.TextStyle =
        style.copy(fontWeight = weight)
    val ember = Typography(
        displayLarge = hierarchy(base.displayLarge, FontWeight.Bold),
        displayMedium = hierarchy(base.displayMedium, FontWeight.Bold),
        displaySmall = hierarchy(base.displaySmall, FontWeight.Bold),
        headlineLarge = hierarchy(base.headlineLarge, FontWeight.Bold),
        headlineMedium = hierarchy(base.headlineMedium, FontWeight.Bold),
        headlineSmall = hierarchy(base.headlineSmall, FontWeight.Bold),
        titleLarge = hierarchy(base.titleLarge, FontWeight.Bold),
        titleMedium = hierarchy(base.titleMedium, FontWeight.SemiBold),
        titleSmall = hierarchy(base.titleSmall, FontWeight.Medium),
        bodyLarge = hierarchy(base.bodyLarge, FontWeight.Normal),
        bodyMedium = hierarchy(base.bodyMedium, FontWeight.Normal),
        bodySmall = hierarchy(base.bodySmall, FontWeight.Normal),
        labelLarge = hierarchy(base.labelLarge, FontWeight.SemiBold),
        labelMedium = hierarchy(base.labelMedium, FontWeight.Medium),
        labelSmall = hierarchy(base.labelSmall, FontWeight.Medium),
    )
    if (fontFamily == FontFamily.Default) return ember
    fun withFont(style: androidx.compose.ui.text.TextStyle): androidx.compose.ui.text.TextStyle =
        style.copy(fontFamily = fontFamily)
    return ember.copy(
        displayLarge = withFont(ember.displayLarge), displayMedium = withFont(ember.displayMedium),
        displaySmall = withFont(ember.displaySmall), headlineLarge = withFont(ember.headlineLarge),
        headlineMedium = withFont(ember.headlineMedium), headlineSmall = withFont(ember.headlineSmall),
        titleLarge = withFont(ember.titleLarge), titleMedium = withFont(ember.titleMedium),
        titleSmall = withFont(ember.titleSmall), bodyLarge = withFont(ember.bodyLarge),
        bodyMedium = withFont(ember.bodyMedium), bodySmall = withFont(ember.bodySmall),
        labelLarge = withFont(ember.labelLarge), labelMedium = withFont(ember.labelMedium),
        labelSmall = withFont(ember.labelSmall),
    )
}
