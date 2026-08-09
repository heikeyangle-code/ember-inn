package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.group.GroupGenerationMode
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 群聊（对齐官方 group 核心字段：members/disabledMembers/generationMode）。 */
@Serializable
data class GroupRecord(
    val id: String,
    val name: String,
    val members: List<String>,
    val disabledMembers: List<String> = emptyList(),
    val generationMode: Int = GroupGenerationMode.APPEND,
)

/** 群聊存储：filesDir/groups/{id}.json（官方 data/group-chats/*.json 近似）。 */
class GroupStore(context: Context) {

    private val dir = File(context.filesDir, "groups").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun list(): List<GroupRecord> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<GroupRecord>(f.readText()) }.getOrNull() }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun get(id: String): GroupRecord? = list().firstOrNull { it.id == id }

    fun save(group: GroupRecord) {
        File(dir, "${group.id}.json").writeText(json.encodeToString(GroupRecord.serializer(), group))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
