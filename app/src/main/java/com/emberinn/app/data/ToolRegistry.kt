package com.emberinn.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** App 层工具接口：官方 ToolManager.tools 的 Android 等价物。 */
interface EmberTool {
    val name: String
    val description: String
    val parameters: JsonObject
    fun execute(arguments: JsonObject): String
}

data class ExecutedToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String,
)

/** 工具注册表（官方 ToolManager 的 App 层执行器）。 */
object ToolRegistry {

    private val json = Json { ignoreUnknownKeys = true }
    private val tools = mutableMapOf<String, EmberTool>()

    fun register(tool: EmberTool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun list(): List<EmberTool> = tools.values.toList()

    fun get(name: String): EmberTool? = tools[name]

    /** 执行流式响应里的 tool_calls 快照（官方 ToolManager.invokeFunctionTools）。 */
    fun executeToolCalls(snapshot: JsonElement): List<ExecutedToolCall> {
        val calls = mutableListOf<ExecutedToolCall>()
        val choices = snapshot as? JsonArray ?: return calls
        for (choice in choices) {
            val toolCalls = (choice as? JsonObject)?.get("tool_calls")?.jsonArray ?: continue
            for (toolCallEl in toolCalls) {
                val toolCall = toolCallEl as? JsonObject ?: continue
                val id = toolCall["id"]?.jsonPrimitive?.content ?: continue
                val function = toolCall["function"]?.jsonObject ?: continue
                val name = function["name"]?.jsonPrimitive?.content ?: continue
                val argumentsRaw = function["arguments"]?.jsonPrimitive?.content ?: "{}"
                val arguments = runCatching { json.parseToJsonElement(argumentsRaw) as? JsonObject }
                    .getOrNull() ?: JsonObject(emptyMap())
                val tool = tools[name]
                val result = if (tool != null) {
                    runCatching { tool.execute(arguments) }.getOrElse { "Error: ${it.message}" }
                } else {
                    "Error: Unknown tool $name"
                }
                calls += ExecutedToolCall(id = id, name = name, arguments = argumentsRaw, result = result)
            }
        }
        return calls
    }
}
