package com.emberinn.engine.prompt

/**
 * 工具调用循环决策（对齐官方 ToolManager.RECURSE_LIMIT 默认 5 与 openai.js Generate 的递归语义）。
 * 工具真正执行由 App 层扩展注册表完成；本类只负责“是否继续 / 下一轮消息怎么拼”。
 */
object ToolLoopPlanner {

    /** 官方 oai_settings.tool_call_recurse_limit 默认值。 */
    const val DEFAULT_RECURSE_LIMIT = 5

    /**
     * 判断是否要继续工具循环：
     * 官方在响应含 tool_calls 且未超过递归上限时继续；达到上限则停止并把结果当普通回复。
     */
    fun shouldContinue(toolCalls: List<ToolCall>?, recursionCount: Int, recurseLimit: Int = DEFAULT_RECURSE_LIMIT): Boolean =
        toolCalls != null && toolCalls.isNotEmpty() && recursionCount < recurseLimit

    /**
     * 构建下一轮消息：assistant（带 tool_calls）+ 每个工具结果一条 role=tool 消息。
     * 官方工具结果消息：tool_call_id + content。
     */
    fun buildNextMessages(
        assistant: CompletionMessage,
        toolResults: List<Pair<String, String>>,
    ): List<CompletionMessage> = listOf(assistant) + toolResults.map { (callId, content) ->
        CompletionMessage(role = "tool", content = content, toolCallId = callId)
    }

    /** 生成一次“重试预算”推进：官方每次递归 +1，达到上限后由 App 停止循环。 */
    fun nextRecursionCount(current: Int): Int = current + 1
}
