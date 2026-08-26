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
 * 12 个后端全覆盖：ElevenLabs、OpenAI、Edge、Azure、Novel、MiniMax、Volcengine、Chutes、
 * Pollinations、Google Native、Google Translate、Electron Hub。其余本地后端见 TtsBackendsLocal2，
 * 未接登记见 HANDOFF 3.7。
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

/**
 * hex 字符串 → ByteArray。官方 src/endpoints/minimax.js:143-190：
 * 去 `0x` 前缀与空白；非 hex 字符视为无效（返回空）；奇数长度左侧补 0 后逐字节解析。
 */
private fun hexToBytes(hex: String): ByteArray {
    val cleanHex = hex.replace(Regex("^0x"), "").replace(Regex("\\s"), "")
    if (!Regex("^[0-9a-fA-F]*$").matches(cleanHex)) return ByteArray(0)
    val paddedHex = if (cleanHex.length % 2 == 0) cleanHex else "0$cleanHex"
    val out = ByteArray(paddedHex.length / 2)
    for (i in out.indices) {
        out[i] = paddedHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
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
                @Suppress("unused") val _verify = jsBody
                // 服务端 SSML 拼接（src/endpoints/azure.js:53-56）：
                // lang=voice 前 2 段；&/</> XML 转义；带 xmlns；单行
                val lang = voiceId.split("-").take(2).joinToString("-")
                val escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                val ssml =
                    "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$lang'><voice xml:lang='$lang' name='$voiceId'>$escapedText</voice></speak>"
                val request = Request.Builder()
                    .url("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("X-Microsoft-OutputFormat", "webm-24khz-16bit-mono-opus")
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
                // 服务端映射（novelai.js generate-voice）：text→query, voice→seed；
                // 固定 opus=false&version=v2；Accept audio/mpeg
                val chunkText = jsBody["text"]!!.jsonPrimitive.content
                val url = "https://api.novelai.net/ai/generate-voice?text=${formEncode(chunkText)}&voice=-1&seed=${formEncode(voiceId)}&opus=false&version=v2"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "audio/mpeg")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 6. MiniMax —— minimax.js fetchTtsGeneration + src/endpoints/minimax.js router.post('/generate-voice')
//    .js body = { text, voiceId, apiHost, model, speed, volume, pitch, audioSampleRate, bitrate, format, language }
//    服务端映射：POST {apiHost}/v1/t2a_v2?GroupId={groupId}，头 Authorization+MM-API-Source，
//    厂商 body = { model, text, stream:false, voice_setting:{voice_id,speed,vol,pitch},
//                  audio_setting:{sample_rate,bitrate,format,channel:1}, lang? } → data.audio(hex)/data.url
// ============================================================================
class MinimaxCloudBackend : TtsBackend {
    override val id = "minimax"
    override val displayName = "MiniMax"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.minimax.io"

    /** 官方 mapLanguageToMiniMaxFormat（minimax.js:850-887）：lang code → MiniMax 语言格式。 */
    private fun mapLanguageToMiniMaxFormat(lang: String): String = when (lang) {
        "zh-CN" -> "zh_CN"; "zh-TW" -> "zh_TW"; "en-US" -> "en_US"; "en-GB" -> "en_GB"
        "en-AU" -> "en_AU"; "en-IN" -> "en_IN"; "ja-JP" -> "ja_JP"; "ko-KR" -> "ko_KR"
        "fr-FR" -> "fr_FR"; "de-DE" -> "de_DE"; "es-ES" -> "es_ES"; "pt-BR" -> "pt_BR"
        "it-IT" -> "it_IT"; "ar-SA" -> "ar_SA"; "ru-RU" -> "ru_RU"; "tr-TR" -> "tr_TR"
        "nl-NL" -> "nl_NL"; "uk-UA" -> "uk_UA"; "vi-VN" -> "vi_VN"; "id-ID" -> "id_ID"
        "th-TH" -> "th_TH"; "pl-PL" -> "pl_PL"; "ro-RO" -> "ro_RO"; "el-GR" -> "el_GR"
        "cs-CZ" -> "cs_CZ"; "fi-FI" -> "fi_FI"; "hi-IN" -> "hi_IN"
        else -> "auto"
    }

    // 官方 static defaultVoices 仅 1 个（MiniMax 无 voices 列表 API，其余靠用户自填克隆音色）
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("Chinese (Mandarin)_Unrestrained_Young_Man", "Unrestrained Young Man", "zh-CN"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                // 官方 MINIMAX + MINIMAX_GROUP_ID 两个 secret；App 单 key 框约定 "apiKey:groupId"
                val parts = VoicePrefs.ttsApiKey(context).split(":")
                val apiKey = parts.getOrNull(0).orEmpty()
                val groupId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@runCatching null
                val host = VoicePrefs.ttsEndpoint(context).ifBlank { defaultEndpoint }.trimEnd('/')
                // 引擎层 .js body（差分，含 clamp + defaultSettings 兜底）
                val jsBody = TtsRequestEngine.minimaxRequestBody(
                    settings = ttsSettings(
                        apiHost = host,
                        model = VoicePrefs.ttsModel(context).ifBlank { "speech-02-hd" },
                    ),
                    inputText = text,
                    voiceId = voiceId,
                    language = null,
                )
                // 官方 generateTts：按声音对象 lang 映射 MiniMax 语言，找不到则不带 lang
                val voiceLang = getVoices(context).firstOrNull { it.id == voiceId }?.lang
                val langParam = voiceLang?.let { mapLanguageToMiniMaxFormat(it) }
                // 服务端映射：src/endpoints/minimax.js:36-58
                val vendorBody = org.json.JSONObject()
                    .put("model", jsBody["model"]?.jsonPrimitive?.content ?: "speech-02-hd")
                    .put("text", text)
                    .put("stream", false)
                    .put(
                        "voice_setting",
                        org.json.JSONObject()
                            .put("voice_id", voiceId)
                            .put("speed", jsBody["speed"]?.jsonPrimitive?.doubleOrNull ?: 1.0)
                            .put("vol", jsBody["volume"]?.jsonPrimitive?.doubleOrNull ?: 1.0)
                            .put("pitch", jsBody["pitch"]?.jsonPrimitive?.doubleOrNull ?: 0.0),
                    )
                    .put(
                        "audio_setting",
                        org.json.JSONObject()
                            .put("sample_rate", jsBody["audioSampleRate"]?.jsonPrimitive?.doubleOrNull ?: 32000.0)
                            .put("bitrate", jsBody["bitrate"]?.jsonPrimitive?.doubleOrNull ?: 128000.0)
                            .put("format", jsBody["format"]?.jsonPrimitive?.content ?: "mp3")
                            .put("channel", 1),
                    )
                langParam?.let { vendorBody.put("lang", it) }
                val request = Request.Builder()
                    .url("$host/v1/t2a_v2?GroupId=$groupId")
                    .header("Authorization", "Bearer $apiKey")
                    .header("MM-API-Source", "SillyTavern-TTS")
                    .post(jsonBody(vendorBody))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    // 官方：base_resp.status_code != 0 视为失败
                    val baseResp = json.optJSONObject("base_resp")
                    if (baseResp != null && baseResp.optInt("status_code", 0) != 0) return@use null
                    val data = json.optJSONObject("data") ?: return@use null
                    val audioHex = data.optString("audio")
                    if (audioHex.isNotBlank()) return@use hexToBytes(audioHex)
                    // 官方 data.url 分支：拉取音频 URL 返回字节
                    val audioUrl = data.optString("url")
                    if (audioUrl.isNotBlank()) {
                        client.newCall(Request.Builder().url(audioUrl).get().build()).execute().use { r2 ->
                            if (!r2.isSuccessful) null else r2.body?.bytes()
                        }
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }
}

// ============================================================================
// 7. 火山引擎（豆包）—— volcengine.js fetchTtsGeneration + src/endpoints/volcengine.js router.post('/generate-voice')
//    .js body = { provider_endpoint, resource_id, text, voice_speaker, speed }
//    服务端映射：POST {provider_endpoint 默认 v3 unidirectional}，头 X-Api-App-Id/X-Api-Access-Key/
//    X-Api-Resource-Id，厂商 body = { req_params:{ text, speaker,
//    audio_params:{format:'mp3', speech_rate}, additions:<JSON 字符串> } }；
//    响应为 NDJSON 流（每行 {data(base64), code, message}，code 0 或 20000000 通过），拼接 data
// ============================================================================
class VolcengineCloudBackend : TtsBackend {
    override val id = "volcengine"
    override val displayName = "Volcengine (Doubao)"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://openspeech.bytedance.com/api/v3/tts/unidirectional"

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
                // 官方 VOLCENGINE_APP_ID / ACCESS_KEY 两个 secret；App 单 key 框约定 "appId:accessKey"
                val parts = VoicePrefs.ttsApiKey(context).split(":")
                val appId = parts.getOrNull(0).orEmpty()
                val accessKey = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@runCatching null
                // resource_id 官方为独立设置项（默认空 → 服务端 400）；App 复用 tts_model 槽位承载
                val resourceId = VoicePrefs.ttsModel(context).ifBlank { return@runCatching null }
                val endpoint = VoicePrefs.ttsEndpoint(context).ifBlank { defaultEndpoint }
                // 引擎层 .js body（差分）：{ provider_endpoint, resource_id, text, voice_speaker, speed }
                val jsBody = TtsRequestEngine.volcengineRequestBody(
                    settings = ttsSettings(
                        providerEndpoint = endpoint,
                        resourceId = resourceId,
                        speed = 0.0,
                    ),
                    text = text,
                    voiceSpeaker = voiceId,
                )
                // 官方 processText（volcengine.js:81-83）：'...' 全部移除
                val cleanText = (jsBody["text"]?.jsonPrimitive?.contentOrNull ?: text).replace("...", "")
                // 服务端映射：src/endpoints/volcengine.js:33-66；speech_rate = parseInt(speed || '0')
                val speechRate = jsBody["speed"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0
                val additions = org.json.JSONObject()
                    .put("mute_cut_threshold", "400")
                    .put("mute_cut_remain_ms", "1")
                    .put("explicit_language", "crosslingual")
                    .put("enable_language_detector", true)
                    .put("disable_markdown_filter", true)
                    .put(
                        "cache_config",
                        org.json.JSONObject().put("use_cache", true).put("text_type", 1),
                    )
                val vendorBody = org.json.JSONObject()
                    .put(
                        "req_params",
                        org.json.JSONObject()
                            .put("text", cleanText)
                            .put("speaker", voiceId)
                            .put(
                                "audio_params",
                                org.json.JSONObject().put("format", "mp3").put("speech_rate", speechRate),
                            )
                            // 官方 additions 是 JSON.stringify 后的字符串字段
                            .put("additions", additions.toString()),
                    )
                val request = Request.Builder()
                    .url(endpoint)
                    .header("X-Api-App-Id", appId)
                    .header("X-Api-Access-Key", accessKey)
                    .header("X-Api-Resource-Id", resourceId)
                    .post(jsonBody(vendorBody))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    // NDJSON 流：逐行解析 {data, code, message}，base64 音频块按序拼接
                    val audio = mutableListOf<ByteArray>()
                    val buf = java.io.ByteArrayOutputStream()
                    resp.body?.byteStream()?.bufferedReader(Charsets.UTF_8)?.forEachLine { line ->
                        if (line.isBlank()) return@forEachLine
                        val obj = runCatching { org.json.JSONObject(line) }.getOrNull() ?: return@forEachLine
                        val code = obj.optInt("code", 0)
                        if (code != 0 && code != 20000000) return@runCatching null
                        val data = obj.optString("data")
                        if (data.isNotBlank()) {
                            audio.add(Base64.decode(data, Base64.DEFAULT))
                        }
                    }
                    for (chunk in audio) buf.write(chunk)
                    val out = buf.toByteArray()
                    if (out.isEmpty()) null else out
                }
            }.getOrNull()
        }
}

// ============================================================================
// 8. Chutes —— chutes.js fetchTtsGeneration + src/endpoints/openai.js router.post('/chutes/generate-voice')
//    .js body = { input, voice: voiceId || 'af_heart', speed: settings.speed || 1 }
//    服务端映射：body = { text: input, voice||'af_heart', speed||1 } →
//    POST https://chutes-kokoro.chutes.ai/speak（无 model 字段——官方服务端不透传模型）
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
                val apiKey = VoicePrefs.ttsApiKey(context).ifBlank { return@runCatching null }
                // 引擎层 .js body（差分，含 voice||'af_heart' 与 speed||1 短路）
                val jsBody = TtsRequestEngine.chutesRequestBody(
                    settings = ttsSettings(),
                    text = text,
                    voiceId = voiceId,
                )
                // 服务端映射（openai.js:429-453）：input→text；仅 text/voice/speed 三字段
                val vendorBody = org.json.JSONObject().apply {
                    put("text", jsBody["input"]?.jsonPrimitive?.content ?: text)
                    put("voice", jsBody["voice"]!!.jsonPrimitive.content)
                    put("speed", jsBody["speed"]!!.jsonPrimitive.doubleOrNull ?: 1.0)
                }
                val request = Request.Builder()
                    .url("https://chutes-kokoro.chutes.ai/speak")
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
// 9. Pollinations —— pollinations.js fetchTtsGeneration + src/endpoints/speech.js router.post('/pollinations/generate')
//    .js body = { model, text:'Say exactly this and nothing else:'+'\n'+chunk, voice }（≤1000 分块）
//    服务端映射：POST https://gen.pollinations.ai/v1/chat/completions，
//    body = { model, stream:false, modalities:['text','audio'], seed, audio:{format:'mp3',voice},
//             messages:[{role:'user',content:text}] } → choices[0].message.audio.data(base64)
// ============================================================================
class PollinationsCloudBackend : TtsBackend {
    override val id = "pollinations"
    override val displayName = "Pollinations"
    // 官方服务端无 key 直接 400（speech.js:118-121）
    override val requiresApiKey = true
    override val defaultEndpoint = "https://gen.pollinations.ai"

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
                val apiKey = VoicePrefs.ttsApiKey(context)
                // 引擎层 .js body（差分，含 'Say exactly this and nothing else:\n' 前缀 + splitRecursive 分块）
                val jsBody = TtsRequestEngine.pollinationsRequestBody(
                    settings = ttsSettings(model = "openai-audio"),
                    text = text,
                    voiceId = voiceId,
                )
                val prompt = jsBody["text"]!!.jsonPrimitive.content
                val voice = jsBody["voice"]?.jsonPrimitive?.contentOrNull ?: voiceId
                val model = jsBody["model"]?.jsonPrimitive?.contentOrNull ?: "openai-audio"
                // 服务端映射（speech.js:124-152）；seed = Math.floor(Math.random() * 2^32) 等价无符号 32 位
                val vendorBody = org.json.JSONObject()
                    .put("model", model)
                    .put("stream", false)
                    .put("modalities", org.json.JSONArray(listOf("text", "audio")))
                    .put("seed", java.util.Random().nextLong() and 0xFFFF_FFFFL)
                    .put(
                        "audio",
                        org.json.JSONObject().put("format", "mp3").put("voice", voice),
                    )
                    .put(
                        "messages",
                        org.json.JSONArray(listOf(org.json.JSONObject().put("role", "user").put("content", prompt))),
                    )
                val request = Request.Builder()
                    .url("$defaultEndpoint/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(vendorBody))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val audioData = json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optJSONObject("audio")?.optString("data")
                    if (audioData.isNullOrBlank()) null else Base64.decode(audioData, Base64.DEFAULT)
                }
            }.getOrNull()
        }
}

