package com.emberinn.engine.persona

/** 人设-角色/群组连接（对齐 personas.js PersonaConnection）。 */
data class PersonaConnection(val type: String, val id: String)

/** 人设描述（对齐 getOrCreatePersonaDescriptor）。 */
data class PersonaDescriptor(
    val description: String = "",
    val position: Int = 0,
    val depth: Int = 2,
    val role: Int = 0,
    val lorebook: String = "",
    val connections: List<PersonaConnection> = emptyList(),
    val title: String = "",
)

/** 人设状态（对齐 getPersonaStates）。 */
data class PersonaState(
    val avatarId: String,
    val isDefault: Boolean,
    val lockedChat: Boolean,
    val lockedCharacter: Boolean,
)

/** 临时锁信息（对齐 getPersonaTemporaryLockInfo）。 */
data class PersonaLockInfo(
    val isTemporary: Boolean,
    val hasDifferentChatLock: Boolean,
    val hasDifferentDefaultLock: Boolean,
    val info: String,
)

/** 按聊天解析人设的结果（对齐 loadPersonaForCurrentChat 纯逻辑部分）。 */
data class PersonaResolveResult(
    val chatPersona: String?,
    val connectType: String?,
    val unlockChat: Boolean,
    val clearDefault: Boolean,
    val willSwitch: Boolean,
    val autoLock: Boolean,
)

/** 人设引擎纯逻辑（对齐 personas.js states/temporary/connections/resolve）。 */
object PersonaEngine {

    fun states(
        avatarId: String,
        defaultPersona: String?,
        chatPersona: String?,
        connections: List<PersonaConnection>,
        selectedGroup: String?,
        charAvatar: String?,
    ): PersonaState {
        val isDefault = defaultPersona == avatarId
        val hasChatLock = chatPersona == avatarId
        val hasCharLock = connections.any { c ->
            (selectedGroup == null && c.type == "character" && c.id == charAvatar) ||
                (selectedGroup != null && c.type == "group" && c.id == selectedGroup)
        }
        return PersonaState(avatarId, isDefault, hasChatLock, hasCharLock)
    }

    fun temporaryLockInfo(
        userAvatar: String,
        chatPersona: String?,
        defaultPersona: String?,
        personas: Map<String, String>,
    ): PersonaLockInfo {
        val hasDifferentChatLock = !chatPersona.isNullOrEmpty() && chatPersona != userAvatar
        val hasDifferentDefaultLock = !defaultPersona.isNullOrEmpty() && defaultPersona != userAvatar
        val isTemporary = hasDifferentChatLock || (chatPersona.isNullOrEmpty() && hasDifferentDefaultLock)
        val info = if (isTemporary) {
            buildString {
                append("Current: ").append(personas[userAvatar] ?: "")
                if (hasDifferentChatLock) append(" Chat: ").append(personas[chatPersona] ?: "")
                if (hasDifferentDefaultLock) append(" Default: ").append(personas[defaultPersona] ?: "")
            }
        } else ""
        return PersonaLockInfo(isTemporary, hasDifferentChatLock, hasDifferentDefaultLock, info)
    }

    fun connected(personaDescriptions: Map<String, PersonaDescriptor>, characterKey: String): List<String> =
        personaDescriptions.filterValues { d -> d.connections.any { it.id == characterKey } }.keys.toList()

    fun connectionObj(selectedGroup: String?, charAvatar: String?): PersonaConnection? = when {
        selectedGroup != null -> PersonaConnection("group", selectedGroup)
        charAvatar != null -> PersonaConnection("character", charAvatar)
        else -> null
    }

    fun getOrCreateDescriptor(
        userAvatar: String,
        existing: MutableMap<String, PersonaDescriptor>,
        defaults: PersonaDescriptor,
    ): PersonaDescriptor = existing.getOrPut(userAvatar) { defaults.copy(connections = emptyList(), title = "") }

    fun resolve(
        chatMetaPersona: String?,
        userAvatars: List<String>,
        connectedPersonas: List<String>,
        defaultPersona: String?,
        allowMultiConnections: Boolean,
        userAvatar: String,
        personaAutoLock: Boolean = false,
    ): PersonaResolveResult {
        var chatPersona: String? = null
        var connectType: String? = null
        var unlockChat = false
        var clearDefault = false

        if (!chatMetaPersona.isNullOrEmpty()) {
            chatPersona = chatMetaPersona
            if (chatMetaPersona !in userAvatars) {
                unlockChat = true
                chatPersona = null
            }
            if (chatPersona != null) connectType = "chat"
        }

        if (chatPersona == null && connectedPersonas.isNotEmpty()) {
            if (connectedPersonas.size == 1 || !allowMultiConnections) {
                chatPersona = connectedPersonas.first()
            }
            if (chatPersona != null) connectType = "character"
        }

        if (chatPersona == null && !defaultPersona.isNullOrEmpty()) {
            chatPersona = defaultPersona
            if (chatPersona != null) connectType = "default"
        }

        if (!chatMetaPersona.isNullOrEmpty() && chatMetaPersona !in userAvatars) unlockChat = true
        if (!defaultPersona.isNullOrEmpty() && defaultPersona !in userAvatars) {
            clearDefault = true
        }

        val willSwitch = chatPersona != null && userAvatar != chatPersona
        val autoLock = chatPersona != null && personaAutoLock &&
            if (userAvatar != chatPersona) userAvatar != chatMetaPersona else chatMetaPersona.isNullOrEmpty()
        return PersonaResolveResult(chatPersona, connectType, unlockChat, clearDefault, willSwitch, autoLock)
    }
}
