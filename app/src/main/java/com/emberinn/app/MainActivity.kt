package com.emberinn.app

import com.emberinn.app.ui.components.UiSounds

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import com.emberinn.app.data.ThemeState
import com.emberinn.app.ui.settings.AppearancePrefs
import com.emberinn.app.ui.MainScreen
import com.emberinn.app.ui.theme.EmberInnTheme
import com.emberinn.app.ui.theme.ThemeMode
import com.emberinn.app.ui.theme.ThemePrefs
import com.emberinn.app.ui.theme.ThemePreset
import com.emberinn.app.ui.theme.ThemePresets
import com.emberinn.app.ui.theme.VibePrefs
import com.emberinn.app.ui.theme.VibePreset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiSounds.ensure(applicationContext)
        enableEdgeToEdge()
        setContent {
            var mode by remember { mutableStateOf(ThemePrefs.mode(this)) }
            var preset by remember { mutableStateOf(ThemePrefs.preset(this)) }
            var vibe by remember { mutableStateOf(VibePrefs.resolve(this)) }
            val recipe by ThemeState.recipe.collectAsState()
            val seedColor by ThemeState.seedColor.collectAsState()
            // 第三层角色主题配方：浅深锁定 > 全局模式；seed > 角色取色 > 全局预设
            val effectiveMode = when (recipe?.lockMode) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> mode
            }
            val darkTheme = when (effectiveMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val effectivePreset = remember(preset, recipe, seedColor) {
                val seedHex = recipe?.seed?.trim()
                val seed = seedHex?.takeIf { it.isNotEmpty() }?.let(::parseColor)
                    ?: seedColor?.let { Color(it.toInt()) }
                    ?: preset.seed
                if (seed != preset.seed) {
                    ThemePreset(
                        id = "character",
                        name = "角色主题",
                        desc = "角色卡配方",
                        seed = seed,
                        secondary = seed,
                        tertiary = seed,
                        lightBg = Color(0xFFF7F2E8),
                        darkBg = Color(0xFF171513),
                    )
                } else {
                    preset
                }
            }
            // 形状：角色配方优先，否则全局外观档；字体：衬线近似（M3 1.4 Typography 无 defaultFontFamily，只能整体换族）
            val globalRadius = AppearancePrefs.radius(this)
            // 形状：角色配方 > 用户全局档 > 预设性格（README 清单 8/9）
            val radius = when (recipe?.shape?.takeIf { it.isNotBlank() } ?: globalRadius) {
                "square" -> 4.dp
                "circle" -> 24.dp
                "rounded" -> 16.dp
                else -> when (preset.shape) {
                    "square" -> 4.dp
                    "circle" -> 24.dp
                    "rounded" -> 16.dp
                    else -> 12.dp
                }
            }
            val shapes = Shapes(
                extraSmall = RoundedCornerShape(radius),
                small = RoundedCornerShape(radius),
                medium = RoundedCornerShape(radius),
                large = RoundedCornerShape(radius + 8.dp),
                extraLarge = RoundedCornerShape(radius + 12.dp),
            )
            val fontFamily = when (recipe?.font ?: AppearancePrefs.font(this)) {
                "source", "serif" -> FontFamily.Serif
                else -> FontFamily.Default
            }
            EmberInnTheme(darkTheme = darkTheme, preset = effectivePreset, vibe = vibe, shapes = shapes, fontFamily = fontFamily) {
                MainScreen(
                    themeMode = mode,
                    themePreset = preset,
                    vibe = vibe,
                    onVibeChanged = { newVibe ->
                        vibe = newVibe
                        VibePrefs.save(this, newVibe)
                    },
                    onThemeChanged = { newMode: ThemeMode, newPreset: ThemePreset ->
                        mode = newMode
                        preset = newPreset
                        ThemePrefs.save(this, newMode, newPreset)
                    },
                )
            }
        }
    }
}

/** 解析 #RRGGBB / #AARRGGBB 十六进制颜色（失败返回 null）。 */
private fun parseColor(hex: String): Color? = runCatching {
    val h = hex.removePrefix("#")
    val argb = when (h.length) {
        6 -> "FF$h"
        8 -> h
        else -> return null
    }
    Color(argb.toLong(16))
}.getOrNull()
