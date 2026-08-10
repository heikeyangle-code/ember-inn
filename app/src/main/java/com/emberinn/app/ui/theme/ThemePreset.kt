package com.emberinn.app.ui.theme

import androidx.compose.ui.graphics.Color

/** README 预设主题（按中国人审美）：seed 生成整套 M3 配色，克制、低饱和。 */
data class ThemePreset(
    val id: String,
    val name: String,
    val desc: String,
    val seed: Color,
    val secondary: Color,
    val tertiary: Color,
    val lightBg: Color,
    val darkBg: Color,
    /** README 清单 9：六套主题各自性格——形状语言随预设变化（墨韵圆润/丹砂方正/琉璃浑圆）。 */
    val shape: String = "default",
)

val ThemePresets: List<ThemePreset> = listOf(
    ThemePreset(
        id = "ink",
        name = "墨韵",
        desc = "水墨留白 · 朱砂点缀",
        seed = Color(0xFFB23A2A),
        secondary = Color(0xFF55524A),
        tertiary = Color(0xFF3D5A6C),
        lightBg = Color(0xFFF7F2E8),
        darkBg = Color(0xFF171513),
        shape = "rounded",
    ),
    ThemePreset(
        id = "celadon",
        name = "青瓷",
        desc = "米白青墨 · 青绿点缀",
        seed = Color(0xFF2E7D6B),
        secondary = Color(0xFF5B6E6A),
        tertiary = Color(0xFFB49B6A),
        lightBg = Color(0xFFF5F3EC),
        darkBg = Color(0xFF15201D),
        shape = "rounded",
    ),
    ThemePreset(
        id = "night",
        name = "夜航",
        desc = "雾白深蓝 · 琥珀灯",
        seed = Color(0xFFC98A2B),
        secondary = Color(0xFF5C6B7A),
        tertiary = Color(0xFF2C4A6E),
        lightBg = Color(0xFFF4F5F6),
        darkBg = Color(0xFF101820),
        shape = "default",
    ),
    ThemePreset(
        id = "cinnabar",
        name = "丹砂",
        desc = "纸白暖黑 · 丹红克制",
        seed = Color(0xFFC73E2B),
        secondary = Color(0xFF8A5A44),
        tertiary = Color(0xFFB08A3E),
        lightBg = Color(0xFFFBF4EF),
        darkBg = Color(0xFF1B1210),
        shape = "square",
    ),
    ThemePreset(
        id = "glaze",
        name = "琉璃",
        desc = "冰白玻璃 · 紫蓝渐变",
        seed = Color(0xFF5B6CFF),
        secondary = Color(0xFF3FA7B8),
        tertiary = Color(0xFF9B6BFF),
        lightBg = Color(0xFFF5F7FB),
        darkBg = Color(0xFF10131A),
        shape = "circle",
    ),
    ThemePreset(
        id = "paper",
        name = "简约纸感",
        desc = "象牙白 · 石墨中性",
        seed = Color(0xFF5A5A5E),
        secondary = Color(0xFF6E6E72),
        tertiary = Color(0xFF808488),
        lightBg = Color(0xFFF6F4EF),
        darkBg = Color(0xFF161616),
        shape = "default",
    ),
)

fun List<ThemePreset>.byId(id: String): ThemePreset = firstOrNull { it.id == id } ?: first()

/** 主题模式：跟随系统 / 浅色 / 深色（README 默认浅色，深色跟随系统可选）。 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
}
