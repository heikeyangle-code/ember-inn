package com.emberinn.engine.prompt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 对齐官方 populateChatCompletion 的操作计划（用于差分与调度）。 */
object ChatCompletionPipelinePlan {

    private val FIXED_ORDER = listOf(
        "worldInfoBefore", "main", "worldInfoAfter", "charDescription",
        "charPersonality", "scenario", "personaDescription",
    )
    private val KNOWN_RELATIVE = listOf("summary", "authorsNote", "vectorsMemory", "vectorsDataBank", "smartContext")

    fun plan(
        prompts: PromptItems,
        messages: List<PromptMessage>,
        type: String,
        bias: String,
        quietPrompt: String,
        pinExamples: Boolean = false,
        toolBudgetReserve: Int = 0,
        toolCallsEnabled: Boolean = false,
        disabledPromptIds: Set<String> = emptySet(),
        continuePrefill: Boolean = false,
        chatCompletionSource: String = "openai",
        assistantPrefill: String = "",
        selectedGroup: Boolean = false,
        inChatExtensions: List<ExtensionPrompt> = emptyList(),
    ): List<JsonElement> {
        val ops = mutableListOf<JsonElement>()

        fun messageJson(p: PromptItem): JsonObject = buildJsonObject {
            put("role", JsonPrimitive(p.role))
            put("content", JsonPrimitive(p.content))
            put("name", if (p.name.isBlank()) JsonNull else JsonPrimitive(p.name))
            put("identifier", JsonPrimitive(p.identifier))
        }

        fun addToChatCompletion(source: String, target: String? = null) {
            if (!prompts.has(source)) return
            if (disabledPromptIds.contains(source) && source != "main") return
            val prompt = prompts.get(source) ?: return
            if (prompt.injectionPosition == PromptInjection.ABSOLUTE) return
            val index = if (target != null) prompts.index(target) else prompts.index(source)
            ops += buildJsonObject {
                put("op", JsonPrimitive("add"))
                put("collection", JsonPrimitive(source))
                put("index", index.takeIf { it >= 0 }?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }

        ops += buildJsonObject { put("op", JsonPrimitive("reserve")); put("amount", JsonPrimitive(3)) }

        FIXED_ORDER.forEach { addToChatCompletion(it) }

        ops += buildJsonObject {
            put("op", JsonPrimitive("overridden"))
            put("ids", JsonArray(prompts.overriddenPrompts.map { JsonPrimitive(it) }))
        }

        val controlMessages = mutableListOf<JsonObject>()
        if (type == "impersonate") {
            prompts.get("impersonate")?.let { controlMessages += messageJson(it) }
        }
        prompts.get("quietPrompt")?.let { if (it.content.isNotEmpty()) controlMessages += messageJson(it) }

        fun controlObject(): JsonObject = buildJsonObject {
            put("identifier", JsonPrimitive("controlPrompts"))
            put("collection", JsonArray(controlMessages))
        }
        ops += buildJsonObject { put("op", JsonPrimitive("reserve")); put("amount", controlObject()) }

        val systemPrompts = listOf("nsfw", "jailbreak")
        val userRelative = prompts.collection
            .filter { !it.systemPrompt && it.injectionPosition != PromptInjection.ABSOLUTE }
            .map { it.identifier }
        for (id in systemPrompts + userRelative) addToChatCompletion(id)

        if (prompts.has("enhanceDefinitions")) addToChatCompletion("enhanceDefinitions")
        if (bias.isNotBlank() && prompts.has("bias")) addToChatCompletion("bias")

        fun injectToMain(p: PromptItem) {
            if (!prompts.has("main")) return
            val msg = messageJson(p)
            val position = p.position
            if (position == "start" || position == "end") {
                ops += buildJsonObject {
                    put("op", JsonPrimitive("insert"))
                    put("target", JsonPrimitive("main"))
                    put("position", JsonPrimitive(position))
                    put("message", msg)
                }
            }
        }
        KNOWN_RELATIVE.forEach { id ->
            prompts.get(id)?.takeIf { it.position != null }?.let(::injectToMain)
        }
        prompts.collection.filter { it.extension && it.position != null }.forEach(::injectToMain)

        if (toolCallsEnabled && toolBudgetReserve > 0) {
            ops += buildJsonObject { put("op", JsonPrimitive("reserve")); put("amount", JsonPrimitive(toolBudgetReserve)) }
        }

        var historyMessages = messages.toMutableList()
        if (type == "continue" && continuePrefill && historyMessages.isNotEmpty()) {
            val chatMessage = historyMessages.removeAt(0)
            val isAssistantRole = chatMessage.role == "assistant"
            val supportsAssistantPrefill = chatCompletionSource == "claude"
            val prefill = if (isAssistantRole && supportsAssistantPrefill) assistantPrefill else ""
            val content = listOf(prefill, chatMessage.content).filter { it.isNotEmpty() }.joinToString("\n\n")
            val continueMessage = buildJsonObject {
                put("role", JsonPrimitive(chatMessage.role))
                put("content", JsonPrimitive(content))
                put("identifier", JsonPrimitive("continuePrefill"))
            }
            controlMessages += continueMessage
            ops += buildJsonObject { put("op", JsonPrimitive("reserve")); put("amount", continueMessage) }
        }

        if (pinExamples) {
            ops += buildJsonObject { put("op", JsonPrimitive("populate")); put("name", JsonPrimitive("dialogueExamples")) }
            ops += buildJsonObject { put("op", JsonPrimitive("populate")); put("name", JsonPrimitive("chatHistory")) }
        } else {
            ops += buildJsonObject { put("op", JsonPrimitive("populate")); put("name", JsonPrimitive("chatHistory")) }
            ops += buildJsonObject { put("op", JsonPrimitive("populate")); put("name", JsonPrimitive("dialogueExamples")) }
        }

        ops += buildJsonObject { put("op", JsonPrimitive("free")); put("amount", controlObject()) }
        if (controlMessages.isNotEmpty()) {
            ops += buildJsonObject {
                put("op", JsonPrimitive("add"))
                put("collection", JsonPrimitive("controlPrompts"))
                put("index", JsonNull)
            }
        }

        return ops
    }
}
