package com.emberinn.app.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    val depth: Int = 4,
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
