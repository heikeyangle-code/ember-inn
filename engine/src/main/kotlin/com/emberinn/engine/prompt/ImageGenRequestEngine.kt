package com.emberinn.engine.prompt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 官方 stable-diffusion 扩展请求体（extensions/stable-diffusion/index.js generateAutoImage /
 * generateSdcppImage 1:1）。差分：scripts/diff/imagegen-official.mjs（10 例）。
 * 边界（登记）：ADetailer alwayson_scripts 未移植；vlad/drawthings/novel/openai/horde/hf/comfy 等其余后端另开差分。
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

    /** JSON.stringify 数字语义：整数值不带小数点。 */
    private fun num(v: Double): JsonPrimitive =
        if (v % 1.0 == 0.0) JsonPrimitive(v.toLong()) else JsonPrimitive(v)
}
