package com.emberinn.engine.prompt

/** 扩展提示（对齐官方 extensionPrompts 项）。 */
data class ExtensionPrompt(
    val identifier: String,
    val role: String = "system",
    val content: String = "",
    val position: String = "end", // start / end / in_chat
)

/**
 * 对齐官方 preparePromptsForChatCompletion 的扩展提示注入顺序：
 * summary → 作者注释 → vectors → vectorsDataBank → chromadb → personaDescription → 未知扩展（仅 BEFORE/IN_PROMPT）。
 * 非空内容才注入；标识符对齐官方（1_memory→summary 等）；unknown 扩展只接受 position=start/end。
 * 边界：injection_position/injection_depth 的 PromptManager 覆盖属于下一阶段。
 */
object ExtensionPromptInjection {

    /** 官方系统提示标识符映射（preparePromptsForChatCompletion）。 */
    private val KNOWN_IDENTIFIERS = mapOf(
        "1_memory" to "summary",
        "2_floating_prompt" to "authorsNote",
        "3_vectors" to "vectorsMemory",
        "4_vectors_data_bank" to "vectorsDataBank",
        "chromadb" to "smartContext",
    )
    private val KNOWN_KEYS = KNOWN_IDENTIFIERS.keys

    fun inject(
        systemPrompts: List<PromptMessage>,
        extensions: Map<String, ExtensionPrompt>,
        personaDescription: String = "",
        personaInPrompt: Boolean = false,
    ): List<PromptMessage> {
        val out = systemPrompts.toMutableList()

        fun push(key: String) {
            val ext = extensions[key] ?: return
            if (ext.content.isBlank()) return
            val position = ext.position.takeIf { it == "start" || it == "end" }
            out.add(
                PromptMessage(
                    role = ext.role,
                    content = ext.content,
                    identifier = KNOWN_IDENTIFIERS[key] ?: key,
                    position = position,
                    // 官方已知扩展不带 extension 标记（未知扩展才带）
                    extension = false,
                ),
            )
        }

        push("1_memory")
        push("2_floating_prompt")
        push("3_vectors")
        push("4_vectors_data_bank")
        push("chromadb")

        if (personaInPrompt && personaDescription.isNotBlank()) {
            out.add(PromptMessage("system", personaDescription, identifier = "personaDescription"))
        }

        for ((key, ext) in extensions) {
            if (key in KNOWN_KEYS) continue
            if (ext.content.isBlank()) continue
            if (ext.position != "start" && ext.position != "end") continue
            out.add(
                PromptMessage(
                    role = ext.role,
                    content = ext.content,
                    identifier = key.replace(Regex("""\W"""), "_"),
                    position = ext.position,
                    extension = true,
                ),
            )
        }

        return out
    }
}
