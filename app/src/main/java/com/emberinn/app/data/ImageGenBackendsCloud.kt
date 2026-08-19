package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.ServicesPrefs
import java.io.File
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 云端图像生成后端集合：对齐官方 stable-diffusion 扩展
 * /root/sillytavern-ref/src/endpoints/stable-diffusion.js 各 `{backend}.post('/generate')`
 * 路由所转发的上游调用（1:1），共 9 个：TogetherAI、Pollinations、Stability、AIMLAPI、
 * Chutes、ElectronHub、NanoGPT、BFL、xAI。每个函数对照同包 index.js 的 generateXxxImage
 * 客户端请求体 + 服务器路由的真实上游 URL/Header/响应解析翻译为 Kotlin。
 *
 * 配置统一取自 [ServicesPrefs]；落盘复用 [saveBase64] / [saveBytes]（与 ImageGenClient 同模式）。
 * 失败返回 null。各函数为 private suspend fun，由 [generate] 按 source 分派；
 * ImageGenClient 通过 [generate] 调用这些后端。
 */
object ImageGenBackendsCloud {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 按 source 分派到对应云端后端；任一失败均返回 null。 */
    suspend fun generate(context: Context, source: String, prompt: String, negativePrompt: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                when (source) {
                    "togetherai" -> generateTogetherAIImage(context, prompt, negativePrompt)
                    "pollinations" -> generatePollinationsImage(context, prompt, negativePrompt)
                    "stability" -> generateStabilityImage(context, prompt, negativePrompt)
                    "aimlapi" -> generateAimlapiImage(context, prompt, negativePrompt)
                    "chutes" -> generateChutesImage(context, prompt, negativePrompt)
                    "electronhub" -> generateElectronHubImage(context, prompt, negativePrompt)
                    "nanogpt" -> generateNanoGPTImage(context, prompt, negativePrompt)
                    "bfl" -> generateBflImage(context, prompt, negativePrompt)
                    "xai" -> generateXAIImage(context, prompt, negativePrompt)
                    else -> null
                }
            }.getOrNull()
        }

    /**
     * TogetherAI（官方 src/endpoints/stable-diffusion.js together.post('/generate') 1:1，index.js L3460）：
     * POST https://api.together.xyz/v1/images/generations
     * {prompt,negative_prompt,height,width,model,steps,n=1,seed}（seed 未配置则随机 0..10^7）
     * Header Authorization: Bearer apiKey → data[0].b64_json（缺失则下载 data[0].url 转 base64）。
     */
    private suspend fun generateTogetherAIImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val cfgSeed = ServicesPrefs.imageSeed(context)
        val seed: Long = if (cfgSeed >= 0) cfgSeed else kotlin.random.Random.nextLong(0, 10_000_000)
        val payload = JSONObject()
            .put("prompt", prompt)
            .put("negative_prompt", negativePrompt)
            .put("height", ServicesPrefs.imageHeight(context))
            .put("width", ServicesPrefs.imageWidth(context))
            .put("model", ServicesPrefs.imageModel(context))
            .put("steps", ServicesPrefs.imageSteps(context))
            .put("n", 1)
            .put("seed", seed)
            .toString()
        val request = Request.Builder()
            .url("https://api.together.xyz/v1/images/generations")
            .post(payload.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val choice = JSONObject(resp.body?.string().orEmpty())
                .optJSONArray("data")?.optJSONObject(0) ?: return null
            val b64 = choice.optString("b64_json")
            if (b64.isNotBlank()) return runCatching { saveBase64(context, b64, "jpg") }.getOrNull()
            val imgUrl = choice.optString("url")
            if (imgUrl.isBlank()) return null
            val bytes = client.newCall(Request.Builder().url(imgUrl).get().build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.bytes()
            } ?: return null
            return runCatching { saveBytes(context, bytes, "jpg") }.getOrNull()
        }
    }

    /**
     * Pollinations（官方 src/endpoints/stable-diffusion.js pollinations.post('/generate') 1:1，index.js L3491）：
     * GET https://gen.pollinations.ai/image/{urlEncodedPrompt}?model=&negative_prompt=&seed=&width=&height=&enhance=
     * Header Authorization: Bearer apiKey；响应为图片原始字节，format 由 Content-Type 推断（默认 jpg）。
     * enhance 默认 false（无 ServicesPrefs 对应项）。
     */
    private suspend fun generatePollinationsImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val cfgSeed = ServicesPrefs.imageSeed(context)
        val seed: Long = if (cfgSeed >= 0) cfgSeed else kotlin.random.Random.nextLong(0, 10_000_000)
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        // 官方：path = /image/{encodeURIComponent(prompt)}，query 走 URLSearchParams（model/negative_prompt/seed/width/height）
        val qs = "?model=" + URLEncoder.encode(ServicesPrefs.imageModel(context), "UTF-8") +
            "&negative_prompt=" + URLEncoder.encode(negativePrompt, "UTF-8") +
            "&seed=" + seed +
            "&width=" + ServicesPrefs.imageWidth(context) +
            "&height=" + ServicesPrefs.imageHeight(context)
        val request = Request.Builder()
            .url("https://gen.pollinations.ai/image/$encodedPrompt$qs")
            .get()
            .header("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val bytes = body.bytes()
            val ext = mimeExt(body.contentType()?.toString() ?: "image/jpeg")
            return runCatching { saveBytes(context, bytes, ext) }.getOrNull()
        }
    }

    /**
     * Stability AI（官方 src/endpoints/stable-diffusion.js stability.post('/generate') 1:1，index.js L3711）：
     * 按 model 选 endpoint（ultra/core/sd3，未知则 core），multipart form-data
     * {prompt,negative_prompt,aspect_ratio,output_format=png,seed?,style_preset?}（undefined 省略）
     * Header Authorization: Bearer apiKey / Accept: image／；响应为图片原始字节（png）。
     * style_preset 无 ServicesPrefs 对应项，按官方 undefined 省略。
     */
    private suspend fun generateStabilityImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val model = ServicesPrefs.imageModel(context)
        val apiUrl = when (model) {
            "stable-image-ultra" -> "https://api.stability.ai/v2beta/stable-image/generate/ultra"
            "stable-diffusion-3" -> "https://api.stability.ai/v2beta/stable-image/generate/sd3"
            else -> "https://api.stability.ai/v2beta/stable-image/generate/core"
        }
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        val cfgSeed = ServicesPrefs.imageSeed(context)
        val formBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt.take(10000))
            .addFormDataPart("negative_prompt", negativePrompt.take(10000))
            .addFormDataPart("aspect_ratio", closestAspectRatio(width, height, "stability"))
            .addFormDataPart("output_format", "png")
        if (cfgSeed >= 0) formBuilder.addFormDataPart("seed", cfgSeed.toString())
        val request = Request.Builder()
            .url(apiUrl)
            .post(formBuilder.build())
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "image/*")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            return runCatching { saveBytes(context, bytes, "png") }.getOrNull()
        }
    }

    /**
     * AIMLAPI（官方 src/endpoints/stable-diffusion.js aimlapi.post('/generate-image') 1:1，index.js L4176）：
     * POST https://api.aimlapi.com/v1/images/generations，body 按 model 分支：
     *  - SD/Flux/Recraft 类（flux/、stable、recraft-v3、triposr）：{prompt,model,steps,guidance,width,height,seed?}
     *  - 其它（OpenAI 类）：{prompt,model,n=1,size}
     * Header Authorization: Bearer apiKey + 官方 AIMLAPI_HEADERS（HTTP-Referer / X-Title）。
     * 响应 images[0] 或 data[0]，优先 b64_json/base64，否则下载 url 转 base64。
     * openai_quality / openai_style 无 ServicesPrefs 对应项，省略。
     */
    private suspend fun generateAimlapiImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val fullModel = ServicesPrefs.imageModel(context)
        val model = fullModel.lowercase()
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        val cfgSeed = ServicesPrefs.imageSeed(context)
        val body = JSONObject().put("prompt", prompt).put("model", fullModel)
        val isSdLike = model.startsWith("flux/") || model.startsWith("stable") || model == "recraft-v3" || model == "triposr"
        if (isSdLike) {
            body.put("steps", clamp(ServicesPrefs.imageSteps(context), 1, 50))
            body.put("guidance", clamp(ServicesPrefs.imageScale(context), 1.5, 5.0))
            body.put("width", clamp(width, 256, 1440))
            body.put("height", clamp(height, 256, 1440))
            if (cfgSeed >= 0) body.put("seed", cfgSeed)
        } else {
            body.put("n", 1)
            body.put("size", "${width}x${height}")
        }
        val request = Request.Builder()
            .url("https://api.aimlapi.com/v1/images/generations")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://sillytavern.app")
            .header("X-Title", "SillyTavern")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val root = JSONObject(resp.body?.string().orEmpty())
            val imgObj = root.optJSONArray("images")?.optJSONObject(0)
                ?: root.optJSONArray("data")?.optJSONObject(0) ?: return null
            val b64 = imgObj.optString("b64_json").ifBlank { imgObj.optString("base64") }
            if (b64.isNotBlank()) return runCatching { saveBase64(context, b64, "png") }.getOrNull()
            val imgUrl = imgObj.optString("url")
            if (imgUrl.isBlank()) return null
            val bytes = client.newCall(Request.Builder().url(imgUrl).get().build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.bytes()
            } ?: return null
            return runCatching { saveBytes(context, bytes, "png") }.getOrNull()
        }
    }

    /**
     * Chutes（官方 src/endpoints/stable-diffusion.js chutes.post('/generate') 1:1，index.js L4369）：
     * POST https://image.chutes.ai/generate
     * {model,prompt,negative_prompt,guidance_scale,width,height,num_inference_steps}
     * Header Authorization: Bearer apiKey；响应为图片原始字节 → base64。
     */
    private suspend fun generateChutesImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val payload = JSONObject()
            .put("model", ServicesPrefs.imageModel(context))
            .put("prompt", prompt)
            .put("negative_prompt", negativePrompt)
            .put("guidance_scale", ServicesPrefs.imageScale(context))
            .put("width", ServicesPrefs.imageWidth(context))
            .put("height", ServicesPrefs.imageHeight(context))
            .put("num_inference_steps", ServicesPrefs.imageSteps(context))
            .toString()
        val request = Request.Builder()
            .url("https://image.chutes.ai/generate")
            .post(payload.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            return runCatching { saveBytes(context, bytes, "jpg") }.getOrNull()
        }
    }

    /**
     * ElectronHub（官方 src/endpoints/stable-diffusion.js electronhub.post('/generate') 1:1，index.js L4400）：
     * POST https://api.electronhub.ai/v1/images/generations
     * {model,prompt,response_format=b64_json,size?,quality?}（size 取 /v1/models/{model} 的 sizes 最近值，失败省略）
     * Header Authorization: Bearer apiKey → data[0].b64_json。
     * electronhub_quality 无 ServicesPrefs 对应项，省略。
     */
    private suspend fun generateElectronHubImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val model = ServicesPrefs.imageModel(context)
        val body = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("response_format", "b64_json")
        electronhubClosestSize(model, ServicesPrefs.imageWidth(context), ServicesPrefs.imageHeight(context))
            ?.let { body.put("size", it) }
        val request = Request.Builder()
            .url("https://api.electronhub.ai/v1/images/generations")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val b64 = JSONObject(resp.body?.string().orEmpty())
                .optJSONArray("data")?.optJSONObject(0)?.optString("b64_json") ?: return null
            if (b64.isBlank()) return null
            return runCatching { saveBase64(context, b64, "jpg") }.getOrNull()
        }
    }

    /**
     * NanoGPT（官方 src/endpoints/stable-diffusion.js nanogpt.post('/generate') 1:1，直传 index.js L4431 客户端 body）：
     * POST https://nano-gpt.com/api/generate-image
     * {model,prompt,negative_prompt,num_steps,scale,width,height,resolution,showExplicitContent=true,nImages=1}
     * Header x-api-key: apiKey → data[0].b64_json。
     */
    private suspend fun generateNanoGPTImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        val payload = JSONObject()
            .put("model", ServicesPrefs.imageModel(context))
            .put("prompt", prompt)
            .put("negative_prompt", negativePrompt)
            .put("num_steps", ServicesPrefs.imageSteps(context))
            .put("scale", ServicesPrefs.imageScale(context))
            .put("width", width)
            .put("height", height)
            .put("resolution", "${width}x${height}")
            .put("showExplicitContent", true)
            .put("nImages", 1)
            .toString()
        val request = Request.Builder()
            .url("https://nano-gpt.com/api/generate-image")
            .post(payload.toRequestBody(jsonMedia))
            .header("x-api-key", apiKey)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val b64 = JSONObject(resp.body?.string().orEmpty())
                .optJSONArray("data")?.optJSONObject(0)?.optString("b64_json") ?: return null
            if (b64.isBlank()) return null
            return runCatching { saveBase64(context, b64, "jpg") }.getOrNull()
        }
    }

    /**
     * BFL（官方 src/endpoints/stable-diffusion.js bfl.post('/generate') 1:1，异步轮询，index.js L4465）：
     * POST https://api.bfl.ml/v1/{model} → {id}；轮询 /v1/get_result?id= 每 2.5s 至 status=Ready，
     * 下载 result.sample → base64。Header x-key: apiKey。
     * body 默认 {prompt,steps,guidance,width,height,prompt_upsampling,seed,safety_tolerance=6,output_format=jpeg}，
     * model 后缀为 -ultra 时改用 aspect_ratio 并移除 steps/guidance/width/height/prompt_upsampling，
     * 后缀 -pro-1.1 时移除 steps/guidance。
     */
    private suspend fun generateBflImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val model = ServicesPrefs.imageModel(context)
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        val cfgSeed = ServicesPrefs.imageSeed(context)
        val body = JSONObject()
            .put("prompt", prompt)
            .put("steps", clamp(ServicesPrefs.imageSteps(context), 1, 50))
            .put("guidance", clamp(ServicesPrefs.imageScale(context), 1.5, 5.0))
            .put("width", clamp(width, 256, 1440))
            .put("height", clamp(height, 256, 1440))
            .put("prompt_upsampling", false)
            .put("seed", if (cfgSeed >= 0) cfgSeed else JSONObject.NULL)
            .put("safety_tolerance", 6)
            .put("output_format", "jpeg")
        when {
            model.endsWith("-ultra") -> {
                body.put("aspect_ratio", bflAspectRatio(width, height))
                body.remove("steps"); body.remove("guidance"); body.remove("width"); body.remove("height"); body.remove("prompt_upsampling")
            }
            model.endsWith("-pro-1.1") -> {
                body.remove("steps"); body.remove("guidance")
            }
        }
        val request = Request.Builder()
            .url("https://api.bfl.ml/v1/$model")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("x-key", apiKey)
            .build()
        val id = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string().orEmpty()).optString("id").ifBlank { return null }
        }
        // 官方 MAX_ATTEMPTS=100 / delay 2500ms
        repeat(100) {
            Thread.sleep(2500)
            val status = client.newCall(
                Request.Builder().url("https://api.bfl.ml/v1/get_result?id=$id").get().build(),
            ).execute().use { resp ->
                if (!resp.isSuccessful) return null
                JSONObject(resp.body?.string().orEmpty())
            }
            when (status.optString("status")) {
                "Pending" -> Unit
                "Ready" -> {
                    val sample = status.optJSONObject("result")?.optString("sample") ?: return null
                    if (sample.isBlank()) return null
                    val bytes = client.newCall(Request.Builder().url(sample).get().build()).execute().use { r ->
                        if (!r.isSuccessful) null else r.body?.bytes()
                    } ?: return null
                    return runCatching { saveBytes(context, bytes, "jpg") }.getOrNull()
                }
                else -> return null
            }
        }
        return null
    }

    /**
     * xAI（官方 src/endpoints/stable-diffusion.js xai.post('/generate') 1:1，index.js L4498）：
     * POST https://api.x.ai/v1/images/generations {prompt,model,aspect_ratio?,resolution?,response_format=b64_json}
     * （aspect_ratio/resolution 仅当 model 含 grok-imagine 时按宽高计算；resolution 阈值 1296*864 → 2k/1k）
     * Header Authorization: Bearer apiKey → data[0].b64_json（可能是 data:{mime};base64,{...}，解析 mime→ext）。
     */
    private suspend fun generateXAIImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val model = ServicesPrefs.imageModel(context)
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        val body = JSONObject()
            .put("prompt", prompt)
            .put("model", model)
            .put("response_format", "b64_json")
        if (model.contains("grok-imagine")) {
            val resolutionThreshold = 1296 * 864
            val use2k = (width * height) > resolutionThreshold
            body.put("aspect_ratio", closestAspectRatio(width, height, "xai"))
            body.put("resolution", if (use2k) "2k" else "1k")
        }
        val request = Request.Builder()
            .url("https://api.x.ai/v1/images/generations")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val encoded = JSONObject(resp.body?.string().orEmpty())
                .optJSONArray("data")?.optJSONObject(0)?.optString("b64_json") ?: return null
            if (encoded.isBlank()) return null
            // 官方：可能是 data:{mime};base64,{...} 数据 URL
            val match = Regex("^data:(.+);base64,(.+)$").find(encoded)
            val image = match?.groupValues?.get(2) ?: encoded
            val ext = match?.groupValues?.get(1)?.let { mimeExt(it) } ?: "jpg"
            return runCatching { saveBase64(context, image, ext) }.getOrNull()
        }
    }

    // ---- helpers（对齐官方 index.js / stable-diffusion.js 工具函数）----

    /** 官方 index.js getClosestAspectRatio（stability / xai 比例表 + 最近匹配）。 */
    private fun closestAspectRatio(width: Int, height: Int, source: String): String {
        val ratios: Map<String, Double> = when (source) {
            "stability" -> linkedMapOf(
                "16:9" to 16.0 / 9, "1:1" to 1.0, "21:9" to 21.0 / 9, "2:3" to 2.0 / 3,
                "3:2" to 3.0 / 2, "4:5" to 4.0 / 5, "5:4" to 5.0 / 4, "9:16" to 9.0 / 16, "9:21" to 9.0 / 21,
            )
            "xai" -> linkedMapOf(
                "1:1" to 1.0, "3:4" to 3.0 / 4, "4:3" to 4.0 / 3, "9:16" to 9.0 / 16, "16:9" to 16.0 / 9,
                "2:3" to 2.0 / 3, "3:2" to 3.0 / 2, "9:19.5" to 9.0 / 19.5, "19.5:9" to 19.5 / 9,
                "9:20" to 9.0 / 20, "20:9" to 20.0 / 9, "1:2" to 1.0 / 2, "2:1" to 2.0 / 1,
            )
            else -> linkedMapOf("1:1" to 1.0)
        }
        val aspect = width.toDouble() / height
        var best = ratios.keys.first()
        var minDiff = Math.abs(aspect - (ratios[best] ?: 1.0))
        for (key in ratios.keys) {
            val diff = Math.abs(aspect - (ratios[key] ?: 1.0))
            if (diff < minDiff) { minDiff = diff; best = key }
        }
        return best
    }

    /** 官方 stable-diffusion.js bfl.post 内 getClosestAspectRatio（BFL ultra 用，比例范围 9:21..21:9）。 */
    private fun bflAspectRatio(width: Int, height: Int): String {
        val minAspect = 9.0 / 21
        val maxAspect = 21.0 / 9
        val current = width.toDouble() / height
        fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
        fun simplify(w: Int, h: Int): String {
            val d = gcd(w.toLong(), h.toLong())
            return "${w / d.toInt()}:${h / d.toInt()}"
        }
        return when {
            current < minAspect -> simplify(width, Math.round(width / minAspect).toInt())
            current > maxAspect -> simplify(Math.round(height * maxAspect).toInt(), height)
            else -> simplify(width, height)
        }
    }

    /** 官方 index.js getClosestSize（ElectronHub：取 /v1/models/{model}.sizes 最近值）。 */
    private fun electronhubClosestSize(model: String, width: Int, height: Int): String? {
        val req = Request.Builder()
            .url("https://api.electronhub.ai/v1/models/${model.trimStart('/')}")
            .get().build()
        val sizes: List<String> = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("sizes") ?: return@use emptyList()
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }
        if (sizes.isEmpty()) return null
        val targetAspect = width.toDouble() / height
        val targetRes = (width * height).toDouble()
        var best: String? = null
        var bestDiff = Double.POSITIVE_INFINITY
        for (s in sizes) {
            val parts = s.split("x")
            if (parts.size != 2) continue
            val sw = parts[0].toIntOrNull() ?: continue
            val sh = parts[1].toIntOrNull() ?: continue
            val aspectDiff = Math.abs((sw.toDouble() / sh) - targetAspect) / targetAspect
            val resDiff = Math.abs((sw * sh).toDouble() - targetRes) / targetRes
            val diff = aspectDiff + resDiff
            if (diff < bestDiff) { bestDiff = diff; best = s }
        }
        return best
    }

    /** 官方 mime.extension 近似：Content-Type → 扩展名（默认 jpg）。 */
    private fun mimeExt(contentType: String): String {
        val lower = contentType.lowercase()
        return when {
            lower.contains("png") -> "png"
            lower.contains("webp") -> "webp"
            lower.contains("gif") -> "gif"
            lower.contains("bmp") -> "bmp"
            else -> "jpg"
        }
    }

    private fun clamp(v: Int, min: Int, max: Int): Int = v.coerceIn(min, max)
    private fun clamp(v: Double, min: Double, max: Double): Double = v.coerceIn(min, max)

    private fun saveBase64(context: Context, base64: String, ext: String): String =
        saveBytes(context, Base64.getDecoder().decode(base64), ext)

    private fun saveBytes(context: Context, bytes: ByteArray, ext: String): String {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(dir, "gen-${System.nanoTime()}.$ext")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
