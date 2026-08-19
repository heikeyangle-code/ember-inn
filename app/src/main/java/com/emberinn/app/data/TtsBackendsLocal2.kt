package com.emberinn.app.data

import android.content.Context
import com.emberinn.app.ui.settings.VoicePrefs
import com.emberinn.engine.prompt.TtsRequestEngine
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 第二批本地 TTS 后端：准则 2——引擎层 [TtsRequestEngine] 构造 .js body（差分 38 例），
 * App 层只做：①构建 settings JsonObject（含官方 defaultSettings + VoicePrefs 覆盖）；
 * ②调引擎层得 body；③拼 URL + Header + 发请求 + 解析响应。共用文件级 OkHttpClient
 * （connect 15s / read 120s）；generateTts 失败统一返回 null。路由分发由 TtsBackendRegistry
 * （见 TtsBackend.kt）按 provider id 处理。
 *
 * 注：
 * - sbvits2 / vits 的官方 fetchTtsGeneration 用 URLSearchParams（query string / form-urlencoded），
 *   非 JSON body——引擎层 [TtsRequestEngine.sbvits2Query] / [TtsRequestEngine.vitsForm] 忠实保留
 *   原传输方式（formEncode = URLEncoder.encode，与 JS URLSearchParams.toString 一致 space=+）。
 * - kokoro-worker.js / openai-compatible.js 与官方不同源（WebWorker / ST 代理），登记不差分
 *   （见 HANDOFF 4.4）；App 自有简化契约。
 * - 默认端点取各 .js 的官方默认值（defaultSettings / constructor settings.provider_endpoint）。
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

