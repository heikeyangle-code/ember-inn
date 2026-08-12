package com.emberinn.app.ui.settings

import android.content.Context

/** 官方预设管理器选择（context/instruct/sampler/sysprompt/reasoning，按预设名保存）。 */
data class PresetPrefs(
    val contextPreset: String = "Default",
    val instructPreset: String = "Alpaca",
    val samplerPreset: String = "",
    val syspromptPreset: String = "",
    val reasoningPreset: String = "",
)

object PresetPrefsStore {
    private const val KEY = "preset_prefs_v1"

    fun load(context: Context): PresetPrefs {
        val p = context.getSharedPreferences("ember_settings", Context.MODE_PRIVATE)
        return PresetPrefs(
            contextPreset = p.getString("preset_context", "Default") ?: "Default",
            instructPreset = p.getString("preset_instruct", "Alpaca") ?: "Alpaca",
            samplerPreset = p.getString("preset_sampler", "") ?: "",
            syspromptPreset = p.getString("preset_sysprompt", "") ?: "",
            reasoningPreset = p.getString("preset_reasoning", "") ?: "",
        )
    }

    fun save(context: Context, prefs: PresetPrefs) {
        context.getSharedPreferences("ember_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("preset_context", prefs.contextPreset)
            .putString("preset_instruct", prefs.instructPreset)
            .putString("preset_sampler", prefs.samplerPreset)
            .putString("preset_sysprompt", prefs.syspromptPreset)
            .putString("preset_reasoning", prefs.reasoningPreset)
            .apply()
    }
}
