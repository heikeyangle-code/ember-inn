package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.media.MediaAttachment
import com.emberinn.engine.media.MediaDisplay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * populateChatHistory（对齐官方 openai.js 核心，不含工具/媒体/推理/continue nudge）：
 * 先放 chatHistory 集合占位，预留 newChat 预算；
 * 消息逆序逐个 insertAtStart（最终按时间正序），预算不足停止；
 * 最后 newChat 放最前、群聊 nudge 放最后，均归还预算。
 */
object ChatHistoryPopulator {

    /** 对齐官方 tool_reasoning_modes。 */
    const val TOOL_REASONING_DISABLED = "disabled"
    const val TOOL_REASONING_SINCE_LAST_USER = "since_last_user"
    const val TOOL_REASONING_ACTIVE_CHAIN = "active_chain"

    fun populate(
        messages: List<PromptMessage>,
        chatCompletion: ChatCompletion,
        prompts: PromptItems,
        handler: TokenHandler,
        type: String,
        newChatPrompt: String,
        newGroupChatPrompt: String = newChatPrompt,
        env: MacroEnv,
        selectedGroup: Boolean = false,
        sendIfEmpty: String = "",
        cyclePrompt: String = "",
        continueNudgePrompt: String = "[Continue your last message without repeating its original content.]",
        continuePrefill: Boolean = false,
        canUseTools: Boolean = false,
        assistantPrefill: String = "",
        namesBehavior: Int = PromptAssembler.NAMES_DEFAULT,
        includeSignature: Boolean = false,
        toolReasoningMode: String = TOOL_REASONING_DISABLED,
        imageInlining: Boolean = false,
        videoInlining: Boolean = false,
        audioInlining: Boolean = false,
        mediaTokenCosts: Map<String, Int> = emptyMap(),
    ) {
        if (!prompts.has("chatHistory")) return
        chatCompletion.add(CompletionCollection("chatHistory"), prompts.index("chatHistory"))

        // 官方 continue nudge：把最后一条非注入消息移到末尾集合，并追加 nudge 提示
        val historyMessages = messages.toMutableList()
        var continueCollection: CompletionCollection? = null
        if (type == "continue" && cyclePrompt.isNotEmpty() && !continuePrefill) {
            val collection = CompletionCollection("continueNudge")
            val lastIndex = historyMessages.indexOfLast { !it.injected }
            if (lastIndex >= 0) {
                val last = historyMessages.removeAt(lastIndex)
                collection.add(
                    CompletionMessage(
                        role = last.role,
                        content = MacroEngine.substitute(last.content, env),
                        name = if (namesBehavior == PromptAssembler.NAMES_COMPLETION && last.name != null) {
                            PromptNameSanitizer.sanitizeName(last.name)
                        } else {
                            null
                        },
                        identifier = last.identifier ?: "",
                        tokens = handler.countAsync(MacroEngine.substitute(last.content, env), "conversation"),
                    ),
                )
            }
            val nudgeText = MacroEngine.substitute(
                continueNudgePrompt.replace("{{lastChatMessage}}", cyclePrompt.trim()),
                env,
            )
            collection.add(
                CompletionMessage(
                    role = "system",
                    content = nudgeText,
                    identifier = "continueNudge",
                    tokens = handler.countAsync(nudgeText, "nudge"),
                ),
            )
            chatCompletion.reserveBudget(collection.getTokens())
            continueCollection = collection
        }

        // 官方 openai.js:884：selected_group ? new_group_chat_prompt : new_chat_prompt
        val newChatText = MacroEngine.substitute(if (selectedGroup) newGroupChatPrompt else newChatPrompt, env)
        val newChatMessage = CompletionMessage(
            role = "system",
            content = newChatText,
            identifier = "newMainChat",
            tokens = handler.countAsync(newChatText, "prompt"),
        )
        chatCompletion.reserveBudget(newChatMessage)

        // 群聊 nudge（impersonate 除外）
        var groupNudgeMessage: CompletionMessage? = null
        if (selectedGroup && prompts.has("groupNudge") && type != "impersonate") {
            val content = prompts.get("groupNudge")?.content ?: ""
            if (content.isNotEmpty()) {
                groupNudgeMessage = CompletionMessage(
                    role = "system",
                    content = content,
                    identifier = "groupNudge",
                    tokens = handler.countAsync(content, "nudge"),
                )
                chatCompletion.reserveBudget(groupNudgeMessage)
            }
        }

        // 最后一条是 assistant 且配置了空输入时，补一个空用户消息
        val lastChatPrompt = historyMessages.lastOrNull()
        if (lastChatPrompt?.role == "assistant" && sendIfEmpty.isNotEmpty()) {
            val message = CompletionMessage(
                role = "user",
                content = sendIfEmpty,
                identifier = "emptyUserMessageReplacement",
                tokens = handler.countAsync(sendIfEmpty, "conversation"),
            )
            if (chatCompletion.canAfford(message)) {
                chatCompletion.insert(message, "chatHistory")
            }
        }

        // 官方 lastUserIdx：原始消息里最后一个 user 的位置（工具推理链 eligibility 用）
        val lastUserIdx = historyMessages.indexOfLast { it.role == "user" }

        // 逆序插入（insertAtStart 后最终为时间正序）。
        // 普通消息先收集成批，最后一次性 addAll(0)，避免逐条 unshift 的 O(n²)。
        val historyBatch = mutableListOf<CompletionMessage>()
        var simulatedBudget = chatCompletion.tokenBudget
        for ((poolIndex, m) in historyMessages.asReversed().withIndex()) {
            val chatIdentifier = "chatHistory-${historyMessages.size - poolIndex}"

            // 对齐官方 preparePrompt：每条历史消息 content 过宏替换（{{char}}/{{user}} 等）
            val substitutedContent = MacroEngine.substitute(m.content, env)
            val content = if (
                poolIndex == 0 && type == "continue" && continuePrefill && m.role != "user"
            ) {
                // 官方：continue_prefill 时给最后一条 assistant 加预填
                listOf(assistantPrefill, substitutedContent).filter { it.isNotEmpty() }.joinToString("\n\n")
            } else {
                substitutedContent
            }
            // 官方 populateChatHistory：fromPromptAsync 丢弃 prompt.name，
            // 仅 COMPLETION 模式 setName（isValidName 通过则原样，否则 sanitizeName），其余模式不带 name 字段
            val effectiveName = if (namesBehavior == PromptAssembler.NAMES_COMPLETION && m.name != null) {
                if (PromptNameSanitizer.isValidName(m.name)) m.name else PromptNameSanitizer.sanitizeName(m.name)
            } else {
                null
            }

            // 官方 inlineMediaAttachment：LIST 逐个 / GALLERY 按 mediaIndex；
            // 非 data: URL 在 fixture 语义下视为抓取失败跳过（官方 fetch 分支）；类型缺省按 IMAGE
            var mediaTokens = 0
            val inlinedMedia = mutableListOf<MediaAttachment>()
            val rawMedia = m.media.orEmpty()
            if (rawMedia.isNotEmpty()) {
                val chosen = if (m.mediaDisplay == MediaDisplay.GALLERY) {
                    listOfNotNull(rawMedia.getOrNull(m.mediaIndex ?: 0))
                } else {
                    rawMedia
                }
                for (media in chosen) {
                    if (media.url.isBlank() || !media.url.startsWith("data:")) continue
                    val mediaType = media.type.ifBlank { "image" }
                    val canInline = when (mediaType) {
                        "image" -> imageInlining
                        "video" -> videoInlining
                        "audio" -> audioInlining
                        else -> false
                    }
                    if (canInline) {
                        inlinedMedia += MediaAttachment(type = mediaType, url = media.url, title = media.title)
                        mediaTokens += mediaTokenCosts[media.url] ?: defaultMediaCost(mediaType)
                    }
                }
            }

            // 对齐官方：工具调用消息 → tool_call + tool 结果消息（推理链模式/签名按官方 openai.js）
            // 工具消息与普通消息交错时不能整体批量插队，先把手头普通批次按时间正序落盘
            if (historyBatch.isNotEmpty()) {
                chatCompletion.insertAllAtStart(historyBatch.asReversed(), "chatHistory")
                historyBatch.clear()
                simulatedBudget = chatCompletion.tokenBudget
            }
            val invocations = m.toolInvocations
            if (canUseTools && invocations != null && invocations.isNotEmpty()) {
                val promptIdx = historyMessages.size - 1 - poolIndex
                val reasoningEligible = toolReasoningMode != TOOL_REASONING_DISABLED && promptIdx > lastUserIdx
                var previousAssistantReasoning = ""
                if (reasoningEligible) {
                    when (toolReasoningMode) {
                        TOOL_REASONING_ACTIVE_CHAIN -> {
                            var idx = promptIdx - 1
                            while (idx > lastUserIdx) {
                                val candidate = historyMessages[idx]
                                if (candidate.role == "tool") { idx--; continue }
                                if (candidate.role == "assistant" && !candidate.toolInvocations.isNullOrEmpty()) { idx--; continue }
                                val hasAssistantText = candidate.role == "assistant" &&
                                    candidate.toolInvocations.isNullOrEmpty() &&
                                    candidate.content.trim().isNotEmpty()
                                if (hasAssistantText) previousAssistantReasoning = candidate.reasoning ?: ""
                                break
                            }
                        }
                        TOOL_REASONING_SINCE_LAST_USER -> {
                            var idx = promptIdx - 1
                            while (idx > lastUserIdx) {
                                val candidate = historyMessages[idx]
                                val hasAssistantText = candidate.role == "assistant" &&
                                    candidate.toolInvocations.isNullOrEmpty() &&
                                    candidate.content.trim().isNotEmpty()
                                if (!hasAssistantText) { idx--; continue }
                                val candidateReasoning = candidate.reasoning ?: ""
                                if (candidateReasoning.isNotEmpty()) {
                                    previousAssistantReasoning = candidateReasoning
                                    break
                                }
                                idx--
                            }
                        }
                    }
                }
                val processed = invocations.map { inv ->
                    val reasoning = when {
                        !reasoningEligible -> null
                        previousAssistantReasoning.isNotEmpty() && inv.reasoning.isNullOrEmpty() -> previousAssistantReasoning
                        else -> inv.reasoning
                    }
                    inv.copy(reasoning = reasoning)
                }
                val includeToolReasoning = toolReasoningMode != TOOL_REASONING_DISABLED
                val toolCallMessage = CompletionMessage(
                    role = m.role,
                    content = "",
                    identifier = "toolCall-$chatIdentifier",
                    tokens = toolCallTokens(m.role, processed, includeSignature, includeToolReasoning, handler),
                    toolCalls = processed.map {
                        ToolCall(id = it.id, name = it.name, arguments = it.parameters, signature = if (includeSignature) it.signature else null)
                    },
                    reasoning = if (includeToolReasoning) processed.firstOrNull { !it.reasoning.isNullOrEmpty() }?.reasoning else null,
                )
                val toolResultMessages = processed.asReversed().map { inv ->
                    CompletionMessage(
                        role = "tool",
                        content = inv.result.ifEmpty { "[No content]" },
                        identifier = inv.id,
                        toolCallId = inv.id,
                        tokens = handler.countAsync(inv.result, "conversation"),
                    )
                }
                if (chatCompletion.canAffordAll(listOf(toolCallMessage) + toolResultMessages)) {
                    toolResultMessages.forEach { chatCompletion.insertAtStart(it, "chatHistory") }
                    chatCompletion.insertAtStart(toolCallMessage, "chatHistory")
                    simulatedBudget = chatCompletion.tokenBudget
                } else {
                    break
                }
                continue
            }

            val chatMessage = CompletionMessage(
                role = m.role,
                content = content,
                name = effectiveName,
                // 对齐官方 populateChatHistory：identifier = chatHistory-{正序位置}
                identifier = chatIdentifier,
                tokens = handler.countAsync(content, "conversation") + mediaTokens,
                media = inlinedMedia.takeIf { it.isNotEmpty() },
                // 官方：仅 includeSignature 且消息带 signature 时透传（Gemini thoughtSignature）
                signature = if (includeSignature) m.signature?.takeIf { it.isNotEmpty() } else null,
            )
            if (chatMessage.tokens <= simulatedBudget) {
                historyBatch += chatMessage
                simulatedBudget -= chatMessage.tokens
            } else {
                break
            }
        }

        chatCompletion.insertAllAtStart(historyBatch.asReversed(), "chatHistory")
        chatCompletion.freeBudget(newChatMessage)
        chatCompletion.insertAtStart(newChatMessage, "chatHistory")

        if (selectedGroup && groupNudgeMessage != null) {
            chatCompletion.freeBudget(groupNudgeMessage)
            chatCompletion.insertAtEnd(groupNudgeMessage, "chatHistory")
        }

        // continue nudge 集合追加到末尾（官方 add(collection, -1)）
        if (continueCollection != null) {
            chatCompletion.freeBudget(continueCollection!!.getTokens())
            chatCompletion.add(continueCollection!!)
        }
    }

