package com.emberinn.engine.prompt

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
import com.emberinn.engine.macros.ChatMessage

/**
 * 提示词组装核心（对齐官方 renderStoryString / preparePromptsForChatCompletion / setOpenAIMessages）。
 * 边界：PromptManager 排序/预算、populateChatHistory、扩展提示词注入属于下一阶段。
 */
object PromptAssembler {

    /** 官方默认 Main Prompt 模板（power-user.js defaultStoryString）。 */
    val DEFAULT_STORY_STRING: String =
        "{{#if system}}{{system}}\n{{/if}}{{#if description}}{{description}}\n{{/if}}" +
            "{{#if personality}}{{char}}'s personality: {{personality}}\n{{/if}}" +
            "{{#if scenario}}Scenario: {{scenario}}\n{{/if}}{{#if persona}}{{persona}}\n{{/if}}"

    private const val DEFAULT_WI_FORMAT = "{0}"
    private const val DEFAULT_PERSONALITY_FORMAT = "{{personality}}"
    private const val DEFAULT_SCENARIO_FORMAT = "{{scenario}}"
    private const val DEFAULT_GROUP_NUDGE = "[Write the next reply only as {{char}}.]"

    fun renderStoryString(
        params: StoryParams,
        template: String = DEFAULT_STORY_STRING,
    ): String {
        val map = mapOf(
            "description" to params.description,
            "personality" to params.personality,
            "persona" to params.persona,
            "scenario" to params.scenario,
            "system" to params.system,
            "char" to params.char,
            "user" to params.user,
            "wiBefore" to params.wiBefore,
            "wiAfter" to params.wiAfter,
            "loreBefore" to params.wiBefore,
            "loreAfter" to params.wiAfter,
            "mesExamples" to params.mesExamples,
            "mesExamplesRaw" to params.mesExamplesRaw,
            "anchorBefore" to params.anchorBefore,
            "anchorAfter" to params.anchorAfter,
        )
        var output = StoryStringRenderer.render(template, map)
        output = MacroEngine.substitute(output, MacroEnv(user = params.user, char = params.char))
        output = output.replace(Regex("""^\n+"""), "")
        if (output.isNotEmpty() && !output.endsWith("\n")) output += "\n"
        return output
    }

    /** formatWorldInfo：stringFormat(format, value)，默认 {0}。 */
    fun formatWorldInfo(value: String, format: String = DEFAULT_WI_FORMAT): String {
        if (value.isEmpty()) return ""
        if (format.isBlank()) return value
        return format.replace(Regex("""\{(\d+)\}""")) { m ->
            if (m.groupValues[1] == "0") value else m.value
        }
    }

