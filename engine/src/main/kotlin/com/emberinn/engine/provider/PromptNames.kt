package com.emberinn.engine.provider

/** 对齐官方 PromptNames（getPromptNames 的纯数据面）。 */
data class PromptNames(
    val userName: String = "",
    val charName: String = "",
    val groupNames: List<String> = emptyList(),
) {
    fun startsWithGroupName(message: String): Boolean =
        groupNames.any { message.startsWith("$it: ") }
}
