package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/**
 * populateDialogueExamples（对齐官方 openai.js）：
 * 每个对话组 = 一个 newChat 系统消息 + 若干 system 消息（带名字）；
 * 整组预算不足则停止；插入在 dialogueExamples marker 之后。
 */
object DialogueExamplesPopulator {

    fun populate(
        chatCompletion: ChatCompletion,
        handler: TokenHandler,
        dialogues: List<List<ExampleMessage>>,
        newExampleChatPrompt: String,
        env: MacroEnv,
    ) {
        // 添加 marker 占位
        chatCompletion.add(CompletionMessage("system", "", identifier = "dialogueExamples", tokens = 0))

        if (dialogues.isEmpty()) return

        val newExampleText = MacroEngine.substitute(newExampleChatPrompt, env)
        val newExampleChat = CompletionMessage(
            role = "system",
            content = newExampleText,
            identifier = "newChat",
            tokens = handler.countAsync(newExampleText, "examples"),
        )

        for ((dialogueIndex, dialogue) in dialogues.withIndex()) {
            val chatMessages = dialogue.mapIndexed { promptIndex, example ->
                val tokens = handler.countAsync(example.content, "examples")
                CompletionMessage(
                    role = "system",
                    content = example.content,
                    name = example.name,
                    identifier = "dialogueExamples $dialogueIndex-$promptIndex",
                    tokens = tokens,
                )
            }
            val group = listOf(newExampleChat) + chatMessages
            if (!chatCompletion.canAffordAll(group)) break
            chatCompletion.insertAfterIdentifier("dialogueExamples", newExampleChat)
            chatMessages.forEach { chatCompletion.insertAfterIdentifier("dialogueExamples", it) }
        }
    }
}

data class ExampleMessage(val name: String? = null, val content: String = "")
