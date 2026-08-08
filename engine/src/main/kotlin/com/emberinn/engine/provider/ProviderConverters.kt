package com.emberinn.engine.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 官方 prompt-converters.js 其余纯转换函数（逐字差分）：
 * Cohere/AI21/Mistral/xAI 消息转换、mergeMessages、postProcessPrompt、addAssistantPrefix、
 * convertTextCompletionPrompt、calculateClaudeBudgetTokens、calculateGoogleBudgetTokens。
 */
object ProviderConverters {

    private val random = SecureRandom()

    private fun sha512Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun userMessage(content: String): JsonObject = buildJsonObject {
        put("role", JsonPrimitive("user"))
        put("content", JsonPrimitive(content))
    }

    /** 默认媒体 token（对齐官方 crypto.randomBytes(32).base64；差分测试注入确定性 provider）。 */
    fun defaultMediaToken(index: Int): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    // ---------- Cohere ----------

    fun convertCohere(
        messages: List<JsonObject>,
        names: PromptNames,
        promptPlaceholder: String = "Let's get started.",
    ): List<JsonObject> {
        val msgs = messages.map { JsonObject(it) }.toMutableList()
        if (msgs.isEmpty()) msgs.add(0, userMessage(promptPlaceholder))
        val len = msgs.size
        var idx = 0
        while (idx < len && msgs.isNotEmpty()) {
            var msg = msgs[idx]
            if (msg["tool_calls"] is JsonArray) {
                if (idx > 0 && msgs[idx - 1].role() == "assistant") {
                    val prevContent = msgs[idx - 1]["content"] ?: JsonPrimitive("")
                    msgs.removeAt(idx - 1)
                    msg = msg.set("content", prevContent)
                    if (idx - 1 < msgs.size) msgs[idx - 1] = msg else msgs.add(msg)
                } else {
                    val toolNames = (msg["tool_calls"] as JsonArray).mapNotNull { tc ->
                        (tc as? JsonObject)?.function()?.str("name")
                    }
                    msg = msg.set("content", JsonPrimitive("I'm going to call a tool for that: ${toolNames.joinToString(", ")}"))
                    msgs[idx] = msg
                }
            }
            val name = msg.name()
            if (!name.isNullOrEmpty()) {
                var content = msg.str("content").orEmpty()
                if (msg.role() == "system" && name == "example_assistant" && names.charName.isNotEmpty() &&
                    !content.startsWith("${names.charName}: ") && !names.startsWithGroupName(content)
                ) {
                    content = "${names.charName}: $content"
                }
                if (msg.role() == "system" && name == "example_user" && names.userName.isNotEmpty() &&
                    !content.startsWith("${names.userName}: ")
                ) {
                    content = "${names.userName}: $content"
                }
                if (msg.role() != "system" && !content.startsWith("$name: ")) {
                    content = "$name: $content"
                }
                msg = msg.set("content", JsonPrimitive(content)).without("name")
                msgs[idx] = msg
            }
            idx++
        }
        return msgs
    }

    // ---------- AI21 ----------

    fun convertAI21(
        messages: List<JsonObject>?,
        names: PromptNames,
        promptPlaceholder: String = "Let's get started.",
    ): List<JsonObject> {
        if (messages == null) return emptyList()
        val msgs = messages.map { JsonObject(it) }.toMutableList()
        var systemPrompt = ""
        var i = 0
        while (i < msgs.size && msgs[i].role() == "system") {
            var content = msgs[i].str("content").orEmpty()
            if (names.userName.isNotEmpty() && msgs[i].name() == "example_user" && !content.startsWith("${names.userName}: ")) {
                content = "${names.userName}: $content"
            }
            if (names.charName.isNotEmpty() && msgs[i].name() == "example_assistant" &&
                !content.startsWith("${names.charName}: ") && !names.startsWithGroupName(content)
            ) {
                content = "${names.charName}: $content"
            }
            systemPrompt += "$content\n\n"
            i++
        }
        if (i > 0) repeat(i) { msgs.removeAt(0) }
        if (msgs.isEmpty()) msgs.add(0, userMessage(promptPlaceholder))
        if (systemPrompt.isNotEmpty()) {
            msgs.add(0, buildJsonObject {
                put("role", JsonPrimitive("system"))
                put("content", JsonPrimitive(systemPrompt.trim()))
            })
        }
        for (k in msgs.indices) {
            var msg = msgs[k]
            if (msg["name"] != null) {
                if (msg.role() != "system") {
                    val content = msg.str("content").orEmpty()
                    val name = msg.name().orEmpty()
                    if (!content.startsWith("$name: ")) msg = msg.set("content", JsonPrimitive("$name: $content"))
                }
                msg = msg.without("name")
                msgs[k] = msg
            }
        }
        val merged = mutableListOf<JsonObject>()
        for (m in msgs) {
            val last = merged.lastOrNull()
            if (last != null && last.role() == m.role()) {
                merged[merged.lastIndex] = last.set(
                    "content",
                    JsonPrimitive(last.str("content").orEmpty() + "\n\n" + m.str("content").orEmpty()),
                )
            } else {
                merged += m
            }
        }
        return merged
    }

