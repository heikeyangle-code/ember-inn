package com.emberinn.app.ui.theme

import android.content.Context

/** 主题偏好（模式 + 预设主题），SharedPreferences 持久化，后续可迁 DataStore。 */
object ThemePrefs {

    private const val NAME = "ember_theme"
    private const val KEY_MODE = "mode"
    private const val KEY_PRESET = "preset"

    fun mode(context: Context): ThemeMode = runCatching {
        val id = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ThemeMode.SYSTEM.id) ?: ThemeMode.SYSTEM.id
        ThemeMode.entries.firstOrNull { it.id == id } ?: ThemeMode.SYSTEM
    }.getOrDefault(ThemeMode.SYSTEM)

    fun preset(context: Context): ThemePreset {
        val id = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRESET, ThemePresets.first().id) ?: ThemePresets.first().id
        return ThemePresets.byId(id)
    }

    fun save(context: Context, mode: ThemeMode, preset: ThemePreset) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.id)
            .putString(KEY_PRESET, preset.id)
            .apply()
    }
}

/** 视觉氛围偏好：预设 id + 自定义三项参数，全部走同一份 ember_theme 首选项。 */
object VibePrefs {

    private const val NAME = "ember_theme"
    private const val KEY_VIBE = "vibe_id"
    private const val KEY_DESAT = "vibe_desat"
    private const val KEY_WARMTH = "vibe_warmth"
    private const val KEY_GLOW = "vibe_glow"

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun resolve(context: Context): VibePreset {
        val id = prefs(context).getString(KEY_VIBE, "standard") ?: "standard"
        val base = VibePresets.vibeById(id)
        if (base.id != "custom") return base
        return VibePreset(
            id = "custom",
            name = "自定义",
            desc = "手动调节三项参数",
            desaturateLight = prefs(context).getFloat(KEY_DESAT, 0.15f),
            desaturateDark = prefs(context).getFloat(KEY_DESAT, 0.15f),
            warmth = prefs(context).getFloat(KEY_WARMTH, 0f),
            glow = prefs(context).getFloat(KEY_GLOW, 0.6f),
        )
    }

    fun save(context: Context, vibe: VibePreset) {
        val e = prefs(context).edit().putString(KEY_VIBE, vibe.id)
        if (vibe.id == "custom") {
            e.putFloat(KEY_DESAT, vibe.desaturateLight)
            e.putFloat(KEY_WARMTH, vibe.warmth)
            e.putFloat(KEY_GLOW, vibe.glow)
        }
        e.apply()
    }
}
