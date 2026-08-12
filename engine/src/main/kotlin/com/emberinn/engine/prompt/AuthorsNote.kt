package com.emberinn.engine.prompt

/** 作者注释（对齐官方 authors-note.js 核心设置）。 */
data class AuthorsNote(
    val content: String = "",
    val interval: Int = 1,
    val position: Int = 1,
    val depth: Int = 4,
    val role: String = "system",
    /** 官方 resolve 的原始数字 role（透传，含非法值；注入时按官方查表转字符串）。 */
    val roleRaw: Int? = null,
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

/** 官方角色备注（extension_settings.note.chara 条目）。position：0=replace/1=before/2=after。 */
data class CharaNote(
    val name: String,
    val prompt: String = "",
    val useChara: Boolean = false,
    val position: Int = 0,
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
            roleRaw = roleValue,
            // 官方 getExtensionPromptRoleByName 查表：0=system/1=user/2=assistant，其它（含非法）默认 system
            role = when (roleValue) { 1 -> "user"; 2 -> "assistant"; else -> "system" },
            allowWIScan = settings.allowWIScan,
        )
    }

    /** 官方 authors-note.js：按“用户消息数”判断是否该注入 AN。 */
    fun shouldInjectNote(lastUserMessageNumber: Int, interval: Int): Boolean {
        var last = lastUserMessageNumber
        var intervalValue = interval
        // interval 1 应无论如何都注入
        if (intervalValue == 1) last = 1
        if (last <= 0 || intervalValue <= 0) return false
        val messagesTillInsertion = if (last >= intervalValue) {
            last % intervalValue
        } else {
            intervalValue - last
        }
        return messagesTillInsertion == 0
    }

    /** 对齐 world-info.js ANWithWI：top + 原文 + bottom，只去掉一个首部换行和一个尾部换行（官方 replace /(^\n)|(\n$)/g）。 */
    fun composeWithWorldInfo(original: String, top: List<String> = emptyList(), bottom: List<String> = emptyList()): String {
        val joined = listOf(top.joinToString("\n"), original, bottom.joinToString("\n")).joinToString("\n")
        return joined.removePrefix("\n").removeSuffix("\n")
    }

    /** 官方 authors-note.js 角色备注应用：useChara 时 before=前置/after=后置/replace=替换。 */
    fun applyCharaNote(prompt: String, charaNote: CharaNote?): String {
        if (charaNote == null || !charaNote.useChara) return prompt
        return when (charaNote.position) {
            1 -> charaNote.prompt + "\n" + prompt
            2 -> prompt + "\n" + charaNote.prompt
            else -> charaNote.prompt
        }
    }
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