/** JsonObject → org.json.JSONObject（用于 OkHttp post body；递归一层嵌套对象）。 */
private fun JsonObject.toOrgJson(): org.json.JSONObject {
    val out = org.json.JSONObject()
    for (k in keys) {
        val v = this[k] ?: continue
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

// 1) kokoro-worker — 官方是 WebWorker（kokoro-worker.js，无 fetchTtsGeneration），generateTts 内部
//    tts.generate(text,{voice,speed})；与官方不同源——按需求契约为 POST {endpoint}/tts
//    {text,voice,speaking_rate}（登记不差分，见 HANDOFF 4.4）。
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
//    query 由引擎层 [TtsRequestEngine.sbvits2Query] 构造。voiceId 格式 model_id-speaker_id-style
//    → split('-')，缺位为 "undefined"（对齐 JS 解构）。官方默认值（defaultSettings）传 settings。
class SbVits2TtsBackend : TtsBackend {
    override val id = "sbvits2"
    override val displayName = "Style-Bert-VITS2"
    override val defaultEndpoint = "http://localhost:5000"

    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                // 官方 defaultSettings
                val settings = buildJsonObject {
                    put("sdp_ratio", JsonPrimitive(0.2))
                    put("noise", JsonPrimitive(0.6))
                    put("noisew", JsonPrimitive(0.8))
                    put("length", JsonPrimitive(1))
                    put("language", JsonPrimitive("JP"))
                    put("auto_split", JsonPrimitive(true))
                    put("split_interval", JsonPrimitive(0.5))
                    put("style_weight", JsonPrimitive(1))
                    put("assist_text", JsonPrimitive(""))
                    put("reference_audio_path", JsonPrimitive(""))
                }
                val query = TtsRequestEngine.sbvits2Query(settings, text, voiceId)
                    .jsonObject["query"]!!.jsonPrimitive.content
                // 官方：fetch(url, {method:'POST', headers:{}}) — 无 body
                val request = Request.Builder()
                    .url("$base/voice?$query")
                    .post("".toRequestBody())
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 3) silerotts — silerotts.js fetchTtsGeneration：POST {endpoint}/generate，JSON
//    {text, speaker: voiceId, session: 'sillytavern'}，Content-Type application/json + Cache-Control no-cache。
//    body 由引擎层 [TtsRequestEngine.sileroBody] 构造（无 settings 依赖）。
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
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                val body = TtsRequestEngine.sileroBody(text, voiceId)
                val request = Request.Builder()
                    .url("$base/generate")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 4) speecht5 — speecht5.js fetchTtsGeneration：官方走 ST 内部代理 /api/speech/synthesize，
//    body {text, speaker: speaker.data, model: 'Xenova/speecht5_tts'}（model 官方硬编码，引擎层忠实保留）。
//    speaker 字段实为 2048 字节说话人嵌入（base64），无内置默认 speaker；App 用 voiceId 作 speaker
//    （值源不同但 body 字段集合一致，引擎层打桩）。按需求独立部署采用 {endpoint}/tts 路径
//    （官方 /api/speech/synthesize 仅 ST 服务端可用）。
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
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                val body = TtsRequestEngine.speechT5Body(text, voiceId)
                val request = Request.Builder()
                    .url("$base/tts")
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 5) tts-webui — tts-webui.js fetchTtsGeneration：直接 POST 到 settings.provider_endpoint（完整 URL），
//    JSON {model, voice, input, response_format:'wav', speed, stream, params:{...chatterbox...}}。
//    body 由引擎层 [TtsRequestEngine.ttsWebuiBody] 构造：从 settings 过滤 chatterboxParamKeys 16 项
//    作为 params 子对象；speed 取 settings.speed，stream 取 settings.streaming。
//    官方默认 endpoint = http://127.0.0.1:7778/v1/audio/speech。stream=false 返回完整音频字节。
class TtsWebuiTtsBackend : TtsBackend {
    override val id = "tts-webui"
    override val displayName = "TTS WebUI"
    override val defaultEndpoint = "http://127.0.0.1:7778/v1/audio/speech"

    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                // 官方 defaultSettings（chatterbox 子对象 16 字段 + 顶层 model/speed/streaming）
                val settings = buildJsonObject {
                    put("model", JsonPrimitive(model(context, "chatterbox")))
                    put("speed", JsonPrimitive(1))
                    put("streaming", JsonPrimitive(false))
                    // chatterbox 参数子对象（官方 defaultSettings 全字段）
                    put("desired_length", JsonPrimitive(80))
                    put("max_length", JsonPrimitive(200))
                    put("halve_first_chunk", JsonPrimitive(true))
                    put("exaggeration", JsonPrimitive(0.5))
                    put("cfg_weight", JsonPrimitive(0.5))
                    put("temperature", JsonPrimitive(0.8))
                    put("device", JsonPrimitive("auto"))
                    put("dtype", JsonPrimitive("float32"))
                    put("cpu_offload", JsonPrimitive(false))
                    put("chunked", JsonPrimitive(true))
                    put("cache_voice", JsonPrimitive(false))
                    put("tokens_per_slice", JsonPrimitive(1000))
                    put("remove_milliseconds", JsonPrimitive(45))
                    put("remove_milliseconds_start", JsonPrimitive(25))
                    put("chunk_overlap_method", JsonPrimitive("zero"))
                    put("seed", JsonPrimitive(-1))
                }
                val body = TtsRequestEngine.ttsWebuiBody(settings, text, voiceId)
                val request = Request.Builder()
                    .url(endpoint(context, defaultEndpoint))
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 6) vits — vits.js fetchTtsGeneration：POST {endpoint}/voice/{model_type 小写}，
//    Content-Type application/x-www-form-urlencoded，body = URLSearchParams（text, id=speaker_id, format,
//    lang, length, noise, noisew, segment_size + W2V2/BERT-VITS2 条件字段）。voiceId 格式 model_type&id。
//    form 由引擎层 [TtsRequestEngine.vitsForm] 构造（forceNoStreaming=true 锁非流式分支，对齐 App 用法）。
class VitsTtsBackend : TtsBackend {
    override val id = "vits"
    override val displayName = "VITS"
    override val defaultEndpoint = "http://localhost:23456"

    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                // 官方 defaultSettings
                val settings = buildJsonObject {
                    put("format", JsonPrimitive("wav"))
                    put("lang", JsonPrimitive("auto"))
                    put("length", JsonPrimitive(1.0))
                    put("noise", JsonPrimitive(0.33))
                    put("noisew", JsonPrimitive(0.4))
                    put("segment_size", JsonPrimitive(50))
                    put("dim_emotion", JsonPrimitive(0))
                    put("sdp_ratio", JsonPrimitive(0.2))
                    put("emotion", JsonPrimitive(0))
                    put("text_prompt", JsonPrimitive(""))
                    put("style_text", JsonPrimitive(""))
                    put("style_weight", JsonPrimitive(1))
                }
                val form = TtsRequestEngine.vitsForm(settings, text, voiceId, forceNoStreaming = true)
                val formStr = form.jsonObject["form"]!!.jsonPrimitive.content
                val modelType = form.jsonObject["model_type"]!!.jsonPrimitive.content
                val request = Request.Builder()
                    .url("$base/voice/$modelType")
                    .post(formStr.toRequestBody(formMedia))
                    .build()
                client.newCall(request).execute().use { it.body?.bytes() }
            }.getOrNull()
        }
}

// 7) xtts — xtts.js fetchTtsGeneration（非流式）：POST {endpoint}/tts_to_audio/，JSON
//    {text, speaker_wav: voiceId, language}，Content-Type application/json + Cache-Control no-cache。
//    body 由引擎层 [TtsRequestEngine.xttsBody] 构造。
//    注：speed/temperature 在官方走单独的 /set_tts_settings 调用，不在 fetchTtsGeneration 请求体内；
//    processText 在 fetchTtsGeneration 不调用（死代码），引擎层与 App 均不调用。
class XttsTtsBackend : TtsBackend {
    override val id = "xtts"
    override val displayName = "XTTS"
    override val defaultEndpoint = "http://localhost:8020"

    override suspend fun getVoices(context: Context): List<TtsVoice> = emptyList()

    override suspend fun generateTts(context: Context, text: String, voiceId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = endpoint(context, defaultEndpoint).trimEnd('/')
                val settings = buildJsonObject {
                    put("language", JsonPrimitive("en"))
                }
                val body = TtsRequestEngine.xttsBody(settings, text, voiceId)
                val request = Request.Builder()
                    .url("$base/tts_to_audio/")
                    .post(body.toString().toRequestBody(jsonMedia))
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
//    与官方不同源（ST 代理 vs 直连厂商），登记不差分（见 HANDOFF 4.4）。
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
