package com.emberinn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** 当前视觉氛围：供空状态/阴影等组件读取；默认标准=无品牌滤镜。 */
val LocalVibe = staticCompositionLocalOf { VibePresets.first() }

/** 当前预设主题：供间距节奏/动效速度等组件读取（README 清单 9）。 */
val LocalThemePreset = staticCompositionLocalOf { ThemePresets.first() }

@Composable
fun EmberInnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: ThemePreset = ThemePresets.first(),
    vibe: VibePreset = VibePresets.first(),
    shapes: Shapes = Shapes(),
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalVibe provides vibe, LocalThemePreset provides preset) {
        MaterialTheme(
            // 完整 scheme 覆盖的主题（酒馆官方=官方绝对色）没有浅色模式：浅/深都按官方深色渲染
            colorScheme = if (preset.schemeBackground != null || darkTheme) preset.darkScheme(vibe) else preset.lightScheme(vibe),
            typography = typographyWith(fontFamily),
            shapes = shapes,
            content = content,
        )
    }
}

/** M3 1.4 没有 defaultFontFamily 参数：整体换字体族时逐样式 copy。 */
private fun typographyWith(fontFamily: FontFamily): Typography {
    val base = Typography()
    // README 清单 13：排版层级大胆——标题 Bold/SemiBold，正文保持常规，级差更鲜明
    fun hierarchy(style: androidx.compose.ui.text.TextStyle, weight: FontWeight, size: androidx.compose.ui.unit.TextUnit = style.fontSize):
        androidx.compose.ui.text.TextStyle = style.copy(fontWeight = weight, fontSize = size)
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
        displayLarge = withFont(ember.displayLarge),
        displayMedium = withFont(ember.displayMedium),
        displaySmall = withFont(ember.displaySmall),
        headlineLarge = withFont(ember.headlineLarge),
        headlineMedium = withFont(ember.headlineMedium),
        headlineSmall = withFont(ember.headlineSmall),
        titleLarge = withFont(ember.titleLarge),
        titleMedium = withFont(ember.titleMedium),
        titleSmall = withFont(ember.titleSmall),
        bodyLarge = withFont(ember.bodyLarge),
        bodyMedium = withFont(ember.bodyMedium),
        bodySmall = withFont(ember.bodySmall),
        labelLarge = withFont(ember.labelLarge),
        labelMedium = withFont(ember.labelMedium),
        labelSmall = withFont(ember.labelSmall),
    )
}

private fun ThemePreset.lightScheme(vibe: VibePreset): ColorScheme {
    // 视觉氛围：降饱和强度 + 冷暖偏移来自 vibe（默认标准 = 0，取色原样输出）
    val bg = tinted(lightBg, vibe.warmth)
    val primary = desaturate(darken(seed, 0.04f), vibe.desaturateLight)
    val onBackground = darken(lightBg, 0.86f)
    val onSurface = onBackground
    val secondary = desaturate(darken(this.secondary, 0.03f), vibe.desaturateLight)
    val tertiary = desaturate(darken(this.tertiary, 0.03f), vibe.desaturateLight)
    return lightColorScheme(
        primary = primary,
        onPrimary = readableOn(primary),
        primaryContainer = lighten(seed, 0.72f),
        onPrimaryContainer = darken(seed, 0.52f),
        secondary = secondary,
        onSecondary = readableOn(secondary),
        secondaryContainer = lighten(this.secondary, 0.74f),
        onSecondaryContainer = darken(this.secondary, 0.50f),
        tertiary = tertiary,
        onTertiary = readableOn(tertiary),
        tertiaryContainer = lighten(this.tertiary, 0.76f),
        onTertiaryContainer = darken(this.tertiary, 0.46f),
        background = bg,
        onBackground = onBackground,
        surface = bg,
        onSurface = onSurface,
        surfaceVariant = lerp(bg, onSurface, 0.10f),
        onSurfaceVariant = darken(lightBg, 0.60f),
        outline = darken(lightBg, 0.42f),
        outlineVariant = lerp(bg, onSurface, 0.16f),
        surfaceContainerLowest = darken(bg, 0.03f),
        surfaceContainerLow = lerp(bg, onSurface, 0.045f),
        surfaceContainer = lerp(bg, onSurface, 0.075f),
        surfaceContainerHigh = lerp(bg, onSurface, 0.11f),
        surfaceContainerHighest = lerp(bg, onSurface, 0.15f),
    )
}

