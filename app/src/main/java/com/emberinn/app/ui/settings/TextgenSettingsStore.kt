package com.emberinn.app.ui.settings

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** textgenerationwebui_settings 持久化（官方 data/settings.json 的 textgenerationwebui_settings 语义）。 */
object TextgenSettingsStore {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun file(context: Context) = File(context.filesDir, "textgen_settings_v1.json")

    fun load(context: Context): JsonObject = runCatching {
        json.parseToJsonElement(file(context).readText()).jsonObject
    }.getOrDefault(JsonObject(emptyMap()))

    fun save(context: Context, settings: JsonObject) {
        file(context).writeText(json.encodeToString(JsonObject.serializer(), settings))
    }
}
