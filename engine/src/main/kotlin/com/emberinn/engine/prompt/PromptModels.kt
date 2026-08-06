package com.emberinn.engine.prompt

/** 提示消息（对齐官方 systemPrompts / OpenAI messages 核心字段）。 */
data class PromptMessage(
    val role: String,
    val content: String,
    val name: String? = null,
    val identifier: String? = null,
    val position: String? = null,
    val extension: Boolean = false,
)

/** renderStoryString 参数（对齐官方 storyStringParams）。 */
data class StoryParams(
    val description: String = "",
    val personality: String = "",
    val persona: String = "",
    val scenario: String = "",
    val system: String = "",
    val char: String = "",
    val user: String = "",
    val wiBefore: String = "",
    val wiAfter: String = "",
    val mesExamples: String = "",
    val mesExamplesRaw: String = "",
    val anchorBefore: String = "",
    val anchorAfter: String = "",
)
