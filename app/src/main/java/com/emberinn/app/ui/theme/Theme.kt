package com.emberinn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * README 主题系统：1 个 seed（预设主题/角色卡取色）→ 生成整套 M3 配色。
 * 浅色取低饱和容器，深色取提亮主色；背景用各主题的纸色/夜色。
 */
@Composable
fun EmberInnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: ThemePreset = ThemePresets.first(),
    shapes: Shapes = Shapes(),
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) preset.darkScheme() else preset.lightScheme(),
        typography = typographyWith(fontFamily),
        shapes = shapes,
        content = content,
    )
}

/** M3 1.4 没有 defaultFontFamily 参数：整体换字体族时逐样式 copy（默认族时原样）。 */
private fun typographyWith(fontFamily: FontFamily): Typography {
    if (fontFamily == FontFamily.Default) return Typography()
    val base = Typography()
    fun withFont(style: androidx.compose.ui.text.TextStyle): androidx.compose.ui.text.TextStyle =
        style.copy(fontFamily = fontFamily)
    return Typography(
        displayLarge = withFont(base.displayLarge),
        displayMedium = withFont(base.displayMedium),
        displaySmall = withFont(base.displaySmall),
        headlineLarge = withFont(base.headlineLarge),
        headlineMedium = withFont(base.headlineMedium),
        headlineSmall = withFont(base.headlineSmall),
        titleLarge = withFont(base.titleLarge),
        titleMedium = withFont(base.titleMedium),
        titleSmall = withFont(base.titleSmall),
        bodyLarge = withFont(base.bodyLarge),
        bodyMedium = withFont(base.bodyMedium),
        bodySmall = withFont(base.bodySmall),
        labelLarge = withFont(base.labelLarge),
        labelMedium = withFont(base.labelMedium),
        labelSmall = withFont(base.labelSmall),
    )
}

private fun ThemePreset.lightScheme(): ColorScheme {
    val primary = darken(seed, 0.06f)
    val onBackground = darken(lightBg, 0.82f)
    val onSurface = onBackground
    val secondary = darken(this.secondary, 0.05f)
    val tertiary = darken(this.tertiary, 0.05f)
    return lightColorScheme(
        primary = primary,
        onPrimary = readableOn(primary),
        primaryContainer = lighten(seed, 0.76f),
        onPrimaryContainer = darken(seed, 0.48f),
        secondary = secondary,
        onSecondary = readableOn(secondary),
        secondaryContainer = lighten(this.secondary, 0.78f),
        onSecondaryContainer = darken(this.secondary, 0.45f),
        tertiary = tertiary,
        onTertiary = readableOn(tertiary),
        tertiaryContainer = lighten(this.tertiary, 0.80f),
        onTertiaryContainer = darken(this.tertiary, 0.42f),
        background = lightBg,
        onBackground = onBackground,
        surface = lightBg,
        onSurface = onSurface,
        surfaceVariant = lerp(lightBg, onSurface, 0.08f),
        onSurfaceVariant = darken(lightBg, 0.55f),
        outline = darken(lightBg, 0.38f),
        surfaceContainerLowest = lightBg,
        surfaceContainerLow = lerp(lightBg, onSurface, 0.035f),
        surfaceContainer = lerp(lightBg, onSurface, 0.06f),
        surfaceContainerHigh = lerp(lightBg, onSurface, 0.09f),
        surfaceContainerHighest = lerp(lightBg, onSurface, 0.12f),
    )
}

private fun ThemePreset.darkScheme(): ColorScheme {
    val primary = lighten(seed, 0.24f)
    val onBackground = lighten(darkBg, 0.78f)
    val onSurface = onBackground
    val secondary = lighten(this.secondary, 0.20f)
    val tertiary = lighten(this.tertiary, 0.20f)
    return darkColorScheme(
        primary = primary,
        onPrimary = darken(seed, 0.55f),
        primaryContainer = darken(seed, 0.45f),
        onPrimaryContainer = lighten(seed, 0.72f),
        secondary = secondary,
        onSecondary = darken(this.secondary, 0.52f),
        secondaryContainer = darken(this.secondary, 0.45f),
        onSecondaryContainer = lighten(this.secondary, 0.68f),
        tertiary = tertiary,
        onTertiary = darken(this.tertiary, 0.52f),
        tertiaryContainer = darken(this.tertiary, 0.45f),
        onTertiaryContainer = lighten(this.tertiary, 0.68f),
        background = darkBg,
        onBackground = onBackground,
        surface = lighten(darkBg, 0.03f),
        onSurface = onSurface,
        surfaceVariant = lighten(darkBg, 0.12f),
        onSurfaceVariant = lighten(darkBg, 0.55f),
        outline = lighten(darkBg, 0.32f),
        surfaceContainerLowest = darkBg,
        surfaceContainerLow = lighten(darkBg, 0.05f),
        surfaceContainer = lighten(darkBg, 0.08f),
        surfaceContainerHigh = lighten(darkBg, 0.11f),
        surfaceContainerHighest = lighten(darkBg, 0.14f),
    )
}

private fun lighten(color: Color, fraction: Float): Color = lerp(color, Color.White, fraction)

private fun darken(color: Color, fraction: Float): Color = lerp(color, Color.Black, fraction)

private fun readableOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF221A16) else Color.White
