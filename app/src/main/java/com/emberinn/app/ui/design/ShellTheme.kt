package com.emberinn.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 壳层调色板推导（Commit 4a 架构核心）：官方主题字段 → [EmberColors] 角色令牌，单向纯函数。
 * 取代旧 EmberSkin 六件套——壳层不再有自己的皮肤体系，导入任何官方主题整壳自动换装。
 *
 * 每条规则对应该字段在官方 style.css 的真实用途（power-user.js SmartTheme 变量）：
 *  - blur_tint_color（--SmartThemeBlurTintColor）= 全局面纱 → 页底 bg（合成到暗中性上）
 *  - bot_mes_blur_tint_color / user_mes_blur_tint_color = AI/用户消息卡纱 → surface / surface2
 *    （官方两条消息的底色差本身就是表面亮度阶梯）
 *  - shadow_color = 阴影基色 → 凹陷面 surfaceSink（输入框/搜索井）
 *  - main_text_color（--SmartThemeBodyColor）→ ink；italics_text_color → inkMute
 *  - quote_text_color（--SmartThemeQuoteColor）→ accent 三态
 *  - border_color → line（全透明时回退 ink 低 alpha——Moonlit 以阴影代替描边）
 *  - chat_tint_color / blur_tint_color → stageTint（聊天页渐变遮罩）
 * AI 三态不再用品牌金：由内容面（bot tint）向 ink 混合派生，随主题换装。
 * 对比度守卫：ink≥4.5、弱墨/强调≥3.0，不足则向远离底色方向提到达标（最小干预）。
 */
object ShellTheme {

    data class Derived(
        val colors: EmberColors,
        val chat: ChatAreaTheme,
        val stageTint: Color?,
        /** 统一毛玻璃档：blur_strength×1.2dp（上限 36）；fast_ui_mode=true → 0。 */
        val blurRadius: Dp = 0.dp,
    )

    /** 合成底色：半透明面纱按官方 backdrop-filter 语义落到这层暗中性上。 */
    private val NEUTRAL = Color(0xFF101012)

    fun derive(themeJson: String?): Derived {
        val obj = themeJson?.let {
            runCatching { Json.parseToJsonElement(it) }.getOrNull() as? JsonObject
        }
        fun col(key: String): Color? = obj?.get(key)?.let { el ->
            runCatching { el.jsonPrimitive.content }.getOrNull()?.let(::parseColor)
        }
        val fastUiMode = obj?.get("fast_ui_mode")?.jsonPrimitive?.booleanOrNull
        val blurStrength = obj?.get("blur_strength")?.jsonPrimitive?.floatOrNull ?: 10f
        // 统一毛玻璃档：官方 blur_strength（0-30）→ dp；fast_ui_mode=true 直接关模糊
        val blurRadius =
            if (fastUiMode == true) 0.dp
            else (blurStrength * 1.2f).dp.coerceIn(0.dp, 36.dp)

        val blurTint = col("blur_tint_color")
        val quote = col("quote_text_color")
        val mainText = col("main_text_color")
        // 三大主字段全缺 = 非官方主题/空态 → 月光默认板（MoonlitEchoes 推导值）
        if (blurTint == null && quote == null && mainText == null) return FALLBACK

        val bg = (blurTint ?: Color(0xFF212121)).compositeOver(NEUTRAL)
        val surface = col("bot_mes_blur_tint_color")?.compositeOver(bg) ?: lift(bg, 0.06f)
        val surface2 = col("user_mes_blur_tint_color")?.compositeOver(bg) ?: lift(bg, 0.10f)
        val sink = col("shadow_color")?.compositeOver(bg) ?: sinkOf(bg)

        val ink = ensureContrast((mainText ?: Color(0xFFCCCCCC)).copy(alpha = 1f), bg, 4.5f)
        val inkMute = col("italics_text_color")?.let { ensureContrast(it.copy(alpha = 1f), bg, 3f) }
            ?: ink.copy(alpha = 0.52f)

        val borderRaw = col("border_color")
        val line = if (borderRaw != null && borderRaw.alpha > 0.02f) borderRaw else ink.copy(alpha = 0.12f)
        val lineStrong =
            if (borderRaw != null && borderRaw.alpha > 0.02f) borderRaw.copy(alpha = (borderRaw.alpha * 2f).coerceAtMost(0.5f))
            else ink.copy(alpha = 0.22f)

        val accent = ensureContrast((quote ?: Color(0xFF51A0DE)).copy(alpha = 1f), bg, 3f)

        val colors = EmberColors(
            bg = bg,
            bgTint = bg,
            surface = surface,
            surface2 = surface2,
            surfaceSink = sink,
            ink = ink,
            inkSoft = ink.copy(alpha = 0.72f),
            inkMute = inkMute,
            inkSoft2 = ink.copy(alpha = 0.34f),
            line = line,
            lineStrong = lineStrong,
            accent = accent,
            accentSoft = accent.copy(alpha = 0x5C / 255f),
            accentBg = accent.copy(alpha = 0x1A / 255f),
            ai = lerp(surface, ink, 0.30f),
            aiSoft = lerp(surface, ink, 0.30f).copy(alpha = 0x5C / 255f),
            aiBg = lerp(surface, ink, 0.30f).copy(alpha = 0x1A / 255f),
            success = Color(0xFF3D8F5A),
            warning = Color(0xFFC9A227),
            danger = Color(0xFFB34A4A),
        )
        val chat = ChatAreaTheme(
            inputBg = sink,
            inputText = ink,
            inputPlaceholder = inkMute,
            inputBorder = line,
            inputAccent = accent,
            buttonBg = surface2,
            buttonIcon = ink.copy(alpha = 0.72f),
            bottomScrim = bg.copy(alpha = 0.88f),
            topScrim = bg.copy(alpha = 0.88f),
            floatingInput = false,
        )
        return Derived(colors, chat, blurTint, blurRadius)
    }

