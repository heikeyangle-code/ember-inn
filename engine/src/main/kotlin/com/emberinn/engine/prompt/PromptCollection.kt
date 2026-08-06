package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/** 提示项（对齐官方 PromptManager Prompt 核心字段）。 */
data class PromptItem(
    val identifier: String,
    val name: String,
    val content: String = "",
    val role: String = "system",
    val systemPrompt: Boolean = true,
    val marker: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * PromptManager 核心：默认提示集合 + 默认顺序 + preparePrompt + 系统提示合并。
 * 边界：用户自定义集合/顺序持久化、injection 位置/深度、预算分配属于后续阶段。
 */
object PromptCollection {

    /** 对齐官方 chatCompletionDefaultPrompts.prompts。 */
    val DEFAULT_PROMPTS: List<PromptItem> = listOf(
        PromptItem("main", "Main Prompt",
            content = "Write {{char}}'s next reply in a fictional chat between {{charIfNotGroup}} and {{user}}."),
        PromptItem("nsfw", "Auxiliary Prompt", content = ""),
        PromptItem("dialogueExamples", "Chat Examples", marker = true),
        PromptItem("jailbreak", "Post-History Instructions", content = ""),
        PromptItem("chatHistory", "Chat History", marker = true),
        PromptItem("worldInfoAfter", "World Info (after)", marker = true),
        PromptItem("worldInfoBefore", "World Info (before)", marker = true),
        PromptItem("enhanceDefinitions", "Enhance Definitions",
            content = "If you have more knowledge of {{char}}, add to the character's lore and personality to enhance them but keep the Character Sheet's definitions absolute.",
            enabled = false),
        PromptItem("charDescription", "Char Description", marker = true),
        PromptItem("charPersonality", "Char Personality", marker = true),
        PromptItem("scenario", "Scenario", marker = true),
        PromptItem("personaDescription", "Persona Description", marker = true),
    )

    /** 对齐官方 promptManagerDefaultPromptOrder。 */
    val DEFAULT_ORDER: List<String> = listOf(
        "main", "worldInfoBefore", "personaDescription", "charDescription",
        "charPersonality", "scenario", "enhanceDefinitions", "nsfw",
        "worldInfoAfter", "dialogueExamples", "chatHistory", "jailbreak",
    )

    /** preparePrompt：内容做宏替换（含 original/groupOverride 的基础版）。 */
    fun prepare(prompt: PromptItem, env: MacroEnv): PromptItem {
        val content = if (prompt.marker) prompt.content else MacroEngine.substitute(prompt.content, env)
        return prompt.copy(content = content)
    }

    /** 按默认顺序返回已 prepare 的集合（只含 enabled）。 */
    fun getCollection(env: MacroEnv): List<PromptItem> {
        val byId = DEFAULT_PROMPTS.associateBy { it.identifier }
        return DEFAULT_ORDER.mapNotNull { byId[it] }
            .filter { it.enabled }
            .map { prepare(it, env) }
    }

    /** 对齐 preparePromptsForChatCompletion：把系统提示合并进同名 marker。 */
    fun mergeSystemPrompts(collection: List<PromptItem>, systemPrompts: List<PromptMessage>): List<PromptItem> {
        val map = systemPrompts.filter { it.content.isNotBlank() }.associateBy { it.identifier }
        return collection.map { item ->
            val sp = map[item.identifier]
            if (sp != null && item.marker) {
                item.copy(content = sp.content, role = sp.role)
            } else item
        }
    }
}
