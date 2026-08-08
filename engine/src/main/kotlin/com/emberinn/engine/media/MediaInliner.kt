package com.emberinn.engine.media

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 媒体内联（对齐 openai.js Message.addImage/addVideo/addAudio 的内容部分）。
 * 网络抓取/压缩/token 计算由调用方负责。
 */
object MediaInliner {

    fun inlineOpenAi(
        content: JsonElement?,
        media: List<MediaAttachment>,
        quality: String = "auto",
    ): JsonElement {
        if (media.isEmpty()) {
            return content ?: JsonArray(emptyList())
        }
        val parts = mutableListOf<JsonElement>()
        when (content) {
            is JsonArray -> parts.addAll(content)
            is JsonPrimitive -> if (content.isString) {
                parts += buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", content)
                }
            }
            else -> {}
        }

        for (m in media) {
            when (m.type) {
                "image" -> parts += buildJsonObject {
                    put("type", JsonPrimitive("image_url"))
                    put("image_url", buildJsonObject {
                        put("url", JsonPrimitive(m.url))
                        put("detail", JsonPrimitive(quality))
                    })
                }
                "video" -> parts += buildJsonObject {
                    put("type", JsonPrimitive("video_url"))
                    put("video_url", buildJsonObject {
                        put("url", JsonPrimitive(m.url))
                        put("detail", JsonPrimitive(quality))
                    })
                }
                "audio" -> parts += buildJsonObject {
                    put("type", JsonPrimitive("audio_url"))
                    put("audio_url", buildJsonObject {
                        put("url", JsonPrimitive(m.url))
                    })
                }
            }
        }
        return JsonArray(parts)
    }
}
