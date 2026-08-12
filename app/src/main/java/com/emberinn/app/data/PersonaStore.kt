package com.emberinn.app.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 人设存储（对齐官方 Persona Management：全局 personas.json + activePersona）。
 * 官方字段：name / description（含 {{char}}/{{user}} 宏，进提示词前由引擎替换）；
 * persona_descriptions[avatar] = { description, position, depth, role, lorebook, title }。
 */
@Serializable
data class PersonaConnection(
    val type: String = "character",
    val id: String = "",
)

@Serializable
data class Persona(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val title: String = "",
    /** 官方 persona avatar：本地图片路径（filesDir/persona-avatars/{id}.png）。 */
    val avatarPath: String = "",
    val lorebook: String = "",
    /** 官方 persona_description_positions：0=IN_PROMPT/2=TOP_AN/3=BOTTOM_AN/4=AT_DEPTH/9=NONE。 */
    val position: Int = 0,
    val depth: Int = 2,
    val role: Int = 0,
    /** 官方 PersonaConnection：绑定角色/群聊时自动使用该人设。 */
    val connections: List<PersonaConnection> = emptyList(),
)

class PersonaStore(context: Context) {

    private val file = File(context.filesDir, "personas.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    private data class PersonaList(
        val activeId: String = "",
        val defaultId: String = "",
        val personas: List<Persona> = emptyList(),
    )

    fun list(): List<Persona> = load().personas

    fun active(): Persona? {
        val data = load()
        return data.personas.firstOrNull { it.id == data.activeId } ?: data.personas.firstOrNull()
    }

    fun default(): Persona? {
        val data = load()
        return data.personas.firstOrNull { it.id == data.defaultId } ?: data.personas.firstOrNull()
    }

    fun setDefault(id: String) {
        val data = load()
        file.writeText(json.encodeToString(PersonaList.serializer(), data.copy(defaultId = id)))
    }

    private val avatarDir = File(context.filesDir, "persona-avatars")

    /** 官方 Backup/Restore 结果：ok=false 表示文件格式无效。 */
    data class ImportResult(val ok: Boolean, val warnings: List<String> = emptyList())

    private fun exportKey(p: Persona): String =
        p.avatarPath.takeIf { it.isNotBlank() }?.let { File(it).name } ?: p.id

    private fun restoreAvatarPath(key: String): String =
        File(avatarDir, key).takeIf { it.exists() }?.absolutePath.orEmpty()

    /**
     * 官方 Backup Personas：{ personas: {avatarKey: name}, persona_descriptions: {avatarKey: {...}},
     * default_persona: avatarKey }，文件名 personas_YYYYMMDD.json；官方备份不含 active 人设。
     */
    fun exportJson(): String {
        val data = load()
        val root = buildJsonObject {
            put("personas", buildJsonObject {
                data.personas.forEach { p -> put(exportKey(p), JsonPrimitive(p.name)) }
            })
            put("persona_descriptions", buildJsonObject {
                data.personas.forEach { p ->
                    put(exportKey(p), buildJsonObject {
                        put("description", JsonPrimitive(p.description))
                        put("position", JsonPrimitive(p.position))
                        put("depth", JsonPrimitive(p.depth))
                        put("role", JsonPrimitive(p.role))
                        put("lorebook", JsonPrimitive(p.lorebook))
                        put("title", JsonPrimitive(p.title))
                        put("connections", json.encodeToJsonElement(p.connections))
                    })
                }
            })
            data.personas.firstOrNull { it.id == data.defaultId }?.let {
                put("default_persona", JsonPrimitive(exportKey(it)))
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** 官方 Restore Personas：合并语义（已存在跳过并告警；default_persona 存在才应用）。 */
    fun importJson(text: String): ImportResult = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        val personasObj = root["personas"]?.jsonObject
        val descsObj = root["persona_descriptions"]?.jsonObject
        if (personasObj == null || descsObj == null) return ImportResult(false)

        val data = load()
        val existingKeys = data.personas.map { exportKey(it) }.toMutableSet()
        val warnings = mutableListOf<String>()
        val added = mutableListOf<Persona>()

        for ((key, value) in personasObj) {
            val name = (value as? JsonPrimitive)?.contentOrNull ?: continue
            if (key in existingKeys) {
                warnings += "人设 \"$key\"（$name）已存在，跳过"
                continue
            }
            existingKeys += key
            added += Persona(id = key, name = name, avatarPath = restoreAvatarPath(key))
        }

        for ((key, value) in descsObj) {
            val desc = value.jsonObject ?: continue
            val idx = added.indexOfFirst { exportKey(it) == key }
            if (idx < 0) {
                warnings += if (key in existingKeys) "人设 \"$key\" 的描述已存在，跳过" else "人设 \"$key\" 不存在，跳过描述"
                continue
            }
            added[idx] = added[idx].copy(
                description = (desc["description"] as? JsonPrimitive)?.contentOrNull ?: "",
                position = (desc["position"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                depth = (desc["depth"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 2,
                role = (desc["role"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                lorebook = (desc["lorebook"] as? JsonPrimitive)?.contentOrNull ?: "",
                title = (desc["title"] as? JsonPrimitive)?.contentOrNull ?: "",
                connections = desc["connections"]?.jsonArray?.mapNotNull { el ->
                    val c = el.jsonObject
                    val type = (c["type"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val id = (c["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    PersonaConnection(type = type, id = id)
                } ?: emptyList(),
            )
        }

        var nextDefault = data.defaultId
        (root["default_persona"] as? JsonPrimitive)?.contentOrNull?.let { dp ->
            val target = added.firstOrNull { exportKey(it) == dp }?.id
                ?: data.personas.firstOrNull { exportKey(it) == dp }?.id
            if (target != null) {
                nextDefault = target
            } else {
                warnings += "默认人设 \"$dp\" 不存在，跳过"
            }
        }

        file.writeText(
            json.encodeToString(
                PersonaList.serializer(),
                PersonaList(activeId = data.activeId, defaultId = nextDefault, personas = data.personas + added),
            ),
        )
        ImportResult(true, warnings)
    }.getOrElse { ImportResult(false) }

    fun save(personas: List<Persona>, activeId: String? = null, defaultId: String? = null) {
        val data = load()
        val nextActive = activeId ?: data.activeId
        val nextDefault = defaultId ?: data.defaultId
        file.writeText(
            json.encodeToString(
                PersonaList.serializer(),
                PersonaList(activeId = nextActive, defaultId = nextDefault, personas = personas),
            ),
        )
    }

    fun setActive(id: String) {
        val data = load()
        file.writeText(json.encodeToString(PersonaList.serializer(), data.copy(activeId = id)))
    }

    private fun load(): PersonaList = runCatching {
        json.decodeFromString<PersonaList>(file.readText())
    }.getOrElse { PersonaList() }
}
