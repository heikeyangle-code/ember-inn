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
                "horde" -> horde(context, prompt, model, apiKey, steps)
                "comfy" -> comfy(context, url, prompt, model, steps)
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

    /**
     * Stable Horde：官方 src/endpoints/horde.js generate-image 1:1——
     * 截断(5000-neg-5) + sanitizeHordeImagePrompt + POST /api/v2/generate/async，
     * 轮询 /check/{id} 至 done，/status/{id} 取 generations[0].img（base64 webp）。
     * 默认参数对齐官方：cfg_scale=7、512x512、karras=true、sampler=k_euler_a、nsfw=false。
     */
    private fun horde(context: Context, prompt: String, model: String, apiKey: String, steps: Int): String? {
        val negative = ""
        val maxLength = 5000 - negative.length - 5
        val safePrompt = if (prompt.length > maxLength) prompt.substring(0, maxLength) else prompt
        val sanitized = sanitizeHordePrompt(safePrompt)
        val payload = JSONObject()
            .put("prompt", "$sanitized ### $negative")
            .put("params", JSONObject()
                .put("sampler_name", "k_euler_a")
                .put("hires_fix", false)
                .put("use_gfpgan", false)
                .put("cfg_scale", 7)
                .put("steps", steps.coerceIn(1, 50))
                .put("width", 512)
                .put("height", 512)
                .put("karras", true)
                .put("seed", kotlin.random.Random.nextLong(0, Long.MAX_VALUE).toString())
                .put("n", 1))
            .put("r2", false)
            .put("nsfw", false)
            .put("models", JSONArray().put(model.ifBlank { "Deliberate" }))
        val requestBuilder = Request.Builder()
            .url("https://stablehorde.net/api/v2/generate/async")
            .post(payload.toString().toRequestBody(jsonMedia))
        if (apiKey.isNotBlank()) requestBuilder.header("apikey", apiKey)
        val id = client.newCall(requestBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string().orEmpty()).optString("id").ifBlank { return null }
        }
        // 官方 CHECK_INTERVAL=3000 / MAX_ATTEMPTS=200
        repeat(200) {
            Thread.sleep(3000)
            val done = client.newCall(
                Request.Builder().url("https://stablehorde.net/api/v2/generate/check/$id").get().build(),
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                JSONObject(resp.body?.string().orEmpty()).optBoolean("done")
            }
            if (done) {
                val status = client.newCall(
                    Request.Builder().url("https://stablehorde.net/api/v2/generate/status/$id").get().build(),
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    JSONObject(resp.body?.string().orEmpty())
                } ?: return null
                val img = status.optJSONArray("generations")?.optJSONObject(0)?.optString("img") ?: return null
                return runCatching { saveBase64(context, img, "webp") }.getOrNull()
            }
        }
        return null
    }

    /**
     * ComfyUI：官方 src/endpoints/stable-diffusion.js comfy.generate 1:1——
     * POST {url}/prompt（workflow JSON 字符串，含 %prompt%/%model%/%steps%/%width% 等占位符），
     * 轮询 /history 至 prompt_id 出现，取 outputs 第一张图，GET /view 下载。
     * workflow 由用户在设置里提供（官方默认 Default_Comfy_Workflow.json 不在仓库，登记）。
     */
    private fun comfy(context: Context, url: String, prompt: String, model: String, steps: Int): String? {
        if (url.isBlank()) return null
        val workflow = ServicesPrefs.comfyWorkflow(context)
        if (workflow.isBlank()) return null
        val seed = kotlin.random.Random.nextLong(0, Long.MAX_VALUE).toString()
        var replaced = workflow
        val replacements = mapOf(
            "%prompt%" to JSONObject().put("v", prompt).toString().removePrefix("{\"v\":").removeSuffix("}"),
            "%negative_prompt%" to "\"\"",
            "%seed%" to "\"$seed\"",
            "%denoise%" to "1.0",
            "%clip_skip%" to "-1",
            "%model%" to JSONObject().put("v", model.ifBlank { "v1-5-pruned-emaonly.safetensors" }).toString().removePrefix("{\"v\":").removeSuffix("}"),
            "%vae%" to "\"\"",
            "%sampler%" to "\"euler\"",
            "%scheduler%" to "\"normal\"",
            "%steps%" to steps.toString(),
            "%scale%" to "7",
            "%width%" to "512",
            "%height%" to "512",
        )
        for ((k, v) in replacements) replaced = replaced.replace(k, v)

        // 1) 提交 prompt
        val submit = Request.Builder()
            .url(url.trimEnd('/') + "/prompt")
            .post(replaced.toRequestBody(jsonMedia))
            .build()
        val id = client.newCall(submit).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string().orEmpty()).optString("prompt_id").ifBlank { return null }
        }

        // 2) 轮询 /history（官方 100ms 间隔；上限 6000 次 ≈ 10 分钟）
        var history: JSONObject? = null
        repeat(6000) {
            Thread.sleep(100)
            history = client.newCall(
                Request.Builder().url(url.trimEnd('/') + "/history").get().build(),
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string().orEmpty())
            }
            if (history?.has(id) == true) return@repeat
            history = null
        }
        val item = history?.optJSONObject(id) ?: return null
        if (item.optJSONObject("status")?.optString("status_str") == "error") return null
        val outputs = item.optJSONObject("outputs") ?: return null
        val keys = outputs.keys()
        var imgInfo: JSONObject? = null
        while (keys.hasNext()) {
            val out = outputs.optJSONObject(keys.next()) ?: continue
            val images = out.optJSONArray("images") ?: out.optJSONArray("gifs") ?: continue
            if (images.length() > 0) { imgInfo = images.optJSONObject(0); break }
        }
        val info = imgInfo ?: return null
        val filename = info.optString("filename")
        val subfolder = info.optString("subfolder")
        val type = info.optString("type")
        val ext = filename.substringAfterLast('.', "png").lowercase()

        // 3) 下载图片
        val viewUrl = url.trimEnd('/') + "/view?filename=${java.net.URLEncoder.encode(filename, "UTF-8")}" +
            "&subfolder=${java.net.URLEncoder.encode(subfolder, "UTF-8")}&type=${java.net.URLEncoder.encode(type, "UTF-8")}"
        val bytes = client.newCall(Request.Builder().url(viewUrl).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.bytes()
        } ?: return null
        return saveBytes(context, bytes, ext)
    }

    /** 官方 sanitizeHordeImagePrompt（horde.js）：替换/移除高风险的年龄相关词。 */
    private fun sanitizeHordePrompt(prompt: String): String {
        var out = prompt
        out = Regex("\\b(girl)\\b", RegexOption.IGNORE_CASE).replace(out, "woman")
        out = Regex("\\b(boy)\\b", RegexOption.IGNORE_CASE).replace(out, "man")
        out = Regex("\\b(girls)\\b", RegexOption.IGNORE_CASE).replace(out, "women")
        out = Regex("\\b(boys)\\b", RegexOption.IGNORE_CASE).replace(out, "men")
        out = Regex("\\b(under\\.age|under\\.aged|underage|underaged|loli|pedo|pedophile|(\\w+)\\.year\\.old|(\\w+)\\.years\\.old|minor|prepubescent|minors|shota)\\b", RegexOption.IGNORE_CASE).replace(out, "")
        out = Regex("\\b(youngster|infant|baby|toddler|child|teen|kid|kiddie|kiddo|teenager|student|preteen|pre\\.teen)\\b", RegexOption.IGNORE_CASE).replace(out, "person")
        out = Regex("\\b(young|younger|youthful|youth|small|smaller|smallest|girly|boyish|lil|tiny|teenaged|lit[tl]le|school\\.aged|school|highschool|kindergarten|teens|children|kids)\\b", RegexOption.IGNORE_CASE).replace(out, "")
        return out
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
