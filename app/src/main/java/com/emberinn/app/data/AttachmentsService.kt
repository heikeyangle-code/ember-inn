package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.BehaviorPrefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * 官方 attachments 扩展（public/scripts/extensions/attachments/index.js）1:1 翻译：
 * Data Bank 附件三源（global / character / chat）CRUD + disable / enable，配套 /db-* 斜杠命令。
 *
 * 与官方对齐点：
 * - TYPES = ['global', 'character', 'chat']（index.js:16）
 * - getAttachments(source) → 全部或按源（index.js:24-30，含 disabled）
 * - getAttachmentByField → name/url 不区分大小写精确匹配（index.js:38-43）
 * - list → JSON 数组（默认 url 字段，可选 name）（index.js:67-71）
 * - get → 读 attachment.url 文件文本（index.js:79-95）
 * - add → 写入文件，name 默认 Locale 时间串（index.js:103-109）
 * - update → 删旧 + 写新（保留 name）（index.js:117-131）
 * - delete → 删文件（index.js:139-151）
 * - disable / enable → 维护 disabled_attachments url 列表（index.js:159-198）
 *
 * 存储布局（Android filesDir 下）：
 *   filesDir/attachments/global/<name>
 *   filesDir/attachments/character/<charAvatar>/<name>
 *   filesDir/attachments/chat/<chatFile>/<name>
 * disabled 列表存 UserSettingsPrefs.disabledAttachments（JSON 数组）。
 */
object AttachmentsService {

    private val TYPES = listOf("global", "character", "chat")
    private val FIELDS = listOf("name", "url")

    data class FileAttachment(
        val name: String,
        val url: String,    // 相对路径，如 "global/foo.txt"
        val source: String, // global / character / chat
        val size: Long,
    )

    /** 等价官方 getDataBankAttachments(includeDisabled=true) / getDataBankAttachmentsForSource。
     *  characterAvatar / chatFile 由调用方（ChatViewModel）传入，对应官方当前角色/会话上下文。 */
    fun getAttachments(
        context: Context,
        source: String? = null,
        characterAvatar: String = "",
        chatFile: String = "",
    ): List<FileAttachment> {
        if (source.isNullOrBlank() || !TYPES.contains(source)) {
            return TYPES.flatMap { src -> listSource(context, src, characterAvatar, chatFile) }
        }
        return listSource(context, source, characterAvatar, chatFile)
    }

    private fun listSource(
        context: Context,
        source: String,
        characterAvatar: String,
        chatFile: String,
    ): List<FileAttachment> {
        val base = baseDir(context, source, characterAvatar, chatFile)
        if (!base.exists()) return emptyList()
        return base.walkTopDown()
            .filter { it.isFile }
            .map { f ->
                val rel = "${source}/${f.relativeTo(rootDir(context)).path}"
                FileAttachment(f.name, rel, source, f.length())
            }
            .toList()
    }

    /** 等价官方 getAttachmentByField：name / url 不区分大小写精确匹配。 */
    fun getAttachmentByField(attachments: List<FileAttachment>, value: String): FileAttachment? {
        val v = value.trim().lowercase(Locale.ROOT)
        val eq = { s: String -> s.trim().lowercase(Locale.ROOT) == v }
        return attachments.firstOrNull { eq(it.url) } ?: attachments.firstOrNull { eq(it.name) }
    }

    /** 等价官方 listDataBankAttachments：返回 JSON 字符串数组（默认 url 字段，可选 name）。 */
    fun listAttachmentsJson(
        context: Context,
        source: String? = null,
        field: String = "url",
        characterAvatar: String = "",
        chatFile: String = "",
    ): String {
        val attachments = getAttachments(context, source, characterAvatar, chatFile)
        val f = if (FIELDS.contains(field)) field else "url"
        val arr = JSONArray()
        attachments.forEach { arr.put(if (f == "name") it.name else it.url) }
        return arr.toString()
    }

    /** 等价官方 getDataBankText：读 attachment.url 文本。 */
    fun getAttachmentText(
        context: Context,
        source: String? = null,
        value: String,
        characterAvatar: String = "",
        chatFile: String = "",
    ): String? {
        if (value.isBlank()) return null
        val attachments = getAttachments(context, source, characterAvatar, chatFile)
        val attachment = getAttachmentByField(attachments, value) ?: return null
        val file = urlToFile(context, attachment.url)
        return file.takeIf { it.exists() }?.readText()
    }

