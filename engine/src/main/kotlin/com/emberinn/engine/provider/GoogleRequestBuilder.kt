package com.emberinn.engine.provider

import com.emberinn.engine.prompt.CompletionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Gemini 工具定义（对齐官方 request.body.tools 的 function 结构）。 */
data class GeminiFunctionTool(
    val name: String,
    val description: String = "",
    val parameters: JsonObject? = null,
)

/**
 * Gemini generateContent 请求体（对齐官方 sendMakerSuiteRequest getGeminiBody）。
 * convertGooglePrompt 已移植（官方差分 41 例）；
 * 边界：calculateGoogleBudgetTokens（预算由调用方传）、
 * GEMINI_SAFETY/VERTEX_SAFETY（安全设置由上层传）。
 */
object GoogleRequestBuilder {

    /** JS 数字序列化：整数输出整数。 */
    private fun num(value: Double): JsonPrimitive =
        if (value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            JsonPrimitive(value.toInt())
        } else {
            JsonPrimitive(value)
        }

    fun build(
        model: String,
        messages: List<CompletionMessage>,
        maxOutputTokens: Int = 512,
        temperature: Double = 1.0,
        stream: Boolean = false,
        topP: Double = 1.0,
        topK: Int? = null,
        stop: List<String> = emptyList(),
        seed: Long? = null,
        responseMimeType: String? = null,
        responseSchema: JsonObject? = null,
        useSystemPrompt: Boolean = true,
        tools: List<GeminiFunctionTool> = emptyList(),
        toolChoice: JsonElement? = null,
        enableWebSearch: Boolean = false,
        requestImages: Boolean = false,
        aspectRatio: String = "",
        imageSize: String = "",
        reasoningEffort: String = "",
        includeReasoning: Boolean = false,
        reasoningBudget: Any? = 0,
        safetySettings: JsonArray = JsonArray(emptyList()),
        charName: String = "",
        userName: String = "",
        groupNames: List<String> = emptyList(),
        mediaQuality: String = "auto",
        enableThoughtSignatures: Boolean = true,
    ): String = buildFromChatML(
        model = model,
        messages = messages.map { it.toChatMLJson(mediaQuality) },
        maxOutputTokens = maxOutputTokens,
        temperature = temperature,
        stream = stream,
        topP = topP,
        topK = topK,
        stop = stop,
        seed = seed,
        responseMimeType = responseMimeType,
        responseSchema = responseSchema,
        useSystemPrompt = useSystemPrompt,
        tools = tools,
        toolChoice = toolChoice,
        enableWebSearch = enableWebSearch,
        requestImages = requestImages,
        aspectRatio = aspectRatio,
        imageSize = imageSize,
        reasoningEffort = reasoningEffort,
        includeReasoning = includeReasoning,
        reasoningBudget = reasoningBudget,
        safetySettings = safetySettings,
        charName = charName,
        userName = userName,
        groupNames = groupNames,
        enableThoughtSignatures = enableThoughtSignatures,
    )

