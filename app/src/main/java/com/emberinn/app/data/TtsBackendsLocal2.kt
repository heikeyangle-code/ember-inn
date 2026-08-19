package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.VoicePrefs
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 第二批本地 TTS 后端：1:1 对照官方 public/scripts/extensions/tts/{后端}.js 的 fetchTtsGeneration
 * 翻译成 Kotlin。共用文件级 OkHttpClient（connect 15s / read 120s）；generateTts 失败统一返回 null。
 * 路由分发由 TtsBackendRegistry（见 TtsBackend.kt）按 provider id 处理。
 *
 * 注：sbvits2 / vits 的官方 fetchTtsGeneration 用 URLSearchParams（query string / form-urlencoded），
 * 非 JSON body——此处忠实保留原传输方式（用 URLEncoder 构造），未强行改写成 JSON。
 */

private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

private val jsonMedia = "application/json; charset=utf-8".toMediaType()
private val formMedia = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

private fun endpoint(context: Context, fallback: String): String =
    VoicePrefs.ttsEndpoint(context).trim().ifBlank { fallback }

private fun model(context: Context, fallback: String): String =
    VoicePrefs.ttsModel(context).trim().ifBlank { fallback }

private fun apiKey(context: Context): String = VoicePrefs.ttsApiKey(context).trim()

private fun enc(v: Any): String = URLEncoder.encode(v.toString(), "UTF-8")

