package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 官方 tool-calling.js ToolManager.parseToolCalls / #applyToolCallDelta 的引擎移植。
 * 累加器保存官方 streamData 中逐 choice/逐 tool call 的增量合并结果。
 */
class ToolCallAccumulator(
    private val toolCallingSupported: Boolean = true,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val toolCalls = mutableListOf<MutableList<MutableMap<String, JsonElement>>>()

    fun parse(parsed: JsonElement?, toolSignatures: Map<String, String> = emptyMap()) {
        if (!toolCallingSupported || parsed !is JsonObject) return

        parsed["choices"]?.jsonArrayOrNull()?.forEach { choiceEl ->
            val choice = choiceEl.jsonObjectOrNull() ?: return@forEach
            val choiceIndex = choice["index"]?.jsonPrimitive?.content?.toIntOrNull()
            val delta = choice["delta"]?.jsonObjectOrNull() ?: return@forEach
            if (choiceIndex == null) return@forEach
            val toolCallDeltas = delta["tool_calls"]?.jsonArrayOrNull() ?: return@forEach
            ensureChoice(choiceIndex)
            toolCallDeltas.forEachIndexed { deltaPosition, deltaEl ->
                val toolCallDelta = deltaEl.jsonObjectOrNull() ?: return@forEachIndexed
                val rawIndex = toolCallDelta["index"]?.jsonPrimitive?.content?.toIntOrNull()
                val toolCallIndex = if (rawIndex != null && rawIndex >= 0) rawIndex else deltaPosition
                if (toolCallIndex < 0) return@forEachIndexed
                ensureTool(choiceIndex, toolCallIndex)
                val target = toolCalls[choiceIndex][toolCallIndex]
                applyDelta(target, toolCallDelta)
                val id = target["id"]?.stringOrNull()
                if (id != null && toolSignatures.containsKey(id)) {
                    target["signature"] = JsonPrimitive(toolSignatures.getValue(id))
                }
            }
        }

        val cohereEvents = setOf("message-start", "tool-call-start", "tool-call-delta", "tool-call-end")
        if (parsed["type"]?.stringOrNull() in cohereEvents && parsed["delta"]?.jsonObjectOrNull()?.get("message") != null) {
            val toolCallIndex = parsed["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            ensureChoice(0)
            ensureTool(0, toolCallIndex)
            val message = parsed["delta"]?.jsonObjectOrNull()?.get("message")?.jsonObjectOrNull()
            if (message != null) {
                applyDelta(toolCalls[0][toolCallIndex], message)
            }
        }

        val contentBlock = parsed["content_block"]?.jsonObjectOrNull()
        if (contentBlock != null && contentBlock["type"]?.stringOrNull() == "tool_use") {
            val toolCallIndex = parsed["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            ensureChoice(0)
            ensureTool(0, toolCallIndex)
            applyDelta(toolCalls[0][toolCallIndex], contentBlock)
        }

        val deltaObj = parsed["delta"]?.jsonObjectOrNull()
        if (deltaObj != null) {
            val toolCallIndex = parsed["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val target = toolCalls.getOrNull(0)?.getOrNull(toolCallIndex)
            if (target != null && deltaObj["type"]?.stringOrNull() == "input_json_delta") {
                val jsonDelta = deltaObj["partial_json"]?.stringOrNull() ?: ""
                val existing = target["__input_json_delta"]?.stringOrNull().orEmpty()
                target["__input_json_delta"] = JsonPrimitive(existing + jsonDelta)
            }
        }

        if (parsed["type"]?.stringOrNull() == "content_block_stop") {
            val toolCallIndex = parsed["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val target = toolCalls.getOrNull(0)?.getOrNull(toolCallIndex)
            if (target != null) {
                val jsonDeltaString = target["__input_json_delta"]?.stringOrNull()
                if (!jsonDeltaString.isNullOrEmpty()) {
                    runCatching {
                        val parsedJson = json.parseToJsonElement(jsonDeltaString)
                        target.remove("__input_json_delta")
                        applyDelta(target, JsonObject(mapOf("input" to parsedJson)))
                    }
                }
            }
        }

        parsed["candidates"]?.jsonArrayOrNull()?.forEachIndexed { choiceIndex, candidateEl ->
            val parts = candidateEl.jsonObjectOrNull()?.get("content")?.jsonObjectOrNull()?.get("parts")?.jsonArrayOrNull() ?: return@forEachIndexed
            for (partEl in parts) {
                val part = partEl.jsonObjectOrNull() ?: continue
                val functionCall = part["functionCall"]?.jsonObjectOrNull() ?: continue
                ensureChoice(choiceIndex)
                val toolCallIndex = toolCalls[choiceIndex].size
                ensureTool(choiceIndex, toolCallIndex)
                val target = toolCalls[choiceIndex][toolCallIndex]
                part["thoughtSignature"]?.stringOrNull()?.let { target["thoughtSignature"] = JsonPrimitive(it) }
                applyDelta(target, functionCall)
            }
        }
    }

    fun snapshot(): JsonArray = JsonArray(
        toolCalls.map { choices ->
            JsonArray(choices.map { JsonObject(it) })
        },
    )

    private fun ensureChoice(choiceIndex: Int) {
        while (toolCalls.size <= choiceIndex) toolCalls.add(mutableListOf())
    }

    private fun ensureTool(choiceIndex: Int, toolCallIndex: Int) {
        ensureChoice(choiceIndex)
        while (toolCalls[choiceIndex].size <= toolCallIndex) {
            toolCalls[choiceIndex].add(mutableMapOf())
        }
    }

    private fun applyDelta(target: MutableMap<String, JsonElement>, delta: JsonObject) {
        for ((key, deltaValue) in delta) {
            if (key == "__proto__" || key == "constructor") continue
            val targetValue = target[key]
            if (deltaValue is JsonNull) {
                if (targetValue != null && targetValue != JsonNull) continue
                target[key] = JsonNull
                continue
            }
            if (deltaValue is JsonPrimitive && deltaValue.isString) {
                if (targetValue is JsonPrimitive && targetValue.isString) {
                    target[key] = JsonPrimitive(targetValue.content + deltaValue.content)
                } else {
                    target[key] = deltaValue
                }
            } else if (deltaValue is JsonObject) {
                val nested = if (targetValue is JsonObject) targetValue.toMutableMap() else mutableMapOf()
                applyDelta(nested, deltaValue)
                target[key] = JsonObject(nested)
            } else {
                target[key] = deltaValue
            }
        }
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
