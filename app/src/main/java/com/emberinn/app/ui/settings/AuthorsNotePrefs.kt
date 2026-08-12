package com.emberinn.app.ui.settings

import android.content.Context
import com.emberinn.engine.prompt.CharaNote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement

/**
 * 作者注释全局默认与角色备注（官方 authors-note.js extension_settings.note：
 * default/defaultPosition/defaultDepth/defaultInterval/defaultRole/allowWIScan/chara）。
 */
@Serializable
data class AuthorsNotePrefs(
    val defaultPrompt: String = "",
    val defaultPosition: Int = 1,
    val defaultDepth: Int = 4,
    val defaultInterval: Int = 1,
    val defaultRole: Int = 0,
    val allowWIScan: Boolean = false,
    /** 官方 extension_settings.note.chara：按角色名。 */
    val charaNotes: Map<String, CharaNoteData> = emptyMap(),
)

@Serializable
data class CharaNoteData(
    val prompt: String = "",
    val useChara: Boolean = false,
    val position: Int = 0,
)

object AuthorsNotePrefsStore {
    private const val KEY = "authors_note_prefs_v1"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): AuthorsNotePrefs {
        val raw = context.getSharedPreferences("ember_settings", Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return AuthorsNotePrefs()
        return runCatching {
            json.decodeFromString(AuthorsNotePrefs.serializer(), raw)
        }.getOrDefault(AuthorsNotePrefs())
    }

    fun save(context: Context, prefs: AuthorsNotePrefs) {
        context.getSharedPreferences("ember_settings", Context.MODE_PRIVATE)
            .edit().putString(KEY, json.encodeToString(AuthorsNotePrefs.serializer(), prefs)).apply()
    }

    /** 按角色名取角色备注（无则 null）。 */
    fun charaNote(context: Context, charName: String): CharaNote? {
        val data = load(context).charaNotes[charName] ?: return null
        return CharaNote(name = charName, prompt = data.prompt, useChara = data.useChara, position = data.position)
    }
}
