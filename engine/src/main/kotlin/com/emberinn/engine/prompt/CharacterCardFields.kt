package com.emberinn.engine.prompt

import com.emberinn.engine.group.GroupCharacterCards
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/** 角色卡字段源（对齐官方 characters[] 的提示词相关字段）。 */
data class CharacterCardSource(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val mesExample: String = "",
    val firstMessage: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val characterVersion: String = "",
    val creatorNotes: String = "",
    val depthPrompt: String = "",
    val alternateGreetings: List<String> = emptyList(),
)

/** 对齐官方 getCharacterCardFields 的返回对象。 */
data class CharacterCardFields(
    val system: String,
    val mesExamples: String,
    val description: String,
    val personality: String,
    val persona: String,
    val scenario: String,
    val jailbreak: String,
    val version: String,
    val charDepthPrompt: String,
    val creatorNotes: String,
    val firstMessage: String,
    val alternateGreetings: List<String>,
)

/** 对齐官方 script.js getCharacterCardFields（含群聊覆盖与 chat_metadata 覆盖）。 */
object CharacterCardFieldsEngine {

    fun fields(
        character: CharacterCardSource?,
        personaDescription: String = "",
        preferCharacterPrompt: Boolean = true,
        preferCharacterJailbreak: Boolean = true,
        chatMetadataSystem: String = "",
        chatMetadataScenario: String = "",
        chatMetadataMesExample: String = "",
        groupCards: GroupCharacterCards? = null,
        /** 官方 power_user.collapse_newlines：baseChatReplace 折叠连续换行。 */
        collapseNewlines: Boolean = false,
    ): CharacterCardFields {
        fun baseChatReplace(value: String): String {
            if (value.isEmpty()) return value
            val env = MacroEnv(user = "", char = character?.name ?: "")
            var out = MacroEngine.substitute(value, env)
            if (collapseNewlines) out = PromptUtils.collapseNewlines(out)
            return out.replace("\r", "")
        }

        val persona = baseChatReplace(personaDescription.trim())
        val system = if (character == null) "" else {
            val prompt = chatMetadataSystem.ifEmpty { character.systemPrompt }
            if (preferCharacterPrompt) baseChatReplace(prompt.trim()) else ""
        }
        val jailbreak = if (character == null) "" else {
            if (preferCharacterJailbreak) baseChatReplace(character.postHistoryInstructions.trim()) else ""
        }
        val version = character?.characterVersion ?: ""
        val charDepthPrompt = if (character == null) "" else baseChatReplace(character.depthPrompt.trim())
        val creatorNotes = if (character == null) "" else baseChatReplace(character.creatorNotes.trim())

        val description = if (groupCards != null) groupCards.description
        else if (character == null) "" else baseChatReplace(character.description.trim())
        val personality = if (groupCards != null) groupCards.personality
        else if (character == null) "" else baseChatReplace(character.personality.trim())
        val scenario = if (groupCards != null) groupCards.scenario
        else if (character == null) "" else baseChatReplace(chatMetadataScenario.ifEmpty { character.scenario }.trim())
        val mesExamples = if (groupCards != null) groupCards.mesExamples
        else if (character == null) "" else baseChatReplace(chatMetadataMesExample.ifEmpty { character.mesExample }.trim())

        val firstMessage = if (character == null) "" else baseChatReplace(character.firstMessage.trim())
        val alternateGreetings = character?.alternateGreetings?.map { baseChatReplace(it.trim()) } ?: emptyList()

        return CharacterCardFields(
            system = system,
            mesExamples = mesExamples,
            description = description,
            personality = personality,
            persona = persona,
            scenario = scenario,
            jailbreak = jailbreak,
            version = version,
            charDepthPrompt = charDepthPrompt,
            creatorNotes = creatorNotes,
            firstMessage = firstMessage,
            alternateGreetings = alternateGreetings,
        )
    }
}
