package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.slash.QuickReplyPreset
import com.emberinn.engine.slash.QuickReplySlot
import java.io.File
import kotlinx.serialization.json.Json

/**
 * 全局快捷回复（对齐官方 Quick Reply 扩展：预设文件 + 槽位）。
 * 官方存储 data/default-user/quick-replies 目录的 *.json；App 落盘 filesDir/quick-replies.json，
 * 结构与槽位字段（mes/label/enabled/automationId/preventAutoExecute）完全复用官方 QuickReplyPreset/QuickReplySlot，
 * 执行复用 QuickReplyExecutor（引擎已 1:1）。
 */
class QuickReplyStore(context: Context) {

    private val file = File(context.filesDir, "quick-replies.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): QuickReplyPreset = runCatching {
        json.decodeFromString<QuickReplyPreset>(file.readText())
    }.getOrElse { QuickReplyPreset(name = "default", slots = emptyList()) }

    fun save(preset: QuickReplyPreset) {
        file.writeText(json.encodeToString(QuickReplyPreset.serializer(), preset))
    }

    fun slots(): List<QuickReplySlot> = load().slots

    fun saveSlots(slots: List<QuickReplySlot>) = save(load().copy(slots = slots))
}
