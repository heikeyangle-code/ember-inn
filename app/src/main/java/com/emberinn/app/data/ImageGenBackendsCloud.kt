package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.ServicesPrefs
import java.io.File
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
     * 请求体由引擎层 ImageGenRequestEngine.togetherAiPayload 构造（差分 3 例）；
     * Header Authorization: Bearer apiKey → data[0].b64_json（缺失则下载 data[0].url 转 base64）。
     */
    private suspend fun generateTogetherAIImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val cfgSeed = ServicesPrefs.imageSeed(context)
        val seed: Long = if (cfgSeed >= 0) cfgSeed else kotlin.random.Random.nextLong(0, 10_000_000)
        val payload = com.emberinn.engine.prompt.ImageGenRequestEngine.togetherAiPayload(
            prompt = prompt,
            negativePrompt = negativePrompt,
            model = ServicesPrefs.imageModel(context),
            steps = ServicesPrefs.imageSteps(context),
            width = ServicesPrefs.imageWidth(context),
            height = ServicesPrefs.imageHeight(context),
            seed = seed,
        ).toString()
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
     * enhance 读 sd_pollinations_enhance（官方 defaultSettings L345 默认 false）。
     */
    private suspend fun generatePollinationsImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val cfgSeed = ServicesPrefs.imageSeed(context)
        val seed: Long = if (cfgSeed >= 0) cfgSeed else kotlin.random.Random.nextLong(0, 10_000_000)
        // URL 由引擎层 ImageGenRequestEngine.pollinationsUrl 构造（差分 6 例，锁 path encodeURIComponent / query URLSearchParams 边界）
        val url = com.emberinn.engine.prompt.ImageGenRequestEngine.pollinationsUrl(
            prompt = prompt,
            negativePrompt = negativePrompt,
            model = ServicesPrefs.imageModel(context),
            seed = seed,
            width = ServicesPrefs.imageWidth(context),
            height = ServicesPrefs.imageHeight(context),
            enhance = ServicesPrefs.pollinationsEnhance(context),
        )
        val request = Request.Builder()
            .url(url)
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
     * 请求体由引擎层 [ImageGenRequestEngine.stabilityPayload] 构造（差分含 stabilityPayload + getClosestAspectRatio）；
     * App 仅按 model 选 endpoint、转 multipart、发请求、保存 png 字节。
     * style_preset 读 sd_stability_style_preset（官方 defaultSettings L354 默认 'anime'，客户端恒发送）。
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
        // 引擎层构造（含 aspect_ratio = getClosestAspectRatio('stability')、slice(10000)、seed<0 省略）
        val jsBody = com.emberinn.engine.prompt.ImageGenRequestEngine.stabilityPayload(
            model = model,
            prompt = prompt,
            negativePrompt = negativePrompt,
            width = width,
            height = height,
            seed = cfgSeed.toLong(),
            stylePreset = ServicesPrefs.stabilityStylePreset(context),
        )
        val payload = JSONObject(jsBody.toString()).getJSONObject("payload")
        val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        for (key in payload.keys()) {
            formBuilder.addFormDataPart(key, payload.getString(key))
        }
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
     * POST https://api.aimlapi.com/v1/images/generations
     * 请求体由引擎层 [ImageGenRequestEngine.aimlapiBody] 构造（差分含 flux/stable/recraft-v3/triposr 分支、
     * clamp 步数/CFG/尺寸、OpenAI 类 size=n=1、quality/style 省略）；
     * Header Authorization: Bearer apiKey + 官方 AIMLAPI_HEADERS（HTTP-Referer / X-Title）。
     * 响应 images[0] 或 data[0]，优先 b64_json/base64，否则下载 url 转 base64。
     * 非 SD 类模型的 quality/style 读 sd_openai_quality / sd_openai_style（官方 L4194-L4195 原样透传）。
     */
    private suspend fun generateAimlapiImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val fullModel = ServicesPrefs.imageModel(context)
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        val cfgSeed = ServicesPrefs.imageSeed(context)
        // 引擎层构造（含 isSdLike 分支、clamp(1,50)/(1.5,5)/(256,1440)、seed>=0 才加、OpenAI 类 size/n=1）
        val payload = com.emberinn.engine.prompt.ImageGenRequestEngine.aimlapiBody(
            prompt = prompt,
            model = fullModel,
            steps = ServicesPrefs.imageSteps(context),
            scale = ServicesPrefs.imageScale(context),
            width = width,
            height = height,
            seed = cfgSeed.toLong(),
            openaiQuality = ServicesPrefs.openaiQuality(context),
            openaiStyle = ServicesPrefs.openaiStyle(context),
        ).toString()
        val request = Request.Builder()
            .url("https://api.aimlapi.com/v1/images/generations")
            .post(payload.toRequestBody(jsonMedia))
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
     * 请求体由引擎层 ImageGenRequestEngine.chutesPayload 构造（差分 3 例，锁 || 短路：0→默认）；
     * Header Authorization: Bearer apiKey；响应为图片原始字节 → base64。
     */
    private suspend fun generateChutesImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val payload = com.emberinn.engine.prompt.ImageGenRequestEngine.chutesPayload(
            model = ServicesPrefs.imageModel(context),
            prompt = prompt,
            negativePrompt = negativePrompt,
            guidanceScale = ServicesPrefs.imageScale(context),
            width = ServicesPrefs.imageWidth(context),
            height = ServicesPrefs.imageHeight(context),
            steps = ServicesPrefs.imageSteps(context),
        ).toString()
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
     * 请求体由引擎层 [ImageGenRequestEngine.electronhubBody] 构造（差分含 response_format=b64_json、
     * size/quality 空省略）；size 由 App 调 [electronhubClosestSize] 网络 GET /v1/models/{model}.sizes 后
     * 调引擎层 [ImageGenRequestEngine.getClosestSize] 选最近（差分）。
     * Header Authorization: Bearer apiKey → data[0].b64_json。
     * quality 读 sd_electronhub_quality：官方服务端 String(quality||'').trim() || undefined——
     * 空串省略（官方默认 undefined），UI 选择后发送。
     */
    private suspend fun generateElectronHubImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val model = ServicesPrefs.imageModel(context)
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        // App 仅做网络取 sizes，最近值匹配由引擎层 getClosestSize 差分保证
        val sizes = electronhubFetchSizes(model)
        val size = com.emberinn.engine.prompt.ImageGenRequestEngine.getClosestSize(width, height, sizes)
        val payload = com.emberinn.engine.prompt.ImageGenRequestEngine.electronhubBody(
            model = model,
            prompt = prompt,
            size = size,
            quality = ServicesPrefs.electronhubQuality(context).trim().ifBlank { null },
        ).toString()
        val request = Request.Builder()
            .url("https://api.electronhub.ai/v1/images/generations")
            .post(payload.toRequestBody(jsonMedia))
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
     * 请求体由引擎层 [ImageGenRequestEngine.nanogptBody] 构造（差分含 parseInt/parseFloat 语义、
     * resolution 模板字符串、showExplicitContent=true、nImages=1）；
     * Header x-api-key: apiKey → data[0].b64_json。
     */
    private suspend fun generateNanoGPTImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        val payload = com.emberinn.engine.prompt.ImageGenRequestEngine.nanogptBody(
            model = ServicesPrefs.imageModel(context),
            prompt = prompt,
            negativePrompt = negativePrompt,
            steps = ServicesPrefs.imageSteps(context).toDouble(),
            scale = ServicesPrefs.imageScale(context),
            width = width.toDouble(),
            height = height.toDouble(),
        ).toString()
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
     * 请求体由引擎层 [ImageGenRequestEngine.bflBody] 构造（差分含 -ultra / -pro-1.1 分支、
     * clamp(1,50)/(1.5,5)/(256,1440)、seed=null JSON null、safety_tolerance=6、bflGetClosestAspectRatio）；
     * App 仅做轮询与下载。
     */
    private suspend fun generateBflImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val model = ServicesPrefs.imageModel(context)
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        val cfgSeed = ServicesPrefs.imageSeed(context)
        val seed: Long? = if (cfgSeed >= 0) cfgSeed.toLong() else null
        val payload = com.emberinn.engine.prompt.ImageGenRequestEngine.bflBody(
            model = model,
            prompt = prompt,
            steps = ServicesPrefs.imageSteps(context),
            scale = ServicesPrefs.imageScale(context),
            width = width,
            height = height,
            // 官方 index.js L4477：prompt_upsampling: !!sd_bfl_upsampling（默认 false）
            promptUpsampling = ServicesPrefs.bflUpsampling(context),
            seed = seed,
        ).toString()
        val request = Request.Builder()
            .url("https://api.bfl.ml/v1/$model")
            .post(payload.toRequestBody(jsonMedia))
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
     * 请求体由引擎层 [ImageGenRequestEngine.xaiBody] 构造（差分含 aspect_ratio/resolution 省略、
     * response_format=b64_json）；aspect_ratio/resolution 仅当 model 含 grok-imagine 时由
     * [ImageGenRequestEngine.getClosestAspectRatio]('xai') 计算 + 阈值 1296*864 → 2k/1k（官方 index.js）。
     * Header Authorization: Bearer apiKey → data[0].b64_json（可能是 data:{mime};base64,{...}，解析 mime→ext）。
     */
    private suspend fun generateXAIImage(context: Context, prompt: String, negativePrompt: String): String? {
        val apiKey = ServicesPrefs.imageApiKey(context)
        if (apiKey.isBlank()) return null
        val model = ServicesPrefs.imageModel(context)
        val width = ServicesPrefs.imageWidth(context)
        val height = ServicesPrefs.imageHeight(context)
        var aspectRatio: String? = null
        var resolution: String? = null
        if (model.contains("grok-imagine")) {
            val resolutionThreshold = 1296 * 864
            aspectRatio = com.emberinn.engine.prompt.ImageGenRequestEngine.getClosestAspectRatio(width, height, "xai")
            resolution = if ((width * height) > resolutionThreshold) "2k" else "1k"
        }
        val payload = com.emberinn.engine.prompt.ImageGenRequestEngine.xaiBody(
            prompt = prompt,
            model = model,
            aspectRatio = aspectRatio,
            resolution = resolution,
        ).toString()
        val request = Request.Builder()
            .url("https://api.x.ai/v1/images/generations")
            .post(payload.toRequestBody(jsonMedia))
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

    // ---- helpers（仅保留 App 层特有：网络 + 落盘；纯逻辑见引擎层 ImageGenRequestEngine）----

    /**
     * 取 ElectronHub 模型 qualities 数组（官方 ensureElectronHubQualitySelect index.js L2047-L2084：
     * GET /v1/models 后按 id 匹配且 endpoints 含 '/v1/images/generations' 的模型项取其 qualities）。
     * 无 key/模型不匹配/无 qualities → 空表（官方此时隐藏选择行并置 electronhub_quality=undefined）。
     */
    fun electronhubFetchQualities(model: String, apiKey: String): List<String> {
        if (apiKey.isBlank() || model.isBlank()) return emptyList()
        val req = Request.Builder()
            .url("https://api.electronhub.ai/v1/models")
            .get()
            .header("Authorization", "Bearer $apiKey")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val data = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data")
                ?: return@use emptyList()
            for (i in 0 until data.length()) {
                val m = data.optJSONObject(i) ?: continue
                if (m.optString("id") != model) continue
                val eps = m.optJSONArray("endpoints") ?: continue
                var supportsImages = false
                for (j in 0 until eps.length()) {
                    if (eps.optString(j) == "/v1/images/generations") supportsImages = true
                }
                if (!supportsImages) return@use emptyList()
                val qs = m.optJSONArray("qualities") ?: return@use emptyList()
                return@use (0 until qs.length()).map { qs.optString(it) }.filter { it.isNotBlank() }
            }
            emptyList()
        }
    }

    /**
     * 取 ElectronHub 模型 sizes 数组（官方 index.js 调 /v1/models/{model}.sizes 的网络部分）；
     * 最近值匹配由引擎层 [ImageGenRequestEngine.getClosestSize] 差分保证，App 不重复实现。
     */
    private fun electronhubFetchSizes(model: String): List<String> {
        val req = Request.Builder()
            .url("https://api.electronhub.ai/v1/models/${model.trimStart('/')}")
            .get().build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("sizes") ?: return@use emptyList()
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }
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

    private fun saveBase64(context: Context, base64: String, ext: String): String =
        saveBytes(context, Base64.getDecoder().decode(base64), ext)

    private fun saveBytes(context: Context, bytes: ByteArray, ext: String): String {
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(dir, "gen-${System.nanoTime()}.$ext")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
