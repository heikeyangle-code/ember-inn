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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
                    // 官方 extension_settings.sd.* 对应 fixture 的 settings 子对象
                    val st = args["settings"]?.jsonObject
                    val setting = { k: String -> st?.get(k)?.jsonPrimitive?.contentOrNull ?: "" }
                    val actual = ImageGenRequestEngine.replaceComfyWorkflow(
                        workflow = workflow,
                        runPod = args["runPod"]?.jsonPrimitive?.content == "true",
                        prompt = prompt,
                        negativePrompt = neg,
                        seed = seed,
                        denoisingStrength = denoise,
                        clipSkip = clipSkip,
                        model = setting("model"),
                        vae = setting("vae"),
                        sampler = setting("sampler"),
                        scheduler = setting("scheduler"),
                        steps = setting("steps").toIntOrNull() ?: 0,
                        scale = setting("scale").toDoubleOrNull() ?: 0.0,
                        width = setting("width").toIntOrNull() ?: 0,
                        height = setting("height").toIntOrNull() ?: 0,
                        // 官方 comfy_placeholders（find,replace 对；replace 已过 substituteParams）
                        customPlaceholders = args["customPlaceholders"]?.jsonArray?.map { el ->
                            val o = el.jsonObject
                            Pair(
                                o.getValue("find").jsonPrimitive.content,
                                o.getValue("replace").jsonPrimitive.content,
                            )
                        } ?: emptyList(),
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
                "drawthings-body" -> {
                    fun s(k: String) = args.getValue(k).jsonPrimitive.content
                    // clipSkip null 模拟官方 undefined（JSON 省略键）；restore/enable 官方 !! 布尔化
                    val setting = ImageGenRequestEngine.ImageGenSettings(
                        sampler = s("sampler"),
                        steps = s("steps").toInt(),
                        scale = s("scale").toDouble(),
                        width = s("width").toInt(),
                        height = s("height").toInt(),
                        restoreFaces = s("restoreFaces") == "true",
                        enableHr = s("enableHr") == "true",
                        denoisingStrength = s("denoisingStrength").toDouble(),
                        clipSkip = s("clipSkip").takeIf { it != "null" }?.toDouble()?.toInt(),
                        hrScale = s("hrScale").toDouble(),
                        seed = s("seed").toLong(),
                    )
                    val actual = ImageGenRequestEngine.drawthingsPayload(setting, s("prompt"), s("negativePrompt"))
                    val expectedResult = expected.jsonObject.getValue("result").jsonPrimitive.content
                    assertEquals("case $id", expectedResult, actual.toString())
                }
                "closest-resolution" -> {
                    val width = args.getValue("width").jsonPrimitive.content.toInt()
                    val height = args.getValue("height").jsonPrimitive.content.toInt()
                    val actual = ImageGenRequestEngine.getClosestKnownResolution(width, height)
                    val expectedResult = expected.jsonObject.getValue("result").jsonPrimitive.content
                    assertEquals("case $id", expectedResult, actual)
                }
                "set-type-dims" -> {
                    fun i(k: String): Int? {
                        val el = args[k] ?: return null
                        return if (el is JsonNull) null else el.jsonPrimitive.content.toDouble().toInt()
                    }
                    val actual = ImageGenRequestEngine.setTypeSpecificDimensions(
                        width = i("width") ?: 0,
                        height = i("height") ?: 0,
                        generationType = i("generationType") ?: -99,
                        mediaWidth = i("mediaWidth"),
                        mediaHeight = i("mediaHeight"),
                        snap = args.getValue("snap").jsonPrimitive.content == "true",
                    )
                    val exp = expected.jsonObject.getValue("result").jsonObject
                    assertEquals("case $id width", exp.getValue("width").jsonPrimitive.content.toInt(), actual.first)
                    assertEquals("case $id height", exp.getValue("height").jsonPrimitive.content.toInt(), actual.second)
                }
                "novel-params" -> {
                    fun num(k: String) = (args[k] ?: error("missing $k")).let { el ->
                        when (el) {
                            is JsonNull -> null
                            else -> el.jsonPrimitive.content
                        }
                    }
                    fun bool(k: String) = args.getValue(k).jsonPrimitive.content == "true"
                    val p = ImageGenRequestEngine.getNovelParams(
                        steps = num("steps")!!.toInt(),
                        width = num("width")!!.toInt(),
                        height = num("height")!!.toInt(),
                        sampler = num("sampler")!!,
                        model = num("model")!!,
                        novelSm = bool("novelSm"),
                        novelSmDyn = bool("novelSmDyn"),
                        anlasGuard = bool("anlasGuard"),
                    )
                    // novel-params 的 expected 即结果对象本体（无 result 包裹键）
                    val exp = expected.jsonObject
                    assertEquals("case $id steps", exp.getValue("steps").jsonPrimitive.content.toInt(), p.steps)
                    assertEquals("case $id width", exp.getValue("width").jsonPrimitive.content.toInt(), p.width)
                    assertEquals("case $id height", exp.getValue("height").jsonPrimitive.content.toInt(), p.height)
                    assertEquals("case $id sm", exp.getValue("sm").jsonPrimitive.content == "true", p.sm)
                    // 官方参考实现返回对象字面量 { …, smDyn }（camelCase，非请求体的 sm_dyn）
                    assertEquals("case $id smDyn", exp.getValue("smDyn").jsonPrimitive.content == "true", p.smDyn)
                }
                "skip-cfg-sigma" -> {
                    val width = args.getValue("width").jsonPrimitive.content.toInt()
                    val height = args.getValue("height").jsonPrimitive.content.toInt()
                    val modelEl = args["model"]
                    val model = if (modelEl == null || modelEl is JsonNull) null else modelEl.jsonPrimitive.content
                    val actual = ImageGenRequestEngine.calculateSkipCfgAboveSigma(width, height, model)
                    // JS Math.pow 与 JVM 可差 1 ulp → 数值容差比较（相对 1e-9）
                    val expectedResult = expected.jsonObject.getValue("result").jsonPrimitive.content.toDouble()
                    assertTrue(
                        "case $id: expected=$expectedResult actual=$actual",
                        abs(actual - expectedResult) <= Math.max(1e-9, abs(expectedResult) * 1e-9),
                    )
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
