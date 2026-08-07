package com.emberinn.engine.group

import kotlin.random.Random

/** 群聊成员（对齐官方 characters 的 avatar/name/talkativeness）。 */
data class GroupMember(
    val avatar: String,
    val name: String,
    val talkativeness: Double = 0.5,
)

/** 群聊消息（对齐官方 chat message 的激活相关字段）。 */
data class GroupMessage(
    val name: String = "",
    val isUser: Boolean = false,
    val isSystem: Boolean = false,
    val originalAvatar: String? = null,
    val extraType: String? = null,
)

/**
 * 群聊成员激活策略（对齐官方 group-chats.js activate*）：
 * NATURAL / LIST / MANUAL / POOLED / SWIPE / IMPERSONATE。
 * 返回激活的 avatar 列表；完整生成循环/消息组装仍由上层调用。
 */
object GroupActivationEngine {

    private const val NARRATOR = "narrator"
    private const val TALKATIVENESS_DEFAULT = 0.5
    private val wordRegex = Regex("""\b\w+\b""", RegexOption.IGNORE_CASE)

    fun listOrder(members: List<GroupMember>): List<String> = members.map { it.avatar }.distinct()

    fun impersonate(members: List<GroupMember>, random: () -> Double = { Random.nextDouble() }): List<String> {
        if (members.isEmpty()) return emptyList()
        val index = ((random() * members.size).toInt()).coerceIn(0, members.size - 1)
        return listOf(members[index].avatar)
    }

    fun swipe(
        members: List<GroupMember>,
        chat: List<GroupMessage>,
        allowSystem: Boolean = false,
        random: () -> Double = { Random.nextDouble() },
    ): List<String> {
        val activated = mutableListOf<String>()
        val lastMessage = chat.lastOrNull() ?: return emptyList()

        val skip = { m: GroupMessage ->
            m.isUser || (!allowSystem && m.isSystem) || m.extraType == NARRATOR
        }
        if (skip(lastMessage)) {
            for (message in chat.asReversed()) {
                if (skip(message)) continue
                if (message.originalAvatar != null) {
                    activated += message.originalAvatar
                    break
                }
            }
            if (activated.isEmpty()) {
                shuffle(members.map { it.avatar }, random).firstOrNull()?.let { activated += it }
            }
        }

        if (lastMessage.originalAvatar == null) {
            members.firstOrNull { it.name == lastMessage.name }?.avatar?.let { activated += it }
        } else {
            activated += lastMessage.originalAvatar
        }
        return activated.distinct()
    }

    fun pooled(
        members: List<GroupMember>,
        chat: List<GroupMessage>,
        lastMessage: GroupMessage?,
        isUserInput: Boolean,
        random: () -> Double = { Random.nextDouble() },
    ): List<String> {
        var activatedMember: String? = null
        val spokenSinceUser = mutableListOf<String>()
        for (message in chat.asReversed()) {
            if (message.isUser || isUserInput) break
            if (message.isSystem || message.extraType == NARRATOR) continue
            if (message.originalAvatar != null) spokenSinceUser += message.originalAvatar
        }

        val avatars = members.map { it.avatar }
        val haveNotSpoken = avatars.filter { it !in spokenSinceUser }
        if (haveNotSpoken.isNotEmpty()) {
            activatedMember = haveNotSpoken[((random() * haveNotSpoken.size).toInt()).coerceIn(0, haveNotSpoken.size - 1)]
        }
        if (activatedMember == null) {
            val lastMessageAvatar = avatars.size > 1 && lastMessage != null &&
                !lastMessage.isUser && lastMessage.originalAvatar != null
            val randomPool = if (lastMessageAvatar) {
                avatars.filter { it != lastMessage?.originalAvatar }
            } else {
                avatars
            }
            if (randomPool.isNotEmpty()) {
                activatedMember = randomPool[((random() * randomPool.size).toInt()).coerceIn(0, randomPool.size - 1)]
            }
        }
        return activatedMember?.let { listOf(it) } ?: emptyList()
    }

    fun natural(
        members: List<GroupMember>,
        input: String,
        lastMessage: GroupMessage?,
        allowSelfResponses: Boolean,
        isUserInput: Boolean,
        random: () -> Double = { Random.nextDouble() },
    ): List<String> {
        val activated = mutableListOf<String>()
        var bannedUser: String? =
            if (!isUserInput && lastMessage != null && !lastMessage.isUser && lastMessage.name.isNotEmpty()) {
                lastMessage.name
            } else {
                null
            }
        if (allowSelfResponses) bannedUser = null

        if (input.isNotEmpty()) {
            for (inputWord in extractWords(input)) {
                for (member in members) {
                    if (member.name == bannedUser) continue
                    if (extractWords(member.name).contains(inputWord)) {
                        activated += member.avatar
                        break
                    }
                }
            }
        }

        val chattyMembers = mutableListOf<GroupMember>()
        for (avatar in shuffle(members.map { it.avatar }, random)) {
            val member = members.first { it.avatar == avatar }
            if (member.name == bannedUser) continue
            val roll = random()
            val talkativeness = member.talkativeness
            if (talkativeness >= roll) activated += member.avatar
            if (talkativeness > 0) chattyMembers += member
        }

        val randomPool = if (chattyMembers.isNotEmpty()) chattyMembers.map { it.avatar } else members.map { it.avatar }
        var retries = 0
        while (activated.isEmpty() && ++retries <= randomPool.size) {
            if (randomPool.isEmpty()) break
            val index = ((random() * randomPool.size).toInt()).coerceIn(0, randomPool.size - 1)
            val avatar = randomPool[index]
            if (members.any { it.avatar == avatar }) activated += avatar
        }

        return activated.distinct()
    }

    private fun extractWords(value: String): List<String> =
        wordRegex.findAll(value).map { it.value.lowercase() }.toList()

    private fun shuffle(list: List<String>, random: () -> Double): List<String> {
        val arr = list.toMutableList()
        var current = arr.size
        while (current != 0) {
            val randomIndex = ((random() * current).toInt()).coerceIn(0, current - 1)
            current--
            val tmp = arr[current]
            arr[current] = arr[randomIndex]
            arr[randomIndex] = tmp
        }
        return arr
    }
}