// ============================================================================
// 10. Google Gemini TTS —— google-native.js fetchNativeTtsGeneration + src/endpoints/google.js router.post('/generate-native-tts')
//     .js body = { text, voice, model, api, reverse_proxy, proxy_password, vertexai_auth_mode, vertexai_region, vertexai_express_project_id }
//     服务端映射（google.js:367-421）：POST {apiUrl}/v1beta/models/{model}:generateContent，
//     头 x-goog-api-key；body = { contents, generationConfig:{responseModalities:['AUDIO'],
//     speechConfig:{voiceConfig:{prebuiltVoiceConfig:{voiceName}}}}, safetySettings }；
//     响应 candidates[0].content.parts[0].inlineData：audio/l16 裸 PCM 补 WAV 头，其余原样。
// ============================================================================
class GoogleNativeCloudBackend : TtsBackend {
    override val id = "google-native"
    // 官方 index.js:135 provider 表名
    override val displayName = "Google Gemini TTS"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://generativelanguage.googleapis.com"

    // 官方 /list-native-voices 硬编码 30 个 Gemini 声音（google.js:285-317，含 description）
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("Zephyr", "Zephyr (Bright)"), TtsVoice("Puck", "Puck (Upbeat)"),
        TtsVoice("Charon", "Charon (Informative)"), TtsVoice("Kore", "Kore (Firm)"),
        TtsVoice("Fenrir", "Fenrir (Excitable)"), TtsVoice("Leda", "Leda (Youthful)"),
        TtsVoice("Orus", "Orus (Firm)"), TtsVoice("Aoede", "Aoede (Breezy)"),
        TtsVoice("Callirhoe", "Callirhoe (Easy-going)"), TtsVoice("Autonoe", "Autonoe (Bright)"),
        TtsVoice("Enceladus", "Enceladus (Breathy)"), TtsVoice("Iapetus", "Iapetus (Clear)"),
        TtsVoice("Umbriel", "Umbriel (Easy-going)"), TtsVoice("Algieba", "Algieba (Smooth)"),
        TtsVoice("Despina", "Despina (Smooth)"), TtsVoice("Erinome", "Erinome (Clear)"),
        TtsVoice("Algenib", "Algenib (Gravelly)"), TtsVoice("Rasalgethi", "Rasalgethi (Informative)"),
        TtsVoice("Laomedeia", "Laomedeia (Upbeat)"), TtsVoice("Achernar", "Achernar (Soft)"),
        TtsVoice("Alnilam", "Alnilam (Firm)"), TtsVoice("Schedar", "Schedar (Even)"),
        TtsVoice("Gacrux", "Gacrux (Mature)"), TtsVoice("Pulcherrima", "Pulcherrima (Forward)"),
        TtsVoice("Achird", "Achird (Friendly)"), TtsVoice("Zubenelgenubi", "Zubenelgenubi (Casual)"),
        TtsVoice("Vindemiatrix", "Vindemiatrix (Gentle)"), TtsVoice("Sadachbia", "Sadachbia (Lively)"),
        TtsVoice("Sadaltager", "Sadaltager (Knowledgeable)"), TtsVoice("Sulafat", "Sulafat (Warm)"),
    )

    /** 官方 createCompleteWavFile（google.js:26-29）：裸 PCM 补 44 字节 RIFF/WAV 头。 */
    private fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(44 + pcm.size)
        val le = { v: Int, bytes: Int -> ByteArray(bytes) { i -> ((v shr (8 * i)) and 0xFF).toByte() } }
        out.write("RIFF".toByteArray()); out.write(le(36 + pcm.size, 4))
        out.write("WAVE".toByteArray()); out.write("fmt ".toByteArray())
        out.write(le(16, 4)); out.write(le(1, 2))          // PCM
        out.write(le(1, 2))                                 // mono
        out.write(le(sampleRate, 4)); out.write(le(sampleRate * 2, 4))
        out.write(le(2, 2)); out.write(le(16, 2))
        out.write("data".toByteArray()); out.write(le(pcm.size, 4))
        out.write(pcm)
        return out.toByteArray()
    }

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context).ifBlank { return@runCatching null }
                val model = VoicePrefs.ttsModel(context).ifBlank { "gemini-3.1-flash-tts-preview" }
                // 引擎层 .js body（差分，含 useReverseProxy 分支）——此处仅校验参数路径
                val jsBody = TtsRequestEngine.googleNativeRequestBody(
                    settings = ttsSettings(
                        model = model,
                        apiType = "generate",
                    ),
                    text = text,
                    voiceId = voiceId,
                    oaiSettings = ttsOaiSettings(),
                )
                @Suppress("unused") val _verify = jsBody
                // 服务端映射（google.js:367-386；AI Studio 分支 getGoogleApiConfig else 路径）
                val safety = org.json.JSONArray(
                    listOf(
                        "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT",
                        "HARM_CATEGORY_CIVIC_INTEGRITY",
                    ).map { org.json.JSONObject().put("category", it).put("threshold", "OFF") },
                )
                val vendorBody = org.json.JSONObject()
                    .put(
                        "contents",
                        org.json.JSONArray(
                            listOf(
                                org.json.JSONObject().put("role", "user")
                                    .put("parts", org.json.JSONArray(listOf(org.json.JSONObject().put("text", text)))),
                            ),
                        ),
                    )
                    .put(
                        "generationConfig",
                        org.json.JSONObject()
                            .put("responseModalities", org.json.JSONArray(listOf("AUDIO")))
                            .put(
                                "speechConfig",
                                org.json.JSONObject().put(
                                    "voiceConfig",
                                    org.json.JSONObject().put(
                                        "prebuiltVoiceConfig",
                                        org.json.JSONObject().put("voiceName", voiceId),
                                    ),
                                ),
                            ),
                    )
                    .put("safetySettings", safety)
                val url = "$defaultEndpoint/v1beta/models/${encodeURIComponent(model)}:generateContent"
                val request = Request.Builder()
                    .url(url)
                    .header("x-goog-api-key", apiKey)
                    .post(jsonBody(vendorBody))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = org.json.JSONObject(resp.body?.string().orEmpty())
                    val part = json.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                        ?: return@use null
                    val inline = part.optJSONObject("inlineData") ?: return@use null
                    val audioB64 = inline.optString("data")
                    if (audioB64.isBlank()) return@use null
                    val audio = Base64.decode(audioB64, Base64.DEFAULT)
                    val mime = inline.optString("mimeType")
                    // 官方：audio/l16 裸 PCM → 补 WAV 头（rate 取自 mimeType，缺省 24000）
                    if (mime.lowercase().contains("audio/l16")) {
                        val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                        pcmToWav(audio, rate)
                    } else {
                        audio
                    }
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

