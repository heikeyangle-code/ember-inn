package com.emberinn.engine.prompt

/** 提示消息（对齐官方 systemPrompts / OpenAI messages 核心字段）。 */
data class PromptMessage(
    val role: String,
    val content: String,
    val name: String? = null,
    val identifier: String? = null,
    val position: String? = null,
    val extension: Boolean = false,
    val injected: Boolean = false,
    val toolInvocations: List<ToolInvocation>? = null,
    /** 对齐官方 messages[i].media（extra.media，聊天附件）。 */
    val media: List<com.emberinn.engine.media.MediaAttachment>? = null,
    /** 对齐官方 messages[i].mediaDisplay（list/gallery）。 */
    val mediaDisplay: String? = null,
    /** 对齐官方 messages[i].mediaIndex（gallery 选中下标）。 */
    val mediaIndex: Int? = null,
    /** 对齐官方 messages[i].signature（仅同 API/模型才携带，App 层过滤）。 */
    val signature: String? = null,
    /** 对齐官方 messages[i].reasoning（助手思考文本，工具推理链用）。 */
    val reasoning: String? = null,
)

/** 对齐官方 ToolInvocation 核心字段（id/name/parameters/result + 推理链签名/思考）。 */
data class ToolInvocation(
    val id: String,
    val name: String,
    val parameters: String,
    val result: String,
    val reasoning: String? = null,
    val signature: String? = null,
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
