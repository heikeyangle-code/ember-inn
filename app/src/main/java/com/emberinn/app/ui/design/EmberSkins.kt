package com.emberinn.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * EmberSkin 壳层皮肤包（docs/DESIGN_SYSTEM.md §五）：
 * 浅/深两份独立色板 + 形状/间距/动效性格 + 聊天区独立配色。
 * 切换皮肤 = 整个壳层换一层皮（背景+卡面+输入区+强调色整体变化）。
 *
 * 内置 6 套（2 暗 2 明 2 特色，§八）：
 *  - midnight 子夜（默认深）：DESIGN_SYSTEM §4.1 实测色值原样
 *  - glimmer 微光：Moonlit Glimmer DNA——中性近黑、引号蓝、极细描边
 *  - azure 冷灰蓝（深）：配官方内容主题 Azure 的示范组合
 *  - porcelain 素瓷（默认浅）
 *  - linen 亚麻暖白（浅·特色）
 *  - ember 余烬（深·特色暖调）
 */
data class EmberSkin(
    val id: String,
    val name: String,
    val dark: EmberColors,
    val light: EmberColors,
    val shapes: EmberShapes,
    val spacing: EmberSpacing,
    val motion: EmberMotion,
    val chat: ChatAreaTheme,
)

private fun c(argb: Long) = Color(argb)

object EmberSkins {

    /** AI 身份金三色：所有皮肤共用同族，保证"AI 在说话"一眼可辨（验收标准 2）。 */
    private fun aiTriple(dark: Boolean): Triple<Color, Color, Color> =
        if (dark) Triple(c(0xFFE9C46A), c(0x5CE9C46A), c(0x1AE9C46A))
        else Triple(c(0xFFD9A441), c(0x8CD9A441), c(0x1AD9A441))

    private fun semantic(dark: Boolean): Triple<Color, Color, Color> =
        if (dark) Triple(c(0xFF3D8F5A), c(0xFFC9A227), c(0xFFB34A4A))
        else Triple(c(0xFF3D8F5A), c(0xFFB08A1E), c(0xFFB34A4A))

    // ---------------------------------------------------------------- midnight 子夜（默认）

    val midnight = run {
        val (ai, aiSoft, aiBg) = aiTriple(dark = true)
        val (ok, warn, bad) = semantic(dark = true)
        val chat = ChatAreaTheme(
            inputBg = c(0xFF0E131A), inputText = c(0xFFD7DAE0), inputPlaceholder = c(0xFF686C73),
            inputBorder = c(0xFF2A3038), inputAccent = c(0xFF8FA8BE),
            buttonBg = c(0xFF222A34), buttonIcon = c(0xFF96999E),
            bottomScrim = c(0xD910151C), topScrim = c(0xD910151C), floatingInput = false,
        )
        EmberSkin(
            id = "midnight", name = "子夜",
            dark = EmberColors(
                bg = c(0xFF10151C), bgTint = c(0xFF171717), surface = c(0xFF1A2028), surface2 = c(0xFF222A34),
                surfaceSink = c(0xFF0E131A),
                ink = c(0xFFD7DAE0), inkSoft = c(0xFF96999E), inkMute = c(0xFF686C73), inkSoft2 = c(0xFF4A4F58),
                line = c(0xFF2A3038), lineStrong = c(0xFF3A4048),
                accent = c(0xFF8FA8BE), accentSoft = c(0x5C8FA8BE), accentBg = c(0x1A8FA8BE),
                ai = ai, aiSoft = aiSoft, aiBg = aiBg,
                success = ok, warning = warn, danger = bad,
            ),
            light = run {
                val (lai, laiSoft, laiBg) = aiTriple(dark = false)
                val (lok, lwarn, lbad) = semantic(dark = false)
                EmberColors(
                    bg = c(0xFFFCFCFA), bgTint = c(0xFFF4F4F1), surface = c(0xFFE9EAED), surface2 = c(0xFFDFE1E5),
                    surfaceSink = c(0xFFF1F1EC),
                    ink = c(0xFF171717), inkSoft = c(0xFF56595F), inkMute = c(0xFF888B91), inkSoft2 = c(0xFFB5B8BD),
                    line = c(0xFFE2E4E7), lineStrong = c(0xFFCFD2D6),
                    accent = c(0xFF4E6B68), accentSoft = c(0x8C4E6B68), accentBg = c(0x1A4E6B68),
                    ai = lai, aiSoft = laiSoft, aiBg = laiBg,
                    success = lok, warning = lwarn, danger = lbad,
                )
            },
            shapes = EmberShapes(cornerCard = 16.dp, cornerBubble = 14.dp, cornerSheet = 28.dp, cornerChip = 8.dp),
            spacing = EmberSpacing(unit = 4.dp, screenPadding = 20.dp, bubbleGap = 8.dp, sectionGap = 24.dp),
            motion = EmberMotion(),
            chat = chat,
        )
    }

    // ---------------------------------------------------------------- glimmer 微光（Moonlit DNA）

