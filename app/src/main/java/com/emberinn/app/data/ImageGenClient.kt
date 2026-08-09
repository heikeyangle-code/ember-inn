package com.emberinn.app.data

import android.content.Context
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 图像生成执行层（P1-6）：对齐官方 stable-diffusion 扩展的 AUTOMATIC1111 协议
 * （POST {url}/sdapi/v1/txt2img，images[0] = base64）。
 * 未实现（登记）：ComfyUI/SDCPP/Draw Things/Stable Horde/NovelAI/OpenAI/HuggingFace 来源。
 */
class ImageGenClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 返回生成的图片本地路径（失败返回 null）。 */
    suspend fun generate(context: Context, prompt: String): String? = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext null
        val source = ServicesPrefs.imageSource(context)
        val url = ServicesPrefs.imageUrl(context)
        val steps = ServicesPrefs.imageSteps(context)
        if (source != "auto" || url.isBlank()) return@withContext null
        runCatching {
            val payload = JSONObject()
                .put("prompt", prompt)
                .put("steps", steps)
                .put("width", 512)
                .put("height", 768)
                .toString()
            val request = Request.Builder()
                .url(url.trimEnd('/') + "/sdapi/v1/txt2img")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val images = JSONObject(resp.body?.string().orEmpty()).optJSONArray("images") ?: return@use null
                val base64 = images.optString(0) ?: return@use null
                val bytes = Base64.getDecoder().decode(base64)
                val file = File(context.filesDir, "media/gen-${System.nanoTime()}.png")
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                file.absolutePath
            }
        }.getOrNull()
    }
}
