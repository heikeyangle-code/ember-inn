package com.emberinn.engine.prompt

/**
 * 官方 extensions/memory 纯逻辑：getLatestMemoryFromChat / getIndexOfLatestChatSummary /
 * getSummaryPromptForNow / getRawSummaryPrompt（index.js:353/374/559/756）。
 */
data class MemoryMessage(
    val name: String = "",
    val mes: String = "",
    val isSystem: Boolean = false,
    val memory: String? = null,
)

data class RawSummaryPrompt(
    val rawPrompt: String,
    val lastUsedIndex: Int,
)

object MemoryEngine {

    /** 官方 extensions/memory defaultSettings.prompt。 */
    const val DEFAULT_PROMPT =
        "Ignore previous instructions. Summarize the most important facts and events in the story so far. " +
            "If a summary already exists in your memory, use that as a base and expand with new facts. " +
            "Limit the summary to {{words}} words or less. Your response should include nothing but the summary."

    /** 官方 extensions/memory defaultSettings.template。 */
    const val DEFAULT_TEMPLATE = "[Summary: {{summary}}]"

    private val wordRegex = Regex("\\b\\w+\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    /** 官方 formatMemoryValue：模板为空时用 `Summary: value`，否则 substituteParamsExtended(template, {summary})。 */
    fun formatMemoryValue(
        value: String,
        template: String = DEFAULT_TEMPLATE,
        substitute: (String) -> String = { it },
    ): String {
        if (value.isBlank()) return ""
        val trimmed = value.trim()
        return if (template.isNotEmpty()) substitute(template) else "Summary: $trimmed"
    }

    fun getLatestMemoryFromChat(chat: List<MemoryMessage>): String {
        if (chat.isEmpty()) return ""
        for (i in chat.indices.reversed()) {
            if (i == chat.lastIndex) continue
            val memory = chat[i].memory
            if (!memory.isNullOrEmpty()) return memory
        }
        return ""
    }

    fun getIndexOfLatestChatSummary(chat: List<MemoryMessage>): Int {
        if (chat.isEmpty()) return -1
        for (i in chat.indices.reversed()) {
            if (i == chat.lastIndex) continue
            if (!chat[i].memory.isNullOrEmpty()) return i
        }
        return -1
    }

    fun getSummaryPromptForNow(
        chat: List<MemoryMessage>,
        promptInterval: Int,
        promptForceWords: Int,
        promptWords: Int,
        force: Boolean,
        prompt: String,
    ): String {
        if (promptInterval == 0 && !force) return ""
        if (chat.isEmpty()) return ""
        if (chat.size < promptInterval && !force) return ""

        var messagesSinceLastSummary = 0
        var wordsSinceLastSummary = 0
        var conditionSatisfied = false
        for (i in chat.indices.reversed()) {
            if (!chat[i].memory.isNullOrEmpty()) break
            messagesSinceLastSummary++
            wordsSinceLastSummary += countWords(chat[i].mes)
        }
        if (messagesSinceLastSummary >= promptInterval) conditionSatisfied = true
        if (promptForceWords > 0 && wordsSinceLastSummary >= promptForceWords) conditionSatisfied = true
        if (!conditionSatisfied && !force) return ""

        return if (prompt.isBlank()) "" else prompt
    }

    fun getRawSummaryPrompt(
        chat: List<MemoryMessage>,
        prompt: String,
        maxMessagesPerRequest: Int,
        promptSize: Int,
        padding: Int = 64,
        countTokens: (String, Int) -> Int = { text, pad -> text.length + pad },
    ): RawSummaryPrompt {
        val working = chat.toMutableList()
        val latestSummary = getLatestMemoryFromChat(working)
        val latestSummaryIndex = getIndexOfLatestChatSummary(working)
        if (working.isNotEmpty()) working.removeAt(working.lastIndex)
        val chatBuffer = mutableListOf<String>()
        var latestUsedMessage: MemoryMessage? = null

        for (index in latestSummaryIndex + 1 until working.size) {
            val message = working[index]
            if (message.isSystem || message.mes.isEmpty()) continue
            val entry = "${message.name}:\n${message.mes}"
            chatBuffer += entry
            val tokens = countTokens(memoryString(prompt, latestSummary, chatBuffer), padding)
            if (tokens > promptSize) {
                chatBuffer.removeAt(chatBuffer.lastIndex)
                break
            }
            latestUsedMessage = message
            if (maxMessagesPerRequest > 0 && chatBuffer.size >= maxMessagesPerRequest) break
        }

        val lastUsedIndex = if (latestUsedMessage == null) -1 else chat.indexOf(latestUsedMessage)
        return RawSummaryPrompt(
            rawPrompt = memoryString(prompt, latestSummary, chatBuffer, includeSystem = false),
            lastUsedIndex = lastUsedIndex,
        )
    }

    private fun memoryString(
        prompt: String,
        latestSummary: String,
        buffer: List<String>,
        includeSystem: Boolean = true,
    ): String {
        val delimiter = "\n\n"
        val parts = mutableListOf<String>()
        if (includeSystem) parts += prompt
        if (latestSummary.isNotEmpty()) parts += latestSummary
        parts += buffer
        return parts.joinToString(delimiter).trim()
    }

    private fun countWords(value: String): Int = wordRegex.findAll(value).count()
}