    val glimmer = run {
        val (ai, aiSoft, aiBg) = aiTriple(dark = true)
        val (ok, warn, bad) = semantic(dark = true)
        EmberSkin(
            id = "glimmer", name = "微光",
            dark = EmberColors(
                bg = c(0xFF141414), bgTint = c(0xFF171717), surface = c(0xFF1E1E1E), surface2 = c(0xFF272727),
                surfaceSink = c(0xFF0F0F0F),
                ink = c(0xFFCCCCCC), inkSoft = c(0xFF969696), inkMute = c(0xFF6C6C6C), inkSoft2 = c(0xFF4A4A4A),
                line = c(0x1FFFFFFF), lineStrong = c(0x33FFFFFF),
                accent = c(0xFF51A0DE), accentSoft = c(0x8C51A0DE), accentBg = c(0x1A51A0DE),
                ai = ai, aiSoft = aiSoft, aiBg = aiBg,
                success = ok, warning = warn, danger = bad,
            ),
            light = midnight.light,
            shapes = EmberShapes(cornerCard = 12.dp, cornerBubble = 12.dp, cornerSheet = 24.dp, cornerChip = 6.dp),
            spacing = EmberSpacing(unit = 4.dp, screenPadding = 16.dp, bubbleGap = 6.dp, sectionGap = 20.dp),
            motion = EmberMotion(scale = 0.92f),
            chat = ChatAreaTheme(
                inputBg = c(0xE61E1E1E), inputText = c(0xFFCCCCCC), inputPlaceholder = c(0xFF6C6C6C),
                inputBorder = c(0x1FFFFFFF), inputAccent = c(0xFF51A0DE),
                buttonBg = c(0xFF272727), buttonIcon = c(0xFF969696),
                bottomScrim = c(0xE6141414), topScrim = c(0xE6141414), floatingInput = true,
            ),
        )
    }

    // ---------------------------------------------------------------- azure 冷灰蓝（配官方 Azure）

    val azure = run {
        val (ai, aiSoft, aiBg) = aiTriple(dark = true)
        val (ok, warn, bad) = semantic(dark = true)
        EmberSkin(
            id = "azure", name = "冷灰蓝",
            dark = EmberColors(
                bg = c(0xFF0F141A), bgTint = c(0xFF121A22), surface = c(0xFF18202A), surface2 = c(0xFF212B37),
                surfaceSink = c(0xFF0B1016),
                ink = c(0xFFD3DDE6), inkSoft = c(0xFF93A2B0), inkMute = c(0xFF667582), inkSoft2 = c(0xFF45525E),
                line = c(0xFF26313D), lineStrong = c(0xFF36434F),
                accent = c(0xFF7FB2D8), accentSoft = c(0x8C7FB2D8), accentBg = c(0x1A7FB2D8),
                ai = ai, aiSoft = aiSoft, aiBg = aiBg,
                success = ok, warning = warn, danger = bad,
            ),
            light = midnight.light,
            shapes = EmberShapes(cornerCard = 16.dp, cornerBubble = 14.dp, cornerSheet = 28.dp, cornerChip = 8.dp),
            spacing = EmberSpacing(unit = 4.dp, screenPadding = 20.dp, bubbleGap = 8.dp, sectionGap = 24.dp),
            motion = EmberMotion(),
            chat = ChatAreaTheme(
                inputBg = c(0xFF0B1016), inputText = c(0xFFD3DDE6), inputPlaceholder = c(0xFF667582),
                inputBorder = c(0xFF26313D), inputAccent = c(0xFF7FB2D8),
                buttonBg = c(0xFF212B37), buttonIcon = c(0xFF93A2B0),
                bottomScrim = c(0xD90F141A), topScrim = c(0xD90F141A), floatingInput = false,
            ),
        )
    }

    // ---------------------------------------------------------------- porcelain 素瓷（默认浅）

    val porcelain = run {
        val (ai, aiSoft, aiBg) = aiTriple(dark = false)
        val (ok, warn, bad) = semantic(dark = false)
        val light = EmberColors(
            bg = c(0xFFFCFCFA), bgTint = c(0xFFF4F4F1), surface = c(0xFFFFFFFF), surface2 = c(0xFFF1F2F4),
            surfaceSink = c(0xFFF5F5F2),
            ink = c(0xFF171717), inkSoft = c(0xFF56595F), inkMute = c(0xFF888B91), inkSoft2 = c(0xFFB5B8BD),
            line = c(0xFFE2E4E7), lineStrong = c(0xFFCFD2D6),
            accent = c(0xFF4E6B68), accentSoft = c(0x8C4E6B68), accentBg = c(0x1A4E6B68),
            ai = ai, aiSoft = aiSoft, aiBg = aiBg,
            success = ok, warning = warn, danger = bad,
        )
        EmberSkin(
            id = "porcelain", name = "素瓷",
            dark = midnight.dark, light = light,
            shapes = EmberShapes(cornerCard = 20.dp, cornerBubble = 16.dp, cornerSheet = 32.dp, cornerChip = 10.dp),
            spacing = EmberSpacing(unit = 4.dp, screenPadding = 20.dp, bubbleGap = 10.dp, sectionGap = 28.dp),
            motion = EmberMotion(scale = 1.05f),
            chat = ChatAreaTheme(
                inputBg = c(0xFFF1F2F4), inputText = c(0xFF171717), inputPlaceholder = c(0xFF888B91),
                inputBorder = c(0xFFE2E4E7), inputAccent = c(0xFF4E6B68),
                buttonBg = c(0xFFE4E6E9), buttonIcon = c(0xFF56595F),
                bottomScrim = c(0xD9FCFCFA), topScrim = c(0xD9FCFCFA), floatingInput = false,
            ),
        )
    }

