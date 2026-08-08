package com.emberinn.engine.group

/** 自动续写设置（对齐 power_user.auto_continue）。 */
data class AutoContinueSettings(
    val enabled: Boolean = false,
    val targetLength: Int = 0,
    val allowChatCompletions: Boolean = false,
)

/** 群聊生成计划中的一名成员。 */
data class GroupPlanEntry(
    val avatar: String,
    val generateType: String,
    val queue: Int?,
)

/** 群聊完整生成计划（对齐 generateGroupWrapper 纯逻辑部分）。 */
data class GroupGenerationPlan(
    val plan: List<GroupPlanEntry>,
    val queueOrder: List<Pair<String, Int>>,
)

/** 群聊完整循环纯逻辑：自动续写判定 + 每人生成类型/队列。 */
object GroupLoopEngine {

    fun shouldAutoContinue(
        messageChunk: String?,
        isImpersonate: Boolean,
        settings: AutoContinueSettings,
        userInputEmpty: Boolean,
        lastMessageTokens: Int?,
        isOpenAi: Boolean,
    ): Boolean {
        if (!settings.enabled) return false
        if (messageChunk == null) return false
        if (isImpersonate) return false
        if (settings.targetLength <= 0) return false
        if (isOpenAi && !settings.allowChatCompletions) return false
        if (!userInputEmpty) return false
        val usableLength = 5
        if (messageChunk.trim().length > usableLength && lastMessageTokens != null) {
            return lastMessageTokens < settings.targetLength
        }
        return false
    }

    fun planGeneration(
        type: String,
        activatedMembers: List<String>,
        showQueue: Boolean,
    ): GroupGenerationPlan {
        val special = setOf("swipe", "impersonate", "quiet", "continue")
        val generateType = if (type in special) type else "normal"
        val queueOrder = if (showQueue) activatedMembers.mapIndexed { i, a -> a to (i + 1) } else emptyList()
        val plan = activatedMembers.map { avatar ->
            GroupPlanEntry(
                avatar = avatar,
                generateType = generateType,
                queue = if (showQueue) activatedMembers.indexOf(avatar) + 1 else null,
            )
        }
        return GroupGenerationPlan(plan, queueOrder)
    }
}
