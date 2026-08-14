package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：stable-diffusion 扩展 generateAutoImage / generateSdcppImage 请求体。
 * fixture 由 scripts/diff/imagegen-official.mjs 生成，禁止手改。
 */
class ImageGenDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `image generation request bodies match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/imagegen.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val kind = case.getValue("kind").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val st = args.getValue("settings").jsonObject
            val expected = case.getValue("expected").jsonObject

            fun d(key: String, default: Double = 0.0): Double = st[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: default
            fun i(key: String, default: Int = 0): Int = st[key]?.jsonPrimitive?.content?.toIntOrNull() ?: default
            fun l(key: String, default: Long = 0L): Long = st[key]?.jsonPrimitive?.content?.toLongOrNull() ?: default
            fun s(key: String, default: String = ""): String = st[key]?.jsonPrimitive?.contentOrNull ?: default

            val settings = ImageGenRequestEngine.ImageGenSettings(
                sampler = s("sampler", "DDIM"),
                scheduler = s("scheduler", "normal"),
                steps = i("steps", 20),
                scale = d("scale", 7.0),
                width = i("width", 512),
                height = i("height", 512),
                restoreFaces = st["restore_faces"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                enableHr = st["enable_hr"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                hrUpscaler = s("hr_upscaler", "Latent"),
                hrScale = d("hr_scale", 1.0),
                denoisingStrength = d("denoising_strength", 0.7),
                hrSecondPassSteps = i("hr_second_pass_steps", 0),
                seed = l("seed", -1L),
                clipSkip = st["clip_skip"]?.jsonPrimitive?.content?.toIntOrNull(),
                vae = s("vae"),
                model = s("model"),
            )
            val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
            val negative = args["negativePrompt"]?.jsonPrimitive?.content ?: ""

            val actual = if (kind == "auto") {
                ImageGenRequestEngine.auto1111Payload(settings, prompt, negative)
            } else {
                ImageGenRequestEngine.sdcppPayload(settings, prompt, negative)
            }
            assertEquals("case $id", expected, actual)
        }
    }
}