    /** 直接吃官方 ChatML 消息（差分 fixture 与 App 层原始消息用）。 */
    fun buildFromChatML(
        model: String,
        messages: List<JsonObject>,
        maxOutputTokens: Int = 512,
        temperature: Double = 1.0,
        stream: Boolean = false,
        topP: Double = 1.0,
        topK: Int? = null,
        stop: List<String> = emptyList(),
        seed: Long? = null,
        responseMimeType: String? = null,
        responseSchema: JsonObject? = null,
        useSystemPrompt: Boolean = true,
        tools: List<GeminiFunctionTool> = emptyList(),
        toolChoice: JsonElement? = null,
        enableWebSearch: Boolean = false,
        requestImages: Boolean = false,
        aspectRatio: String = "",
        imageSize: String = "",
        reasoningEffort: String = "",
        includeReasoning: Boolean = false,
        reasoningBudget: Any? = 0,
        safetySettings: JsonArray = JsonArray(emptyList()),
        charName: String = "",
        userName: String = "",
        groupNames: List<String> = emptyList(),
        enableThoughtSignatures: Boolean = true,
    ): String {
        val isGemma3 = "gemma-3" in model
        val isLearnLM = model.contains("learnlm")
        val imageGenerationModels = listOf(
            "gemini-2.0-flash-exp",
            "gemini-2.0-flash-exp-image-generation",
            "gemini-2.0-flash-preview-image-generation",
            "gemini-2.5-flash-image-preview",
            "gemini-2.5-flash-image",
            "gemini-3-pro-image-preview",
            "gemini-3.1-flash-image-preview",
        )
        val noSearchModels = listOf(
            "gemini-2.0-flash-lite",
            "gemini-2.0-flash-lite-001",
            "gemini-2.0-flash-lite-preview-02-05",
            "gemini-robotics-er-1.5-preview",
        )
        val isThinkingConfigModel = {
            val m = model
            (Regex("^gemini-2.5-(flash|pro)").containsMatchIn(m) && !Regex("-image(-preview)?$").containsMatchIn(m)) ||
                Regex("^gemini-3[.0-9]*-(flash|pro)").containsMatchIn(m)
        }
        val isImageSizeModel = Regex("^gemini-3").containsMatchIn(model)

        // generationConfig（对齐官方，undefined 不序列化）
        val generationConfig = mutableMapOf<String, JsonElement>()
        if (stop.isNotEmpty()) generationConfig["stopSequences"] = JsonArray(stop.map { JsonPrimitive(it) })
        generationConfig["candidateCount"] = JsonPrimitive(1)
        generationConfig["maxOutputTokens"] = JsonPrimitive(maxOutputTokens)
        generationConfig["temperature"] = num(temperature)
        generationConfig["topP"] = num(topP)
        topK?.let { generationConfig["topK"] = JsonPrimitive(it) }
        responseMimeType?.let { generationConfig["responseMimeType"] = JsonPrimitive(it) }
        responseSchema?.let { generationConfig["responseSchema"] = it }
        seed?.let { generationConfig["seed"] = JsonPrimitive(it) }

        val enableImageModality = requestImages && model in imageGenerationModels
        val enableImageConfig = enableImageModality && (aspectRatio.isNotEmpty() || imageSize.isNotEmpty())
        if (enableImageModality) {
            generationConfig["responseModalities"] = JsonArray(listOf(JsonPrimitive("text"), JsonPrimitive("image")))
            if (enableImageConfig) {
                val imageConfig = mutableMapOf<String, JsonElement>()
                if (imageSize.isNotEmpty() && isImageSizeModel) imageConfig["imageSize"] = JsonPrimitive(imageSize)
                if (aspectRatio.isNotEmpty()) imageConfig["aspectRatio"] = JsonPrimitive(aspectRatio)
                generationConfig["imageConfig"] = JsonObject(imageConfig)
            }
        }

        val effectiveUseSystemPrompt = !enableImageModality && !isGemma3 && useSystemPrompt

        // tools（官方：function 工具 + 自定义工具；联网搜索；thinking 需 tools 前处理）
        val toolsList = mutableListOf<JsonElement>()
        val functionDeclarations = mutableListOf<JsonObject>()
        val customTools = mutableListOf<JsonObject>()
        for (tool in tools) {
            var parameters = tool.parameters
            if (parameters?.containsKey("\$schema") == true) {
                parameters = JsonObject(parameters.toMutableMap().apply { remove("\$schema") })
            }
            if (parameters?.get("properties")?.let { it is JsonObject && it.isEmpty() } == true) {
                parameters = null
            }
            functionDeclarations += buildJsonObject {
                put("name", JsonPrimitive(tool.name))
                put("description", JsonPrimitive(tool.description))
                parameters?.let { put("parameters", it) }
            }
        }
        if (functionDeclarations.isNotEmpty()) {
            toolsList += buildJsonObject { put("function_declarations", JsonArray(functionDeclarations)) }
        }
        if (functionDeclarations.isEmpty() && customTools.isNotEmpty()) {
            toolsList += customTools
        }

        if (enableWebSearch && !enableImageModality && !isGemma3 && !isLearnLM && model !in noSearchModels) {
            if (toolsList.none { (it as? JsonObject)?.containsKey("function_declarations") == true }) {
                toolsList += buildJsonObject { put("google_search", JsonObject(emptyMap())) }
            }
        }

        if (isThinkingConfigModel()) {
            val thinkingConfig = mutableMapOf<String, JsonElement>()
            thinkingConfig["includeThoughts"] = JsonPrimitive(includeReasoning)
            if (reasoningBudget is Int) thinkingConfig["thinkingBudget"] = JsonPrimitive(reasoningBudget)
            if (reasoningBudget is String && reasoningBudget.isNotEmpty()) thinkingConfig["thinkingLevel"] = JsonPrimitive(reasoningBudget)
            generationConfig["thinkingConfig"] = JsonObject(thinkingConfig)
        }

        // 消息转换/角色映射/system 提取/思考签名由官方 convertGooglePrompt 逐字差分处理
        val names = PromptNames(userName = userName, charName = charName, groupNames = groupNames)
        val converted = GooglePromptConverter.convert(messages, model, effectiveUseSystemPrompt, names, enableThoughtSignatures)
        val body = mutableMapOf<String, JsonElement>()
        body["contents"] = JsonArray(converted.contents)
        body["safetySettings"] = safetySettings
        body["generationConfig"] = JsonObject(generationConfig)

        if (effectiveUseSystemPrompt && converted.systemInstructionParts.isNotEmpty()) {
            body["systemInstruction"] = buildJsonObject {
                put("parts", JsonArray(converted.systemInstructionParts))
            }
        }

        if (toolsList.isNotEmpty()) {
            body["tools"] = JsonArray(toolsList)
            val functionCallingConfig = mutableMapOf<String, JsonElement>()
            when {
                toolChoice is JsonPrimitive && toolChoice.isString -> when (toolChoice.content) {
                    "none" -> functionCallingConfig["mode"] = JsonPrimitive("NONE")
                    "required" -> functionCallingConfig["mode"] = JsonPrimitive("ANY")
                    "auto" -> functionCallingConfig["mode"] = JsonPrimitive("AUTO")
                }
                toolChoice is JsonObject -> {
                    val fnName = (toolChoice["function"] as? JsonObject)?.get("name")?.let {
                        if (it is JsonPrimitive && it.isString) it.content else null
                    }
                    if (fnName != null) {
                        functionCallingConfig["mode"] = JsonPrimitive("ANY")
                        functionCallingConfig["allowedFunctionNames"] = JsonArray(listOf(JsonPrimitive(fnName)))
                    }
                }
            }
            if (functionCallingConfig.isNotEmpty()) {
                body["toolConfig"] = buildJsonObject { put("functionCallingConfig", JsonObject(functionCallingConfig)) }
            }
        }

        return JsonObject(body).toString()
    }
}
