package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.provider.ProviderStore
import com.emberinn.app.ui.settings.ServicesPrefs
import java.io.File
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 图像生成执行层：对齐官方 stable-diffusion 扩展。
 * 已实现：AUTOMATIC1111（auto）、SDCPP（sdcpp，同 /sdapi/v1/txt2img）、NovelAI（zip→png）、
 * OpenAI gpt-image、Hugging Face Inference（原始字节）。
 * 开发中（UI 如实标注）：ComfyUI / Draw Things / Stable Horde。
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
        val model = ServicesPrefs.imageModel(context)
        val steps = ServicesPrefs.imageSteps(context)
        val apiKey = ServicesPrefs.imageApiKey(context)
        runCatching {
            when (source) {
                "openai" -> openAi(context, prompt)
                "sdcpp" -> auto1111(context, url, prompt, steps, model)
                "novel" -> novel(context, prompt, model, apiKey, steps)
                "huggingface" -> huggingface(context, prompt, model, apiKey)
                else -> auto1111(context, url, prompt, steps, null)
            }
        }.getOrNull()
    }

    /** OpenAI gpt-image（复用提供商档案里的 Key；默认官方 /v1/images/generations）。 */
    private fun openAi(context: Context, prompt: String): String? {
        val profile = ProviderStore(File(context.filesDir, "provider")).load()
        if (profile == null || profile.providerId != "openai" || profile.apiKey.isBlank()) return null
        val payload = JSONObject()
            .put("model", "gpt-image-1")
            .put("prompt", prompt)
            .put("size", "1024x1024")
            .toString()
        val baseUrl = profile.baseUrlOverride.ifBlank { "https://api.openai.com/v1" }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/images/generations")
            .post(payload.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer ${profile.apiKey}")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val root = JSONObject(resp.body?.string().orEmpty())
            val b64 = root.optJSONArray("data")?.optJSONObject(0)?.optString("b64_json") ?: return null
            return runCatching { saveBase64(context, b64, "png") }.getOrNull()
        }
    }

    /** AUTOMATIC1111 / SDCPP：POST {url}/sdapi/v1/txt2img，images[0]=base64（sdcpp 带 model）。 */
    private fun auto1111(context: Context, url: String, prompt: String, steps: Int, model: String?): String? {
        if (url.isBlank()) return null
        val payload = JSONObject()
            .put("prompt", prompt)
            .put("steps", steps)
            .put("width", 512)
            .put("height", 768)
            .apply { if (!model.isNullOrBlank()) put("model", model) }
            .toString()
        val request = Request.Builder()
            .url(url.trimEnd('/') + "/sdapi/v1/txt2img")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val images = JSONObject(resp.body?.string().orEmpty()).optJSONArray("images") ?: return null
            val base64 = images.optString(0)
            return runCatching { saveBase64(context, base64, "png") }.getOrNull()
        }
    }

    /**
     * NovelAI：POST https://image.novelai.net/ai/generate-image（官方 src/endpoints/novelai.js 1:1 请求体），
     * 响应为 ZIP 归档；官方提取其中 PNG 回传 base64，本实现直接解压出 PNG 落盘。
     */
    private fun novel(context: Context, prompt: String, model: String, apiKey: String, steps: Int): String? {
        if (apiKey.isBlank()) return null
        val seed = kotlin.random.Random.nextLong(0, 10_000_000_000)
        val parameters = JSONObject()
            .put("params_version", 3)
            .put("prefer_brownian", true)
            .put("negative_prompt", "")
            .put("height", 512)
            .put("width", 512)
            .put("scale", 9)
            .put("seed", seed)
            .put("sampler", "k_dpmpp_2m")
            .put("noise_schedule", "karras")
            .put("steps", steps.coerceAtMost(50))
            .put("n_samples", 1)
            .put("ucPreset", 0)
            .put("qualityToggle", false)
            .put("add_original_image", false)
            .put("controlnet_strength", 1)
            .put("deliberate_euler_ancestral_bug", false)
            .put("dynamic_thresholding", false)
            .put("legacy", false)
            .put("legacy_v3_extend", false)
            .put("sm", false)
            .put("sm_dyn", false)
            .put("uncond_scale", 1)
            .put("skip_cfg_above_sigma", JSONObject.NULL)
            .put("use_coords", false)
            .put("characterPrompts", JSONArray())
            .put("reference_image_multiple", JSONArray())
            .put("reference_information_extracted_multiple", JSONArray())
            .put("reference_strength_multiple", JSONArray())
            .put("v4_negative_prompt", JSONObject().put("caption", JSONObject().put("base_caption", "").put("char_captions", JSONArray())))
            .put("v4_prompt", JSONObject().put("caption", JSONObject().put("base_caption", prompt).put("char_captions", JSONArray())))
            .put("use_order", true)
        val payload = JSONObject()
            .put("action", "generate")
            .put("input", prompt)
            .put("model", model.ifBlank { "nai-diffusion-3" })
            .put("parameters", parameters)
            .toString()
        val request = Request.Builder()
            .url("https://image.novelai.net/ai/generate-image")
            .post(payload.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val archive = resp.body?.bytes() ?: return null
            // 官方 extractFileFromZipBuffer：取归档内 PNG
            ZipInputStream(archive.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".png", ignoreCase = true)) {
                        return saveBytes(context, zip.readBytes(), "png")
                    }
                    entry = zip.nextEntry
                }
            }
            return null
        }
    }

    /** Hugging Face Inference：POST /models/{model} {inputs: prompt}，响应为图片原始字节。 */
    private fun huggingface(context: Context, prompt: String, model: String, apiKey: String): String? {
        if (apiKey.isBlank() || model.isBlank()) return null
        val payload = JSONObject().put("inputs", prompt).toString()
        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/${model.trim('/')}")
            .post(payload.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return saveBytes(context, resp.body?.bytes() ?: return null, "png")
        }
    }

    private fun saveBase64(context: Context, base64: String, ext: String): String =
        saveBytes(context, Base64.getDecoder().decode(base64), ext)

    private fun saveBytes(context: Context, bytes: ByteArray, ext: String): String {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(dir, "gen-${System.nanoTime()}.$ext")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
