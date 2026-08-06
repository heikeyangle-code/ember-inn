package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 官方 PromptManager INJECTION_POSITION / 默认值。 */
object PromptInjection {
    const val RELATIVE = 0
    const val ABSOLUTE = 1
    const val DEFAULT_DEPTH = 4
    const val DEFAULT_ORDER = 100
}

/** 提示顺序条目（对齐官方 promptOrderEntry：identifier/enabled/injection_*）。 */
@Serializable
data class PromptOrderEntry(
    val identifier: String,
    val enabled: Boolean = true,
    @SerialName("injection_position")
    val injectionPosition: Int? = null,
    @SerialName("injection_depth")
    val injectionDepth: Int? = null,
    @SerialName("injection_order")
    val injectionOrder: Int? = null,
    val role: String? = null,
)

/** 每角色的提示顺序持久化结构（对齐 serviceSettings.prompt_order）。 */
@Serializable
data class PromptOrderList(
    @SerialName("character_id")
    val characterId: String? = null,
    val order: List<PromptOrderEntry> = emptyList(),
)

/** 对齐官方 PromptCollection：有序提示列表 + 覆盖记录。 */
class PromptItems(initial: List<PromptItem> = emptyList()) {
    val collection = initial.toMutableList()
    val overriddenPrompts = mutableListOf<String>()

    fun add(prompt: PromptItem) { collection += prompt }

    fun set(prompt: PromptItem, position: Int) {
        collection[position] = prompt
    }

    fun get(identifier: String): PromptItem? =
        collection.firstOrNull { it.identifier == identifier }

    fun index(identifier: String): Int =
        collection.indexOfFirst { it.identifier == identifier }

    fun has(identifier: String): Boolean = index(identifier) != -1

    fun override(prompt: PromptItem, position: Int) {
        set(prompt, position)
        overriddenPrompts += prompt.identifier
    }
}

/**
 * PromptManager 纯逻辑核心（对齐官方 PromptManager.getPromptCollection/preparePrompt/shouldTrigger）。
 * 边界：角色专属提示（appendPrompt）、import/export UI、渲染属于 app 层。
 */
object PromptManagerCore {

    /** 对齐官方 promptManagerDefaultPromptOrder（enabled 含 enhanceDefinitions=false）。 */
    val DEFAULT_ORDER_ENTRIES: List<PromptOrderEntry> = listOf(
        PromptOrderEntry("main"),
        PromptOrderEntry("worldInfoBefore"),
        PromptOrderEntry("personaDescription"),
        PromptOrderEntry("charDescription"),
        PromptOrderEntry("charPersonality"),
        PromptOrderEntry("scenario"),
        PromptOrderEntry("enhanceDefinitions", enabled = false),
        PromptOrderEntry("nsfw"),
        PromptOrderEntry("worldInfoAfter"),
        PromptOrderEntry("dialogueExamples"),
        PromptOrderEntry("chatHistory"),
        PromptOrderEntry("jailbreak"),
    )

    fun shouldTrigger(prompt: PromptItem, generationType: String): Boolean {
        if (prompt.injectionTrigger.isEmpty()) return true
        return generationType in prompt.injectionTrigger
    }

    /** 对齐 preparePrompt：内容做宏替换；marker 不替换；original/groupOverride 走 MacroEnv。 */
    fun prepare(
        prompt: PromptItem,
        env: MacroEnv,
        original: String? = null,
        groupOverride: String? = null,
    ): PromptItem {
        if (prompt.marker) return prompt
        val prepared = env.copy(
            original = original ?: env.original,
            group = groupOverride ?: env.group,
            groupNotMuted = groupOverride ?: env.groupNotMuted,
        )
        return prompt.copy(content = MacroEngine.substitute(prompt.content, prepared))
    }

    /**
     * 对齐 getPromptCollection：按用户顺序（缺省用官方默认）收集已启用提示；
     * main 被禁用时补一个空 content 的占位（相对插入依赖它）。
     */
    fun getCollection(
        userOrder: List<PromptOrderEntry>,
        userPrompts: List<PromptItem>,
        generationType: String,
        env: MacroEnv,
    ): PromptItems {
        val order = userOrder.ifEmpty { DEFAULT_ORDER_ENTRIES }
        val collection = PromptItems()
        val defaults = PromptCollection.DEFAULT_PROMPTS.associateBy { it.identifier }

        for (entry in order) {
            val prompt = userPrompts.firstOrNull { it.identifier == entry.identifier }
                ?: defaults[entry.identifier]
                ?: continue

            val allowed = entry.enabled && shouldTrigger(prompt, generationType)
            if (allowed) {
                collection.add(prepare(prompt, env))
            } else if (entry.identifier == "main") {
                collection.add(prepare(prompt.copy(content = ""), env))
            }
        }
        return collection
    }

    /**
     * 对齐 preparePromptsForChatCompletion 的合并：把系统提示内容写入同名 marker，
     * 并应用 PromptManager 的 role / injection_position / injection_depth / injection_order 覆盖。
     */
    fun mergeSystemPrompts(
        collection: PromptItems,
        systemPrompts: List<PromptMessage>,
    ): PromptItems {
        val out = PromptItems(collection.collection.toList())
        for (prompt in systemPrompts) {
            val item = out.get(prompt.identifier)
            val merged = PromptItem(
                identifier = prompt.identifier,
                name = item?.name ?: prompt.identifier,
                content = prompt.content,
                role = item?.role ?: prompt.role,
                systemPrompt = item?.systemPrompt ?: true,
                marker = item?.marker ?: false,
                enabled = item?.enabled ?: true,
                injectionPosition = item?.injectionPosition,
                injectionDepth = item?.injectionDepth,
                injectionOrder = item?.injectionOrder,
                position = item?.position ?: prompt.position,
                extension = item?.extension ?: prompt.extension,
            )
            val idx = out.index(prompt.identifier)
            if (idx != -1) out.set(merged, idx) else out.add(merged)
        }
        return out
    }
}
