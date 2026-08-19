package com.emberinn.engine.prompt

import kotlinx.serialization.Serializable

/**
 * Caption 扩展纯函数（1:1 差分，脚本 caption-official.mjs → fixture 17 例，CaptionDiffTest）。
 * 摘自 SillyTavern release 8172dcd public/scripts/extensions/caption/index.js：
 * - PROMPT_DEFAULT / TEMPLATE_DEFAULT
 * - resolvePrompt：external || settings.prompt || PROMPT_DEFAULT
 * - wrapCaptionTemplate：poka-yoke + substituteParams + dynamicMacros(caption) 不区分大小写替换
 * - captionMultimodal：getMultimodalCaption(base64, prompt) 不含固定 system（与 captionImageBySource multimodal 分支对照）
 * - isVideo：视频扩展名拦截（VIDEO_EXT），URL 忽略 query/hash
 */
@Serializable
data class MultimodalMsg(val role: String, val content: String, val images: List<String>)

object CaptionEngine {
    const val PROMPT_DEFAULT = "What's in this image?"
    const val TEMPLATE_DEFAULT = "[{{user}} sends {{char}} a picture that contains: {{caption}}]"

    private val VIDEO_EXT: Set<String> =
        setOf("mp4","webm","mov","avi","mkv","flv","wmv","m4v")

    fun resolvePrompt(external: String?, settingsPrompt: String): String {
        if (!external.isNullOrBlank()) return external
        if (settingsPrompt.isNotBlank()) return settingsPrompt
        return PROMPT_DEFAULT
    }

    fun wrapCaptionTemplate(
        template: String,
        caption: String,
        user: String,
        char: String,
    ): String {
        val t = if (!containsCaptionTag(template)) "$template {{caption}}" else template
        val u1 = t.replace("{{user}}", user).replace("{{User}}", user)
        val u2 = u1.replace("{{char}}", char).replace("{{Char}}", char)
        return Regex("""\{\{caption\}\}""", RegexOption.IGNORE_CASE).replace(u2, caption)
    }

    private fun containsCaptionTag(t: String): Boolean =
        Regex("""\{\{caption\}\}""", RegexOption.IGNORE_CASE).containsMatchIn(t)

    fun multimodalRequest(prompt: String, dataUrl: String): List<MultimodalMsg> =
        listOf(MultimodalMsg(role = "user", content = prompt, images = listOf(dataUrl)))

    fun isVideo(filenameOrUrl: String): Boolean {
        val clean = filenameOrUrl.substringBefore('?').substringBefore('#')
        val ext = clean.substringAfterLast('.', "")
        return VIDEO_EXT.contains(ext.lowercase())
    }
}
