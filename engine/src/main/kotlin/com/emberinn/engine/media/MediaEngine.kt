package com.emberinn.engine.media

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** 媒体附件（对齐官方 MEDIA_TYPE / message.extra.media）。 */
data class MediaAttachment(
    val type: String,
    val url: String,
    val title: String = "",
)

/** 媒体显示方式（对齐官方 MEDIA_DISPLAY）。 */
object MediaDisplay {
    const val LIST = "list"
    const val GALLERY = "gallery"
}

/** 媒体纯逻辑：类型/显示方式/索引（对齐 script.js getMediaDisplay/getMediaIndex + constants.js getFromMime）。 */
object MediaEngine {

    private val validDisplays = setOf(MediaDisplay.LIST, MediaDisplay.GALLERY)

    fun typeFromMime(mimeType: String): String? = when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "audio"
        else -> null
    }

    fun getMediaDisplay(extraMediaDisplay: String?, powerUserMediaDisplay: String?): String {
        val value = extraMediaDisplay?.takeIf { it.isNotEmpty() }
            ?: powerUserMediaDisplay?.takeIf { it.isNotEmpty() }
            ?: MediaDisplay.LIST
        return if (value in validDisplays) value else MediaDisplay.LIST
    }

    /** 对齐官方：media_index 原样返回（数字返回数字、字符串返回字符串），无效回退 0。 */
    fun getMediaIndex(mediaCount: Int, mediaIndex: JsonElement?): JsonElement {
        if (mediaCount <= 0) return JsonPrimitive(0)
        if (mediaIndex == null) return JsonPrimitive(0)
        if (mediaIndex is JsonNull) return JsonNull
        val p = mediaIndex.jsonPrimitive
        val value = p.content.toDoubleOrNull() ?: return JsonPrimitive(0)
        if (value.isNaN() || value < 0 || value >= mediaCount) return JsonPrimitive(0)
        return p
    }
}
