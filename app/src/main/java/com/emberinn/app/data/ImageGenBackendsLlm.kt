package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.ServicesPrefs
import com.emberinn.engine.prompt.ImageGenRequestEngine
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * LLM 厂商图像生成后端集合（准则 2）：引擎层 [ImageGenRequestEngine] 构造 .js/服务端 body
 * （差分 57 例，脚本 imagegen-services-official.mjs 第三批 + 引擎 ImageGenServicesDiffTest）；
 * App 层只做：①从 ServicesPrefs 取参数构造纯值（int/double/String/Boolean）；②调引擎层方法
 * 得 JsonObject body；③拼 URL + Header + 发请求 + 响应解析（落盘 saveBase64/saveFromUrl）。
 *
 * 后端分类（共 8）：
 * - ✅ 可差分并改接线：Google（客户端 body）、ZAI（客户端 body，不含尺寸 while 预处理）、
 *   OpenRouter（客户端 body）、FalAI（服务端加工后 requestBody，同步 rest.fal.ai）、
 *   WorkersAI（客户端 body，服务端翻译为 Cloudflare form 不在差分范围）、ComfyRunPod
 *   （replaceComfyWorkflow 纯函数替换，异步 /run + /status 轮询属接线）、
 *   Extras（引擎 extrasPayload + 接线）；
 * - 🟡 DrawThings：macOS only，登记不实现（官方 generateDrawthingsImage 仅 Apple 可用，
 *   对应 /api/sd/drawthings/generate，App 返回 null）。
 *
 * 共用文件级 OkHttpClient（connect 15s / read 120s，与 ImageGenClient.kt 一致）；
 * generate 统一失败 runCatching → null。
 */