private fun ThemePreset.darkScheme(vibe: VibePreset): ColorScheme {
    val bg = schemeBackground?.let { tinted(it, vibe.warmth) } ?: tinted(darkBg, vibe.warmth)
    // 官方主题（完整 scheme 覆盖）色值绝对精确、不经过降饱和；其余主题的 scheme* 只是显式化的派生值，仍跟随视觉氛围
    val officialOverride = schemeBackground != null
    fun accent(scheme: Color?, auto: Color): Color =
        if (scheme != null && !officialOverride) desaturate(scheme, vibe.desaturateDark) else scheme ?: auto
    val primary = accent(schemePrimary, desaturate(lighten(seed, 0.30f), vibe.desaturateDark))
    val onBackground = schemeOnBackground ?: lighten(darkBg, 0.82f)
    val onSurface = schemeOnSurface ?: onBackground
    val secondary = accent(schemeSecondary, desaturate(lighten(this.secondary, 0.24f), vibe.desaturateDark))
    val tertiary = accent(schemeTertiary, desaturate(lighten(this.tertiary, 0.24f), vibe.desaturateDark))
    val surface = schemeSurface ?: lighten(bg, 0.035f)
    return darkColorScheme(
        primary = primary,
        onPrimary = if (schemePrimary != null) readableOn(primary) else darken(seed, 0.55f),
        primaryContainer = if (schemePrimary != null) darken(primary, 0.45f) else darken(seed, 0.45f),
        onPrimaryContainer = if (schemePrimary != null) lighten(primary, 0.72f) else lighten(seed, 0.72f),
        secondary = secondary,
        onSecondary = if (schemeSecondary != null) readableOn(secondary) else darken(this.secondary, 0.52f),
        secondaryContainer = if (schemeSecondary != null) darken(secondary, 0.45f) else darken(this.secondary, 0.45f),
        onSecondaryContainer = if (schemeSecondary != null) lighten(secondary, 0.68f) else lighten(this.secondary, 0.68f),
        tertiary = tertiary,
        onTertiary = if (schemeTertiary != null) readableOn(tertiary) else darken(this.tertiary, 0.52f),
        tertiaryContainer = if (schemeTertiary != null) darken(tertiary, 0.45f) else darken(this.tertiary, 0.45f),
        onTertiaryContainer = if (schemeTertiary != null) lighten(tertiary, 0.68f) else lighten(this.tertiary, 0.68f),
        background = bg,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = lighten(bg, 0.14f),
        onSurfaceVariant = lighten(darkBg, 0.60f),
        outline = lighten(darkBg, 0.34f),
        outlineVariant = lighten(bg, 0.10f),
        surfaceContainerLowest = darken(bg, 0.02f),
        surfaceContainerLow = lighten(bg, 0.055f),
        surfaceContainer = lighten(bg, 0.09f),
        surfaceContainerHigh = lighten(bg, 0.125f),
        surfaceContainerHighest = lighten(bg, 0.16f),
    )
}

private fun lighten(color: Color, fraction: Float): Color = lerp(color, Color.White, fraction)

private fun darken(color: Color, fraction: Float): Color = lerp(color, Color.Black, fraction)

private fun readableOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF221A16) else Color.White

/** 冷暖偏移：>0 偏暖黄，<0 偏冷蓝，幅度由 vibe.warmth 决定（默认 0 = 不偏移）。 */
private fun tinted(color: Color, warmth: Float): Color {
    val target = if (warmth > 0) Color(0xFFFFDDB0) else Color(0xFFC7D8EC)
    return lerp(color, target, kotlin.math.abs(warmth).coerceIn(0f, 1f))
}

/** 视觉氛围滤镜：把颜色往同亮度中性色靠拢 amount 比例，降低饱和度。 */
private fun desaturate(color: Color, amount: Float): Color {
    val l = color.luminance()
    return lerp(color, Color(l, l, l), amount.coerceIn(0f, 1f))
}
