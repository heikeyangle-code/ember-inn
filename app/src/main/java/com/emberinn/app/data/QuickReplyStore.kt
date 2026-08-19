package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.slash.QuickReply
import com.emberinn.engine.slash.QuickReplyPreset
import com.emberinn.engine.slash.QuickReplySlot
import com.emberinn.engine.slash.QuickReplyV2Config
import com.emberinn.engine.slash.QuickReplyV2Settings
import com.emberinn.engine.slash.QuickReplyV2Set
import com.emberinn.engine.slash.QuickReplyV2Slot
import com.emberinn.engine.slash.QuickReplyVisibleSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 全局快捷回复多预设存储（对齐官方 Quick Reply 扩展 v2 完整契约）。
 *
 * 官方对照（sillytavern-ref/public/scripts/extensions/quick-reply/）：
 *   - settings 文件：/api/settings/get 的 extension_settings.quickreply =
 *     { isEnabled: Boolean, isCombined: Boolean, config: { setList: [{set, isVisible}] } }
 *     → 对应本类 [QuickReplyV2Settings]；settings 存 filesDir/quick-reply-settings.json（纯 JSON）
 *   - set 文件：一个 set = 一个 QuickReplyV2Set（version=2，qrList 为 v2 槽），
 *     存 filesDir/quick-replies-presets/{name}.json
 *   - 加载：loadSets（quick-reply/index.js L55-L104）若 version!=2 → 9 字段迁移（quickActionEnabled→…）
 *     → 迁移逻辑 1:1 在引擎层 [QuickReply.migrateSetV1ToV2]，差分通过（QuickReplyDiffTest 16 例）。
 *
 * 兼容迁移：
 *   1) 旧单预设 filesDir/quick-replies.json  → quick-replies-presets/default.json
 *   2) 旧多预设 filesDir/quick-replies/ 目录下各 .json -> quick-replies-presets/
 *   3) 读 v1 preset（{name, quickReplySlots:[{mes,label,...}]}）时调用 migrateSetV1ToV2 转为 v2，
 *      写回仍保留 v1 presetToJson 格式（向后兼容旧导入流程）。
 *   - QuickReplyPreset（v1 slots 精简型）继续被引擎 AutoExecute / SlashExecutor 消费；
 *   - QuickReplyV2Set（v2 qrList 完整版）仅在 App UI 显示/编辑 executeOn* / id 等字段。
 */
class QuickReplyStore(context: Context) {

    private val presetsDir = File(context.filesDir, "quick-replies-presets")
    private val settingsFile = File(context.filesDir, "quick-reply-settings.json")
    private val legacySingleFile = File(context.filesDir, "quick-replies.json")
    private val legacyDir = File(context.filesDir, "quick-replies")
    private val prefs = context.getSharedPreferences("ember_quick_reply", Context.MODE_PRIVATE)

    private val jsonFmt = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    init {
        presetsDir.mkdirs()
        migrateLegacySingle()
        migrateLegacyDir()
    }

    // ---------- v2 Settings ----------
    /** 读取 v2 settings；文件缺失或损坏回退默认。 */
    fun loadSettings(): QuickReplyV2Settings = runCatching {
        val text = settingsFile.readText()
        jsonFmt.decodeFromString(QuickReplyV2Settings.serializer(), text)
    }.getOrDefault(QuickReplyV2Settings(
        isEnabled = false, isCombined = false,
        config = QuickReplyV2Config(
            setList = listOf(QuickReplyVisibleSet(set = "Default", isVisible = true))
        )
    ))

    fun saveSettings(s: QuickReplyV2Settings) {
        settingsFile.writeText(jsonFmt.encodeToString(QuickReplyV2Settings.serializer(), s))
    }

    /** 切换某个 set 的可见性（settings.config.setList[i].isVisible）。 */
    fun toggleSetVisible(setName: String, visible: Boolean) {
        val s = loadSettings()
        val newList = s.config.setList.toMutableList()
        val idx = newList.indexOfFirst { it.set == setName }
        if (idx >= 0) newList[idx] = newList[idx].copy(isVisible = visible)
        else newList.add(QuickReplyVisibleSet(set = setName, isVisible = visible))
        saveSettings(s.copy(config = QuickReplyV2Config(setList = newList)))
    }

