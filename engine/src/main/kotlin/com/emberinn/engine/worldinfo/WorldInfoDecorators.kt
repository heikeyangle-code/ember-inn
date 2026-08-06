package com.emberinn.engine.worldinfo

/** 对齐官方 parseDecorators：内容开头的 @@ 装饰器行从正文剥离。 */
object WorldInfoDecorators {

    private val KNOWN = listOf("@@activate", "@@dont_activate")

    fun parse(content: String): Pair<List<String>, String> {
        if (!content.startsWith("@@")) return emptyList() to content

        val lines = content.split("\n")
        val decorators = mutableListOf<String>()
        var fallbacked = false
        var newContent = content

        for (i in lines.indices) {
            if (lines[i].startsWith("@@")) {
                if (lines[i].startsWith("@@@") && !fallbacked) continue
                if (isKnown(lines[i])) {
                    decorators.add(if (lines[i].startsWith("@@@")) lines[i].substring(1) else lines[i])
                    fallbacked = false
                } else {
                    fallbacked = true
                }
            } else {
                newContent = lines.drop(i).joinToString("\n")
                break
            }
        }
        return decorators to newContent
    }

    private fun isKnown(data: String): Boolean {
        val d = if (data.startsWith("@@@")) data.substring(1) else data
        return KNOWN.any { d.startsWith(it) }
    }
}
