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

/** 底材纹理偏好：跟随主题预设（默认）或全局自定义配方（六图元自由组合），同一份 ember_theme 首选项。 */
object TexturePrefs {

    private const val NAME = "ember_theme"
    private const val KEY_CUSTOM = "texture_custom"
    private const val KEY_WEAVE = "texture_weave"
    private const val KEY_STIPPLE = "texture_stipple"
    private const val KEY_HATCH = "texture_hatch"
    private const val KEY_CROSS = "texture_cross"
    private const val KEY_ANGLE = "texture_angle"
    private const val KEY_FIBER = "texture_fiber"
    private const val KEY_GRAIN = "texture_grain"
    private const val KEY_SCALE = "texture_scale"
    private const val KEY_INTENSITY = "texture_intensity"

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 是否启用全局自定义纹理（false = 跟随主题预设）。 */
    fun custom(context: Context): Boolean = prefs(context).getBoolean(KEY_CUSTOM, false)

    /** 读自定义配方（未启用时返回值无意义，仅作编辑初值）。 */
    fun spec(context: Context): TextureSpec = TextureSpec(
        weave = prefs(context).getFloat(KEY_WEAVE, 0f),
        stipple = prefs(context).getFloat(KEY_STIPPLE, 0.4f),
        hatch = prefs(context).getFloat(KEY_HATCH, 0f),
        crossHatch = prefs(context).getFloat(KEY_CROSS, 0f),
        hatchAngle = prefs(context).getFloat(KEY_ANGLE, 45f),
        fiber = prefs(context).getFloat(KEY_FIBER, 0f),
        grain = prefs(context).getFloat(KEY_GRAIN, 0.3f),
        scale = prefs(context).getFloat(KEY_SCALE, 1f),
        intensity = prefs(context).getFloat(KEY_INTENSITY, 1f),
    )

    /** 生效纹理：未启用自定义 = null（各处 resolveTexture 回退主题预设）。 */
    fun resolve(context: Context): TextureSpec? = if (custom(context)) spec(context) else null

    fun saveCustom(context: Context, custom: Boolean, spec: TextureSpec) {
        prefs(context).edit()
            .putBoolean(KEY_CUSTOM, custom)
            .putFloat(KEY_WEAVE, spec.weave)
            .putFloat(KEY_STIPPLE, spec.stipple)
            .putFloat(KEY_HATCH, spec.hatch)
            .putFloat(KEY_CROSS, spec.crossHatch)
            .putFloat(KEY_ANGLE, spec.hatchAngle)
            .putFloat(KEY_FIBER, spec.fiber)
            .putFloat(KEY_GRAIN, spec.grain)
            .putFloat(KEY_SCALE, spec.scale)
            .putFloat(KEY_INTENSITY, spec.intensity)
            .apply()
    }
}
