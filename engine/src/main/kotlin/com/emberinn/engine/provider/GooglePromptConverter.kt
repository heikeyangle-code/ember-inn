package com.emberinn.engine.provider

import com.emberinn.engine.media.MediaConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 对齐官方 convertGooglePrompt（src/prompt-converters.js，逐字差分）：
 * 前置 system 提取、role 修正（assistant→model、system/tool→user）、内容块包装、
 * name 前缀、text/tool_call_id/tool_calls/媒体块转换、Gemini 2.5/3 思考签名注入、
 * 同角色合并（文本 \n\n 拼接、媒体/函数块追加）。
 */
object GooglePromptConverter {

    data class Result(val contents: List<JsonObject>, val systemInstructionParts: List<JsonObject>)

    private val json = Json { ignoreUnknownKeys = true }
    private val gemini3 = Regex("gemini-3")
    private val gemini25 = Regex("gemini-2\\.5")
    private val imageModel = Regex("-image")

    fun convert(
        messages: List<JsonObject>,
        model: String,
        useSysPrompt: Boolean = false,
        names: PromptNames = PromptNames(),
        enableThoughtSignatures: Boolean = true,
    ): Result {
        val msgs = messages.map { JsonObject(it) }.toMutableList()
        val sysPrompt = mutableListOf<String>()

        if (useSysPrompt) {
            // 官方条件为 messages.length > 1：只有一条 system 时不做提取（保留在 contents 变 user）
            while (msgs.size > 1 && msgs[0].role() == "system") {
                sysPrompt += prefixExampleNames(msgs[0].str("content").orEmpty(), msgs[0].name(), names)
                msgs.removeAt(0)
            }
        }
        val systemParts = sysPrompt.map { buildJsonObject { put("text", JsonPrimitive(it)) } }

        val contents = mutableListOf<JsonObject>()
        val toolNameMap = mutableMapOf<String, String>()

        for (raw in msgs) {
            var msg = raw
            val role = when (msg.role()) {
                "system", "tool" -> "user"
                "assistant" -> "model"
                else -> msg.role()
            }
            msg = msg.set("role", JsonPrimitive(role))

            // 非数组内容 → 包装成单个内容块
            val content = msg["content"]
            if (content !is JsonArray) {
                val wrapped = when {
                    (msg["tool_calls"] as? JsonArray)?.isNotEmpty() == true -> buildJsonObject {
                        put("type", JsonPrimitive("tool_calls"))
                        put("tool_calls", msg["tool_calls"]!!)
                    }
                    msg.str("tool_call_id")?.isNotEmpty() == true -> buildJsonObject {
                        put("type", JsonPrimitive("tool_call_id"))
                        put("tool_call_id", JsonPrimitive(msg.str("tool_call_id").orEmpty()))
                        put("content", JsonPrimitive((content as? JsonPrimitive)?.content ?: ""))
                    }
                    else -> buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive((content as? JsonPrimitive)?.content ?: ""))
                    }
                }
                msg = msg.set("content", JsonArray(listOf(wrapped)))
            }

            // 名字前缀（对齐官方：example 用 userName/charName，其他用 message.name，均有 startsWith 检查）
            val mName = msg.name()
            if (!mName.isNullOrEmpty()) {
                val prefixed = msg.contentParts().map { p ->
                    if (p.str("type") != "text") p
                    else {
                        val rawText = p.str("text").orEmpty()
                        val fixed = when (mName) {
                            "example_user" ->
                                if (names.userName.isNotEmpty() && !rawText.startsWith("${names.userName}: ")) "${names.userName}: $rawText" else rawText
                            "example_assistant" ->
                                if (names.charName.isNotEmpty() && !rawText.startsWith("${names.charName}: ") && !names.startsWithGroupName(rawText)) "${names.charName}: $rawText" else rawText
                            else -> if (!rawText.startsWith("$mName: ")) "$mName: $rawText" else rawText
                        }
                        p.set("text", JsonPrimitive(fixed))
                    }
                }
                msg = msg.set("content", JsonArray(prefixed))
            }

