package com.emberinn.app.ui.settings

import android.content.Context

/** 图片描述扩展偏好（对齐官方 extensions/caption defaultSettings）。 */
data class CaptionSettings(
    val enabled: Boolean = false,
    val source: String = "multimodal",
    val prompt: String = "What's in this image?",
    val template: String = "[{{user}} sends {{char}} a picture that contains: {{caption}}]",
    val showInChat: Boolean = false,
    val refineMode: Boolean = false,
    val promptAsk: Boolean = false,
)

object CaptionPrefs {

    private const val NAME = "ember_caption"

    fun load(context: Context): CaptionSettings {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return CaptionSettings(
            enabled = p.getBoolean("enabled", false),
            source = p.getString("source", "multimodal") ?: "multimodal",
            prompt = p.getString("prompt", "What's in this image?") ?: "What's in this image?",
            template = p.getString("template", "[{{user}} sends {{char}} a picture that contains: {{caption}}]")
                ?: "[{{user}} sends {{char}} a picture that contains: {{caption}}]",
            showInChat = p.getBoolean("show_in_chat", false),
            refineMode = p.getBoolean("refine_mode", false),
            promptAsk = p.getBoolean("prompt_ask", false),
        )
    }

    fun save(context: Context, s: CaptionSettings) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled)
            .putString("source", s.source)
            .putString("prompt", s.prompt)
            .putString("template", s.template)
            .putBoolean("show_in_chat", s.showInChat)
            .putBoolean("refine_mode", s.refineMode)
            .putBoolean("prompt_ask", s.promptAsk)
            .apply()
    }
}
