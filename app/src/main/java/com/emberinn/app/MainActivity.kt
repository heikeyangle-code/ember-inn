package com.emberinn.app


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import com.emberinn.app.data.FontManager
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
import com.emberinn.app.ui.theme.VibePresets
import com.emberinn.app.ui.theme.vibeById

class MainActivity : ComponentActivity() {
    /** 官方每次加载设置都会把当前采样预设应用到 oai_settings；App 等价在冷启动应用一次。 */
    private fun applySelectedSamplerPresetOnLoad() {
        val ctx = applicationContext
        if (com.emberinn.app.data.ChatRepository(ctx).profile() == null) return
        val prefs = com.emberinn.app.ui.settings.PresetPrefsStore.load(ctx)
        if (prefs.samplerPreset.isNotBlank()) {
            com.emberinn.app.ui.settings.PresetSettingsStore.applySampler(ctx, prefs.samplerPreset)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 官方 settings 加载即应用当前选中的采样预设（oai_settings.preset_settings_openai → onSettingsPresetChange）
        runCatching { applySelectedSamplerPresetOnLoad() }
        // 已下线字体的旧文件回收（lxgw.ttf 等），启动时静默执行
        runCatching { FontManager.cleanupLegacy(this) }
        enableEdgeToEdge()
        setContent {
            var mode by remember { mutableStateOf(ThemePrefs.mode(this)) }
            var preset by remember { mutableStateOf(ThemePrefs.preset(this)) }
            var textureOverride by remember { mutableStateOf(com.emberinn.app.ui.theme.BackdropPrefs.resolve(this)) }
            var appearanceRev by remember { mutableIntStateOf(0) }
            remember(appearanceRev) { Unit }
            val recipe by ThemeState.recipe.collectAsState()
            val seedColor by ThemeState.seedColor.collectAsState()
            // 第四层角色主题氛围：角色配方 style > 用户全局 vibe（README 清单 3）
            // 无配方 style 时读全局；配方 style 变更时随 remember 键重算
            var userVibe by remember { mutableStateOf(VibePrefs.resolve(this)) }
            val vibe = remember(recipe?.style) {
                recipe?.style?.takeIf { it.isNotBlank() }?.let { VibePresets.vibeById(it) } ?: userVibe
            }
            // 第三层角色主题配方：浅深锁定 > 全局模式；显式配方 seed > 角色取色/名字哈希 > 全局预设
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
                        // 角色取色主题沿用全局预设的画布与天空氛围，只换配色不换"画材"
                        backdrop = preset.backdrop,
                        auraTop = preset.auraTop,
                        auraTopLight = preset.auraTopLight,
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
                "noto" -> {
                    val files = FontManager.notoFiles(this)
                    if (files.size == 4) {
                        FontFamily(
                            Font(files[0], FontWeight.Normal, FontStyle.Normal),
                            Font(files[1], FontWeight.Bold, FontStyle.Normal),
                            Font(files[2], FontWeight.Normal, FontStyle.Italic),
                            Font(files[3], FontWeight.Bold, FontStyle.Italic),
                        )
                    } else {
                        FontFamily.Default
                    }
                }
                "source", "serif" -> FontFamily.Serif
                else -> FontFamily.Default
            }
            EmberInnTheme(darkTheme = darkTheme, preset = effectivePreset, vibe = vibe, shapes = shapes, fontFamily = fontFamily) {
                // 画布全局覆盖：设置→外观自定义（null = 跟随主题预设）
                androidx.compose.runtime.CompositionLocalProvider(
                    com.emberinn.app.ui.theme.LocalBackdropOverride provides textureOverride,
                ) {
                MainScreen(
                    themeMode = mode,
                    themePreset = preset,
                    vibe = vibe,
                    onVibeChanged = { newVibe ->
                        userVibe = newVibe
                        VibePrefs.save(this, newVibe)
                    },
                    onTextureChanged = {
                        textureOverride = com.emberinn.app.ui.theme.BackdropPrefs.resolve(this)
                        appearanceRev++
                    },
                    onAppearanceChanged = { appearanceRev++ },
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
