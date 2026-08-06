package com.emberinn.engine.group

/** 群聊模型（对齐官方 group：成员/禁用成员/生成模式/队列）。 */
data class GroupChat(
    val id: String,
    val name: String,
    val members: List<String>,
    val disabledMembers: List<String> = emptyList(),
    val generationMode: Int = GroupGenerationMode.SWAP,
)

/** 官方 group_generation_mode。 */
object GroupGenerationMode {
    const val SWAP = 0
    const val APPEND = 1
    const val APPEND_DISABLED = 2
}

/**
 * 群聊调度核心（对齐官方 generateGroupWrapper 的激活策略）：
 * SWAP：上一发言人之后的下一个启用成员（循环）；
 * APPEND / APPEND_DISABLED：本轮所有（启用）成员依次回复。
 */
object GroupScheduler {

    fun nextSpeaker(
        group: GroupChat,
        lastSpeaker: String?,
    ): String? {
        val enabled = group.members.filter { it !in group.disabledMembers }
        if (enabled.isEmpty()) return null
        if (group.generationMode != GroupGenerationMode.SWAP) return null
        if (lastSpeaker == null || lastSpeaker !in enabled) return enabled.first()
        val idx = enabled.indexOf(lastSpeaker)
        return enabled[(idx + 1) % enabled.size]
    }

    fun speakersForTurn(group: GroupChat): List<String> {
        return when (group.generationMode) {
            GroupGenerationMode.APPEND -> group.members.filter { it !in group.disabledMembers }
            GroupGenerationMode.APPEND_DISABLED -> group.members
            else -> emptyList()
        }
    }
}

/** 群聊队列（对齐官方 groupChatQueueOrder：队首发言，发言后排到队尾）。 */
class GroupQueue(initial: List<String>) {
    private val queue = ArrayDeque<String>()

    init {
        queue.addAll(initial)
    }

    fun next(): String? {
        val head = queue.removeFirstOrNull() ?: return null
        queue.addLast(head)
        return head
    }

    fun peek(): String? = queue.firstOrNull()

    fun toList(): List<String> = queue.toList()
}