    // ---------- Mistral ----------

    fun convertMistral(
        messages: List<JsonObject>?,
        names: PromptNames,
        enablePrefix: Boolean = false,
    ): List<JsonObject> {
        if (messages == null) return emptyList()
        val msgs = messages.map { JsonObject(it) }.toMutableList()
        val last = msgs.lastOrNull()
        if (enablePrefix && msgs.isNotEmpty() && last?.role() == "assistant") {
            msgs[msgs.lastIndex] = last.set("prefix", JsonPrimitive(true))
        }
        for (k in msgs.indices) {
            var msg = msgs[k]
            if (msg["tool_calls"] is JsonArray) {
                val calls = (msg["tool_calls"] as JsonArray).map { tc ->
                    val t = tc.jsonObject
                    t.set("id", JsonPrimitive(sha512Hex(t.str("id").orEmpty()).take(9)))
                }
                msg = msg.set("tool_calls", JsonArray(calls))
            }
            if (msg.role() == "tool" && msg.str("tool_call_id") != null) {
                msg = msg.set("tool_call_id", JsonPrimitive(sha512Hex(msg.str("tool_call_id").orEmpty()).take(9)))
            }
            if (msg.role() == "system" && msg.name() == "example_assistant") {
                val content = msg.str("content").orEmpty()
                if (names.charName.isNotEmpty() && !content.startsWith("${names.charName}: ") && !names.startsWithGroupName(content)) {
                    msg = msg.set("content", JsonPrimitive("${names.charName}: $content"))
                }
                msg = msg.without("name")
            }
            if (msg.role() == "system" && msg.name() == "example_user") {
                val content = msg.str("content").orEmpty()
                if (names.userName.isNotEmpty() && !content.startsWith("${names.userName}: ")) {
                    msg = msg.set("content", JsonPrimitive("${names.userName}: $content"))
                }
                msg = msg.without("name")
            }
            val name = msg.name()
            if (!name.isNullOrEmpty() && msg.role() != "system") {
                val content = msg.str("content").orEmpty()
                if (!content.startsWith("$name: ")) msg = msg.set("content", JsonPrimitive("$name: $content"))
                msg = msg.without("name")
            }
            msgs[k] = msg
        }
        // tool 后紧跟 user 时并入最近一条 user
        var rerun = true
        while (rerun) {
            rerun = false
            var k = 0
            while (k < msgs.size - 1) {
                if (msgs[k].role() == "tool" && msgs[k + 1].role() == "user") {
                    val lastUser = msgs.subList(0, k).indexOfLast { it.role() == "user" && it.str("content").orEmpty().isNotEmpty() }
                    if (lastUser != -1) {
                        msgs[lastUser] = msgs[lastUser].set(
                            "content",
                            JsonPrimitive(msgs[lastUser].str("content").orEmpty() + "\n\n" + msgs[k + 1].str("content").orEmpty()),
                        )
                        msgs.removeAt(k + 1)
                        rerun = true
                        break
                    }
                }
                k++
            }
        }
        for (k in 0 until msgs.size - 1) {
            if (msgs[k].role() == "assistant" && msgs[k + 1].role() == "system") {
                msgs[k + 1] = msgs[k + 1].set("role", JsonPrimitive("user"))
            }
        }
        return msgs
    }

    // ---------- xAI ----------

