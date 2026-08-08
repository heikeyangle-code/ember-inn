package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Anthropic 工具定义（对齐官方 request.body.tools 的 function 结构）。 */
data class AnthropicTool(
    val name: String,
    val description: String = "",
    val parameters: JsonObject = JsonObject(emptyMap()),
)

/** Anthropic 请求体构造结果：body + beta headers（对齐官方 sendClaudeRequest）。 */
data class AnthropicRequest(
    val body: String,
    val betaHeaders: List<String>,
)

/**
 * Anthropic Messages API 请求体（对齐官方 src/endpoints/backends/chat-completions.js sendClaudeRequest）。
 * convertClaudeMessages + cachingAtDepthForClaude 已移植（官方差分 41+4 例）；
 * 边界：calculateClaudeBudgetTokens（预算计算）由调用方传参（差分 fixture 同样打桩）。
 */
object AnthropicRequestBuilder {

    /** JS 数字序列化：整数输出为整数（1.0 → 1），否则保留小数。 */
    private fun num(value: Double): JsonPrimitive =
        if (value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            JsonPrimitive(value.toInt())
        } else {
            JsonPrimitive(value)
        }

    private val useThinkingRegex = Regex("claude-(3-7|opus-4|sonnet-4|haiku-4-5|opus-4-5|opus-4-6|sonnet-4-6|opus-4-7)")
    private val useWebSearchRegex = Regex("claude-(3-5|3-7|opus-4|sonnet-4|haiku-4-5|opus-4-5|opus-4-6|sonnet-4-6|opus-4-7)")
    private val limitedSamplingRegex = Regex("claude-(opus-4-1|sonnet-4-5|haiku-4-5|opus-4-5|opus-4-6|sonnet-4-6)")
    private val verbosityRegex = Regex("claude-(opus-4-5|opus-4-6|sonnet-4-6|opus-4-7)")
    private val noPrefillRegex = Regex("claude-(opus-4-6|sonnet-4-6|opus-4-7)")
    private val adaptiveRegex = Regex("claude-(opus-4-7)")
    private val noSamplingRegex = Regex("claude-(opus-4-7)")

    /**
     * @param reasoningBudget 官方 calculateClaudeBudgetTokens 的结果：数字预算 / 字符串 effort（adaptive）/ null（auto 且 adaptive → 不加 thinking）。
     * @param enableSystemPromptCache 官方 enableSystemPromptCache 配置
     * @param cachingAtDepth 官方 cachingAtDepth 配置（>=0 时启用缓存注入，本实现只发 beta 头）
     * @param enableAdaptiveThinking 官方 enableAdaptiveThinking 配置
     */
    fun build(
        model: String,
        messages: List<CompletionMessage>,
        maxTokens: Int = 512,
        temperature: Double = 1.0,
        stream: Boolean = false,
        topP: Double = 1.0,
        topK: Int? = null,
        stop: List<String> = emptyList(),
        useSystemPrompt: Boolean = true,
        systemPrompt: List<String> = emptyList(),
        assistantPrefill: String = "",
        tools: List<AnthropicTool> = emptyList(),
        toolChoice: String? = null,
        jsonSchema: JsonObject? = null,
        enableWebSearch: Boolean = false,
        reasoningEffort: String = "",
        includeReasoning: Boolean = false,
        verbosity: String = "",
        reasoningBudget: Any? = 1024,
        enableSystemPromptCache: Boolean = false,
        cachingAtDepth: Int = -1,
        enableAdaptiveThinking: Boolean = true,
        charName: String = "",
        userName: String = "",
        groupNames: List<String> = emptyList(),
        mediaQuality: String = "auto",
        promptPlaceholder: String = "Let's get started.",
        cacheTTL: String = "5m",
    ): AnthropicRequest = buildFromChatML(
        model = model,
        messages = messages.map { it.toChatMLJson(mediaQuality) },
        maxTokens = maxTokens,
        temperature = temperature,
        stream = stream,
        topP = topP,
        topK = topK,
        stop = stop,
        useSystemPrompt = useSystemPrompt,
        systemPrompt = systemPrompt,
        assistantPrefill = assistantPrefill,
        tools = tools,
        toolChoice = toolChoice,
        jsonSchema = jsonSchema,
        enableWebSearch = enableWebSearch,
        reasoningEffort = reasoningEffort,
        includeReasoning = includeReasoning,
        verbosity = verbosity,
        reasoningBudget = reasoningBudget,
        enableSystemPromptCache = enableSystemPromptCache,
        cachingAtDepth = cachingAtDepth,
        enableAdaptiveThinking = enableAdaptiveThinking,
        charName = charName,
        userName = userName,
        groupNames = groupNames,
        promptPlaceholder = promptPlaceholder,
        cacheTTL = cacheTTL,
    )

