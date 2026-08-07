package com.emberinn.engine.group

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/** 群聊角色卡字段（对齐官方 getGroupCharacterCards 的返回对象）。 */
data class GroupCharacterCards(
    val description: String,
    val personality: String,
    val scenario: String,
    val mesExamples: String,
)

/** 群聊成员角色卡（对齐官方 characters 的合并所需字段）。 */
data class GroupCardMember(
    val avatar: String,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val mesExample: String = "",
)

/**
 * APPEND/APPEND_DISABLED 群聊角色卡合并（对齐官方 group-chats.js getGroupCharacterCardsLazy）。
 * 官方按需惰性求值；本引擎直接返回全部字段，结果一致。
 */
object GroupCharacterCardsEngine {

    fun cards(
        groupId: String,
        generationMode: Int,
        members: List<String>,
        disabledMembers: List<String>,
        joinPrefix: String,
        joinSuffix: String,
        characterCards: List<GroupCardMember>,
        characterId: Int = 0,
        scenarioOverride: String = "",
        mesExamplesOverride: String = "",
    ): GroupCharacterCards? {
        if (generationMode == GroupGenerationMode.SWAP || members.isEmpty()) return null

        fun baseChatReplace(value: String, characterName: String): String {
            if (value.isEmpty()) return value
            val env = MacroEnv(user = "", char = characterName)
            return MacroEngine.substitute(value, env).replace("\r", "")
        }

        fun customTransform(value: String, fieldName: String, characterName: String, trim: Boolean): String {
            if (value.isEmpty()) return ""
            var v = value.replace(Regex("<FIELDNAME>", RegexOption.IGNORE_CASE), fieldName)
            if (trim) v = v.trim()
            return baseChatReplace(v, characterName)
        }

        fun replaceAndPrepareForJoin(
            value: String,
            characterName: String,
            fieldName: String,
            preprocess: ((String) -> String)? = null,
        ): String {
            var v = value.trim()
            if (v.isEmpty()) return ""
            if (preprocess != null) v = preprocess(v)
            val prefix = customTransform(joinPrefix, fieldName, characterName, false)
            val suffix = customTransform(joinSuffix, fieldName, characterName, false)
            v = customTransform(v, fieldName, characterName, true)
            return prefix + v + suffix
        }

        fun collectField(fieldName: String, getter: (GroupCardMember) -> String, preprocess: ((String) -> String)? = null): String {
            val values = mutableListOf<String>()
            for (avatar in members) {
                val index = characterCards.indexOfFirst { it.avatar == avatar }
                if (index == -1) continue
                val character = characterCards[index]
                if (avatar in disabledMembers && characterId != index && generationMode != GroupGenerationMode.APPEND_DISABLED) continue
                values += replaceAndPrepareForJoin(getter(character), character.name, fieldName, preprocess)
            }
            return values.filter { it.isNotEmpty() }.joinToString("\n")
        }

        val scenario = baseChatReplace(scenarioOverride.trim(), "") ?: ""
        val mesExamples = baseChatReplace(mesExamplesOverride.trim(), "") ?: ""

        return GroupCharacterCards(
            description = collectField("Description", getter = { it.description }),
            personality = collectField("Personality", getter = { it.personality }),
            scenario = scenario.ifEmpty { collectField("Scenario", getter = { it.scenario }) },
            mesExamples = mesExamples.ifEmpty {
                collectField("Example Messages", { it.mesExample }) { x ->
                    if (!x.startsWith("<START>")) "<START>\n$x" else x
                }
            },
        )
    }
}
