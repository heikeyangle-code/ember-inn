package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.slash.QuickReplyPreset
import com.emberinn.engine.slash.QuickReplySlot
import java.io.File
import kotlinx.serialization.json.Json

/**
 * 全局快捷回复（对齐官方 Quick Reply 扩展：data/default-user/quick-replies 目录 *.json 多预设）。
 * App 落盘 filesDir/quick-replies/*.json；旧单文件 quick-replies.json 自动迁移。
 * 槽位字段（mes/label/enabled/automationId/preventAutoExecute）完全复用官方 QuickReplyPreset/QuickReplySlot。
 */
class QuickReplyStore(context: Context) {

    private val dir = File(context.filesDir, "quick-replies")
    private val legacyFile = File(context.filesDir, "quick-replies.json")
    private val prefs = context.getSharedPreferences("ember_quick_reply", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        if (legacyFile.exists()) {
            runCatching {
                val legacy = json.decodeFromString<QuickReplyPreset>(legacyFile.readText())
                if (presets().none { it.name == legacy.name }) save(legacy)
                legacyFile.delete()
            }
        }
        if (dir.exists().not()) dir.mkdirs()
    }

    fun presets(): List<QuickReplyPreset> =
        (dir.listFiles()?.filter { it.extension == "json" } ?: emptyList())
            .mapNotNull { f -> runCatching { json.decodeFromString<QuickReplyPreset>(f.readText()) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    fun load(name: String? = null): QuickReplyPreset {
        val active = name ?: activeName()
        return presets().firstOrNull { it.name == active }
            ?: presets().firstOrNull()
            ?: QuickReplyPreset(name = "default", slots = emptyList())
    }

    fun save(preset: QuickReplyPreset) {
        if (preset.name.isBlank()) return
        dir.mkdirs()
        File(dir, sanitize(preset.name) + ".json").writeText(json.encodeToString(QuickReplyPreset.serializer(), preset))
    }

    fun delete(name: String) {
        File(dir, sanitize(name) + ".json").delete()
        if (activeName() == name) {
            prefs.edit().remove("active").apply()
        }
    }

    fun setActive(name: String) {
        prefs.edit().putString("active", name).apply()
    }

    fun activeName(): String = prefs.getString("active", "") ?: ""

    fun slots(): List<QuickReplySlot> = load().slots

    fun saveSlots(slots: List<QuickReplySlot>) {
        val preset = load()
        save(preset.copy(slots = slots))
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "default" }
}
