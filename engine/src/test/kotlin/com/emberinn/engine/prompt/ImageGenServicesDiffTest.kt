package com.emberinn.engine.prompt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方行为差分：stable-diffusion 扩展 services 后端服务端 body/URL 构造。
 * fixture 由 scripts/diff/imagegen-services-official.mjs 生成，禁止手改。
 *
 * 覆盖：togetherai（JSON body + n=1 + seed 随机回退）、pollinations（URL path encodeURIComponent +
 * URLSearchParams query，专门锁 space=%20 vs + 边界）、chutes（|| 短路：0 视同默认）。
 */
class ImageGenServicesDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `services backends match official fixtures`() {
        val resource = checkNotNull(javaClass.getResource("/diff/imagegen-services.json"))
        val root = json.parseToJsonElement(resource.readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        require(cases.isNotEmpty())

        for (caseEl in cases) {
            val case = caseEl.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val kind = case.getValue("kind").jsonPrimitive.content
            val args = case.getValue("args").jsonObject
            val expected = case.getValue("expected")

            when (kind) {
                "together" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val steps = args["steps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val seed = args["seed"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
                    val actual = ImageGenRequestEngine.togetherAiPayload(prompt, neg, model, steps, width, height, seed)
                    assertEquals("case $id", expected, actual)
                }
                "pollinations" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val seed = args["seed"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull()
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull()
                    val enhance = args["enhance"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val actual = ImageGenRequestEngine.pollinationsUrl(prompt, neg, model, seed, width, height, enhance)
                    assertEquals("case $id", expected.jsonPrimitive.content, actual)
                }
                "chutes" -> {
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val guidanceScale = args["guidanceScale"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull()
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull()
                    val steps = args["steps"]?.jsonPrimitive?.content?.toIntOrNull()
                    val actual = ImageGenRequestEngine.chutesPayload(model, prompt, neg, guidanceScale, width, height, steps)
                    assertEquals("case $id", expected, actual)
                }
                "stability" -> {
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val seed = args["seed"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
                    val stylePreset = args["stylePreset"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val actual = ImageGenRequestEngine.stabilityPayload(model, prompt, neg, width, height, seed, stylePreset)
                    assertEquals("case $id", expected, actual)
                }
                "aimlapi" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val steps = args["steps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val scale = args["scale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val seed = args["seed"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
                    val quality = args["openaiQuality"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val style = args["openaiStyle"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val actual = ImageGenRequestEngine.aimlapiBody(prompt, model, steps, scale, width, height, seed, quality, style)
                    assertEquals("case $id", expected, actual)
                }
                "getclosestsize" -> {
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val sizes = args["sizes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    val actual = ImageGenRequestEngine.getClosestSize(width, height, sizes)
                    val resultEl = expected.jsonObject["result"]
                    val expectedResult = if (resultEl == null || resultEl is JsonNull) null else resultEl.jsonPrimitive.contentOrNull
                    assertEquals("case $id", expectedResult, actual)
                }
                "electronhub" -> {
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val size = args["size"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val quality = args["quality"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val actual = ImageGenRequestEngine.electronhubBody(model, prompt, size, quality)
                    assertEquals("case $id", expected, actual)
                }
                "nanogpt" -> {
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val steps = args["steps"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val scale = args["scale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val width = args["width"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val height = args["height"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val actual = ImageGenRequestEngine.nanogptBody(model, prompt, neg, steps, scale, width, height)
                    assertEquals("case $id", expected, actual)
                }
                "bfl" -> {
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val steps = args["steps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val scale = args["scale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val promptUpsampling = args["promptUpsampling"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val seedEl = args["seed"]
                    val seed: Long? = if (seedEl == null || seedEl is JsonNull) null else seedEl.jsonPrimitive.content.toLongOrNull()
                    val actual = ImageGenRequestEngine.bflBody(model, prompt, steps, scale, width, height, promptUpsampling, seed)
                    assertEquals("case $id", expected, actual)
                }
                "xai" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val aspectRatio = args["aspectRatio"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val resolution = args["resolution"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val actual = ImageGenRequestEngine.xaiBody(prompt, model, aspectRatio, resolution)
                    assertEquals("case $id", expected, actual)
                }
                "extras" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val sampler = args["sampler"]?.jsonPrimitive?.content ?: ""
                    val steps = args["steps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val scale = args["scale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val restoreFaces = args["restoreFaces"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val enableHr = args["enableHr"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val hordeKarras = args["hordeKarras"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val hrUpscaler = args["hrUpscaler"]?.jsonPrimitive?.content ?: ""
                    val hrScale = args["hrScale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val denoisingStrength = args["denoisingStrength"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val hrSecondPassSteps = args["hrSecondPassSteps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val seed = args["seed"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
                    val actual = ImageGenRequestEngine.extrasPayload(
                        prompt, neg, sampler, steps, scale, width, height,
                        restoreFaces, enableHr, hordeKarras, hrUpscaler, hrScale,
                        denoisingStrength, hrSecondPassSteps, seed,
                    )
                    assertEquals("case $id", expected, actual)
                }
                "falai-server" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val steps = args["steps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val scale = args["scale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val seedEl = args["seed"]
                    val seed: Long? = if (seedEl == null || seedEl is JsonNull) null else seedEl.jsonPrimitive.content.toLongOrNull()
                    val actual = ImageGenRequestEngine.falaiServerBody(prompt, width, height, steps, scale, seed)
                    assertEquals("case $id", expected, actual)
                }
                "google-client" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val aspectRatio = args["aspectRatio"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val enhanceEl = args["enhance"]
                    val enhance = if (enhanceEl == null || enhanceEl is JsonNull) null else enhanceEl.jsonPrimitive.content.toBooleanStrictOrNull()
                    val api = args["api"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val seedEl = args["seed"]
                    val seed: Long? = if (seedEl == null || seedEl is JsonNull) null else seedEl.jsonPrimitive.content.toLongOrNull()
                    val vAuth = args["vertexAuthMode"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val vRegion = args["vertexRegion"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val vProj = args["vertexProject"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val actual = ImageGenRequestEngine.googleClientBody(
                        prompt, aspectRatio, neg, model, enhance, api, seed, vAuth, vRegion, vProj,
                    )
                    assertEquals("case $id", expected, actual)
                }
                "zai" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val quality = args["quality"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val actual = ImageGenRequestEngine.zaiClientBody(prompt, model, quality, width, height)
                    assertEquals("case $id", expected, actual)
                }
                "openrouter" -> {
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val aspectRatio = args["aspectRatio"]?.jsonPrimitive?.content ?: ""
                    val actual = ImageGenRequestEngine.openRouterBody(model, prompt, aspectRatio)
                    assertEquals("case $id", expected, actual)
                }
                "workersai-client" -> {
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val model = args["model"]?.jsonPrimitive?.content ?: ""
                    val width = args["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val height = args["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val steps = args["steps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val scale = args["scale"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val seedEl = args["seed"]
                    val seed: Long? = if (seedEl == null || seedEl is JsonNull) null else seedEl.jsonPrimitive.content.toLongOrNull()
                    val accountId = args["accountId"]?.jsonPrimitive?.content ?: ""
                    val actual = ImageGenRequestEngine.workersAiClientBody(
                        prompt, neg, model, width, height, steps, scale, seed, accountId,
                    )
                    assertEquals("case $id", expected, actual)
                }
                "comfy-replace" -> {
                    val workflow = args["workflow"]?.jsonPrimitive?.content ?: ""
                    val prompt = args["prompt"]?.jsonPrimitive?.content ?: ""
                    val neg = args["negativePrompt"]?.jsonPrimitive?.content ?: ""
                    val seed = args.getValue("seed").jsonPrimitive.content.toLong()
                    val denoiseEl = args["denoisingStrength"]
                    val denoise = when (denoiseEl) {
                        null, is JsonNull -> null
                        else -> denoiseEl.jsonPrimitive.content.toDouble()
                    }
                    val clipSkipEl = args["clipSkip"]
                    val clipSkip =
                        if (clipSkipEl == null || clipSkipEl is JsonNull) Double.NaN
                        else clipSkipEl.jsonPrimitive.content.toDouble()
                    val str = { k: String -> args[k]?.jsonPrimitive?.contentOrNull ?: "" }
                    val actual = ImageGenRequestEngine.replaceComfyWorkflow(
                        workflow = workflow,
                        runPod = args["runPod"]?.jsonPrimitive?.content == "true",
                        prompt = prompt,
                        negativePrompt = neg,
                        seed = seed,
                        denoisingStrength = denoise,
                        clipSkip = clipSkip,
                        model = str("model"),
                        vae = str("vae"),
                        sampler = str("sampler"),
                        scheduler = str("scheduler"),
                        steps = str("steps").toIntOrNull() ?: 0,
                        scale = str("scale").toDoubleOrNull() ?: 0.0,
                        width = str("width").toIntOrNull() ?: 0,
                        height = str("height").toIntOrNull() ?: 0,
                    )
                    val expectedResult = expected.jsonObject.getValue("result").jsonPrimitive.content
                    assertEquals("case $id", expectedResult, actual)
                }
                "comfy-seed-resolve" -> {
                    val seed = args.getValue("seed").jsonPrimitive.content.toLong()
                    val random01 = args.getValue("random01").jsonPrimitive.content.toDouble()
                    val actual = ImageGenRequestEngine.resolveComfySeed(seed, random01)
                    val expectedResult = expected.jsonObject.getValue("result").jsonPrimitive.content.toLong()
                    assertEquals("case $id", expectedResult, actual)
                }
                else -> error("unknown kind: $kind")
            }
        }
    }

    // Suppress unused warnings for helpers below — kept for future expansion when adding more backends.
    @Suppress("unused")
    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    @Suppress("unused")
    private fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    @Suppress("unused")
    private fun JsonObject.intOrNull(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    @Suppress("unused")
    private fun JsonObject.doubleOrNull(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
    @Suppress("unused")
    private fun JsonObject.boolOrNull(key: String): Boolean? =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
    @Suppress("unused")
    private fun JsonObject.primitiveContentOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    @Suppress("unused")
    private fun JsonPrimitive.asStringOrNull(): String? = this.contentOrNull
}
