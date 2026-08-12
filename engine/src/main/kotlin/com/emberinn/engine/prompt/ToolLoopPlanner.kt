package com.emberinn.engine.prompt

/**
 * 工具调用循环决策（对齐官方 ToolManager.RECURSE_LIMIT 默认 5 与 openai.js Generate 的递归语义）。
 * 工具真正执行由 App 层扩展注册表完成；本类只负责“是否继续 / 下一轮消息怎么拼”。
 */
object ToolLoopPlanner {

    /** 官方 oai_settings.tool_call_recurse_limit 默认值。 */
    const val DEFAULT_RECURSE_LIMIT = 5

    /** 官方 ToolManager.canPerformToolCalls 禁用的生成类型。 */
    private val NO_TOOL_CALL_TYPES = setOf("impersonate", "quiet", "continue")

    /** 官方 script.js 工具循环决策快照（流式/非流式两分支）。 */
    data class ToolLoopDecision(
        val canPerformToolCalls: Boolean,
        val shouldDeleteMessage: Boolean,
        val shouldStopGeneration: Boolean,
        val shouldRecurse: Boolean,
        val nextDepth: Int,
    )

    /**
     * 对齐官方 script.js 4436/5351-5378/5482-5500：
     * canPerformToolCalls = !dryRun && 工具可用 && type 不在禁用集 && depth < RECURSE_LIMIT；
     * 流式分支额外要求 isStreamFinished && isStreamWithToolCalls；
     * shouldDeleteMessage（流式：mes 空 + 无 reasoning + result 空；非流式：getMessage 空 + 无 reasoning）；
     * shouldStopGeneration = (无调用结果 && shouldDeleteMessage) || stealthCalls；
     * 递归条件 = 有 tool_calls && !shouldStopGeneration，递归前 depth+1。
     */
    fun decide(
        dryRun: Boolean,
        type: String,
        depth: Int,
        recurseLimit: Int = DEFAULT_RECURSE_LIMIT,
        toolCallingSupported: Boolean = true,
        isStreaming: Boolean = false,
        isStreamFinished: Boolean = true,
        isStreamWithToolCalls: Boolean = false,
        hasToolCalls: Boolean = false,
        lastMessageMes: String = "",
        hasReasoning: Boolean = false,
        streamingResult: String = "",
        invocationCount: Int = 0,
        stealthCalls: Boolean = false,
    ): ToolLoopDecision {
        val canPerform = !dryRun && toolCallingSupported && type !in NO_TOOL_CALL_TYPES && depth < recurseLimit
        val loopActive = canPerform && if (isStreaming) isStreamFinished && isStreamWithToolCalls else true
        val shouldDelete = loopActive && if (isStreaming) {
            type != "swipe" && lastMessageMes in setOf("", "...") && !hasReasoning && streamingResult in setOf("", "...")
        } else {
            type != "swipe" && lastMessageMes in setOf("", "...") && !hasReasoning
        }
        val shouldStop = if (loopActive) {
            (invocationCount == 0 && shouldDelete) || stealthCalls
        } else {
            false
        }
        val shouldRecurse = loopActive && hasToolCalls && !shouldStop
        return ToolLoopDecision(
            canPerformToolCalls = canPerform,
            shouldDeleteMessage = shouldDelete,
            shouldStopGeneration = shouldStop,
            shouldRecurse = shouldRecurse,
            nextDepth = if (shouldRecurse) depth + 1 else depth,
        )
    }

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
