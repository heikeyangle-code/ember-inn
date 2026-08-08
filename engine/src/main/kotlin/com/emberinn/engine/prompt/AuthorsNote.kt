package com.emberinn.engine.prompt

/** 作者注释（对齐官方 authors-note.js 核心设置）。 */
data class AuthorsNote(
    val content: String = "",
    val interval: Int = 1,
    val position: Int = 1,
    val depth: Int = 4,
    val role: String = "system",
    val allowWIScan: Boolean = true,
)

/** 导演备注全局/默认设置（对齐 authors-note.js extension_settings.note）。 */
data class AuthorsNoteSettings(
    val default: String = "",
    val defaultPosition: Int = 1,
    val defaultDepth: Int = 4,
    val defaultInterval: Int = 1,
    val defaultRole: Int = 0,
    val allowWIScan: Boolean = false,
)

/** 聊天元数据里的导演备注覆盖（对齐 metadata_keys）。 */
data class AuthorsNoteMetadata(
    val prompt: String? = null,
    val interval: Int? = null,
    val position: Int? = null,
    val depth: Int? = null,
    val role: Int? = null,
)

/** 对齐 authors-note.js：默认值解析 + ANWithWI 合并。 */
object AuthorsNoteEngine {

    fun resolve(meta: AuthorsNoteMetadata, settings: AuthorsNoteSettings): AuthorsNote {
        val roleValue = meta.role ?: settings.defaultRole ?: 0
        return AuthorsNote(
            content = meta.prompt ?: settings.default ?: "",
            interval = meta.interval ?: settings.defaultInterval ?: 1,
            position = meta.position ?: settings.defaultPosition ?: 1,
            depth = meta.depth ?: settings.defaultDepth ?: 4,
            role = when (roleValue) { 1 -> "user"; 2 -> "assistant"; else -> "system" },
            allowWIScan = settings.allowWIScan,
        )
    }

    /** 对齐 world-info.js ANWithWI：top + 原文 + bottom，去掉首尾换行。 */
    fun composeWithWorldInfo(original: String, top: List<String> = emptyList(), bottom: List<String> = emptyList()): String =
        listOf(top.joinToString("\n"), original, bottom.joinToString("\n"))
            .joinToString("\n")
            .replace(Regex("""^\n|\n$"""), "")
}

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