    fun convertXAI(messages: List<JsonObject>?, names: PromptNames): List<JsonObject> {
        if (messages == null) return emptyList()
        val msgs = messages.map { JsonObject(it) }.toMutableList()
        for (k in msgs.indices) {
            var msg = msgs[k]
            val name = msg.name()
            if (name.isNullOrEmpty() || msg.role() == "user") continue
            val content = msg.str("content").orEmpty()
            var prefix: String? = null
            if (msg.role() == "assistant" && names.charName.isNotEmpty() &&
                !content.startsWith("${names.charName}: ") && !names.startsWithGroupName(content)
            ) {
                prefix = names.charName
            } else if (msg.role() == "system" && name == "example_assistant" && names.charName.isNotEmpty() &&
                !content.startsWith("${names.charName}: ") && !names.startsWithGroupName(content)
            ) {
                prefix = names.charName
            } else if (msg.role() == "system" && name == "example_user" && names.userName.isNotEmpty() &&
                !content.startsWith("${names.userName}: ")
            ) {
                prefix = names.userName
            }
            if (prefix != null) msg = msg.set("content", JsonPrimitive("$prefix: $content"))
            msg = msg.without("name")
            msgs[k] = msg
        }
        return msgs
    }

    // ---------- mergeMessages ----------

    fun mergeMessages(
        messages: List<JsonObject>,
        names: PromptNames,
        strict: Boolean = false,
        placeholders: Boolean = false,
        single: Boolean = false,
        tools: Boolean = false,
        promptPlaceholder: String = "Let's get started.",
        mediaToken: (Int) -> String = ::defaultMediaToken,
    ): List<JsonObject> {
        val msgs = messages.map { JsonObject(it) }.toMutableList()
        val contentTokens = mutableMapOf<String, JsonObject>()
        var tokenIndex = 0

        for (k in msgs.indices) {
            var msg = msgs[k]
            var content = msg.str("content").orEmpty()
            if (msg["content"] is JsonArray) {
                val text = (msg["content"] as JsonArray).joinToString("\n\n") { part ->
                    val p = part.jsonObject
                    when (p.str("type")) {
                        "text" -> p.str("text").orEmpty()
                        "image_url", "video_url", "audio_url" -> {
                            val token = mediaToken(tokenIndex++)
                            contentTokens[token] = p
                            token
                        }
                        else -> ""
                    }
                }
                content = text
                msg = msg.set("content", JsonPrimitive(content))
            }
            if (msg.role() == "system" && msg.name() == "example_assistant" && names.charName.isNotEmpty() &&
                !content.startsWith("${names.charName}: ") && !names.startsWithGroupName(content)
            ) {
                content = "${names.charName}: $content"
                msg = msg.set("content", JsonPrimitive(content))
            }
            if (msg.role() == "system" && msg.name() == "example_user" && names.userName.isNotEmpty() &&
                !content.startsWith("${names.userName}: ")
            ) {
                content = "${names.userName}: $content"
                msg = msg.set("content", JsonPrimitive(content))
            }
            val name = msg.name()
            if (!name.isNullOrEmpty() && msg.role() != "system" && !content.startsWith("$name: ")) {
                content = "$name: $content"
                msg = msg.set("content", JsonPrimitive(content))
            }
            if (msg.role() == "tool" && !tools) msg = msg.set("role", JsonPrimitive("user"))
            if (single) {
                if (msg.role() == "assistant" && names.charName.isNotEmpty() &&
                    !content.startsWith("${names.charName}: ") && !names.startsWithGroupName(content)
                ) {
                    content = "${names.charName}: $content"
                    msg = msg.set("content", JsonPrimitive(content))
                }
                if (msg.role() == "user" && names.userName.isNotEmpty() && !content.startsWith("${names.userName}: ")) {
                    content = "${names.userName}: $content"
                    msg = msg.set("content", JsonPrimitive(content))
                }
                msg = msg.set("role", JsonPrimitive("user"))
            }
            msg = msg.without("name")
            if (!tools) msg = msg.without("tool_calls", "tool_call_id")
            msgs[k] = msg
        }

        // 相邻同角色合并
        val merged = mutableListOf<JsonObject>()
        for (m in msgs) {
            val last = merged.lastOrNull()
            val content = m.str("content").orEmpty()
            if (last != null && last.role() == m.role() && content.isNotEmpty() && m.role() != "tool") {
                merged[merged.lastIndex] = last.set(
                    "content",
                    JsonPrimitive(last.str("content").orEmpty() + "\n\n" + content),
                )
            } else {
                merged += m
            }
        }
        if (merged.isEmpty()) merged.add(0, userMessage(promptPlaceholder))

        // token 还原为媒体内容块
        if (contentTokens.isNotEmpty()) {
            for (k in merged.indices) {
                val content = merged[k].str("content").orEmpty()
                val hasValidToken = contentTokens.keys.any { content.contains(it) }
                if (hasValidToken) {
                    val rebuilt = mutableListOf<JsonObject>()
                    for (piece in content.split("\n\n")) {
                        val tokenPart = contentTokens[piece]
                        if (tokenPart != null) {
                            rebuilt += tokenPart
                        } else {
                            val lastPart = rebuilt.lastOrNull()
                            if (lastPart != null && lastPart.str("type") == "text") {
                                rebuilt[rebuilt.lastIndex] = lastPart.set(
                                    "text",
                                    JsonPrimitive(lastPart.str("text").orEmpty() + "\n\n" + piece),
                                )
                            } else {
                                rebuilt += buildJsonObject {
                                    put("type", JsonPrimitive("text"))
                                    put("text", JsonPrimitive(piece))
                                }
                            }
                        }
                    }
                    merged[k] = merged[k].set("content", JsonArray(rebuilt))
                }
            }
        }

        if (strict) {
            for (k in merged.indices) {
                if (k > 0 && merged[k].role() == "system") {
                    merged[k] = merged[k].set("role", JsonPrimitive("user"))
                }
            }
            if (merged.isNotEmpty() && placeholders) {
                if (merged[0].role() == "system" && (merged.size == 1 || merged[1].role() != "user")) {
                    merged.add(1, userMessage(promptPlaceholder))
                } else if (merged[0].role() != "system" && merged[0].role() != "user") {
                    merged.add(0, userMessage(promptPlaceholder))
                }
            }
            return mergeMessages(merged, names, strict = false, placeholders = placeholders, single = false, tools = tools, promptPlaceholder = promptPlaceholder, mediaToken = mediaToken)
        }

        return merged
    }

