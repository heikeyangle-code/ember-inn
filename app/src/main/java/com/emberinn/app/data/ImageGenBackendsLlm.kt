package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.ServicesPrefs
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * LLM 厂商图像生成后端集合：1:1 对照官方 stable-diffusion 扩展
 * (/root/sillytavern-ref/public/scripts/extensions/stable-diffusion/index.js) 的 generateXxxImage 函数。
 *
 * 官方经 SillyTavern 代理 (/api/sd/xxx/generate 或 /api/google/generate-image 等) 转发到各厂商；
 * 本实现按任务规格直连各厂商 API（URL/请求体字段均按源码确认结果落定，差异在函数注释中标注）。
 * 落盘 helper、OkHttpClient 超时与 ImageGenClient.kt 保持一致：connect 15s / read 120s。
 * 失败统一 runCatching → null。
 */
object ImageGenBackendsLlm {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * 按 source 分派到对应 LLM 后端（对照官方 stable-diffusion/settings.html sd_source 选项）：
     * google/zai/openrouter/workersai/falai/extras/drawthings + comfy 走 runpod_serverless 模式。
     * 任一失败均返回 null；drawthings 官方为 macOS 应用，Android 端不可达，将返回 null。
     */
    suspend fun generate(context: Context, source: String, prompt: String, negativePrompt: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                when (source) {
                    "google" -> generateGoogleImage(context, prompt, negativePrompt)
                    "zai" -> generateZaiImage(context, prompt, negativePrompt)
                    "openrouter" -> generateOpenRouterImage(context, prompt)
                    "workersai" -> generateWorkersAIImage(context, prompt, negativePrompt)
                    "falai" -> generateFalaiImage(context, prompt)
                    "extras" -> generateExtrasImage(context, prompt, negativePrompt)
                    "drawthings" -> generateDrawthingsImage(context, prompt, negativePrompt)
                    "comfy_runpod" -> generateComfyRunPodImage(context, prompt, negativePrompt)
                    else -> null
                }
            }.getOrNull()
        }

    // ----------------------------------------------------------------- Google
    /**
     * Google Vertex AI（index.js L4570 generateGoogleImage，image 分支，非 veo）。
     * 官方代理 /api/google/generate-image 转发 body {prompt, aspect_ratio, negative_prompt, model,
     * enhance, api, seed, vertexai_auth_mode, vertexai_region, vertexai_express_project_id}；
     * 本实现直连 Vertex AI predict：POST .../models/{model}:predict，
     * body {instances:[{prompt}], parameters:{sampleCount:1, negativePrompt, aspectRatio, safetySetting}}，
     * 响应 predictions[0].bytesBase64Encoded（base64 PNG）。
     *
     * 注：ServicesPrefs 暂无独立 Google project / OAuth 字段，这里 project 与 Bearer 均取 imageApiKey；
     * 真实部署需补 project_id / OAuth token 字段，配置不符即返回 null。
     */
    suspend fun generateGoogleImage(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = ServicesPrefs.imageApiKey(context)
            val model = ServicesPrefs.imageModel(context).ifBlank { "imagegeneration@005" }
            if (apiKey.isBlank()) return@runCatching null
            val project = apiKey
            val aspect = closestGoogleAspect(
                ServicesPrefs.imageWidth(context),
                ServicesPrefs.imageHeight(context),
            )
            val parameters = JSONObject()
                .put("sampleCount", 1)
                .put("aspectRatio", aspect)
                .put("safetySetting", "block_some")
            if (negativePrompt.isNotBlank()) parameters.put("negativePrompt", negativePrompt)
            val payload = JSONObject()
                .put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
                .put("parameters", parameters)
                .toString()
            val url = "https://us-central1-aiplatform.googleapis.com/v1/projects/$project" +
                "/locations/us-central1/publishers/google/models/$model:predict"
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $apiKey")
                .build()
            val body: String? = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
            if (body.isNullOrBlank()) return@runCatching null
            val b64 = JSONObject(body)
                .optJSONArray("predictions")?.optJSONObject(0)?.optString("bytesBase64Encoded")
                ?.takeIf { it.isNotBlank() } ?: return@runCatching null
            saveBase64(context, b64, "png")
        }.getOrNull()
    }

    // ------------------------------------------------------------------- Z.AI
    /**
     * Z.AI（index.js L4636 generateZaiImage，image 分支，非 cogvideox/vidu）。
     * 官方代理 /api/sd/zai/generate 转发 body {prompt, model, quality, size}；
     * 本实现直连 Z.AI OpenAI 兼容接口：POST https://api.z.ai/api/paas/v4/images/generations，
     * body {model, prompt, negative_prompt}，响应 data[0].url（下载）或 data[0].b64_json。
     */
    suspend fun generateZaiImage(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = ServicesPrefs.imageApiKey(context)
            val model = ServicesPrefs.imageModel(context).ifBlank { "cogview-3" }
            if (apiKey.isBlank()) return@runCatching null
            val payload = JSONObject()
                .put("model", model)
                .put("prompt", prompt)
                .put("negative_prompt", negativePrompt)
                .toString()
            val request = Request.Builder()
                .url("https://api.z.ai/api/paas/v4/images/generations")
                .post(payload.toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $apiKey")
                .build()
            val body: String? = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
            if (body.isNullOrBlank()) return@runCatching null
            val item = JSONObject(body).optJSONArray("data")?.optJSONObject(0)
                ?: return@runCatching null
            val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
            b64?.let { saveBase64(context, it, "png") }
                ?: item.optString("url").takeIf { it.isNotBlank() }?.let { saveFromUrl(context, it, "png") }
        }.getOrNull()
    }

    // ------------------------------------------------------------- OpenRouter
    /**
     * OpenRouter（index.js L4716 generateOpenRouterImage）。
     * 官方代理 /api/openrouter/image/generate 转发 body {model, prompt, aspect_ratio}；
     * 本实现直连 OpenRouter：POST https://openrouter.ai/api/v1/images/generations，
     * body {model, prompt}，响应 data[0].url（下载）或 data[0].b64_json。
     */
    suspend fun generateOpenRouterImage(
        context: Context,
        prompt: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = ServicesPrefs.imageApiKey(context)
            val model = ServicesPrefs.imageModel(context)
            if (apiKey.isBlank() || model.isBlank()) return@runCatching null
            val payload = JSONObject()
                .put("model", model)
                .put("prompt", prompt)
                .toString()
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/images/generations")
                .post(payload.toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $apiKey")
                .build()
            val body: String? = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
            if (body.isNullOrBlank()) return@runCatching null
            val item = JSONObject(body).optJSONArray("data")?.optJSONObject(0)
                ?: return@runCatching null
            val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
            b64?.let { saveBase64(context, it, "png") }
                ?: item.optString("url").takeIf { it.isNotBlank() }?.let { saveFromUrl(context, it, "png") }
        }.getOrNull()
    }

    // --------------------------------------------------------- Workers AI
    /**
     * Cloudflare Workers AI（index.js L4737 generateWorkersAIImage）。
     * 官方代理 /api/sd/workersai/generate 转发 body {prompt, negative_prompt, model, width, height,
     * steps, scale, seed, account_id}；本实现直连 Cloudflare：
     * POST https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/@cf/{model}，
     * 表单 prompt(+negative_prompt)，响应 result.image（base64，亦可为 result.uri 的 data URI）。
     *
     * 注：ServicesPrefs 暂无独立 account_id 字段，这里 account_id 与 Bearer 均取 imageApiKey；
     * 真实部署需补 account_id 字段。Cloudflare 同时接受 JSON，但任务规格要求 form。
     */
    suspend fun generateWorkersAIImage(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = ServicesPrefs.imageApiKey(context)
            val model = ServicesPrefs.imageModel(context).removePrefix("@cf/")
            if (apiKey.isBlank() || model.isBlank()) return@runCatching null
            val accountId = apiKey
            val form = FormBody.Builder()
                .add("prompt", prompt)
                .apply { if (negativePrompt.isNotBlank()) add("negative_prompt", negativePrompt) }
                .build()
            val url = "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/@cf/$model"
            val request = Request.Builder()
                .url(url)
                .post(form)
                .header("Authorization", "Bearer $apiKey")
                .build()
            val body: String? = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
            if (body.isNullOrBlank()) return@runCatching null
            val result = JSONObject(body).optJSONObject("result") ?: return@runCatching null
            val image = result.optString("image").takeIf { it.isNotBlank() }
                ?: result.optString("uri").takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val b64 = if (image.startsWith("data:")) image.substringAfter("base64,") else image
            saveBase64(context, b64, "png")
        }.getOrNull()
    }

    // ---------------------------------------------------------------- FAL.AI
    /**
     * FAL.AI（index.js L4537 generateFalaiImage）。
     * 官方代理 /api/sd/falai/generate 转发 body {prompt, negative_prompt, model, steps, guidance,
     * width, height, seed}；本实现直连 fal.ai 同步 REST：POST https://rest.fal.ai/v1/{model}，
     * body {prompt, image_size:{width,height}, num_images:1, enable_safety_checker:false}，
     * 响应 images[0].url（下载）或 images[0].b64_json。
     * 注：fal.ai 鉴权头为 "Authorization: Key {apiKey}"。
     */
    suspend fun generateFalaiImage(
        context: Context,
        prompt: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = ServicesPrefs.imageApiKey(context)
            val model = ServicesPrefs.imageModel(context)
            if (apiKey.isBlank() || model.isBlank()) return@runCatching null
            val imageSize = JSONObject()
                .put("width", ServicesPrefs.imageWidth(context))
                .put("height", ServicesPrefs.imageHeight(context))
            val payload = JSONObject()
                .put("prompt", prompt)
                .put("image_size", imageSize)
                .put("num_images", 1)
                .put("enable_safety_checker", false)
                .toString()
            val request = Request.Builder()
                .url("https://rest.fal.ai/v1/$model")
                .post(payload.toRequestBody(jsonMedia))
                .header("Authorization", "Key $apiKey")
                .build()
            val body: String? = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
            if (body.isNullOrBlank()) return@runCatching null
            val item = JSONObject(body).optJSONArray("images")?.optJSONObject(0)
                ?: return@runCatching null
            val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
            b64?.let { saveBase64(context, it, "png") }
                ?: item.optString("url").takeIf { it.isNotBlank() }?.let { saveFromUrl(context, it, "png") }
        }.getOrNull()
    }

    // ----------------------------------------------------------------- Extras
    /**
     * Extras（index.js L3524 generateExtrasImage）。请求体构造由引擎层
     * [com.emberinn.engine.prompt.ImageGenRequestEngine.extrasPayload] 1:1 实现（差分 4 例），
     * App 仅负责 HTTP 接线与响应解析（准则 2）。
     *
     * 官方 doExtrasFetch POST {apiUrl}/api/image，body 见 extrasPayload；响应 {image: base64}（jpg）。
     * horde_karras 官方默认 false（ServicesPrefs 无此字段，按官方默认 false 等价）。
     * 响应优先取 b64_json，回退 image（jpg）。
     */
    suspend fun generateExtrasImage(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiUrl = ServicesPrefs.imageUrl(context)
            if (apiUrl.isBlank()) return@runCatching null
            val jsBody = com.emberinn.engine.prompt.ImageGenRequestEngine.extrasPayload(
                prompt = prompt,
                negativePrompt = negativePrompt,
                sampler = ServicesPrefs.imageSampler(context),
                steps = ServicesPrefs.imageSteps(context),
                scale = ServicesPrefs.imageScale(context),
                width = ServicesPrefs.imageWidth(context),
                height = ServicesPrefs.imageHeight(context),
                restoreFaces = ServicesPrefs.imageRestoreFaces(context),
                enableHr = ServicesPrefs.imageEnableHr(context),
                hordeKarras = false,
                hrUpscaler = ServicesPrefs.imageHrUpscaler(context),
                hrScale = ServicesPrefs.imageHrScale(context),
                denoisingStrength = ServicesPrefs.imageDenoisingStrength(context),
                hrSecondPassSteps = ServicesPrefs.imageHrSecondPassSteps(context),
                seed = ServicesPrefs.imageSeed(context),
            )
            val request = Request.Builder()
                .url(apiUrl.trimEnd('/') + "/api/image")
                .post(jsBody.toString().toRequestBody(jsonMedia))
                .apply {
                    val key = ServicesPrefs.imageApiKey(context)
                    if (key.isNotBlank()) header("Authorization", "Bearer $key")
                }
                .build()
            val body: String? = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
            if (body.isNullOrBlank()) return@runCatching null
            val root = JSONObject(body)
            val b64 = root.optString("b64_json").takeIf { it.isNotBlank() }
                ?: root.optString("image").takeIf { it.isNotBlank() }
                ?: return@runCatching null
            saveBase64(context, b64, "jpg")
        }.getOrNull()
    }

    // ------------------------------------------------------------- DrawThings
    /**
     * DrawThings（index.js L3922 generateDrawthingsImage）。
     * DrawThings 仅 macOS，Android 不适用已跳过。
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun generateDrawthingsImage(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
    ): String? = null

    // --------------------------------------------------------- ComfyRunPod
    /**
     * ComfyUI on RunPod（index.js L4325 generateComfyRunPodImage）。
     * 官方 generateComfyImageCommon(prompt, neg, signal, '/api/sd/comfyrunpod',
     * [steps,scale,width,height], comfy_runpod_url)，由 SillyTavern 服务端转发到 RunPod。
     * 本实现直连 RunPod serverless：POST https://api.runpod.ai/v2/{endpoint_id}/run，
     * body {input:{workflow:<replaced>}}，轮询 /status/{id} 至 COMPLETED，取 output 中的图片（base64 或 url）。
     *
     * 注：endpoint_id 取自 imageUrl 末段（无独立字段）；workflow 取自 comfyWorkflow；
     * RunPod ComfyUI 模板的 input 合同因模板而异，此处用 {input:{workflow:<object>}}，
     * 若模板不符或 endpoint_id 缺失则返回 null。任务文中 "runless" 解读为标准异步 /run 端点 + /status/{id} 轮询。
     */
    suspend fun generateComfyRunPodImage(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = ServicesPrefs.imageApiKey(context)
            val endpointId = ServicesPrefs.imageUrl(context).trimEnd('/').substringAfterLast('/')
            val workflow = ServicesPrefs.comfyWorkflow(context)
            if (apiKey.isBlank() || endpointId.isBlank() || workflow.isBlank()) return@runCatching null
            val replaced = replaceComfyWorkflow(
                workflow,
                prompt,
                negativePrompt,
                ServicesPrefs.imageModel(context),
                ServicesPrefs.imageSteps(context),
                ServicesPrefs.imageScale(context).toInt(),
                ServicesPrefs.imageWidth(context),
                ServicesPrefs.imageHeight(context),
            )
            val workflowObj = runCatching { JSONObject(replaced) }.getOrNull()
                ?: return@runCatching null
            val payload = JSONObject().put("input", JSONObject().put("workflow", workflowObj)).toString()
            val base = "https://api.runpod.ai/v2/$endpointId"
            val submit = Request.Builder()
                .url("$base/run")
                .post(payload.toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $apiKey")
                .build()
            val submitResp: JSONObject? = client.newCall(submit).execute().use { resp ->
                if (!resp.isSuccessful) null else JSONObject(resp.body?.string().orEmpty())
            }
            val id = submitResp?.optString("id")?.takeIf { it.isNotBlank() } ?: return@runCatching null
            // 轮询 /status/{id}（官方 comfy 轮询 100ms；RunPod 任务通常秒级，这里 2s × 120 ≈ 4 分钟）
            var output: Any? = null
            var finished = false
            for (i in 0 until 120) {
                Thread.sleep(2000)
                val statusReq = Request.Builder().url("$base/status/$id").get()
                    .header("Authorization", "Bearer $apiKey").build()
                val statusBody: JSONObject? = client.newCall(statusReq).execute().use { resp ->
                    if (!resp.isSuccessful) null else JSONObject(resp.body?.string().orEmpty())
                }
                if (statusBody == null) continue
                when (statusBody.optString("status")) {
                    "COMPLETED" -> { output = statusBody.opt("output"); finished = true }
                    "FAILED", "CANCELLED" -> { finished = true }
                }
                if (finished) break
            }
            if (!finished) return@runCatching null
            saveFromRunPodOutput(context, output)
        }.getOrNull()
    }

    // ----------------------------------------------------------- helpers

    /** 官方 getClosestAspectRatio（google 集，index.js L3583）：1:1 / 16:9 / 9:16 / 4:3 / 3:4。 */
    private fun closestGoogleAspect(width: Int, height: Int): String {
        val ratios = listOf(
            "1:1" to 1.0,
            "16:9" to 16.0 / 9,
            "9:16" to 9.0 / 16,
            "4:3" to 4.0 / 3,
            "3:4" to 3.0 / 4,
        )
        val target = width.toDouble() / height.toDouble()
        return ratios.minByOrNull { Math.abs(it.second - target) }?.first ?: "1:1"
    }

    /**
     * ComfyUI workflow 占位符替换（对齐 ImageGenClient.comfy 的替换集合，
     * 含官方 generateComfyImageCommon 的 prompt/negative/seed/model 及 runpod 占位 steps/scale/width/height）。
     */
    private fun replaceComfyWorkflow(
        workflow: String,
        prompt: String,
        negativePrompt: String,
        model: String,
        steps: Int,
        scale: Int,
        width: Int,
        height: Int,
    ): String {
        val seed = kotlin.random.Random.nextLong(0, Long.MAX_VALUE).toString()
        val promptVal = JSONObject().put("v", prompt).toString().removePrefix("{\"v\":").removeSuffix("}")
        val negVal = JSONObject().put("v", negativePrompt).toString().removePrefix("{\"v\":").removeSuffix("}")
        val modelVal = JSONObject().put("v", model.ifBlank { "v1-5-pruned-emaonly.safetensors" })
            .toString().removePrefix("{\"v\":").removeSuffix("}")
        return workflow
            .replace("%prompt%", promptVal)
            .replace("%negative_prompt%", negVal)
            .replace("%seed%", "\"$seed\"")
            .replace("%model%", modelVal)
            .replace("%steps%", steps.toString())
            .replace("%scale%", scale.toString())
            .replace("%width%", width.toString())
            .replace("%height%", height.toString())
    }

    /** 从 RunPod status 的 output 提取并落盘图片（base64 / data URI / url，多种合同）。 */
    private fun saveFromRunPodOutput(context: Context, output: Any?): String? {
        return when (output) {
            is String -> when {
                output.startsWith("http", ignoreCase = true) -> saveFromUrl(context, output, "png")
                output.startsWith("data:") -> saveBase64(context, output.substringAfter("base64,"), "png")
                else -> saveBase64(context, output, "png")
            }
            is JSONObject -> {
                val img = output.optJSONArray("images")?.optJSONObject(0)
                if (img != null) {
                    img.optString("url").takeIf { it.isNotBlank() }?.let { saveFromUrl(context, it, "png") }
                        ?: img.optString("b64_json").takeIf { it.isNotBlank() }?.let { saveBase64(context, it, "png") }
                } else {
                    output.optString("b64_json").takeIf { it.isNotBlank() }?.let { saveBase64(context, it, "png") }
                        ?: output.optString("image").takeIf { it.isNotBlank() }?.let { saveBase64(context, it, "png") }
                        ?: output.optString("url").takeIf { it.isNotBlank() }?.let { saveFromUrl(context, it, "png") }
                }
            }
            is JSONArray -> output.optJSONObject(0)?.let { saveFromRunPodOutput(context, it) }
            else -> null
        }
    }

    private fun saveFromUrl(context: Context, url: String, ext: String): String? {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) null
            else resp.body?.bytes()?.let { saveBytes(context, it, ext) }
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
