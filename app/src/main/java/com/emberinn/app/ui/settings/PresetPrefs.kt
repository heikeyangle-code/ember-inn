package com.emberinn.app.ui.settings

import android.content.Context

/** 官方预设管理器选择（context/instruct/sampler/sysprompt/reasoning，按预设名保存；默认值与官方一致）。 */
data class PresetPrefs(
    val contextPreset: String = "Default",
    val instructPreset: String = "Alpaca",
    val samplerPreset: String = "Default",
    val syspromptPreset: String = "Neutral - Chat",
    val reasoningPreset: String = "Think XML",
    /** 官方 oai_settings.bind_preset_to_connection（默认 true）：应用采样预设时是否覆盖连接类字段。 */
    val bindPresetToConnection: Boolean = true,
)

object PresetPrefsStore {
    private const val KEY = "preset_prefs_v1"

    fun load(context: Context): PresetPrefs {
        val p = context.getSharedPreferences("ember_settings", Context.MODE_PRIVATE)
        return PresetPrefs(
            contextPreset = p.getString("preset_context", "Default") ?: "Default",
            instructPreset = p.getString("preset_instruct", "Alpaca") ?: "Alpaca",
            samplerPreset = p.getString("preset_sampler", "Default") ?: "Default",
            syspromptPreset = p.getString("preset_sysprompt", "Neutral - Chat") ?: "Neutral - Chat",
            reasoningPreset = p.getString("preset_reasoning", "Think XML") ?: "Think XML",
            bindPresetToConnection = p.getBoolean("preset_bind_to_connection", true),
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
            .putBoolean("preset_bind_to_connection", prefs.bindPresetToConnection)
            .apply()
    }
}
