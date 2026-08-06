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

    /** 对齐 preparePromptsForChatCompletion 的 systemPrompts 基础列表（只保留非空内容）。 */
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
    ): List<PromptMessage> {
        val env = MacroEnv(user = user, char = char)
        val personalityText = if (charPersonality.isNotEmpty() && DEFAULT_PERSONALITY_FORMAT.isNotEmpty()) {
            MacroEngine.substitute(DEFAULT_PERSONALITY_FORMAT, env)
        } else charPersonality
        val scenarioText = if (scenario.isNotEmpty() && DEFAULT_SCENARIO_FORMAT.isNotEmpty()) {
            MacroEngine.substitute(DEFAULT_SCENARIO_FORMAT, env)
        } else scenario
        val groupNudgeText = if (groupNudge.isNotEmpty()) MacroEngine.substitute(groupNudge, env) else ""

        val prompts = listOf(
            PromptMessage("system", formatWorldInfo(worldInfoBefore), identifier = "worldInfoBefore"),
            PromptMessage("system", formatWorldInfo(worldInfoAfter), identifier = "worldInfoAfter"),
            PromptMessage("system", charDescription, identifier = "charDescription"),
            PromptMessage("system", personalityText, identifier = "charPersonality"),
            PromptMessage("system", scenarioText, identifier = "scenario"),
            PromptMessage("system", impersonationPrompt, identifier = "impersonate"),
            PromptMessage("system", quietPrompt, identifier = "quietPrompt"),
            PromptMessage("system", groupNudgeText, identifier = "groupNudge"),
        )
        val withPersona = if (persona.isNotEmpty()) {
            prompts + PromptMessage("system", persona, identifier = "personaDescription")
        } else prompts
        return withPersona.filter { it.content.isNotBlank() }
    }

    /** 对齐 setOpenAIMessages：chat → OpenAI 消息（names_behavior 前缀）。 */
    fun toOpenAiMessages(
        chat: List<ChatMessage>,
        namesBehavior: Int = NAMES_DEFAULT,
        selectedGroup: Boolean = false,
        user: String = "",
    ): List<PromptMessage> {
        val messages = mutableListOf<PromptMessage>()
        for (m in chat) {
            if (m.isSystem) continue
            val role = if (m.isUser) "user" else "assistant"
            val name = m.name ?: (if (m.isUser) user else "")
            var content = m.mes.replace("\r", "")
            val prefix = when (namesBehavior) {
                NAMES_NONE, NAMES_COMPLETION -> false
                NAMES_CONTENT -> true
                else -> selectedGroup && name != user
            }
            if (prefix && name.isNotEmpty()) content = "$name: $content"
            messages += PromptMessage(role, content, name.ifEmpty { null })
        }
        return messages
    }

    const val NAMES_NONE = 0
    const val NAMES_DEFAULT = 1
    const val NAMES_CONTENT = 2
    const val NAMES_COMPLETION = 3
}