    // ---------- postProcessPrompt / addAssistantPrefix / text completion ----------

    fun postProcessPrompt(
        messages: List<JsonObject>,
        type: String,
        names: PromptNames,
        promptPlaceholder: String = "Let's get started.",
        mediaToken: (Int) -> String = ::defaultMediaToken,
    ): List<JsonObject> = when (type) {
        "", "claude", "merge" -> mergeMessages(messages, names, strict = false, tools = false, promptPlaceholder = promptPlaceholder, mediaToken = mediaToken)
        "merge_tools" -> mergeMessages(messages, names, strict = false, tools = true, promptPlaceholder = promptPlaceholder, mediaToken = mediaToken)
        "semi" -> mergeMessages(messages, names, strict = true, tools = false, promptPlaceholder = promptPlaceholder, mediaToken = mediaToken)
        "semi_tools" -> mergeMessages(messages, names, strict = true, tools = true, promptPlaceholder = promptPlaceholder, mediaToken = mediaToken)
        "strict" -> mergeMessages(messages, names, strict = true, placeholders = true, tools = false, promptPlaceholder = promptPlaceholder, mediaToken = mediaToken)
        "strict_tools" -> mergeMessages(messages, names, strict = true, placeholders = true, tools = true, promptPlaceholder = promptPlaceholder, mediaToken = mediaToken)
        "single" -> mergeMessages(messages, names, strict = true, single = true, tools = false, promptPlaceholder = promptPlaceholder, mediaToken = mediaToken)
        else -> messages
    }

    fun addAssistantPrefix(prompt: List<JsonObject>, tools: List<JsonObject>, property: String): List<JsonObject> {
        if (prompt.isEmpty()) return prompt
        val hasAnyTools = tools.isNotEmpty() || prompt.any { it.role() == "tool" }
        if (!hasAnyTools && prompt.last().role() == "assistant") {
            val last = prompt.last()
            return prompt.dropLast(1) + last.set(property, JsonPrimitive(true))
        }
        return prompt
    }

    fun convertTextCompletionPrompt(messages: JsonElement): String {
        if (messages is JsonPrimitive && messages.isString) return messages.content
        val list = (messages as? JsonArray) ?: return ""
        val strings = list.map { m ->
            val obj = m.jsonObject
            val role = obj.role()
            val name = obj.name()
            val content = obj.str("content").orEmpty()
            if (role == "system" && name == null) "System: $content"
            else if (role == "system") "$name: $content"
            else "$role: $content"
        }
        return strings.joinToString("\n") + "\nassistant:"
    }

    // ---------- OpenRouter 专项 ----------