    // ---------- Presets (v1 backward) ----------
    /** 列出全部预设（v1 精简列表），按 name 字典序。 */
    fun presets(): List<QuickReplyPreset> =
        (presetsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) } ?: emptyArray())
            .mapNotNull { f -> runCatching { readPresetFile(f) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    /** 以 v2 set 形态列出（用于 UI 显示 v2 字段：executeOn* / id / isHidden …）。 */
    fun presetsV2(): List<QuickReplyV2Set> =
        (presetsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) } ?: emptyArray())
            .mapNotNull { f -> runCatching { readPresetFileV2(f) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    /** 加载指定预设 v1；name 为空则取当前激活；激活不存在则回退第一个；全空则 default 空。 */
    fun load(name: String? = null): QuickReplyPreset {
        val target = name ?: activeName()
        return presets().firstOrNull { it.name == target }
            ?: presets().firstOrNull()
            ?: QuickReplyPreset(name = "default", slots = emptyList())
    }

    fun loadV2(name: String? = null): QuickReplyV2Set {
        val target = name ?: activeName()
        return presetsV2().firstOrNull { it.name == target }
            ?: presetsV2().firstOrNull()
            ?: QuickReplyV2Set(name = "default", qrList = emptyList())
    }

    /** 保存（新增或覆盖）一个预设；name 为空则忽略。v1 写入形式，便于兼容旧流程。 */
    fun save(preset: QuickReplyPreset) {
        if (preset.name.isBlank()) return
        writePreset(preset.name, presetToJson(preset))
        ensureInSetList(preset.name)
    }

    /** 保存 v2 set（qrList 完整字段）。 */
    fun saveV2(set: QuickReplyV2Set) {
        if (set.name.isBlank()) return
        writePreset(set.name, setToJsonV2(set))
        ensureInSetList(set.name)
    }

    /** 删除指定预设；若删除的是当前激活预设，则清空激活记录；同时从 setList 移除。 */
    fun delete(name: String) {
        File(presetsDir, sanitize(name) + ".json").delete()
        if (activeName() == name) prefs.edit().remove("active").apply()
        val s = loadSettings()
        saveSettings(s.copy(config = QuickReplyV2Config(
            setList = s.config.setList.filter { it.set != name }
        )))
    }

    fun setActive(name: String) {
        prefs.edit().putString("active", name).apply()
    }

    fun activeName(): String = prefs.getString("active", "") ?: ""

    /** 当前激活预设的 v1 槽位（快捷按钮/SlashExecutor 用）。 */
    fun slots(): List<QuickReplySlot> = load().slots

    /** 当前激活预设的 v2 槽位（UI 显示 executeOn* / id 等）。 */
    fun slotsV2(): List<QuickReplyV2Slot> = loadV2().qrList

    /** 覆盖当前激活预设的 v1 槽位列表。 */
    fun saveSlots(slots: List<QuickReplySlot>) {
        val preset = load()
        save(preset.copy(slots = slots))
    }

    /** 覆盖当前激活预设的 v2 槽位列表（同时同步 v1 slots）。 */
    fun saveSlotsV2(slots: List<QuickReplyV2Slot>) {
        val set = loadV2()
        saveV2(set.copy(qrList = slots))
        // 同步为 v1 QuickReplyPreset，保证 slashexecutor/autoexec 读到一致数据
        save(QuickReplyPreset(
            name = set.name,
            slots = slots.map { s ->
                QuickReplySlot(
                    mes = s.message, label = s.label,
                    enabled = !s.isHidden, automationId = s.automationId,
                    preventAutoExecute = s.preventAutoExecute,
                )
            }
        ))
    }

    private fun ensureInSetList(name: String) {
        val s = loadSettings()
        if (s.config.setList.any { it.set == name }) return
        saveSettings(s.copy(config = QuickReplyV2Config(
            setList = s.config.setList + QuickReplyVisibleSet(set = name, isVisible = true)
        )))
    }

    // ---- 迁移 ----

    private fun migrateLegacySingle() {
        if (!legacySingleFile.exists()) return
        val ok = runCatching {
            val text = legacySingleFile.readText()
            if (text.isBlank()) return@runCatching false
            val obj = JSONObject(text)
            val name = obj.optString("name").ifBlank { "default" }
            obj.put("name", name)
            if (presets().any { it.name == name }) return@runCatching false
            writePreset(name, obj)
            true
        }.getOrDefault(false)
        if (ok) runCatching { legacySingleFile.delete() }
    }

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
                if (!existing.add(name)) return@runCatching false
                writePreset(name, obj)
                true
            }.getOrDefault(false)
            if (ok) runCatching { f.delete() }
        }
        if (legacyDir.listFiles()?.isEmpty() == true) runCatching { legacyDir.delete() }
    }

    // ---- org.json + v2 读写 ----

    private fun writePreset(name: String, obj: JSONObject) {
        presetsDir.mkdirs()
        File(presetsDir, sanitize(name) + ".json").writeText(obj.toString(2))
    }

    /** Read file as v1 preset. 如果磁盘是 v2 set，则反向生成 v1 slots。 */
    private fun readPresetFile(file: File): QuickReplyPreset {
        val text = file.readText()
        // 先尝试 v2
        val v2 = runCatching { jsonFmt.decodeFromString(QuickReplyV2Set.serializer(), text) }.getOrNull()
        if (v2 != null && v2.qrList.isNotEmpty()) {
            return QuickReplyPreset(
                name = v2.name,
                slots = v2.qrList.map { s ->
                    QuickReplySlot(
                        mes = s.message, label = s.label,
                        enabled = !s.isHidden, automationId = s.automationId,
                        preventAutoExecute = s.preventAutoExecute,
                    )
                }
            )
        }
        return presetFromJson(JSONObject(text))
    }

    /** Read file as v2 set. 如果磁盘是 v1 preset，则调用 migrateSetV1ToV2 1:1 转换。 */
    private fun readPresetFileV2(file: File): QuickReplyV2Set {
        val text = file.readText()
        val obj = runCatching { jsonFmt.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return QuickReplyV2Set(name = file.nameWithoutExtension, qrList = emptyList())
        val version = obj["version"]?.jsonPrimitive?.intOrNull
        if (version == 2) {
            return runCatching { jsonFmt.decodeFromString(QuickReplyV2Set.serializer(), text) }.getOrElse {
                QuickReplyV2Set(name = file.nameWithoutExtension, qrList = emptyList())
            }
        }
        // v1 → 引擎层 migrateSetV1ToV2 1:1 转换
        val migrated = QuickReply.migrateSetV1ToV2(obj)
        val name = migrated["name"]?.jsonPrimitive?.contentOrNull
            ?: obj["name"]?.jsonPrimitive?.contentOrNull
            ?: file.nameWithoutExtension
        val slots = runCatching<List<QuickReplyV2Slot>> {
            val arr = migrated["qrList"]?.jsonArray ?: return@runCatching emptyList()
            arr.map { el -> jsonFmt.decodeFromJsonElement(QuickReplyV2Slot.serializer(), el) }
        }.getOrElse { emptyList() }
        return QuickReplyV2Set(
            version = 2, name = name, qrList = slots,
            disableSend = migrated["disableSend"]?.jsonPrimitive?.booleanOrNull ?: false,
            placeBeforeInput = migrated["placeBeforeInput"]?.jsonPrimitive?.booleanOrNull ?: false,
            injectInput = migrated["injectInput"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

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

    private fun setToJsonV2(set: QuickReplyV2Set): JSONObject {
        val obj = JSONObject()
        obj.put("version", set.version)
        obj.put("name", set.name)
        obj.put("disableSend", set.disableSend)
        obj.put("placeBeforeInput", set.placeBeforeInput)
        obj.put("injectInput", set.injectInput)
        val arr = JSONArray()
        set.qrList.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id); o.put("label", s.label); o.put("title", s.title)
            o.put("message", s.message); o.put("isHidden", s.isHidden)
            o.put("executeOnStartup", s.executeOnStartup); o.put("executeOnUser", s.executeOnUser)
            o.put("executeOnAi", s.executeOnAi); o.put("preventAutoExecute", s.preventAutoExecute)
            o.put("automationId", s.automationId); o.put("placeBeforeInput", s.placeBeforeInput)
            o.put("injectInput", s.injectInput); o.put("disableSend", s.disableSend)
            arr.put(o)
        }
        obj.put("qrList", arr)
        return obj
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "default" }
}
