package com.emberinn.engine.slash

/**
 * 斜杠链式执行引擎，对齐官方 SlashCommandClosure.executeDirect + SlashCommandExecutor：
 * - 单管道 |：前一条命令的输出注入下一条的无名参数（官方 scope.pipe + injectPipe）
 * - 双管道 ||：不注入
 * - 闭包 {: ... :}：作为参数值立即执行，取其 pipe 输出
 * - 返回链上最后一条命令的输出
 * 注：官方惰性闭包（传给命令对象）与 () 即时执行在本核心中统一为即时求值。
 */
object SlashEngine {

    fun execute(text: String): String {
        val segments = parseChain(text)
        var pipe: String = ""
        for ((index, segment) in segments.withIndex()) {
            val resolved = resolveClosures(segment.text)
            var invocation = SlashParser.parse(resolved)
            if (index > 0 && segment.inject && invocation.unnamedArgs.isEmpty()) {
                invocation = invocation.copy(unnamedArgs = listOf(pipe))
            }
            val def = SlashRegistry.get(invocation.name)
                ?: throw SlashParseException("未知命令: /${invocation.name}")
            pipe = def.callback(invocation)
        }
        return pipe
    }

    /** 顶层按 | / || 拆分，引号与闭包内的 | 不算。 */
    private fun parseChain(text: String): List<PipeSegment> {
        val segments = mutableListOf<PipeSegment>()
        val sb = StringBuilder()
        var quote: Char? = null
        var closureDepth = 0
        var nextInject = true
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote != null) {
                sb.append(c)
                if (c == quote) quote = null
                i++
                continue
            }
            when {
                c == '"' || c == '\'' -> { quote = c; sb.append(c) }
                text.startsWith("{:", i) -> { closureDepth++; sb.append("{:"); i += 2; continue }
                text.startsWith(":}", i) -> { closureDepth--; sb.append(":}"); i += 2; continue }
                c == '|' && closureDepth == 0 -> {
                    val inject = !text.startsWith("||", i)
                    segments.add(PipeSegment(sb.toString(), nextInject))
                    sb.setLength(0)
                    nextInject = inject
                    i += if (inject) 1 else 2
                    continue
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotBlank()) segments.add(PipeSegment(sb.toString(), nextInject))
        return segments
    }

    /** 把 {: 链 :} 替换为其执行输出（加引号保持单个参数）。 */
    private fun resolveClosures(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text.startsWith("{:", i)) {
                var depth = 1
                var j = i + 2
                var quote: Char? = null
                var end = -1
                while (j < text.length) {
                    val c = text[j]
                    if (quote != null) {
                        if (c == quote) quote = null
                    } else if (c == '"' || c == '\'') {
                        quote = c
                    } else if (text.startsWith("{:", j)) {
                        depth++; j += 1
                    } else if (text.startsWith(":}", j)) {
                        depth--
                        if (depth == 0) { end = j; break }
                        j += 1
                    }
                    j++
                }
                if (end < 0) { sb.append(text, i, text.length); break }
                val inner = text.substring(i + 2, end)
                val output = runCatching { execute(inner) }.getOrElse { "" }
                sb.append('"').append(output.replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
                i = end + 2
            } else {
                sb.append(text[i]); i++
            }
        }
        return sb.toString()
    }

    private data class PipeSegment(val text: String, val inject: Boolean)
}
