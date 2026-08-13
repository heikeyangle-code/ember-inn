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

    fun shouldTrigger(prompt: PromptItem?, generationType: String): Boolean {
        // 官方：injection_trigger 非数组（含 prompt 不存在时 undefined）→ 恒 true
        if (prompt == null || prompt.injectionTrigger.isEmpty()) return true
        return generationType in prompt.injectionTrigger
    }

    /** 对齐官方 preparePrompt：无条件宏替换（marker 也一样）；original/groupOverride 走 MacroEnv。 */
    fun prepare(
        prompt: PromptItem,
        env: MacroEnv,
        original: String? = null,
        groupOverride: String? = null,
    ): PromptItem {
        val prepared = env.copy(
            original = original ?: env.original,
            group = groupOverride ?: env.group,
            groupNotMuted = groupOverride ?: env.groupNotMuted,
        )
        // 官方 preparePrompt 走 new Prompt(prompt)：marker/enabled 不复制（语义上等同 false/true）
        return prompt.copy(
            content = MacroEngine.substitute(prompt.content, prepared),
            marker = false,
            enabled = true,
            // 官方 new Prompt() 构造函数：injection_order 缺省 100
            injectionOrder = prompt.injectionOrder ?: PromptInjection.DEFAULT_ORDER,
        )
    }

    /**
     * 对齐 getPromptCollection：按用户顺序（缺省用官方默认）收集已启用提示；
     * main 被禁用时补一个空 content 的占位（相对插入依赖它）。
     */
    /** 对齐官方 getPromptOrderForCharacter：无角色→[]；按 String(character_id)===String(id) 匹配，无存储→[]。 */
    fun resolveOrder(characterId: String?, lists: List<PromptOrderList>): List<PromptOrderEntry> {
        if (characterId == null) return emptyList()
        return lists.firstOrNull { it.characterId?.toString() == characterId }?.order ?: emptyList()
    }

    fun getCollection(
        userOrder: List<PromptOrderEntry>,
        userPrompts: List<PromptItem>,
        generationType: String,
        env: MacroEnv,
    ): PromptItems {
        // 对齐官方 getPromptCollection：generationType 归一（空→normal、小写、去空白）；
        // order 直接用传入值（官方 getPromptOrderForCharacter 无存储时返回 []，默认顺序由调用方接线）
        val normalizedType = generationType.ifBlank { "normal" }.lowercase().trim()
        val order = userOrder
        val collection = PromptItems()
        val defaults = PromptCollection.DEFAULT_PROMPTS.associateBy { it.identifier }

        for (entry in order) {
            val prompt = userPrompts.firstOrNull { it.identifier == entry.identifier }
                ?: defaults[entry.identifier]
                ?: continue

            val allowed = entry.enabled && shouldTrigger(prompt, normalizedType)
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
    /**
     * 对齐 preparePromptsForChatCompletion 的合并：
     * 官方 new Prompt(systemPrompt) 只继承系统提示自身字段，仅 role/injection_position/
     * injection_depth/injection_order 来自 PromptManager 集合项；随后 preparePrompt 宏替换。
     * system_prompt 官方为 undefined（语义等同 system，Kotlin 用 true 表示）。
     */
    fun mergeSystemPrompts(
        collection: PromptItems,
        systemPrompts: List<PromptMessage>,
        env: MacroEnv = MacroEnv(user = "", char = ""),
    ): PromptItems {
        val out = PromptItems(collection.collection.toList())
        for (prompt in systemPrompts) {
            val id = prompt.identifier ?: continue
            val item = out.get(id)
            val merged = PromptItem(
                identifier = id,
                // 官方 new Prompt(prompt) 不复制 name（undefined）；系统提示条目不携带 name
                name = prompt.name ?: "",
                content = prompt.content,
                role = item?.role ?: prompt.role,
                systemPrompt = true,
                marker = false,
                enabled = true,
                injectionPosition = item?.injectionPosition,
                injectionDepth = item?.injectionDepth,
                injectionOrder = item?.injectionOrder ?: PromptInjection.DEFAULT_ORDER,
                injectionTrigger = emptyList(),
                forbidOverrides = false,
                position = prompt.position,
                extension = prompt.extension,
            )
            val prepared = prepare(merged, env)
            val idx = out.index(id)
            if (idx != -1) out.set(prepared, idx) else out.add(prepared)
        }
        return out
    }
}
