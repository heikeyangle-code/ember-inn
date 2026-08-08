package com.emberinn.engine.media

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 媒体内容块转换（逐字对齐 prompt-converters.js Claude/Gemini 部分）。
 * 官方 fixture 由 scripts/diff/media-convert-official.mjs 生成，禁止手改。
 */
object MediaConverter {

    /** JS arr[i] 语义：越界返回 null（对应 JS undefined，序列化时字段省略）。 */
    private fun splitIndex(s: String, sep: String, index: Int): String? {
        val parts = s.split(sep)
        return if (parts.size > index) parts[index] else null
    }

    /**
     * 对齐 convertClaudeMessages 数组内容块：image_url → image/source，text → text（空文本零宽空格，name 前缀）。
     * 与官方相同：非 data: 的 URL 也按 split 语义产出（media_type 为 URL 冒号后段、data 省略）。
     */
    fun convertClaudePart(part: JsonObject, name: String? = null): JsonObject {
        val type = part["type"]?.jsonPrimitive?.content ?: return part
        return when (type) {
            "image_url" -> {
                val imageData = part["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
                val mimeType = splitIndex(splitIndex(imageData, ";", 0) ?: "", ":", 1)
                val base64Data = splitIndex(imageData, ",", 1)
                buildJsonObject {
                    put("type", JsonPrimitive("image"))
                    put("source", buildJsonObject {
                        put("type", JsonPrimitive("base64"))
                        if (mimeType != null) put("media_type", JsonPrimitive(mimeType))
                        if (base64Data != null) put("data", JsonPrimitive(base64Data))
                    })
                }
            }
            "text" -> {
                val rawText = part["text"]?.jsonPrimitive?.content ?: ""
                val text = if (!name.isNullOrEmpty()) "$name: $rawText" else rawText
                buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(text.ifEmpty { "\u200b" }))
                }
            }
            else -> part
        }
    }

    /** 对齐 convertGooglePrompt：text / image_url / video_url / audio_url → Gemini parts，非 data: 返回 null。 */
    fun convertGeminiPart(part: JsonObject, model: String): JsonElement? {
        val type = part["type"]?.jsonPrimitive?.content ?: return part
        return when (type) {
            "text" -> buildJsonObject { put("text", JsonPrimitive(part["text"]?.jsonPrimitive?.content ?: "")) }
            "image_url" -> geminiDataUrl(part["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: "", "image/png", part["image_url"]?.jsonObject?.get("detail")?.jsonPrimitive?.content, model)
            "video_url" -> geminiDataUrl(part["video_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: "", "video/mp4", part["video_url"]?.jsonObject?.get("detail")?.jsonPrimitive?.content, model)
            "audio_url" -> geminiDataUrl(part["audio_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: "", "audio/mpeg", null, model)
            else -> part
        }
    }

    /**
     * 对齐 addDataUrlPart：url 必须非空且以 data: 开头；base64Data 取逗号分割第 2 段（多余逗号丢弃）；
     * 分辨率枚举为官方 media_resolution_low/high，仅 gemini-3 模型带 mediaResolution。
     */
    private fun geminiDataUrl(url: String, defaultMime: String, detail: String?, model: String): JsonElement? {
        if (url.isEmpty() || !url.startsWith("data:")) return null
        val parts = url.split(",")
        val header = parts[0]
        val base64Data = parts.getOrNull(1)
        val mimeType = Regex("""data:([^;]+)""").find(header)?.groupValues?.get(1) ?: defaultMime
        val mediaResolution = when (detail) {
            "low" -> "media_resolution_low"
            "high" -> "media_resolution_high"
            else -> null
        }
        return buildJsonObject {
            put("inlineData", buildJsonObject {
                put("mimeType", JsonPrimitive(mimeType))
                if (base64Data != null) put("data", JsonPrimitive(base64Data))
            })
            if (Regex("gemini-3").containsMatchIn(model) && mediaResolution != null) {
                put("mediaResolution", buildJsonObject { put("level", JsonPrimitive(mediaResolution)) })
            }
        }
    }
}
