package com.emberinn.app.ui.settings

import android.content.Context
import com.emberinn.engine.prompt.MemoryEngine

/**
 * 记忆扩展偏好（对齐官方 extensions/memory defaultSettings，index.js:104-140）。
 * source 官方默认 extras；App 先实现 main（当前模型总结），extras 登记为待接。
 */
data class MemorySettings(
    val source: String = "main",
    val memoryFrozen: Boolean = false,
    val skipWIAN: Boolean = false,
    val prompt: String = MemoryEngine.DEFAULT_PROMPT,
    val template: String = MemoryEngine.DEFAULT_TEMPLATE,
    val position: Int = 0,
    val role: Int = 0,
    val scan: Boolean = false,
    val depth: Int = 2,
    val promptWords: Int = 200,
    val promptInterval: Int = 10,
    val promptForceWords: Int = 0,
    val overrideResponseLength: Int = 0,
    val maxMessagesPerRequest: Int = 0,
    val promptBuilder: Int = 0,
)

object MemoryPrefs {

    private const val NAME = "ember_memory"

    fun load(context: Context): MemorySettings {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return MemorySettings(
            source = p.getString("source", "main") ?: "main",
            memoryFrozen = p.getBoolean("memoryFrozen", false),
            skipWIAN = p.getBoolean("SkipWIAN", false),
            prompt = p.getString("prompt", MemoryEngine.DEFAULT_PROMPT) ?: MemoryEngine.DEFAULT_PROMPT,
            template = p.getString("template", MemoryEngine.DEFAULT_TEMPLATE) ?: MemoryEngine.DEFAULT_TEMPLATE,
            position = p.getInt("position", 0),
            role = p.getInt("role", 0),
            scan = p.getBoolean("scan", false),
            depth = p.getInt("depth", 2),
            promptWords = p.getInt("promptWords", 200),
            promptInterval = p.getInt("promptInterval", 10),
            promptForceWords = p.getInt("promptForceWords", 0),
            overrideResponseLength = p.getInt("overrideResponseLength", 0),
            maxMessagesPerRequest = p.getInt("maxMessagesPerRequest", 0),
            promptBuilder = p.getInt("prompt_builder", 0),
        )
    }

    fun save(context: Context, s: MemorySettings) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("source", s.source)
            .putBoolean("memoryFrozen", s.memoryFrozen)
            .putBoolean("SkipWIAN", s.skipWIAN)
            .putString("prompt", s.prompt)
            .putString("template", s.template)
            .putInt("position", s.position)
            .putInt("role", s.role)
            .putBoolean("scan", s.scan)
            .putInt("depth", s.depth)
            .putInt("promptWords", s.promptWords)
            .putInt("promptInterval", s.promptInterval)
            .putInt("promptForceWords", s.promptForceWords)
            .putInt("overrideResponseLength", s.overrideResponseLength)
            .putInt("maxMessagesPerRequest", s.maxMessagesPerRequest)
            .putInt("prompt_builder", s.promptBuilder)
            .apply()
    }
}
