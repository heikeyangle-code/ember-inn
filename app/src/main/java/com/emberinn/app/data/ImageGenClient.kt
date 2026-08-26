package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.prompt.ImageGenRequestEngine
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
 * OpenAI gpt-image、Hugging Face Inference（原始字节）、Stable Horde（异步轮询）、
 * ComfyUI（workflow JSON + /prompt + /history + /view）。DrawThings 仅 macOS，Android 不适用已移除。
 */
class ImageGenClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 返回生成的图片本地路径（失败返回 null）。 */
    suspend fun generate(
        context: Context,
        prompt: String,
        negativePrompt: String = "",
        extraPrompt: String = "",
        additionalNegativePrefix: String = "",
    ): String? = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext null
        val source = ServicesPrefs.imageSource(context)
        val url = ServicesPrefs.imageUrl(context)
        val model = ServicesPrefs.imageModel(context)
        val steps = ServicesPrefs.imageSteps(context)
        val apiKey = ServicesPrefs.imageApiKey(context)
        // 官方 generatePicture L3324-L3338：
        // prompt 链 = combine(combine(prompt_prefix, 角色前缀), 用户文本, '{prompt}')
        // 负面链 = combine(命令附加前缀, combine(negative_prompt 设置, 角色负面))
        val prefix = combinePrefixes(ServicesPrefs.imagePromptPrefix(context), extraPrompt)
        val fullPrompt = combinePrefixes(prefix, prompt, "{prompt}")
        val negativeChain = combinePrefixes(ServicesPrefs.imageNegativePrompt(context), negativePrompt)
        val fullNegative = combinePrefixes(additionalNegativePrefix, negativeChain)
        runCatching {
            when (source) {
                "openai" -> openAi(context, fullPrompt)
                "sdcpp" -> auto1111(context, url, fullPrompt, fullNegative, steps, model, sdcpp = true)
                "novel" -> novel(context, fullPrompt, fullNegative, model, apiKey)
                "huggingface" -> huggingface(context, fullPrompt, model, apiKey)
                "horde" -> horde(context, fullPrompt, fullNegative, model, apiKey)
                "vlad" -> auto1111(context, url, fullPrompt, fullNegative, steps, model, sdcpp = false) // SD.Next 同走 /sdapi/v1/txt2img（vladmandic/automatic1111 API 兼容）；登记：sd_vlad_url 字段并入 sd_url
                "comfy" -> if (ServicesPrefs.comfyType(context) == "runpod_serverless") {
                    ImageGenBackendsLlm.generate(context, "comfy_runpod", fullPrompt, fullNegative)
                } else {
                    comfy(context, url, fullPrompt, fullNegative)
                }
                "togetherai", "pollinations", "stability", "aimlapi", "chutes",
                "electronhub", "nanogpt", "bfl", "xai" ->
                    ImageGenBackendsCloud.generate(context, source, fullPrompt, fullNegative)
                "google", "zai", "openrouter", "workersai", "falai", "extras", "drawthings" ->
                    ImageGenBackendsLlm.generate(context, source, fullPrompt, fullNegative)
                else -> auto1111(context, url, fullPrompt, fullNegative, steps, null, sdcpp = false)
            }
        }.getOrNull()
    }

    /**
     * /imagine 入口（官方 applyCommandArguments index.js L5384-L5408 语义）：把命令命名参数临时
     * 写入 SD 设置，生成后在 finally 恢复原值（官方 Object.assign(extension_settings.sd, currentSettings)）。
     * settingMap 覆盖官方全部设置类参数；edit/extend/multimodal/processing 对应功能未接入——忽略。
     */
    suspend fun generateWithOverrides(
        context: Context,
        prompt: String,
        negativePrefix: String,
        args: Map<String, String>,
    ): String? = withContext(Dispatchers.IO) {
        val overrides: Map<String, Pair<String, String>> = commandSettingMap.filterKeys { it in args }
        if (overrides.isEmpty()) return@withContext generate(context, prompt, additionalNegativePrefix = negativePrefix)
        val prefs = context.getSharedPreferences("ember_services", Context.MODE_PRIVATE)
        val oldKeys = ArrayList<String>()
        val oldValues = ArrayList<Any?>()
        for ((_, spec) in overrides) {
            oldKeys.add(spec.first)
            oldValues.add(prefs.all[spec.first])
        }
        try {
            val editor = prefs.edit()
            for ((arg, spec) in overrides) {
                val key = spec.first
                val type = spec.second
                val value = args[arg] ?: continue
                when (type) {
                    "long" -> editor.putLong(key, value.toDoubleOrNull()?.toLong() ?: -1L)
                    "int" -> editor.putInt(key, value.toDoubleOrNull()?.toInt() ?: 0)
                    "float" -> editor.putFloat(key, value.toFloatOrNull() ?: 0f)
                    "bool" -> editor.putBoolean(key, value.equals("true", ignoreCase = true))
                    else -> editor.putString(key, value)
                }
            }
            editor.commit()
            generate(context, prompt, additionalNegativePrefix = negativePrefix)
        } finally {
            val restore = prefs.edit()
            for (i in oldKeys.indices) {
                val key = oldKeys[i]
                when (val v = oldValues[i]) {
                    is Int -> restore.putInt(key, v)
                    is Long -> restore.putLong(key, v)
                    is Float -> restore.putFloat(key, v)
                    is Boolean -> restore.putBoolean(key, v)
                    is String -> restore.putString(key, v)
                    else -> restore.remove(key) // 原本未写入 → 移除临时值，回落默认
                }
            }
            restore.apply()
        }
    }

    /** 官方 applyCommandArguments settingMap：命令参数名 → (prefs 键, 类型)。 */
    private val commandSettingMap: Map<String, Pair<String, String>> = mapOf(
        "seed" to Pair("sd_seed", "long"),
        "width" to Pair("sd_width", "int"),
        "height" to Pair("sd_height", "int"),
        "steps" to Pair("sd_steps", "int"),
        "cfg" to Pair("sd_scale", "float"),
        "skip" to Pair("sd_clip_skip", "int"),
        "model" to Pair("sd_model", "string"),
        "sampler" to Pair("sd_sampler", "string"),
        "scheduler" to Pair("sd_scheduler", "string"),
        "vae" to Pair("sd_vae", "string"),
        "upscaler" to Pair("sd_hr_upscaler", "string"),
        "scale" to Pair("sd_hr_scale", "float"),
        "hires" to Pair("sd_enable_hr", "bool"),
        "denoise" to Pair("sd_denoising_strength", "float"),
        "2ndpass" to Pair("sd_hr_second_pass_steps", "int"),
        "faces" to Pair("sd_restore_faces", "bool"),
    )

    /**
     * 官方 combinePrefixes（stable-diffusion/index.js L969-L984）：两端去空白与首尾逗号后 ', ' 连接；
     * [macro] 在 str1 中出现时仅首处原位替换为 str2（JS String.replace 单次替换语义），不追加；
     * str2 为空原样返回 str1。
     */
    private fun combinePrefixes(str1: String, str2: String, macro: String = ""): String {
        if (str2.isEmpty()) return str1
        fun process(s: String): String = s.trim().replace(Regex("^,|,$"), "").trim()
        val s1 = process(str1)
        val s2 = process(str2)
        val result =
            if (macro.isNotEmpty() && s1.contains(macro)) Regex(Regex.escape(macro)).replaceFirst(s1) { s2 }
            else "$s1, $s2,"
        return process(result)
    }

    /**
     * OpenAI 图像（官方客户端 generateOpenAiImage index.js L4073-L4160 逐字对齐；服务端
     * /api/openai/generate-image 把 body 原样转发 api.openai.com/v1/images/generations，openai.js L641）：
     * - model 读 ServicesPrefs.imageModel 原样；按模型族截断 prompt（dall-e-2 1000/dall-e-3 4000/
     *   gpt-image-* 32000）。
     * - size：默认 1024x1024；dall-e-3 宽高比 <1 → 高 1792、>1 → 宽 1792；gpt-image → 1536；
     *   dall-e-2 且 w,h≤512 → 512x512。
     * - quality/style/response_format/moderation 按模型族条件发送；undefined 字段不序列化。
     *   openai_quality/openai_style/openai_quality_gpt App 无 UI → 官方默认值 'standard'/'vivid'/'auto'
     *   （defaultSettings L328-L330）——登记 #16。sora-2 视频分支未接——登记偏差。
     * - key 复用 OpenAI 提供商档案（官方读 SECRET_KEYS.OPENAI）。
     */
    private fun openAi(context: Context, prompt: String): String? {
        val profile = ProviderStore(File(context.filesDir, "provider")).load()
        if (profile == null || profile.providerId != "openai" || profile.apiKey.isBlank()) return null
        val model = ServicesPrefs.imageModel(context)
        val isDalle2 = Regex("dall-e-2").containsMatchIn(model)
        val isDalle3 = Regex("dall-e-3").containsMatchIn(model)
        val isGptImg = Regex("gpt-image-(1|2|latest)").containsMatchIn(model)
        var p = prompt
        if (isDalle2 && p.length > 1000) p = p.substring(0, 1000)
        if (isDalle3 && p.length > 4000) p = p.substring(0, 4000)
        if (isGptImg && p.length > 32000) p = p.substring(0, 32000)

        var width = 1024
        var height = 1024
        val sdWidth = ServicesPrefs.imageWidth(context)
        val sdHeight = ServicesPrefs.imageHeight(context)
        val aspectRatio = sdWidth.toDouble() / sdHeight
        if (isDalle3 && aspectRatio < 1) height = 1792
        if (isDalle3 && aspectRatio > 1) width = 1792
        if (isGptImg && aspectRatio < 1) height = 1536
        if (isGptImg && aspectRatio > 1) width = 1536
        if (isDalle2 && sdWidth <= 512 && sdHeight <= 512) {
            width = 512
            height = 512
        }

        val payload = JSONObject()
            .put("prompt", p)
            .put("model", model)
            .put("size", "${width}x${height}")
            .put("n", 1)
        // 官方 undefined 字段被 JSON.stringify 丢弃 → 仅对应模型族才 put
        if (isDalle3) {
            payload.put("quality", "standard")
            payload.put("style", "vivid")
            payload.put("response_format", "b64_json")
        }
        if (isGptImg) {
            payload.put("quality", "auto")
            payload.put("moderation", "low")
        }
        if (isDalle2) payload.put("response_format", "b64_json")

        val baseUrl = profile.baseUrlOverride.ifBlank { "https://api.openai.com/v1" }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/images/generations")
            .post(payload.toString().toRequestBody(jsonMedia))
            .header("Authorization", "Bearer ${profile.apiKey}")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val root = JSONObject(resp.body?.string().orEmpty())
            val b64 = root.optJSONArray("data")?.optJSONObject(0)?.optString("b64_json") ?: return null
            return runCatching { saveBase64(context, b64, "png") }.getOrNull()
        }
    }

    /** AUTOMATIC1111 / SDCPP：POST {url}/sdapi/v1/txt2img（官方 generateAutoImage/generateSdcppImage 请求体 1:1）。 */
    private fun auto1111(
        context: Context,
        url: String,
        prompt: String,
        negativePrompt: String,
        steps: Int,
        model: String?,
        sdcpp: Boolean,
    ): String? {
        if (url.isBlank()) return null
        val settings = com.emberinn.engine.prompt.ImageGenRequestEngine.ImageGenSettings(
            sampler = ServicesPrefs.imageSampler(context),
            scheduler = ServicesPrefs.imageScheduler(context),
            steps = steps,
            scale = ServicesPrefs.imageScale(context),
            width = ServicesPrefs.imageWidth(context),
            height = ServicesPrefs.imageHeight(context),
            restoreFaces = ServicesPrefs.imageRestoreFaces(context),
            enableHr = ServicesPrefs.imageEnableHr(context),
            hrUpscaler = ServicesPrefs.imageHrUpscaler(context),
            hrScale = ServicesPrefs.imageHrScale(context),
            denoisingStrength = ServicesPrefs.imageDenoisingStrength(context),
            hrSecondPassSteps = ServicesPrefs.imageHrSecondPassSteps(context),
            seed = ServicesPrefs.imageSeed(context),
            clipSkip = ServicesPrefs.imageClipSkip(context),
            vae = ServicesPrefs.imageVae(context),
            model = model ?: "",
            adetailerFace = !sdcpp && ServicesPrefs.imageADetailerFace(context),
        )
        val payload = if (sdcpp) {
            com.emberinn.engine.prompt.ImageGenRequestEngine.sdcppPayload(settings, prompt, negativePrompt, url)
        } else {
            com.emberinn.engine.prompt.ImageGenRequestEngine.auto1111Payload(settings, prompt, negativePrompt, url)
        }.toString()
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
     * NovelAI：POST https://image.novelai.net/ai/generate-image。
     * 官方客户端 generateNovelImage（index.js L3963-L3991）+ getNovelParams（L3989-L4027）+
     * 服务端 novelai.js /generate-image（L300-L400）合并 1:1；App 直连 NAI（官方经 ST 服务端转发）：
     * - steps = min(sd.steps, 50)；sampler/scale/width/height 读 ServicesPrefs（官方读 extension_settings.sd.*）；
     *   scheduler 不在官方白名单 ['karras','native','exponential','polyexponential'] 时归一 'karras'（L2537-2539）。
     * - seed：sd_seed >= 0 原样，否则 Math.floor(random01 * 9999999999)（服务端 L330，上界 9999999998）。
     * - model 原样发送（官方 `request.body.model ?? 'nai-diffusion'` 对 '' 不兜底）。
     * - sm/sm_dyn/dynamic_thresholding(decrisper)/variety_boost(skip_cfg_above_sigma)：App 无对应开关 UI →
     *   按官方默认 false/false/null 发送；variety+ 公式 calculateSkipCfgAboveSigma（服务端 L120-L129）
     *   与 upscale 二次调用（upscale_ratio>1 → /ai/upscale）未接——登记偏差待 #16。
     */
    private fun novel(context: Context, prompt: String, negativePrompt: String, model: String, apiKey: String): String? {
        if (apiKey.isBlank()) return null
        val seedPref = ServicesPrefs.imageSeed(context)
        val seed = if (seedPref >= 0) seedPref else Math.floor(kotlin.random.Random.nextDouble() * 9999999999.0).toLong()
        val schedulers = listOf("karras", "native", "exponential", "polyexponential")
        val schedulerPref = ServicesPrefs.imageScheduler(context)
        val noiseSchedule = if (schedulerPref in schedulers) schedulerPref else "karras"
        val parameters = JSONObject()
            .put("params_version", 3)
            .put("prefer_brownian", true)
            .put("negative_prompt", negativePrompt)
            .put("height", ServicesPrefs.imageHeight(context))
            .put("width", ServicesPrefs.imageWidth(context))
            .put("scale", ServicesPrefs.imageScale(context))
            .put("seed", seed)
            .put("sampler", ServicesPrefs.imageSampler(context))
            .put("noise_schedule", noiseSchedule)
            .put("steps", ServicesPrefs.imageSteps(context).coerceAtMost(50))
            .put("n_samples", 1)
            // NAI handholding for prompts
            .put("ucPreset", 0)
            .put("qualityToggle", false)
            .put("add_original_image", false)
            .put("controlnet_strength", 1)
            .put("deliberate_euler_ancestral_bug", false)
            // 官方 dynamic_thresholding = decrisper ?? false
            .put("dynamic_thresholding", false)
            .put("legacy", false)
            .put("legacy_v3_extend", false)
            // 官方 sm/sm_dyn = novel_sm/novel_sm_dyn ?? false（ddim/v4 模型强制 false）
            .put("sm", false)
            .put("sm_dyn", false)
            .put("uncond_scale", 1)
            // 官方 variety_boost ? calculateSkipCfgAboveSigma(w,h,model) : null
            .put("skip_cfg_above_sigma", JSONObject.NULL)
            .put("use_coords", false)
            .put("characterPrompts", JSONArray())
            .put("reference_image_multiple", JSONArray())
            .put("reference_information_extracted_multiple", JSONArray())
            .put("reference_strength_multiple", JSONArray())
            .put(
                "v4_negative_prompt",
                JSONObject().put(
                    "caption",
                    JSONObject().put("base_caption", negativePrompt).put("char_captions", JSONArray()),
                ),
            )
            // 官方 use_coords/use_order 在 v4_prompt 内部（L375-377），非 parameters 顶层
            .put(
                "v4_prompt",
                JSONObject()
                    .put("caption", JSONObject().put("base_caption", prompt).put("char_captions", JSONArray()))
                    .put("use_coords", false)
                    .put("use_order", true),
            )
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
     * Stable Horde：官方客户端 generateHordeImage（index.js L3757-L3778）+ 服务端 horde.js
     * /generate-image（L309-L400）合并 1:1——截断(5000-neg-5) + sanitizeHordeImagePrompt +
     * POST /api/v2/generate/async，轮询 /check/{id}（3000ms×200）至 done，/status/{id} 取图。
     * 参数全读 ServicesPrefs（官方读 extension_settings.sd.*）：sampler/scale/steps（原样不截断）/
     * width/height/enable_hr→hires_fix/restore_faces→use_gfpgan/clip_skip；seed 仅 sd_seed>=0 时
     * 发字符串，否则省略由 Horde 随机。
     * 官方怪点：generateHordeImage 不发 karras 字段 → 服务端 Boolean(undefined)=false（非 defaultSettings
     * 的 horde_karras:true，该键用于其他后端 L3543）。sanitize 恒开（horde_sanitize 默认 true）、
     * nsfw=false（horde_nsfw 默认），App 无这两个开关 UI——登记 #16。model 原样发送无兜底。
     */
    private fun horde(context: Context, prompt: String, negativePrompt: String, model: String, apiKey: String): String? {
        val maxLength = 5000 - negativePrompt.length - 5
        val safePrompt = if (prompt.length > maxLength) prompt.substring(0, maxLength) else prompt
        val sanitized = sanitizeHordePrompt(safePrompt)
        val params = JSONObject()
            .put("sampler_name", ServicesPrefs.imageSampler(context))
            .put("hires_fix", ServicesPrefs.imageEnableHr(context))
            .put("use_gfpgan", ServicesPrefs.imageRestoreFaces(context))
            .put("cfg_scale", ServicesPrefs.imageScale(context))
            .put("steps", ServicesPrefs.imageSteps(context))
            .put("width", ServicesPrefs.imageWidth(context))
            .put("height", ServicesPrefs.imageHeight(context))
            // 官方服务端 Boolean(request.body.karras)，而客户端从不发 karras → false
            .put("karras", false)
            .put("clip_skip", ServicesPrefs.imageClipSkip(context))
        val seedPref = ServicesPrefs.imageSeed(context)
        // 官方：seed >= 0 ? String(seed) : undefined（JSON.stringify 丢弃 undefined 键）
        if (seedPref >= 0) params.put("seed", seedPref.toString())
        params.put("n", 1)
        val payload = JSONObject()
            .put("prompt", "$sanitized ### $negativePrompt")
            .put("params", params)
            .put("r2", false)
            .put("nsfw", false)
            .put("models", JSONArray().put(model))
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
     * ComfyUI 直连（官方客户端 generateComfyImageCommon stable-diffusion/index.js L4219-4300 +
     * 服务端 comfy.post('/generate') src/endpoints/stable-diffusion.js L562-630 1:1）：
     * - 占位符替换走 [ImageGenRequestEngine.replaceComfyWorkflow]（standard 全 8 项），seed 解析走
     *   [ImageGenRequestEngine.resolveComfySeed]（sd_seed>=0 原样否则随机）；参数全读 ServicesPrefs
     *   （官方读 extension_settings.sd.*）。
     * - 提交 POST {url}/prompt，body 为官方客户端模板字符串原样（index.js L4283-4286；
     *   ST 服务端把 request.body.prompt 不加改动转发给 ComfyUI /prompt）。
     * - 轮询 GET {url}/history（官方 100ms 无上限依赖中断；App 上限 6000 次 ≈10 分钟）至
     *   history[prompt_id] 出现；status.status_str=='error' 判失败。
     * - outputs 各节点 images 平铺第一张优先，整体没有再取 gifs 平铺第一张（官方 L616-617）；
     *   GET /view 下载，query 官方直接插值不做 URL 编码；扩展名 extname || 'png'。
     */
    private fun comfy(context: Context, url: String, prompt: String, negativePrompt: String): String? {
        if (url.isBlank()) return null
        val workflow = ComfyWorkflowStore(context).activeWorkflowJson()
        if (workflow.isBlank()) return null
        val replaced = ImageGenRequestEngine.replaceComfyWorkflow(
            workflow = workflow,
            runPod = false,
            prompt = prompt,
            negativePrompt = negativePrompt,
            seed = ImageGenRequestEngine.resolveComfySeed(
                ServicesPrefs.imageSeed(context),
                kotlin.random.Random.nextDouble(),
            ),
            denoisingStrength = ServicesPrefs.imageDenoisingStrength(context),
            clipSkip = ServicesPrefs.imageClipSkip(context).toDouble(),
            model = ServicesPrefs.imageModel(context),
            vae = ServicesPrefs.imageVae(context),
            sampler = ServicesPrefs.imageSampler(context),
            scheduler = ServicesPrefs.imageScheduler(context),
            steps = ServicesPrefs.imageSteps(context),
            scale = ServicesPrefs.imageScale(context),
            width = ServicesPrefs.imageWidth(context),
            height = ServicesPrefs.imageHeight(context),
        )

        // 官方模板字符串原样（index.js L4283-4286）：换行与缩进都保留在线上字节里
        val submitBody = "{\n                \"prompt\": $replaced\n            }"
        val submit = Request.Builder()
            .url(url.trimEnd('/') + "/prompt")
            .post(submitBody.toRequestBody(jsonMedia))
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
        // 官方 L616-617：images 平铺第一张优先；一张都没有才取 gifs 平铺第一张
        val nodes = mutableListOf<JSONObject>()
        outputs.keys().forEach { key -> outputs.optJSONObject(key)?.let(nodes::add) }
        val info = nodes.mapNotNull { it.optJSONArray("images") }
            .firstOrNull { it.length() > 0 }?.optJSONObject(0)
            ?: nodes.mapNotNull { it.optJSONArray("gifs") }
                .firstOrNull { it.length() > 0 }?.optJSONObject(0)
            ?: return null
        val filename = info.optString("filename")
        val subfolder = info.optString("subfolder")
        val type = info.optString("type")
        val ext = filename.substringAfterLast('.', "png").lowercase()

        // 3) 下载图片（官方 query 直接字符串插值，不做 URL 编码）
        val viewUrl = url.trimEnd('/') + "/view?filename=$filename&subfolder=$subfolder&type=$type"
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