    fun cachingAtDepthForOpenRouterClaude(messages: MutableList<JsonObject>, cachingAtDepth: Int, ttl: String) {
        var passedThePrefill = false
        var depth = 0
        var previousRoleName = ""
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            if (!passedThePrefill && msg.role() == "assistant") continue
            passedThePrefill = true
            if (msg.role() == "system") continue
            if (msg.role() != previousRoleName) {
                if (depth == cachingAtDepth || depth == cachingAtDepth + 2) {
                    val content = msg["content"]
                    if (content is JsonPrimitive) {
                        messages[i] = msg.set("content", JsonArray(listOf(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", content)
                            put("cache_control", buildJsonObject {
                                put("type", JsonPrimitive("ephemeral"))
                                put("ttl", JsonPrimitive(ttl))
                            })
                        })))
                    } else if (content is JsonArray && content.isNotEmpty()) {
                        val parts = content.toMutableList()
                        val last = parts.last().jsonObject.toMutableMap()
                        last["cache_control"] = buildJsonObject {
                            put("type", JsonPrimitive("ephemeral"))
                            put("ttl", JsonPrimitive(ttl))
                        }
                        parts[parts.lastIndex] = JsonObject(last)
                        messages[i] = msg.set("content", JsonArray(parts))
                    }
                }
                if (depth == cachingAtDepth + 2) break
                depth += 1
                previousRoleName = msg.role()
            }
        }
    }

    fun cachingSystemPromptForOpenRouter(messages: MutableList<JsonObject>, ttl: String?) {
        if (messages.isEmpty()) return
        val idx = messages.indexOfFirst { it.role() == "system" }
        if (idx < 0) return
        val sys = messages[idx]
        if (sys["cache_control"] is JsonObject) return
        val cacheControl = if (!ttl.isNullOrEmpty()) {
            buildJsonObject {
                put("type", JsonPrimitive("ephemeral"))
                put("ttl", JsonPrimitive(ttl))
            }
        } else {
            buildJsonObject { put("type", JsonPrimitive("ephemeral")) }
        }
        val content = sys["content"]
        if (content is JsonArray) {
            if (content.any { (it as? JsonObject)?.get("cache_control") != null }) return
            for (i in content.indices.reversed()) {
                val part = content[i].jsonObject
                if (part.str("type") == "text") {
                    val updated = content.toMutableList()
                    updated[i] = JsonObject(part.toMutableMap().apply { put("cache_control", cacheControl) })
                    messages[idx] = sys.set("content", JsonArray(updated))
                    return
                }
            }
        } else if (content is JsonPrimitive) {
            messages[idx] = sys.set("content", JsonArray(listOf(buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", content)
                put("cache_control", cacheControl)
            })))
        }
    }

    fun embedOpenRouterMedia(messages: MutableList<JsonObject>, audio: Boolean = true, video: Boolean = true) {
        for (k in messages.indices) {
            val content = messages[k]["content"] as? JsonArray ?: continue
            val updated = content.map { partEl ->
                val part = partEl.jsonObject
                var p = part
                if (audio && p.str("type") == "audio_url" &&
                    p["audio_url"]?.jsonObject?.str("url")?.startsWith("data:") == true
                ) {
                    val url = p["audio_url"]!!.jsonObject.str("url").orEmpty()
                    val header = url.substringBefore(",")
                    val base64Data = url.substringAfter(",", "")
                    val mimeType = Regex("""data:([^;]+)""").find(header)?.groupValues?.get(1) ?: "audio/mpeg"
                    val format = if (mimeType == "audio/wav") "wav" else "mp3"
                    p = buildJsonObject {
                        put("type", JsonPrimitive("input_audio"))
                        put("input_audio", buildJsonObject {
                            put("format", JsonPrimitive(format))
                            put("data", JsonPrimitive(base64Data))
                        })
                    }
                }
                p
            }
            messages[k] = messages[k].set("content", JsonArray(updated))
        }
    }

    fun addReasoningContentToToolCalls(messages: MutableList<JsonObject>) {
        for (k in messages.indices) {
            val msg = messages[k]
            if (msg["tool_calls"] !is JsonArray || msg["reasoning_content"] != null) continue
            messages[k] = msg.set("reasoning_content", JsonPrimitive(""))
        }
    }

    fun addOpenRouterSignatures(messages: MutableList<JsonObject>, model: String, enableThoughtSignatures: Boolean = true) {
        val format = when {
            Regex("google/gemini").containsMatchIn(model) -> "google-gemini-v1"
            Regex("anthropic/claude").containsMatchIn(model) -> "anthropic-claude-v1"
            Regex("openai/gpt").containsMatchIn(model) -> "openai-responses-v1"
            Regex("x-ai/grok").containsMatchIn(model) -> "xai-responses-v1"
            else -> "unknown"
        }
        for (k in messages.indices) {
            var msg = messages[k]
            val details = mutableListOf<JsonObject>()
            fun addDetail(data: String?, id: String?) {
                if (data.isNullOrEmpty()) return
                details += buildJsonObject {
                    put("index", JsonPrimitive(details.size))
                    put("id", JsonPrimitive(id ?: "signature-${details.size}"))
                    put("type", JsonPrimitive("reasoning.encrypted"))
                    put("data", JsonPrimitive(data))
                    put("format", JsonPrimitive(format))
                }
            }
            val signature = msg.str("signature")
            if (signature != null) {
                if (enableThoughtSignatures) addDetail(signature, null)
                msg = msg.without("signature")
            }
            if (msg["tool_calls"] is JsonArray) {
                val calls = (msg["tool_calls"] as JsonArray).map { tcEl ->
                    val tc = tcEl.jsonObject
                    val tcSig = tc.str("signature")
                    if (tcSig != null) {
                        addDetail(tcSig, tc.str("id"))
                        tc.without("signature")
                    } else tc
                }
                msg = msg.set("tool_calls", JsonArray(calls))
            }
            if (details.isNotEmpty()) {
                msg = msg.set("reasoning_details", JsonArray(details))
            }
            messages[k] = msg
        }
    }

    // ---------- 预算计算 ----------

    fun calculateClaudeBudgetTokens(maxTokens: Int, reasoningEffort: String, stream: Boolean, isAdaptiveModel: Boolean): Any? {
        if (isAdaptiveModel) {
            return when (reasoningEffort) {
                "auto" -> null
                "min", "low" -> "low"
                "medium" -> "medium"
                "high" -> "high"
                "max" -> "max"
                else -> null
            }
        }
        val budget = when (reasoningEffort) {
            "auto" -> return null
            "min" -> 1024
            "low" -> Math.floor(maxTokens * 0.1).toInt()
            "medium" -> Math.floor(maxTokens * 0.25).toInt()
            "high" -> Math.floor(maxTokens * 0.5).toInt()
            "max" -> Math.floor(maxTokens * 0.95).toInt()
            else -> 0
        }
        var result = Math.max(budget, 1024)
        if (!stream) result = Math.min(result, 21333)
        return result
    }

    fun calculateGoogleBudgetTokens(maxTokens: Int, reasoningEffort: String, model: String): Any? {
        fun flash(): Any {
            val budget = when (reasoningEffort) {
                "auto" -> return -1
                "min" -> 0
                "low" -> Math.floor(maxTokens * 0.1).toInt()
                "medium" -> Math.floor(maxTokens * 0.25).toInt()
                "high" -> Math.floor(maxTokens * 0.5).toInt()
                "max" -> maxTokens
                else -> 0
            }
            return Math.min(budget, 24576)
        }
        fun flashLite(): Any {
            val budget = when (reasoningEffort) {
                "auto" -> return -1
                "min" -> 0
                "low" -> Math.floor(maxTokens * 0.1).toInt()
                "medium" -> Math.floor(maxTokens * 0.25).toInt()
                "high" -> Math.floor(maxTokens * 0.5).toInt()
                "max" -> maxTokens
                else -> 0
            }
            return Math.max(Math.min(budget, 24576), 512)
        }
        fun pro(): Any {
            val budget = when (reasoningEffort) {
                "auto" -> return -1
                "min" -> 128
                "low" -> Math.floor(maxTokens * 0.1).toInt()
                "medium" -> Math.floor(maxTokens * 0.25).toInt()
                "high" -> Math.floor(maxTokens * 0.5).toInt()
                "max" -> maxTokens
                else -> 0
            }
            return Math.max(Math.min(budget, 32768), 128)
        }
        fun gemini3Flash(): Any? = when (reasoningEffort) {
            "auto" -> null
            "min" -> "minimal"
            "low" -> "low"
            "medium" -> "medium"
            "high" -> "high"
            "max" -> "high"
            else -> null
        }
        fun gemini3Pro(): Any? = when (reasoningEffort) {
            "auto" -> null
            "min", "low", "medium" -> "low"
            "high", "max" -> "high"
            else -> null
        }

        if (Regex("gemini-3[.\\d]*-pro").containsMatchIn(model)) return gemini3Pro()
        if (Regex("gemini-3[.\\d]*-flash").containsMatchIn(model)) return gemini3Flash()
        if (model.contains("flash-lite")) return flashLite()
        if (model.contains("flash")) return flash()
        if (model.contains("pro")) return pro()
        return null
    }
}