    /** 对齐 preparePromptsForChatCompletion 的 systemPrompts 基础列表（官方空内容也保留）。 */
    fun buildSystemPrompts(
        charDescription: String,
        charPersonality: String,
        scenario: String,
        worldInfoBefore: String,
        worldInfoAfter: String,
        char: String,
        user: String,
        persona: String = "",
        quietPrompt: String = "",
        groupNudge: String = DEFAULT_GROUP_NUDGE,
        impersonationPrompt: String = "",
        bias: String = "",
        personalityFormat: String = DEFAULT_PERSONALITY_FORMAT,
        scenarioFormat: String = DEFAULT_SCENARIO_FORMAT,
        wiFormat: String = DEFAULT_WI_FORMAT,
    ): List<PromptMessage> {
        val env = MacroEnv(user = user, char = char)
        // 官方 substituteParams 的 env 来自活动角色卡：{{personality}}/{{scenario}} 解析到传入文本
        val cardEnv = env.copy(
            character = env.character.copy(personality = charPersonality, scenario = scenario),
        )
        val personalityText = if (charPersonality.isNotEmpty() && personalityFormat.isNotEmpty()) {
            MacroEngine.substitute(personalityFormat, cardEnv)
        } else charPersonality
        val scenarioText = if (scenario.isNotEmpty() && scenarioFormat.isNotEmpty()) {
            MacroEngine.substitute(scenarioFormat, cardEnv)
        } else scenario
        val groupNudgeText = if (groupNudge.isNotEmpty()) MacroEngine.substitute(groupNudge, env) else ""
        val impersonationText = if (impersonationPrompt.isNotEmpty()) MacroEngine.substitute(impersonationPrompt, env) else ""

        val prompts = listOf(
            PromptMessage("system", formatWorldInfo(worldInfoBefore, wiFormat), identifier = "worldInfoBefore"),
            PromptMessage("system", formatWorldInfo(worldInfoAfter, wiFormat), identifier = "worldInfoAfter"),
            PromptMessage("system", charDescription, identifier = "charDescription"),
            PromptMessage("system", personalityText, identifier = "charPersonality"),
            PromptMessage("system", scenarioText, identifier = "scenario"),
            PromptMessage("system", impersonationText, identifier = "impersonate"),
            PromptMessage("system", quietPrompt, identifier = "quietPrompt"),
            PromptMessage("system", groupNudgeText, identifier = "groupNudge"),
            PromptMessage("assistant", bias, identifier = "bias"),
        )
        return if (persona.isNotEmpty()) {
            prompts + PromptMessage("system", persona, identifier = "personaDescription")
        } else prompts
    }

    /**
     * 对齐 openai.js preparePromptsForChatCompletion 纯逻辑：
     * 系统提示 + 扩展注入 → PromptManager 集合合并 → main/jailbreak override。
     * 边界：PromptManager 的 injection_position/depth 真正插入聊天属于后续阶段。
     */
    fun preparePromptsForChatCompletion(
        scenario: String,
        charPersonality: String,
        name2: String,
        worldInfoBefore: String,
        worldInfoAfter: String,
        charDescription: String,
        quietPrompt: String,
        bias: String,
        extensionPrompts: Map<String, ExtensionPrompt>,
        systemPromptOverride: String,
        jailbreakPromptOverride: String,
        type: String,
        userOrder: List<PromptOrderEntry>,
        userPrompts: List<PromptItem>,
        env: MacroEnv,
        personaDescription: String = "",
        personaInPrompt: Boolean = false,
        impersonationPrompt: String = "",
        personalityFormat: String = DEFAULT_PERSONALITY_FORMAT,
        scenarioFormat: String = DEFAULT_SCENARIO_FORMAT,
        groupNudge: String = DEFAULT_GROUP_NUDGE,
        wiFormat: String = DEFAULT_WI_FORMAT,
    ): PromptItems {
        val base = buildSystemPrompts(
            charDescription = charDescription,
            charPersonality = charPersonality,
            scenario = scenario,
            worldInfoBefore = worldInfoBefore,
            worldInfoAfter = worldInfoAfter,
            char = name2,
            user = env.user,
            persona = "",
            quietPrompt = quietPrompt,
            groupNudge = groupNudge,
            impersonationPrompt = impersonationPrompt,
            bias = bias,
            personalityFormat = personalityFormat,
            scenarioFormat = scenarioFormat,
            wiFormat = wiFormat,
        )
        val systemPrompts = ExtensionPromptInjection.inject(
            base,
            extensionPrompts,
            personaDescription = personaDescription,
            personaInPrompt = personaInPrompt,
        )

        // 官方默认顺序由调用方注入（getPromptOrderForCharacter 无存储时返回 []，此处补默认）
        val order = userOrder.ifEmpty { PromptManagerCore.DEFAULT_ORDER_ENTRIES }
        val collection = PromptManagerCore.getCollection(order, userPrompts, type, env)
        val merged = PromptManagerCore.mergeSystemPrompts(collection, systemPrompts, env)

        val mainEnabled = order.firstOrNull { it.identifier == "main" }?.enabled ?: true
        val jailbreakEnabled = order.firstOrNull { it.identifier == "jailbreak" }?.enabled ?: true

        applyPromptOverride(merged, "main", systemPromptOverride, mainEnabled, env)
        applyPromptOverride(merged, "jailbreak", jailbreakPromptOverride, jailbreakEnabled, env)
        return merged
    }

