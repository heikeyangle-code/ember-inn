package com.emberinn.engine.group

import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv

/** 群聊成员深度提示（对齐官方 getGroupDepthPrompts 返回项）。 */
data class GroupDepthPrompt(
    val text: String,
    val depth: Int,
    val role: String,
)

/** 群聊成员深度提示配置。 */
data class GroupDepthMember(
    val avatar: String,
    val name: String,
    val depthPrompt: String? = null,
    val depth: Int = 4,
    val role: String = "system",
)

/** 对齐官方 group-chats.js getGroupDepthPrompts：APPEND/APPEND_DISABLED 收集成员 depth_prompt。 */
object GroupDepthPromptsEngine {

    fun collect(
        groupId: String,
        generationMode: Int,
        members: List<String>,
        disabledMembers: List<String>,
        characterCards: List<GroupDepthMember>,
        characterId: Int = 0,
    ): List<GroupDepthPrompt> {
        if (groupId.isEmpty() || members.isEmpty()) return emptyList()
        if (generationMode == GroupGenerationMode.SWAP) return emptyList()

        val result = mutableListOf<GroupDepthPrompt>()
        for (avatar in members) {
            val index = characterCards.indexOfFirst { it.avatar == avatar }
            if (index == -1) continue
            val character = characterCards[index]
            if (avatar in disabledMembers && characterId != index) continue

            val text = character.depthPrompt?.trim()?.let {
                MacroEngine.substitute(it, MacroEnv(user = "", char = character.name)).replace("\r", "")
            } ?: ""
            if (text.isNotEmpty()) {
                result += GroupDepthPrompt(text = text, depth = character.depth, role = character.role)
            }
        }
        return result
    }
}
