package com.emberinn.app.data

/**
 * 显示管线 App 侧剩余工具：
 * - fixMarkdown / encode_tags 已迁至 engine（FixMarkdown / MessageFormattingEngine，差分锁死）
 * - balanceStreamingDelimiters：App 增强（官方 1.18 无此函数，流式中间态补齐用）
 */
object DisplayPipeline {

    /**
     * 官方 onProgressStreaming 的流式定界符补齐：* / " / ``` / ~~~ 出现奇数次时，
     * 在行尾补一个（多字符定界符前加换行），避免流式途中 markdown 结构断裂。
     * isFinal=true 时官方跳过补齐（保存时走 cleanUpMessage）。
     */
    fun balanceStreamingDelimiters(text: String, isFinal: Boolean = false): String {
        if (isFinal) return text
        // 快路径：文本里一个定界符都没有时直接返回（流式高频调用）
        if (text.indexOf('*') < 0 && text.indexOf('"') < 0 && !text.contains("```") && !text.contains("~~~")) return text
        var out = text
        for (delimiter in listOf("*", "\"", "```", "~~~")) {
            // 奇数且行尾未以该定界符结尾时才补：避免 "你好*" 被补成 "你好**"（视觉反而更怪）
            if (countOccurrences(out, delimiter) % 2 == 1 && !out.trimEnd().endsWith(delimiter)) {
                val separator = if (delimiter.length > 1) "\n" else ""
                out = out.trimEnd() + separator + delimiter
            }
        }
        return out
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val idx = text.indexOf(needle, from)
            if (idx < 0) break
            count++
            from = idx + needle.length
        }
        return count
    }
}