    /** 直接吃官方 ChatML 消息（差分 fixture 与 App 层原始消息用）。 */
    fun buildFromChatML(
        model: String,
        messages: List<JsonObject>,
        maxTokens: Int = 512,
        temperature: Double = 1.0,
        stream: Boolean = false,
        topP: Double = 1.0,
        topK: Int? = null,
        stop: List<String> = emptyList(),
        useSystemPrompt: Boolean = true,
        systemPrompt: List<String> = emptyList(),
        assistantPrefill: String = "",
        tools: List<AnthropicTool> = emptyList(),
        toolChoice: String? = null,
        jsonSchema: JsonObject? = null,
        enableWebSearch: Boolean = false,
        reasoningEffort: String = "",
        includeReasoning: Boolean = false,
        verbosity: String = "",
        reasoningBudget: Any? = 1024,
        enableSystemPromptCache: Boolean = false,
        cachingAtDepth: Int = -1,
        enableAdaptiveThinking: Boolean = true,
        charName: String = "",
        userName: String = "",
        groupNames: List<String> = emptyList(),
        promptPlaceholder: String = "Let's get started.",
        cacheTTL: String = "5m",
    ): AnthropicRequest {
        val betaHeaders = mutableListOf("output-128k-2025-02-19", "context-1m-2025-08-07")
        val useTools = tools.isNotEmpty()
        val names = PromptNames(userName = userName, charName = charName, groupNames = groupNames)
        val converted = ClaudeMessagesConverter.convert(
            messages,
            assistantPrefill,
            useSystemPrompt,
            useTools,
            names,
            promptPlaceholder,
        )
        val convertedMessages = converted.messages.toMutableList()

        val requestBody = buildJsonObject {
            put("system", JsonArray(emptyList()))
            put("messages", JsonArray(convertedMessages))
            put("model", JsonPrimitive(model))
            put("max_tokens", JsonPrimitive(maxTokens))
            put("stop_sequences", JsonArray(stop.map { JsonPrimitive(it) }))
            put("temperature", num(temperature))
            put("top_p", num(topP))
            if (topK != null) put("top_k", JsonPrimitive(topK)) else put("top_k", JsonPrimitive(0))
            put("stream", JsonPrimitive(stream))
        }.toMutableMap()

        if (useSystemPrompt) {
            val sysArr = if (systemPrompt.isNotEmpty()) {
                systemPrompt.map { buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(it))
                } }.toMutableList()
            } else {
                converted.systemPrompt.toMutableList()
            }
            if (enableSystemPromptCache && sysArr.isNotEmpty()) {
                val last = sysArr.last().toMutableMap()
                last["cache_control"] = buildJsonObject {
                    put("type", JsonPrimitive("ephemeral"))
                    put("ttl", JsonPrimitive(cacheTTL))
                }
                sysArr[sysArr.lastIndex] = JsonObject(last)
            }
            requestBody["system"] = JsonArray(sysArr)
        } else {
            requestBody.remove("system")
        }

        // tools / tool_choice / json_schema / web_search
        if (useTools) {
            betaHeaders += "tools-2024-05-16"
            requestBody["tool_choice"] = buildJsonObject {
                put("type", JsonPrimitive(toolChoice ?: "auto"))
            }
            val mappedTools = tools.map { tool ->
                buildJsonObject {
                    put("name", JsonPrimitive(tool.name))
                    put("description", JsonPrimitive(tool.description))
                    put("input_schema", tool.parameters)
                }
            }.toMutableList()
            if (enableSystemPromptCache && mappedTools.isNotEmpty()) {
                val last = mappedTools.last().toMutableMap()
                last["cache_control"] = buildJsonObject {
                    put("type", JsonPrimitive("ephemeral"))
                    put("ttl", JsonPrimitive(cacheTTL))
                }
                mappedTools[mappedTools.lastIndex] = JsonObject(last)
            }
            requestBody["tools"] = JsonArray(mappedTools)
        }

        if (jsonSchema != null) {
            val jsonTool = buildJsonObject {
                put("name", JsonPrimitive(jsonSchema["name"]?.let { it.toString().trim('"') } ?: "json"))
                put("description", JsonPrimitive(jsonSchema["description"]?.let { it.toString().trim('"') } ?: "Well-formed JSON object"))
                put("input_schema", jsonSchema["value"] ?: jsonSchema)
            }
            val toolsList = (requestBody["tools"] as? JsonArray)?.let { it.toMutableList() } ?: mutableListOf()
            toolsList += jsonTool
            requestBody["tools"] = JsonArray(toolsList)
            requestBody["tool_choice"] = buildJsonObject {
                put("type", JsonPrimitive("tool"))
                put("name", JsonPrimitive(jsonTool["name"]!!.toString().trim('"')))
            }
        }

        if (enableWebSearch && useWebSearchRegex.containsMatchIn(model)) {
            val webSearchTool = buildJsonObject {
                put("type", JsonPrimitive("web_search_20250305"))
                put("name", JsonPrimitive("web_search"))
            }
            val toolsList = (requestBody["tools"] as? JsonArray)?.let { it.toMutableList() } ?: mutableListOf()
            toolsList.add(0, webSearchTool)
            requestBody["tools"] = JsonArray(toolsList)
        }

        if (cachingAtDepth != -1) {
            ClaudeMessagesConverter.atDepth(convertedMessages, cachingAtDepth, cacheTTL)
            requestBody["messages"] = JsonArray(convertedMessages)
        }
        if (cachingAtDepth != -1 || enableSystemPromptCache) {
            betaHeaders += "prompt-caching-2024-07-31"
            betaHeaders += "extended-cache-ttl-2025-04-11"
        }

        // 采样限制
        if (limitedSamplingRegex.containsMatchIn(model)) {
            if (requestBody["top_p"]?.let { it.toString().toDoubleOrNull() }?.let { it < 1.0 } == true) {
                requestBody.remove("temperature")
            } else {
                requestBody.remove("top_p")
            }
        }

        if (noSamplingRegex.containsMatchIn(model)) {
            requestBody.remove("temperature")
            requestBody.remove("top_p")
            requestBody.remove("top_k")
        }

        // thinking
        val isAdaptiveModel = adaptiveRegex.containsMatchIn(model) || (enableAdaptiveThinking && Regex("claude-(opus-4-6|sonnet-4-6)").containsMatchIn(model))
        var fixThinkingPrefill = false
        val useThinking = useThinkingRegex.containsMatchIn(model)
        if (useThinking && reasoningBudget is String) {
            fixThinkingPrefill = true
            requestBody["thinking"] = buildJsonObject {
                put("type", JsonPrimitive("adaptive"))
                if (noSamplingRegex.containsMatchIn(model) && includeReasoning) {
                    put("display", JsonPrimitive("summarized"))
                }
            }
            val outputConfig = (requestBody["output_config"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            outputConfig["effort"] = JsonPrimitive(reasoningBudget)
            requestBody["output_config"] = JsonObject(outputConfig)
            requestBody.remove("top_k")
        } else if (useThinking && reasoningBudget is Int) {
            fixThinkingPrefill = true
            var maxTokensValue = maxTokens
            if (maxTokensValue <= 1024) maxTokensValue += 1024
            requestBody["max_tokens"] = JsonPrimitive(maxTokensValue)
            requestBody["thinking"] = buildJsonObject {
                put("type", JsonPrimitive("enabled"))
                put("budget_tokens", JsonPrimitive(reasoningBudget))
            }
            requestBody.remove("temperature")
            requestBody.remove("top_p")
            requestBody.remove("top_k")
        }

        if ((fixThinkingPrefill || noPrefillRegex.containsMatchIn(model)) && convertedMessages.isNotEmpty() && convertedMessages.last()["role"]?.let { it.toString().trim('"') } == "assistant") {
            val last = convertedMessages.last().toMutableMap()
            last["role"] = JsonPrimitive("user")
            convertedMessages[convertedMessages.lastIndex] = JsonObject(last)
            requestBody["messages"] = JsonArray(convertedMessages)
        }

        // verbosity
        if (verbosityRegex.containsMatchIn(model) && verbosity.isNotEmpty()) {
            val outputConfig = (requestBody["output_config"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            if (!outputConfig.containsKey("effort")) {
                betaHeaders += "effort-2025-11-24"
                outputConfig["effort"] = JsonPrimitive(verbosity)
                requestBody["output_config"] = JsonObject(outputConfig)
            }
        }

        return AnthropicRequest(
            body = JsonObject(requestBody).toString(),
            betaHeaders = betaHeaders.distinct(),
        )
    }
}
