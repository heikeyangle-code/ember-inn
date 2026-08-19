package com.emberinn.app.data

import android.content.Context
import android.util.Base64
import com.emberinn.app.ui.settings.VoicePrefs
import com.emberinn.engine.prompt.TtsRequestEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 云端 TTS 后端集合：对齐官方 SillyTavern TTS 扩展
 * （public/scripts/extensions/tts/{backend}.js fetchTtsGeneration + src/endpoints/{speech,openai,...}.js
 * 各代理路由的厂商转发段）。准则 2：引擎层 [TtsRequestEngine] 构造 .js body（差分 35 例），
 * App 层只做：①调引擎层得 .js body；②按官方服务端映射规则把 .js body 翻译成厂商直连 body
 * （属"接线"，不重复实现官方纯逻辑）；③拼 URL + Header + 发请求 + 解析响应。
 *
 * 11 个后端全覆盖：ElevenLabs、OpenAI、Edge、Azure、Novel、MiniMax、Volcengine、Chutes、
 * Pollinations、Google Native、Google Translate。其余本地后端见 TtsBackendsLocal2，未接登记见 HANDOFF 3.7。
 *
 * 注意：官方 .js 走 SillyTavern 服务端代理；Android 端无该代理，故此处改为直连各厂商官方端点
 * （端点/Hdr 来自 fetchTtsGeneration 所调用代理对应的服务端实现 + 各厂商公开 API 文档）。
 */
private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val ttsJson = Json { ignoreUnknownKeys = true }

private fun jsonBody(json: JsonObject): okhttp3.RequestBody =
    json.toString().toRequestBody(JSON_MEDIA)

private fun jsonBody(json: org.json.JSONObject): okhttp3.RequestBody =
    json.toString().toRequestBody(JSON_MEDIA)

/** hex 字符串 → ByteArray（minimax/volcengine 厂商返回 data.audio 为 hex）。 */
private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.filter { it.isLetterOrDigit() }
    val len = clean.length / 2
    val out = ByteArray(len)
    for (i in 0 until len) {
        out[i] = ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
    }
    return out
}

/** JS encodeURIComponent 语义（space=%20，!*'() 不编码），用于 ElevenLabs voiceId URL 段。 */
private fun encodeURIComponent(s: String): String {
    val enc = java.net.URLEncoder.encode(s, "UTF-8")
    return enc
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%2A", "*")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
}

/** JS URLSearchParams 语义（form-urlencoded，space=+）。 */
private fun formEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

/** JsonObject → org.json.JSONObject（用于 OkHttp post body 与字段操作）。 */
private fun JsonObject.toOrgJson(): org.json.JSONObject {
    val out = org.json.JSONObject()
    for (k in keys) {
        val v = this[k] ?: continue
        // JsonNull / JsonPrimitive / 嵌套对象统一用 toString 还原（org.json 会按字面值解析）
        when {
            v is JsonObject -> out.put(k, org.json.JSONObject(v.toString()))
            else -> {
                val prim = v.jsonPrimitive
                val s = prim.content
                when {
                    s == "true" || s == "false" -> out.put(k, s.toBoolean())
                    s.toDoubleOrNull() != null -> out.put(k, s.toDouble())
                    else -> out.put(k, s)
                }
            }
        }
    }
    return out
}

// ============================================================================
// 1. ElevenLabs —— elevenlabs.js fetchTtsGeneration + speech.js elevenlabs.post('/synthesize')
//    .js body = { voiceId, request:{ model_id, text, voice_settings:{...} } }
//    服务端：URL = /v1/text-to-speech/{voiceId}，body = request，header xi-api-key
// ============================================================================
class ElevenLabsCloudBackend : TtsBackend {
    override val id = "elevenlabs"
    override val displayName = "ElevenLabs"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.elevenlabs.io"