    /** 对齐官方 override：原始内容作为 {{original}}，替换后标记 overriddenPrompts。 */
    private fun applyPromptOverride(
        collection: PromptItems,
        identifier: String,
        override: String,
        enabled: Boolean,
        env: MacroEnv,
    ) {
        if (override.isEmpty() || !enabled) return
        val item = collection.get(identifier) ?: return
        if (item.forbidOverrides) return
        val original = item.content
        val replacement = PromptManagerCore.prepare(item.copy(content = override), env, original = original)
        val idx = collection.index(identifier)
        if (idx != -1) collection.override(replacement, idx)
    }

    /** 对齐 setOpenAIMessages：chat → OpenAI 消息（names_behavior 前缀）。 */
    fun toOpenAiMessages(
        chat: List<ChatMessage>,
        namesBehavior: Int = NAMES_DEFAULT,
        selectedGroup: Boolean = false,
        user: String = "",
        name2: String = "",
        currentApi: String = "",
        currentModel: String = "",
    ): List<PromptMessage> {
        val messages = mutableListOf<PromptMessage>()
        for (m in chat) {
            // 官方 setOpenAIMessages：narrator（extra.type === 'narrator'）→ system；其余按 is_user 判定
            val role = when {
                m.narrator -> "system"
                m.isUser -> "user"
                else -> "assistant"
            }
            val name = m.name ?: (if (m.isUser) user else "")
            var content = m.mes.replace("\r", "")
            if (m.titles.isNotEmpty()) {
                content = appendMessageTitles(content, m.titles)
            }
            // 官方 names_behavior：DEFAULT 组内 force_avatar 或群聊非用户；CONTENT 非旁白；NONE/COMPLETION 不加
            val prefix = when (namesBehavior) {
                NAMES_NONE, NAMES_COMPLETION -> false
                NAMES_CONTENT -> !m.narrator
                else -> (selectedGroup && name != user) || (m.forceAvatar && name != user && !m.narrator)
            }
            if (prefix && name.isNotEmpty()) content = "$name: $content"
            // 官方 isSameModel：同 API/模型才携带 reasoning/signature；工具调用里的推理/签名同规则剥离
            val isSameModel = currentApi.isNotEmpty() && m.api == currentApi && m.model == currentModel
            val isOtherGroupMember = selectedGroup && name != name2
            val signature = if (isSameModel && !isOtherGroupMember) m.reasoningSignature else null
            val reasoning = if (isSameModel && !isOtherGroupMember) (m.reasoning ?: "") else null
            val invocations = m.toolInvocations?.map { inv ->
                if (isSameModel) inv else inv.copy(reasoning = null, signature = null)
            }
            messages += PromptMessage(
                role = role,
                content = content,
                name = name.ifEmpty { null },
                toolInvocations = invocations,
                signature = signature,
                reasoning = reasoning,
            )
        }
        // 官方 setOpenAIMessages：messages[i] 从末尾反向填充，最终数组“新的在前”
        return messages.asReversed()
    }

    /** 官方 script.js coreChat.map 的标题追加逻辑。 */
    fun appendMessageTitles(mes: String, titles: List<String>): String =
        if (titles.isEmpty()) mes else "$mes\n\n" + titles.joinToString("\n\n")

    // 官方 character_names_behavior：NONE=-1 / DEFAULT=0 / COMPLETION=1 / CONTENT=2
    const val NAMES_NONE = -1
    const val NAMES_DEFAULT = 0
    const val NAMES_COMPLETION = 1
    const val NAMES_CONTENT = 2
}
