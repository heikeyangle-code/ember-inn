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
    ) {
        if (!prompts.has("chatHistory")) return
        chatCompletion.add(CompletionCollection("chatHistory"), prompts.index("chatHistory"))

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
        val lastChatPrompt = messages.lastOrNull()
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
        for (m in messages.asReversed()) {
            val chatMessage = CompletionMessage(
                role = m.role,
                content = m.content,
                name = m.name,
                identifier = "chatHistory",
                tokens = handler.countAsync(m.content, "conversation"),
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
    }
}
