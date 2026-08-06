package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/**
 * populateChatHistory 核心（对齐官方 openai.js）：
 * 先预留 newChat 消息预算，再逆序插入聊天消息，预算不足即停止。
 * 边界：群聊提示/继续提示/图像/工具/推理签名属于后续阶段。
 */
object ChatHistoryPopulator {

    fun populate(
        messages: List<PromptMessage>,
        chatCompletion: ChatCompletion,
        handler: TokenHandler,
        newChatPrompt: String,
        env: MacroEnv,
    ): List<PromptMessage> {
        val newChatText = MacroEngine.substitute(newChatPrompt, env)
        val newChatMessage = CompletionMessage("system", newChatText, identifier = "newMainChat",
            tokens = handler.countAsync(newChatText, "prompt"))
        chatCompletion.reserveBudget(newChatMessage)

        val added = mutableListOf<PromptMessage>()
        for (m in messages.asReversed()) {
            val content = m.content
            val chatMessage = CompletionMessage(
                role = m.role, content = content, name = m.name,
                identifier = "chatHistory",
                tokens = handler.countAsync(content, "conversation"),
            )
            if (!chatCompletion.canAfford(chatMessage)) break
            chatCompletion.add(chatMessage)
            added.add(m)
        }
        return added
    }
}