    /** 等价官方 uploadDataBankAttachment：source 默认 chat，name 默认 Locale 时间串。 */
    fun addAttachment(
        context: Context,
        source: String? = null,
        name: String? = null,
        content: String,
        characterAvatar: String = "",
        chatFile: String = "",
    ): String? {
        val src = if (source != null && TYPES.contains(source)) source else "chat"
        val n = if (!name.isNullOrBlank()) name else SimpleDateFormat.getDateTimeInstance().format(Date())
        val dir = baseDir(context, src, characterAvatar, chatFile)
        dir.mkdirs()
        var target = File(dir, n)
        if (target.exists()) target = File(dir, "${n}_${System.currentTimeMillis()}")
        target.writeText(content)
        return "${src}/${target.relativeTo(rootDir(context)).path}"
    }

    /** 等价官方 updateDataBankAttachment：删旧 + 写新（保留 name）。 */
    fun updateAttachment(
        context: Context,
        source: String? = null,
        name: String?,
        url: String?,
        content: String,
        characterAvatar: String = "",
        chatFile: String = "",
    ): String? {
        val src = if (source != null && TYPES.contains(source)) source else "chat"
        val attachments = getAttachments(context, src, characterAvatar, chatFile)
        val values = listOfNotNull(url, name)
        var target: FileAttachment? = null
        for (v in values) {
            target = getAttachmentByField(attachments, v)
            if (target != null) break
        }
        target ?: return null
        urlToFile(context, target.url).takeIf { it.exists() }?.delete()
        val dir = baseDir(context, src, characterAvatar, chatFile)
        dir.mkdirs()
        val newFile = File(dir, target.name)
        newFile.writeText(content)
        return "${src}/${newFile.relativeTo(rootDir(context)).path}"
    }

    /** 等价官方 deleteDataBankAttachment。 */
    fun deleteAttachment(
        context: Context,
        source: String? = null,
        value: String,
        characterAvatar: String = "",
        chatFile: String = "",
    ): Boolean {
        val src = if (source != null && TYPES.contains(source)) source else "chat"
        val attachments = getAttachments(context, src, characterAvatar, chatFile)
        val attachment = getAttachmentByField(attachments, value) ?: return false
        return urlToFile(context, attachment.url).takeIf { it.exists() }?.delete() ?: false
    }

    /** 等价官方 disableDataBankAttachment：把 url 加入 disabled 列表。 */
    fun disableAttachment(
        context: Context,
        source: String? = null,
        value: String,
        characterAvatar: String = "",
        chatFile: String = "",
    ): Boolean {
        val attachments = getAttachments(context, source, characterAvatar, chatFile)
        val attachment = getAttachmentByField(attachments, value) ?: return false
        val disabled = BehaviorPrefs.disabledAttachments(context).toMutableList()
        if (disabled.contains(attachment.url)) return true
        disabled.add(attachment.url)
        BehaviorPrefs.saveDisabledAttachments(context, disabled)
        return true
    }

    /** 等价官方 enableDataBankAttachment：从 disabled 列表移除 url。 */
    fun enableAttachment(
        context: Context,
        source: String? = null,
        value: String,
        characterAvatar: String = "",
        chatFile: String = "",
    ): Boolean {
        val attachments = getAttachments(context, source, characterAvatar, chatFile)
        val attachment = getAttachmentByField(attachments, value) ?: return false
        val disabled = BehaviorPrefs.disabledAttachments(context).toMutableList()
        val idx = disabled.indexOf(attachment.url)
        if (idx == -1) return true
        disabled.removeAt(idx)
        BehaviorPrefs.saveDisabledAttachments(context, disabled)
        return true
    }

    /** 获取已禁用附件列表（VectorRAG 注入时跳过这些）。 */
    fun disabledUrls(context: Context): List<String> = BehaviorPrefs.disabledAttachments(context)

    // ---- 文件路径辅助 ----

    private fun rootDir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    private fun baseDir(
        context: Context,
        source: String,
        characterAvatar: String,
        chatFile: String,
    ): File = when (source) {
        "global" -> File(rootDir(context), "global")
        "character" -> File(rootDir(context), "character/${characterAvatar.ifBlank { "_default" }}")
        "chat" -> File(rootDir(context), "chat/${chatFile.ifBlank { "_default" }}")
        else -> File(rootDir(context), "chat/${chatFile.ifBlank { "_default" }}")
    }

    private fun urlToFile(context: Context, url: String): File =
        File(rootDir(context), url)
}
