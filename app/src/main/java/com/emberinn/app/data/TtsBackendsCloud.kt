package com.emberinn.app.data

import android.content.Context
import android.util.Base64
import com.emberinn.app.ui.settings.VoicePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 云端 TTS 后端集合：1:1 对照官方 SillyTavern 源码
 * public/scripts/extensions/tts/{后端}.js 的 fetchTtsGeneration 函数翻译成 Kotlin。
 *
 * 注意：官方 .js 走 SillyTavern 服务端代理（/api/speech/...、/api/openai/generate-voice 等），
 * 代理内部再转发到各云厂商真实端点。Android 端无该代理，故此处改为直连各厂商官方端点
 * （端点/Hdr 来自 fetchTtsGeneration 所调用代理对应的服务端实现 + 各厂商公开 API 文档），
 * 请求体字段与官方 .js fetchTtsGeneration 构造的 JSON 字段名保持一致。
 */
private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/** 官方 .js fetchTtsGeneration 走代理时通常把 body 用 JSON.stringify 包成字符串发后端，再转厂商真实 body。 */
private fun jsonBody(json: JSONObject) = json.toString().toRequestBody(JSON_MEDIA)

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

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

// ============================================================================
// 1. ElevenLabs —— elevenlabs.js fetchTtsGeneration
//    .js body(发给代理) = { voiceId, request:{ model_id, text,
//      voice_settings:{ stability, similarity_boost, speed, [style, use_speaker_boost] } } }
//    直连 ElevenLabs：POST {base}/v1/text-to-speech/{voice}  body = request 部分。
// ============================================================================
class ElevenLabsCloudBackend : TtsBackend {
    override val id = "elevenlabs"
    override val displayName = "ElevenLabs"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.elevenlabs.io"

    // elevenlabs.js 无静态 voice 列表（运行时 GET /api/speech/elevenlabs/voices 拉取账号下的声音）。
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
                val model = VoicePrefs.ttsModel(context).ifBlank { "eleven_turbo_v2_5" }
                // .js voice_settings 默认值（elevenlabs.js defaultSettings）
                val voiceSettings = JSONObject()
                    .put("stability", 0.75)
                    .put("similarity_boost", 0.75)
                    .put("style", 0.0)
                    .put("use_speaker_boost", true)
                    .put("speed", 1.0)
                val body = JSONObject()
                    .put("model_id", model)
                    .put("text", text)
                    .put("voice_settings", voiceSettings)
                val request = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/${enc(voiceId)}")
                    .header("xi-api-key", apiKey)
                    .header("Accept", "audio/mpeg")
                    .post(jsonBody(body))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 2. OpenAI —— openai.js fetchTtsGeneration
//    .js body(发给代理 /api/openai/generate-voice) = { text, voice, model, speed, [instructions] }
//    直连 OpenAI：POST {base}/audio/speech  body 同上（厂商字段为 input；这里用 input 对齐厂商，
//    同时保留 .js 的 voice/model/speed）。
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
                val model = VoicePrefs.ttsModel(context).ifBlank { "tts-1" }
                val body = JSONObject()
                    .put("model", model)
                    .put("voice", voiceId)
                    .put("input", text)
                    .put("speed", 1)
                val request = Request.Builder()
                    .url("$base/audio/speech")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(body))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 3. Edge —— edge.js fetchTtsGeneration
//    .js body(发给代理 /api/edge-tts/generate) = { text, voice, rate }
//    edge.js 走 SillyTavern Extras 的 edge-tts 模块（WebSocket）。Android 端无 Extras，
//    故用任务指定的轻量兼容端点：GET https://api.tts-lab.workers.dev/?voice=&text=。
// ============================================================================
class EdgeCloudBackend : TtsBackend {
    override val id = "edge"
    override val displayName = "Microsoft Edge TTS"
    override val requiresApiKey = false
    override val defaultEndpoint = "https://api.tts-lab.workers.dev"

    // edge.js 通过 /api/edge-tts/list 拉取完整 ShortName 列表（无静态 voice 数组）。
    // 这里给出 en-US 下常用神经声音作为占位。
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
                val url = "$endpoint/?voice=${enc(voiceId)}&text=${enc(text)}"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 4. Azure —— azure.js fetchTtsGeneration
//    .js body(发给代理 /api/azure/generate) = { text, voice, region }
//    直连 Azure：POST https://{region}.tts.speech.microsoft.com/cognitiveservices/v1
//    body = SSML；Authorization: Bearer key；X-Microsoft-OutputFormat: audio-16khz-128kbitrate-mono-mp3
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
                // voiceId 形如 "en-US-AriaNeural" → lang = "en-US"
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
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 5. NovelAI —— novel.js fetchTtsGeneration
//    .js body(发给代理 /api/novelai/generate-voice) = { text, voice }
//    直连 NovelAI：GET https://api.novelai.net/ai/generate-voice?text=&voice=-1&seed=
//    Authorization: Bearer apiKey（voice_id 在官方 NovelAPI 中作为 seed 传 voice=-1）
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
                // Novel 文本上限 1000（novel.js splitRecursive MAX_LENGTH），此处直接发整段。
                val url = "https://api.novelai.net/ai/generate-voice?text=${enc(text)}&voice=-1&seed=${enc(voiceId)}"
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
// 6. MiniMax —— minimax.js fetchTtsGeneration
//    .js body(发给代理 /api/minimax/generate-voice) = { text, voiceId, apiHost, model, speed,
//      volume, pitch, audioSampleRate, bitrate, format, language }
//    直连 MiniMax：POST https://{host}/v1/t2a_v2  Authorization: Bearer apiKey
//    body = { model, voice_id, text, language, speed, pitch }  → data.audio(hex)
// ============================================================================
class MinimaxCloudBackend : TtsBackend {
    override val id = "minimax"
    override val displayName = "MiniMax"
    override val requiresApiKey = true
    override val defaultEndpoint = "https://api.minimax.io"

