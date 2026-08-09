package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.worldinfo.ChatJsonl
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SessionRecord(
    val id: String,
    val characterId: String?,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    /** 群聊会话：groupId 非空时 characterId 为空，成员来自 GroupStore。 */
    val groupId: String? = null,
)

/** 聊天会话存储：sessions 目录（*.json）+ chats 目录（*.jsonl）（对齐官方 jsonl：每行一条消息 JSON）。 */
class ChatStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val sessionsDir: File get() = File(context.filesDir, "sessions").apply { mkdirs() }
    private val chatsDir: File get() = File(context.filesDir, "chats").apply { mkdirs() }

    fun findByCharacter(characterId: String?): SessionRecord? =
        list().firstOrNull { it.characterId == characterId }

    fun get(id: String): SessionRecord? = list().firstOrNull { it.id == id }

    fun list(): List<SessionRecord> =
        sessionsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<SessionRecord>(f.readText()) }.getOrNull() }
            ?.sortedWith(compareByDescending<SessionRecord> { it.pinned }.thenByDescending { it.updatedAt })
            ?: emptyList()

    fun recent(limit: Int): List<SessionRecord> = list().take(limit)

    fun upsert(record: SessionRecord) {
        File(sessionsDir, "${record.id}.json").writeText(json.encodeToString(SessionRecord.serializer(), record))
    }

    /**
     * 聊天元数据（对齐官方 ChatHeader：chats/{id}.json 的 chat_metadata）。
     * 官方字段：custom_background（背景）/ chat_backgrounds（列表）/ system_prompt / scenario / mes_example 等。
     */
    fun metadata(sessionId: String): JsonObject {
        val file = File(chatsDir, "$sessionId.json")
        if (!file.exists()) return JsonObject(emptyMap())
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            root["chat_metadata"]?.jsonObject ?: JsonObject(emptyMap())
        }.getOrDefault(JsonObject(emptyMap()))
    }

    fun saveMetadata(sessionId: String, metadata: JsonObject) {
        val header = buildJsonObject {
            put("chat_metadata", metadata)
            put("user_name", JsonPrimitive("unused"))
            put("character_name", JsonPrimitive("unused"))
        }
        File(chatsDir, "$sessionId.json").writeText(json.encodeToString(JsonObject.serializer(), header))
    }

    fun messages(sessionId: String): List<JsonElement> {
        val file = File(chatsDir, "$sessionId.jsonl")
        if (!file.exists()) return emptyList()
        return ChatJsonl.import(file.readText())
    }

    /** 消息字段对齐官方 script.js：name / is_user / is_system / send_date / mes / extra。 */
    fun append(
        sessionId: String,
        isUser: Boolean,
        content: String,
        name: String,
        media: List<MediaAttachment> = emptyList(),
        api: String? = null,
        model: String? = null,
        genStarted: String? = null,
        genFinished: String? = null,
        reasoning: String? = null,
    ) {
        val list = messages(sessionId).toMutableList()
        val extra = buildJsonObject {
            if (media.isNotEmpty()) {
                // 官方 chats.js populateFileAttachment：上传附件时写 inline_image=true
                put("inline_image", JsonPrimitive(true))
                put(
                    "media",
                    JsonArray(media.map { m ->
                        buildJsonObject {
                            put("type", JsonPrimitive(m.type))
                            put("url", JsonPrimitive(m.url))
                            if (m.title.isNotBlank()) put("title", JsonPrimitive(m.title))
                            put("source", JsonPrimitive("upload"))
                        }
                    }),
                )
            }
            // 官方消息 extra 字段：api / model / reasoning（导出对齐官方 jsonl）
            if (!api.isNullOrBlank()) put("api", JsonPrimitive(api))
            if (!model.isNullOrBlank()) put("model", JsonPrimitive(model))
            if (!reasoning.isNullOrBlank()) put("reasoning", JsonPrimitive(reasoning))
        }
        val now = java.time.Instant.now().toString()
        list += buildJsonObject {
            put("name", JsonPrimitive(name))
            put("is_user", JsonPrimitive(isUser))
            put("is_system", JsonPrimitive(false))
            put("send_date", JsonPrimitive(now))
            if (!isUser) {
                // 官方 AI 消息：gen_started / gen_finished
                put("gen_started", JsonPrimitive(genStarted ?: now))
                put("gen_finished", JsonPrimitive(genFinished ?: now))
            }
            put("mes", JsonPrimitive(content))
            put("extra", extra)
        }
        File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(list))
        get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** 编辑消息：更新文本并清空 extra.bias（对齐官方 editMessage 的 AI_OUTPUT 分支；regex/isEdit 待正则 UI 接线）。
     *  有 swipes 时同步写回 swipes[swipe_id]（官方 editMessage：mes.swipes[mes.swipe_id] = text），否则滑走再滑回会显示旧文本。 */
    fun updateMessage(sessionId: String, index: Int, content: String) {
        val list = messages(sessionId).toMutableList()
        if (index !in list.indices) return
        val el = list[index].jsonObject
        val oldExtra = el["extra"] as? JsonObject
        val newExtra = JsonObject((oldExtra?.toMap() ?: emptyMap()) + ("bias" to JsonNull))
        val withMes = el + ("mes" to JsonPrimitive(content)) + ("extra" to newExtra)
        val swipes = (el["swipes"] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull ?: "" }?.toMutableList()
        if (swipes != null && swipes.isNotEmpty()) {
            val cur = currentSwipeId(el).coerceIn(0, swipes.lastIndex)
            swipes[cur] = content
            list[index] = JsonObject(withMes + ("swipes" to JsonArray(swipes.map { JsonPrimitive(it) })))
        } else {
            list[index] = JsonObject(withMes)
        }
        save(sessionId, list)
    }

    /** 读取消息的 swipes 变体文本（无则空列表）。 */
    fun swipesOf(el: JsonElement): List<String> =
        (el.jsonObject["swipes"] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList()

    /** 读取消息的 swipe_info（无/脏则按空对象占位，长度与 swipes 一致保证下标稳定）。 */
    private fun swipeInfoOf(el: JsonElement): List<JsonObject> =
        (el.jsonObject["swipe_info"] as? JsonArray)?.map { it as? JsonObject ?: JsonObject(emptyMap()) } ?: emptyList()

    /**
     * 读取消息的 swipes 变体数（无 swipes 字段返回 0）。字段对齐官方：swipe_id / swipes[] / swipe_info[]。
     */
    fun swipeCount(el: JsonElement): Int = swipesOf(el).size

    /** 当前 swipe 下标（无则 0）。 */
    fun currentSwipeId(el: JsonElement): Int =
        (el.jsonObject["swipe_id"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

    /**
     * 对齐官方 ensureSwipes：用户/小系统消息不建 swipes；缺字段就补齐
     * （swipes=[当前 mes]、swipe_id=0、swipe_info 逐条复制 send_date/gen_started/gen_finished/extra）。
     * 返回该消息是否具备可用的 swipes。
     */
    fun ensureSwipes(sessionId: String, index: Int): Boolean {
        val list = messages(sessionId).toMutableList()
        if (index !in list.indices) return false
        val obj = list[index].jsonObject
        val isUser = obj["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true
        val isSmallSys = (obj["extra"] as? JsonObject)?.get("isSmallSys")
            ?.jsonPrimitive?.let { it.contentOrNull == "true" } == true
        if (isUser || isSmallSys) return false

        val hasSwipes = obj["swipes"] is JsonArray
        val hasSwipeId = obj["swipe_id"] != null
        val hasSwipeInfo = obj["swipe_info"] is JsonArray
        if (hasSwipes && hasSwipeId && hasSwipeInfo) {
            val cur = currentSwipeId(obj)
            val swipes = swipesOf(obj)
            return swipes.isNotEmpty() && cur in swipes.indices
        }

        val mes = obj["mes"]?.jsonPrimitive?.contentOrNull ?: ""
        val swipes = if (hasSwipes) swipesOf(obj).toMutableList() else mutableListOf(mes)
        var swipeInfo = swipeInfoOf(obj).toMutableList()
        if (swipeInfo.isEmpty()) {
            swipeInfo = swipes.map {
                buildJsonObject {
                    obj["send_date"]?.jsonPrimitive?.contentOrNull?.let { v -> put("send_date", JsonPrimitive(v)) }
                    obj["gen_started"]?.jsonPrimitive?.contentOrNull?.let { v -> put("gen_started", JsonPrimitive(v)) }
                    obj["gen_finished"]?.jsonPrimitive?.contentOrNull?.let { v -> put("gen_finished", JsonPrimitive(v)) }
                    put("extra", (obj["extra"] as? JsonObject) ?: buildJsonObject {})
                }
            }.toMutableList()
        }
        // 兜底：swipes 比 swipe_info 长时补齐（官方 syncSwipeToMes 的 backfill）
        while (swipeInfo.size < swipes.size) {
            swipeInfo += buildJsonObject { put("extra", buildJsonObject {}) }
        }
        val swipeId = currentSwipeId(obj)
        list[index] = JsonObject(obj + mapOf(
            "swipes" to JsonArray(swipes.map { JsonPrimitive(it) }),
            "swipe_info" to JsonArray(swipeInfo.map { it }),
            "swipe_id" to JsonPrimitive(swipeId),
        ))
        save(sessionId, list)
        return swipes.isNotEmpty() && swipeId in swipes.indices
    }

    /**
     * 对齐官方 syncSwipeToMes：设 swipe_id，从 swipe_info 同步 mes/send_date/gen_started/gen_finished/extra。
     * 返回是否成功（越界/无 swipes 返回 false）。
     */
    fun swipeTo(sessionId: String, index: Int, newId: Int): Boolean {
        val list = messages(sessionId).toMutableList()
        if (index !in list.indices) return false
        val obj = list[index].jsonObject
        val swipes = swipesOf(obj)
        if (swipes.isEmpty() || newId < 0 || newId >= swipes.size) return false
        var swipeInfo = swipeInfoOf(obj)
        if (swipeInfo.size < swipes.size) {
            val now = obj["send_date"]?.jsonPrimitive?.contentOrNull
            swipeInfo = (0 until swipes.size).map { i ->
                swipeInfo.getOrNull(i) ?: buildJsonObject {
                    now?.let { put("send_date", JsonPrimitive(it)) }
                    put("extra", buildJsonObject {})
                }
            }
        }
        val info = swipeInfo.getOrNull(newId) ?: JsonObject(emptyMap())
        val newObj = JsonObject(
            obj +
                mapOf(
                    "swipe_id" to JsonPrimitive(newId),
                    "swipe_info" to JsonArray(swipeInfo.map { it }),
                    "mes" to JsonPrimitive(swipes[newId]),
                ) +
                syncFromInfo(info),
        )
        list[index] = newObj
        save(sessionId, list)
        return true
    }

    /**
     * 对齐官方 swipe 生成落盘（Generate('swipe') + saveReply）：swipes 追加新文本 + swipe_info 追加，
     * swipe_id 移到新下标，消息级 mes/send_date/gen_started/extra 同步为新 swipe（extra 保留媒体等非生成字段）。
     * 返回是否成功。
     */
    fun appendSwipe(
        sessionId: String,
        index: Int,
        content: String,
        api: String? = null,
        model: String? = null,
        genStarted: String? = null,
        genFinished: String? = null,
        reasoning: String? = null,
    ): Boolean {
        val list = messages(sessionId).toMutableList()
        if (index !in list.indices) return false
        val obj = list[index].jsonObject
        val swipes = swipesOf(obj).toMutableList()
        val swipeInfo = swipeInfoOf(obj).toMutableList()
        // 兜底：还没初始化 swipes 时，把当前消息作为第 0 条
        if (swipes.isEmpty()) {
            swipes += obj["mes"]?.jsonPrimitive?.contentOrNull ?: ""
            swipeInfo += buildJsonObject {
                obj["send_date"]?.jsonPrimitive?.contentOrNull?.let { v -> put("send_date", JsonPrimitive(v)) }
                put("extra", (obj["extra"] as? JsonObject) ?: buildJsonObject {})
            }
        }
        // 新 swipe 的 extra：保留媒体等字段，只刷新生成相关字段（对齐官方 swipe 落盘只覆盖生成字段）
        val oldExtra = ((obj["extra"] as? JsonObject)?.toMutableMap() ?: mutableMapOf())
        listOf("api", "model", "reasoning", "reasoning_duration", "token_count").forEach { oldExtra.remove(it) }
        if (!api.isNullOrBlank()) oldExtra["api"] = JsonPrimitive(api)
        if (!model.isNullOrBlank()) oldExtra["model"] = JsonPrimitive(model)
        if (!reasoning.isNullOrBlank()) oldExtra["reasoning"] = JsonPrimitive(reasoning)
        val extra = JsonObject(oldExtra)
        val now = genStarted ?: java.time.Instant.now().toString()
        swipes += content
        swipeInfo += buildJsonObject {
            put("send_date", JsonPrimitive(now))
            put("gen_started", JsonPrimitive(genStarted ?: now))
            put("gen_finished", JsonPrimitive(genFinished ?: now))
            put("extra", extra)
        }
        val newId = swipes.lastIndex
        list[index] = JsonObject(
            obj +
                mapOf(
                    "swipes" to JsonArray(swipes.map { JsonPrimitive(it) }),
                    "swipe_info" to JsonArray(swipeInfo.map { it }),
                    "swipe_id" to JsonPrimitive(newId),
                    "mes" to JsonPrimitive(content),
                    "send_date" to JsonPrimitive(now),
                    "gen_started" to JsonPrimitive(genStarted ?: now),
                    "gen_finished" to JsonPrimitive(genFinished ?: now),
                    "extra" to extra,
                ),
        )
        save(sessionId, list)
        return true
    }

    /**
     * 对齐官方 deleteSwipe：删除指定下标 swipe + swipe_info，按官方规则算新 swipe_id
     * （删当前→取下一个，删末尾→取前一个），并同步 mes。返回新的 swipe_id（无法删除返回 -1）。
     */
    fun deleteSwipe(sessionId: String, index: Int, swipeIndex: Int): Int {
        val list = messages(sessionId).toMutableList()
        if (index !in list.indices) return -1
        val obj = list[index].jsonObject
        val swipes = swipesOf(obj).toMutableList()
        if (swipes.size <= 1 || swipeIndex !in swipes.indices) return -1
        swipes.removeAt(swipeIndex)
        val swipeInfo = swipeInfoOf(obj).toMutableList()
        if (swipeIndex < swipeInfo.size) swipeInfo.removeAt(swipeIndex)
        val currentId = currentSwipeId(obj).coerceAtLeast(0)
        val newId = when {
            swipeIndex < currentId -> currentId - 1
            swipeIndex > currentId -> currentId
            else -> minOf(swipeIndex, swipes.lastIndex)
        }
        val safeId = newId.coerceIn(0, swipes.lastIndex)
        val info = swipeInfo.getOrNull(safeId) ?: JsonObject(emptyMap())
        val newObj = JsonObject(
            obj +
                mapOf(
                    "swipes" to JsonArray(swipes.map { JsonPrimitive(it) }),
                    "swipe_info" to JsonArray(swipeInfo.map { it }),
                    "swipe_id" to JsonPrimitive(safeId),
                    "mes" to JsonPrimitive(swipes[safeId]),
                ) +
                syncFromInfo(info),
        )
        list[index] = newObj
        save(sessionId, list)
        return safeId
    }

    /** 从 swipe_info 单项同步消息级字段（对齐 syncSwipeToMes 的后半段）。 */
    private fun syncFromInfo(info: JsonObject): Map<String, JsonElement> = buildMap {
        info["send_date"]?.let { put("send_date", it) }
        info["gen_started"]?.let { put("gen_started", it) }
        info["gen_finished"]?.let { put("gen_finished", it) }
        put("extra", info["extra"] ?: JsonObject(emptyMap()))
    }

    private fun save(sessionId: String, list: List<JsonElement>) {
        File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(list))
        get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** 删除指定下标的一条消息（重新生成/删除消息用；对齐官方删除消息后落盘 jsonl）。 */
    /** 整体替换某会话消息（重新生成/继续/清空会话用）。 */
    fun replace(sessionId: String, elements: List<JsonElement>) {
        val removed = messages(sessionId).filter { old -> elements.none { it == old } }
        File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(elements))
        get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
        deleteMediaFiles(removed)
    }

    fun removeAt(sessionId: String, index: Int) {
        val list = messages(sessionId).toMutableList()
        if (index in list.indices) {
            val removed = list.removeAt(index)
            File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(list))
            get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
            deleteMediaFiles(listOf(removed))
        }
    }

    /** 会话列表预览：最后一条消息文本（无消息返回 null）。 */
    fun lastMessage(sessionId: String): String? {
        val list = messages(sessionId)
        if (list.isEmpty()) return null
        val mes = list.last().jsonObject["mes"]?.jsonPrimitive?.contentOrNull
        return mes?.takeIf { it.isNotBlank() }
    }

    /** 导出聊天：原始 JSONL 文本（对齐官方聊天文件格式）。 */
    fun exportJsonl(sessionId: String): String? {
        val file = File(chatsDir, "$sessionId.jsonl")
        return if (file.exists()) file.readText() else null
    }

    /** 删除单个会话（会话元数据 + 聊天 jsonl + 附件文件）。 */
    fun delete(sessionId: String) {
        deleteMediaFiles(messages(sessionId))
        File(sessionsDir, "$sessionId.json").delete()
        File(chatsDir, "$sessionId.jsonl").delete()
        File(chatsDir, "$sessionId.json").delete()
    }

    fun deleteByCharacter(characterId: String?) {
        list().filter { it.characterId == characterId }.forEach { s ->
            deleteMediaFiles(messages(s.id))
            File(sessionsDir, "${s.id}.json").delete()
            File(chatsDir, "${s.id}.jsonl").delete()
            File(chatsDir, "${s.id}.json").delete()
        }
    }


    // ---- 书签（对齐官方 bookmarks.js：checkpoint 存档 + 消息 extra.bookmark_link）----

    /** 书签名列表（chats/{sessionId}-Checkpoint-*.jsonl）。 */
    fun bookmarkNames(sessionId: String): List<String> =
        chatsDir.listFiles { f -> f.name.startsWith("$sessionId-Checkpoint-") && f.extension == "jsonl" }
            ?.map { it.name.removePrefix("$sessionId-Checkpoint-").removeSuffix(".jsonl") }
            ?.sortedDescending() ?: emptyList()

    /** 创建书签：复制当前聊天为存档，并把最后一条 AI 消息 extra.bookmark_link 写入（官方 saveBookmark）。 */
    fun createBookmark(sessionId: String, name: String): Boolean {
        val src = File(chatsDir, "$sessionId.jsonl")
        if (!src.exists()) return false
        val target = File(chatsDir, "$sessionId-Checkpoint-$name.jsonl")
        target.writeText(src.readText())
        // 官方：lastMes.extra.bookmark_link = name
        val list = messages(sessionId).toMutableList()
        val aiIdx = list.indexOfLast { el ->
            el.jsonObject["is_user"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } != true
        }
        if (aiIdx >= 0) {
            val el = list[aiIdx].jsonObject
            val extra = (el["extra"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            extra["bookmark_link"] = JsonPrimitive(name)
            list[aiIdx] = JsonObject(el + ("extra" to JsonObject(extra)))
            File(chatsDir, "$sessionId.jsonl").writeText(ChatJsonl.export(list))
        }
        return true
    }

    /** 打开书签：用存档内容替换当前聊天（官方切换 checkpoint chat；本 App 载入当前会话，调用方需二次确认）。 */
    fun openBookmark(sessionId: String, name: String): Boolean {
        val target = File(chatsDir, "$sessionId-Checkpoint-$name.jsonl")
        if (!target.exists()) return false
        File(chatsDir, "$sessionId.jsonl").writeText(target.readText())
        get(sessionId)?.let { upsert(it.copy(updatedAt = System.currentTimeMillis())) }
        return true
    }

    fun deleteBookmark(sessionId: String, name: String) {
        File(chatsDir, "$sessionId-Checkpoint-$name.jsonl").delete()
    }
    /** 清理被移除消息引用的本地附件文件（官方删除消息/附件会删文件；data URL 跳过）。 */
    private fun deleteMediaFiles(elements: List<JsonElement>) {
        elements.forEach { el ->
            val extra = el.jsonObject["extra"] as? JsonObject ?: return@forEach
            extra["media"]?.jsonArray?.forEach { me ->
                val url = me.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                if (!url.startsWith("data:")) {
                    runCatching { File(url).delete() }
                }
            }
        }
    }
}
