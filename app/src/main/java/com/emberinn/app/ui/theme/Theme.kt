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
import androidx.compose.runtime.remember
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
    // 完整 scheme 覆盖的主题（酒馆官方=官方绝对色）没有浅色模式：浅/深都按官方深色渲染，
    // 且不做任何滤镜后处理（官方色值逐值还原，滤镜会破坏精确性）。
    // remember 键控：无关重组（外观修订号等状态变化）不再重建 scheme、
    // 也不再因 ColorScheme 实例变化把全 App 的取色读者整批打失效（staticCompositionLocal 特性）。
    val official = preset.schemeBackground != null
    val colorScheme = remember(preset, vibe, darkTheme) {
        if (official) {
            preset.darkScheme()
        } else if (darkTheme) {
            preset.darkScheme().vibed(vibe, dark = true)
        } else {
            preset.lightScheme().vibed(vibe, dark = false)
        }
    }
    CompositionLocalProvider(LocalVibe provides vibe, LocalThemePreset provides preset) {
        MaterialTheme(
            colorScheme = colorScheme,
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

private fun ThemePreset.lightScheme(): ColorScheme {
    // 强化：主色加深 0.10（旧 0.04 几乎等于没压，白底上偏飘），容器色留更多色度
    val bg = lightBg
    val primary = darken(seed, 0.10f)
    val onBackground = darken(lightBg, 0.86f)
    val onSurface = onBackground
    val secondary = darken(this.secondary, 0.03f)
    val tertiary = darken(this.tertiary, 0.03f)
    // 明暗对照（chiaroscuro）：contrast 缩放容器阶梯——>1 白纸与卡片层次拉开，光影更戏剧
    val c = contrast.coerceIn(0.6f, 1.6f)
    return lightColorScheme(
        primary = primary,
        onPrimary = readableOn(primary),
        primaryContainer = lighten(seed, 0.68f),
        onPrimaryContainer = darken(seed, 0.52f),
        secondary = secondary,
        onSecondary = readableOn(secondary),
        secondaryContainer = lighten(this.secondary, 0.70f),
        onSecondaryContainer = darken(this.secondary, 0.50f),
        tertiary = tertiary,
        onTertiary = readableOn(tertiary),
        tertiaryContainer = lighten(this.tertiary, 0.72f),
        onTertiaryContainer = darken(this.tertiary, 0.46f),
        background = bg,
        onBackground = onBackground,
        surface = bg,
        onSurface = onSurface,
        surfaceVariant = lerp(bg, onSurface, 0.10f * c),
        onSurfaceVariant = darken(lightBg, 0.60f),
        outline = darken(lightBg, 0.42f),
        outlineVariant = lerp(bg, onSurface, 0.16f * c),
        surfaceContainerLowest = darken(bg, 0.03f * c),
        surfaceContainerLow = lerp(bg, onSurface, 0.045f * c),
        surfaceContainer = lerp(bg, onSurface, 0.075f * c),
        surfaceContainerHigh = lerp(bg, onSurface, 0.11f * c),
        surfaceContainerHighest = lerp(bg, onSurface, 0.15f * c),
    )
}

private fun ThemePreset.darkScheme(): ColorScheme {
    // 强化：正文/次要文字提亮（0.82→0.86 / 0.34→0.38），深色下对比度更足，摆脱发灰的“low”感
    val bg = schemeBackground ?: darkBg
    val primary = schemePrimary ?: lighten(seed, 0.30f)
    val onBackground = schemeOnBackground ?: lighten(darkBg, 0.86f)
    val onSurface = schemeOnSurface ?: onBackground
    val secondary = schemeSecondary ?: lighten(this.secondary, 0.24f)
    val tertiary = schemeTertiary ?: lighten(this.tertiary, 0.24f)
    val surface = schemeSurface ?: lighten(bg, 0.035f)
    // 明暗对照（chiaroscuro）：contrast 缩放容器阶梯——>1 夜色更沉、卡片浮得更亮
    val c = contrast.coerceIn(0.6f, 1.6f)
    return darkColorScheme(
        primary = primary,
        onPrimary = if (schemePrimary != null) readableOn(primary) else darken(seed, 0.55f),
        primaryContainer = darken(primary, 0.45f),
        onPrimaryContainer = lighten(primary, 0.72f),
        secondary = secondary,
        onSecondary = if (schemeSecondary != null) readableOn(secondary) else darken(this.secondary, 0.52f),
        secondaryContainer = darken(secondary, 0.45f),
        onSecondaryContainer = lighten(secondary, 0.68f),
        tertiary = tertiary,
        onTertiary = if (schemeTertiary != null) readableOn(tertiary) else darken(this.tertiary, 0.52f),
        tertiaryContainer = darken(tertiary, 0.45f),
        onTertiaryContainer = lighten(tertiary, 0.68f),
        background = bg,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = lighten(bg, 0.14f * c),
        onSurfaceVariant = lighten(darkBg, 0.62f),
        outline = lighten(darkBg, 0.38f),
        outlineVariant = lighten(bg, 0.10f * c),
        surfaceContainerLowest = darken(bg, 0.02f * c),
        surfaceContainerLow = lighten(bg, 0.055f * c),
        surfaceContainer = lighten(bg, 0.09f * c),
        surfaceContainerHigh = lighten(bg, 0.125f * c),
        surfaceContainerHighest = lighten(bg, 0.16f * c),
    )
}

/**
 * 视觉氛围滤镜：整盘 scheme 后处理。旧实现把滤镜洒在 3 个强调色 + 底色上，
 * 其余 30+ 配色角色（容器/文字/描边）全部漏网——拖滑杆几乎看不出变化，
 * 这就是“自定义不生效”的根因。现在所有色角色统一过一遍“冷暖偏移 + 降饱和”，
 * 拖动即时可见；错误色/反色等语义色保留原样，官方主题整体豁免（绝对色值）。
 */
private fun ColorScheme.vibed(vibe: VibePreset, dark: Boolean): ColorScheme {
    if (vibe.id == "standard") return this
    val desat = if (dark) vibe.desaturateDark else vibe.desaturateLight
    val warmth = vibe.warmth
    if (desat <= 0f && warmth == 0f) return this
    fun f(c: Color): Color {
        var out = if (warmth != 0f) tinted(c, warmth) else c
        if (desat > 0f) out = desaturate(out, desat)
        return out
    }
    return copy(
        primary = f(primary),
        onPrimary = f(onPrimary),
        primaryContainer = f(primaryContainer),
        onPrimaryContainer = f(onPrimaryContainer),
        secondary = f(secondary),
        onSecondary = f(onSecondary),
        secondaryContainer = f(secondaryContainer),
        onSecondaryContainer = f(onSecondaryContainer),
        tertiary = f(tertiary),
        onTertiary = f(onTertiary),
        tertiaryContainer = f(tertiaryContainer),
        onTertiaryContainer = f(onTertiaryContainer),
        background = f(background),
        onBackground = f(onBackground),
        surface = f(surface),
        onSurface = f(onSurface),
        surfaceVariant = f(surfaceVariant),
        onSurfaceVariant = f(onSurfaceVariant),
        surfaceTint = f(primary),
        outline = f(outline),
        outlineVariant = f(outlineVariant),
        surfaceContainerLowest = f(surfaceContainerLowest),
        surfaceContainerLow = f(surfaceContainerLow),
        surfaceContainer = f(surfaceContainer),
        surfaceContainerHigh = f(surfaceContainerHigh),
        surfaceContainerHighest = f(surfaceContainerHighest),
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