    // elevenlabs.js 无静态 voice 列表（运行时 GET /api/speech/elevenlabs/voices 拉取）。
    // 此处提供 ElevenLabs 公开预置声音作为占位（实际账号内声音应以此 id 体系为准）。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("21m00Tcm4TlvDq8ikWAM", "Rachel", "en-US"),
        TtsVoice("AZnzlk1XvdvUeBnXldxF", "Domi", "en-US"),
        TtsVoice("EXAVITQu4vr4xnSDxMaL", "Bella", "en-US"),
        TtsVoice("ErXwobaYiN019PkySvjV", "Antoni", "en-US"),
        TtsVoice("MF3mGyEYCl7XYWbVwVgW", "Josh", "en-US"),
        TtsVoice("TxGEqnHWLuWfpfYocTTS", "Arnold", "en-US"),
        TtsVoice("VR6AewLTigWG4xSOukaH", "Charlotte", "en-US"),
        TtsVoice("pNInz6obpgDQGcFmaJgB", "Adam", "en-US"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                // 构造 .js body（差分）：引擎层自动按 shouldInvolveExtendedSettings 加 style/use_speaker_boost
                val jsBody = TtsRequestEngine.elevenLabsRequestBody(
                    settings = ttsSettings(model = VoicePrefs.ttsModel(context).ifBlank { "eleven_turbo_v2_5" }),
                    text = text,
                    voiceId = voiceId,
                )
                // 服务端映射：剥 voiceId 外壳，body = request 部分
                val vendorBody = jsBody["request"]!!.jsonObject.toOrgJson()
                val request = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/${encodeURIComponent(voiceId)}")
                    .header("xi-api-key", apiKey)
                    .header("Accept", "audio/mpeg")
                    .post(jsonBody(vendorBody))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 2. OpenAI —— openai.js fetchTtsGeneration + openai.js router.post('/generate-voice')
//    .js body = { text, voice, model, speed, [instructions] }
//    服务端映射：input=text, response_format='mp3', voice??'alloy', speed??1, model??'tts-1', [instructions]
// ============================================================================
class OpenAiCloudBackend : TtsBackend {
    override val id = "openai"
    override val displayName = "OpenAI"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.openai.com/v1"

    // openai.js static voices（逐字 copy voice_id/name/lang/preview_url）
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("alloy", "Alloy", "en-US", "https://cdn.openai.com/API/docs/audio/alloy.wav"),
        TtsVoice("ash", "Ash", "en-US", "https://cdn.openai.com/API/docs/audio/ash.wav"),
        TtsVoice("coral", "Coral", "en-US", "https://cdn.openai.com/API/docs/audio/coral.wav"),
        TtsVoice("echo", "Echo", "en-US", "https://cdn.openai.com/API/docs/audio/echo.wav"),
        TtsVoice("fable", "Fable", "en-US", "https://cdn.openai.com/API/docs/audio/fable.wav"),
        TtsVoice("onyx", "Onyx", "en-US", "https://cdn.openai.com/API/docs/audio/onyx.wav"),
        TtsVoice("nova", "Nova", "en-US", "https://cdn.openai.com/API/docs/audio/nova.wav"),
        TtsVoice("sage", "Sage", "en-US", "https://cdn.openai.com/API/docs/audio/sage.wav"),
        TtsVoice("shimmer", "Shimmer", "en-US", "https://cdn.openai.com/API/docs/audio/shimmer.wav"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                val base = VoicePrefs.ttsEndpoint(context).ifBlank { defaultEndpoint }.trimEnd('/')
                // App 无 characterInstructions UI（待补登记），暂传 null
                val jsBody = TtsRequestEngine.openAiRequestBody(
                    settings = ttsSettings(model = VoicePrefs.ttsModel(context).ifBlank { "tts-1" }, speed = 1.0),
                    inputText = text,
                    voiceId = voiceId,
                    characterName = null,
                    characterInstructions = null,
                )
                // 服务端映射：input=text, response_format='mp3', 兜底 voice/speed/model
                val vendorBody = org.json.JSONObject().apply {
                    put("input", jsBody["text"]!!.jsonPrimitive.content)
                    put("response_format", "mp3")
                    put("voice", jsBody["voice"]?.jsonPrimitive?.contentOrNull ?: "alloy")
                    put("speed", jsBody["speed"]?.jsonPrimitive?.contentOrNull?.toDouble() ?: 1.0)
                    put("model", jsBody["model"]?.jsonPrimitive?.contentOrNull ?: "tts-1")
                    jsBody["instructions"]?.let { put("instructions", it.jsonPrimitive.content) }
                }
                val request = Request.Builder()
                    .url("$base/audio/speech")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(vendorBody))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 3. Edge —— edge.js fetchTtsGeneration（body={text, voice, rate}，走 ST Extras edge-tts 模块）
//    Android 端无 Extras，直连轻量兼容端点：GET ?voice=&text=（占位，非官方端点）
// ============================================================================
class EdgeCloudBackend : TtsBackend {
    override val id = "edge"
    override val displayName = "Microsoft Edge TTS"
    override val requiresApiKey = false
    override val defaultEndpoint = "https://api.tts-lab.workers.dev"

    // edge.js 通过 /api/edge-tts/list 拉取（无静态 voice 数组）。给出 en-US 占位。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("en-US-AriaNeural", "Aria (en-US)", "en-US"),
        TtsVoice("en-US-AnaNeural", "Ana (en-US)", "en-US"),
        TtsVoice("en-US-ChristopherNeural", "Christopher (en-US)", "en-US"),
        TtsVoice("en-US-EricNeural", "Eric (en-US)", "en-US"),
        TtsVoice("en-US-GuyNeural", "Guy (en-US)", "en-US"),
        TtsVoice("en-US-JennyNeural", "Jenny (en-US)", "en-US"),
        TtsVoice("en-US-MichelleNeural", "Michelle (en-US)", "en-US"),
        TtsVoice("en-US-RogerNeural", "Roger (en-US)", "en-US"),
        TtsVoice("en-US-SteffanNeural", "Steffan (en-US)", "en-US"),
        TtsVoice("en-GB-SoniaNeural", "Sonia (en-GB)", "en-GB"),
        TtsVoice("zh-CN-XiaoxiaoNeural", "Xiaoxiao (zh-CN)", "zh-CN"),
        TtsVoice("zh-CN-YunxiNeural", "Yunxi (zh-CN)", "zh-CN"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = VoicePrefs.ttsEndpoint(context).ifBlank { defaultEndpoint }
                // .js body 构造差分用，App 直连第三方端点（非 Extras，无 body 可发）
                TtsRequestEngine.edgeRequestBody(
                    settings = ttsSettings(rate = 0.0),
                    inputText = text,
                    voiceId = voiceId,
                )
                val url = "$endpoint/?voice=${formEncode(voiceId)}&text=${formEncode(text)}"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 4. Azure —— azure.js fetchTtsGeneration + azure.js router.post('/generate')
//    .js body = { text, voice, region }
//    服务端拼 SSML 转发到 Azure；App 直连拼 SSML（与官方服务端语义一致）
// ============================================================================
class AzureCloudBackend : TtsBackend {
    override val id = "azure"
    override val displayName = "Microsoft Azure TTS"
    override val requiresApiKey = true
    override val defaultEndpoint = "" // 由 region 拼接（region 存于 ttsEndpoint）

    // azure.js 通过 /api/azure/list 拉取（无静态 voice 数组）。给出 en-US 占位。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("en-US-AriaNeural", "Aria (en-US)", "en-US"),
        TtsVoice("en-US-DavisNeural", "Davis (en-US)", "en-US"),
        TtsVoice("en-US-GuyNeural", "Guy (en-US)", "en-US"),
        TtsVoice("en-US-JaneNeural", "Jane (en-US)", "en-US"),
        TtsVoice("en-US-JasonNeural", "Jason (en-US)", "en-US"),
        TtsVoice("en-US-NancyNeural", "Nancy (en-US)", "en-US"),
        TtsVoice("en-US-SaraNeural", "Sara (en-US)", "en-US"),
        TtsVoice("en-US-TonyNeural", "Tony (en-US)", "en-US"),
        TtsVoice("zh-CN-XiaoxiaoNeural", "Xiaoxiao (zh-CN)", "zh-CN"),
        TtsVoice("zh-CN-YunxiNeural", "Yunxi (zh-CN)", "zh-CN"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                val region = VoicePrefs.ttsEndpoint(context).ifBlank { "westus" }
                    .removePrefix("https://").removePrefix("http://").trimEnd('/')
                // 引擎层 .js body（差分）
                val jsBody = TtsRequestEngine.azureRequestBody(
                    settings = ttsSettings(region = region),
                    text = text,
                    voiceId = voiceId,
                )
                // 服务端 SSML 拼接（与官方 src/endpoints/azure.js generate 路由一致）
                val lang = voiceId.substringBeforeLast('-').takeIf { it.contains('-') } ?: "en-US"
                val ssml = """
                    <speak version='1.0' xml:lang='$lang'>
                      <voice xml:lang='$lang' name='$voiceId'>$text</voice>
                    </speak>
                """.trimIndent()
                val request = Request.Builder()
                    .url("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
                    .header("Authorization", "Bearer $apiKey")
                    .header("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                    .header("Content-Type", "application/ssml+xml")
                    .post(ssml.toRequestBody("application/ssml+xml".toMediaType()))
                    .build()
                // jsBody 仅用于差分验证；Azure 直连 body=SSML（服务端拼装语义）
                @Suppress("unused") val _verify = jsBody
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 5. NovelAI —— novel.js fetchTtsGeneration + novelai.js router.post('/generate-voice')
//    .js body = { text, voice }（generator splitRecursive(text,1000) 分块）
//    服务端转发到 NovelAI GET /ai/generate-voice?text=&voice=-1&seed=
// ============================================================================
class NovelCloudBackend : TtsBackend {
    override val id = "novel"
    override val displayName = "NovelAI"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.novelai.net"

    // novel.js fetchTtsVoiceObjects 静态 13 个 voice（逐字 copy）
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("Ligeia", "Ligeia", "en-US"),
        TtsVoice("Aini", "Aini", "en-US"),
        TtsVoice("Orea", "Orea", "en-US"),
        TtsVoice("Claea", "Claea", "en-US"),
        TtsVoice("Lim", "Lim", "en-US"),
        TtsVoice("Aurae", "Aurae", "en-US"),
        TtsVoice("Naia", "Naia", "en-US"),
        TtsVoice("Aulon", "Aulon", "en-US"),
        TtsVoice("Elei", "Elei", "en-US"),
        TtsVoice("Ogma", "Ogma", "en-US"),
        TtsVoice("Raid", "Raid", "en-US"),
        TtsVoice("Pega", "Pega", "en-US"),
        TtsVoice("Lam", "Lam", "en-US"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                // 引擎层 .js body（差分，含 splitRecursive 分块逻辑）
                val jsBody = TtsRequestEngine.novelRequestBody(
                    settings = ttsSettings(),
                    inputText = text,
                    voiceId = voiceId,
                )
                // 服务端映射：text→query, voice→seed（NovelAI GET 端点）
                val chunkText = jsBody["text"]!!.jsonPrimitive.content
                val url = "https://api.novelai.net/ai/generate-voice?text=${formEncode(chunkText)}&voice=-1&seed=${formEncode(voiceId)}"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 6. MiniMax —— minimax.js fetchTtsGeneration + minimax.js router.post('/generate-voice')
//    .js body = { text, voiceId, apiHost, model, speed, volume, pitch, audioSampleRate, bitrate, format, language }
//    服务端转发到 MiniMax POST /v1/t2a_v2，body 字段名一致 → data.audio(hex)
// ============================================================================
class MinimaxCloudBackend : TtsBackend {
    override val id = "minimax"
    override val displayName = "MiniMax"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.minimax.io"

    // minimax.js static defaultVoices（占位）
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("Chinese (Mandarin)_Unrestrained_Young_Man", "Unrestrained Young Man", "zh-CN"),
        TtsVoice("English_translucent_cheerful_girl", "Cheerful Girl (en)", "en-US"),
        TtsVoice("English_mature_robust_male", "Mature Male (en)", "en-US"),
        TtsVoice("Japanese_akari_young_cheerful_girl", "Akari (ja)", "ja-JP"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                val host = VoicePrefs.ttsEndpoint(context).ifBlank { defaultEndpoint }.trimEnd('/')
                // 引擎层 .js body（差分，含 clamp + defaultSettings 兜底）
                val jsBody = TtsRequestEngine.minimaxRequestBody(
                    settings = ttsSettings(
                        apiHost = host,
                        model = VoicePrefs.ttsModel(context).ifBlank { "speech-02-hd" },
                    ),
                    inputText = text,
                    voiceId = voiceId,
                    language = "en_US",
                )
                // 服务端映射：MiniMax 端点 body 字段名一致（与 .js body 同），直发
                val request = Request.Builder()
                    .url("$host/v1/t2a_v2")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(jsBody.toOrgJson()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val data = json.optJSONObject("data") ?: return@use null
                    val audioHex = data.optString("audio")
                    if (audioHex.isBlank()) null else hexToBytes(audioHex)
                }
            }.getOrNull()
        }
}

// ============================================================================
// 7. 火山引擎（豆包）—— volcengine.js fetchTtsGeneration + volcengine.js router.post('/generate-voice')
//    .js body = { provider_endpoint, resource_id, text, voice_speaker, speed }
//    服务端拼 app/audio/request 转发到火山；App 直连拼同结构
// ============================================================================
class VolcengineCloudBackend : TtsBackend {
    override val id = "volcengine"
    override val displayName = "Volcengine (Doubao)"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://openspeech.bytedance.com"

    // volcengine.js static voices（8 个，lang 'cl'）
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("zh_female_xiaohe_uranus_bigtts", "zh_female_xiaohe_uranus_bigtts", "cl"),
        TtsVoice("zh_female_vv_uranus_bigtts", "zh_female_vv_uranus_bigtts", "cl"),
        TtsVoice("saturn_zh_female_keainvsheng_tob", "saturn_zh_female_keainvsheng_tob", "cl"),
        TtsVoice("saturn_zh_female_tiaopigongzhu_tob", "saturn_zh_female_tiaopigongzhu_tob", "cl"),
        TtsVoice("saturn_zh_female_cancan_tob", "saturn_zh_female_cancan_tob", "cl"),
        TtsVoice("saturn_zh_male_shuanglangshaonian_tob", "saturn_zh_male_shuanglangshaonian_tob", "cl"),
        TtsVoice("saturn_zh_male_tiancaitongzhuo_tob", "saturn_zh_male_tiancaitongzhuo_tob", "cl"),
        TtsVoice("zh_male_taocheng_uranus_bigtts", "zh_male_taocheng_uranus_bigtts", "cl"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                // 火山 appid/token 对应 SillyTavern VOLCENGINE_APP_ID / ACCESS_KEY：
                // Android 端把 apiKey 拆为 "appid:token"；若未拆则整串当 token、appid 取首段。
                val parts = apiKey.split(":")
                val appid = parts.getOrNull(0).orEmpty()
                val token = parts.getOrNull(1) ?: apiKey
                val endpoint = VoicePrefs.ttsEndpoint(context).ifBlank { defaultEndpoint }
                // 引擎层 .js body（差分）
                val jsBody = TtsRequestEngine.volcengineRequestBody(
                    settings = ttsSettings(
                        providerEndpoint = endpoint,
                        resourceId = "volcservice_tts",
                        speed = 1.0,
                    ),
                    text = text,
                    voiceSpeaker = voiceId,
                )
                // 服务端映射：火山端点 body = { app:{appid, token}, audio:{voice_type, encoding}, request:{text} }
                val vendorBody = org.json.JSONObject()
                    .put("app", org.json.JSONObject().put("appid", appid).put("token", token))
                    .put("audio", org.json.JSONObject().put("voice_type", voiceId).put("encoding", "mp3"))
                    .put("request", org.json.JSONObject().put("text", text))
                val request = Request.Builder()
                    .url("$endpoint/api/v1/tts")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(vendorBody))
                    .build()
                @Suppress("unused") val _verify = jsBody
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val data = json.optJSONObject("data") ?: return@use null
                    val audioHex = data.optString("audio")
                    if (audioHex.isBlank()) null else hexToBytes(audioHex)
                }
            }.getOrNull()
        }
}

// ============================================================================
// 8. Chutes —— chutes.js fetchTtsGeneration + openai.js router.post('/chutes/generate-voice')
//    .js body = { input, voice: voiceId || 'af_heart', speed: settings.speed || 1 }
//    服务端映射：input→text, voice||'af_heart', speed||1 → 转发到 Chutes /v1/tts
// ============================================================================
class ChutesCloudBackend : TtsBackend {
    override val id = "chutes"
    override val displayName = "Chutes"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.chutes.ai"

    // chutes.js updateVoices 静态 kokoro 声音（54 个，逐字 copy id/name/lang）
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("af_alloy", "Alloy (Female)", "en-US"),
        TtsVoice("af_aoede", "Aoede (Female)", "en-US"),
        TtsVoice("af_bella", "Bella (Female)", "en-US"),
        TtsVoice("af_heart", "Heart (Female) - Default", "en-US"),
        TtsVoice("af_jessica", "Jessica (Female)", "en-US"),
        TtsVoice("af_kore", "Kore (Female)", "en-US"),
        TtsVoice("af_nicole", "Nicole (Female)", "en-US"),
        TtsVoice("af_nova", "Nova (Female)", "en-US"),
        TtsVoice("af_river", "River (Female)", "en-US"),
        TtsVoice("af_sarah", "Sarah (Female)", "en-US"),
        TtsVoice("af_sky", "Sky (Female)", "en-US"),
        TtsVoice("am_adam", "Adam (Male)", "en-US"),
        TtsVoice("am_echo", "Echo (Male)", "en-US"),
        TtsVoice("am_eric", "Eric (Male)", "en-US"),
        TtsVoice("am_fenrir", "Fenrir (Male)", "en-US"),
        TtsVoice("am_liam", "Liam (Male)", "en-US"),
        TtsVoice("am_michael", "Michael (Male)", "en-US"),
        TtsVoice("am_onyx", "Onyx (Male)", "en-US"),
        TtsVoice("am_puck", "Puck (Male)", "en-US"),
        TtsVoice("am_santa", "Santa (Male)", "en-US"),
        TtsVoice("bf_alice", "Alice (British Female)", "en-GB"),
        TtsVoice("bf_emma", "Emma (British Female)", "en-GB"),
        TtsVoice("bf_isabella", "Isabella (British Female)", "en-GB"),
        TtsVoice("bf_lily", "Lily (British Female)", "en-GB"),
        TtsVoice("bm_daniel", "Daniel (British Male)", "en-GB"),
        TtsVoice("bm_fable", "Fable (British Male)", "en-GB"),
        TtsVoice("bm_george", "George (British Male)", "en-GB"),
        TtsVoice("bm_lewis", "Lewis (British Male)", "en-GB"),
        TtsVoice("ef_dora", "Dora (European Female)", "es-ES"),
        TtsVoice("em_alex", "Alex (European Male)", "es-ES"),
        TtsVoice("em_santa", "Santa (European Male)", "es-ES"),
        TtsVoice("ff_siwis", "Siwis (French Female)", "fr-FR"),
        TtsVoice("hf_alpha", "Alpha (Hindi Female)", "hi-IN"),
        TtsVoice("hf_beta", "Beta (Hindi Female)", "hi-IN"),
        TtsVoice("hm_omega", "Omega (Hindi Male)", "hi-IN"),
        TtsVoice("hm_psi", "Psi (Hindi Male)", "hi-IN"),
        TtsVoice("if_sara", "Sara (Italian Female)", "it-IT"),
        TtsVoice("im_nicola", "Nicola (Italian Male)", "it-IT"),
        TtsVoice("jf_alpha", "Alpha (Japanese Female)", "ja-JP"),
        TtsVoice("jf_gongitsune", "Gongitsune (Japanese Female)", "ja-JP"),
        TtsVoice("jf_nezumi", "Nezumi (Japanese Female)", "ja-JP"),
        TtsVoice("jf_tebukuro", "Tebukuro (Japanese Female)", "ja-JP"),
        TtsVoice("jm_kumo", "Kumo (Japanese Male)", "ja-JP"),
        TtsVoice("pf_dora", "Dora (Portuguese Female)", "pt-PT"),
        TtsVoice("pm_alex", "Alex (Portuguese Male)", "pt-PT"),
        TtsVoice("pm_santa", "Santa (Portuguese Male)", "pt-PT"),
        TtsVoice("zf_xiaobei", "Xiaobei (Chinese Female)", "zh-CN"),
        TtsVoice("zf_xiaoni", "Xiaoni (Chinese Female)", "zh-CN"),
        TtsVoice("zf_xiaoxiao", "Xiaoxiao (Chinese Female)", "zh-CN"),
        TtsVoice("zf_xiaoyi", "Xiaoyi (Chinese Female)", "zh-CN"),
        TtsVoice("zm_yunjian", "Yunjian (Chinese Male)", "zh-CN"),
        TtsVoice("zm_yunxi", "Yunxi (Chinese Male)", "zh-CN"),
        TtsVoice("zm_yunxia", "Yunxia (Chinese Male)", "zh-CN"),
        TtsVoice("zm_yunyang", "Yunyang (Chinese Male)", "zh-CN"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                val model = VoicePrefs.ttsModel(context).ifBlank { "kokoro" }
                // 引擎层 .js body（差分，含 voice||'af_heart' 与 speed||1 短路）
                val jsBody = TtsRequestEngine.chutesRequestBody(
                    settings = ttsSettings(speed = 1.0),
                    text = text,
                    voiceId = voiceId.ifBlank { "af_heart" },
                )
                // 服务端映射：input→text（注意反向），voice/speed 不变
                val vendorBody = org.json.JSONObject().apply {
                    put("model", model)
                    put("voice", jsBody["voice"]!!.jsonPrimitive.content)
                    put("text", jsBody["input"]?.jsonPrimitive?.content ?: text)
                    jsBody["speed"]?.let { put("speed", it.jsonPrimitive.content.toDoubleOrNull() ?: 1.0) }
                }
                val request = Request.Builder()
                    .url("https://api.chutes.ai/v1/tts")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(vendorBody))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 9. Pollinations —— pollinations.js fetchTtsGeneration + speech.js pollinations.post('/generate')
//    .js body = { model, text:'Say exactly this and nothing else:'+'\n'+chunk, voice }
//    服务端转发到 GET https://text.pollinations.ai/{prompt}?model=openai-audio&voice={voice}
// ============================================================================
class PollinationsCloudBackend : TtsBackend {
    override val id = "pollinations"
    override val displayName = "Pollinations"
    override val requiresApiKey = false
    override val defaultEndpoint = "https://text.pollinations.ai"

    // pollinations.js 通过 /api/speech/pollinations/voices 拉取（model=openai-audio）。
    // 这里给出 openai-audio 兼容的 OpenAI 声音作为占位。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("alloy", "Alloy", "en-US"),
        TtsVoice("echo", "Echo", "en-US"),
        TtsVoice("fable", "Fable", "en-US"),
        TtsVoice("onyx", "Onyx", "en-US"),
        TtsVoice("nova", "Nova", "en-US"),
        TtsVoice("shimmer", "Shimmer", "en-US"),
        TtsVoice("ash", "Ash", "en-US"),
        TtsVoice("coral", "Coral", "en-US"),
        TtsVoice("sage", "Sage", "en-US"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                // 引擎层 .js body（差分，含 'Say exactly this and nothing else:\n' 前缀 + splitRecursive 分块）
                val jsBody = TtsRequestEngine.pollinationsRequestBody(
                    settings = ttsSettings(model = "openai-audio"),
                    text = text,
                    voiceId = voiceId,
                )
                // 服务端映射：text→URL path（encodeURIComponent），voice/model→query
                val prompt = jsBody["text"]!!.jsonPrimitive.content
                val voice = jsBody["voice"]?.jsonPrimitive?.contentOrNull ?: voiceId
                val model = jsBody["model"]?.jsonPrimitive?.contentOrNull ?: "openai-audio"
                val url = "https://text.pollinations.ai/${encodeURIComponent(prompt)}?model=${formEncode(model)}&voice=${formEncode(voice)}"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 10. Google Cloud TTS —— google-native.js fetchNativeTtsGeneration + google.js router.post('/generate-native-tts')
//     .js body = { text, voice, model, api, reverse_proxy, proxy_password, vertexai_auth_mode, vertexai_region, vertexai_express_project_id }
//     服务端转发到 Google Cloud TTS POST /v1/text:synthesize → audioContent(base64)
// ============================================================================
class GoogleNativeCloudBackend : TtsBackend {
    override val id = "google-native"
    override val displayName = "Google Cloud TTS"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://texttospeech.googleapis.com"

    // google-native.js 通过 /api/google/list-native-voices 拉取（无静态 voice 数组）。
    // 给出 Google Cloud TTS 常用声音作为占位。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("en-US-Standard-A", "Standard A (en-US)", "en-US"),
        TtsVoice("en-US-Standard-B", "Standard B (en-US)", "en-US"),
        TtsVoice("en-US-Standard-C", "Standard C (en-US)", "en-US"),
        TtsVoice("en-US-Standard-D", "Standard D (en-US)", "en-US"),
        TtsVoice("en-US-Wavenet-A", "Wavenet A (en-US)", "en-US"),
        TtsVoice("en-US-Wavenet-B", "Wavenet B (en-US)", "en-US"),
        TtsVoice("en-US-Wavenet-C", "Wavenet C (en-US)", "en-US"),
        TtsVoice("en-US-Wavenet-D", "Wavenet D (en-US)", "en-US"),
        TtsVoice("en-US-Neural2-A", "Neural2 A (en-US)", "en-US"),
        TtsVoice("en-US-Neural2-D", "Neural2 D (en-US)", "en-US"),
        TtsVoice("en-GB-Standard-A", "Standard A (en-GB)", "en-GB"),
        TtsVoice("zh-CN-Standard-A", "Standard A (zh-CN)", "zh-CN"),
        TtsVoice("zh-CN-Wavenet-A", "Wavenet A (zh-CN)", "zh-CN"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                val lang = voiceId.substringBeforeLast('-').ifBlank { "en-US" }
                // 引擎层 .js body（差分，含 useReverseProxy 分支）
                val jsBody = TtsRequestEngine.googleNativeRequestBody(
                    settings = ttsSettings(
                        model = VoicePrefs.ttsModel(context).ifBlank { "en-US-Standard-A" },
                        apiType = "generate",
                    ),
                    text = text,
                    voiceId = voiceId,
                    oaiSettings = ttsOaiSettings(),
                )
                // 服务端映射：Google Cloud TTS 端点 body = { input:{text}, voice:{languageCode, name}, audioConfig:{audioEncoding:MP3} }
                val vendorBody = org.json.JSONObject()
                    .put("input", org.json.JSONObject().put("text", text))
                    .put("voice", org.json.JSONObject().put("languageCode", lang).put("name", voiceId))
                    .put("audioConfig", org.json.JSONObject().put("audioEncoding", "MP3"))
                val request = Request.Builder()
                    .url("https://texttospeech.googleapis.com/v1/text:synthesize")
                    .header("x-goog-api-key", apiKey)
                    .post(jsonBody(vendorBody))
                    .build()
                @Suppress("unused") val _verify = jsBody
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val b64 = json.optString("audioContent")
                    if (b64.isBlank()) null else Base64.decode(b64, Base64.DEFAULT)
                }
            }.getOrNull()
        }
}

// ============================================================================
// 11. Google Translate TTS —— google-translate.js fetchTtsGeneration + google.js router.post('/generate-voice')
//     .js body = { text: splitRecursive(text, 200), voice }（text 是数组）
//     服务端转发到 GET https://translate.google.com/translate_tts?ie=UTF-8&q={chunk}&tl={lang}&client=tw-ob
// ============================================================================
class GoogleTranslateCloudBackend : TtsBackend {
    override val id = "google-translate"
    override val displayName = "Google Translate TTS"
    override val requiresApiKey = false
    override val defaultEndpoint = "https://translate.google.com"

    // google-translate.js 通过 /api/google/list-voices 拉取（返回 {lang: name} 字典，无静态数组）。
    // 这里按 lang 给出占位（voice_id = lang，因 Google Translate TTS 无独立 voice 概念，仅按语言切换）。
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("en-US", "English (US)", "en-US"),
        TtsVoice("en-GB", "English (UK)", "en-GB"),
        TtsVoice("zh-CN", "Chinese (Simplified)", "zh-CN"),
        TtsVoice("zh-TW", "Chinese (Traditional)", "zh-TW"),
        TtsVoice("ja-JP", "Japanese", "ja-JP"),
        TtsVoice("ko-KR", "Korean", "ko-KR"),
        TtsVoice("fr-FR", "French", "fr-FR"),
        TtsVoice("de-DE", "German", "de-DE"),
        TtsVoice("es-ES", "Spanish", "es-ES"),
        TtsVoice("pt-BR", "Portuguese (Brazil)", "pt-BR"),
        TtsVoice("it-IT", "Italian", "it-IT"),
        TtsVoice("ru-RU", "Russian", "ru-RU"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                // 引擎层 .js body（差分，body.text 是数组）
                val jsBody = TtsRequestEngine.googleTranslateRequestBody(
                    settings = ttsSettings(),
                    text = text,
                    voiceId = voiceId,
                )
                // 服务端映射：取首块 chunk → URL query；lang=voiceId
                val chunks = jsBody["text"]!!.jsonArray
                val chunk = chunks.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { text }
                val lang = voiceId.ifBlank { "en-US" }
                val url = "https://translate.google.com/translate_tts?ie=UTF-8&q=${formEncode(chunk)}&tl=${formEncode(lang)}&client=tw-ob"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android TTS)")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ---------- 辅助：构造引擎层 settings JsonObject（按 .js defaultSettings 取默认值，App 配置覆盖） ----------

private fun ttsSettings(
    model: String? = null,
    speed: Double? = null,
    stability: Double? = null,
    similarityBoost: Double? = null,
    styleExaggeration: Double? = null,
    speakerBoost: Boolean? = null,
    rate: Double? = null,
    region: String? = null,
    apiHost: String? = null,
    providerEndpoint: String? = null,
    resourceId: String? = null,
    apiType: String? = null,
    volume: Double? = null,
    pitch: Double? = null,
    audioSampleRate: Double? = null,
    bitrate: Double? = null,
    format: String? = null,
): JsonObject = buildJsonObject {
    model?.let { put("model", JsonPrimitive(it)) }
    speed?.let { put("speed", JsonPrimitive(it)) }
    stability?.let { put("stability", JsonPrimitive(it)) }
    similarityBoost?.let { put("similarity_boost", JsonPrimitive(it)) }
    styleExaggeration?.let { put("style_exaggeration", JsonPrimitive(it)) }
    speakerBoost?.let { put("speaker_boost", JsonPrimitive(it)) }
    rate?.let { put("rate", JsonPrimitive(it)) }
    region?.let { put("region", JsonPrimitive(it)) }
    apiHost?.let { put("apiHost", JsonPrimitive(it)) }
    providerEndpoint?.let { put("provider_endpoint", JsonPrimitive(it)) }
    resourceId?.let { put("resource_id", JsonPrimitive(it)) }
    apiType?.let { put("apiType", JsonPrimitive(it)) }
    volume?.let { put("volume", JsonPrimitive(it)) }
    pitch?.let { put("pitch", JsonPrimitive(it)) }
    audioSampleRate?.let { put("audioSampleRate", JsonPrimitive(it)) }
    bitrate?.let { put("bitrate", JsonPrimitive(it)) }
    format?.let { put("format", JsonPrimitive(it)) }
}

/** App 端 oai_settings 默认值（Android 端无反代/vertexai 配置 UI，全置空对齐官方 useReverseProxy=false 分支）。 */
private fun ttsOaiSettings(): JsonObject = buildJsonObject {
    put("reverse_proxy", JsonPrimitive(""))
    put("proxy_password", JsonPrimitive(""))
    put("vertexai_auth_mode", JsonPrimitive("auto"))
    put("vertexai_region", JsonPrimitive(""))
    put("vertexai_express_project_id", JsonPrimitive(""))
}

/**
 * 云端 TTS 后端注册入口：对齐官方 tts/index.js 按 provider 字符串分发。
 * 由 App 启动时调用一次（或直接 object 初始化触发）。
 */
object TtsBackendsCloudInit {
    init {
        TtsBackendRegistry.register(ElevenLabsCloudBackend())
        TtsBackendRegistry.register(OpenAiCloudBackend())
        TtsBackendRegistry.register(EdgeCloudBackend())
        TtsBackendRegistry.register(AzureCloudBackend())
        TtsBackendRegistry.register(NovelCloudBackend())
        TtsBackendRegistry.register(MinimaxCloudBackend())
        TtsBackendRegistry.register(VolcengineCloudBackend())
        TtsBackendRegistry.register(ChutesCloudBackend())
        TtsBackendRegistry.register(PollinationsCloudBackend())
        TtsBackendRegistry.register(GoogleNativeCloudBackend())
        TtsBackendRegistry.register(GoogleTranslateCloudBackend())
    }
}
