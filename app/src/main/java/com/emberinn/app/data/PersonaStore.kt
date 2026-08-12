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
data class Persona(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    /** 官方 persona_description_positions：0=IN_PROMPT/2=TOP_AN/3=BOTTOM_AN/4=AT_DEPTH/9=NONE。 */
    val position: Int = 0,
    val depth: Int = 4,
    val role: Int = 0,
    val lorebook: String = "",
    val title: String = "",
)

class PersonaStore(context: Context) {

    private val file = File(context.filesDir, "personas.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    private data class PersonaList(
        val activeId: String = "",
        val personas: List<Persona> = emptyList(),
    )

    fun list(): List<Persona> = load().personas

    fun active(): Persona? {
        val data = load()
        return data.personas.firstOrNull { it.id == data.activeId } ?: data.personas.firstOrNull()
    }

    fun save(personas: List<Persona>, activeId: String? = null) {
        val data = load()
        val nextActive = activeId ?: data.activeId
        file.writeText(
            json.encodeToString(
                PersonaList.serializer(),
                PersonaList(activeId = nextActive, personas = personas),
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