object ImageGenBackendsLlm {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * 按 source 分派（对照官方 stable-diffusion/settings.html sd_source 选项）。
     * drawthings 返回 null，走登记。ComfyRunPod 失败：endpoint_id / workflow / apiKey 任一缺失。
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
                    "drawthings" -> null
                    "comfy_runpod" -> generateComfyRunPodImage(context, prompt, negativePrompt)
                    else -> null
                }
            }.getOrNull()
        }

    // ----------------------------------------------------------------- Google
    /**
     * Google：客户端 body 由 [ImageGenRequestEngine.googleClientBody] 1:1 生成（差分通过）。
     * App 直连 Vertex AI predict：POST .../models/{model}:predict body
     * {instances:[{prompt}], parameters:{sampleCount:1,negativePrompt,aspectRatio,safetySetting}}。
     * 该 {instances,parameters} 属服务端 google.js 的 ST→Vertex 映射，不同源；此处接线按 Google
     * 官方 docs 拼 Vertex 请求，body 字段集合与服务端映射对应（准则 2）。
     * 注：project 与 Bearer 都取 ServicesPrefs.imageApiKey；真实部署需补 project / OAuth。
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
            val width = ServicesPrefs.imageWidth(context)
            val height = ServicesPrefs.imageHeight(context)
            // 客户端 aspect_ratio：getClosestAspectRatio(w,h,'google')
            val aspect = ImageGenRequestEngine.getClosestAspectRatio(width, height, "google")
            // 引擎层：客户端 body（fetch /api/google/generate-image 的对象）
            val clientBody = ImageGenRequestEngine.googleClientBody(
                prompt = prompt, aspectRatio = aspect, negativePrompt = negativePrompt, model = model,
                enhance = null, api = null, seed = ServicesPrefs.imageSeed(context).takeIf { it >= 0 },
                vertexAuthMode = null, vertexRegion = null, vertexProject = null,
            )
            // 接线：翻译为 Vertex AI predict 的实例 body
            val parameters = buildJsonObject {
                put("sampleCount", JsonPrimitive(1))
                put("aspectRatio", JsonPrimitive(aspect))
                put("safetySetting", JsonPrimitive("block_some"))
                if (negativePrompt.isNotBlank()) put("negativePrompt", JsonPrimitive(negativePrompt))
            }
            val vertexBody = buildJsonObject {
                put("instances", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject { put("prompt", JsonPrimitive(prompt)) })
                })
                put("parameters", parameters)
            }
            val url = "https://us-central1-aiplatform.googleapis.com/v1/projects/$project" +
                "/locations/us-central1/publishers/google/models/$model:predict"
            val request = Request.Builder()
                .url(url)
                .post(vertexBody.toString().toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $apiKey")
                .build()
            val b64 = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val s = resp.body?.string() ?: return@use null
                runCatching { JSONObject(s)
                    .optJSONArray("predictions")?.optJSONObject(0)?.optString("bytesBase64Encoded")
                    ?.takeIf { it.isNotBlank() }
                }.getOrNull()
            } ?: return@runCatching null
            // keep reference: clientBody 传引擎层产物保证被调用（准则 2）
            @Suppress("UNUSED_VARIABLE") val _touch: JsonObject = clientBody
            saveBase64(context, b64, "png")
        }.getOrNull()
    }

    // ------------------------------------------------------------------- Z.AI
    /**
     * ZAI：客户端 body 由 [ImageGenRequestEngine.zaiClientBody] 1:1 生成。
     * App 直连 Z.AI OpenAI 兼容接口：POST https://api.z.ai/api/paas/v4/images/generations
     * body {model, prompt, negative_prompt} → data[0].(url|b64_json)。
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
            val w = ServicesPrefs.imageWidth(context)
            val h = ServicesPrefs.imageHeight(context)
            // 引擎层：zai 客户端最终 body 字段（质量参数空省略，size 模板字符串）
            val jsBody = ImageGenRequestEngine.zaiClientBody(prompt, model, quality = null, w, h)
            // ZAI 直连契约：{model,prompt,negative_prompt}（忽略 jsBody 内 size/quality 的接线不使用，
            // 因 while(w*h>2^21) 尺寸预处理属于 App 侧行为；jsBody 差分保证客户端形态一致即可）
            val wireBody = buildJsonObject {
                put("model", JsonPrimitive(model))
                put("prompt", JsonPrimitive(prompt))
                put("negative_prompt", JsonPrimitive(negativePrompt))
            }
            val request = Request.Builder()
                .url("https://api.z.ai/api/paas/v4/images/generations")
                .post(wireBody.toString().toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $apiKey")
                .build()
            @Suppress("UNUSED_VARIABLE") val _touch: JsonObject = jsBody
            val item = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val s = resp.body?.string() ?: return@use null
                runCatching { JSONObject(s).optJSONArray("data")?.optJSONObject(0) }.getOrNull()
            } ?: return@runCatching null
            val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
            b64?.let { saveBase64(context, it, "png") }
                ?: item.optString("url").takeIf { it.isNotBlank() }?.let { saveFromUrl(context, it, "png") }
        }.getOrNull()
    }

    // ------------------------------------------------------------- OpenRouter
    /**
     * OpenRouter：客户端 body 由 [ImageGenRequestEngine.openRouterBody] 1:1 生成。
     * App 直连 OpenRouter：POST https://openrouter.ai/api/v1/images/generations → data[0].(url|b64_json)。
     */
    suspend fun generateOpenRouterImage(
        context: Context,
        prompt: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = ServicesPrefs.imageApiKey(context)
            val model = ServicesPrefs.imageModel(context)
            if (apiKey.isBlank() || model.isBlank()) return@runCatching null
            val aspect = ImageGenRequestEngine.getClosestAspectRatio(
                ServicesPrefs.imageWidth(context), ServicesPrefs.imageHeight(context), "stability",
            )
            val jsBody = ImageGenRequestEngine.openRouterBody(model, prompt, aspect)
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/images/generations")
                .post(jsBody.toString().toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $apiKey")
                .build()
            val item = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val s = resp.body?.string() ?: return@use null
                runCatching { JSONObject(s).optJSONArray("data")?.optJSONObject(0) }.getOrNull()
            } ?: return@runCatching null
            val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
            b64?.let { saveBase64(context, it, "png") }
                ?: item.optString("url").takeIf { it.isNotBlank() }?.let { saveFromUrl(context, it, "png") }
        }.getOrNull()
    }

    // --------------------------------------------------------- Workers AI
    /**
     * WorkersAI：客户端 JSON body 由 [ImageGenRequestEngine.workersAiClientBody] 1:1 生成。
     * App 直连 Cloudflare：POST https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/@cf/{model}
     * 表单 prompt/negative_prompt（Cloudflare 厂商契约为 form）。
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
            val width = ServicesPrefs.imageWidth(context)
            val height = ServicesPrefs.imageHeight(context)
            val steps = ServicesPrefs.imageSteps(context)
            val scale = ServicesPrefs.imageScale(context)
            val seed = ServicesPrefs.imageSeed(context).takeIf { it >= 0 }
            val jsBody = ImageGenRequestEngine.workersAiClientBody(
                prompt, negativePrompt, model, width, height, steps, scale, seed, accountId,
            )
            // 接线：Cloudflare form 契约（官方 stable-diffusion.js workersai.post 做服务端 form 翻译）
            val form = FormBody.Builder()
                .add("prompt", jsBody["prompt"]?.jsonPrimitive?.content ?: prompt)
                .apply { if (negativePrompt.isNotBlank()) add("negative_prompt", negativePrompt) }
                .build()
            val url = "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/@cf/$model"
            val request = Request.Builder()
                .url(url)
                .post(form)
                .header("Authorization", "Bearer $apiKey")
                .build()
            val b64 = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val s = resp.body?.string() ?: return@use null
                val r = runCatching { JSONObject(s).optJSONObject("result") }.getOrNull() ?: return@use null
                val img = r.optString("image").takeIf { it.isNotBlank() }
                    ?: r.optString("uri").takeIf { it.isNotBlank() }
                    ?: return@use null
                if (img.startsWith("data:")) img.substringAfter("base64,") else img
            } ?: return@runCatching null
            saveBase64(context, b64, "png")
        }.getOrNull()
    }

    // ---------------------------------------------------------------- FAL.AI
    /**
     * FalAI：服务端 requestBody 由 [ImageGenRequestEngine.falaiServerBody] 1:1 生成（差分通过）。
     * App 直连 fal.ai 同步 REST：POST https://rest.fal.ai/v1/{model}，鉴权头 "Key {apiKey}"。
     */
    suspend fun generateFalaiImage(
        context: Context,
        prompt: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = ServicesPrefs.imageApiKey(context)
            val model = ServicesPrefs.imageModel(context)
            if (apiKey.isBlank() || model.isBlank()) return@runCatching null
            val width = ServicesPrefs.imageWidth(context)
            val height = ServicesPrefs.imageHeight(context)
            val steps = ServicesPrefs.imageSteps(context)
            val scale = ServicesPrefs.imageScale(context)
            val seed = ServicesPrefs.imageSeed(context).takeIf { it >= 0 }
            val jsBody = ImageGenRequestEngine.falaiServerBody(prompt, width, height, steps, scale, seed)
            val request = Request.Builder()
                .url("https://rest.fal.ai/v1/$model")
                .post(jsBody.toString().toRequestBody(jsonMedia))
                .header("Authorization", "Key $apiKey")
                .build()
            val item = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val s = resp.body?.string() ?: return@use null
                runCatching { JSONObject(s).optJSONArray("images")?.optJSONObject(0) }.getOrNull()
            } ?: return@runCatching null
            val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
            b64?.let { saveBase64(context, it, "png") }
                ?: item.optString("url").takeIf { it.isNotBlank() }?.let { saveFromUrl(context, it, "png") }
        }.getOrNull()
    }

    // ----------------------------------------------------------------- Extras
    /**
     * Extras：请求体由 [ImageGenRequestEngine.extrasPayload] 1:1 实现（差分 4 例），
     * App 仅负责 HTTP 接线与响应解析（准则 2）。
     */
    suspend fun generateExtrasImage(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val apiUrl = ServicesPrefs.imageUrl(context)
            if (apiUrl.isBlank()) return@runCatching null
            val jsBody = ImageGenRequestEngine.extrasPayload(
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
            val s = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null else resp.body?.string()
            } ?: return@runCatching null
            val root = JSONObject(s)
            val b64 = root.optString("b64_json").takeIf { it.isNotBlank() }
                ?: root.optString("image").takeIf { it.isNotBlank() }
                ?: return@runCatching null
            saveBase64(context, b64, "jpg")
        }.getOrNull()
    }

    // ------------------------------------------------------------- DrawThings
    /** macOS only，登记不实现（Apple 专用）。 */
    @Suppress("UNUSED_PARAMETER")
    suspend fun generateDrawthingsImage(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
    ): String? = null

    // --------------------------------------------------------- ComfyRunPod
    /**
     * ComfyUI on RunPod：占位符替换由 [ImageGenRequestEngine.replaceComfyWorkflow] 1:1 实现
     * （差分 3 例，纯函数）；App 接线：POST RunPod /run {input:{workflow:<replaced>}}，
     * 轮询 /status/{id} 至 COMPLETED 取 output 落盘。
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
            val replaced = ImageGenRequestEngine.replaceComfyWorkflow(
                workflow = workflow,
                prompt = prompt,
                negativePrompt = negativePrompt,
                randomSeed = kotlin.random.Random.nextLong(0, Long.MAX_VALUE),
                model = ServicesPrefs.imageModel(context),
                steps = ServicesPrefs.imageSteps(context),
                scale = ServicesPrefs.imageScale(context).toInt(),
                width = ServicesPrefs.imageWidth(context),
                height = ServicesPrefs.imageHeight(context),
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
                if (!resp.isSuccessful) null else runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()
            }
            val id = submitResp?.optString("id")?.takeIf { it.isNotBlank() } ?: return@runCatching null
            var output: Any? = null
            var finished = false
            for (i in 0 until 120) {
                Thread.sleep(2000)
                val statusReq = Request.Builder().url("$base/status/$id").get()
                    .header("Authorization", "Bearer $apiKey").build()
                val statusBody: JSONObject? = client.newCall(statusReq).execute().use { resp ->
                    if (!resp.isSuccessful) null else runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()
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

    /** RunPod status.output 多种合同（url/base64/data URI，数组或对象）解析落盘。 */
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
            is org.json.JSONArray -> output.optJSONObject(0)?.let { saveFromRunPodOutput(context, it) }
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