            // 内容块 → Gemini parts
            val parts = mutableListOf<JsonObject>()
            for (partEl in msg.contentParts()) {
                val part = partEl
                when (part.str("type")) {
                    "text" -> parts += buildJsonObject { put("text", JsonPrimitive(part.str("text").orEmpty())) }
                    "tool_call_id" -> {
                        val id = part.str("tool_call_id").orEmpty()
                        val name = toolNameMap[id] ?: "unknown"
                        parts += buildJsonObject {
                            put("functionResponse", buildJsonObject {
                                put("name", JsonPrimitive(name))
                                put("response", buildJsonObject {
                                    put("name", JsonPrimitive(name))
                                    put("content", part["content"] ?: JsonPrimitive(""))
                                })
                            })
                        }
                    }
                    "tool_calls" -> (part["tool_calls"] as? JsonArray).orEmpty().forEach { tcEl ->
                        val tc = tcEl.jsonObject
                        val fn = tc.function()
                        val argsRaw = fn?.get("arguments")
                        val args = tryParseArgs(argsRaw) ?: argsRaw ?: JsonNull
                        val fnName = fn?.str("name").orEmpty()
                        parts += buildJsonObject {
                            put("functionCall", buildJsonObject {
                                put("name", JsonPrimitive(fnName))
                                put("args", args)
                            })
                            tc["signature"]?.let { put("thoughtSignature", it) }
                        }
                        tc.str("id")?.let { toolNameMap[it] = fnName }
                    }
                    "image_url", "video_url", "audio_url" ->
                        (MediaConverter.convertGeminiPart(part, model) as? JsonObject)?.let { parts += it }
                    else -> {} // 官方忽略未知块类型
                }
            }

            // Gemini 2.5/3 思考签名
            val isGemini3 = gemini3.containsMatchIn(model)
            if (isGemini3 || gemini25.containsMatchIn(model)) {
                val skipMagic = "skip_thought_signature_validator"
                val textSignature = msg.str("signature")
                val withSignatures = parts.map { p ->
                    val hasText = p["text"] is JsonPrimitive
                    val signed = if (enableThoughtSignatures && textSignature != null && hasText) {
                        p.set("thoughtSignature", JsonPrimitive(textSignature))
                    } else p
                    if (isGemini3 && signed["thoughtSignature"] == null) {
                        when {
                            signed["functionCall"] != null -> signed.set("thoughtSignature", JsonPrimitive(skipMagic))
                            imageModel.containsMatchIn(model) && role == "model" &&
                                (signed["text"] is JsonPrimitive || signed["inlineData"] != null) ->
                                signed.set("thoughtSignature", JsonPrimitive(skipMagic))
                            else -> signed
                        }
                    } else signed
                }
                parts.clear()
                parts.addAll(withSignatures)
            }

            // 同角色合并（contents 对象用 parts 键）
            val last = contents.lastOrNull()
            if (last != null && last.role() == role) {
                val lastParts = last.partsList().toMutableList()
                for (p in parts) {
                    val text = (p["text"] as? JsonPrimitive)?.content
                    if (!text.isNullOrEmpty()) {
                        val existingText = lastParts.firstOrNull { it["text"] is JsonPrimitive }
                        if (existingText != null) {
                            val idx = lastParts.indexOf(existingText)
                            lastParts[idx] = existingText.set(
                                "text",
                                JsonPrimitive(existingText.str("text").orEmpty() + "\n\n" + text),
                            )
                        } else {
                            lastParts += p
                        }
                    }
                    if (p["inlineData"] != null || p["functionCall"] != null || p["functionResponse"] != null ||
                        p["thoughtSignature"] != null || p["mediaResolution"] != null
                    ) {
                        lastParts += p
                    }
                }
                contents[contents.lastIndex] = last.set("parts", JsonArray(lastParts))
            } else {
                contents += buildJsonObject {
                    put("role", JsonPrimitive(role))
                    put("parts", JsonArray(parts))
                }
            }
        }

        return Result(contents, systemParts)
    }

    private fun JsonObject.partsList(): List<JsonObject> =
        (this["parts"] as? JsonArray)?.map { it as JsonObject } ?: emptyList()

    private fun prefixExampleNames(content: String, name: String?, names: PromptNames): String {
        var c = content
        if (names.userName.isNotEmpty() && name == "example_user") {
            if (!c.startsWith("${names.userName}: ")) c = "${names.userName}: $c"
        }
        if (names.charName.isNotEmpty() && name == "example_assistant") {
            if (!c.startsWith("${names.charName}: ") && !names.startsWithGroupName(c)) c = "${names.charName}: $c"
        }
        return c
    }

    /** 官方 tryParse：合法 JSON 解析（数字按 JS 规范化），非法返回 null（调用方回退原字符串）。 */
    private fun tryParseArgs(arguments: JsonElement?): JsonElement? {
        if (arguments == null) return null
        if (arguments !is JsonPrimitive || !arguments.isString) return arguments
        return try {
            ClaudeMessagesConverter.normalizeJsNumbers(json.parseToJsonElement(arguments.content))
        } catch (e: Exception) {
            null
        }
    }
}
