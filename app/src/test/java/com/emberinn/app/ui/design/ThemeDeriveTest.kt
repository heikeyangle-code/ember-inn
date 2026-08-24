package com.emberinn.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ShellTheme.derive 可读性守卫与字段解析单测（DESIGN_SYSTEM.md §八-2）：
 * 穷举异常主题——黑底黑字 / 白底白字 / 畸形 JSON / 缺字段 / 模糊档边界。
 */
class ThemeDeriveTest {

    private fun q(v: String): String = "\"$v\""

    private fun themeJson(vararg kv: Pair<String, String>): String =
        "{" + kv.joinToString(",") { "\"" + it.first + "\":" + it.second } + "}"

    @Test
    fun `black on black gets lifted to readable`() {
        val d = ShellTheme.derive(
            themeJson(
                "blur_tint_color" to q("rgba(0, 0, 0, 1)"),
                "main_text_color" to q("rgba(8, 8, 8, 1)"),
                "quote_text_color" to q("rgba(3, 3, 3, 1)"),
                "italics_text_color" to q("rgba(1, 1, 1, 1)"),
            ),
        )
        assertTrue(ShellTheme.contrastRatio(d.colors.ink, d.colors.bg) >= 4.4f)
        assertTrue(ShellTheme.contrastRatio(d.colors.accent, d.colors.bg) >= 2.9f)
    }

    @Test
    fun `white on white gets pushed down to readable`() {
        val d = ShellTheme.derive(
            themeJson(
                "blur_tint_color" to q("rgba(255, 255, 255, 1)"),
                "main_text_color" to q("rgba(250, 250, 250, 1)"),
                "quote_text_color" to q("rgba(252, 252, 252, 1)"),
            ),
        )
        assertTrue(ShellTheme.contrastRatio(d.colors.ink, d.colors.bg) >= 4.4f)
        assertTrue(ShellTheme.contrastRatio(d.colors.accent, d.colors.bg) >= 2.9f)
    }

    @Test
    fun `normal theme keeps guarded values close to source`() {
        val d = ShellTheme.derive(
            themeJson(
                "blur_tint_color" to q("rgba(30, 30, 30, 1)"),
                "main_text_color" to q("rgba(198, 198, 198, 1)"),
                "quote_text_color" to q("rgba(81, 160, 222, 1)"),
            ),
        )
        // 正常主题守卫不应改动：#C6C6C6 对近黑底远超 4.5
        assertEquals(Color(0xFFC6C6C6), d.colors.ink)
        assertEquals(Color(0xFF51A0DE), d.colors.accent)
    }

    @Test
    fun `malformed json falls back`() {
        val d = ShellTheme.derive("{ this is not json")
        assertSame(ShellTheme.FALLBACK, d)
    }

    @Test
    fun `missing fields still derives without crash`() {
        val d = ShellTheme.derive(themeJson("quote_text_color" to q("#51A0DE")))
        assertEquals(Color(0xFF51A0DE), d.colors.accent)
        assertTrue(ShellTheme.contrastRatio(d.colors.ink, d.colors.bg) >= 4.4f)
    }

    @Test
    fun `fast ui mode kills blur entirely`() {
        val d = ShellTheme.derive(
            themeJson(
                "blur_tint_color" to q("rgba(30, 30, 30, 1)"),
                "fast_ui_mode" to "true",
            ),
        )
        assertEquals(0.dp, d.blurRadius)
    }

    @Test
    fun `blur strength maps and clamps`() {
        val low = ShellTheme.derive(
            themeJson("blur_tint_color" to q("rgba(30, 30, 30, 1)"), "blur_strength" to "5"),
        )
        assertEquals(6.dp, low.blurRadius)
        val high = ShellTheme.derive(
            themeJson("blur_tint_color" to q("rgba(30, 30, 30, 1)"), "blur_strength" to "30"),
        )
        assertEquals(36.dp, high.blurRadius)
    }

    @Test
    fun `ai identity is theme-derived not brand gold`() {
        val d = ShellTheme.derive(
            themeJson(
                "blur_tint_color" to q("rgba(30, 30, 30, 1)"),
                "bot_mes_blur_tint_color" to q("rgba(255, 255, 255, 0.05)"),
            ),
        )
        assertNotEquals(Color(0xFFE9C46A), d.colors.ai)
    }
}
