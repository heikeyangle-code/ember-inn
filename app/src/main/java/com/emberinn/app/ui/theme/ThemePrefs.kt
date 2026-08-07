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
