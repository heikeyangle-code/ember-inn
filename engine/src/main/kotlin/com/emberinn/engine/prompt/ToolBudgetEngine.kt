package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 工具 token 预分配结果（对齐 populateChatCompletion ToolManager 片段）。 */
data class ToolBudgetResult(
    val reserve: Int,
    val toolMessage: List<JsonObject>?,
)

/** 对齐官方：canPerform 时把工具注册数据作为 user 消息计数并预留 token。 */
object ToolBudgetEngine {

    private val json = Json { ignoreUnknownKeys = true }

    fun preallocate(
        canPerform: Boolean,
        toolDataJson: String = "{}",
        tokenCount: Int? = null,
    ): ToolBudgetResult {
        if (!canPerform) return ToolBudgetResult(0, null)
        val toolData = json.parseToJsonElement(toolDataJson) as? JsonObject ?: JsonObject(emptyMap())
        val message = buildJsonObject {
            put("role", "user")
            put("content", toolData.toString())
        }
        val reserve = tokenCount ?: toolData.toString().length
        return ToolBudgetResult(reserve, listOf(message))
    }
}
