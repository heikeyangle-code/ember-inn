package com.emberinn.engine.prompt

import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 官方 stable-diffusion 扩展请求体（extensions/stable-diffusion/index.js generateAutoImage /
 * generateSdcppImage 1:1）。差分：scripts/diff/imagegen-official.mjs（12 例，含 ADetailer alwayson_scripts）。
 * 边界（登记）：vlad/drawthings/novel/openai/horde/hf/comfy 等其余后端另开差分。
 */
object ImageGenRequestEngine {

    data class ImageGenSettings(
        val sampler: String = "DDIM",
        val scheduler: String = "normal",
        val steps: Int = 20,
        val scale: Double = 7.0,
        val width: Int = 512,
        val height: Int = 512,
        val restoreFaces: Boolean = false,
        val enableHr: Boolean = false,
        val hrUpscaler: String = "Latent",
        val hrScale: Double = 1.0,
        val denoisingStrength: Double = 0.7,
        val hrSecondPassSteps: Int = 0,
        val seed: Long = -1,
        val clipSkip: Int? = 1,
        val vae: String = "",
        val model: String = "",
        val adetailerFace: Boolean = false,
    )

    /** 官方 generateAutoImage payload（JSON.stringify 语义：seed<0/非法 vae 的 undefined 键省略）。 */
    fun auto1111Payload(settings: ImageGenSettings, prompt: String, negativePrompt: String, url: String = "http://localhost:7860"): JsonObject {
        val isValidVae = settings.vae.isNotBlank() && settings.vae != "N/A" && settings.vae != "Automatic"
        return buildJsonObject {
            put("url", JsonPrimitive(url))
            put("prompt", JsonPrimitive(prompt))
            put("negative_prompt", JsonPrimitive(negativePrompt))
            put("sampler_name", JsonPrimitive(settings.sampler))
            put("scheduler", JsonPrimitive(settings.scheduler))
            put("steps", JsonPrimitive(settings.steps))
            put("cfg_scale", num(settings.scale))
            put("width", JsonPrimitive(settings.width))
            put("height", JsonPrimitive(settings.height))
            put("restore_faces", JsonPrimitive(settings.restoreFaces))
            put("enable_hr", JsonPrimitive(settings.enableHr))
            put("hr_upscaler", JsonPrimitive(settings.hrUpscaler))
            put("hr_scale", num(settings.hrScale))
            put("hr_additional_modules", JsonArray(emptyList()))
            put("denoising_strength", num(settings.denoisingStrength))
            put("hr_second_pass_steps", JsonPrimitive(settings.hrSecondPassSteps))
            if (settings.seed >= 0) put("seed", JsonPrimitive(settings.seed))
            put(
                "override_settings",
                buildJsonObject {
                    put("CLIP_stop_at_last_layers", JsonPrimitive(settings.clipSkip ?: 1))
                    if (isValidVae) {
                        put("sd_vae", JsonPrimitive(settings.vae))
                        put("forge_additional_modules", JsonArray(listOf(JsonPrimitive(settings.vae))))
                    }
                },
            )
            put("override_settings_restore_afterwards", JsonPrimitive(true))
            put("clip_skip", JsonPrimitive(settings.clipSkip ?: 1))
            put("save_images", JsonPrimitive(true))
            put("send_images", JsonPrimitive(true))
            put("do_not_save_grid", JsonPrimitive(false))
            put("do_not_save_samples", JsonPrimitive(false))
            if (settings.adetailerFace) {
                put(
                    "alwayson_scripts",
                    buildJsonObject {
                        put(
                            "ADetailer",
                            buildJsonObject {
                                put(
                                    "args",
                                    JsonArray(
                                        listOf(
                                            JsonPrimitive(true), // ad_enable
                                            JsonPrimitive(true), // skip_img2img
                                            buildJsonObject { put("ad_model", JsonPrimitive("face_yolov8n.pt")) },
                                        ),
                                    ),
                                )
                            },
                        )
                    },
                )
            }
        }
    }