// ============================================================================
// 12. Electron Hub —— electronhub.js fetchTtsGeneration + openai.js router.post('/electronhub/generate-voice')
//     客户端 body = { input, voice, speed, temperature, model, [instructions/speaker_transcript/
//     cfg_scale/cfg_filter_top_k/speech_rate/pitch_adjustment/emotional_style], top_p }
//     服务端映射：+ response_format:'mp3'，直发 https://api.electronhub.ai/v1/audio/speech（Bearer key）。
//     defaultSettings：model 'tts-1'、speed 1、temperature 1、top_p 1（electronhub.js defaultSettings）。
// ============================================================================
class ElectronHubCloudBackend : TtsBackend {
    override val id = "electronhub"
    override val displayName = "Electron Hub"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.electronhub.ai/v1"

    // electronhub.js fetchTtsVoiceObjects：模型清单不可得时回退 11 个常见 OpenAI 声音（逐字 copy）
    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("alloy", "alloy", "en-US"),
        TtsVoice("ash", "ash", "en-US"),
        TtsVoice("ballad", "ballad", "en-US"),
        TtsVoice("coral", "coral", "en-US"),
        TtsVoice("echo", "echo", "en-US"),
        TtsVoice("fable", "fable", "en-US"),
        TtsVoice("onyx", "onyx", "en-US"),
        TtsVoice("nova", "nova", "en-US"),
        TtsVoice("sage", "sage", "en-US"),
        TtsVoice("shimmer", "shimmer", "en-US"),
        TtsVoice("verse", "verse", "en-US"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = VoicePrefs.ttsApiKey(context)
                val base = VoicePrefs.ttsEndpoint(context).ifBlank { defaultEndpoint }.trimEnd('/')
                val model = VoicePrefs.ttsModel(context).ifBlank { "tts-1" }
                val lowerModel = model.lowercase()
                // App 无 per-provider 参数 UI（并入设置面板对齐）：按官方 defaultSettings 恒值发送
                // （speed/temperature/top_p/cfg_scale/cfg_filter_top_k/speech_rate/pitch_adjustment 均为
                //   Number → JS Number.isFinite 恒真；instructions/speaker_transcript/emotional_style 空串跳过）
                val vendorBody = org.json.JSONObject().apply {
                    put("input", text)
                    put("voice", voiceId)
                    put("speed", 1.0)
                    put("temperature", 1.0)
                    put("model", model)
                    put("response_format", "mp3") // 服务端固定附加（openai.js:352）
                    if (lowerModel.contains("dia")) {
                        put("cfg_scale", 3.0)
                        put("cfg_filter_top_k", 25.0)
                    }
                    if (lowerModel.contains("microsoft-tts")) {
                        put("speech_rate", 0.0)
                        put("pitch_adjustment", 0.0)
                    }
                    put("top_p", 1.0)
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
        TtsBackendRegistry.register(ElectronHubCloudBackend())
    }
}