    /** 官方 rgba()/hex 字符串 → Color；解析失败返回 null（官方存 rgba() 形态）。 */
    private fun parseColor(v: String): Color? {
        val s = v.trim()
        Regex("""rgba?\s*\(\s*(\d+)\s*[\s,]\s*(\d+)\s*[\s,]\s*(\d+)(?:\s*[\s,]\s*([\d.]+))?\s*\)""").find(s)?.let { m ->
            return Color(
                red = m.groupValues[1].toInt().coerceIn(0, 255) / 255f,
                green = m.groupValues[2].toInt().coerceIn(0, 255) / 255f,
                blue = m.groupValues[3].toInt().coerceIn(0, 255) / 255f,
                alpha = (m.groupValues[4].toFloatOrNull() ?: 1f).coerceIn(0f, 1f),
            )
        }
        val hex = s.removePrefix("#")
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { Color((0xFF000000L or it).toInt()) }
            8 -> hex.toLongOrNull(16)?.let { Color(it.toInt()) }
            else -> null
        }
    }

    private fun lift(c: Color, f: Float): Color = lerp(c, Color.White, f)

    /** 凹陷面：页底向阴影基色压暗一档。 */
    private fun sinkOf(c: Color): Color = lerp(c, Color.Black, 0.25f)

    /** WCAG 相对亮度对比度。 */
    internal fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /**
     * 可读性守卫：fg 对 bg 达不到 target 就向远离底色方向逐档混合（每档 9%，最多 28 档）。
     * 纯函数确定性——黑底黑字/白底白字这类离谱主题也能被拉到可读。
     */
    internal fun ensureContrast(fg: Color, bg: Color, target: Float): Color {
        var c = fg.copy(alpha = 1f)
        val towardDark = bg.luminance() >= 0.5f
        var i = 0
        while (contrastRatio(c, bg) < target && i < 28) {
            c = if (towardDark) lerp(c, Color.Black, 0.09f) else lerp(c, Color.White, 0.09f)
            i++
        }
        return c
    }

    /** 月光默认板：MoonlitEchoes 字段推导结果常量化（无主题 JSON 时的壳层基线）。 */
    val FALLBACK: Derived = buildFallback()

    private fun buildFallback(): Derived {
        val bg = Color(0xFF212121).compositeOver(NEUTRAL) // ≈ #1B1B1B
        val ink = Color(0xFFCCCCCC)
        val accent = Color(0xFF51A0DE)
        val fbSurface = Color(0xFF232323)
        val aiTone = lerp(fbSurface, ink, 0.30f)
        return Derived(
            colors = EmberColors(
                bg = bg,
                bgTint = bg,
                surface = Color(0xFF232323),
                surface2 = Color(0xFF292929),
                surfaceSink = lerp(bg, Color.Black, 0.25f),
                ink = ink,
                inkSoft = ink.copy(alpha = 0.72f),
                inkMute = Color(0xFF969696),
                inkSoft2 = ink.copy(alpha = 0.34f),
                line = ink.copy(alpha = 0.12f),
                lineStrong = ink.copy(alpha = 0.22f),
                accent = accent,
                accentSoft = accent.copy(alpha = 0x5C / 255f),
                accentBg = accent.copy(alpha = 0x1A / 255f),
                ai = aiTone,
                aiSoft = aiTone.copy(alpha = 0x5C / 255f),
                aiBg = aiTone.copy(alpha = 0x1A / 255f),
                success = Color(0xFF3D8F5A),
                warning = Color(0xFFC9A227),
                danger = Color(0xFFB34A4A),
            ),
            chat = ChatAreaTheme(null, null, null, null, null, null, null, null, null, false),
            stageTint = Color(0xA6212121),
            blurRadius = 12.dp,
        )
    }
}
