package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/** 对齐官方 openai.js populateDialogueExamples：整组预算不足即停止，newChat 放每组前。 */
object DialogueExamplesPopulator {

    fun populate(
        chatCompletion: ChatCompletion,
        handler: TokenHandler,
        prompts: PromptItems,
        dialogues: List<List<ExampleMessage>>,
        newExampleChatPrompt: String,
        env: MacroEnv,
    ) {
        if (!prompts.has("dialogueExamples")) return
        chatCompletion.add(CompletionCollection("dialogueExamples"), prompts.index("dialogueExamples"))
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
                CompletionMessage(
                    role = "system",
                    content = example.content,
                    name = example.name,
                    identifier = "dialogueExamples $dialogueIndex-$promptIndex",
                    tokens = handler.countAsync(example.content, "examples"),
                )
            }
            if (!chatCompletion.canAffordAll(listOf(newExampleChat) + chatMessages)) break
            chatCompletion.insert(newExampleChat, "dialogueExamples")
            chatMessages.forEach { chatCompletion.insert(it, "dialogueExamples") }
        }
    }
}

data class ExampleMessage(val name: String? = null, val content: String = "")
