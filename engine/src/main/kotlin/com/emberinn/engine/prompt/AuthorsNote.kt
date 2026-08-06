package com.emberinn.engine.prompt

/** 作者注释（对齐官方 authors-note.js 核心设置）。 */
data class AuthorsNote(
    val content: String = "",
    val position: Int = 0,
    val depth: Int = 4,
    val role: String = "system",
    val allowWIScan: Boolean = true,
)

/** 对齐官方 ANWithWI 组合：AN 前后并入世界书 AN 注入，去掉首尾换行。 */
object AuthorsNoteBuilder {

    fun compose(
        content: String,
        anBefore: List<String>,
        anAfter: List<String>,
        allowWIScan: Boolean,
    ): String {
        if (!allowWIScan) return content
        return listOf(anBefore.joinToString("\n"), content, anAfter.joinToString("\n"))
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim('\n')
    }
}
