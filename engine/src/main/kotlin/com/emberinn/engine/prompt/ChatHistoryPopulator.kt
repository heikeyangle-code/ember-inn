package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/**
 * populateChatHistory（对齐官方 openai.js 核心，不含工具/媒体/推理/continue nudge）：
 * 先放 chatHistory 集合占位，预留 newChat 预算；
 * 消息逆序逐个 insertAtStart（最终按时间正序），预算不足停止；
 * 最后 newChat 放最前、群聊 nudge 放最后，均归还预算。
 */
object ChatHistoryPopulator {

    fun populate(
        messages: List<PromptMessage>,
        chatCompletion: ChatCompletion,
        prompts: PromptItems,
        handler: TokenHandler,
        type: String,
        newChatPrompt: String,
        env: MacroEnv,
        selectedGroup: Boolean = false,
        sendIfEmpty: String = "",
        cyclePrompt: String = "",
        continueNudgePrompt: String = "[Continue your last message without repeating its original content.]",
        continuePrefill: Boolean = false,
        canUseTools: Boolean = false,
        assistantPrefill: String = "",
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
                        content = last.content,
                        name = last.name,
                        identifier = "continueMessage",
                        tokens = handler.countAsync(last.content, "conversation"),
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

        val newChatText = MacroEngine.substitute(newChatPrompt, env)
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

        // 逆序插入（insertAtStart 后最终为时间正序）
        for ((poolIndex, m) in historyMessages.asReversed().withIndex()) {
            // 对齐官方：工具调用消息 → tool_call + tool 结果消息
            val invocations = m.toolInvocations
            if (canUseTools && invocations != null && invocations.isNotEmpty()) {
                val toolCallMessage = CompletionMessage(
                    role = "assistant",
                    content = "",
                    identifier = "toolCall-chatHistory",
                    tokens = handler.countAsync(invocations.joinToString { it.name + it.parameters }, "conversation"),
                    toolCalls = invocations.map { ToolCall(id = it.id, name = it.name, arguments = it.parameters) },
                )
                val toolResultMessages = invocations.asReversed().map { inv ->
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
                } else {
                    break
                }
                continue
            }
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
            val chatMessage = CompletionMessage(
                role = m.role,
                content = content,
                name = m.name,
                identifier = "chatHistory",
                tokens = handler.countAsync(content, "conversation"),
            )
            if (chatCompletion.canAfford(chatMessage)) {
                chatCompletion.insertAtStart(chatMessage, "chatHistory")
            } else {
                break
            }
        }

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
}