    /** 官方 Message.addImage/addVideo/addAudio 失败回退的成本（MediaTokenCost 差分覆盖精确分支）。 */
    private fun defaultMediaCost(type: String): Int = when (type) {
        "image" -> 85
        "video" -> 263 * 40
        "audio" -> 32 * 300
        else -> 0
    }

    /** 官方 setToolCalls：tokens = countAsync(JSON.stringify({role, tool_calls, reasoning?}))。 */
    private fun toolCallTokens(
        role: String,
        invocations: List<ToolInvocation>,
        includeSignature: Boolean,
        includeReasoning: Boolean,
        handler: TokenHandler,
    ): Int {
        val calls = invocations.map { inv ->
            buildJsonObject {
                put("id", inv.id)
                put("type", "function")
                put("function", buildJsonObject {
                    put("arguments", inv.parameters)
                    put("name", inv.name)
                })
                if (includeSignature && !inv.signature.isNullOrEmpty()) put("signature", inv.signature)
            }
        }
        val fallbackReasoning = if (includeReasoning) {
            invocations.firstOrNull { !it.reasoning.isNullOrEmpty() }?.reasoning
        } else null
        val obj = buildJsonObject {
            put("role", role)
            put("tool_calls", JsonPrimitive(TOOL_JSON.encodeToString(JsonElement.serializer(), JsonArray(calls))))
            if (!fallbackReasoning.isNullOrEmpty()) put("reasoning", fallbackReasoning)
        }
        return handler.countAsync(TOOL_JSON.encodeToString(JsonObject.serializer(), obj), "conversation")
    }

    private val TOOL_JSON = Json { encodeDefaults = false }
}
