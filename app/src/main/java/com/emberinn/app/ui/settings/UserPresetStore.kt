package com.emberinn.app.ui.settings

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * 用户预设存储（官方 preset 文件导入：data/default-user/content/presets/{type}/{name}.json）。
 * 类型目录：context / instruct / sampler / sysprompt / reasoning。
 */
object UserPresetStore {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(context: Context, type: String): File =
        File(context.filesDir, "presets/$type").apply { mkdirs() }

    fun list(context: Context, type: String): List<String> =
        dir(context, type).listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun load(context: Context, type: String, name: String): JsonObject? =
        runCatching {
            json.parseToJsonElement(File(dir(context, type), "$name.json").readText()).jsonObject
        }.getOrNull()

    fun save(context: Context, type: String, name: String, content: String): Boolean =
        runCatching {
            val parsed = json.parseToJsonElement(content).jsonObject
            val safeName = name.replace(Regex("""[^\w\- ]"""), "").trim().ifBlank { return false }
            File(dir(context, type), "$safeName.json").writeText(json.encodeToString(JsonObject.serializer(), parsed))
            true
        }.getOrDefault(false)

    fun delete(context: Context, type: String, name: String) {
        File(dir(context, type), "$name.json").delete()
    }

    /**
     * 按字段识别官方预设类型（与 default/content/presets 结构对应）：
     * context=story_string / instruct=input_sequence+output_sequence /
     * sampler=temperature+openai_max_tokens / sysprompt=content+post_history / reasoning=prefix+suffix。
     */
    fun detectType(obj: JsonObject): String? = when {
        obj["story_string"] != null -> "context"
        obj["input_sequence"] != null && obj["output_sequence"] != null -> "instruct"
        obj["temperature"] != null && obj["openai_max_tokens"] != null -> "sampler"
        obj["content"] != null && obj["post_history"] != null -> "sysprompt"
        obj["prefix"] != null && obj["suffix"] != null -> "reasoning"
        else -> null
    }
}
