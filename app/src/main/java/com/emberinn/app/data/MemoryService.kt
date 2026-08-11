package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.MemoryPrefs
import com.emberinn.app.ui.settings.MemorySettings
import com.emberinn.engine.prompt.MemoryEngine
import com.emberinn.engine.prompt.MemoryMessage
import com.emberinn.engine.worldinfo.TokenCounterFactory
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 官方 extensions/memory 的 App 接线：
 * onChatEvent 触发判定 → getSummaryPromptForNow / getRawSummaryPrompt →
 * generateQuietPrompt（DEFAULT）或 generateRaw（RAW）→ setMemoryContext 落盘 extra.memory。
 * 注入（setExtensionPrompt('1_memory')）在 ChatPromptFactory 按 memory 参数完成。
 */
class MemoryService(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val chatStore: ChatStore,
) {

    fun settings(): MemorySettings = MemoryPrefs.load(context)

    fun saveSettings(s: MemorySettings) = MemoryPrefs.save(context, s)

    fun latestMemory(history: List<JsonElement>): String =
        MemoryEngine.getLatestMemoryFromChat(history.mapNotNull { it.toMemoryMessage() })

    /** 官方 onChatEvent：满足条件时自动总结（frozen/非 main/正在生成跳过）。 */
    fun maybeAutoSummarize(sessionId: String, isStreaming: Boolean = false) {
        if (isStreaming) return
        val s = settings()
        if (s.memoryFrozen || s.source != "main") return
        val history = chatStore.messages(sessionId)
        val prompt = MemoryEngine.getSummaryPromptForNow(
            chat = history.mapNotNull { it.toMemoryMessage() },
            promptInterval = s.promptInterval,
            promptForceWords = s.promptForceWords,
            promptWords = s.promptWords,
            force = false,
            prompt = s.prompt,
        )
        if (prompt.isBlank()) return
        runSummarize(sessionId, history, prompt)
    }

    /** 官方 forceSummarizeChat：无条件总结；返回摘要文本（失败空串）。 */
    fun forceSummarize(sessionId: String, onDone: (String) -> Unit = {}) {
        val s = settings()
        if (s.source != "main") {
            onDone("")
            return
        }
        val history = chatStore.messages(sessionId)
        val prompt = MemoryEngine.getSummaryPromptForNow(
            chat = history.mapNotNull { it.toMemoryMessage() },
            promptInterval = s.promptInterval,
            promptForceWords = s.promptForceWords,
            promptWords = s.promptWords,
            force = true,
            prompt = s.prompt,
        )
        if (prompt.isBlank()) {
            onDone("")
            return
        }
        runSummarize(sessionId, history, prompt, force = true, onDone = onDone)
    }

    /** 官方 onMemoryContentInput / setMemoryContext(value, true)：手动编辑摘要并保存到倒数第二条。 */
    fun setCurrentMemory(sessionId: String, value: String) {
        val history = chatStore.messages(sessionId)
        if (history.isEmpty()) return
        chatStore.setMemoryExtra(sessionId, (history.size - 2).coerceAtLeast(0), value)
    }

    /** 官方 onMemoryRestoreClick：删除最近一条 extra.memory。 */
    fun restoreMemory(sessionId: String) {
        val history = chatStore.messages(sessionId)
        val messages = history.mapNotNull { it.toMemoryMessage() }
        val idx = MemoryEngine.getIndexOfLatestChatSummary(messages)
        if (idx >= 0) chatStore.setMemoryExtra(sessionId, idx, null)
    }

    private fun runSummarize(
        sessionId: String,
        history: List<JsonElement>,
        prompt: String,
        force: Boolean = false,
        onDone: (String) -> Unit = {},
    ) {
        val s = settings()
        val messages = history.mapNotNull { it.toMemoryMessage() }
        if (s.promptBuilder == 0) {
            // DEFAULT：官方 generateQuietPrompt（quietPrompt + 当前上下文，结果不落盘）
            chatRepository.generateQuietSummary(
                history = history,
                quietPrompt = prompt,
                responseLength = s.overrideResponseLength,
                onResult = { summary ->
                    val clean = summary.trim()
                    if (clean.isNotEmpty()) {
                        setMemoryContext(sessionId, history, clean, index = null)
                    }
                    onDone(clean)
                },
                onError = { onDone("") },
            )
            return
        }

        // RAW_BLOCKING(1) / RAW_NON_BLOCKING(2)：官方 getRawSummaryPrompt + generateRaw
        val profile = chatRepository.profile()
        val model = profile?.model.orEmpty()
        val maxContext = profile?.contextWindow?.takeIf { it > 0 } ?: 8192
        val currentMaxTokens = profile?.sampler?.maxTokens?.takeIf { it > 0 } ?: 512
        val promptSize = if (s.overrideResponseLength > 0) {
            maxContext - s.overrideResponseLength
        } else {
            maxContext - currentMaxTokens
        }.coerceAtLeast(1)
        val raw = MemoryEngine.getRawSummaryPrompt(
            chat = messages,
            prompt = prompt,
            maxMessagesPerRequest = s.maxMessagesPerRequest,
            promptSize = promptSize,
            countTokens = { text, padding ->
                runCatching { TokenCounterFactory.forModel(model).count(text) }.getOrDefault(text.length / 4) + padding
            },
        )
        if (raw.lastUsedIndex < 0) {
            onDone("")
            return
        }
        chatRepository.summarizeRaw(
            systemPrompt = prompt,
            userPrompt = raw.rawPrompt,
            responseLength = s.overrideResponseLength,
            onResult = { summary ->
                val clean = summary.trim()
                if (clean.isNotEmpty()) {
                    setMemoryContext(sessionId, history, clean, index = raw.lastUsedIndex)
                }
                onDone(clean)
            },
            onError = { onDone("") },
        )
    }

    /** 官方 setMemoryContext(value, saveToMessage, index)：index=null → 倒数第二条（<0 钳 0）。 */
    private fun setMemoryContext(sessionId: String, history: List<JsonElement>, value: String, index: Int?) {
        val idx = index ?: (history.size - 2).coerceAtLeast(0)
        chatStore.setMemoryExtra(sessionId, idx, value)
    }

    private fun JsonElement.toMemoryMessage(): MemoryMessage? {
        val obj = jsonObject
        val mes = obj["mes"]?.jsonPrimitive?.contentOrNull ?: return null
        return MemoryMessage(
            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
            mes = mes,
            isSystem = obj["is_system"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.content == "true") } == true,
            memory = obj["extra"]?.jsonObject?.get("memory")?.jsonPrimitive?.contentOrNull,
        )
    }
}