    // ---------------------------------------------------------------- linen 亚麻暖白（浅·特色）

    val linen = run {
        val (ai, aiSoft, aiBg) = aiTriple(dark = false)
        val (ok, warn, bad) = semantic(dark = false)
        val light = EmberColors(
            bg = c(0xFFFAF6EF), bgTint = c(0xFFF3EEE4), surface = c(0xFFFFFDF8), surface2 = c(0xFFF2EDE2),
            surfaceSink = c(0xFFF6F1E8),
            ink = c(0xFF29241D), inkSoft = c(0xFF6A6154), inkMute = c(0xFF98907F), inkSoft2 = c(0xFFC2BAAB),
            line = c(0xFFE5DED0), lineStrong = c(0xFFD3CABB),
            accent = c(0xFF8A6D3B), accentSoft = c(0x8C8A6D3B), accentBg = c(0x1A8A6D3B),
            ai = ai, aiSoft = aiSoft, aiBg = aiBg,
            success = ok, warning = warn, danger = bad,
        )
        EmberSkin(
            id = "linen", name = "亚麻",
            dark = midnight.dark, light = light,
            shapes = EmberShapes(cornerCard = 18.dp, cornerBubble = 16.dp, cornerSheet = 30.dp, cornerChip = 10.dp),
            spacing = EmberSpacing(unit = 4.dp, screenPadding = 20.dp, bubbleGap = 10.dp, sectionGap = 26.dp),
            motion = EmberMotion(scale = 1.05f),
            chat = ChatAreaTheme(
                inputBg = c(0xFFF2EDE2), inputText = c(0xFF29241D), inputPlaceholder = c(0xFF98907F),
                inputBorder = c(0xFFE5DED0), inputAccent = c(0xFF8A6D3B),
                buttonBg = c(0xFFEBE4D6), buttonIcon = c(0xFF6A6154),
                bottomScrim = c(0xD9FAF6EF), topScrim = c(0xD9FAF6EF), floatingInput = false,
            ),
        )
    }

    // ---------------------------------------------------------------- ember 余烬（深·特色暖调）

    val ember = run {
        val (ai, aiSoft, aiBg) = aiTriple(dark = true)
        val (ok, warn, bad) = semantic(dark = true)
        EmberSkin(
            id = "ember", name = "余烬",
            dark = EmberColors(
                bg = c(0xFF16110D), bgTint = c(0xFF1A140E), surface = c(0xFF211A13), surface2 = c(0xFF2C2318),
                surfaceSink = c(0xFF100C08),
                ink = c(0xFFE2D8CC), inkSoft = c(0xFFA99A88), inkMute = c(0xFF7A6E5F), inkSoft2 = c(0xFF544B3E),
                line = c(0xFF2E261C), lineStrong = c(0xFF42372A),
                accent = c(0xFFD98E4A), accentSoft = c(0x8CD98E4A), accentBg = c(0x1AD98E4A),
                ai = ai, aiSoft = aiSoft, aiBg = aiBg,
                success = ok, warning = warn, danger = bad,
            ),
            light = linen.light,
            shapes = EmberShapes(cornerCard = 10.dp, cornerBubble = 10.dp, cornerSheet = 20.dp, cornerChip = 6.dp),
            spacing = EmberSpacing(unit = 4.dp, screenPadding = 18.dp, bubbleGap = 6.dp, sectionGap = 22.dp),
            motion = EmberMotion(scale = 0.9f),
            chat = ChatAreaTheme(
                inputBg = c(0xFF100C08), inputText = c(0xFFE2D8CC), inputPlaceholder = c(0xFF7A6E5F),
                inputBorder = c(0xFF2E261C), inputAccent = c(0xFFD98E4A),
                buttonBg = c(0xFF2C2318), buttonIcon = c(0xFFA99A88),
                bottomScrim = c(0xD916110D), topScrim = c(0xD916110D), floatingInput = false,
            ),
        )
    }

    val all = listOf(midnight, glimmer, azure, porcelain, linen, ember)

    fun byId(id: String): EmberSkin = all.firstOrNull { it.id == id } ?: midnight

    /** 默认皮肤：子夜（暗色优先，DESIGN_SYSTEM §三 3）。 */
    val DEFAULT = midnight
}
