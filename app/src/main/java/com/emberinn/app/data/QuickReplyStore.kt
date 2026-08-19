package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.slash.QuickReplyPreset
import com.emberinn.engine.slash.QuickReplySlot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 全局快捷回复多预设存储（对齐官方 Quick Reply 扩展的 quickReplyPresets 列表）。
 *
 * 官方对照（sillytavern-ref/public/scripts/extensions/quick-reply/index.js L55-L104 loadSets）：
 *   - 官方从 /api/settings/get 返回的 `quickReplyPresets` 数组加载，数组每个元素 = 一个 "set"
 *     （含 name + qrList/buttons），并支持 v1→v2 字段迁移（quickReplySlots→qrList、autoExecute_*→executeOn*）。
 *   - 每个 set 通过 /api/quick-replies/save 与 /api/quick-replies/delete 单独持久化（即一个 set = 一个文件）。
 *
 * 本类把每个 preset 落盘为 filesDir/quick-replies-presets/{name}.json（一个文件 = 一个 QuickReplyPreset），
 * 持久化使用 org.json.JSONObject/JSONArray，磁盘键名与旧 kotlinx.serialization 输出一致：
 *   { "name": "...", "quickReplySlots": [ { mes, label, enabled, automationId, preventAutoExecute } ] }
 * 故旧文件可直接读取。槽位字段复用 engine.QuickReplySlot（mes/label/enabled/automationId/preventAutoExecute）。
 *
 * 兼容迁移（保留单预设兼容）：
 *   1) 旧单预设 filesDir/quick-replies.json  -> quick-replies-presets/default.json（name 缺省填 "default"）
 *   2) 旧多预设 filesDir/quick-replies/ 目录下各 .json -> quick-replies-presets/（逐个搬运，保留原 name）
 * 迁移成功才删除源；解析失败或名称冲突则保留源文件以保护数据。
 */
class QuickReplyStore(context: Context) {

    private val presetsDir = File(context.filesDir, "quick-replies-presets")
    private val legacySingleFile = File(context.filesDir, "quick-replies.json")
    private val legacyDir = File(context.filesDir, "quick-replies")
    private val prefs = context.getSharedPreferences("ember_quick_reply", Context.MODE_PRIVATE)

    init {
        presetsDir.mkdirs()
        migrateLegacySingle()
        migrateLegacyDir()
    }

    /** 列出全部预设，按 name 字典序。 */
    fun presets(): List<QuickReplyPreset> =
        (presetsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) } ?: emptyArray())
            .mapNotNull { f -> runCatching { readPresetFile(f) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    /** 加载指定预设；name 为空则取当前激活；激活不存在则回退第一个；全空则返回 default 空预设。 */
    fun load(name: String? = null): QuickReplyPreset {
        val target = name ?: activeName()
        return presets().firstOrNull { it.name == target }
            ?: presets().firstOrNull()
            ?: QuickReplyPreset(name = "default", slots = emptyList())
    }

    /** 保存（新增或覆盖）一个预设；name 为空则忽略。 */
    fun save(preset: QuickReplyPreset) {
        if (preset.name.isBlank()) return
        writePreset(preset.name, presetToJson(preset))
    }

    /** 删除指定预设；若删除的是当前激活预设，则清空激活记录。 */
    fun delete(name: String) {
        File(presetsDir, sanitize(name) + ".json").delete()
        if (activeName() == name) prefs.edit().remove("active").apply()
    }

    /** 设置当前激活预设名。 */
    fun setActive(name: String) {
        prefs.edit().putString("active", name).apply()
    }

    /** 当前激活预设名（未设置则为空串）。 */
    fun activeName(): String = prefs.getString("active", "") ?: ""

    /** 当前激活预设的全部槽位。 */
    fun slots(): List<QuickReplySlot> = load().slots

    /** 覆盖当前激活预设的槽位列表。 */
    fun saveSlots(slots: List<QuickReplySlot>) {
        val preset = load()
        save(preset.copy(slots = slots))
    }

    // ---- 迁移 ----

    /** 旧单预设 filesDir/quick-replies.json -> quick-replies-presets/default.json。 */
    private fun migrateLegacySingle() {
        if (!legacySingleFile.exists()) return
        val ok = runCatching {
            val text = legacySingleFile.readText()
            if (text.isBlank()) return@runCatching false
            val obj = JSONObject(text)
            val name = obj.optString("name").ifBlank { "default" }
            obj.put("name", name)
            if (presets().any { it.name == name }) return@runCatching false // 名称冲突，保留源
            writePreset(name, obj)
            true
        }.getOrDefault(false)
        if (ok) runCatching { legacySingleFile.delete() }
    }

    /** 旧多预设 filesDir/quick-replies/ 目录下各 .json -> quick-replies-presets/（逐个搬运，保留原 name）。 */
    private fun migrateLegacyDir() {
        if (!legacyDir.exists() || !legacyDir.isDirectory) return
        val files = legacyDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            ?: emptyArray()
        val existing = presets().map { it.name }.toMutableSet()
        for (f in files) {
            val ok = runCatching {
                val text = f.readText()
                if (text.isBlank()) return@runCatching false
                val obj = JSONObject(text)
                val name = obj.optString("name").ifBlank { f.nameWithoutExtension }
                obj.put("name", name)
                if (!existing.add(name)) return@runCatching false // 名称冲突，保留源
                writePreset(name, obj)
                true
            }.getOrDefault(false)
            if (ok) runCatching { f.delete() }
        }
        if (legacyDir.listFiles()?.isEmpty() == true) runCatching { legacyDir.delete() }
    }

    // ---- org.json 读写 ----

    private fun writePreset(name: String, obj: JSONObject) {
        presetsDir.mkdirs()
        File(presetsDir, sanitize(name) + ".json").writeText(obj.toString(2))
    }

    private fun readPresetFile(file: File): QuickReplyPreset =
        presetFromJson(JSONObject(file.readText()))

    private fun presetFromJson(obj: JSONObject): QuickReplyPreset {
        val name = obj.optString("name").ifBlank { "default" }
        val slotsArr = obj.optJSONArray("quickReplySlots") ?: JSONArray()
        val slots = buildList {
            for (i in 0 until slotsArr.length()) {
                val s = slotsArr.optJSONObject(i) ?: continue
                add(
                    QuickReplySlot(
                        mes = s.optString("mes"),
                        label = s.optString("label"),
                        enabled = s.optBoolean("enabled", true),
                        automationId = s.optString("automationId"),
                        preventAutoExecute = s.optBoolean("preventAutoExecute", false),
                    )
                )
            }
        }
        return QuickReplyPreset(name = name, slots = slots)
    }

    private fun presetToJson(preset: QuickReplyPreset): JSONObject {
        val obj = JSONObject()
        obj.put("name", preset.name)
        val arr = JSONArray()
        preset.slots.forEach { slot ->
            val s = JSONObject()
            s.put("mes", slot.mes)
            s.put("label", slot.label)
            s.put("enabled", slot.enabled)
            s.put("automationId", slot.automationId)
            s.put("preventAutoExecute", slot.preventAutoExecute)
            arr.put(s)
        }
        obj.put("quickReplySlots", arr)
        return obj
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "default" }
}