// 1) kokoro-worker — 官方是 WebWorker（kokoro-worker.js，无 fetchTtsGeneration），generateTts 内部
//    tts.generate(text,{voice,speed})；按需求简化为 POST {endpoint}/tts {text,voice,speaking_rate}。
class KokoroWorkerTtsBackend : TtsBackend {
    override val id = "kokoro-worker"
    override val displayName = "Kokoro (Worker)"
    override val defaultEndpoint = "http://localhost:8881"

    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("af_sky", "af_sky (American Female)"),
        TtsVoice("af_bella", "af_bella (American Female)"),
        TtsVoice("af_nicole", "af_nicole (American Female)"),
        TtsVoice("am_michael", "am_michael (American Male)"),
        TtsVoice("am_adam", "am_adam (American Male)"),
        TtsVoice("bf_emma", "bf_emma (British Female)", "en-GB"),
        TtsVoice("bm_george", "bm_george (British Male)", "en-GB"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("text", text)
                    .put("voice", voiceId)
                    .put("speaking_rate", 1.0)
                    .toString()
                val request = Request.Builder()
                    .url(endpoint(context, defaultEndpoint).trimEnd('/') + "/tts")
                    .post(body.toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 2) sbvits2 — sbvits2.js fetchTtsGeneration：POST {endpoint}/voice?{query}（URLSearchParams，无 body）。
//    字段：text, model_id, speaker_id, sdp_ratio, noise, noisew, length, language, auto_split,
//    split_interval, [assist_text, assist_text_weight], style, style_weight, [reference_audio_path]。
//    voiceId 格式 model_id-speaker_id-style → split('-')。
class SbVits2TtsBackend : TtsBackend {
    override val id = "sbvits2"
    override val displayName = "Style-Bert-VITS2"
    override val defaultEndpoint = "http://localhost:5000"

    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                // 官方：inputText.replaceAll('<br>', '\n')
                val inputText = text.replace("<br>", "\n")
                // 官方：const [model_id, speaker_id, ...rest] = voiceId.split('-'); style = rest.join('-')
                val parts = voiceId.split("-")
                val modelId = parts.getOrElse(0) { "0" }
                val speakerId = parts.getOrElse(1) { "0" }
                val style = if (parts.size > 2) parts.drop(2).joinToString("-") else "Neutral"
                // 官方默认值（defaultSettings）
                val sdpRatio = 0.2
                val noise = 0.6
                val noisew = 0.8
                val length = 1
                val language = "JP"
                val autoSplit = true
                val splitInterval = 0.5
                val styleWeight = 1
                val assistText = ""
                val referenceAudioPath = ""

                val params = mutableListOf<Pair<String, Any>>()
                params += "text" to inputText
                params += "model_id" to modelId
                params += "speaker_id" to speakerId
                params += "sdp_ratio" to sdpRatio
                params += "noise" to noise
                params += "noisew" to noisew
                params += "length" to length
                params += "language" to language
                params += "auto_split" to autoSplit
                params += "split_interval" to splitInterval
                if (assistText.isNotEmpty()) {
                    params += "assist_text" to assistText
                    params += "assist_text_weight" to 1
                }
                params += "style" to style
                params += "style_weight" to styleWeight
                if (referenceAudioPath.isNotEmpty()) {
                    params += "reference_audio_path" to referenceAudioPath
                }
                val query = params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
                // 官方：fetch(url, {method:'POST', headers:{}}) — 无 body
                val request = Request.Builder()
                    .url(endpoint(context, defaultEndpoint).trimEnd('/') + "/voice?" + query)
                    .post("".toRequestBody())
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 3) silerotts — silerotts.js fetchTtsGeneration：POST {endpoint}/generate，JSON
//    {text, speaker: voiceId, session: 'sillytavern'}，Content-Type application/json + Cache-Control no-cache。
//    注：官方默认 endpoint = http://localhost:8001/tts（含 /tts），故完整 URL = .../tts/generate。
class SileroTtsBackend : TtsBackend {
    override val id = "silerotts"
    override val displayName = "Silero"
    override val defaultEndpoint = "http://localhost:8001/tts"

    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("kseniya", "kseniya", "ru-RU"),
        TtsVoice("aidar", "aidar", "ru-RU"),
        TtsVoice("baya", "baya", "ru-RU"),
        TtsVoice("irina", "irina", "ru-RU"),
        TtsVoice("pavel", "pavel", "ru-RU"),
        TtsVoice("ruslan", "ruslan", "ru-RU"),
        TtsVoice("natasha", "natasha", "ru-RU"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("text", text)
                    .put("speaker", voiceId)
                    .put("session", "sillytavern")
                    .toString()
                val request = Request.Builder()
                    .url(endpoint(context, defaultEndpoint).trimEnd('/') + "/generate")
                    .post(body.toRequestBody(jsonMedia))
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 4) speecht5 — speecht5.js fetchTtsGeneration：官方走 ST 内部代理 /api/speech/synthesize，
//    body {text, speaker: speaker.data, model: 'Xenova/speecht5_tts'}。speaker 字段实为 2048 字节说话人
//    嵌入（base64），无内置默认 speaker；此处用 voiceId 作 speaker，model 取官方默认（可被 VoicePrefs.ttsModel 覆盖）。
//    按需求独立部署采用 {endpoint}/tts 路径（官方 /api/speech/synthesize 仅 ST 服务端可用）。
class SpeechT5TtsBackend : TtsBackend {
    override val id = "speecht5"
    override val displayName = "SpeechT5"
    override val defaultEndpoint = "http://localhost:5000"

    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("default", "default", "en-US"),
        TtsVoice("p226", "p226", "en-US"),
        TtsVoice("p230", "p230", "en-US"),
        TtsVoice("p233", "p233", "en-US"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("text", text)
                    .put("speaker", voiceId)
                    .put("model", model(context, "Xenova/speecht5_tts"))
                    .toString()
                val request = Request.Builder()
                    .url(endpoint(context, defaultEndpoint).trimEnd('/') + "/tts")
                    .post(body.toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 5) tts-webui — tts-webui.js fetchTtsGeneration：直接 POST 到 settings.provider_endpoint（完整 URL），
//    JSON {model, voice, input, response_format:'wav', speed, stream, params:{...chatterbox...}}。
//    官方默认 endpoint = http://127.0.0.1:7778/v1/audio/speech。stream=false 返回完整音频字节。
class TtsWebuiTtsBackend : TtsBackend {
    override val id = "tts-webui"
    override val displayName = "TTS WebUI"
    override val defaultEndpoint = "http://127.0.0.1:7778/v1/audio/speech"

    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    /** 官方 chatterbox 参数子对象（defaultSettings）。 */
    private fun chatterboxParams(): JSONObject = JSONObject()
        .put("desired_length", 80)
        .put("max_length", 200)
        .put("halve_first_chunk", true)
        .put("exaggeration", 0.5)
        .put("cfg_weight", 0.5)
        .put("temperature", 0.8)
        .put("device", "auto")
        .put("dtype", "float32")
        .put("cpu_offload", false)
        .put("chunked", true)
        .put("cache_voice", false)
        .put("tokens_per_slice", 1000)
        .put("remove_milliseconds", 45)
        .put("remove_milliseconds_start", 25)
        .put("chunk_overlap_method", "zero")
        .put("seed", -1)

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("model", model(context, "chatterbox"))
                    .put("voice", voiceId)
                    .put("input", text)
                    .put("response_format", "wav")
                    .put("speed", 1)
                    .put("stream", false)
                    .put("params", chatterboxParams())
                    .toString()
                val request = Request.Builder()
                    .url(endpoint(context, defaultEndpoint))
                    .post(body.toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 6) vits — vits.js fetchTtsGeneration：POST {endpoint}/voice/{model_type 小写}，
//    Content-Type application/x-www-form-urlencoded，body = URLSearchParams（text, id=speaker_id, format,
//    lang, length, noise, noisew, segment_size + W2V2/BERT-VITS2 条件字段）。voiceId 格式 model_type&id。
class VitsTtsBackend : TtsBackend {
    override val id = "vits"
    override val displayName = "VITS"
    override val defaultEndpoint = "http://localhost:23456"

    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                // 官方：const [model_type, speaker_id] = voiceId.split('&');
                val amp = voiceId.split("&", limit = 2)
                val modelType = if (amp.size == 2) amp[0] else "VITS"
                val speakerId = if (amp.size == 2) amp[1] else voiceId
                // 官方默认值（defaultSettings）
                val format = "wav"
                val lang = "auto"
                val length = 1.0
                val noise = 0.33
                val noisew = 0.4
                val segmentSize = 50
                val dimEmotion = 0
                val sdpRatio = 0.2
                val emotion = 0
                val textPrompt = ""
                val styleText = ""
                val styleWeight = 1

                val params = mutableListOf<Pair<String, Any>>()
                params += "text" to text
                params += "id" to speakerId
                // streaming=false → 走 format 分支
                params += "format" to format
                params += "lang" to lang
                params += "length" to length
                params += "noise" to noise
                params += "noisew" to noisew
                params += "segment_size" to segmentSize
                // 官方：model_type 分支
                when (modelType) {
                    "W2V2-VITS" -> params += "emotion" to dimEmotion
                    "BERT-VITS2" -> {
                        params += "sdp_ratio" to sdpRatio
                        params += "emotion" to emotion
                        if (textPrompt.isNotEmpty()) params += "text_prompt" to textPrompt
                        if (styleText.isNotEmpty()) {
                            params += "style_text" to styleText
                            params += "style_weight" to styleWeight
                        }
                    }
                }
                val formBody = params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
                val request = Request.Builder()
                    .url(endpoint(context, defaultEndpoint).trimEnd('/') + "/voice/" + modelType.lowercase())
                    .post(formBody.toRequestBody(formMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 7) xtts — xtts.js fetchTtsGeneration（非流式）：POST {endpoint}/tts_to_audio/，JSON
//    {text, speaker_wav: voiceId, language}，Content-Type application/json + Cache-Control no-cache。
//    注：speed/temperature 在官方走单独的 /set_tts_settings 调用，不在 fetchTtsGeneration 请求体内。
class XttsTtsBackend : TtsBackend {
    override val id = "xtts"
    override val displayName = "XTTS"
    override val defaultEndpoint = "http://localhost:8020"

    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                // 官方 processText：省略号/引号/多点规整
                val inputText = text
                    .replace("…", "...")
                    .replace(Regex("[\"“”‘’]"), "")
                    .replace(Regex("\\.+"), ".")
                val body = JSONObject()
                    .put("text", inputText)
                    .put("speaker_wav", voiceId)
                    .put("language", "en")
                    .toString()
                val request = Request.Builder()
                    .url(endpoint(context, defaultEndpoint).trimEnd('/') + "/tts_to_audio/")
                    .post(body.toRequestBody(jsonMedia))
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 8) openai-compatible — openai-compatible.js fetchTtsGeneration：官方走 ST 代理
//    /api/openai/custom/generate-voice，body {provider_endpoint, model, input, voice, response_format:'mp3', speed}。
//    独立部署直接 POST 到 provider_endpoint（OpenAI 兼容 /v1/audio/speech 全 URL），body {model, input, voice,
//    response_format, speed}；available_voices 官方硬编码 ['alloy','echo','fable','onyx','nova','shimmer']。
class OpenAiCompatibleTtsBackend : TtsBackend {
    override val id = "openai-compatible"
    override val displayName = "OpenAI Compatible"
    override val defaultEndpoint = "http://127.0.0.1:8000/v1/audio/speech"
    override val requiresApiKey = true

    override suspend fun getVoices(context: Context): List<TtsVoice> = listOf(
        TtsVoice("alloy", "alloy", "en-US"),
        TtsVoice("echo", "echo", "en-US"),
        TtsVoice("fable", "fable", "en-US"),
        TtsVoice("onyx", "onyx", "en-US"),
        TtsVoice("nova", "nova", "en-US"),
        TtsVoice("shimmer", "shimmer", "en-US"),
    )

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("model", model(context, "tts-1"))
                    .put("input", text)
                    .put("voice", voiceId)
                    .put("response_format", "mp3")
                    .put("speed", 1)
                    .toString()
                val builder = Request.Builder()
                    .url(endpoint(context, defaultEndpoint))
                    .post(body.toRequestBody(jsonMedia))
                val key = apiKey(context)
                if (key.isNotEmpty()) builder.header("Authorization", "Bearer $key")
                client.newCall(builder.build()).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

/** 注册第二批 8 个本地 TTS 后端。在 App 启动时被引用即触发 init。 */
object TtsBackendsLocal2Init {
    init {
        TtsBackendRegistry.register(KokoroWorkerTtsBackend())
        TtsBackendRegistry.register(SbVits2TtsBackend())
        TtsBackendRegistry.register(SileroTtsBackend())
        TtsBackendRegistry.register(SpeechT5TtsBackend())
        TtsBackendRegistry.register(TtsWebuiTtsBackend())
        TtsBackendRegistry.register(VitsTtsBackend())
        TtsBackendRegistry.register(XttsTtsBackend())
        TtsBackendRegistry.register(OpenAiCompatibleTtsBackend())
    }
}