    /** 官方 generateSdcppImage payload。 */
    fun sdcppPayload(settings: ImageGenSettings, prompt: String, negativePrompt: String, url: String = "http://127.0.0.1:1234"): JsonObject {
        return buildJsonObject {
            put("url", JsonPrimitive(url))
            if (settings.model.isNotBlank()) put("model", JsonPrimitive(settings.model))
            put("prompt", JsonPrimitive(prompt))
            put("negative_prompt", JsonPrimitive(negativePrompt))
            put("steps", JsonPrimitive(settings.steps))
            put("cfg_scale", num(settings.scale))
            put("width", JsonPrimitive(settings.width))
            put("height", JsonPrimitive(settings.height))
            put("batch_size", JsonPrimitive(1))
            if (settings.seed >= 0) put("seed", JsonPrimitive(settings.seed))
            if (settings.sampler.isNotBlank() && settings.sampler != "N/A") put("sampler_name", JsonPrimitive(settings.sampler))
            if (settings.scheduler.isNotBlank() && settings.scheduler != "N/A") put("scheduler", JsonPrimitive(settings.scheduler))
            if (settings.clipSkip != null) put("clip_skip", JsonPrimitive(settings.clipSkip))
        }
    }

    /**
     * 官方 generateDrawthingsImage 客户端 body（index.js L3918-L3944 1:1；url/auth 两键由调用方附加，
     * 模拟服务端 spread 后 delete url/auth 再转发）。upscaler_scale = hr_scale；seed<0 → undefined
     * （JSON 省略）；无 scheduler/hr 细分/override_settings——官方 TODO 注明 advanced API 未接。
     */
    fun drawthingsPayload(settings: ImageGenSettings, prompt: String, negativePrompt: String): JsonObject =
        buildJsonObject {
            put("prompt", JsonPrimitive(prompt))
            put("negative_prompt", JsonPrimitive(negativePrompt))
            put("sampler_name", JsonPrimitive(settings.sampler))
            put("steps", JsonPrimitive(settings.steps))
            put("cfg_scale", num(settings.scale))
            put("width", JsonPrimitive(settings.width))
            put("height", JsonPrimitive(settings.height))
            put("restore_faces", JsonPrimitive(settings.restoreFaces))
            put("enable_hr", JsonPrimitive(settings.enableHr))
            put("denoising_strength", num(settings.denoisingStrength))
            settings.clipSkip?.let { put("clip_skip", JsonPrimitive(it)) }
            put("upscaler_scale", num(settings.hrScale))
            if (settings.seed >= 0) put("seed", JsonPrimitive(settings.seed))
        }

    // ---------- services 后端（src/endpoints/stable-diffusion.js 各 <backend>.post('/generate')） ----------
    // 差分：scripts/diff/imagegen-services-official.mjs（12 例：together 3 / pollinations 6 / chutes 3）。
    // 边界（不差分，登记）：stability multipart form-data、aimlapi/electronhub/nanogpt/xai body 简单另开、
    // bfl 异步轮询属行为差分（非纯）、drawthings/comfyrunpod 等 LLM 后端另开。

    /**
     * 官方 TogetherAI 服务端 body（stable-diffusion.js L788-L799 1:1）。
     * 调用方在 seed<0 时自行替换为随机 long（0..10^7）；本方法只做构造，不调随机保证差分确定性。
     */
    fun togetherAiPayload(prompt: String, negativePrompt: String, model: String, steps: Int, width: Int, height: Int, seed: Long): JsonObject =
        buildJsonObject {
            put("prompt", JsonPrimitive(prompt))
            put("negative_prompt", JsonPrimitive(negativePrompt))
            put("height", JsonPrimitive(height))
            put("width", JsonPrimitive(width))
            put("model", JsonPrimitive(model))
            put("steps", JsonPrimitive(steps))
            put("n", JsonPrimitive(1))
            put("seed", JsonPrimitive(if (seed >= 0) seed else 0L))
        }

    /**
     * 官方 Pollinations 服务端 URL（L1045-L1056 1:1）。
     * path 段：encodeURIComponent 语义（space=%20，!*'() 不编码）—— Kotlin 用 URLEncoder.encode 后将 + 替回 %20，
     * 再把被 URLEncoder 多编码的 !*'() 解码回来，使输出逐字等于 JS encodeURIComponent。
     * query 段：URLSearchParams 语义（form-urlencoded，space=+）—— Kotlin URLEncoder.encode 直接对齐。
     * 调用方在 seed<0 时自行替换为随机 long；width/height null=1024 默认；enhance=false 不追加。
     */
    fun pollinationsUrl(prompt: String, negativePrompt: String, model: String, seed: Long, width: Int?, height: Int?, enhance: Boolean): String {
        val path = encodeURIComponent(prompt)
        val w = width ?: 1024
        val h = height ?: 1024
        val s = if (seed >= 0) seed else 0L
        val query = StringBuilder()
            .append("model=").append(formEncode(model))
            .append("&negative_prompt=").append(formEncode(negativePrompt))
            .append("&seed=").append(s)
            .append("&width=").append(w)
            .append("&height=").append(h)
        if (enhance) query.append("&enhance=true")
        return "https://gen.pollinations.ai/image/$path?$query"
    }

