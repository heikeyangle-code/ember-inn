package com.emberinn.app.data

import android.content.Context
import com.emberinn.engine.macros.MacroEngine
import com.emberinn.engine.macros.MacroEnv
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
 * 已实现：AUTOMATIC1111（auto）、SD.Next（vlad）、stable-diffusion.cpp、DrawThings（三者同
 * /sdapi/v1/txt2img，逐源 URL/auth 见 [ServicesPrefs] sd_auto/sdcpp/vlad/drawthings_* 键）、
 * NovelAI（zip→png）、OpenAI gpt-image、Hugging Face Inference（原始字节）、Stable Horde（异步轮询）、
 * ComfyUI（workflow JSON + /prompt + /history + /view）。另含逐源「验证」[pingSource]。
 */
class ImageGenClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * 官方逐源「验证」按钮（settings.html sd_auto/sdcpp/drawthings/vlad/comfy/comfy_runpod_validate
     * + 服务端 stable-diffusion.js 各 ping 路由 1:1）。返回 null=连通，否则失败说明。
     * - auto/vlad：GET {url→整路径替换}/sdapi/v1/options + Basic auth（L31-L49）
     * - sdcpp：OPTIONS urlJoin(url,'/v1/images/generations')，无 auth（L833-L846）
     * - drawthings：HEAD {url→整路径 '/'}（L918-L932）
     * - comfy：GET urlJoin(url,'/system_stats')（L387-L400）
     * - comfy_runpod：GET urlJoin(url,'/health') + Bearer key；workers.ready<=0 仅告警仍算通（L636-L661）
     */
    suspend fun pingSource(context: Context, source: String): String? = withContext(Dispatchers.IO) {
        val raw = when (source) {
            "sdcpp" -> ServicesPrefs.sdcppUrl(context)
            "vlad" -> ServicesPrefs.vladUrl(context)
            "drawthings" -> ServicesPrefs.drawthingsUrl(context)
            "comfy" ->
                if (ServicesPrefs.comfyType(context) == "runpod_serverless") ServicesPrefs.comfyRunpodUrl(context)
                else ServicesPrefs.comfyUrl(context)
            else -> ServicesPrefs.autoUrl(context)
        }
        if (raw.isBlank()) return@withContext "URL is not set."
        runCatching {
            val base = java.net.URL(raw)
            fun joined(suffix: String) = java.net.URL(base.protocol, base.host, base.port, base.path.trimEnd('/') + suffix).toString()
            val request = when (source) {
                // new URL(url); url.pathname='/...' —— 整段路径替换（丢弃原 path）
                "auto", "vlad" -> {
                    val auth = if (source == "vlad") ServicesPrefs.vladAuth(context) else ServicesPrefs.autoAuth(context)
                    Request.Builder()
                        .url(java.net.URL(base.protocol, base.host, base.port, "/sdapi/v1/options").toString())
                        .header("Authorization", basicAuthHeader(auth))
                        .get()
                        .build()
                }
                "drawthings" -> Request.Builder()
                    .url(java.net.URL(base.protocol, base.host, base.port, "/").toString())
                    .head()
                    .build()
                "sdcpp" -> Request.Builder().url(joined("/v1/images/generations")).method("OPTIONS", null).build()
                "comfy" -> Request.Builder().url(joined("/system_stats")).get().build()
                else -> { // comfy_runpod：Bearer key 缺失 → 官方 400
                    val key = ServicesPrefs.imageApiKey(context)
                    if (key.isBlank()) return@withContext "RunPod key not found."
                    Request.Builder().url(joined("/health")).header("Authorization", "Bearer $key").get().build()
                }
            }
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext "HTTP ${resp.code}"
            }
            null
        }.getOrElse { it.message ?: "连接失败" }
    }

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
        // 官方逐源 URL（getSdRequestBody index.js L438-L447 + comfy L4314/L4333）：
        // auto/auto_auth、vlad/vlad_auth、drawthings/drawthings_auth 各自独立；sdcpp 无 auth
        val url = when (source) {
            "sdcpp" -> ServicesPrefs.sdcppUrl(context)
            "vlad" -> ServicesPrefs.vladUrl(context)
            "comfy" ->
                if (ServicesPrefs.comfyType(context) == "runpod_serverless") ServicesPrefs.comfyRunpodUrl(context)
                else ServicesPrefs.comfyUrl(context)
            else -> ServicesPrefs.autoUrl(context)
        }
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
                "sdcpp" -> auto1111(context, url, null, fullPrompt, fullNegative, steps, model, sdcpp = true)
                "novel" -> novel(context, fullPrompt, fullNegative, model, apiKey)
                "huggingface" -> huggingface(context, fullPrompt, apiKey)
                "horde" -> horde(context, fullPrompt, fullNegative, model, apiKey)
                // SD.Next 同走 /sdapi/v1/txt2img（vladmandic/automatic1111 API 兼容），带 vlad_auth Basic 头
                "vlad" -> auto1111(context, url, ServicesPrefs.vladAuth(context), fullPrompt, fullNegative, steps, model, sdcpp = false)
                "drawthings" ->
                    drawthings(
                        context,
                        ServicesPrefs.drawthingsUrl(context),
                        ServicesPrefs.drawthingsAuth(context),
                        fullPrompt,
                        fullNegative,
                    )
                "comfy" -> if (ServicesPrefs.comfyType(context) == "runpod_serverless") {
                    ImageGenBackendsLlm.generate(context, "comfy_runpod", fullPrompt, fullNegative)
                } else {
                    comfy(context, url, fullPrompt, fullNegative)
                }
                "togetherai", "pollinations", "stability", "aimlapi", "chutes",
                "electronhub", "nanogpt", "bfl", "xai" ->
                    ImageGenBackendsCloud.generate(context, source, fullPrompt, fullNegative)
                "google", "zai", "openrouter", "workersai", "falai", "extras" ->
                    ImageGenBackendsLlm.generate(context, source, fullPrompt, fullNegative)
                else -> auto1111(context, url, ServicesPrefs.autoAuth(context), fullPrompt, fullNegative, steps, null, sdcpp = false)
            }
        }.getOrNull()
    }

    /**
     * /imagine 入口（官方 applyCommandArguments index.js L5384-L5443 语义）：把命令命名参数临时
     * 写入 SD 设置，生成后在 finally 恢复原值（官方 Object.assign(extension_settings.sd, currentSettings)）。
     * settingMap 覆盖官方全部设置类参数（含 processing 枚举 standard/minimal）；bool 解析对齐
     * isTrueBoolean/isFalseBoolean。edit/extend/multimodal 对应功能未接入——忽略。
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
                    // 官方 boolean 分支：isTrueBoolean(v) || !isFalseBoolean(v)（utils.js L1011-L1022，
                    // trim+小写后 on/true/1 与 off/false/0，其余字符串一律 true）
                    "bool" -> {
                        val v = value.trim().lowercase()
                        val isTrue = v in setOf("on", "true", "1")
                        val isFalse = v in setOf("off", "false", "0")
                        editor.putBoolean(key, isTrue || !isFalse)
                    }
                    // 官方 enumHandlers.processing：/standard/i → false、/minimal/i → true、其余不覆盖
                    "processing" -> {
                        val enumValue = when {
                            Regex("standard", RegexOption.IGNORE_CASE).containsMatchIn(value) -> false
                            Regex("minimal", RegexOption.IGNORE_CASE).containsMatchIn(value) -> true
                            else -> null
                        }
                        if (enumValue != null) editor.putBoolean(key, enumValue) else continue
                    }
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
        // 官方 enumHandlers.processing（index.js L5410-L5419）：standard/minimal 枚举
        "processing" to Pair("sd_minimal_prompt_processing", "processing"),
    )

    /**
     * 官方 combinePrefixes（stable-diffusion/index.js L969-L984）：两端去空白与首尾逗号后 ', ' 连接；
     * [macro] 在 str1 中出现时仅首处原位替换为 str2（JS String.replace 单次替换语义，含替换串
     * 特殊模式 $$/$&/$`/$' 展开——见 [jsReplaceReplacement]），不追加；str2 为空原样返回 str1。
     */
    private fun combinePrefixes(str1: String, str2: String, macro: String = ""): String {
        if (str2.isEmpty()) return str1
        fun process(s: String): String = s.trim().replace(Regex("^,|,$"), "").trim()
        val s1 = process(str1)
        val s2 = process(str2)
        val result =
            // JS str1.replace(macro, str2)：字符串搜索、仅首处、字面匹配（非正则）
            if (macro.isNotEmpty()) {
                val idx = s1.indexOf(macro)
                if (idx >= 0) {
                    val replacement = jsReplaceReplacement(
                        s2,
                        matched = macro,
                        before = s1.substring(0, idx),
                        after = s1.substring(idx + macro.length),
                    )
                    s1.substring(0, idx) + replacement + s1.substring(idx + macro.length)
                } else "$s1, $s2,"
            } else "$s1, $s2,"
        return process(result)
    }

    /** JS 字符串替换的替换串特殊模式：$$→$、$&→被匹配段、$`→匹配前文、$'→匹配后文（其余字面）。 */
    private fun jsReplaceReplacement(replacement: String, matched: String, before: String, after: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            if (c == '$' && i + 1 < replacement.length) {
                when (replacement[i + 1]) {
                    '$' -> { sb.append('$'); i += 2; continue }
                    '&' -> { sb.append(matched); i += 2; continue }
                    '`' -> { sb.append(before); i += 2; continue }
                    '\'' -> { sb.append(after); i += 2; continue }
                }
            }
            sb.append(c); i += 1
        }
        return sb.toString()
    }

    /**
     * OpenAI 图像（官方客户端 generateOpenAiImage index.js L4073-L4160 逐字对齐；服务端
     * /api/openai/generate-image 把 body 原样转发 api.openai.com/v1/images/generations，openai.js L641）：
     * - model 读 ServicesPrefs.imageModel 原样；按模型族截断 prompt（dall-e-2 1000/dall-e-3 4000/
     *   gpt-image-* 32000）。
     * - size：默认 1024x1024；dall-e-3 宽高比 <1 → 高 1792、>1 → 宽 1792；gpt-image → 1536；
     *   dall-e-2 且 w,h≤512 → 512x512。
     * - quality/style/response_format/moderation 按模型族条件发送；undefined 字段不序列化。
     *   openai_quality/openai_style/openai_quality_gpt 读 ServicesPrefs（官方默认 'standard'/'vivid'/'auto'，
     *   defaultSettings L328-L330）。sora-2 视频分支未接——登记偏差。
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
        // 官方 undefined 字段被 JSON.stringify 丢弃 → 仅对应模型族才 put；
        // quality/style 读 sd_openai_quality / sd_openai_style / sd_openai_quality_gpt（默认 standard/vivid/auto）
        if (isDalle3) {
            payload.put("quality", ServicesPrefs.openaiQuality(context))
            payload.put("style", ServicesPrefs.openaiStyle(context))
            payload.put("response_format", "b64_json")
        }
        if (isGptImg) {
            payload.put("quality", ServicesPrefs.openaiQualityGpt(context))
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

    /**
     * 官方 getBasicAuthHeader（src/util.js L126-L129）：`Basic base64(auth)`——空串也发（"Basic "）。
     * auto/vlad/drawthings 恒带；sdcpp 服务端不发。
     */
    private fun basicAuthHeader(auth: String): String =
        "Basic " + android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)

    /** AUTOMATIC1111 / SDCPP：POST {url}/sdapi/v1/txt2img（官方 generateAutoImage/generateSdcppImage 请求体 1:1）。 */
    private fun auto1111(
        context: Context,
        url: String,
        auth: String?,
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
            // 官方服务端 /api/sd/generate 恒带 Basic auth（sdcpp /generate 不带）
            .apply { if (!sdcpp && auth != null) header("Authorization", basicAuthHeader(auth)) }
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val images = JSONObject(resp.body?.string().orEmpty()).optJSONArray("images") ?: return null
            val base64 = images.optString(0)
            return runCatching { saveBase64(context, base64, "png") }.getOrNull()
        }
    }

    /**
     * DrawThings（官方 generateDrawthingsImage index.js L3918-L3944 + 服务端 drawthings/generate
     * stable-diffusion.js L975-L1001 合并）：POST {url}/sdapi/v1/txt2img，body 为设置键直传
     * （引擎 [ImageGenRequestEngine.drawthingsPayload]，差分 3 例），恒带 Basic auth 头。
     */
    private fun drawthings(context: Context, url: String, auth: String, prompt: String, negativePrompt: String): String? {
        if (url.isBlank()) return null
        val settings = ImageGenRequestEngine.ImageGenSettings(
            sampler = ServicesPrefs.imageSampler(context),
            steps = ServicesPrefs.imageSteps(context),
            scale = ServicesPrefs.imageScale(context),
            width = ServicesPrefs.imageWidth(context),
            height = ServicesPrefs.imageHeight(context),
            restoreFaces = ServicesPrefs.imageRestoreFaces(context),
            enableHr = ServicesPrefs.imageEnableHr(context),
            denoisingStrength = ServicesPrefs.imageDenoisingStrength(context),
            clipSkip = ServicesPrefs.imageClipSkip(context),
            hrScale = ServicesPrefs.imageHrScale(context),
            seed = ServicesPrefs.imageSeed(context),
        )
        val payload = ImageGenRequestEngine.drawthingsPayload(settings, prompt, negativePrompt).toString()
        val request = Request.Builder()
            .url(url.trimEnd('/') + "/sdapi/v1/txt2img")
            .header("Authorization", basicAuthHeader(auth))
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
    /**
     * NovelAI 直连（官方客户端 generateNovelImage index.js L3963-L3987 + 服务端 novelai.js
     * /generate-image L300-L400 合并）：调参走引擎层 [ImageGenRequestEngine.getNovelParams]
     * （差分 7 例：steps min50 / anlas guard 尺寸步数钳制 / ddim·v4 强制关 SMEA）；
     * decrisper→dynamic_thresholding、sm/sm_dyn 透传、variety_boost→skip_cfg_above_sigma =
     * [ImageGenRequestEngine.calculateSkipCfgAboveSigma]（差分 4 例，魔数 19/58）；调度器白名单外回退
     * 'karras'（官方 getNovelParams 内的设置归一副作用等价实现）。model 原样发送（官方服务端
     * request.body.model ?? 'nai-diffusion' 对空串不触发，默认设置即发空串）。
     */
    private fun novel(context: Context, prompt: String, negativePrompt: String, model: String, apiKey: String): String? {
        if (apiKey.isBlank()) return null
        val seedPref = ServicesPrefs.imageSeed(context)
        val seed = if (seedPref >= 0) seedPref else Math.floor(kotlin.random.Random.nextDouble() * 9999999999.0).toLong()
        val schedulers = listOf("karras", "native", "exponential", "polyexponential")
        val schedulerPref = ServicesPrefs.imageScheduler(context)
        val noiseSchedule = if (schedulerPref in schedulers) schedulerPref else "karras"
        val np = ImageGenRequestEngine.getNovelParams(
            steps = ServicesPrefs.imageSteps(context),
            width = ServicesPrefs.imageWidth(context),
            height = ServicesPrefs.imageHeight(context),
            sampler = ServicesPrefs.imageSampler(context),
            model = model,
            novelSm = ServicesPrefs.novelSm(context),
            novelSmDyn = ServicesPrefs.novelSmDyn(context),
            anlasGuard = ServicesPrefs.novelAnlasGuard(context),
        )
        val varietyBoost = ServicesPrefs.novelVarietyBoost(context)
        val parameters = JSONObject()
            .put("params_version", 3)
            .put("prefer_brownian", true)
            .put("negative_prompt", negativePrompt)
            .put("height", np.height)
            .put("width", np.width)
            .put("scale", ServicesPrefs.imageScale(context))
            .put("seed", seed)
            .put("sampler", ServicesPrefs.imageSampler(context))
            .put("noise_schedule", noiseSchedule)
            .put("steps", np.steps)
            .put("n_samples", 1)
            // NAI handholding for prompts
            .put("ucPreset", 0)
            .put("qualityToggle", false)
            .put("add_original_image", false)
            .put("controlnet_strength", 1)
            .put("deliberate_euler_ancestral_bug", false)
            // 官方 dynamic_thresholding = decrisper ?? false
            .put("dynamic_thresholding", ServicesPrefs.novelDecrisper(context))
            .put("legacy", false)
            .put("legacy_v3_extend", false)
            .put("sm", np.sm)
            .put("sm_dyn", np.smDyn)
            .put("uncond_scale", 1)
            // 官方 variety_boost ? calculateSkipCfgAboveSigma(w,h,model) : null
            .put(
                "skip_cfg_above_sigma",
                if (varietyBoost) {
                    ImageGenRequestEngine.calculateSkipCfgAboveSigma(np.width, np.height, model)
                } else {
                    JSONObject.NULL
                },
            )
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
            // 官方服务端 request.body.model ?? 'nai-diffusion'：?? 对空串不触发，默认设置即发空串
            .put("model", model)
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
     * /generate-image（L309-L400）合并 1:1——截断(5000-neg-5) + 按 sd_horde_sanitize 开关做
     * sanitizeHordeImagePrompt + POST /api/v2/generate/async，轮询 /check/{id}（3000ms×200）至
     * done，/status/{id} 取图。
     * 参数全读 ServicesPrefs（官方读 extension_settings.sd.*）：sampler/scale/steps（原样不截断）/
     * width/height/enable_hr→hires_fix/restore_faces→use_gfpgan/clip_skip；seed 仅 sd_seed>=0 时
     * 发字符串，否则省略由 Horde 随机。
     * 官方怪点①：generateHordeImage 不发 karras 字段 → 服务端 Boolean(undefined)=false（非
     * defaultSettings 的 horde_karras:true，该键用于 extras 路径 L3543）。
     * 官方怪点②：客户端发 nsfw 但服务端读 request.body.nfsw（笔误，undefined）→ JSON 序列化时
     * 键被丢弃 → 直连 Horde 从不发送 nsfw。为保 1:1 App 同样省略（sd_horde_nsfw 开关仅存档）。
     * model 原样发送无兜底。
     */
    private fun horde(context: Context, prompt: String, negativePrompt: String, model: String, apiKey: String): String? {
        val maxLength = 5000 - negativePrompt.length - 5
        val safePrompt = if (prompt.length > maxLength) prompt.substring(0, maxLength) else prompt
        val sanitized =
            if (ServicesPrefs.imageHordeSanitize(context)) sanitizeHordePrompt(safePrompt) else safePrompt
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
            // 官方服务端 body.nfsw 笔误 → nsfw 键从不进入 Horde 请求（怪点②，见上）
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
            // 官方 comfy_placeholders：replace 过 substituteParams；此处用最小宏环境
            // （user=激活人格，char 名该层不可得 → {{char}} 保留字面，登记偏差）
            customPlaceholders = ServicesPrefs.comfyPlaceholders(context).map { (f, r) ->
                Pair(
                    f,
                    MacroEngine.substitute(
                        r,
                        MacroEnv(user = PersonaStore(context).active()?.name ?: "", char = ""),
                    ),
                )
            },
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
    /**
     * Hugging Face Inference（官方客户端 generateHuggingFaceImage index.js L4344-L4360 +
     * 服务端 stable-diffusion.js L1142-L1179 合并）：POST api-inference.huggingface.co/models/{model_id}
     * （model 原样插值，官方不 trim），body {inputs: prompt}，Bearer key。model_id 读独立的
     * sd_huggingface_model_id（官方 settings.html L116 独立输入框，非通用 sd_model）。
     */
    private fun huggingface(context: Context, prompt: String, apiKey: String): String? {
        if (apiKey.isBlank()) return null
        val modelId = ServicesPrefs.huggingfaceModelId(context)
        if (modelId.isBlank()) return null
        val payload = JSONObject().put("inputs", prompt).toString()
        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/$modelId")
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