    // minimax.js static defaultVoices（1 个）
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
                val model = VoicePrefs.ttsModel(context).ifBlank { "speech-02-hd" }
                val body = JSONObject()
                    .put("model", model)
                    .put("voice_id", voiceId)
                    .put("text", text)
                    .put("language", "en_US")
                    .put("speed", 1.0)
                    .put("pitch", 0)
                val request = Request.Builder()
                    .url("$host/v1/t2a_v2")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(body))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val data = json.optJSONObject("data") ?: return@use null
                    val audioHex = data.optString("audio")
                    if (audioHex.isBlank()) null else hexToBytes(audioHex)
                }
            }.getOrNull()
        }
}

// ============================================================================
// 7. 火山引擎（豆包）—— volcengine.js fetchTtsGeneration
//    .js body(发给代理 /api/volcengine/generate-voice) =
//      { provider_endpoint, resource_id, text, voice_speaker, speed }
//    直连火山：POST https://openspeech.bytedance.com/api/v1/tts  Authorization: Bearer key
//    body = { app:{ appid, token }, audio:{ voice_type, encoding }, request:{ text } } → data.audio(hex)
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
                // 火山 appid/token 对应 SillyTavern secret VOLCENGINE_APP_ID / ACCESS_KEY：
                // Android 端把 apiKey 拆为 "appid:token"；若未拆则整串当 token、appid 取首段。
                val parts = apiKey.split(":")
                val appid = parts.getOrNull(0).orEmpty()
                val token = parts.getOrNull(1) ?: apiKey
                val body = JSONObject()
                    .put("app", JSONObject().put("appid", appid).put("token", token))
                    .put("audio", JSONObject().put("voice_type", voiceId).put("encoding", "mp3"))
                    .put("request", JSONObject().put("text", text))
                val request = Request.Builder()
                    .url("https://openspeech.bytedance.com/api/v1/tts")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(body))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val data = json.optJSONObject("data") ?: return@use null
                    val audioHex = data.optString("audio")
                    if (audioHex.isBlank()) null else hexToBytes(audioHex)
                }
            }.getOrNull()
        }
}

// ============================================================================
// 8. Chutes —— chutes.js fetchTtsGeneration
//    .js body(发给代理 /api/openai/chutes/generate-voice) = { input, voice, speed }
//    直连 Chutes：POST https://api.chutes.ai/v1/tts  Authorization: Bearer apiKey
//    body = { model, voice, input }（model 默认 kokoro）
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
                val body = JSONObject()
                    .put("model", model)
                    .put("voice", voiceId.ifBlank { "af_heart" })
                    .put("input", text)
                val request = Request.Builder()
                    .url("https://api.chutes.ai/v1/tts")
                    .header("Authorization", "Bearer $apiKey")
                    .post(jsonBody(body))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 9. Pollinations —— pollinations.js fetchTtsGeneration
//    .js body(发给代理 /api/speech/pollinations/generate) =
//      { model, text:'Say exactly this and nothing else:\n'+chunk, voice }
//    直连 Pollinations：GET https://text.pollinations.ai/{prompt}?model=openai-audio&voice={voice}
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
                val prompt = "Say exactly this and nothing else:\n$text"
                val url = "https://text.pollinations.ai/${enc(prompt)}?model=openai-audio&voice=${enc(voiceId)}"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                }
            }.getOrNull()
        }
}

// ============================================================================
// 10. Google Cloud TTS —— google-native.js fetchNativeTtsGeneration
//     .js body(发给代理 /api/google/generate-native-tts) = { text, voice, model, api,
//       reverse_proxy, proxy_password, vertexai_auth_mode, vertexai_region, vertexai_express_project_id }
//     直连 Google Cloud TTS：POST https://texttospeech.googleapis.com/v1/text:synthesize
//     x-goog-api-key; body = { input:{text}, voice:{ languageCode, name }, audioConfig:{ audioEncoding:MP3 } }
//     → audioContent(base64)
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
                val body = JSONObject()
                    .put("input", JSONObject().put("text", text))
                    .put("voice", JSONObject().put("languageCode", lang).put("name", voiceId))
                    .put("audioConfig", JSONObject().put("audioEncoding", "MP3"))
                val request = Request.Builder()
                    .url("https://texttospeech.googleapis.com/v1/text:synthesize")
                    .header("x-goog-api-key", apiKey)
                    .post(jsonBody(body))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val b64 = json.optString("audioContent")
                    if (b64.isBlank()) null else Base64.decode(b64, Base64.DEFAULT)
                }
            }.getOrNull()
        }
}

// ============================================================================
// 11. Google Translate TTS —— google-translate.js fetchTtsGeneration
//     .js body(发给代理 /api/google/generate-voice) = { text: splitRecursive(text,200), voice }
//     直连：GET https://translate.google.com/translate_tts?ie=UTF-8&q={text}&tl={lang}&client=tw-ob
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
                // google-translate.js 用 splitRecursive(text, 200) 分块；此处取首段（≤200 字符）。
                val chunk = if (text.length > 200) text.substring(0, 200) else text
                val lang = if (voiceId.isNotBlank()) voiceId else "en-US"
                val url = "https://translate.google.com/translate_tts?ie=UTF-8&q=${enc(chunk)}&tl=${enc(lang)}&client=tw-ob"
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
