package com.emberinn.app.ui.design

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * EmberTheme（docs/DESIGN_SYSTEM.md §四）：
 * 令牌经 CompositionLocal 下发；M3 只是实现底座——EmberColors 映射进 MaterialTheme.scheme
 * 保证存量 M3 组件自动协调，但业务组件一律读 [EmberTheme]，禁止直接 colorScheme。
 *
 * 调色板来源=官方主题字段推导（ShellTheme.derive，Commit 4a）：壳层没有自己的皮肤体系，
 * 导入任何官方主题整壳随之换装；暗色为基线（用户决策，不跟随系统浅色）。
 */
object EmberTheme {
    val colors: EmberColors @Composable get() = LocalEmberColors.current
    val shapes: EmberShapes @Composable get() = LocalEmberShapes.current
    val spacing: EmberSpacing @Composable get() = LocalEmberSpacing.current
    val motion: EmberMotion @Composable get() = LocalEmberMotion.current
    val chat: ChatAreaTheme @Composable get() = LocalEmberChatTheme.current

    /** 壳层类型比例（第 16 阶段）：业务组件统一取用，禁止散写 fontSize/sp。 */
    val typo: EmberTypography @Composable get() = LocalEmberTypography.current

    /** 是否暗基底（聊天页渐变遮罩等按此取深浅）。 */
    val isDark: Boolean @Composable get() = LocalEmberDark.current

    /** 官方内容主题的舞台染色桥（blur_tint），聊天页遮罩可用。null = 未桥接。 */
    val stageTint: Color? @Composable get() = LocalEmberStageTint.current

    /** 减动画模式（官方主题 reduced_motion 桥）：全部动效降为 80ms fade（§七）。 */
    val reducedMotion: Boolean @Composable get() = LocalEmberReduced.current

    /** 统一毛玻璃档（官方 blur_strength 桥，fast_ui_mode=true 时为 0）。 */
    val blur: Dp @Composable get() = LocalEmberBlur.current
}

val LocalEmberColors = staticCompositionLocalOf { ShellTheme.FALLBACK.colors }
val LocalEmberShapes = staticCompositionLocalOf { EmberShapes(16.dp, 14.dp, 28.dp, 8.dp) }
val LocalEmberSpacing = staticCompositionLocalOf { EmberSpacing(4.dp, 20.dp, 8.dp, 24.dp) }
val LocalEmberMotion = staticCompositionLocalOf { EmberMotion() }
val LocalEmberChatTheme = staticCompositionLocalOf { ShellTheme.FALLBACK.chat }
val LocalEmberDark = staticCompositionLocalOf { true }
val LocalEmberStageTint = staticCompositionLocalOf<Color?> { ShellTheme.FALLBACK.stageTint }
val LocalEmberReduced = staticCompositionLocalOf { false }
val LocalEmberBlur = staticCompositionLocalOf { 12.dp }

/** 圆角偏好四档 → 形状性格（外观页「全局圆角」实时生效）。 */
fun shapesForRadius(radius: String): EmberShapes = when (radius) {
    "square" -> EmberShapes(10.dp, 8.dp, 14.dp, 4.dp)
    "rounded" -> EmberShapes(20.dp, 16.dp, 32.dp, 12.dp)
    "circle" -> EmberShapes(26.dp, 24.dp, 36.dp, 14.dp)
    else -> EmberShapes(16.dp, 14.dp, 28.dp, 8.dp)
}

/** 壳层密度两档 → 间距节奏（第 15 阶段）：comfortable=舒展留白 / compact=组间收紧。 */
fun spacingForDensity(density: String): EmberSpacing = when (density) {
    "compact" -> EmberSpacing(4.dp, 16.dp, 6.dp, 16.dp)
    else -> EmberSpacing(4.dp, 20.dp, 8.dp, 22.dp)
}

/** 壳层动效两档（第 15 阶段）：full=弹簧物理 / reduced=时长收紧（pageMs≈112 / sheetMs≈84）。 */
fun motionForLevel(level: String): EmberMotion = when (level) {
    "reduced" -> EmberMotion(scale = 0.35f)
    else -> EmberMotion()
}

/**
 * 应用根主题。
 * @param colors 由官方主题字段推导的完整调色板（ShellTheme.derive）
 * @param radius 外观页圆角偏好（default/square/rounded/circle）
 * @param density 外观页壳层密度（comfortable/compact，只影响壳层间距）
 * @param motionLevel 外观页动效档位（full/reduced，只影响壳层时长）
 */
@Composable
fun EmberTheme(
    colors: EmberColors,
    chat: ChatAreaTheme,
    stageTint: Color?,
    reducedMotion: Boolean = false,
    blur: Dp = 12.dp,
    fontFamily: FontFamily = FontFamily.Default,
    radius: String = "default",
    density: String = "comfortable",
    motionLevel: String = "full",
    content: @Composable () -> Unit,
) {
    val dark = colors.bg.luminance() < 0.5f
    CompositionLocalProvider(
        LocalEmberColors provides colors,
        LocalEmberShapes provides shapesForRadius(radius),
        LocalEmberSpacing provides spacingForDensity(density),
        LocalEmberMotion provides motionForLevel(motionLevel),
        LocalEmberTypography provides EmberTypographyDefault,
        LocalEmberChatTheme provides chat,
        LocalEmberDark provides dark,
        LocalEmberStageTint provides stageTint,
        LocalEmberReduced provides reducedMotion,
        LocalEmberBlur provides blur,
    ) {
        MaterialTheme(
            colorScheme = mapToM3Scheme(colors, dark),
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