    /**
     * 官方 Chutes 服务端 body（L1356-L1364 1:1）。
     * JS `||` 短路语义：0/undefined/null 替换为默认（guidance_scale=7.0/width=1024/height=1024/steps=10）。
     * 注意 guidance_scale=0 也会被替换为 7.0（JS falsy）—— 内部把 0 视同 null。
     */
    fun chutesPayload(model: String, prompt: String, negativePrompt: String, guidanceScale: Double?, width: Int?, height: Int?, steps: Int?): JsonObject {
        val gs = guidanceScale?.takeIf { it != 0.0 } ?: 7.0
        val w = width?.takeIf { it != 0 } ?: 1024
        val h = height?.takeIf { it != 0 } ?: 1024
        val st = steps?.takeIf { it != 0 } ?: 10
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("prompt", JsonPrimitive(prompt))
            put("negative_prompt", JsonPrimitive(negativePrompt))
            put("guidance_scale", num(gs))
            put("width", JsonPrimitive(w))
            put("height", JsonPrimitive(h))
            put("num_inference_steps", JsonPrimitive(st))
        }
    }

    /**
     * 官方 getClosestAspectRatio（index.js L3568-L3635 1:1，纯函数）。
     * stability/xai 比例表 + 最近匹配；遍历顺序与 Object.keys 一致（Kotlin linkedMapOf 保持插入顺序）。
     * 严格 `diff < minDiff`：相等时不更新（保留先出现的）。
     */
    fun getClosestAspectRatio(width: Int, height: Int, source: String): String {
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
            if (diff < minDiff) {
                minDiff = diff
                best = key
            }
        }
        return best
    }

    /**
     * 官方 getClosestSize（index.js L3644-L3701 1:1，纯逻辑部分；网络段由调用方处理）。
     * 空 sizes → null；size 非 string/split('x') length!=2/Number NaN → skip；
     * reduce 选 aspectDiff + resolutionDiff 最小的 size。
     */
    fun getClosestSize(width: Int, height: Int, sizes: List<String>): String? {
        if (sizes.isEmpty()) return null
        val targetWidth = width.toDouble()
        val targetHeight = height.toDouble()
        val targetAspect = targetWidth / targetHeight
        val targetResolution = targetWidth * targetHeight
        var bestSize: String? = null
        var bestDiff = Double.POSITIVE_INFINITY
        for (size in sizes) {
            if (size.isBlank()) continue
            val parts = size.split("x")
            if (parts.size != 2) continue
            val sw = parts[0].toDoubleOrNull() ?: continue
            val sh = parts[1].toDoubleOrNull() ?: continue
            val aspectDiff = Math.abs((sw / sh) - targetAspect) / targetAspect
            val resolutionDiff = Math.abs(sw * sh - targetResolution) / targetResolution
            val diff = aspectDiff + resolutionDiff
            if (diff < bestDiff) {
                bestDiff = diff
                bestSize = size
            }
        }
        return bestSize
    }

    /**
     * 官方 Stability 客户端 body（index.js generateStabilityImage L3720-L3730 1:1）。
     * body = { model, payload: { prompt: slice(0,10000), negative_prompt: slice(0,10000), aspect_ratio: getClosestAspectRatio(w,h,'stability'), seed: seed>=0?seed:undefined, style_preset, output_format: "png" } }
     * stylePreset null/空省略（JS undefined 键省略）。
     */
    fun stabilityPayload(
        model: String, prompt: String, negativePrompt: String,
        width: Int, height: Int, seed: Long, stylePreset: String?,
    ): JsonObject {
        val payload = buildJsonObject {
            put("prompt", JsonPrimitive(prompt.take(10000)))
            put("negative_prompt", JsonPrimitive(negativePrompt.take(10000)))
            put("aspect_ratio", JsonPrimitive(getClosestAspectRatio(width, height, "stability")))
            if (seed >= 0) put("seed", JsonPrimitive(seed))
            stylePreset?.takeIf { it.isNotBlank() }?.let { put("style_preset", JsonPrimitive(it)) }
            put("output_format", JsonPrimitive("png"))
        }
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("payload", payload)
        }
    }

    /**
     * 官方 Aimlapi 客户端 body（index.js generateAimlapiImage L4184-L4196 1:1）。
     * isSdLike = model.startsWith('flux/') || model.startsWith('stable') || model==='recraft-v3' || model==='triposr'
     * isSdLike: {prompt, model, steps: clamp(steps,1,50), guidance: clamp(scale,1.5,5), width: clamp(w,256,1440), height: clamp(h,256,1440), seed?: seed>=0}
     * 否则: {prompt, model, n:1, size: "${w}x${h}", quality: openaiQuality, style: openaiStyle}
     * openaiQuality/openaiStyle null 省略（JS undefined 键省略）。
     */
    fun aimlapiBody(
        prompt: String, model: String, steps: Int, scale: Double,
        width: Int, height: Int, seed: Long,
        openaiQuality: String?, openaiStyle: String?,
    ): JsonObject {
        val lower = model.lowercase()
        val isSdLike = lower.startsWith("flux/") || lower.startsWith("stable") ||
            lower == "recraft-v3" || lower == "triposr"
        return buildJsonObject {
            put("prompt", JsonPrimitive(prompt))
            put("model", JsonPrimitive(model))
            if (isSdLike) {
                put("steps", JsonPrimitive(clamp(steps, 1, 50)))
                put("guidance", num(clamp(scale, 1.5, 5.0)))
                put("width", JsonPrimitive(clamp(width, 256, 1440)))
                put("height", JsonPrimitive(clamp(height, 256, 1440)))
                if (seed >= 0) put("seed", JsonPrimitive(seed))
            } else {
                put("n", JsonPrimitive(1))
                put("size", JsonPrimitive("${width}x${height}"))
                openaiQuality?.let { put("quality", JsonPrimitive(it)) }
                openaiStyle?.let { put("style", JsonPrimitive(it)) }
            }
        }
    }

    /**
     * 官方 ElectronHub 服务端 bodyParams（stable-diffusion.js electronhub.post L1232-L1244 1:1）。
     * bodyParams = { model, prompt, response_format: "b64_json", size?, quality? }
     * size null/空省略；quality null/空省略。
     */
    fun electronhubBody(model: String, prompt: String, size: String?, quality: String?): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put("prompt", JsonPrimitive(prompt))
        put("response_format", JsonPrimitive("b64_json"))
        size?.takeIf { it.isNotBlank() }?.let { put("size", JsonPrimitive(it)) }
        quality?.takeIf { it.isNotBlank() }?.let { put("quality", JsonPrimitive(it)) }
    }

    /**
     * 官方 NanoGPT 客户端 body（index.js generateNanoGPTImage L4436-L4447 1:1）。
     * body = { model, prompt, negative_prompt, num_steps: parseInt(steps), scale: parseFloat(scale), width: parseInt(width), height: parseInt(height), resolution: "${w}x${h}", showExplicitContent: true, nImages: 1 }
     * width/height 用 Double 模拟 JS Number，parseInt→toInt，resolution 用 jsNumStr 还原 JS 模板字符串语义。
     */
    fun nanogptBody(
        model: String, prompt: String, negativePrompt: String,
        steps: Double, scale: Double, width: Double, height: Double,
    ): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put("prompt", JsonPrimitive(prompt))
        put("negative_prompt", JsonPrimitive(negativePrompt))
        put("num_steps", JsonPrimitive(steps.toInt()))
        put("scale", num(scale))
        put("width", JsonPrimitive(width.toInt()))
        put("height", JsonPrimitive(height.toInt()))
        put("resolution", JsonPrimitive("${jsNumStr(width)}x${jsNumStr(height)}"))
        put("showExplicitContent", JsonPrimitive(true))
        put("nImages", JsonPrimitive(1))
    }

    /**
     * 官方 BFL 服务端 requestBody（stable-diffusion.js bfl.post L1481-L1527 1:1，含加工）。
     * 初始 = { prompt, steps: clamp(steps,1,50), guidance: clamp(scale,1.5,5), width: clamp(w,256,1440), height: clamp(h,256,1440), prompt_upsampling, seed: seed ?? null, safety_tolerance: 6, output_format: "jpeg" }
     * model.endsWith("-ultra"): 加 aspect_ratio = bflGetClosestAspectRatio(w,h)，删 steps/guidance/width/height/prompt_upsampling
     * model.endsWith("-pro-1.1"): 删 steps/guidance
     */
    fun bflBody(
        model: String, prompt: String, steps: Int, scale: Double,
        width: Int, height: Int, promptUpsampling: Boolean, seed: Long?,
    ): JsonObject = buildJsonObject {
        val isUltra = model.endsWith("-ultra")
        val isPro11 = model.endsWith("-pro-1.1")
        put("prompt", JsonPrimitive(prompt))
        if (!isUltra && !isPro11) {
            put("steps", JsonPrimitive(clamp(steps, 1, 50)))
            put("guidance", num(clamp(scale, 1.5, 5.0)))
        }
        if (!isUltra) {
            put("width", JsonPrimitive(clamp(width, 256, 1440)))
            put("height", JsonPrimitive(clamp(height, 256, 1440)))
            put("prompt_upsampling", JsonPrimitive(promptUpsampling))
        }
        put("seed", if (seed != null) JsonPrimitive(seed) else JsonNull)
        put("safety_tolerance", JsonPrimitive(6))
        put("output_format", JsonPrimitive("jpeg"))
        if (isUltra) {
            put("aspect_ratio", JsonPrimitive(bflGetClosestAspectRatio(width, height)))
        }
    }

    /** 官方 BFL 内联 getClosestAspectRatio（bfl.post L1493-L1513 1:1，比例范围 9/21..21/9，gcd 简化）。 */
    private fun bflGetClosestAspectRatio(width: Int, height: Int): String {
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

    /**
     * 官方 xAI 服务端 requestBody（stable-diffusion.js xai.post L1740-L1746 1:1）。
     * requestBody = { prompt, model, aspect_ratio, resolution, response_format: "b64_json" }
     * aspectRatio/resolution null 省略（JS undefined 键省略；客户端仅 grok-imagine 才计算）。
     */
    fun xaiBody(prompt: String, model: String, aspectRatio: String?, resolution: String?): JsonObject = buildJsonObject {
        put("prompt", JsonPrimitive(prompt))
        put("model", JsonPrimitive(model))
        aspectRatio?.let { put("aspect_ratio", JsonPrimitive(it)) }
        resolution?.let { put("resolution", JsonPrimitive(it)) }
        put("response_format", JsonPrimitive("b64_json"))
    }

    /**
     * 官方 Extras 客户端 body（index.js generateExtrasImage L3524-L3550 1:1）。
     * body = { prompt, sampler, steps, scale, width, height, negative_prompt,
     *   restore_faces: !!restoreFaces, enable_hr: !!enableHr, karras: !!hordeKarras,
     *   hr_upscaler, hr_scale, denoising_strength, hr_second_pass_steps,
     *   seed: seed>=0 ? seed : undefined }
     * scale/hrScale/denoisingStrength 用 num() 锁 JSON.stringify 数字语义（7.0→7）；seed<0 省略。
     */
    fun extrasPayload(
        prompt: String, negativePrompt: String, sampler: String, steps: Int,
        scale: Double, width: Int, height: Int,
        restoreFaces: Boolean, enableHr: Boolean, hordeKarras: Boolean,
        hrUpscaler: String, hrScale: Double,
        denoisingStrength: Double, hrSecondPassSteps: Int, seed: Long,
    ): JsonObject = buildJsonObject {
        put("prompt", JsonPrimitive(prompt))
        put("sampler", JsonPrimitive(sampler))
        put("steps", JsonPrimitive(steps))
        put("scale", num(scale))
        put("width", JsonPrimitive(width))
        put("height", JsonPrimitive(height))
        put("negative_prompt", JsonPrimitive(negativePrompt))
        put("restore_faces", JsonPrimitive(restoreFaces))
        put("enable_hr", JsonPrimitive(enableHr))
        put("karras", JsonPrimitive(hordeKarras))
        put("hr_upscaler", JsonPrimitive(hrUpscaler))
        put("hr_scale", num(hrScale))
        put("denoising_strength", num(denoisingStrength))
        put("hr_second_pass_steps", JsonPrimitive(hrSecondPassSteps))
        if (seed >= 0) put("seed", JsonPrimitive(seed))
    }

    // ========== 第三批：LLM 后端 5 个 + comfy replaceComfyWorkflow（差分脚本 imagegen-services-official.mjs 第三批） ==========

    /**
     * 官方 FalAI 服务端 requestBody（stable-diffusion.js falai.post L1643-L1651 逐字摘）。
     * body = { prompt, image_size: { width, height }, num_inference_steps, seed: seed ?? null,
     *   guidance_scale, enable_safety_checker: false, safety_tolerance: 6 }
     * steps 1..50 clamp；guidance 1.5..5 clamp；width/height 256..1440 clamp。
     * 注：App 直连 rest.fal.ai（同步），官方走 queue.fal.run（异步），URL/轮询属接线差异——body 一致。
     */
    fun falaiServerBody(
        prompt: String, width: Int, height: Int, steps: Int, scale: Double, seed: Long?,
    ): JsonObject = buildJsonObject {
        put("prompt", JsonPrimitive(prompt))
        put("image_size", buildJsonObject {
            put("width", JsonPrimitive(width.coerceIn(256, 1440)))
            put("height", JsonPrimitive(height.coerceIn(256, 1440)))
        })
        put("num_inference_steps", JsonPrimitive(steps.coerceIn(1, 50)))
        put("seed", seed?.let { JsonPrimitive(it) } ?: JsonNull)
        put("guidance_scale", num(scale.coerceIn(1.5, 5.0)))
        put("enable_safety_checker", JsonPrimitive(false))
        put("safety_tolerance", JsonPrimitive(6))
    }

    /**
     * 官方 Google 客户端 body（index.js generateGoogleImage L4610-L4623 逐字摘，非 veo 分支）。
     * body = { prompt, aspect_ratio: getClosestAspectRatio(w,h,'google'), negative_prompt, model,
     *   enhance, api, seed: seed>=0?seed:undefined, vertexai_auth_mode, vertexai_region, vertexai_express_project_id }
     * aspectRatio 取 google 集合 ['1:1','16:9','9:16','4:3','3:4'] 最小差。
     * 注：服务端 google.js /generate-image 再加工成 {instances, parameters}，此处仅对齐客户端发 ST 代理的 body
     * （即 fetch('/api/google/generate-image') 时 JSON.stringify 的对象字面量）。
     */
    fun googleClientBody(
        prompt: String, aspectRatio: String, negativePrompt: String, model: String,
        enhance: Boolean?, api: String?, seed: Long?,
        vertexAuthMode: String?, vertexRegion: String?, vertexProject: String?,
    ): JsonObject = buildJsonObject {
        put("prompt", JsonPrimitive(prompt))
        put("aspect_ratio", JsonPrimitive(aspectRatio))
        put("negative_prompt", JsonPrimitive(negativePrompt))
        put("model", JsonPrimitive(model))
        if (enhance != null) put("enhance", JsonPrimitive(enhance))
        if (!api.isNullOrBlank()) put("api", JsonPrimitive(api))
        if (seed != null && seed >= 0) put("seed", JsonPrimitive(seed))
        if (!vertexAuthMode.isNullOrBlank()) put("vertexai_auth_mode", JsonPrimitive(vertexAuthMode))
        if (!vertexRegion.isNullOrBlank()) put("vertexai_region", JsonPrimitive(vertexRegion))
        if (!vertexProject.isNullOrBlank()) put("vertexai_express_project_id", JsonPrimitive(vertexProject))
    }

    /**
     * 官方 ZAI 客户端 image 分支 body（index.js generateZaiImage L4688-L4699 逐字摘）。
     * body = { prompt, model, quality, size: "${width}x${height}" }
     * width/height 预处理：round(multiple=16 倍数) → clamp 512..2048；若非 glm-image，再 while(w*h>2^21) 减 multiple。
     * 因预处理不纯（while 调 Math.round/clamp），此处仅差分最终字段值集合形态：size 必须为 "WxH"，quality 空字符串省略。
     */
    fun zaiClientBody(
        prompt: String, model: String, quality: String?, width: Int, height: Int,
    ): JsonObject = buildJsonObject {
        put("prompt", JsonPrimitive(prompt))
        put("model", JsonPrimitive(model))
        if (!quality.isNullOrBlank()) put("quality", JsonPrimitive(quality))
        put("size", JsonPrimitive("${width}x${height}"))
    }

    /**
     * 官方 OpenRouter 客户端 body（index.js generateOpenRouterImage L4722-L4730 逐字摘）。
     * body = { model, prompt, aspect_ratio: getClosestAspectRatio(w,h,'stability') }
     * aspectRatio 取 stability 集合（9 项，同 Stability 后端）。
     */
    fun openRouterBody(model: String, prompt: String, aspectRatio: String): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put("prompt", JsonPrimitive(prompt))
        put("aspect_ratio", JsonPrimitive(aspectRatio))
    }

    /**
     * 官方 WorkersAI 客户端 body（index.js generateWorkersAIImage L4745-L4755 逐字摘）。
     * body = { prompt, negative_prompt, model, width, height, steps, scale, seed: seed>=0?seed:undefined, account_id }
     * 注：服务端 workersai.post('/generate') 走表单 form-urlencoded（prompt/negative_prompt 键）——此处仅对齐
     * 客户端 fetch('/api/sd/workersai/generate') JSON body；实际 App 直连 Cloudflare 按厂商 form 契约，
     * 两者不同源但可差分同一份 client body 字段（App 接线翻译为 Cloudflare form）。
     */
    fun workersAiClientBody(
        prompt: String, negativePrompt: String, model: String,
        width: Int, height: Int, steps: Int, scale: Double, seed: Long?, accountId: String,
    ): JsonObject = buildJsonObject {
        put("prompt", JsonPrimitive(prompt))
        put("negative_prompt", JsonPrimitive(negativePrompt))
        put("model", JsonPrimitive(model))
        put("width", JsonPrimitive(width))
        put("height", JsonPrimitive(height))
        put("steps", JsonPrimitive(steps))
        put("scale", num(scale))
        if (seed != null && seed >= 0) put("seed", JsonPrimitive(seed))
        put("account_id", JsonPrimitive(accountId))
    }

    /**
     * 官方 Comfy 占位符替换纯函数（generateComfyImageCommon index.js L4231-L4261 逐字对齐）。
     * 搜索串全部含外层双引号 '"%xxx%"'，替换值 = JSON.stringify 产物：
     * 字符串 → "…（引号转义）"；整值数字无小数点（JSON.stringify(7)="7"、JSON.stringify(0.7)="0.7"）。
     *
     * [seed] 为已解析值：官方 L4235 内联 `settings.seed >= 0 ? seed : Math.round(Math.random()*MAX_SAFE)`，
     * 见 [resolveComfySeed]；[denoisingStrength] null 模拟官方 undefined → 1.0；
     * [clipSkip] NaN 模拟官方 isNaN → -1，否则取负（12 → -12）。
     *
     * 占位符组两份列表（官方 generateComfyImage L4304-L4313 / generateComfyRunPodImage L4326-L4331）：
     * standard 含 model/vae/sampler/scheduler，runPod 仅 steps/scale/width/height。
     * [customPlaceholders] 对应官方 comfy_placeholders（L4248-L4250）：`"%find%"` → JSON.stringify(replace)，
     * replace 须由调用方先过 substituteParams（App 侧宏替换）。官方 %user_avatar%/%char_avatar% 头像
     * 注入仍未接——登记偏差。
     */
    fun replaceComfyWorkflow(
        workflow: String,
        runPod: Boolean,
        prompt: String,
        negativePrompt: String,
        seed: Long,
        denoisingStrength: Double?,
        clipSkip: Double,
        model: String,
        vae: String,
        sampler: String,
        scheduler: String,
        steps: Int,
        scale: Double,
        width: Int,
        height: Int,
        customPlaceholders: List<Pair<String, String>> = emptyList(),
    ): String {
        val jsStr = { s: String -> buildJsonObject { put("v", JsonPrimitive(s)) }.toString()
            .removePrefix("{\"v\":").removeSuffix("}") }
        var w = workflow
            .replace("\"%prompt%\"", jsStr(prompt))
            .replace("\"%negative_prompt%\"", jsStr(negativePrompt))
            .replace("\"%seed%\"", seed.toString())
            // 官方 L4238：denoising_strength === undefined ? 1.0 : 值
            .replace("\"%denoise%\"", jsNumStr(denoisingStrength ?: 1.0))
            // 官方 L4240：isNaN(clip_skip) ? -1 : -clip_skip
            .replace("\"%clip_skip%\"", jsNumStr(if (clipSkip.isNaN()) -1.0 else -clipSkip))
        val placeholders = if (runPod) {
            listOf("steps", "scale", "width", "height")
        } else {
            listOf("model", "vae", "sampler", "scheduler", "steps", "scale", "width", "height")
        }
        for (ph in placeholders) {
            val value = when (ph) {
                "steps" -> steps.toString()
                "scale" -> jsNumStr(scale)
                "width" -> width.toString()
                "height" -> height.toString()
                "model" -> jsStr(model)
                "vae" -> jsStr(vae)
                "sampler" -> jsStr(sampler)
                else -> jsStr(scheduler)
            }
            w = w.replace("\"%$ph%\"", value)
        }
        // 官方 L4248-L4250：自定义占位符在标准组之后替换（replaceAll 字符串搜索全局语义）
        for ((find, replace) in customPlaceholders) {
            w = w.replace("\"%$find%\"", jsStr(replace))
        }
        return w
    }

    /**
     * 官方 Comfy seed 解析（generateComfyImageCommon L4235 内联式拆出）：
     * settings.seed >= 0 原样；否则 Math.round(random01 * Number.MAX_SAFE_INTEGER)。
     * [random01] 由调用方注入（官方为 Math.random()），差分打桩确定值。
     */
    fun resolveComfySeed(seed: Long, random01: Double): Long =
        if (seed >= 0) seed else Math.round(random01 * 9007199254740991.0)

    /** NovelAI 调参结果（官方 getNovelParams 返回对象 {steps,width,height,sm,sm_dyn}）。 */
    data class NovelParams(val steps: Int, val width: Int, val height: Int, val sm: Boolean, val smDyn: Boolean)

    /**
     * 官方 getNovelParams（stable-diffusion/index.js L4002-L4059）1:1：
     * steps=min(设置,50)；sampler='ddim' 或 model ∈ {nai-diffusion-4-curated-preview,nai-diffusion-4-full}
     * 时强制 sm/sm_dyn=false；anlasGuard 开启时尺寸>1024*1024 按 sqrt 比例缩至多倍 64（while 循环
     * 保证像素数不超限）、steps>28 截为 28。调度器白名单回退 'karras' 属设置层副作用，由 App 层处理。
     */
    fun getNovelParams(
        steps: Int,
        width: Int,
        height: Int,
        sampler: String,
        model: String,
        novelSm: Boolean,
        novelSmDyn: Boolean,
        anlasGuard: Boolean,
    ): NovelParams {
        var s = Math.min(steps, 50)
        var w = width
        var h = height
        var sm = novelSm
        var smDyn = novelSmDyn

        if (sampler == "ddim" ||
            listOf("nai-diffusion-4-curated-preview", "nai-diffusion-4-full").contains(model)
        ) {
            sm = false
            smDyn = false
        }

        if (!anlasGuard) {
            return NovelParams(steps = s, width = w, height = h, sm = sm, smDyn = smDyn)
        }

        val maxSteps = 28
        val maxPixels = 1024.0 * 1024.0

        if (w.toDouble() * h > maxPixels) {
            val ratio = Math.sqrt(maxPixels / (w.toDouble() * h))
            var newWidth = Math.round(w * ratio).toInt()
            var newHeight = Math.round(h * ratio).toInt()

            // 尺寸取整到 64 的倍数，不足则下调
            if (newWidth % 64 != 0) {
                newWidth -= newWidth % 64
            }
            if (newHeight % 64 != 0) {
                newHeight -= newHeight % 64
            }

            // 取整后像素数仍超限 → 每次 -64 直到不超
            while (newWidth.toDouble() * newHeight > maxPixels) {
                if (newWidth > newHeight) {
                    newWidth -= 64
                } else {
                    newHeight -= 64
                }
            }

            w = newWidth
            h = newHeight
        }

        if (s > maxSteps) {
            s = maxSteps
        }

        return NovelParams(steps = s, width = w, height = h, sm = sm, smDyn = smDyn)
    }

    /**
     * 官方 calculateSkipCfgAboveSigma（src/endpoints/novelai.js L14-L17 常量 + L120-L128）1:1：
     * model 含 'nai-diffusion-4-5' 用魔数 58 否则 19；(宽*高)/1011712 开平方后乘魔数。
     */
    fun calculateSkipCfgAboveSigma(width: Int, height: Int, model: String?): Double {
        val magicConstant = if (model != null && model.contains("nai-diffusion-4-5")) 58 else 19
        val pixelCount = width.toDouble() * height
        val ratio = pixelCount / 1011712.0
        return Math.pow(ratio, 0.5) * magicConstant
    }

    // ---------- helpers ----------

    /** JSON.stringify 数字语义：整数值不带小数点（7.0 → 7）。 */
    private fun num(v: Double): JsonPrimitive =
        if (v % 1.0 == 0.0) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

    /** JS Number.toString() 语义（整数不带 .0，用于 nanogptBody resolution 模板字符串）。 */
    private fun jsNumStr(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    /** JS Math.min(Math.max(n, lower), upper) 语义（clamp）。 */
    private fun clamp(n: Int, lower: Int, upper: Int): Int = n.coerceIn(lower, upper)
    private fun clamp(n: Double, lower: Double, upper: Double): Double = n.coerceIn(lower, upper)

    /** JS encodeURIComponent 语义（space=%20，!*'() 不编码）。Java URLEncoder.encode 多编码这些，回退。 */
    private fun encodeURIComponent(s: String): String {
        val enc = URLEncoder.encode(s, "UTF-8")
        return enc
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%2A", "*")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
    }

    /** JS URLSearchParams 语义（form-urlencoded，space=+）。Java URLEncoder.encode 完全对齐。 */
    private fun formEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
}

