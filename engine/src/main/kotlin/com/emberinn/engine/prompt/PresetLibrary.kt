package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 通用采样预设：name + 设置对象（openai/textgen 预设内容各异，保持原样）。 */
data class SamplerPreset(val name: String, val settings: JsonObject)

/**
 * 官方预设库（快照打包在 resources/presets/）：
 * context（上下文模板）、instruct（指令模板）、sampler-openai / sampler-textgen。
 */
object PresetLibrary {

    private val json = Json { ignoreUnknownKeys = true }

    fun contextPresets(): List<ContextSettings> =
        presets("context").map { json.decodeFromJsonElement(ContextSettings.serializer(), it) }

    fun instructPresets(): List<InstructSettings> =
        presets("instruct").map { json.decodeFromJsonElement(InstructSettings.serializer(), it) }

    fun samplerPresets(api: String): List<SamplerPreset> =
        presets("sampler-$api").map { preset ->
            val name = preset["name"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }
                ?: "未命名"
            SamplerPreset(name = name, settings = preset)
        }

    fun systemPromptPresets(): List<SamplerPreset> = genericPresets("sysprompt")

    fun reasoningPresets(): List<SamplerPreset> = genericPresets("reasoning")

    private fun genericPresets(name: String): List<SamplerPreset> =
        presets(name).map { preset ->
            val nameValue = preset["name"]?.jsonPrimitive?.let { if (it.isString) it.content else it.toString() }
                ?: "未命名"
            SamplerPreset(name = nameValue, settings = preset)
        }

    private fun presets(name: String): List<JsonObject> {
        val resource = PresetLibrary::class.java.getResource("/presets/$name.json")
            ?: error("preset resource missing: /presets/$name.json (build/resources/main/presets)")
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        return root.getValue("presets").jsonArray.map { it.jsonObject }
    }
}
