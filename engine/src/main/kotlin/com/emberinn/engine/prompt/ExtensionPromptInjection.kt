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
 * 非空内容才注入；unknown 扩展只接受 position=start/end。
 */
object ExtensionPromptInjection {

    fun inject(
        systemPrompts: List<PromptMessage>,
        extensions: Map<String, ExtensionPrompt>,
        personaDescription: String = "",
        personaInPrompt: Boolean = false,
    ): List<PromptMessage> {
        val out = systemPrompts.toMutableList()
        val known = setOf("1_memory", "2_floating_prompt", "3_vectors", "4_vectors_data_bank", "chromadb")

        fun push(id: String) {
            val ext = extensions[id] ?: return
            if (ext.content.isBlank()) return
            out.add(PromptMessage(ext.role, ext.content, identifier = id))
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
            if (key in known) continue
            if (ext.content.isBlank()) continue
            if (ext.position != "start" && ext.position != "end") continue
            out.add(PromptMessage(ext.role, ext.content, identifier = key.replace(Regex("""\W"""), "_")))
        }

        return out
    }
}
